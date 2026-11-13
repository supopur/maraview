package cz.maramat.scada.model;

/**
 * Standard pipe outer diameters in grid units (G).
 */
public enum PipeDiameter {
    SMALL("Small (1G)", 1.0),
    MEDIUM("Medium (1.5G)", 1.5),
    LARGE("Large (2G)", 2.0);

    private final String displayName;
    private final double gridUnits;

    PipeDiameter(String displayName, double gridUnits) {
        this.displayName = displayName;
        this.gridUnits = gridUnits;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getGridUnits() {
        return gridUnits;
    }
}
