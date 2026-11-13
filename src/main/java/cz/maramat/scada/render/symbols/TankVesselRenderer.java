package cz.maramat.scada.render.symbols;

import cz.maramat.scada.model.FluidType;
import cz.maramat.scada.render.EquipmentState;
import cz.maramat.scada.render.ScadaPalette;
import cz.maramat.scada.render.ScadaPalette.Material;
import cz.maramat.scada.render.ScadaShader;
import cz.maramat.scada.render.SymbolLayerCache;
import cz.maramat.scada.render.SymbolLayerCache.CacheKey;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * 3D procedural renderers for CylindricalTank, HorizontalTank, HopperSilo, OpenBasin, and TankAgitator.
 */
public final class TankVesselRenderer {

    private TankVesselRenderer() {
    }

    public static void paint(Graphics2D g, String type, int w, int h, int gPx,
                             double levelPct, FluidType fluid, EquipmentState state,
                             long timeMs, SymbolLayerCache cache) {
        double clampedLevel = Math.max(0.0, Math.min(100.0, levelPct));

        switch (type) {
            case "CylindricalTank" -> paintCylindricalTank(g, w, h, gPx, clampedLevel, fluid, timeMs, cache);
            case "HorizontalTank" -> paintHorizontalTank(g, w, h, gPx, clampedLevel, fluid, timeMs);
            case "HopperSilo" -> paintHopperSilo(g, w, h, clampedLevel, fluid);
            case "OpenBasin" -> paintOpenBasin(g, w, h, clampedLevel, fluid, timeMs);
            case "TankAgitator" -> paintTankAgitator(g, w, h, gPx, clampedLevel, fluid, state, timeMs, cache);
            default -> paintCylindricalTank(g, w, h, gPx, clampedLevel, fluid, timeMs, cache);
        }
    }

    private static void paintCylindricalTank(Graphics2D g, int w, int h, int gPx,
                                             double levelPct, FluidType fluid, long timeMs,
                                             SymbolLayerCache cache) {
        CacheKey key = new CacheKey("CylindricalTank", w, h, 0, false, false, "SHELL");
        BufferedImage shell = cache.getOrCreateBackground(key, (sg, sw, sh) -> {
            double headH = Math.max(8.0, sh * 0.14);
            ScadaShader.paintDishedTankHead(sg, 2, 2, sw - 4, headH, true, Material.STAINLESS_STEEL);
            ScadaShader.paintDishedTankHead(sg, 2, sh - headH - 2, sw - 4, headH, false, Material.STAINLESS_STEEL);
            ScadaShader.paintCylinderV(sg, 2, 2 + headH, sw - 4, sh - 4 - headH * 2, Material.STAINLESS_STEEL);
        });
        g.drawImage(shell, 0, 0, null);

        // Sight-glass cutaway + liquid fill
        double cutX = w * 0.28;
        double cutY = h * 0.20;
        double cutW = w * 0.44;
        double cutH = h * 0.60;
        paintLevelCutaway(g, cutX, cutY, cutW, cutH, levelPct, fluid, timeMs);
    }

    private static void paintHorizontalTank(Graphics2D g, int w, int h, int gPx,
                                            double levelPct, FluidType fluid, long timeMs) {
        ScadaShader.paintCylinderH(g, 4, h * 0.18, w - 8, h * 0.64, Material.STAINLESS_STEEL);
        ScadaShader.paintBeveledHousing(g, w * 0.16, h * 0.80, w * 0.12, h * 0.16, 2.0, Material.FLANGE_STEEL);
        ScadaShader.paintBeveledHousing(g, w * 0.72, h * 0.80, w * 0.12, h * 0.16, 2.0, Material.FLANGE_STEEL);
        paintLevelCutaway(g, w * 0.22, h * 0.28, w * 0.56, h * 0.44, levelPct, fluid, timeMs);
    }

