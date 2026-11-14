package cz.maramat.scada.routing;

import cz.maramat.scada.model.CardinalDirection;
import cz.maramat.scada.model.GridPoint;
import cz.maramat.scada.model.PipePort;

import java.util.ArrayList;
import java.util.List;

/**
 * Direction-aware orthogonal shortest-route pipe router on the integer grid.
 * Steps out by flange clearance C = 2G along port normals and connects via
 * 1-segment straight, 2-segment L-bend, 3-segment Z-bend, or 5-segment U/S-bend.
 */
public final class PipeRouter {

    public static final int DEFAULT_FLANGE_CLEARANCE = 1;

    private PipeRouter() {
    }

    public static List<GridPoint> route(PipePort.WorldPort src, PipePort.WorldPort dst) {
        return route(src, dst, DEFAULT_FLANGE_CLEARANCE);
    }

    public static List<GridPoint> route(PipePort.WorldPort src, PipePort.WorldPort dst, int clearance) {
        if (src == null || dst == null) {
            return List.of();
        }
        GridPoint s = src.position();
        GridPoint d = dst.position();
        if (s.equals(d)) {
            return List.of(s);
        }

        CardinalDirection sd = src.direction();
        CardinalDirection dd = dst.direction();
        int c = Math.max(1, clearance);

        // Special case: directly facing and collinear (1-segment straight run)
        if (sd == dd.opposite()) {
            if (sd.isHorizontal() && s.y() == d.y() && Integer.signum(d.x() - s.x()) == sd.dx()) {
                return List.of(s, d);
            }
            if (sd.isVertical() && s.x() == d.x() && Integer.signum(d.y() - s.y()) == sd.dy()) {
                return List.of(s, d);
            }
        }

        GridPoint p1 = new GridPoint(s.x() + sd.dx() * c, s.y() + sd.dy() * c);
        GridPoint p2 = new GridPoint(d.x() + dd.dx() * c, d.y() + dd.dy() * c);

        List<GridPoint> raw = new ArrayList<>();
        raw.add(s);
        raw.add(p1);

        // Connect p1 -> p2 orthogonally based on exit/entry axes
        if (p1.x() == p2.x() || p1.y() == p2.y()) {
            // Check if direct line between p1 and p2 would immediately double back into s or d
            boolean p1Back = isBackward(p1, p2, sd);
            boolean p2Back = isBackward(p2, p1, dd);
            if (!p1Back && !p2Back) {
                raw.add(p2);
            } else {
                appendDetour(raw, p1, p2, sd, dd, c);
            }
        } else if (sd.isHorizontal() && dd.isHorizontal()) {
            boolean facing = (sd == dd.opposite())
                    && Integer.signum(p2.x() - p1.x()) == sd.dx();
            if (facing) {
                // 3-segment Z-bend with vertical midpoint snapped to integer grid
                int midX = (p1.x() + p2.x()) / 2;
                raw.add(new GridPoint(midX, p1.y()));
                raw.add(new GridPoint(midX, p2.y()));
                raw.add(p2);
            } else {
                // 5-segment U/S-bend around ports
                int midY = (p1.y() + p2.y()) / 2;
                if (midY == p1.y() || midY == p2.y()) {
                    midY = Math.max(p1.y(), p2.y()) + c;
                }
                raw.add(new GridPoint(p1.x(), midY));
                raw.add(new GridPoint(p2.x(), midY));
                raw.add(p2);
            }
        } else if (sd.isVertical() && dd.isVertical()) {
            boolean facing = (sd == dd.opposite())
                    && Integer.signum(p2.y() - p1.y()) == sd.dy();
            if (facing) {
                int midY = (p1.y() + p2.y()) / 2;
                raw.add(new GridPoint(p1.x(), midY));
                raw.add(new GridPoint(p2.x(), midY));
                raw.add(p2);
            } else {
                int midX = (p1.x() + p2.x()) / 2;
                if (midX == p1.x() || midX == p2.x()) {
                    midX = Math.max(p1.x(), p2.x()) + c;
                }
                raw.add(new GridPoint(midX, p1.y()));
                raw.add(new GridPoint(midX, p2.y()));
                raw.add(p2);
            }
        } else {
            // Orthogonal pair (H + V or V + H) -> 2-segment L-bend if forward, else 4/5-segment S-bend
            if (sd.isHorizontal()) {
                boolean hForward = Integer.signum(p2.x() - p1.x()) == sd.dx();
                boolean vForward = Integer.signum(p1.y() - p2.y()) == dd.dy();
                if (hForward && vForward) {
                    raw.add(new GridPoint(p2.x(), p1.y()));
                    raw.add(p2);
                } else {
                    int midX = p1.x() + sd.dx() * c;
                    int midY = p2.y() + dd.dy() * c;
                    if (!hForward) {
                        raw.add(new GridPoint(p1.x(), midY));
                        raw.add(new GridPoint(p2.x(), midY));
                    } else {
                        raw.add(new GridPoint(midX, p1.y()));
                        raw.add(new GridPoint(midX, p2.y()));
                    }
                    raw.add(p2);
                }
            } else {
                boolean vForward = Integer.signum(p2.y() - p1.y()) == sd.dy();
                boolean hForward = Integer.signum(p1.x() - p2.x()) == dd.dx();
                if (vForward && hForward) {
                    raw.add(new GridPoint(p1.x(), p2.y()));
                    raw.add(p2);
                } else {
                    int midY = p1.y() + sd.dy() * c;
                    int midX = p2.x() + dd.dx() * c;
                    if (!vForward) {
                        raw.add(new GridPoint(midX, p1.y()));
                        raw.add(new GridPoint(midX, p2.y()));
                    } else {
                        raw.add(new GridPoint(p1.x(), midY));
                        raw.add(new GridPoint(p2.x(), midY));
                    }
                    raw.add(p2);
                }
            }
        }

        raw.add(d);
        return simplify(raw);
    }

