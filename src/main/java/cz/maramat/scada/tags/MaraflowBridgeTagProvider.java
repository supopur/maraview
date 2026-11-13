package cz.maramat.scada.tags;

import cz.maramat.scada.model.ScadaProject;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * RuntimeTagProvider bridging ScadaTags to an external PlcTagEndpoint (such as Maraflow's
 * SimulationEngine or a serial/TCP PlcConnection), falling back to internal memory for INTERNAL tags.
 */
public class MaraflowBridgeTagProvider implements RuntimeTagProvider {

    private final PlcTagEndpoint endpoint;

    public MaraflowBridgeTagProvider(PlcTagEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public double readTag(ScadaProject project, String tagName) {
        if (project == null || tagName == null) return 0.0;
        ScadaTag tag = project.getTag(tagName);
        if (tag == null) return 0.0;
        if (tag.getSourceType() == TagSourceType.INTERNAL || endpoint == null) {
            return tag.getRuntimeValue();
        }
        if (!endpoint.isOnline()) {
            tag.setRuntimeQuality(TagQuality.DISCONNECTED);
            return tag.getRuntimeValue();
        }
        String ref = resolveEndpointRef(tag);
        double val = endpoint.readOperand(ref);
        tag.setRuntimeValue(val);
        tag.setRuntimeQuality(TagQuality.GOOD);
        return tag.getRuntimeValue();
    }

    @Override
    public void writeTag(ScadaProject project, String tagName, double value) {
        if (project == null || tagName == null) return;
        ScadaTag tag = project.getTag(tagName);
        if (tag == null) return;
        tag.setRuntimeValue(value);
        if (tag.getSourceType() != TagSourceType.INTERNAL && endpoint != null && endpoint.isOnline()) {
            endpoint.writeOperand(resolveEndpointRef(tag), tag.getRuntimeValue());
            tag.setRuntimeQuality(TagQuality.GOOD);
        }
    }

    @Override
    public void syncAll(ScadaProject project) {
        if (project == null) return;
        boolean online = endpoint != null && endpoint.isOnline();
        Set<String> refs = new LinkedHashSet<>();
        for (ScadaTag tag : project.getTags().values()) {
            if (tag.getSourceType() == TagSourceType.INTERNAL) {
                tag.setRuntimeQuality(TagQuality.GOOD);
            } else if (!online) {
                tag.setRuntimeQuality(TagQuality.DISCONNECTED);
            } else {
                refs.add(resolveEndpointRef(tag));
            }
        }
        if (!online || refs.isEmpty()) {
            return;
        }
        Map<String, Double> values = endpoint.readOperands(refs);
        boolean stillOnline = endpoint.isOnline();
        for (ScadaTag tag : project.getTags().values()) {
            if (tag.getSourceType() == TagSourceType.INTERNAL) {
                continue;
            }
            if (!stillOnline) {
                tag.setRuntimeQuality(TagQuality.DISCONNECTED);
                continue;
            }
            String ref = resolveEndpointRef(tag);
            Double val = values.get(ref);
            if (val != null) {
                tag.setRuntimeValue(val);
            }
            tag.setRuntimeQuality(TagQuality.GOOD);
        }
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
