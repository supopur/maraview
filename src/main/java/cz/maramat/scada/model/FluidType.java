package cz.maramat.scada.model;

import java.awt.Color;

/**
 * Process media / fluid types determining interior pipe and vessel liquid colors.
 */
public enum FluidType {
    EMPTY("Empty / Depressurized", new Color(0x4A, 0x52, 0x5D), new Color(0x63, 0x6D, 0x7A), null),
    WATER("Water (Cold / Process)", new Color(0x1F, 0x61, 0x8D), new Color(0x54, 0x99, 0xC7), new Color(0xAE, 0xD6, 0xF1)),
    HOT_WATER_STEAM("Hot Water / Steam", new Color(0xC0, 0x39, 0x2B), new Color(0xE7, 0x4C, 0x3C), new Color(0xF5, 0xB7, 0xB1)),
    OIL_FUEL("Oil / Fuel / Hydraulic", new Color(0xB7, 0x79, 0x1F), new Color(0xD6, 0x9E, 0x2E), new Color(0xF6, 0xE0, 0x5E)),
    CHEMICAL_GAS("Chemical / Acid / Gas", new Color(0x1E, 0x84, 0x49), new Color(0x2E, 0xCC, 0x71), new Color(0xAB, 0xEB, 0xC6)),
    SLURRY("Slurry / Wastewater", new Color(0x5D, 0x40, 0x37), new Color(0x8D, 0x6E, 0x63), new Color(0xBC, 0xAA, 0xA4));

    private final String displayName;
    private final Color baseColor;
    private final Color highlightColor;
    private final Color flowDashColor;

    FluidType(String displayName, Color baseColor, Color highlightColor, Color flowDashColor) {
        this.displayName = displayName;
        this.baseColor = baseColor;
        this.highlightColor = highlightColor;
        this.flowDashColor = flowDashColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Color getBaseColor() {
        return baseColor;
    }

    public Color getHighlightColor() {
        return highlightColor;
    }

    public Color getFlowDashColor() {
        return flowDashColor;
    }
}
