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
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * 3D procedural renderers for all tank, silo, hopper, basin, and reactor vessels
 * with animated level fill, setpoint markers, and HH/H/L/LL alarm bands.
 */
public final class TankVesselRenderer {

    public record TankMarkers(Double setpointPct, Double hhPct, Double hPct, Double lPct, Double llPct) {
        public static final TankMarkers NONE = new TankMarkers(null, null, null, null, null);
    }

    private TankVesselRenderer() {
    }

    public static void paint(Graphics2D g, String type, int w, int h, int gPx,
                             double levelPct, FluidType fluid, EquipmentState state,
                             long timeMs, SymbolLayerCache cache) {
        paint(g, type, w, h, gPx, levelPct, fluid, state, timeMs, cache, TankMarkers.NONE);
    }

    public static void paint(Graphics2D g, String type, int w, int h, int gPx,
                             double levelPct, FluidType fluid, EquipmentState state,
                             long timeMs, SymbolLayerCache cache, TankMarkers markers) {
        double clampedLevel = Math.max(0.0, Math.min(100.0, levelPct));
        TankMarkers m = (markers != null) ? markers : TankMarkers.NONE;

        switch (type) {
            case "CylindricalTank" -> paintCylindricalTank(g, w, h, gPx, clampedLevel, fluid, timeMs, cache, m);
            case "HorizontalTank" -> paintHorizontalTank(g, w, h, gPx, clampedLevel, fluid, timeMs, m);
            case "HopperSilo" -> paintHopperSilo(g, w, h, clampedLevel, fluid, timeMs, m);
            case "OpenBasin" -> paintOpenBasin(g, w, h, clampedLevel, fluid, timeMs, m);
            case "TankAgitator" -> paintTankAgitator(g, w, h, gPx, clampedLevel, fluid, state, timeMs, cache, m);
            case "ConeBottomTank" -> paintConeBottomTank(g, w, h, clampedLevel, fluid, timeMs, m);
            case "SphericalTank" -> paintSphericalTank(g, w, h, clampedLevel, fluid, timeMs, m);
            case "ClosedTank" -> paintClosedTank(g, w, h, gPx, clampedLevel, fluid, timeMs, cache, m);
            case "Silo" -> paintSilo(g, w, h, clampedLevel, fluid, timeMs, m);
            case "Hopper" -> paintHopper(g, w, h, clampedLevel, fluid, timeMs, m);
            case "Reactor" -> paintReactor(g, w, h, gPx, clampedLevel, fluid, state, timeMs, cache, m);
            default -> paintCylindricalTank(g, w, h, gPx, clampedLevel, fluid, timeMs, cache, m);
        }
    }

    private static void paintCylindricalTank(Graphics2D g, int w, int h, int gPx,
                                             double levelPct, FluidType fluid, long timeMs,
                                             SymbolLayerCache cache, TankMarkers m) {
        CacheKey key = new CacheKey("CylindricalTank", w, h, 0, false, false, "SHELL@" + gPx);
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
        paintLevelCutaway(g, cutX, cutY, cutW, cutH, levelPct, fluid, timeMs, m);
    }

    private static void paintHorizontalTank(Graphics2D g, int w, int h, int gPx,
                                            double levelPct, FluidType fluid, long timeMs, TankMarkers m) {
        ScadaShader.paintCylinderH(g, 4, h * 0.18, w - 8, h * 0.64, Material.STAINLESS_STEEL);
        ScadaShader.paintBeveledHousing(g, w * 0.16, h * 0.80, w * 0.12, h * 0.16, 2.0, Material.FLANGE_STEEL);
        ScadaShader.paintBeveledHousing(g, w * 0.72, h * 0.80, w * 0.12, h * 0.16, 2.0, Material.FLANGE_STEEL);
        paintLevelCutaway(g, w * 0.22, h * 0.28, w * 0.56, h * 0.44, levelPct, fluid, timeMs, m);
    }

    private static void paintHopperSilo(Graphics2D g, int w, int h, double levelPct, FluidType fluid, long timeMs, TankMarkers m) {
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

        paintLevelCutaway(g, w * 0.30, h * 0.18, w * 0.40, h * 0.38, levelPct, fluid, timeMs, m);
    }

    private static void paintOpenBasin(Graphics2D g, int w, int h, double levelPct, FluidType fluid, long timeMs, TankMarkers m) {
        ScadaShader.paintBeveledHousing(g, 2, h * 0.15, w - 4, h * 0.82, 2.0, Material.CAST_IRON);
        paintLevelCutaway(g, 8, h * 0.22, w - 16, h * 0.68, levelPct, fluid, timeMs, m);
    }

