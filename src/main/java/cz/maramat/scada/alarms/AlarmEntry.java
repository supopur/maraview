package cz.maramat.scada.alarms;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Active or unacknowledged SCADA alarm instance for a tag.
 */
public class AlarmEntry {
    public enum Severity {
        FAULT,
        HIGH_HIGH,
        HIGH,
        LOW,
        LOW_LOW
    }

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final String tagName;
    private Severity severity;
    private String message;
    private double triggerValue;
    private long timestampMs;
    private boolean active;
    private boolean acknowledged;

    public AlarmEntry(String tagName, Severity severity, String message, double triggerValue, long timestampMs) {
        this.tagName = tagName;
        this.severity = severity;
        this.message = message;
        this.triggerValue = triggerValue;
        this.timestampMs = timestampMs;
        this.active = true;
        this.acknowledged = false;
    }

    public String getFormattedTime() {
        return TIME_FMT.format(Instant.ofEpochMilli(timestampMs));
    }

    public String getTagName() {
        return tagName;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public double getTriggerValue() {
        return triggerValue;
    }

    public void setTriggerValue(double triggerValue) {
        this.triggerValue = triggerValue;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public void setTimestampMs(long timestampMs) {
        this.timestampMs = timestampMs;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public void setAcknowledged(boolean acknowledged) {
        this.acknowledged = acknowledged;
    }
}
