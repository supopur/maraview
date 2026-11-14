package cz.maramat.scada.model;

/**
 * Standard pipe outer diameters in grid units (G).
 */
public enum PipeDiameter {
    SMALL("Small (DN25)", 0.65),
    MEDIUM("Medium (DN50)", 0.90),
    LARGE("Large (DN100)", 1.25),
    XLARGE("Extra Large (DN150)", 1.60);

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
