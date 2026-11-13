package cz.maramat.scada.render;

/**
 * Operational visual state for SCADA equipment symbols driven by bound PLC tags.
 */
public enum EquipmentState {
    STOPPED,
    RUNNING,
    STANDBY,
    WARNING,
    FAULT;

    /**
     * Returns true during the active half-cycle of the 2 Hz (500 ms period) fault halo blink.
     */
    public static boolean isFaultFlashOn(long timeMs) {
        return ((timeMs / 250L) & 1L) == 0L;
    }
}