    private static void paintTankAgitator(Graphics2D g, int w, int h, int gPx,
                                          double levelPct, FluidType fluid, EquipmentState state,
                                          long timeMs, SymbolLayerCache cache, TankMarkers m) {
        paintCylindricalTank(g, w, h, gPx, levelPct, fluid, timeMs, cache, m);
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

    private static void paintConeBottomTank(Graphics2D g, int w, int h, double levelPct, FluidType fluid, long timeMs, TankMarkers m) {
        double coneTopY = h * 0.68;
        ScadaShader.paintDishedTankHead(g, 2, 2, w - 4, h * 0.12, true, Material.STAINLESS_STEEL);
        ScadaShader.paintCylinderV(g, 2, 2 + h * 0.12, w - 4, coneTopY - h * 0.12 - 2, Material.STAINLESS_STEEL);
        Path2D.Double cone = new Path2D.Double();
        cone.moveTo(2, coneTopY);
        cone.lineTo(w - 2, coneTopY);
        cone.lineTo(w * 0.56, h - 2);
        cone.lineTo(w * 0.44, h - 2);
        cone.closePath();
        g.setColor(new Color(0x7C, 0x87, 0x96));
        g.fill(cone);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(cone);
        // Support legs
        ScadaShader.paintCylinderV(g, w * 0.10, coneTopY, 5, h - coneTopY - 2, Material.FLANGE_STEEL);
        ScadaShader.paintCylinderV(g, w * 0.90 - 5, coneTopY, 5, h - coneTopY - 2, Material.FLANGE_STEEL);
        paintLevelCutaway(g, w * 0.28, h * 0.18, w * 0.44, h * 0.44, levelPct, fluid, timeMs, m);
    }

    private static void paintSphericalTank(Graphics2D g, int w, int h, double levelPct, FluidType fluid, long timeMs, TankMarkers m) {
        double d = Math.min(w - 8, h * 0.78);
        double sx = (w - d) / 2.0;
        double sy = h * 0.06;
        // Support legs
        ScadaShader.paintCylinderV(g, sx + d * 0.15, sy + d * 0.65, 5, h - (sy + d * 0.65) - 2, Material.FLANGE_STEEL);
        ScadaShader.paintCylinderV(g, sx + d * 0.85 - 5, sy + d * 0.65, 5, h - (sy + d * 0.65) - 2, Material.FLANGE_STEEL);
        // Sphere body
        ScadaShader.paintVoluteCasing(g, w * 0.5, sy + d * 0.5, d * 0.5, d * 0.34, Material.STAINLESS_STEEL);
        paintLevelCutaway(g, w * 0.5 - d * 0.24, sy + d * 0.20, d * 0.48, d * 0.60, levelPct, fluid, timeMs, m);
    }

    private static void paintClosedTank(Graphics2D g, int w, int h, int gPx,
                                        double levelPct, FluidType fluid, long timeMs,
                                        SymbolLayerCache cache, TankMarkers m) {
        paintCylindricalTank(g, w, h, gPx, levelPct, fluid, timeMs, cache, m);
        // Bolted pressure manway hatch on top head
        ScadaShader.paintBeveledHousing(g, w * 0.36, 2, w * 0.28, h * 0.07, 2.0, Material.FLANGE_STEEL);
    }

    private static void paintSilo(Graphics2D g, int w, int h, double levelPct, FluidType fluid, long timeMs, TankMarkers m) {
        paintHopperSilo(g, w, h, levelPct, fluid, timeMs, m);
        // Structural skirt legs
        ScadaShader.paintCylinderV(g, 4, h * 0.62, 5, h * 0.36, Material.FLANGE_STEEL);
        ScadaShader.paintCylinderV(g, w - 9, h * 0.62, 5, h * 0.36, Material.FLANGE_STEEL);
    }

    private static void paintHopper(Graphics2D g, int w, int h, double levelPct, FluidType fluid, long timeMs, TankMarkers m) {
        // Wide open-top bin tapering to bottom discharge
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h * 0.32, 2.0, Material.STAINLESS_STEEL);
        Path2D.Double chute = new Path2D.Double();
        chute.moveTo(2, h * 0.34);
        chute.lineTo(w - 2, h * 0.34);
        chute.lineTo(w * 0.62, h - 2);
        chute.lineTo(w * 0.38, h - 2);
        chute.closePath();
        g.setColor(new Color(0x7C, 0x87, 0x96));
        g.fill(chute);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(chute);
        paintLevelCutaway(g, w * 0.30, h * 0.10, w * 0.40, h * 0.50, levelPct, fluid, timeMs, m);
    }

