package cz.maramat.scada.render;

import cz.maramat.scada.model.CardinalDirection;
import cz.maramat.scada.model.FluidType;
import cz.maramat.scada.model.GridPoint;
import cz.maramat.scada.model.ScadaPipe;
import cz.maramat.scada.render.ScadaPalette.Material;
import cz.maramat.scada.routing.FlowState;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * 3-pass 3D multi-pass renderer for orthogonal ScadaPipes:
 * Pass 1: 3D cylindrical horizontal/vertical pipe segments.
 * Pass 2: 3D mitered/rounded elbows, tees, and bolted port flanges.
 * Pass 3: Animated water/fluid cutaway with moving lighter bubbles (only when simulation is running/paused).
 */
public final class PipeRenderer {

    private static final BasicStroke OUTLINE_STROKE = new BasicStroke(1.0f);
    private static final BasicStroke SELECTED_STROKE = new BasicStroke(2.0f);

    private PipeRenderer() {
    }

    public static void paintPipe(Graphics2D g, ScadaPipe pipe, int gridSizePx,
                                 FlowState flowState, boolean simActive,
                                 double dashPhasePx, boolean selected) {
        if (pipe == null) return;
        List<GridPoint> verts = pipe.getVertices();
        if (verts.size() < 2) return;

        double diameterPx = Math.max(6.0, pipe.getDiameter().getGridUnits() * gridSizePx);
        double halfD = diameterPx / 2.0;

        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();

        // Pass 1: 3D cylindrical pipe segments (solid metallic grey)
        for (int i = 0; i < verts.size() - 1; i++) {
            GridPoint a = verts.get(i);
            GridPoint b = verts.get(i + 1);
            double x1 = a.x() * (double) gridSizePx;
            double y1 = a.y() * (double) gridSizePx;
            double x2 = b.x() * (double) gridSizePx;
            double y2 = b.y() * (double) gridSizePx;

            if (a.y() == b.y()) {
                double rx = Math.min(x1, x2);
                double rw = Math.abs(x2 - x1);
                if (rw > 0.5) {
                    paintHorizontalWall(g, rx, y1 - halfD, rw, diameterPx);
                }
            } else if (a.x() == b.x()) {
                double ry = Math.min(y1, y2);
                double rh = Math.abs(y2 - y1);
                if (rh > 0.5) {
                    paintVerticalWall(g, x1 - halfD, ry, diameterPx, rh);
                }
            }
        }

        // Pass 2: 3D Elbows at 90-degree bends + end flanges
        for (int i = 1; i < verts.size() - 1; i++) {
            GridPoint v = verts.get(i);
            double cx = v.x() * (double) gridSizePx;
            double cy = v.y() * (double) gridSizePx;
            paintElbowCollar(g, cx, cy, diameterPx);
        }

        GridPoint first = verts.getFirst();
        GridPoint second = verts.get(1);
        CardinalDirection startDir = segmentDirection(first, second);
        if (startDir != null) {
            ScadaShader.paintFlange(g, first.x() * (double) gridSizePx, first.y() * (double) gridSizePx,
                    diameterPx + 6.0, Math.max(4.0, gridSizePx * 0.45), startDir);
        }
        GridPoint last = verts.getLast();
        GridPoint prevLast = verts.get(verts.size() - 2);
        CardinalDirection endDir = segmentDirection(last, prevLast);
        if (endDir != null) {
            ScadaShader.paintFlange(g, last.x() * (double) gridSizePx, last.y() * (double) gridSizePx,
                    diameterPx + 6.0, Math.max(4.0, gridSizePx * 0.45), endDir);
        }

        // Pass 3: Water/Fluid infill + moving lighter bubbles (hidden completely when sim is stopped)
        FluidType fluid = (flowState != null && flowState.fluidType() != null)
                ? flowState.fluidType()
                : pipe.getFluidType();
        if (simActive && fluid != FluidType.EMPTY && flowState != null && flowState.pressurized()) {
            double coreWidth = Math.max(3.5, diameterPx * 0.50);
            g.setColor(fluid.getBaseColor());
            g.setStroke(new BasicStroke((float) coreWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < verts.size() - 1; i++) {
                GridPoint a = verts.get(i);
                GridPoint b = verts.get(i + 1);
                g.draw(new Line2D.Double(
                        a.x() * (double) gridSizePx, a.y() * (double) gridSizePx,
                        b.x() * (double) gridSizePx, b.y() * (double) gridSizePx
                ));
            }

            // Inner lighter water sheen
            g.setColor(new Color(fluid.getHighlightColor().getRed(), fluid.getHighlightColor().getGreen(),
                    fluid.getHighlightColor().getBlue(), 140));
            g.setStroke(new BasicStroke((float) Math.max(1.5, coreWidth * 0.42), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < verts.size() - 1; i++) {
                GridPoint a = verts.get(i);
                GridPoint b = verts.get(i + 1);
                g.draw(new Line2D.Double(
                        a.x() * (double) gridSizePx, a.y() * (double) gridSizePx,
                        b.x() * (double) gridSizePx, b.y() * (double) gridSizePx
                ));
            }

            // Moving lighter water bubbles along each segment
            if (flowState.flowRate() > 0.0 && fluid.getFlowDashColor() != null) {
                paintFlowBubbles(g, verts, gridSizePx, coreWidth, fluid.getFlowDashColor(),
                        dashPhasePx * Math.max(0.35, flowState.flowRate()));
            }
        }

        // Selection highlight overlay
        if (selected) {
            g.setColor(new Color(0x00, 0x78, 0xD7, 190));
            g.setStroke(SELECTED_STROKE);
            for (int i = 0; i < verts.size() - 1; i++) {
                GridPoint a = verts.get(i);
                GridPoint b = verts.get(i + 1);
                g.draw(new Line2D.Double(
                        a.x() * (double) gridSizePx, a.y() * (double) gridSizePx,
                        b.x() * (double) gridSizePx, b.y() * (double) gridSizePx
                ));
            }
        }

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
    }

    private static void paintFlowBubbles(Graphics2D g, List<GridPoint> verts, int gridSizePx,
                                         double coreWidth, Color bubbleColor, double phasePx) {
        double spacing = Math.max(14.0, gridSizePx * 1.6);
        double bubbleR = Math.max(1.8, coreWidth * 0.30);
        Color specWhite = new Color(255, 255, 255, 200);
        Color softBubble = new Color(bubbleColor.getRed(), bubbleColor.getGreen(), bubbleColor.getBlue(), 210);

        double accumulatedLen = 0.0;
        for (int i = 0; i < verts.size() - 1; i++) {
            GridPoint a = verts.get(i);
            GridPoint b = verts.get(i + 1);
            double x1 = a.x() * (double) gridSizePx;
            double y1 = a.y() * (double) gridSizePx;
            double x2 = b.x() * (double) gridSizePx;
            double y2 = b.y() * (double) gridSizePx;
            double segLen = Math.hypot(x2 - x1, y2 - y1);
            if (segLen < 1.0) continue;

            double ux = (x2 - x1) / segLen;
            double uy = (y2 - y1) / segLen;
            double nx = -uy;
            double ny = ux;

            double travel = ((-phasePx) % spacing + spacing) % spacing;
            for (double d = travel; d < segLen; d += spacing) {
                // Slight alternating lateral offset so bubbles look natural inside the pipe
                int idx = (int) Math.floor((accumulatedLen + d) / spacing);
                double lat = ((idx & 1) == 0 ? 1.0 : -1.0) * Math.max(0.8, coreWidth * 0.14);
                double bx = x1 + ux * d + nx * lat;
                double by = y1 + uy * d + ny * lat;
                double r = ((idx % 3) == 0) ? bubbleR * 1.15 : bubbleR * 0.85;

                Shape bubble = new Ellipse2D.Double(bx - r, by - r, r * 2.0, r * 2.0);
                g.setColor(softBubble);
                g.fill(bubble);
                g.setColor(specWhite);
                g.fill(new Ellipse2D.Double(bx - r * 0.45, by - r * 0.45, r * 0.7, r * 0.7));
            }
            accumulatedLen += segLen;
        }
    }

    private static void paintHorizontalWall(Graphics2D g, double x, double y, double w, double h) {
        Rectangle2D.Double rect = new Rectangle2D.Double(x, y, w, h);
        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(x, y),
                new Point2D.Double(x, y + Math.max(1.0, h)),
                ScadaPalette.STANDARD_FRACTIONS,
                Material.PIPE_WALL.getStops()
        ));
        g.fill(rect);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(OUTLINE_STROKE);
        g.draw(new Line2D.Double(x, y, x + w, y));
        g.draw(new Line2D.Double(x, y + h, x + w, y + h));
    }

