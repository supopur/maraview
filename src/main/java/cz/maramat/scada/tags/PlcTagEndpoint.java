package cz.maramat.scada.tags;

/**
 * Endpoint abstraction for reading/writing live operands in Maraflow's SimulationEngine
 * or a hardware PlcConnection without coupling Maraview's core model to Maraflow internals.
 */
public interface PlcTagEndpoint {
    double readOperand(String operandOrAddress);

    void writeOperand(String operandOrAddress, double value);

    boolean isOnline();

    default String getStatusLabel() {
        return isOnline() ? "ONLINE" : "OFFLINE";
    }
}
