package cz.maramat.scada.render;

import cz.maramat.scada.model.CardinalDirection;
import cz.maramat.scada.model.ConnectionType;
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

        if (pipe.getConnectionType() == ConnectionType.ELECTRICAL_POWER) {
            paintWire(g, verts, gridSizePx, ConnectionType.ELECTRICAL_POWER, simActive, flowState, dashPhasePx, selected);
            return;
        }
        if (pipe.getConnectionType() == ConnectionType.CONTROL_SIGNAL) {
            paintWire(g, verts, gridSizePx, ConnectionType.CONTROL_SIGNAL, simActive, flowState, dashPhasePx, selected);
            return;
        }
        if (pipe.getConnectionType() == ConnectionType.PNEUMATIC) {
            paintWire(g, verts, gridSizePx, ConnectionType.PNEUMATIC, simActive, flowState, dashPhasePx, selected);
            return;
        }
        if (pipe.getConnectionType() == ConnectionType.CAPILLARY) {
            paintWire(g, verts, gridSizePx, ConnectionType.CAPILLARY, simActive, flowState, dashPhasePx, selected);
            return;
        }

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

            // Moving lighter water bubbles + directional chevrons along each segment
            if (flowState.flowRate() > 0.0 && fluid.getFlowDashColor() != null) {
                paintFlowBubbles(g, verts, gridSizePx, coreWidth, fluid.getFlowDashColor(),
                        dashPhasePx * Math.max(0.35, flowState.flowRate()));
                paintFlowChevrons(g, verts, gridSizePx, coreWidth, fluid.getFlowDashColor(),
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

    private static void paintFlowChevrons(Graphics2D g, List<GridPoint> verts, int gridSizePx,
                                          double coreWidth, Color chevronColor, double phasePx) {
        double spacing = Math.max(26.0, gridSizePx * 2.8);
        double arm = Math.max(3.0, coreWidth * 0.45);
        g.setColor(new Color(chevronColor.getRed(), chevronColor.getGreen(), chevronColor.getBlue(), 230));
        g.setStroke(new BasicStroke(Math.max(1.2f, (float) (coreWidth * 0.22)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        for (int i = 0; i < verts.size() - 1; i++) {
            GridPoint a = verts.get(i);
            GridPoint b = verts.get(i + 1);
            double x1 = a.x() * (double) gridSizePx;
            double y1 = a.y() * (double) gridSizePx;
            double x2 = b.x() * (double) gridSizePx;
            double y2 = b.y() * (double) gridSizePx;
            double segLen = Math.hypot(x2 - x1, y2 - y1);
            if (segLen < arm * 2.5) continue;

            double ux = (x2 - x1) / segLen;
            double uy = (y2 - y1) / segLen;
            double nx = -uy;
            double ny = ux;

            double travel = ((phasePx) % spacing + spacing) % spacing;
            for (double d = travel; d < segLen - arm; d += spacing) {
                if (d < arm) continue;
                double tipX = x1 + ux * d;
                double tipY = y1 + uy * d;
                double backX = tipX - ux * arm;
                double backY = tipY - uy * arm;
                g.draw(new Line2D.Double(backX + nx * arm * 0.75, backY + ny * arm * 0.75, tipX, tipY));
                g.draw(new Line2D.Double(backX - nx * arm * 0.75, backY - ny * arm * 0.75, tipX, tipY));
            }
        }
    }

    private static void paintHorizontalWall(Graphics2D g, double x, double y, double w, double h) {
        Rectangle2D.Double rect = new Rectangle2D.Double(x, y, w, h);
        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(x, y),
                new Point2D.Double(x, y + Math.max(1.0, h)),
                ScadaPalette.STANDARD_FRACTIONS,
                Material.PIPE_WALL.stopsRaw()
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
                Material.PIPE_WALL.stopsRaw()
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
                Material.FLANGE_STEEL.stopsRaw()
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

    private static void paintWire(Graphics2D g, List<GridPoint> verts, int gridSizePx,
                                  ConnectionType connType, boolean simActive, FlowState flowState,
                                  double dashPhasePx, boolean selected) {
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();

        boolean powerWire = connType == ConnectionType.ELECTRICAL_POWER;
        boolean pneumatic = connType == ConnectionType.PNEUMATIC;
        boolean capillary = connType == ConnectionType.CAPILLARY;

        float outerW = powerWire ? Math.max(3.5f, gridSizePx * 0.36f)
                : (pneumatic ? Math.max(2.8f, gridSizePx * 0.28f)
                : (capillary ? Math.max(1.8f, gridSizePx * 0.18f) : Math.max(2.2f, gridSizePx * 0.22f)));
        float innerW = Math.max(1.2f, outerW * 0.52f);

        Color sheathColor = switch (connType) {
            case ELECTRICAL_POWER -> new Color(0x37, 0x47, 0x4F);
            case PNEUMATIC -> new Color(0x1E, 0x4B, 0x63);
            case CAPILLARY -> new Color(0x4A, 0x35, 0x56);
            default -> new Color(0x2E, 0x5D, 0x56);
        };
        g.setColor(sheathColor);
        g.setStroke(new BasicStroke(outerW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        drawPolyline(g, verts, gridSizePx);

        boolean flowing = simActive && flowState != null && flowState.pressurized() && flowState.flowRate() > 0.0;
        Color activeCore = switch (connType) {
            case ELECTRICAL_POWER -> new Color(0xFF, 0x98, 0x00);
            case PNEUMATIC -> new Color(0x29, 0xB6, 0xF6);
            case CAPILLARY -> new Color(0xCE, 0x93, 0xD8);
            default -> new Color(0x00, 0xE6, 0x76);
        };
        Color idleCore = switch (connType) {
            case ELECTRICAL_POWER -> new Color(0x90, 0xA4, 0xAE);
            case PNEUMATIC -> new Color(0x81, 0xD4, 0xFA);
            case CAPILLARY -> new Color(0xB3, 0x9D, 0xDB);
            default -> new Color(0x80, 0xCB, 0xC4);
        };

        if (flowing) {
            g.setColor(activeCore);
            float phase = (float) (((dashPhasePx % 14.0) + 14.0) % 14.0);
            float[] pattern = capillary ? new float[]{4.0f, 5.0f} : new float[]{8.0f, 6.0f};
            g.setStroke(new BasicStroke(innerW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0f, pattern, phase));
        } else {
            g.setColor(idleCore);
            if (capillary) {
                g.setStroke(new BasicStroke(innerW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        1.0f, new float[]{4.0f, 4.0f}, 0.0f));
            } else {
                g.setStroke(new BasicStroke(innerW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            }
        }
        drawPolyline(g, verts, gridSizePx);

        // P&ID function ticks for pneumatic (double slash) and capillary (cross ticks)
        if (pneumatic || capillary) {
            paintLineFunctionTicks(g, verts, gridSizePx, pneumatic, flowing ? activeCore : idleCore);
        }

        // Terminal dots at endpoints
        double dotR = outerW * 0.65;
        g.setColor(switch (connType) {
            case ELECTRICAL_POWER -> new Color(0xE6, 0x51, 0x00);
            case PNEUMATIC -> new Color(0x02, 0x88, 0xD1);
            case CAPILLARY -> new Color(0x8E, 0x24, 0xAA);
            default -> new Color(0x00, 0x89, 0x7B);
        });
        for (int idx : new int[]{0, verts.size() - 1}) {
            GridPoint gp = verts.get(idx);
            g.fill(new Ellipse2D.Double(gp.x() * (double) gridSizePx - dotR,
                    gp.y() * (double) gridSizePx - dotR, dotR * 2.0, dotR * 2.0));
        }

        if (selected) {
            g.setColor(ScadaPalette.SELECTION_BLUE);
            g.setStroke(new BasicStroke(outerW + 3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            drawPolyline(g, verts, gridSizePx);
        }

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
    }

    private static void paintLineFunctionTicks(Graphics2D g, List<GridPoint> verts, int gridSizePx,
                                               boolean pneumatic, Color tickColor) {
        double spacing = Math.max(18.0, gridSizePx * 2.0);
        double tickHalf = Math.max(3.0, gridSizePx * 0.35);
        g.setColor(tickColor);
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < verts.size() - 1; i++) {
            GridPoint a = verts.get(i);
            GridPoint b = verts.get(i + 1);
            double x1 = a.x() * (double) gridSizePx;
            double y1 = a.y() * (double) gridSizePx;
            double x2 = b.x() * (double) gridSizePx;
            double y2 = b.y() * (double) gridSizePx;
            double segLen = Math.hypot(x2 - x1, y2 - y1);
            if (segLen < spacing * 0.6) continue;
            double ux = (x2 - x1) / segLen;
            double uy = (y2 - y1) / segLen;
            double nx = -uy;
            double ny = ux;
            for (double d = spacing * 0.5; d < segLen - spacing * 0.3; d += spacing) {
                double cx = x1 + ux * d;
                double cy = y1 + uy * d;
                if (pneumatic) {
                    // Double slash //
                    for (double off : new double[]{-2.0, 2.0}) {
                        double sx = cx + ux * off;
                        double sy = cy + uy * off;
                        g.draw(new Line2D.Double(sx - ux * 2.0 - nx * tickHalf, sy - uy * 2.0 - ny * tickHalf,
                                sx + ux * 2.0 + nx * tickHalf, sy + uy * 2.0 + ny * tickHalf));
                    }
                } else {
                    // Cross tick X
                    g.draw(new Line2D.Double(cx - ux * 2.5 - nx * tickHalf, cy - uy * 2.5 - ny * tickHalf,
                            cx + ux * 2.5 + nx * tickHalf, cy + uy * 2.5 + ny * tickHalf));
                    g.draw(new Line2D.Double(cx + ux * 2.5 - nx * tickHalf, cy + uy * 2.5 - ny * tickHalf,
                            cx - ux * 2.5 + nx * tickHalf, cy - uy * 2.5 + ny * tickHalf));
                }
            }
        }
    }

    private static void drawPolyline(Graphics2D g, List<GridPoint> verts, int gridSizePx) {
        for (int i = 0; i < verts.size() - 1; i++) {
            GridPoint a = verts.get(i);
            GridPoint b = verts.get(i + 1);
            g.draw(new Line2D.Double(
                    a.x() * (double) gridSizePx, a.y() * (double) gridSizePx,
                    b.x() * (double) gridSizePx, b.y() * (double) gridSizePx
            ));
        }
    }
}
