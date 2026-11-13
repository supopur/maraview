package cz.maramat.scada.alarms;

import cz.maramat.scada.alarms.AlarmEntry.Severity;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.tags.AlarmConfig;
import cz.maramat.scada.tags.ScadaTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates tag values against configured High/Low/Fault alarm thresholds and tracks acknowledgment state.
 */
public class AlarmEngine {

    private final Map<String, AlarmEntry> entries = new LinkedHashMap<>();

    public synchronized void evaluate(ScadaProject project, long nowMs) {
        if (project == null) return;
        for (ScadaTag tag : project.getTags().values()) {
            AlarmConfig cfg = tag.getAlarmConfig();
            if (cfg == null || !cfg.isEnabled()) {
                entries.remove(tag.getName());
                continue;
            }
            double val = tag.getRuntimeValue();
            Severity sev = checkThreshold(cfg, val);
            AlarmEntry existing = entries.get(tag.getName());

            if (sev != null) {
                String msg = (cfg.getAlarmMessage() != null && !cfg.getAlarmMessage().isBlank())
                        ? cfg.getAlarmMessage()
                        : tag.getName() + " " + sev.name() + " (" + tag.getFormattedValue(true) + ")";
                if (existing == null) {
                    entries.put(tag.getName(), new AlarmEntry(tag.getName(), sev, msg, val, nowMs));
                } else {
                    if (!existing.isActive() || existing.getSeverity() != sev) {
                        existing.setAcknowledged(false);
                        existing.setTimestampMs(nowMs);
                    }
                    existing.setActive(true);
                    existing.setSeverity(sev);
                    existing.setMessage(msg);
                    existing.setTriggerValue(val);
                }
            } else if (existing != null) {
                if (existing.isAcknowledged()) {
                    entries.remove(tag.getName());
                } else {
                    existing.setActive(false);
                }
            }
        }
    }

    public synchronized void acknowledge(String tagName) {
        AlarmEntry entry = entries.get(tagName);
        if (entry != null) {
            entry.setAcknowledged(true);
            if (!entry.isActive()) {
                entries.remove(tagName);
            }
        }
    }

    public synchronized void acknowledgeAll() {
        List<String> toRemove = new ArrayList<>();
        for (AlarmEntry entry : entries.values()) {
            entry.setAcknowledged(true);
            if (!entry.isActive()) {
                toRemove.add(entry.getTagName());
            }
        }
        toRemove.forEach(entries::remove);
    }

    public synchronized List<AlarmEntry> getAlarms() {
        return new ArrayList<>(entries.values());
    }

    public synchronized boolean hasUnacknowledged() {
        for (AlarmEntry e : entries.values()) {
            if (!e.isAcknowledged()) return true;
        }
        return false;
    }

    private static Severity checkThreshold(AlarmConfig cfg, double val) {
        if (cfg.isFaultOnTrue() && val != 0.0) {
            return Severity.FAULT;
        }
        if (cfg.getHighHighLimit() != null && val >= cfg.getHighHighLimit()) {
            return Severity.HIGH_HIGH;
        }
        if (cfg.getLowLowLimit() != null && val <= cfg.getLowLowLimit()) {
            return Severity.LOW_LOW;
        }
        if (cfg.getHighLimit() != null && val >= cfg.getHighLimit()) {
            return Severity.HIGH;
        }
        if (cfg.getLowLimit() != null && val <= cfg.getLowLimit()) {
            return Severity.LOW;
        }
        return null;
    }
}
