package cz.maramat.scada.tags;

import cz.maramat.scada.model.ScadaProject;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Standalone in-memory tag provider used in Designer preview and offline testing.
 */
public class InternalTagProvider implements RuntimeTagProvider {

    private final AtomicLong revision = new AtomicLong(0L);

    @Override
    public long getRevision() {
        return revision.get();
    }

    @Override
    public double readTag(ScadaProject project, String tagName) {
        if (project == null || tagName == null) return 0.0;
        ScadaTag tag = project.getTag(tagName);
        return (tag != null) ? tag.getRuntimeValue() : 0.0;
    }

    @Override
    public void writeTag(ScadaProject project, String tagName, double value) {
        if (project == null || tagName == null) return;
        ScadaTag tag = project.getTag(tagName);
        if (tag != null) {
            tag.setRuntimeValue(value);
            tag.setRuntimeQuality(TagQuality.GOOD);
            revision.incrementAndGet();
        }
    }

    @Override
    public void syncAll(ScadaProject project) {
        if (project == null) return;
        for (ScadaTag tag : project.getTags().values()) {
            tag.setRuntimeQuality(TagQuality.GOOD);
        }
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public String getConnectionStatusText() {
        return "INTERNAL SIM";
    }
}
