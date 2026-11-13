package cz.maramat.scada.render.symbols;

import cz.maramat.scada.model.CardinalDirection;
import cz.maramat.scada.render.EquipmentState;
import cz.maramat.scada.render.ScadaPalette;
import cz.maramat.scada.render.ScadaPalette.Material;
import cz.maramat.scada.render.ScadaShader;
import cz.maramat.scada.render.SymbolLayerCache;
import cz.maramat.scada.render.SymbolLayerCache.CacheKey;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
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
            case "SubmersiblePump" -> paintSubmersiblePump(g, w, h, gPx, state, angle);
            case "PositiveDisplacementPump" -> paintPositiveDisplacementPump(g, w, h, gPx, state, angle);
            case "GearPump" -> paintGearPump(g, w, h, gPx, state, angle);
            case "DiaphragmPump" -> paintDiaphragmPump(g, w, h, gPx, state, angle);
            case "PeristalticPump" -> paintPeristalticPump(g, w, h, gPx, state, angle);
            case "ScrewPump" -> paintScrewPump(g, w, h, gPx, state, angle);
            case "VacuumPump" -> paintVacuumPump(g, w, h, gPx, state, angle);
            case "BoosterPump" -> paintBoosterPump(g, w, h, gPx, state, angle);
            case "ElectricMotor" -> paintElectricMotor(g, w, h, gPx, state, angle);
            case "BlowerFan" -> paintBlowerFan(g, w, h, gPx, state, angle, cache);
            default -> ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 4, Material.CAST_IRON);
        }
    }

    private static void paintCentrifugalPump(Graphics2D g, int w, int h, int gPx,
                                             EquipmentState state, double angle, SymbolLayerCache cache) {
        CacheKey key = new CacheKey("CentrifugalPump", w, h, 0, false, false, "SHELL@" + gPx);
        BufferedImage shell = cache.getOrCreateBackground(key, (sg, sw, sh) -> {
            double cx = sw * 0.52;
            double cy = sh * 0.52;
            double outerR = Math.min(sw, sh) * 0.38;
            double cutoutR = outerR * 0.62;

            // Mounting base feet
            ScadaShader.paintBeveledHousing(sg, sw * 0.18, sh - gPx * 1.1, sw * 0.64, gPx * 1.0, 2.0, Material.FLANGE_STEEL);
            // Discharge neck (top, aligned with port at 0.375 * sw)
            double disX = sw * 0.375;
            double flangeT = Math.max(4.0, gPx * 0.45);
            double neckW = Math.max(gPx * 1.6, outerR * 0.52);
            double neckX = Math.max(0.0, disX - neckW / 2.0);
            ScadaShader.paintCylinderV(sg, neckX, 0.0, neckW, cy - outerR * 0.3, Material.CAST_IRON);
            ScadaShader.paintFlange(sg, disX, flangeT / 2.0, neckW + 6.0, flangeT, CardinalDirection.NORTH);

            // Suction neck (west, aligned with port at 0.5 * sh)
            double sucY = sh * 0.50;
            double sucH = Math.max(gPx * 1.5, outerR * 0.48);
            ScadaShader.paintCylinderH(sg, 0.0, sucY - sucH / 2.0, cx - outerR * 0.4, sucH, Material.CAST_IRON);
            ScadaShader.paintFlange(sg, flangeT / 2.0, sucY, sucH + 6.0, flangeT, CardinalDirection.WEST);

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
        CacheKey key = new CacheKey("InlinePump", w, h, 0, false, false, "SHELL@" + gPx);
        BufferedImage shell = cache.getOrCreateBackground(key, (sg, sw, sh) -> {
            double pipeH = Math.max(gPx * 1.5, sh * 0.32);
            ScadaShader.paintCylinderH(sg, 0.0, (sh - pipeH) / 2.0, sw, pipeH, Material.CAST_IRON);
            ScadaShader.paintFlange(sg, 2.0, sh / 2.0, pipeH + 6.0, 4.0, CardinalDirection.WEST);
            ScadaShader.paintFlange(sg, sw - 2.0, sh / 2.0, pipeH + 6.0, 4.0, CardinalDirection.EAST);
            double r = Math.min(sw, sh) * 0.36;
            ScadaShader.paintVoluteCasing(sg, sw / 2.0, sh / 2.0, r, r * 0.62, Material.CAST_IRON);
        });
        g.drawImage(shell, 0, 0, null);

        double cutoutR = Math.min(w, h) * 0.36 * 0.62;
        ScadaShader.paintRotorImpeller(g, w / 2.0, h / 2.0, cutoutR * 0.9, 6, angle);
        paintStateLed(g, w * 0.82, h * 0.2, Math.max(2.5, gPx * 0.3), state);
    }

    private static void paintSubmersiblePump(Graphics2D g, int w, int h, int gPx,
                                             EquipmentState state, double angle) {
        // Vertical discharge riser + motor cylinder + bottom suction strainer
        double bodyW = w * 0.56;
        double bodyX = (w - bodyW) / 2.0;
        ScadaShader.paintCylinderV(g, w * 0.42, 0, w * 0.16, h * 0.22, Material.FLANGE_STEEL);
        ScadaShader.paintCylinderV(g, bodyX, h * 0.18, bodyW, h * 0.54, Material.CAST_IRON);
        // Bottom volute + strainer grille
        ScadaShader.paintBeveledHousing(g, bodyX - w * 0.06, h * 0.72, bodyW + w * 0.12, h * 0.24, 3.0, Material.FLANGE_STEEL);
        g.setColor(ScadaPalette.CUTOUT_DARK);
        for (int i = 0; i < 4; i++) {
            double sx = bodyX + (i + 0.5) * (bodyW / 4.0);
            g.draw(new Line2D.Double(sx, h * 0.76, sx, h * 0.93));
        }
        double r = Math.min(bodyW, h * 0.25) * 0.36;
        ScadaShader.paintRotorImpeller(g, w * 0.5, h * 0.56, r, 6, angle);
        paintStateLed(g, bodyX + bodyW * 0.78, h * 0.25, Math.max(2.5, gPx * 0.28), state);
    }

    private static void paintPositiveDisplacementPump(Graphics2D g, int w, int h, int gPx,
                                                      EquipmentState state, double angle) {
        double pipeH = Math.max(gPx * 1.4, h * 0.28);
        ScadaShader.paintCylinderH(g, 0, (h - pipeH) / 2.0, w, pipeH, Material.FLANGE_STEEL);
        double r = Math.min(w, h) * 0.38;
        ScadaShader.paintVoluteCasing(g, w * 0.5, h * 0.5, r, r * 0.68, Material.CAST_IRON);
        // Twin-lobe rotor
        double lobeR = r * 0.34;
        for (int i = 0; i < 2; i++) {
            double a = angle + i * Math.PI;
            double lx = w * 0.5 + Math.cos(a) * lobeR * 0.55;
            double ly = h * 0.5 + Math.sin(a) * lobeR * 0.55;
            g.setColor(i == 0 ? ScadaPalette.ROTOR_BLADE_A : ScadaPalette.ROTOR_BLADE_A_HIGHLIGHT);
            g.fill(new Ellipse2D.Double(lx - lobeR, ly - lobeR, lobeR * 2, lobeR * 2));
        }
        paintStateLed(g, w * 0.84, h * 0.18, Math.max(2.5, gPx * 0.28), state);
    }

    private static void paintGearPump(Graphics2D g, int w, int h, int gPx,
                                      EquipmentState state, double angle) {
        double pipeH = Math.max(gPx * 1.4, h * 0.28);
        ScadaShader.paintCylinderH(g, 0, (h - pipeH) / 2.0, w, pipeH, Material.FLANGE_STEEL);
        ScadaShader.paintBeveledHousing(g, w * 0.20, h * 0.14, w * 0.60, h * 0.72, 8.0, Material.CAST_IRON);
        double gr = Math.min(w, h) * 0.17;
        ScadaShader.paintRotorImpeller(g, w * 0.50, h * 0.36, gr, 8, angle);
        ScadaShader.paintRotorImpeller(g, w * 0.50, h * 0.64, gr, 8, -angle + Math.PI / 8.0);
        paintStateLed(g, w * 0.82, h * 0.20, Math.max(2.5, gPx * 0.28), state);
    }

    private static void paintDiaphragmPump(Graphics2D g, int w, int h, int gPx,
                                           EquipmentState state, double angle) {
        double pipeH = Math.max(gPx * 1.4, h * 0.26);
        ScadaShader.paintCylinderH(g, 0, (h - pipeH) / 2.0, w, pipeH, Material.FLANGE_STEEL);
        ScadaShader.paintBeveledHousing(g, w * 0.22, h * 0.16, w * 0.56, h * 0.68, 6.0, Material.STAINLESS_STEEL);
        double flex = (state == EquipmentState.RUNNING) ? Math.sin(angle * 2.5) * (w * 0.06) : 0.0;
        g.setColor(ScadaPalette.CUTOUT_DARK);
        g.fill(new Ellipse2D.Double(w * 0.30, h * 0.26, w * 0.40, h * 0.48));
        g.setColor(ScadaPalette.ROTOR_BLADE_A_HIGHLIGHT);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new Arc2D.Double(w * 0.38 + flex, h * 0.28, w * 0.24, h * 0.44, 90, 180, Arc2D.OPEN));
        paintStateLed(g, w * 0.82, h * 0.20, Math.max(2.5, gPx * 0.28), state);
    }

    private static void paintPeristalticPump(Graphics2D g, int w, int h, int gPx,
                                             EquipmentState state, double angle) {
        double r = Math.min(w, h) * 0.40;
        double cx = w * 0.5;
        double cy = h * 0.5;
        ScadaShader.paintVoluteCasing(g, cx, cy, r, r * 0.68, Material.CAST_IRON);
        // Flexible hose arc
        g.setColor(new Color(0x26, 0xA6, 0x9A));
        g.setStroke(new BasicStroke(Math.max(2.5f, (float) (r * 0.20))));
        g.draw(new Arc2D.Double(cx - r * 0.58, cy - r * 0.58, r * 1.16, r * 1.16, -20, 220, Arc2D.OPEN));
        // 3 compression rollers
        double rollerR = r * 0.16;
        for (int i = 0; i < 3; i++) {
            double a = angle + i * (2.0 * Math.PI / 3.0);
            double rx = cx + Math.cos(a) * r * 0.42;
            double ry = cy + Math.sin(a) * r * 0.42;
            g.setColor(ScadaPalette.BEVEL_HIGHLIGHT);
            g.fill(new Ellipse2D.Double(rx - rollerR, ry - rollerR, rollerR * 2, rollerR * 2));
        }
        paintStateLed(g, w * 0.84, h * 0.18, Math.max(2.5, gPx * 0.28), state);
    }

    private static void paintScrewPump(Graphics2D g, int w, int h, int gPx,
                                       EquipmentState state, double angle) {
        double barrelH = h * 0.48;
        double barrelY = (h - barrelH) / 2.0;
        ScadaShader.paintCylinderH(g, w * 0.10, barrelY, w * 0.80, barrelH, Material.STAINLESS_STEEL);
        // Helical screw flights inside cutaway
        g.setColor(ScadaPalette.CUTOUT_DARK);
        g.fill(new Rectangle2D.Double(w * 0.20, barrelY + barrelH * 0.18, w * 0.60, barrelH * 0.64));
        double shift = (state == EquipmentState.RUNNING) ? ((angle * 6.0) % 10.0) : 0.0;
        g.setColor(ScadaPalette.BEVEL_HIGHLIGHT);
        g.setStroke(new BasicStroke(2.0f));
        for (double sx = w * 0.22 + shift; sx < w * 0.76; sx += 10.0) {
            g.draw(new Line2D.Double(sx, barrelY + barrelH * 0.22, sx + 5.0, barrelY + barrelH * 0.78));
        }
        paintStateLed(g, w * 0.85, h * 0.22, Math.max(2.5, gPx * 0.28), state);
    }

    private static void paintVacuumPump(Graphics2D g, int w, int h, int gPx,
                                        EquipmentState state, double angle) {
        double r = Math.min(w, h) * 0.38;
        ScadaShader.paintVoluteCasing(g, w * 0.50, h * 0.54, r, r * 0.64, Material.FLANGE_STEEL);
        // Eccentric vane rotor
        ScadaShader.paintRotorImpeller(g, w * 0.50, h * 0.56, r * 0.58, 5, angle);
        // Small vacuum gauge on top-left
        double gr = Math.max(5.0, Math.min(w, h) * 0.14);
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(w * 0.14, h * 0.12, gr * 2, gr * 2));
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Double(w * 0.14, h * 0.12, gr * 2, gr * 2));
        g.draw(new Line2D.Double(w * 0.14 + gr, h * 0.12 + gr, w * 0.14 + gr * 0.4, h * 0.12 + gr * 0.5));
        paintStateLed(g, w * 0.84, h * 0.18, Math.max(2.5, gPx * 0.28), state);
    }

    private static void paintBoosterPump(Graphics2D g, int w, int h, int gPx,
                                         EquipmentState state, double angle) {
        // Multi-stage vertical booster stack
        ScadaShader.paintBeveledHousing(g, w * 0.18, h * 0.82, w * 0.64, h * 0.14, 2.0, Material.FLANGE_STEEL);
        ScadaShader.paintCylinderV(g, w * 0.26, h * 0.34, w * 0.48, h * 0.48, Material.STAINLESS_STEEL);
        // Stage rings
        g.setColor(ScadaPalette.OUTLINE_DARK);
        for (int i = 1; i <= 3; i++) {
            double ry = h * (0.34 + i * 0.11);
            g.draw(new Line2D.Double(w * 0.26, ry, w * 0.74, ry));
        }
        // Top motor
        ScadaShader.paintCylinderV(g, w * 0.30, h * 0.06, w * 0.40, h * 0.28, Material.CAST_IRON);
        double r = Math.min(w, h) * 0.14;
        ScadaShader.paintRotorImpeller(g, w * 0.50, h * 0.20, r, 6, angle);
        paintStateLed(g, w * 0.80, h * 0.14, Math.max(2.5, gPx * 0.28), state);
    }

    private static void paintStateLed(Graphics2D g, double cx, double cy, double r, EquipmentState state) {
        Color ledColor = switch (state) {
            case RUNNING -> ScadaPalette.STATE_RUNNING_LED;
            case STANDBY -> ScadaPalette.STATE_STANDBY;
            case WARNING -> ScadaPalette.STATE_WARNING;
            case FAULT -> ScadaPalette.STATE_FAULT;
            default -> ScadaPalette.STATE_STOPPED_LED;
        };
        ScadaShader.paintLedIndicator(g, cx, cy, r, ledColor, state != EquipmentState.STOPPED);
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