    private static void paintReactor(Graphics2D g, int w, int h, int gPx,
                                     double levelPct, FluidType fluid, EquipmentState state,
                                     long timeMs, SymbolLayerCache cache, TankMarkers m) {
        // Outer thermal jacket
        ScadaShader.paintBeveledHousing(g, 2, h * 0.26, w - 4, h * 0.58, 4.0, Material.FLANGE_STEEL);
        // Inner reactor vessel
        double vx = w * 0.12;
        double vw = w * 0.76;
        ScadaShader.paintDishedTankHead(g, vx, h * 0.12, vw, h * 0.12, true, Material.STAINLESS_STEEL);
        ScadaShader.paintCylinderV(g, vx, h * 0.24, vw, h * 0.56, Material.STAINLESS_STEEL);
        ScadaShader.paintDishedTankHead(g, vx, h * 0.80, vw, h * 0.12, false, Material.STAINLESS_STEEL);
        paintLevelCutaway(g, w * 0.30, h * 0.28, w * 0.40, h * 0.48, levelPct, fluid, timeMs, m);
        // Top agitator motor + impeller shaft
        ScadaShader.paintBeveledHousing(g, w * 0.38, 1, w * 0.24, h * 0.12, 2.0, Material.CAST_IRON);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(2.0f));
        g.draw(new Line2D.Double(w * 0.50, h * 0.13, w * 0.50, h * 0.70));
        double phase = (state == EquipmentState.RUNNING) ? Math.sin(timeMs / 110.0) : 1.0;
        double span = Math.max(4.0, w * 0.14 * Math.abs(phase));
        g.setColor(ScadaPalette.BEVEL_HIGHLIGHT);
        g.setStroke(new BasicStroke(2.6f));
        g.draw(new Line2D.Double(w * 0.50 - span, h * 0.66, w * 0.50 + span, h * 0.66));
    }

    private static void paintLevelCutaway(Graphics2D g, double x, double y, double w, double h,
                                          double levelPct, FluidType fluid, long timeMs, TankMarkers m) {
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

            // Surface highlight / animated wave
            double waveOffset = (timeMs > 0) ? Math.sin(timeMs / 250.0) * 1.4 : 0.0;
            g.setColor(fluid.getHighlightColor());
            g.fill(new Rectangle2D.Double(x, fillY + waveOffset, w, Math.min(3.0, fillH)));

            // Subtle rising bubbles in liquid
            if (timeMs > 0 && fillH > 12.0 && fluid.getFlowDashColor() != null) {
                g.setColor(new Color(fluid.getFlowDashColor().getRed(), fluid.getFlowDashColor().getGreen(),
                        fluid.getFlowDashColor().getBlue(), 140));
                for (int i = 0; i < 3; i++) {
                    double bx = x + w * (0.25 + i * 0.25);
                    double progress = ((timeMs / 40.0 + i * 17.0) % fillH);
                    double by = y + h - progress;
                    if (by > fillY + 3.0) {
                        g.fill(new Ellipse2D.Double(bx - 1.5, by - 1.5, 3.0, 3.0));
                    }
                }
            }
            g.setClip(oldClip);
        }

        // Alarm bands along right edge of sight glass
        double bandW = Math.max(3.0, w * 0.12);
        double bandX = x + w - bandW;
        if (m.hhPct() != null) {
            double hhY = y + h * (1.0 - Math.min(100.0, Math.max(0.0, m.hhPct())) / 100.0);
            g.setColor(new Color(0xD9, 0x26, 0x26, 200));
            g.fill(new Rectangle2D.Double(bandX, y, bandW, Math.max(2.0, hhY - y)));
        }
        if (m.hPct() != null) {
            double hTop = m.hhPct() != null ? y + h * (1.0 - Math.min(100.0, m.hhPct()) / 100.0) : y;
            double hBot = y + h * (1.0 - Math.min(100.0, Math.max(0.0, m.hPct())) / 100.0);
            if (hBot > hTop) {
                g.setColor(new Color(0xD6, 0x89, 0x10, 180));
                g.fill(new Rectangle2D.Double(bandX, hTop, bandW, hBot - hTop));
            }
        }
        if (m.llPct() != null) {
            double llY = y + h * (1.0 - Math.min(100.0, Math.max(0.0, m.llPct())) / 100.0);
            g.setColor(new Color(0xD9, 0x26, 0x26, 200));
            g.fill(new Rectangle2D.Double(bandX, llY, bandW, Math.max(2.0, y + h - llY)));
        }
        if (m.lPct() != null) {
            double lTop = y + h * (1.0 - Math.min(100.0, Math.max(0.0, m.lPct())) / 100.0);
            double lBot = m.llPct() != null ? y + h * (1.0 - Math.max(0.0, m.llPct()) / 100.0) : y + h;
            if (lBot > lTop) {
                g.setColor(new Color(0xD6, 0x89, 0x10, 180));
                g.fill(new Rectangle2D.Double(bandX, lTop, bandW, lBot - lTop));
            }
        }

        // Graduated ruler ticks
        g.setColor(ScadaPalette.BEVEL_HIGHLIGHT_SOFT);
        g.setStroke(new BasicStroke(1.0f));
        for (int i = 1; i < 5; i++) {
            double ty = y + (h * i) / 5.0;
            g.drawLine((int) x, (int) ty, (int) (x + Math.min(6.0, w * 0.22)), (int) ty);
        }

        // Setpoint marker (dashed cyan line + triangle)
        if (m.setpointPct() != null) {
            double sp = Math.max(0.0, Math.min(100.0, m.setpointPct()));
            double spY = y + h * (1.0 - sp / 100.0);
            g.setColor(new Color(0x00, 0xE5, 0xFF));
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    1.0f, new float[]{3.0f, 2.0f}, 0.0f));
            g.draw(new Line2D.Double(x, spY, x + w, spY));
            Path2D.Double tri = new Path2D.Double();
            tri.moveTo(x - 1, spY);
            tri.lineTo(x - 5, spY - 3);
            tri.lineTo(x - 5, spY + 3);
            tri.closePath();
            g.fill(tri);
        }

        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.0f));
        g.draw(cavity);
    }
}