    /**
     * Routes from a source port to a free cursor grid point (for live rubber-band preview).
     */
    public static List<GridPoint> routeToPoint(PipePort.WorldPort src, GridPoint cursor) {
        if (src == null || cursor == null) {
            return List.of();
        }
        CardinalDirection opp = src.direction().opposite();
        return route(src, new PipePort.WorldPort(cursor, opp), DEFAULT_FLANGE_CLEARANCE);
    }

    /**
     * Removes consecutive duplicate points and redundant collinear intermediate vertices.
     */
    public static List<GridPoint> simplify(List<GridPoint> pts) {
        if (pts == null || pts.isEmpty()) return List.of();
        List<GridPoint> dedup = new ArrayList<>(pts.size());
        for (GridPoint p : pts) {
            if (dedup.isEmpty() || !dedup.getLast().equals(p)) {
                dedup.add(p);
            }
        }
        if (dedup.size() <= 2) {
            return dedup;
        }
        List<GridPoint> out = new ArrayList<>(dedup.size());
        out.add(dedup.getFirst());
        for (int i = 1; i < dedup.size() - 1; i++) {
            GridPoint prev = out.getLast();
            GridPoint cur = dedup.get(i);
            GridPoint next = dedup.get(i + 1);
            boolean collinearX = prev.x() == cur.x() && cur.x() == next.x();
            boolean collinearY = prev.y() == cur.y() && cur.y() == next.y();
            if (!collinearX && !collinearY) {
                out.add(cur);
            }
        }
        out.add(dedup.getLast());
        return out;
    }

    private static boolean isBackward(GridPoint from, GridPoint to, CardinalDirection dir) {
        if (dir.isHorizontal()) {
            return from.y() == to.y() && Integer.signum(to.x() - from.x()) == -dir.dx();
        }
        return from.x() == to.x() && Integer.signum(to.y() - from.y()) == -dir.dy();
    }

    private static void appendDetour(List<GridPoint> raw, GridPoint p1, GridPoint p2,
                                      CardinalDirection sd, CardinalDirection dd, int c) {
        if (sd.isHorizontal()) {
            int offsetY = p1.y() + c * 2;
            if (p1.x() == p2.x()) {
                int stepX = p1.x() + sd.dx() * Math.abs(c);
                raw.add(new GridPoint(stepX, p1.y()));
                raw.add(new GridPoint(stepX, offsetY));
                raw.add(new GridPoint(p2.x(), offsetY));
            } else {
                raw.add(new GridPoint(p1.x(), offsetY));
                raw.add(new GridPoint(p2.x(), offsetY));
            }
        } else {
            int offsetX = p1.x() + c * 2;
            if (p1.y() == p2.y()) {
                int stepY = p1.y() + sd.dy() * Math.abs(c);
                raw.add(new GridPoint(p1.x(), stepY));
                raw.add(new GridPoint(offsetX, stepY));
                raw.add(new GridPoint(offsetX, p2.y()));
            } else {
                raw.add(new GridPoint(offsetX, p1.y()));
                raw.add(new GridPoint(offsetX, p2.y()));
            }
        }
        raw.add(p2);
    }
}
