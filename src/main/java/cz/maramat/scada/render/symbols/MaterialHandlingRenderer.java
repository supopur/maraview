package cz.maramat.scada.render.symbols;

import cz.maramat.scada.render.EquipmentState;
import cz.maramat.scada.render.ScadaPalette;
import cz.maramat.scada.render.ScadaPalette.Material;
import cz.maramat.scada.render.ScadaShader;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/**
 * 3D procedural renderers for ConveyorBelt, ScrewAuger, PneumaticCylinder, HydraulicPress, and HeaterCoil.
 */
public final class MaterialHandlingRenderer {

    private MaterialHandlingRenderer() {
    }

    public static void paint(Graphics2D g, String type, int w, int h, int gPx,
                             EquipmentState state, double valuePct, long timeMs) {
        double phase = (state == EquipmentState.RUNNING) ? (timeMs / 1000.0) * 28.0 : 0.0;
        switch (type) {
            case "ConveyorBelt" -> paintConveyorBelt(g, w, h, phase, state == EquipmentState.RUNNING);
            case "ScrewAuger" -> paintScrewAuger(g, w, h, phase);
            case "PneumaticCylinder" -> paintPneumaticCylinder(g, w, h, gPx, valuePct);
            case "HydraulicPress" -> paintHydraulicPress(g, w, h, valuePct);
            case "HeaterCoil" -> paintHeaterCoil(g, w, h, state == EquipmentState.RUNNING ? Math.max(50.0, valuePct) : valuePct);
            default -> ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 4, Material.CAST_IRON);
        }
    }

    private static void paintConveyorBelt(Graphics2D g, int w, int h, double phase, boolean running) {
        double beltY = h * 0.28;
        double beltH = h * 0.44;
        ScadaShader.paintCylinderH(g, beltH / 2.0, beltY, w - beltH, beltH, Material.FLANGE_STEEL);

        // Two rotating 3D end-rollers
        double r = beltH / 2.0;
        ScadaShader.paintVoluteCasing(g, r + 2, h / 2.0, r, r * 0.5, Material.CAST_IRON);
        ScadaShader.paintVoluteCasing(g, w - r - 2, h / 2.0, r, r * 0.5, Material.CAST_IRON);
        ScadaShader.paintRotorImpeller(g, r + 2, h / 2.0, r * 0.48, 4, phase * 0.15);
        ScadaShader.paintRotorImpeller(g, w - r - 2, h / 2.0, r * 0.48, 4, phase * 0.15);

        // Moving cleats on top belt
        g.setColor(ScadaPalette.BEVEL_HIGHLIGHT);
        double span = Math.max(10.0, w - beltH);
        double spacing = 14.0;
        double offset = running ? (phase % spacing) : 0.0;
        for (double cx = r + 4 + offset; cx < w - r - 4; cx += spacing) {
            g.fill(new Rectangle2D.Double(cx, beltY - 2, 3, 4));
        }
        if (span > 0 && running) {
            double boxX = r + 6 + ((phase * 1.4) % Math.max(8.0, span - 14.0));
            ScadaShader.paintBeveledHousing(g, boxX, beltY - 10, 10, 10, 1.0, Material.STAINLESS_STEEL);
        }
    }

    private static void paintScrewAuger(Graphics2D g, int w, int h, double phase) {
        ScadaShader.paintCylinderH(g, 2, h * 0.22, w - 4, h * 0.56, Material.STAINLESS_STEEL);
        double spacing = 12.0;
        double offset = phase % spacing;
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(2.2f));
        for (double x = 6 + offset; x < w - 6; x += spacing) {
            g.drawLine((int) x, (int) (h * 0.25), (int) (x + 6), (int) (h * 0.75));
        }
    }

    private static void paintPneumaticCylinder(Graphics2D g, int w, int h, int gPx, double extPct) {
        double strokeFrac = Math.max(0.0, Math.min(100.0, extPct)) / 100.0;
        ScadaShader.paintCylinderH(g, 2, h * 0.22, w * 0.58, h * 0.56, Material.CAST_IRON);
        double rodW = w * (0.14 + 0.24 * strokeFrac);
        ScadaShader.paintCylinderH(g, w * 0.60, h * 0.40, rodW, h * 0.20, Material.STAINLESS_STEEL);
        ScadaShader.paintBeveledHousing(g, w * 0.60 + rodW - 2, h * 0.30, 6, h * 0.40, 1.0, Material.FLANGE_STEEL);

        // End-of-stroke reed switch LEDs
        ScadaShader.paintLedIndicator(g, w * 0.14, h * 0.18, Math.max(2.0, gPx * 0.25),
                ScadaPalette.STATE_RUNNING_LED, strokeFrac < 0.05);
        ScadaShader.paintLedIndicator(g, w * 0.50, h * 0.18, Math.max(2.0, gPx * 0.25),
                ScadaPalette.STATE_RUNNING_LED, strokeFrac > 0.95);
    }

    private static void paintHydraulicPress(Graphics2D g, int w, int h, double pressPct) {
        double travel = Math.max(0.0, Math.min(100.0, pressPct)) / 100.0 * (h * 0.28);
        // Frame
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h * 0.16, 2.0, Material.CAST_IRON);
        ScadaShader.paintBeveledHousing(g, 2, h * 0.84, w - 4, h * 0.14, 2.0, Material.CAST_IRON);
        ScadaShader.paintCylinderV(g, 4, h * 0.16, 6, h * 0.68, Material.FLANGE_STEEL);
        ScadaShader.paintCylinderV(g, w - 10, h * 0.16, 6, h * 0.68, Material.FLANGE_STEEL);
        // Ram
        ScadaShader.paintCylinderV(g, w * 0.38, h * 0.16, w * 0.24, h * 0.16 + travel, Material.STAINLESS_STEEL);
        ScadaShader.paintBeveledHousing(g, w * 0.20, h * 0.32 + travel, w * 0.60, h * 0.12, 2.0, Material.FLANGE_STEEL);
    }

    private static void paintHeaterCoil(Graphics2D g, int w, int h, double heatPct) {
        ScadaShader.paintBeveledHousing(g, 2, h * 0.18, w - 4, h * 0.64, 4.0, Material.CAST_IRON);
        double frac = Math.max(0.0, Math.min(100.0, heatPct)) / 100.0;
        int r = (int) (90 + 165 * frac);
        int gr = (int) (95 + 40 * frac * (1.0 - frac));
        int b = (int) (105 * (1.0 - frac));
        g.setColor(new Color(r, gr, b));
        g.setStroke(new BasicStroke(3.0f));
        for (int i = 0; i < 3; i++) {
            double cx = w * (0.25 + i * 0.25);
            g.draw(new Ellipse2D.Double(cx - 5, h * 0.30, 10, h * 0.40));
        }
    }
}
