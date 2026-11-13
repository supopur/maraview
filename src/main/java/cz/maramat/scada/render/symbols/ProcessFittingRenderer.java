package cz.maramat.scada.render.symbols;

import cz.maramat.scada.model.CardinalDirection;
import cz.maramat.scada.model.FluidType;
import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.render.EquipmentState;
import cz.maramat.scada.render.ScadaPalette;
import cz.maramat.scada.render.ScadaPalette.Material;
import cz.maramat.scada.render.ScadaShader;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * 3D procedural renderers for pipe fittings, flow elements, and process equipment
 * (mixers/agitators, separators, cyclones, scrubbers, screens).
 */
public final class ProcessFittingRenderer {

    private ProcessFittingRenderer() {
    }

    public static boolean paint(Graphics2D g, ScadaSymbolInstance sym, int w, int h, int gPx,
                                EquipmentState state, FluidType fluid, long timeMs) {
        String type = sym.getSymbolType();
        return switch (type) {
            // Fittings
            case "TeeFitting" -> { paintTee(g, w, h, gPx); yield true; }
            case "ElbowFitting" -> { paintElbow(g, w, h, gPx); yield true; }
            case "ReducerFitting" -> { paintReducer(g, w, h); yield true; }
            case "FlangeFitting" -> { paintFlange(g, w, h); yield true; }
            case "Strainer" -> { paintStrainer(g, w, h, gPx); yield true; }
            case "ProcessFilter" -> { paintProcessFilter(g, w, h); yield true; }
            case "SteamTrap" -> { paintSteamTrap(g, w, h); yield true; }
            case "SightGlass" -> { paintSightGlass(g, w, h, fluid); yield true; }
            case "ExpansionJoint" -> { paintExpansionJoint(g, w, h); yield true; }
            case "Coupling" -> { paintCoupling(g, w, h); yield true; }
            // Flow elements
            case "OrificePlate" -> { paintOrificePlate(g, w, h); yield true; }
            case "VenturiTube" -> { paintVenturiTube(g, w, h); yield true; }
            case "MagneticFlowMeter" -> { paintFlowTransmitter(g, w, h, "MAG", new Color(0x02, 0x88, 0xD1), state); yield true; }
            case "UltrasonicFlowMeter" -> { paintFlowTransmitter(g, w, h, "US", new Color(0x00, 0x89, 0x7B), state); yield true; }
            case "CoriolisFlowMeter" -> { paintCoriolis(g, w, h, state); yield true; }
            case "Rotameter" -> { paintRotameter(g, w, h, state, timeMs); yield true; }
            // Process equipment
            case "MixerAgitator" -> { paintMixerAgitator(g, w, h, state, timeMs); yield true; }
            case "Separator" -> { paintSeparator(g, w, h, fluid); yield true; }
            case "Cyclone" -> { paintCyclone(g, w, h, state, timeMs); yield true; }
            case "Scrubber" -> { paintScrubber(g, w, h, state, timeMs); yield true; }
            case "ProcessScreen" -> { paintProcessScreen(g, w, h); yield true; }
            default -> false;
        };
    }

    private static void paintTee(Graphics2D g, int w, int h, int gPx) {
        double d = Math.max(8.0, Math.min(w, h) * 0.34);
        ScadaShader.paintCylinderH(g, 0, (h - d) / 2.0, w, d, Material.PIPE_WALL);
        ScadaShader.paintCylinderV(g, (w - d) / 2.0, h * 0.50, d, h * 0.50, Material.PIPE_WALL);
        ScadaShader.paintBeveledHousing(g, (w - d - 4) / 2.0, (h - d - 4) / 2.0, d + 4, d + 4, 3.0, Material.FLANGE_STEEL);
    }

