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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3-pass 3D multi-pass renderer for orthogonal ScadaPipes:
 * Pass 1: 3D cylindrical horizontal/vertical pipe segments.
 * Pass 2: 3D mitered/rounded elbows, tees, and bolted port flanges.
 * Pass 3: Animated water/fluid cutaway with moving lighter bubbles (only when simulation is running/paused).
 */
public final class PipeRenderer {

    private static final BasicStroke OUTLINE_STROKE = new BasicStroke(1.0f);
    private static final BasicStroke SELECTED_STROKE = new BasicStroke(2.0f);
    private static final BasicStroke TICK_STROKE = new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Color PIPE_SELECTED_COLOR = new Color(0x00, 0x78, 0xD7, 190);
    private static final float[] WIRE_DASH_CAPILLARY = new float[]{4.0f, 5.0f};
    private static final float[] WIRE_DASH_DEFAULT = new float[]{8.0f, 6.0f};
    private static final float[] WIRE_IDLE_CAPILLARY = new float[]{4.0f, 4.0f};

    private static final Map<Integer, BasicStroke> ROUND_STROKE_CACHE = new ConcurrentHashMap<>();

    private PipeRenderer() {
    }

    private static BasicStroke getRoundStroke(float width) {
        int wq = Math.max(5, Math.round(width * 10.0f));
        BasicStroke cached = ROUND_STROKE_CACHE.get(wq);
        if (cached != null) return cached;
        if (ROUND_STROKE_CACHE.size() > 128) {
            ROUND_STROKE_CACHE.clear();
        }
        return ROUND_STROKE_CACHE.computeIfAbsent(wq,
                k -> new BasicStroke(k / 10.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    public static void paintPipe(Graphics2D g, ScadaPipe pipe, int gridSizePx,
                                 FlowState flowState, boolean simActive,
                                 double dashPhasePx, boolean selected) {
        paintPipeStatic(g, pipe, gridSizePx);
        paintPipeDynamic(g, pipe, gridSizePx, flowState, simActive, dashPhasePx, selected);
    }

    /**
     * Paints the static, topology-only pipe shell (cylindrical walls, elbow collars, flanges,
     * or wire outer sheath). Suitable for caching in an offscreen screen-wide layer.
     */
    public static void paintPipeStatic(Graphics2D g, ScadaPipe pipe, int gridSizePx) {
        if (pipe == null) return;
        List<GridPoint> verts = pipe.getVertices();
        if (verts.size() < 2) return;

        ConnectionType connType = pipe.getConnectionType();
        if (isWireType(connType)) {
            Paint oldPaint = g.getPaint();
            Stroke oldStroke = g.getStroke();
            float outerW = wireOuterWidth(connType, gridSizePx);
            g.setColor(wireSheathColor(connType));
            g.setStroke(getRoundStroke(outerW));
            drawPolyline(g, verts, gridSizePx);
            g.setPaint(oldPaint);
            g.setStroke(oldStroke);
            return;
        }

        double diameterPx = Math.max(3.0, pipe.getDiameter().getGridUnits() * gridSizePx);
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

        // Pass 2: Seamless rounded 90-degree elbows; optional end flanges
        for (int i = 1; i < verts.size() - 1; i++) {
            GridPoint v = verts.get(i);
            double cx = v.x() * (double) gridSizePx;
            double cy = v.y() * (double) gridSizePx;
            paintSeamlessElbow(g, cx, cy, diameterPx);
        }

        if (pipe.isShowFlanges()) {
            GridPoint first = verts.getFirst();
            GridPoint second = verts.get(1);
            CardinalDirection startDir = segmentDirection(first, second);
            if (startDir != null) {
                ScadaShader.paintFlange(g, first.x() * (double) gridSizePx, first.y() * (double) gridSizePx,
                        diameterPx + 4.0, Math.max(3.0, gridSizePx * 0.35), startDir);
            }
            GridPoint last = verts.getLast();
            GridPoint prevLast = verts.get(verts.size() - 2);
            CardinalDirection endDir = segmentDirection(last, prevLast);
            if (endDir != null) {
                ScadaShader.paintFlange(g, last.x() * (double) gridSizePx, last.y() * (double) gridSizePx,
                        diameterPx + 4.0, Math.max(3.0, gridSizePx * 0.35), endDir);
            }
        }

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
    }

    /**
     * Paints only the dynamic per-frame pipe content (fluid infill, bubbles, chevrons,
     * wire core, and selection highlight).
     */
    public static void paintPipeDynamic(Graphics2D g, ScadaPipe pipe, int gridSizePx,
                                        FlowState flowState, boolean simActive,
                                        double dashPhasePx, boolean selected) {
        paintPipePressurizedFluid(g, pipe, gridSizePx, flowState, simActive);
        paintPipeFlowParticles(g, pipe, gridSizePx, flowState, simActive, dashPhasePx, selected);
    }

    /**
     * Paints the stationary energized/pressurized state of a pipe (fluid infill core & sheen,
     * or idle/unflowing wire core, ticks, and endpoint dots). Suitable for baking into
     * an offscreen static layer whenever flow state changes.
     */
    public static void paintPipePressurizedFluid(Graphics2D g, ScadaPipe pipe, int gridSizePx,
                                                 FlowState flowState, boolean simActive) {
        if (pipe == null) return;
        List<GridPoint> verts = pipe.getVertices();
        if (verts.size() < 2) return;

        ConnectionType connType = pipe.getConnectionType();
        if (isWireType(connType)) {
            boolean flowing = simActive && flowState != null && flowState.pressurized() && flowState.flowRate() > 0.0;
            if (!flowing) {
                paintWireStaticCore(g, verts, gridSizePx, connType);
            }
            return;
        }

        FluidType fluid = (flowState != null && flowState.fluidType() != null)
                ? flowState.fluidType()
                : pipe.getFluidType();
        boolean drawFluid = simActive && fluid != FluidType.EMPTY && flowState != null && flowState.pressurized();
        if (!drawFluid) {
            return;
        }

        double diameterPx = Math.max(3.0, pipe.getDiameter().getGridUnits() * gridSizePx);
        double coreWidth = Math.max(2.0, diameterPx * 0.52);
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();

        Color custom = parseHexColor(pipe.getCustomColorHex());
        Color baseColor = (custom != null) ? custom : fluid.getBaseColor();
        Color hl = (custom != null) ? custom.brighter() : fluid.getHighlightColor();

        g.setColor(baseColor);
        g.setStroke(getRoundStroke((float) coreWidth));
        drawPolyline(g, verts, gridSizePx);

        g.setColor(new Color(hl.getRed(), hl.getGreen(), hl.getBlue(), 140));
        g.setStroke(getRoundStroke((float) Math.max(1.0, coreWidth * 0.42)));
        drawPolyline(g, verts, gridSizePx);

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
    }

    /**
     * Paints only the time-varying flow particles (moving bubbles, chevrons, animated wire dashes)
     * and selection highlight on top of {@link #paintPipeStatic} + {@link #paintPipePressurizedFluid}.
     */
    public static void paintPipeFlowParticles(Graphics2D g, ScadaPipe pipe, int gridSizePx,
                                              FlowState flowState, boolean simActive,
                                              double dashPhasePx, boolean selected) {
        if (pipe == null) return;
        List<GridPoint> verts = pipe.getVertices();
        if (verts.size() < 2) return;

        ConnectionType connType = pipe.getConnectionType();
        if (isWireType(connType)) {
            paintWireAnimatedOrSelected(g, verts, gridSizePx, connType, simActive, flowState, dashPhasePx, selected);
            paintPipeLabel(g, pipe, verts, gridSizePx);
            return;
        }

        FluidType fluid = (flowState != null && flowState.fluidType() != null)
                ? flowState.fluidType()
                : pipe.getFluidType();
        boolean drawFlow = simActive && fluid != FluidType.EMPTY
                && flowState != null && flowState.pressurized() && flowState.flowRate() > 0.0
                && fluid.getFlowDashColor() != null;

        double diameterPx = Math.max(3.0, pipe.getDiameter().getGridUnits() * gridSizePx);
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();

        if (drawFlow) {
            double coreWidth = Math.max(2.0, diameterPx * 0.52);
            double effPhase = dashPhasePx * Math.max(0.35, flowState.flowRate());
            int dir = (flowState.direction() < 0) ? -1 : 1;
            paintFlowChevrons(g, verts, gridSizePx, coreWidth, fluid.getFlowDashColor(), effPhase, dir);
        }

        if (selected) {
            g.setColor(PIPE_SELECTED_COLOR);
            g.setStroke(SELECTED_STROKE);
            drawPolyline(g, verts, gridSizePx);
        }

        paintPipeLabel(g, pipe, verts, gridSizePx);

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
    }

    private static boolean isWireType(ConnectionType connType) {
        return connType == ConnectionType.ELECTRICAL_POWER
                || connType == ConnectionType.CONTROL_SIGNAL
                || connType == ConnectionType.PNEUMATIC
                || connType == ConnectionType.CAPILLARY;
    }

    private static float wireOuterWidth(ConnectionType connType, int gridSizePx) {
        return switch (connType) {
            case ELECTRICAL_POWER -> Math.max(3.5f, gridSizePx * 0.36f);
            case PNEUMATIC -> Math.max(2.8f, gridSizePx * 0.28f);
            case CAPILLARY -> Math.max(1.8f, gridSizePx * 0.18f);
            default -> Math.max(2.2f, gridSizePx * 0.22f);
        };
    }

    private static Color wireSheathColor(ConnectionType connType) {
        return switch (connType) {
            case ELECTRICAL_POWER -> new Color(0x37, 0x47, 0x4F);
            case PNEUMATIC -> new Color(0x1E, 0x4B, 0x63);
            case CAPILLARY -> new Color(0x4A, 0x35, 0x56);
            default -> new Color(0x2E, 0x5D, 0x56);
        };
    }

    private static void paintFlowChevrons(Graphics2D g, List<GridPoint> verts, int gridSizePx,
                                          double coreWidth, Color chevronColor, double phasePx, int direction) {
        double spacing = Math.max(24.0, gridSizePx * 2.6);
        double arm = Math.max(2.2, coreWidth * 0.42);
        g.setColor(new Color(chevronColor.getRed(), chevronColor.getGreen(), chevronColor.getBlue(), 230));
        g.setStroke(getRoundStroke(Math.max(1.0f, (float) (coreWidth * 0.22))));

        Path2D.Double path = null;
        int segCount = verts.size() - 1;
        for (int s = 0; s < segCount; s++) {
            int i = (direction >= 0) ? s : (segCount - 1 - s);
            GridPoint a = (direction >= 0) ? verts.get(i) : verts.get(i + 1);
            GridPoint b = (direction >= 0) ? verts.get(i + 1) : verts.get(i);
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

            double travel = ((-phasePx) % spacing + spacing) % spacing;
            for (double d = travel; d < segLen - arm; d += spacing) {
                if (d < arm) continue;
                if (path == null) {
                    path = new Path2D.Double();
                }
                double tipX = x1 + ux * d;
                double tipY = y1 + uy * d;
                double backX = tipX - ux * arm;
                double backY = tipY - uy * arm;
                path.moveTo(backX + nx * arm * 0.75, backY + ny * arm * 0.75);
                path.lineTo(tipX, tipY);
                path.lineTo(backX - nx * arm * 0.75, backY - ny * arm * 0.75);
            }
        }
        if (path != null) {
            g.draw(path);
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

    private static void paintSeamlessElbow(Graphics2D g, double cx, double cy, double diameterPx) {
        double r = diameterPx / 2.0;
        Ellipse2D.Double joint = new Ellipse2D.Double(cx - r, cy - r, diameterPx, diameterPx);
        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(cx - r, cy - r),
                new Point2D.Double(cx + r, cy + r),
                ScadaPalette.STANDARD_FRACTIONS,
                Material.PIPE_WALL.stopsRaw()
        ));
        g.fill(joint);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(OUTLINE_STROKE);
        g.draw(joint);
    }

    private static void paintPipeLabel(Graphics2D g, ScadaPipe pipe, List<GridPoint> verts, int gridSizePx) {
        if (!pipe.isShowLabel() || pipe.getLabel() == null || pipe.getLabel().isBlank() || verts.size() < 2) {
            return;
        }
        // Find longest segment midpoint
        double bestLen = -1;
        double mx = 0;
        double my = 0;
        boolean horizontal = true;
        for (int i = 0; i < verts.size() - 1; i++) {
            GridPoint a = verts.get(i);
            GridPoint b = verts.get(i + 1);
            double x1 = a.x() * (double) gridSizePx;
            double y1 = a.y() * (double) gridSizePx;
            double x2 = b.x() * (double) gridSizePx;
            double y2 = b.y() * (double) gridSizePx;
            double len = Math.hypot(x2 - x1, y2 - y1);
            if (len > bestLen) {
                bestLen = len;
                mx = (x1 + x2) * 0.5;
                my = (y1 + y2) * 0.5;
                horizontal = Math.abs(x2 - x1) >= Math.abs(y2 - y1);
            }
        }
        if (bestLen < 12.0) return;

        Font oldFont = g.getFont();
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(9, gridSizePx - 1)));
        FontMetrics fm = g.getFontMetrics();
        String text = pipe.getLabel();
        int tw = fm.stringWidth(text);
        int th = fm.getHeight();
        int padX = 4;
        int padY = 1;
        int bx = (int) Math.round(mx - tw * 0.5) - padX;
        int by = horizontal
                ? (int) Math.round(my - th - Math.max(4, pipe.getDiameter().getGridUnits() * gridSizePx * 0.55))
                : (int) Math.round(my - th * 0.5);
        g.setColor(new Color(0x16, 0x1A, 0x20, 210));
        g.fillRoundRect(bx, by, tw + padX * 2, th + padY * 2, 5, 5);
        g.setColor(new Color(0x45, 0x52, 0x62));
        g.setStroke(OUTLINE_STROKE);
        g.drawRoundRect(bx, by, tw + padX * 2, th + padY * 2, 5, 5);
        g.setColor(new Color(0xE8, 0xEE, 0xF5));
        g.drawString(text, bx + padX, by + padY + fm.getAscent());
        g.setFont(oldFont);
    }

    private static Color parseHexColor(String hex) {
        if (hex == null || hex.isBlank()) return null;
        String clean = hex.trim();
        if (clean.startsWith("#")) clean = clean.substring(1);
        if (clean.length() != 6) return null;
        try {
            return new Color(Integer.parseInt(clean, 16));
        } catch (NumberFormatException ex) {
            return null;
        }
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

    private static void paintWireStaticCore(Graphics2D g, List<GridPoint> verts, int gridSizePx, ConnectionType connType) {
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();

        boolean pneumatic = connType == ConnectionType.PNEUMATIC;
        boolean capillary = connType == ConnectionType.CAPILLARY;
        float outerW = wireOuterWidth(connType, gridSizePx);
        float innerW = Math.max(1.2f, outerW * 0.52f);

        Color idleCore = wireCoreColor(connType, false);
        g.setColor(idleCore);
        if (capillary) {
            g.setStroke(new BasicStroke(innerW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1.0f, WIRE_IDLE_CAPILLARY, 0.0f));
        } else {
            g.setStroke(getRoundStroke(innerW));
        }
        drawPolyline(g, verts, gridSizePx);

        if (pneumatic || capillary) {
            paintLineFunctionTicks(g, verts, gridSizePx, pneumatic, idleCore);
        }
        paintWireTerminalDots(g, verts, gridSizePx, connType, outerW);

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
    }

    private static void paintWireAnimatedOrSelected(Graphics2D g, List<GridPoint> verts, int gridSizePx,
                                                    ConnectionType connType, boolean simActive, FlowState flowState,
                                                    double dashPhasePx, boolean selected) {
        boolean flowing = simActive && flowState != null && flowState.pressurized() && flowState.flowRate() > 0.0;
        if (!flowing && !selected) {
            return;
        }
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();

        boolean pneumatic = connType == ConnectionType.PNEUMATIC;
        boolean capillary = connType == ConnectionType.CAPILLARY;
        float outerW = wireOuterWidth(connType, gridSizePx);
        float innerW = Math.max(1.2f, outerW * 0.52f);

        if (flowing) {
            Color activeCore = wireCoreColor(connType, true);
            g.setColor(activeCore);
            int dir = (flowState != null && flowState.direction() < 0) ? -1 : 1;
            float phase = (float) ((((dashPhasePx * dir) % 14.0) + 14.0) % 14.0);
            float[] pattern = capillary ? WIRE_DASH_CAPILLARY : WIRE_DASH_DEFAULT;
            g.setStroke(new BasicStroke(innerW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0f, pattern, phase));
            drawPolyline(g, verts, gridSizePx);

            if (pneumatic || capillary) {
                paintLineFunctionTicks(g, verts, gridSizePx, pneumatic, activeCore);
            }
            paintWireTerminalDots(g, verts, gridSizePx, connType, outerW);
        }

        if (selected) {
            g.setColor(ScadaPalette.SELECTION_BLUE);
            g.setStroke(getRoundStroke(outerW + 3.0f));
            drawPolyline(g, verts, gridSizePx);
        }

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
    }

    private static Color wireCoreColor(ConnectionType connType, boolean active) {
        if (active) {
            return switch (connType) {
                case ELECTRICAL_POWER -> new Color(0xFF, 0x98, 0x00);
                case PNEUMATIC -> new Color(0x29, 0xB6, 0xF6);
                case CAPILLARY -> new Color(0xCE, 0x93, 0xD8);
                default -> new Color(0x00, 0xE6, 0x76);
            };
        }
        return switch (connType) {
            case ELECTRICAL_POWER -> new Color(0x90, 0xA4, 0xAE);
            case PNEUMATIC -> new Color(0x81, 0xD4, 0xFA);
            case CAPILLARY -> new Color(0xB3, 0x9D, 0xDB);
            default -> new Color(0x80, 0xCB, 0xC4);
        };
    }

    private static void paintWireTerminalDots(Graphics2D g, List<GridPoint> verts, int gridSizePx,
                                              ConnectionType connType, float outerW) {
        double dotR = outerW * 0.65;
        g.setColor(switch (connType) {
            case ELECTRICAL_POWER -> new Color(0xE6, 0x51, 0x00);
            case PNEUMATIC -> new Color(0x02, 0x88, 0xD1);
            case CAPILLARY -> new Color(0x8E, 0x24, 0xAA);
            default -> new Color(0x00, 0x89, 0x7B);
        });
        Ellipse2D.Double dot = new Ellipse2D.Double();
        for (int k = 0; k < 2; k++) {
            GridPoint gp = verts.get(k == 0 ? 0 : verts.size() - 1);
            dot.setFrame(gp.x() * (double) gridSizePx - dotR,
                    gp.y() * (double) gridSizePx - dotR, dotR * 2.0, dotR * 2.0);
            g.fill(dot);
        }
    }

    private static void paintLineFunctionTicks(Graphics2D g, List<GridPoint> verts, int gridSizePx,
                                               boolean pneumatic, Color tickColor) {
        double spacing = Math.max(18.0, gridSizePx * 2.0);
        double tickHalf = Math.max(3.0, gridSizePx * 0.35);
        g.setColor(tickColor);
        g.setStroke(TICK_STROKE);
        Path2D.Double path = null;
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
                if (path == null) {
                    path = new Path2D.Double();
                }
                double cx = x1 + ux * d;
                double cy = y1 + uy * d;
                if (pneumatic) {
                    for (int s = -1; s <= 1; s += 2) {
                        double off = s * 2.0;
                        double sx = cx + ux * off;
                        double sy = cy + uy * off;
                        path.moveTo(sx - ux * 2.0 - nx * tickHalf, sy - uy * 2.0 - ny * tickHalf);
                        path.lineTo(sx + ux * 2.0 + nx * tickHalf, sy + uy * 2.0 + ny * tickHalf);
                    }
                } else {
                    path.moveTo(cx - ux * 2.5 - nx * tickHalf, cy - uy * 2.5 - ny * tickHalf);
                    path.lineTo(cx + ux * 2.5 + nx * tickHalf, cy + uy * 2.5 + ny * tickHalf);
                    path.moveTo(cx + ux * 2.5 - nx * tickHalf, cy + uy * 2.5 - ny * tickHalf);
                    path.lineTo(cx - ux * 2.5 + nx * tickHalf, cy - uy * 2.5 + ny * tickHalf);
                }
            }
        }
        if (path != null) {
            g.draw(path);
        }
    }

    private static void drawPolyline(Graphics2D g, List<GridPoint> verts, int gridSizePx) {
        int n = verts.size();
        if (n < 2) return;
        if (n == 2) {
            GridPoint a = verts.get(0);
            GridPoint b = verts.get(1);
            g.draw(new Line2D.Double(
                    a.x() * (double) gridSizePx, a.y() * (double) gridSizePx,
                    b.x() * (double) gridSizePx, b.y() * (double) gridSizePx
            ));
            return;
        }
        Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO, n);
        GridPoint first = verts.get(0);
        path.moveTo(first.x() * (double) gridSizePx, first.y() * (double) gridSizePx);
        for (int i = 1; i < n; i++) {
            GridPoint pt = verts.get(i);
            path.lineTo(pt.x() * (double) gridSizePx, pt.y() * (double) gridSizePx);
        }
        g.draw(path);
    }
}