    private static void paintVerticalWall(Graphics2D g, double x, double y, double w, double h) {
        Rectangle2D.Double rect = new Rectangle2D.Double(x, y, w, h);
        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(x, y),
                new Point2D.Double(x + Math.max(1.0, w), y),
                ScadaPalette.STANDARD_FRACTIONS,
                Material.PIPE_WALL.getStops()
        ));
        g.fill(rect);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(OUTLINE_STROKE);
        g.draw(new Line2D.Double(x, y, x, y + h));
        g.draw(new Line2D.Double(x + w, y, x + w, y + h));
    }

    private static void paintElbowCollar(Graphics2D g, double cx, double cy, double diameterPx) {
        double collar = diameterPx + 4.0;
        double r = collar / 2.0;
        RoundRectangle2D.Double joint = new RoundRectangle2D.Double(cx - r, cy - r, collar, collar, r * 0.7, r * 0.7);
        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(cx - r, cy - r),
                new Point2D.Double(cx + r, cy + r),
                ScadaPalette.STANDARD_FRACTIONS,
                Material.FLANGE_STEEL.getStops()
        ));
        g.fill(joint);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(OUTLINE_STROKE);
        g.draw(joint);

        double inner = Math.max(3.0, diameterPx * 0.45);
        g.setColor(ScadaPalette.BEVEL_HIGHLIGHT_SOFT);
        g.draw(new Ellipse2D.Double(cx - inner / 2.0, cy - inner / 2.0, inner, inner));
    }

    private static CardinalDirection segmentDirection(GridPoint from, GridPoint to) {
        int dx = Integer.compare(to.x(), from.x());
        int dy = Integer.compare(to.y(), from.y());
        if (dx > 0) return CardinalDirection.EAST;
        if (dx < 0) return CardinalDirection.WEST;
        if (dy > 0) return CardinalDirection.SOUTH;
        if (dy < 0) return CardinalDirection.NORTH;
        return null;
    }
}
