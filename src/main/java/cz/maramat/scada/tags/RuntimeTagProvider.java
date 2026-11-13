package cz.maramat.scada.tags;

import cz.maramat.scada.model.ScadaProject;

/**
 * Abstraction for reading and writing live SCADA tag values against internal memory,
 * Maraflow's SimulationEngine, or a physical PLC connection.
 */
public interface RuntimeTagProvider {
    double readTag(ScadaProject project, String tagName);

    void writeTag(ScadaProject project, String tagName, double value);

    void syncAll(ScadaProject project);

    boolean isConnected();

    String getConnectionStatusText();
}
