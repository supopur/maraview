package cz.maramat.scada.tags;

import cz.maramat.scada.model.ScadaProject;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * RuntimeTagProvider bridging ScadaTags to an external PlcTagEndpoint (such as Maraflow's
 * SimulationEngine or a serial/TCP PlcConnection), falling back to internal memory for INTERNAL tags.
 * <p>
 * {@link #readTag(ScadaProject, String)} is strictly cache-only and never performs socket I/O,
 * keeping Swing painting off the network.
 */
public class MaraflowBridgeTagProvider implements RuntimeTagProvider {

    public record Snapshot(Map<String, Double> values, boolean online) {
    }

    private final PlcTagEndpoint endpoint;
    private volatile Executor writeExecutor;

    public MaraflowBridgeTagProvider(PlcTagEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public void setWriteExecutor(Executor writeExecutor) {
        this.writeExecutor = writeExecutor;
    }

    @Override
    public double readTag(ScadaProject project, String tagName) {
        if (project == null || tagName == null) return 0.0;
        ScadaTag tag = project.getTag(tagName);
        if (tag == null) return 0.0;
        return tag.getRuntimeValue();
    }

    @Override
    public void writeTag(ScadaProject project, String tagName, double value) {
        if (project == null || tagName == null) return;
        ScadaTag tag = project.getTag(tagName);
        if (tag == null) return;
        tag.setRuntimeValue(value);
        if (tag.getSourceType() != TagSourceType.INTERNAL && endpoint != null && endpoint.isOnline()) {
            String ref = resolveEndpointRef(tag);
            double runtimeVal = tag.getRuntimeValue();
            tag.setRuntimeQuality(TagQuality.GOOD);
            Executor exec = this.writeExecutor;
            if (exec != null) {
                exec.execute(() -> endpoint.writeOperand(ref, runtimeVal));
            } else {
                endpoint.writeOperand(ref, runtimeVal);
            }
        }
    }

    /**
     * Fetches external operand values from the endpoint; safe to call from a background thread.
     */
    public Snapshot fetchSnapshot(ScadaProject project) {
        if (project == null || endpoint == null || !endpoint.isOnline()) {
            return new Snapshot(Map.of(), false);
        }
        Set<String> refs = new LinkedHashSet<>();
        for (ScadaTag tag : project.getTags().values()) {
            if (tag.getSourceType() != TagSourceType.INTERNAL) {
                refs.add(resolveEndpointRef(tag));
            }
        }
        if (refs.isEmpty()) {
            return new Snapshot(Map.of(), true);
        }
        Map<String, Double> values = endpoint.readOperands(refs);
        return new Snapshot(values, endpoint.isOnline());
    }

    /**
     * Applies a previously fetched snapshot to the project tags on the EDT.
     *
     * @return true if any tag value or quality changed
     */
    public boolean applySnapshot(ScadaProject project, Snapshot snapshot) {
        if (project == null) return false;
        boolean online = snapshot != null && snapshot.online();
        Map<String, Double> values = (snapshot != null && snapshot.values() != null) ? snapshot.values() : Map.of();
        boolean changed = false;
        for (ScadaTag tag : project.getTags().values()) {
            if (tag.getSourceType() == TagSourceType.INTERNAL) {
                if (tag.getRuntimeQuality() != TagQuality.GOOD) {
                    tag.setRuntimeQuality(TagQuality.GOOD);
                    changed = true;
                }
                continue;
            }
            if (!online) {
                if (tag.getRuntimeQuality() != TagQuality.DISCONNECTED) {
                    tag.setRuntimeQuality(TagQuality.DISCONNECTED);
                    changed = true;
                }
                continue;
            }
            String ref = resolveEndpointRef(tag);
            Double val = values.get(ref);
            if (val != null && Double.compare(tag.getRuntimeValue(), val) != 0) {
                tag.setRuntimeValue(val);
                changed = true;
            }
            if (tag.getRuntimeQuality() != TagQuality.GOOD) {
                tag.setRuntimeQuality(TagQuality.GOOD);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void syncAll(ScadaProject project) {
        applySnapshot(project, fetchSnapshot(project));
    }

    @Override
    public boolean isConnected() {
        return endpoint != null && endpoint.isOnline();
    }

    @Override
    public String getConnectionStatusText() {
        return endpoint != null ? endpoint.getStatusLabel() : "OFFLINE";
    }

    private static String resolveEndpointRef(ScadaTag tag) {
        String ref = tag.getAddressOrRef();
        return (ref == null || ref.isBlank()) ? tag.getName() : ref;
    }
}
