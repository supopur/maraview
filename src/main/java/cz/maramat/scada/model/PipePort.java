package cz.maramat.scada.model;

/**
 * Directional flange port anchor on a SCADA equipment symbol, specified in unrotated/unflipped
 * local grid coordinates relative to the symbol's top-left corner.
 */
public record PipePort(
        String id,
        String label,
        int offsetX,
        int offsetY,
        CardinalDirection direction,
        ConnectionType connectionType
) {

    public PipePort(String id, String label, int offsetX, int offsetY, CardinalDirection direction) {
        this(id, label, offsetX, offsetY, direction, ConnectionType.PIPE);
    }

    public PipePort {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("PipePort id cannot be blank.");
        }
        if (label == null) {
            label = id;
        }
        if (direction == null) {
            throw new IllegalArgumentException("PipePort direction cannot be null.");
        }
        if (connectionType == null) {
            connectionType = ConnectionType.PIPE;
        }
    }

    public record WorldPort(GridPoint position, CardinalDirection direction) {
    }

    /**
     * Resolves this port's world grid position and outward normal direction after applying
     * horizontal/vertical flip and 90-degree clockwise rotation steps.
     */
    public WorldPort resolveWorldPort(int symX, int symY, int symW, int symH,
                                      int rotationDeg, boolean flipH, boolean flipV) {
        int lx = offsetX;
        int ly = offsetY;
        CardinalDirection dir = direction;

        if (flipH) {
            lx = symW - lx;
            dir = dir.flipHorizontal();
        }
        if (flipV) {
            ly = symH - ly;
            dir = dir.flipVertical();
        }

        int normRot = normalizeRotation(rotationDeg);
        int rx;
        int ry;
        switch (normRot) {
            case 90 -> {
                rx = symH - ly;
                ry = lx;
                dir = dir.rotate90CW();
            }
            case 180 -> {
                rx = symW - lx;
                ry = symH - ly;
                dir = dir.opposite();
            }
            case 270 -> {
                rx = ly;
                ry = symW - lx;
                dir = dir.rotate90CW().rotate90CW().rotate90CW();
            }
            default -> {
                rx = lx;
                ry = ly;
            }
        }
        return new WorldPort(new GridPoint(symX + rx, symY + ry), dir);
    }

    public static int normalizeRotation(int deg) {
        int r = ((deg % 360) + 360) % 360;
        if (r == 90 || r == 180 || r == 270) {
            return r;
        }
        return 0;
    }
}