    private static void paintElbow(Graphics2D g, int w, int h, int gPx) {
        double d = Math.max(8.0, Math.min(w, h) * 0.34);
        ScadaShader.paintCylinderH(g, 0, (h - d) / 2.0, w * 0.58, d, Material.PIPE_WALL);
        ScadaShader.paintCylinderV(g, (w - d) / 2.0, h * 0.42, d, h * 0.58, Material.PIPE_WALL);
        ScadaShader.paintBeveledHousing(g, (w - d - 4) / 2.0, (h - d - 4) / 2.0, d + 4, d + 4, 4.0, Material.FLANGE_STEEL);
    }

    private static void paintReducer(Graphics2D g, int w, int h) {
        Path2D.Double body = new Path2D.Double();
        body.moveTo(2, h * 0.22);
        body.lineTo(w - 2, h * 0.36);
        body.lineTo(w - 2, h * 0.64);
        body.lineTo(2, h * 0.78);
        body.closePath();
        g.setColor(new Color(0x7C, 0x87, 0x96));
        g.fill(body);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(body);
        ScadaShader.paintFlange(g, 3, h * 0.50, h * 0.62, 4, CardinalDirection.WEST);
        ScadaShader.paintFlange(g, w - 3, h * 0.50, h * 0.36, 4, CardinalDirection.EAST);
    }

    private static void paintFlange(Graphics2D g, int w, int h) {
        ScadaShader.paintCylinderH(g, 0, h * 0.34, w, h * 0.32, Material.PIPE_WALL);
        ScadaShader.paintFlange(g, w * 0.44, h * 0.50, h * 0.72, 4, CardinalDirection.WEST);
        ScadaShader.paintFlange(g, w * 0.56, h * 0.50, h * 0.72, 4, CardinalDirection.EAST);
    }

    private static void paintStrainer(Graphics2D g, int w, int h, int gPx) {
        double pipeH = h * 0.28;
        ScadaShader.paintCylinderH(g, 0, h * 0.40 - pipeH / 2.0, w, pipeH, Material.CAST_IRON);
        // Diagonal Y-strainer leg
        Path2D.Double leg = new Path2D.Double();
        leg.moveTo(w * 0.38, h * 0.46);
        leg.lineTo(w * 0.56, h * 0.46);
        leg.lineTo(w * 0.72, h * 0.92);
        leg.lineTo(w * 0.54, h * 0.92);
        leg.closePath();
        g.setColor(new Color(0x63, 0x6D, 0x7A));
        g.fill(leg);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(leg);
    }

