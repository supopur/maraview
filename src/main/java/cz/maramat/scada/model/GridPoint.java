package cz.maramat.scada.model;

/**
 * Immutable integer grid coordinate (in grid units G, where 1G = 10px at 100% zoom).
 */
public record GridPoint(int x, int y) {
}
