package cz.maramat.scada.model;

/**
 * Cardinal orientation for equipment flange ports and orthogonal pipe normals.
 */
public enum CardinalDirection {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    private final int dx;
    private final int dy;

    CardinalDirection(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public boolean isHorizontal() {
        return this == EAST || this == WEST;
    }

    public boolean isVertical() {
        return this == NORTH || this == SOUTH;
    }

    public CardinalDirection opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case EAST -> WEST;
            case SOUTH -> NORTH;
            case WEST -> EAST;
        };
    }

    public CardinalDirection rotate90CW() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
        };
    }

    public CardinalDirection flipHorizontal() {
        return switch (this) {
            case EAST -> WEST;
            case WEST -> EAST;
            case NORTH, SOUTH -> this;
        };
    }

    public CardinalDirection flipVertical() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST, WEST -> this;
        };
    }
}
