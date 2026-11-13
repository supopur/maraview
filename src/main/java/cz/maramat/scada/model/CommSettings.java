package cz.maramat.scada.model;

/**
 * Communication settings for connecting the SCADA runtime to simulation or physical PLC hardware.
 */
public class CommSettings {
    public static final int DEFAULT_MARAFLOW_SIM_PORT = 18502;

    public enum Mode {
        INTERNAL_SIM,
        MARAFLOW_SIM,
        PLC_WATCH_SERIAL,
        PLC_WATCH_TCP,
        MODBUS_TCP,
        MODBUS_RTU
    }

    private Mode mode = Mode.INTERNAL_SIM;
    private String host = "127.0.0.1";
    private int tcpPort = 502;
    private String serialPort = "";
    private int baudRate = 115200;
    private int slaveId = 1;
    private int pollIntervalMs = 50;

    public CommSettings copy() {
        CommSettings c = new CommSettings();
        c.mode = this.mode;
        c.host = this.host;
        c.tcpPort = this.tcpPort;
        c.serialPort = this.serialPort;
        c.baudRate = this.baudRate;
        c.slaveId = this.slaveId;
        c.pollIntervalMs = this.pollIntervalMs;
        return c;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = (mode == null) ? Mode.INTERNAL_SIM : mode;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = (host == null || host.isBlank()) ? "127.0.0.1" : host.trim();
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public void setTcpPort(int tcpPort) {
        this.tcpPort = (tcpPort <= 0 || tcpPort > 65535) ? 502 : tcpPort;
    }

    public String getSerialPort() {
        return serialPort;
    }

    public void setSerialPort(String serialPort) {
        this.serialPort = (serialPort == null) ? "" : serialPort.trim();
    }

    public int getBaudRate() {
        return baudRate;
    }

    public void setBaudRate(int baudRate) {
        this.baudRate = baudRate > 0 ? baudRate : 115200;
    }

    public int getSlaveId() {
        return slaveId;
    }

    public void setSlaveId(int slaveId) {
        this.slaveId = Math.max(0, Math.min(255, slaveId));
    }

    public int getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(int pollIntervalMs) {
        this.pollIntervalMs = Math.max(10, pollIntervalMs);
    }
}
