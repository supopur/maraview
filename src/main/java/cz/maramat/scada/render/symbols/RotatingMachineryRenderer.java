package cz.maramat.scada.render.symbols;

import cz.maramat.scada.model.CardinalDirection;
import cz.maramat.scada.render.EquipmentState;
import cz.maramat.scada.render.ScadaPalette;
import cz.maramat.scada.render.ScadaPalette.Material;
import cz.maramat.scada.render.ScadaShader;
import cz.maramat.scada.render.SymbolLayerCache;
import cz.maramat.scada.render.SymbolLayerCache.CacheKey;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * 3D procedural renderers for CentrifugalPump, InlinePump, MeteringPump, ElectricMotor, and BlowerFan.
 */
public final class RotatingMachineryRenderer {

    private RotatingMachineryRenderer() {
    }

    public static void paint(Graphics2D g, String type, int w, int h, int gPx,
                             EquipmentState state, double speedNorm, long timeMs,
                             SymbolLayerCache cache) {
        double angle = (state == EquipmentState.RUNNING || speedNorm > 0.0)
                ? (timeMs / 1000.0) * (4.5 * Math.max(0.25, speedNorm))
                : 0.0;

        switch (type) {
            case "CentrifugalPump" -> paintCentrifugalPump(g, w, h, gPx, state, angle, cache);
            case "InlinePump" -> paintInlinePump(g, w, h, gPx, state, angle, cache);
            case "MeteringPump" -> paintMeteringPump(g, w, h, gPx, state, angle, timeMs);
            case "ElectricMotor" -> paintElectricMotor(g, w, h, gPx, state, angle);
            case "BlowerFan" -> paintBlowerFan(g, w, h, gPx, state, angle, cache);
            default -> ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 4, Material.CAST_IRON);
        }
    }

    private static void paintCentrifugalPump(Graphics2D g, int w, int h, int gPx,
                                             EquipmentState state, double angle, SymbolLayerCache cache) {
        CacheKey key = new CacheKey("CentrifugalPump", w, h, 0, false, false, "SHELL");
        BufferedImage shell = cache.getOrCreateBackground(key, (sg, sw, sh) -> {
            double cx = sw * 0.52;
            double cy = sh * 0.52;
            double outerR = Math.min(sw, sh) * 0.38;
            double cutoutR = outerR * 0.62;

            // Mounting base feet
            ScadaShader.paintBeveledHousing(sg, sw * 0.18, sh - gPx * 1.1, sw * 0.64, gPx * 1.0, 2.0, Material.FLANGE_STEEL);
            // Discharge neck (top, aligned with port at 3G)
            double neckW = Math.max(gPx * 1.6, outerR * 0.52);
            double neckX = Math.max(2.0, gPx * 3.0 - neckW / 2.0);
            ScadaShader.paintCylinderV(sg, neckX, 2.0, neckW, cy - outerR * 0.3, Material.CAST_IRON);
            ScadaShader.paintFlange(sg, gPx * 3.0, Math.max(3.0, gPx * 0.3), neckW + 6.0, Math.max(4.0, gPx * 0.45), CardinalDirection.NORTH);

            // Suction neck (west, aligned with port at 5G)
            double sucH = Math.max(gPx * 1.5, outerR * 0.48);
            ScadaShader.paintCylinderH(sg, 2.0, gPx * 5.0 - sucH / 2.0, cx - outerR * 0.4, sucH, Material.CAST_IRON);
            ScadaShader.paintFlange(sg, Math.max(3.0, gPx * 0.3), gPx * 5.0, sucH + 6.0, Math.max(4.0, gPx * 0.45), CardinalDirection.WEST);

            // Volute casing with recessed circular sight-glass cavity
            ScadaShader.paintVoluteCasing(sg, cx, cy, outerR, cutoutR, Material.CAST_IRON);
        });
        g.drawImage(shell, 0, 0, null);

        double cx = w * 0.52;
        double cy = h * 0.52;
        double cutoutR = Math.min(w, h) * 0.38 * 0.62;
        ScadaShader.paintRotorImpeller(g, cx, cy, cutoutR * 0.92, 8, angle);

        Color ledColor = (state == EquipmentState.RUNNING) ? ScadaPalette.STATE_RUNNING_LED : ScadaPalette.STATE_STOPPED_LED;
        ScadaShader.paintLedIndicator(g, w * 0.84, h * 0.18, Math.max(3.0, gPx * 0.35), ledColor, state == EquipmentState.RUNNING);
    }

    private static void paintInlinePump(Graphics2D g, int w, int h, int gPx,
                                        EquipmentState state, double angle, SymbolLayerCache cache) {
        CacheKey key = new CacheKey("InlinePump", w, h, 0, false, false, "SHELL");
        BufferedImage shell = cache.getOrCreateBackground(key, (sg, sw, sh) -> {
            double pipeH = Math.max(gPx * 1.5, sh * 0.32);
            ScadaShader.paintCylinderH(sg, 2.0, (sh - pipeH) / 2.0, sw - 4.0, pipeH, Material.CAST_IRON);
            ScadaShader.paintFlange(sg, 3.0, sh / 2.0, pipeH + 6.0, 4.0, CardinalDirection.WEST);
            ScadaShader.paintFlange(sg, sw - 3.0, sh / 2.0, pipeH + 6.0, 4.0, CardinalDirection.EAST);
            double r = Math.min(sw, sh) * 0.36;
            ScadaShader.paintVoluteCasing(sg, sw / 2.0, sh / 2.0, r, r * 0.62, Material.CAST_IRON);
        });
        g.drawImage(shell, 0, 0, null);

        double cutoutR = Math.min(w, h) * 0.36 * 0.62;
        ScadaShader.paintRotorImpeller(g, w / 2.0, h / 2.0, cutoutR * 0.9, 6, angle);
        Color ledColor = (state == EquipmentState.RUNNING) ? ScadaPalette.STATE_RUNNING_LED : ScadaPalette.STATE_STOPPED_LED;
        ScadaShader.paintLedIndicator(g, w * 0.82, h * 0.2, Math.max(2.5, gPx * 0.3), ledColor, state == EquipmentState.RUNNING);
    }

    private static void paintMeteringPump(Graphics2D g, int w, int h, int gPx,
                                          EquipmentState state, double angle, long timeMs) {
        // Motor block + dosing head + stroke indicator
        ScadaShader.paintBeveledHousing(g, w * 0.12, h * 0.28, w * 0.52, h * 0.56, 4.0, Material.CAST_IRON);
        ScadaShader.paintCylinderV(g, w * 0.66, h * 0.16, w * 0.24, h * 0.68, Material.STAINLESS_STEEL);
        double pulse = (state == EquipmentState.RUNNING) ? Math.sin(angle * 2.0) * (h * 0.08) : 0.0;
        ScadaShader.paintBeveledHousing(g, w * 0.72, h * 0.38 + pulse, w * 0.12, h * 0.24, 2.0, Material.FLANGE_STEEL);
        Color ledColor = (state == EquipmentState.RUNNING) ? ScadaPalette.STATE_RUNNING_LED : ScadaPalette.STATE_STOPPED_LED;
        ScadaShader.paintLedIndicator(g, w * 0.25, h * 0.42, Math.max(2.5, gPx * 0.3), ledColor, state == EquipmentState.RUNNING);
    }

    private static void paintElectricMotor(Graphics2D g, int w, int h, int gPx,
                                           EquipmentState state, double angle) {
        // Finned stator housing
        ScadaShader.paintCylinderH(g, w * 0.14, h * 0.18, w * 0.64, h * 0.64, Material.CAST_IRON);
        // Cooling fins
        g.setColor(ScadaPalette.OUTLINE_DARK);
        for (int i = 1; i <= 4; i++) {
            double fy = h * (0.2 + i * 0.12);
            g.drawLine((int) (w * 0.22), (int) fy, (int) (w * 0.70), (int) fy);
        }
        // Terminal box on top
        ScadaShader.paintBeveledHousing(g, w * 0.35, h * 0.06, w * 0.24, h * 0.16, 2.0, Material.FLANGE_STEEL);
        // Drive shaft + rotating fan cowl
        ScadaShader.paintCylinderH(g, w * 0.78, h * 0.42, w * 0.18, h * 0.16, Material.STAINLESS_STEEL);
        double fanR = Math.min(w, h) * 0.20;
        ScadaShader.paintVoluteCasing(g, w * 0.18, h * 0.50, fanR, fanR * 0.72, Material.FLANGE_STEEL);
        ScadaShader.paintRotorImpeller(g, w * 0.18, h * 0.50, fanR * 0.65, 6, angle);
        Color ledColor = (state == EquipmentState.RUNNING) ? ScadaPalette.STATE_RUNNING_LED : ScadaPalette.STATE_STOPPED_LED;
        ScadaShader.paintLedIndicator(g, w * 0.47, h * 0.14, Math.max(2.5, gPx * 0.28), ledColor, state == EquipmentState.RUNNING);
    }

    private static void paintBlowerFan(Graphics2D g, int w, int h, int gPx,
                                       EquipmentState state, double angle, SymbolLayerCache cache) {
        CacheKey key = new CacheKey("BlowerFan", w, h, 0, false, false, "SHELL");
        BufferedImage shell = cache.getOrCreateBackground(key, (sg, sw, sh) -> {
            double r = Math.min(sw, sh) * 0.44;
            ScadaShader.paintVoluteCasing(sg, sw / 2.0, sh / 2.0, r, r * 0.75, Material.STAINLESS_STEEL);
        });
        g.drawImage(shell, 0, 0, null);
        double r = Math.min(w, h) * 0.44 * 0.70;
        ScadaShader.paintRotorImpeller(g, w / 2.0, h / 2.0, r, 6, angle);
        Color ledColor = (state == EquipmentState.RUNNING) ? ScadaPalette.STATE_RUNNING_LED : ScadaPalette.STATE_STOPPED_LED;
        ScadaShader.paintLedIndicator(g, w * 0.85, h * 0.15, Math.max(2.5, gPx * 0.3), ledColor, state == EquipmentState.RUNNING);
        // Subtle hub ring
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(new Ellipse2D.Double(w / 2.0 - 3, h / 2.0 - 3, 6, 6));
    }
}
