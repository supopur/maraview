package cz.maramat.scada.tags;

/**
 * Configurable High/Low/Fault alarm thresholds for a ScadaTag.
 */
public class AlarmConfig {
    private boolean enabled;
    private Double highHighLimit;
    private Double highLimit;
    private Double lowLimit;
    private Double lowLowLimit;
    private boolean faultOnTrue;
    private String alarmMessage = "";

    public AlarmConfig copy() {
        AlarmConfig c = new AlarmConfig();
        c.enabled = this.enabled;
        c.highHighLimit = this.highHighLimit;
        c.highLimit = this.highLimit;
        c.lowLimit = this.lowLimit;
        c.lowLowLimit = this.lowLowLimit;
        c.faultOnTrue = this.faultOnTrue;
        c.alarmMessage = this.alarmMessage;
        return c;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Double getHighHighLimit() {
        return highHighLimit;
    }

    public void setHighHighLimit(Double highHighLimit) {
        this.highHighLimit = highHighLimit;
    }

    public Double getHighLimit() {
        return highLimit;
    }

    public void setHighLimit(Double highLimit) {
        this.highLimit = highLimit;
    }

    public Double getLowLimit() {
        return lowLimit;
    }

    public void setLowLimit(Double lowLimit) {
        this.lowLimit = lowLimit;
    }

    public Double getLowLowLimit() {
        return lowLowLimit;
    }

    public void setLowLowLimit(Double lowLowLimit) {
        this.lowLowLimit = lowLowLimit;
    }

    public boolean isFaultOnTrue() {
        return faultOnTrue;
    }

    public void setFaultOnTrue(boolean faultOnTrue) {
        this.faultOnTrue = faultOnTrue;
    }

    public String getAlarmMessage() {
        return alarmMessage;
    }

    public void setAlarmMessage(String alarmMessage) {
        this.alarmMessage = (alarmMessage == null) ? "" : alarmMessage;
    }
}
