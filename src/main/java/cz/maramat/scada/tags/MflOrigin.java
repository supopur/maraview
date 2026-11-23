package cz.maramat.scada.tags;

/**
 * Specific Maraflow PLC object category when TagSourceType == MFL_LINK.
 */
public enum MflOrigin {
    NONE,
    SYMBOLIC_TAG,
    VIRTUAL_REGISTER,
    X_VAR,
    ST_VARIABLE,
    HARDWARE_IO,
    BLOCK_PIN
}
