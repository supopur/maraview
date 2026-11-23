package cz.maramat.scada.model;

/**
 * Communication settings for connecting the SCADA runtime to Internal Simulator
 * or a MaraProt v1 TCP server (MaraFlow Simulator or physical F407PLC).
 */
public class CommSettings {
    public static final int DEFAULT_MARAFLOW_SIM_PORT = 18502;
    public static final int DEFAULT_MARAPROT_TCP_PORT = 5020;

    public enum Mode {
        INTERNAL_SIM("Internal Simulator"),
        MARAFLOW_SIM("MaraFlow Simulator (MaraProt TCP)"),
        MARAPROT_TCP("PLC Server (MaraProt TCP)");

        private final String displayName;

        Mode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private Mode mode = Mode.INTERNAL_SIM;
    private String host = "127.0.0.1";
    private int tcpPort = DEFAULT_MARAPROT_TCP_PORT;
    private int slaveId = 1;
    private int pollIntervalMs = 50;

    public CommSettings copy() {
        CommSettings c = new CommSettings();
        c.mode = this.mode;
        c.host = this.host;
        c.tcpPort = this.tcpPort;
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

    public boolean isRemoteMaraProt() {
        return mode == Mode.MARAFLOW_SIM || mode == Mode.MARAPROT_TCP;
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
        this.tcpPort = (tcpPort <= 0 || tcpPort > 65535) ? DEFAULT_MARAPROT_TCP_PORT : tcpPort;
    }

    public int getStationId() {
        return slaveId;
    }

    public void setStationId(int stationId) {
        this.slaveId = Math.max(0, Math.min(254, stationId));
    }

    public int getSlaveId() {
        return slaveId;
    }

    public void setSlaveId(int slaveId) {
        setStationId(slaveId);
    }

    public int getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(int pollIntervalMs) {
        this.pollIntervalMs = Math.max(10, pollIntervalMs);
    }

    // Legacy no-op getters/setters for backward compatibility with older .msl/.json files
    public String getSerialPort() { return ""; }
    public void setSerialPort(String ignored) {}
    public int getBaudRate() { return 115200; }
    public void setBaudRate(int ignored) {}
}