    private static void paintHopperSilo(Graphics2D g, int w, int h, double levelPct, FluidType fluid) {
        double coneTopY = h * 0.62;
        ScadaShader.paintDishedTankHead(g, 2, 2, w - 4, h * 0.10, true, Material.STAINLESS_STEEL);
        ScadaShader.paintCylinderV(g, 2, 2 + h * 0.10, w - 4, coneTopY - h * 0.10 - 2, Material.STAINLESS_STEEL);

        Path2D.Double cone = new Path2D.Double();
        cone.moveTo(2, coneTopY);
        cone.lineTo(w - 2, coneTopY);
        cone.lineTo(w * 0.58, h - 2);
        cone.lineTo(w * 0.42, h - 2);
        cone.closePath();
        g.setColor(new Color(0x7C, 0x87, 0x96));
        g.fill(cone);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(cone);

        paintLevelCutaway(g, w * 0.30, h * 0.18, w * 0.40, h * 0.38, levelPct, fluid, 0L);
    }

    private static void paintOpenBasin(Graphics2D g, int w, int h, double levelPct, FluidType fluid, long timeMs) {
        ScadaShader.paintBeveledHousing(g, 2, h * 0.15, w - 4, h * 0.82, 2.0, Material.CAST_IRON);
        paintLevelCutaway(g, 8, h * 0.22, w - 16, h * 0.68, levelPct, fluid, timeMs);
    }

    private static void paintTankAgitator(Graphics2D g, int w, int h, int gPx,
                                          double levelPct, FluidType fluid, EquipmentState state,
                                          long timeMs, SymbolLayerCache cache) {
        paintCylindricalTank(g, w, h, gPx, levelPct, fluid, timeMs, cache);
        // Top motor
        ScadaShader.paintBeveledHousing(g, w * 0.36, 0, w * 0.28, h * 0.14, 2.0, Material.CAST_IRON);
        // Mixing shaft + rotating paddles
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(2.0f));
        g.drawLine(w / 2, (int) (h * 0.14), w / 2, (int) (h * 0.72));

        double phase = (state == EquipmentState.RUNNING) ? Math.sin(timeMs / 120.0) : 1.0;
        double bladeSpan = Math.max(4.0, w * 0.18 * Math.abs(phase));
        g.setColor(ScadaPalette.BEVEL_HIGHLIGHT);
        g.setStroke(new BasicStroke(3.0f));
        g.drawLine((int) (w / 2.0 - bladeSpan), (int) (h * 0.68), (int) (w / 2.0 + bladeSpan), (int) (h * 0.68));
    }

    private static void paintLevelCutaway(Graphics2D g, double x, double y, double w, double h,
                                          double levelPct, FluidType fluid, long timeMs) {
        Rectangle2D.Double cavity = new Rectangle2D.Double(x, y, w, h);
        g.setColor(ScadaPalette.CUTOUT_DARK);
        g.fill(cavity);

        if (levelPct > 0.0 && fluid != FluidType.EMPTY) {
            double fillH = (levelPct / 100.0) * h;
            double fillY = y + h - fillH;
            Shape oldClip = g.getClip();
            g.clip(cavity);

            g.setColor(fluid.getBaseColor());
            g.fill(new Rectangle2D.Double(x, fillY, w, fillH));

            // Surface highlight / subtle wave
            double waveOffset = (timeMs > 0) ? Math.sin(timeMs / 250.0) * 1.2 : 0.0;
            g.setColor(fluid.getHighlightColor());
            g.fill(new Rectangle2D.Double(x, fillY + waveOffset, w, Math.min(3.0, fillH)));
            g.setClip(oldClip);
        }

        // Graduated ruler ticks
        g.setColor(ScadaPalette.BEVEL_HIGHLIGHT_SOFT);
        g.setStroke(new BasicStroke(1.0f));
        for (int i = 1; i < 5; i++) {
            double ty = y + (h * i) / 5.0;
            g.drawLine((int) x, (int) ty, (int) (x + Math.min(6.0, w * 0.22)), (int) ty);
        }

        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(cavity);
    }
}
