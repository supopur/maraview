package cz.maramat.scada.model;

/**
 * Physical connection medium for ports and routing lines.
 * Ensures fluid pipes only connect to fluid ports, electrical wires to electrical terminals,
 * and MaR control signals to signal terminals.
 */
public enum ConnectionType {
    PIPE("Fluid Process Pipe"),
    ELECTRICAL_POWER("Electrical Power (230V / 400V / 24V)"),
    CONTROL_SIGNAL("MaR Control Signal (0-10V / 4-20mA / DI-DO)"),
    PNEUMATIC("Pneumatic Air Line"),
    CAPILLARY("Capillary Tube");

    private final String displayName;

    ConnectionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isCompatibleWith(ConnectionType other) {
        if (other == null) return false;
        return this == other;
    }
}
