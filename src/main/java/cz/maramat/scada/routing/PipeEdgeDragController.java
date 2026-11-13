package cz.maramat.scada.routing;

import cz.maramat.scada.model.GridPoint;
import cz.maramat.scada.model.ScadaPipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Hit-testing, Blender-style segment dragging, and bend-splitting controller for orthogonal ScadaPipes.
 */
public final class PipeEdgeDragController {

    public record SegmentHit(ScadaPipe pipe, int segmentIndex, boolean horizontal, GridPoint snappedPoint) {
    }

    private PipeEdgeDragController() {
    }

    public static SegmentHit hitTest(List<ScadaPipe> pipes, double gridX, double gridY, double toleranceGrid) {
        if (pipes == null) return null;
        for (int i = pipes.size() - 1; i >= 0; i--) {
            ScadaPipe pipe = pipes.get(i);
            SegmentHit hit = hitTestPipe(pipe, gridX, gridY, toleranceGrid);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    public static SegmentHit hitTestPipe(ScadaPipe pipe, double gridX, double gridY, double toleranceGrid) {
        if (pipe == null) return null;
        List<GridPoint> verts = pipe.getVertices();
        if (verts.size() < 2) return null;

        double bestDist = toleranceGrid;
        SegmentHit best = null;
        for (int i = 0; i < verts.size() - 1; i++) {
            GridPoint a = verts.get(i);
            GridPoint b = verts.get(i + 1);
            boolean horiz = (a.y() == b.y());
            boolean vert = (a.x() == b.x());
            if (!horiz && !vert) continue;

            double dist;
            GridPoint snapped;
            if (horiz) {
                int minX = Math.min(a.x(), b.x());
                int maxX = Math.max(a.x(), b.x());
                if (gridX < minX - toleranceGrid || gridX > maxX + toleranceGrid) continue;
                dist = Math.abs(gridY - a.y());
                int sx = Math.max(minX, Math.min(maxX, (int) Math.round(gridX)));
                snapped = new GridPoint(sx, a.y());
            } else {
                int minY = Math.min(a.y(), b.y());
                int maxY = Math.max(a.y(), b.y());
                if (gridY < minY - toleranceGrid || gridY > maxY + toleranceGrid) continue;
                dist = Math.abs(gridX - a.x());
                int sy = Math.max(minY, Math.min(maxY, (int) Math.round(gridY)));
                snapped = new GridPoint(a.x(), sy);
            }

            if (dist <= bestDist) {
                bestDist = dist;
                best = new SegmentHit(pipe, i, horiz, snapped);
            }
        }
        return best;
    }

    /**
     * Moves an orthogonal pipe segment perpendicular to its axis by deltaGrid,
     * lengthening/shortening adjacent risers and inserting stubs if dragging an endpoint segment.
     */
    public static int moveSegment(ScadaPipe pipe, int segIndex, int deltaGrid) {
        if (pipe == null || deltaGrid == 0) return segIndex;
        List<GridPoint> verts = new ArrayList<>(pipe.getVertices());
        if (segIndex < 0 || segIndex >= verts.size() - 1) return segIndex;

        GridPoint a = verts.get(segIndex);
        GridPoint b = verts.get(segIndex + 1);
        boolean horiz = (a.y() == b.y());
        boolean vert = (a.x() == b.x());
        if (!horiz && !vert) return segIndex;

        int activeIdx = segIndex;

        // If first segment is attached directly, insert a perpendicular stub before it so port anchor stays fixed
        if (activeIdx == 0) {
            verts.add(0, a);
            activeIdx++;
        }
        // If last segment is attached directly, insert a perpendicular stub after it so target anchor stays fixed
        if (activeIdx + 1 == verts.size() - 1) {
            verts.add(verts.get(activeIdx + 1));
        }

        GridPoint curA = verts.get(activeIdx);
        GridPoint curB = verts.get(activeIdx + 1);
        if (horiz) {
            int ny = curA.y() + deltaGrid;
            verts.set(activeIdx, new GridPoint(curA.x(), ny));
            verts.set(activeIdx + 1, new GridPoint(curB.x(), ny));
        } else {
            int nx = curA.x() + deltaGrid;
            verts.set(activeIdx, new GridPoint(nx, curA.y()));
            verts.set(activeIdx + 1, new GridPoint(nx, curB.y()));
        }

        pipe.setVertices(verts);
        return activeIdx;
    }

    /**
     * Simplifies redundant collinear/zero-length vertices after a segment drag completes.
     */
    public static void finalizeSegmentDrag(ScadaPipe pipe) {
        if (pipe == null) return;
        pipe.setVertices(PipeRouter.simplify(pipe.getVertices()));
    }

    /**
     * Splits the clicked pipe segment at splitPoint by inserting a step-ready bend pair,
     * allowing either half to be dragged independently around obstacles.
     */
    public static void addBendAt(ScadaPipe pipe, int segIndex, GridPoint splitPoint) {
        if (pipe == null || splitPoint == null) return;
        List<GridPoint> verts = new ArrayList<>(pipe.getVertices());
        if (segIndex < 0 || segIndex >= verts.size() - 1) return;

        GridPoint a = verts.get(segIndex);
        GridPoint b = verts.get(segIndex + 1);
        boolean horiz = (a.y() == b.y());
        GridPoint p = horiz ? new GridPoint(splitPoint.x(), a.y()) : new GridPoint(a.x(), splitPoint.y());

        // Insert two bend vertices (with a 1G step offset on the second half so it's immediately visible & draggable)
        if (horiz) {
            verts.add(segIndex + 1, p);
            verts.add(segIndex + 2, new GridPoint(p.x(), p.y() + 1));
            verts.set(segIndex + 3, new GridPoint(b.x(), b.y() + 1));
            if (segIndex + 3 == verts.size() - 1) {
                verts.add(b);
            }
        } else {
            verts.add(segIndex + 1, p);
            verts.add(segIndex + 2, new GridPoint(p.x() + 1, p.y()));
            verts.set(segIndex + 3, new GridPoint(b.x() + 1, b.y()));
            if (segIndex + 3 == verts.size() - 1) {
                verts.add(b);
            }
        }
        pipe.setVertices(verts);
    }
}