    private static void paintProcessFilter(Graphics2D g, int w, int h) {
        ScadaShader.paintCylinderH(g, 0, h * 0.25, w, h * 0.20, Material.PIPE_WALL);
        ScadaShader.paintCylinderV(g, w * 0.24, h * 0.16, w * 0.52, h * 0.76, Material.STAINLESS_STEEL);
        // Filter cartridge dashed line inside
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1.0f, new float[]{3.0f, 3.0f}, 0.0f));
        g.draw(new Line2D.Double(w * 0.34, h * 0.26, w * 0.34, h * 0.84));
        g.draw(new Line2D.Double(w * 0.66, h * 0.26, w * 0.66, h * 0.84));
    }

    private static void paintSteamTrap(Graphics2D g, int w, int h) {
        ScadaShader.paintCylinderH(g, 0, h * 0.38, w, h * 0.24, Material.PIPE_WALL);
        double d = Math.min(w, h) * 0.56;
        ScadaShader.paintVoluteCasing(g, w * 0.50, h * 0.50, d * 0.5, d * 0.36, Material.CAST_IRON);
        drawBadge(g, "T", w * 0.50, h * 0.58, Color.WHITE, 9);
    }

    private static void paintSightGlass(Graphics2D g, int w, int h, FluidType fluid) {
        ScadaShader.paintCylinderH(g, 0, h * 0.34, w, h * 0.32, Material.FLANGE_STEEL);
        Rectangle2D.Double glass = new Rectangle2D.Double(w * 0.24, h * 0.24, w * 0.52, h * 0.52);
        g.setColor(fluid != FluidType.EMPTY ? fluid.getBaseColor() : ScadaPalette.CUTOUT_DARK);
        g.fill(glass);
        g.setColor(new Color(255, 255, 255, 110));
        g.fill(new Rectangle2D.Double(w * 0.28, h * 0.30, w * 0.44, h * 0.12));
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(glass);
    }

    private static void paintExpansionJoint(Graphics2D g, int w, int h) {
        ScadaShader.paintFlange(g, 4, h * 0.50, h * 0.68, 4, CardinalDirection.WEST);
        ScadaShader.paintFlange(g, w - 4, h * 0.50, h * 0.68, 4, CardinalDirection.EAST);
        // Corrugated bellows ribs
        int ribs = 4;
        double startX = w * 0.18;
        double ribW = (w * 0.64) / ribs;
        for (int i = 0; i < ribs; i++) {
            ScadaShader.paintCylinderV(g, startX + i * ribW, h * 0.22, ribW * 0.85, h * 0.56, Material.STAINLESS_STEEL);
        }
    }

    private static void paintCoupling(Graphics2D g, int w, int h) {
        ScadaShader.paintCylinderH(g, 0, h * 0.34, w, h * 0.32, Material.PIPE_WALL);
        ScadaShader.paintBeveledHousing(g, w * 0.30, h * 0.20, w * 0.40, h * 0.60, 3.0, Material.CAST_IRON);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(new Line2D.Double(w * 0.50, h * 0.22, w * 0.50, h * 0.78));
    }

    private static void paintOrificePlate(Graphics2D g, int w, int h) {
        ScadaShader.paintCylinderH(g, 0, h * 0.44, w, h * 0.32, Material.PIPE_WALL);
        ScadaShader.paintFlange(g, w * 0.50, h * 0.60, h * 0.52, 5, CardinalDirection.WEST);
        // Orifice restriction + transmitter head
        ScadaShader.paintCylinderV(g, w * 0.46, h * 0.20, w * 0.08, h * 0.24, Material.FLANGE_STEEL);
        ScadaShader.paintBeveledHousing(g, w * 0.32, h * 0.04, w * 0.36, h * 0.20, 3.0, Material.CAST_IRON);
        drawBadge(g, "FE", w * 0.50, h * 0.17, Color.WHITE, 7);
    }

    private static void paintVenturiTube(Graphics2D g, int w, int h) {
        Path2D.Double venturi = new Path2D.Double();
        venturi.moveTo(2, h * 0.40);
        venturi.lineTo(w * 0.35, h * 0.50);
        venturi.lineTo(w * 0.65, h * 0.50);
        venturi.lineTo(w - 2, h * 0.40);
        venturi.lineTo(w - 2, h * 0.80);
        venturi.lineTo(w * 0.65, h * 0.70);
        venturi.lineTo(w * 0.35, h * 0.70);
        venturi.lineTo(2, h * 0.80);
        venturi.closePath();
        g.setColor(new Color(0x7C, 0x87, 0x96));
        g.fill(venturi);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(venturi);
        ScadaShader.paintBeveledHousing(g, w * 0.36, h * 0.06, w * 0.28, h * 0.22, 3.0, Material.CAST_IRON);
        drawBadge(g, "dP", w * 0.50, h * 0.20, Color.WHITE, 7);
    }

    private static void paintFlowTransmitter(Graphics2D g, int w, int h, String label, Color accent, EquipmentState state) {
        ScadaShader.paintCylinderH(g, 0, h * 0.46, w, h * 0.28, Material.STAINLESS_STEEL);
        ScadaShader.paintCylinderV(g, w * 0.44, h * 0.28, w * 0.12, h * 0.18, Material.FLANGE_STEEL);
        ScadaShader.paintBeveledHousing(g, w * 0.22, h * 0.04, w * 0.56, h * 0.28, 4.0, Material.CAST_IRON);
        g.setColor(accent);
        g.fill(new Rectangle2D.Double(w * 0.26, h * 0.08, w * 0.48, h * 0.20));
        drawBadge(g, label, w * 0.50, h * 0.22, Color.WHITE, 8);
        ScadaShader.paintLedIndicator(g, w * 0.72, h * 0.60, 2.5,
                state == EquipmentState.RUNNING ? ScadaPalette.STATE_RUNNING_LED : ScadaPalette.STATE_STOPPED_LED,
                state == EquipmentState.RUNNING);
    }

    private static void paintCoriolis(Graphics2D g, int w, int h, EquipmentState state) {
        ScadaShader.paintCylinderH(g, 0, h * 0.44, w, h * 0.24, Material.STAINLESS_STEEL);
        // Twin U-loop sensor tubes below
        g.setColor(ScadaPalette.BEVEL_HIGHLIGHT);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new Ellipse2D.Double(w * 0.25, h * 0.44, w * 0.50, h * 0.46));
        paintFlowTransmitter(g, w, h, "COR", new Color(0x5E, 0x35, 0xB1), state);
    }

    private static void paintRotameter(Graphics2D g, int w, int h, EquipmentState state, long timeMs) {
        // Tapered vertical glass tube
        Path2D.Double tube = new Path2D.Double();
        tube.moveTo(w * 0.24, h * 0.08);
        tube.lineTo(w * 0.76, h * 0.08);
        tube.lineTo(w * 0.64, h * 0.92);
        tube.lineTo(w * 0.36, h * 0.92);
        tube.closePath();
        g.setColor(ScadaPalette.CUTOUT_DARK);
        g.fill(tube);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(tube);
        // Animated float bob
        double bobFrac = (state == EquipmentState.RUNNING)
                ? 0.35 + 0.12 * Math.sin(timeMs / 240.0)
                : 0.82;
        double by = h * bobFrac;
        g.setColor(new Color(0xFF, 0xB3, 0x00));
        g.fill(new Ellipse2D.Double(w * 0.40, by - 4, w * 0.20, 8));
    }

    private static void paintMixerAgitator(Graphics2D g, int w, int h, EquipmentState state, long timeMs) {
        // Top drive motor + gearbox
        ScadaShader.paintBeveledHousing(g, w * 0.26, 2, w * 0.48, h * 0.22, 4.0, Material.CAST_IRON);
        // Shaft
        ScadaShader.paintCylinderV(g, w * 0.46, h * 0.24, w * 0.08, h * 0.64, Material.STAINLESS_STEEL);
        // Impeller blades
        double phase = (state == EquipmentState.RUNNING) ? Math.sin(timeMs / 100.0) : 1.0;
        double span = Math.max(6.0, w * 0.36 * Math.abs(phase));
        g.setColor(ScadaPalette.ROTOR_BLADE_A_HIGHLIGHT);
        g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(w * 0.50 - span, h * 0.82, w * 0.50 + span, h * 0.82));
        g.draw(new Line2D.Double(w * 0.50 - span * 0.7, h * 0.62, w * 0.50 + span * 0.7, h * 0.62));
    }

    private static void paintSeparator(Graphics2D g, int w, int h, FluidType fluid) {
        ScadaShader.paintDishedTankHead(g, w * 0.14, 2, w * 0.72, h * 0.14, true, Material.STAINLESS_STEEL);
        ScadaShader.paintCylinderV(g, w * 0.14, 2 + h * 0.14, w * 0.72, h * 0.70, Material.STAINLESS_STEEL);
        ScadaShader.paintDishedTankHead(g, w * 0.14, h * 0.84, w * 0.72, h * 0.14, false, Material.STAINLESS_STEEL);
        // Internal demister / baffle
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1.0f, new float[]{4.0f, 3.0f}, 0.0f));
        g.draw(new Line2D.Double(w * 0.20, h * 0.35, w * 0.80, h * 0.35));
        if (fluid != FluidType.EMPTY) {
            g.setColor(fluid.getBaseColor());
            g.fill(new Rectangle2D.Double(w * 0.22, h * 0.56, w * 0.56, h * 0.24));
        }
        drawBadge(g, "SEP", w * 0.50, h * 0.50, ScadaPalette.TEXT_PRIMARY, 8);
    }

    private static void paintCyclone(Graphics2D g, int w, int h, EquipmentState state, long timeMs) {
        // Cylindrical upper vortex barrel + tall conical lower section
        ScadaShader.paintCylinderV(g, w * 0.14, h * 0.08, w * 0.72, h * 0.36, Material.STAINLESS_STEEL);
        Path2D.Double cone = new Path2D.Double();
        cone.moveTo(w * 0.14, h * 0.44);
        cone.lineTo(w * 0.86, h * 0.44);
        cone.lineTo(w * 0.58, h - 2);
        cone.lineTo(w * 0.42, h - 2);
        cone.closePath();
        g.setColor(new Color(0x7C, 0x87, 0x96));
        g.fill(cone);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(cone);
        // Vortex spiral
        g.setColor(state == EquipmentState.RUNNING ? new Color(0x4F, 0xC3, 0xF7) : ScadaPalette.BEVEL_HIGHLIGHT_SOFT);
        g.setStroke(new BasicStroke(1.5f));
        double shift = (state == EquipmentState.RUNNING) ? ((timeMs / 180.0) % 6.0) : 0.0;
        g.draw(new Line2D.Double(w * 0.26, h * 0.22 + shift, w * 0.74, h * 0.30 + shift));
    }

    private static void paintScrubber(Graphics2D g, int w, int h, EquipmentState state, long timeMs) {
        ScadaShader.paintDishedTankHead(g, w * 0.16, 2, w * 0.68, h * 0.12, true, Material.STAINLESS_STEEL);
        ScadaShader.paintCylinderV(g, w * 0.16, 2 + h * 0.12, w * 0.68, h * 0.74, Material.STAINLESS_STEEL);
        ScadaShader.paintDishedTankHead(g, w * 0.16, h * 0.86, w * 0.68, h * 0.12, false, Material.STAINLESS_STEEL);
        // Packing bed cross-hatch
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(new Rectangle2D.Double(w * 0.22, h * 0.42, w * 0.56, h * 0.24));
        g.draw(new Line2D.Double(w * 0.22, h * 0.42, w * 0.78, h * 0.66));
        g.draw(new Line2D.Double(w * 0.78, h * 0.42, w * 0.22, h * 0.66));
        // Spray droplets
        g.setColor(state == EquipmentState.RUNNING ? new Color(0x29, 0xB6, 0xF6) : ScadaPalette.BEVEL_HIGHLIGHT_SOFT);
        for (int i = 0; i < 3; i++) {
            double dx = w * (0.34 + i * 0.16);
            double dy = h * 0.26 + ((state == EquipmentState.RUNNING) ? ((timeMs / 90.0 + i * 4.0) % 10.0) : 0.0);
            g.fill(new Ellipse2D.Double(dx - 2, dy, 4, 4));
        }
    }

    private static void paintProcessScreen(Graphics2D g, int w, int h) {
        ScadaShader.paintBeveledHousing(g, 2, h * 0.15, w - 4, h * 0.70, 4.0, Material.CAST_IRON);
        // Inclined perforated screen mesh
        g.setColor(ScadaPalette.BEVEL_HIGHLIGHT);
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1.0f, new float[]{4.0f, 3.0f}, 0.0f));
        g.draw(new Line2D.Double(w * 0.22, h * 0.24, w * 0.78, h * 0.76));
    }

    private static void drawBadge(Graphics2D g, String text, double cx, double y, Color color, int size) {
        g.setColor(color);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) y);
    }
}
