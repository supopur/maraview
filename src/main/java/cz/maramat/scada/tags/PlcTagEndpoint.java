package cz.maramat.scada.tags;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint abstraction for reading/writing live operands in Maraflow's SimulationEngine
 * or a hardware PlcConnection without coupling Maraview's core model to Maraflow internals.
 */
public interface PlcTagEndpoint {
    double readOperand(String operandOrAddress);

    void writeOperand(String operandOrAddress, double value);

    default Map<String, Double> readOperands(Collection<String> operandsOrAddresses) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (operandsOrAddresses != null) {
            for (String op : operandsOrAddresses) {
                if (op != null && !op.isBlank()) {
                    out.put(op, readOperand(op));
                }
            }
        }
        return out;
    }

    default void writeOperands(Map<String, Double> values) {
        if (values != null) {
            for (Map.Entry<String, Double> entry : values.entrySet()) {
                writeOperand(entry.getKey(), entry.getValue());
            }
        }
    }

    boolean isOnline();

    default String getStatusLabel() {
        return isOnline() ? "ONLINE" : "OFFLINE";
    }
}
