package cz.maramat.scada.render.symbols;

import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.render.EquipmentState;
import cz.maramat.scada.render.ScadaPalette;
import cz.maramat.scada.render.ScadaPalette.Material;
import cz.maramat.scada.render.ScadaShader;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Dynamic vector SCADA renderer for Wastewater Treatment Plant (WWTP / ČOV) equipment.
 */
public final class WastewaterRenderer {

    private static final Color RAW_WATER = new Color(0x5D, 0x73, 0x6F);
    private static final Color AERATED_WATER = new Color(0x2E, 0x86, 0xAB);
    private static final Color CLARIFIED_WATER = new Color(0x4F, 0xC3, 0xF7);
    private static final Color SLUDGE_BROWN = new Color(0x5D, 0x40, 0x37);
    private static final Color UV_GLOW = new Color(0xB3, 0x88, 0xFF);

    private WastewaterRenderer() {
    }

    public static boolean paint(Graphics2D g, ScadaSymbolInstance sym,
                                double w, double h, EquipmentState state, double phase) {
        return switch (sym.getSymbolType()) {
            case "BarScreen" -> { paintBarScreen(g, w, h, state, phase); yield true; }
            case "GritChamber" -> { paintGritChamber(g, w, h, state); yield true; }
            case "PrimaryClarifier" -> { paintClarifier(g, w, h, state, phase, false); yield true; }
            case "SecondaryClarifier" -> { paintClarifier(g, w, h, state, phase, true); yield true; }
            case "AerationBasin" -> { paintAerationBasin(g, w, h, state, phase); yield true; }
            case "AirBlower" -> { paintAirBlower(g, w, h, state, phase); yield true; }
            case "SludgeThickener" -> { paintSludgeThickener(g, w, h, state, phase); yield true; }
            case "AnaerobicDigester" -> { paintAnaerobicDigester(g, w, h, state); yield true; }
            case "FilterPress" -> { paintFilterPress(g, w, h, state); yield true; }
            case "UvDisinfection" -> { paintUvDisinfection(g, w, h, state, phase); yield true; }
            case "DosingStation" -> { paintDosingStation(g, w, h, state); yield true; }
            case "SubmersibleMixer" -> { paintSubmersibleMixer(g, w, h, state, phase); yield true; }
            case "ArchimedesScrewPump" -> { paintArchimedesScrew(g, w, h, state, phase); yield true; }
            default -> false;
        };
    }

    private static void paintBarScreen(Graphics2D g, double w, double h, EquipmentState state, double phase) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 4, Material.STAINLESS_STEEL);
        g.setColor(RAW_WATER);
        g.fill(new Rectangle2D.Double(5, h * 0.25, w - 10, h * 0.65));
        // Inclined screen bars
        g.setColor(new Color(0x26, 0x32, 0x38));
        g.setStroke(new BasicStroke(1.8f));
        int bars = Math.max(4, (int) (w / 10));
        double step = (w - 14) / bars;
        for (int i = 0; i <= bars; i++) {
            double bx = 7 + i * step;
            g.draw(new Line2D.Double(bx, h * 0.85, bx + 4, h * 0.18));
        }
        // Rake bar moving when running
        double rakeY = h * 0.30 + (state == EquipmentState.RUNNING ? 10.0 * Math.abs(Math.sin(phase * 0.1)) : 0);
        g.setColor(state == EquipmentState.RUNNING ? ScadaPalette.STATE_RUNNING_LED : new Color(0x90, 0xA4, 0xAE));
        g.fill(new Rectangle2D.Double(6, rakeY, w - 12, 4));
    }

    private static void paintGritChamber(Graphics2D g, double w, double h, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 4, Material.CAST_IRON);
        g.setColor(RAW_WATER);
        g.fill(new Rectangle2D.Double(5, h * 0.22, w - 10, h * 0.50));
        // Settled grit hopper at bottom
        g.setColor(SLUDGE_BROWN);
        Polygon hopper = new Polygon(
                new int[]{5, (int) (w - 5), (int) (w * 0.65), (int) (w * 0.35)},
                new int[]{(int) (h * 0.72), (int) (h * 0.72), (int) (h - 5), (int) (h - 5)},
                4
        );
        g.fill(hopper);
        drawBadge(g, "GRIT", w / 2.0, h * 0.45, Color.WHITE, 9);
    }

    private static void paintClarifier(Graphics2D g, double w, double h, EquipmentState state, double phase, boolean secondary) {
        double d = Math.min(w, h) - 4;
        double cx = w / 2.0;
        double cy = h / 2.0;
        Ellipse2D.Double basin = new Ellipse2D.Double(cx - d / 2.0, cy - d / 2.0, d, d);
        g.setColor(secondary ? CLARIFIED_WATER : RAW_WATER);
        g.fill(basin);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(2.2f));
        g.draw(basin);

        // Center stilling well
        double wd = d * 0.22;
        g.setColor(new Color(0x90, 0xA4, 0xAE));
        g.fill(new Ellipse2D.Double(cx - wd / 2.0, cy - wd / 2.0, wd, wd));
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(new Ellipse2D.Double(cx - wd / 2.0, cy - wd / 2.0, wd, wd));

        // Rotating scraper bridge
        double ang = (state == EquipmentState.RUNNING) ? phase * 0.04 : 0.2;
        g.setColor(new Color(0x37, 0x47, 0x4F));
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(cx, cy, cx + Math.cos(ang) * d * 0.48, cy + Math.sin(ang) * d * 0.48));
    }

    private static void paintAerationBasin(Graphics2D g, double w, double h, EquipmentState state, double phase) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 5, Material.CAST_IRON);
        g.setColor(AERATED_WATER);
        g.fill(new Rectangle2D.Double(5, h * 0.20, w - 10, h * 0.72));

        // Bottom diffuser manifold
        g.setColor(new Color(0x26, 0x32, 0x38));
        g.fill(new Rectangle2D.Double(8, h * 0.84, w - 16, 4));

        // Rising aeration bubbles when running
        if (state == EquipmentState.RUNNING) {
            g.setColor(new Color(0xE1, 0xF5, 0xFE, 210));
            int cols = Math.max(4, (int) (w / 14));
            double step = (w - 18) / cols;
            for (int c = 0; c < cols; c++) {
                double bx = 10 + c * step;
                for (int r = 0; r < 3; r++) {
                    double frac = ((phase * 0.06 + c * 0.31 + r * 0.33) % 1.0);
                    double by = h * 0.82 - frac * (h * 0.56);
                    g.fill(new Ellipse2D.Double(bx - 2.0, by - 2.0, 4.0, 4.0));
                }
            }
        }
        drawBadge(g, "AERATION", w / 2.0, h * 0.16, Color.WHITE, 9);
    }

    private static void paintAirBlower(Graphics2D g, double w, double h, EquipmentState state, double phase) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 6, Material.FLANGE_STEEL);
        double d = Math.min(w, h) * 0.62;
        double cx = w / 2.0;
        double cy = h / 2.0;
        g.setColor(new Color(0xB0, 0xBE, 0xC5));
        g.fill(new Ellipse2D.Double(cx - d / 2.0, cy - d / 2.0, d, d));
        double ang = (state == EquipmentState.RUNNING) ? phase * 0.35 : 0.0;
        g.setColor(new Color(0x26, 0x32, 0x38));
        g.setStroke(new BasicStroke(2.2f));
        for (int i = 0; i < 4; i++) {
            double a = ang + i * Math.PI / 2.0;
            g.draw(new Line2D.Double(cx, cy, cx + Math.cos(a) * d * 0.42, cy + Math.sin(a) * d * 0.42));
        }
    }

    private static void paintSludgeThickener(Graphics2D g, double w, double h, EquipmentState state, double phase) {
        ScadaShader.paintBeveledHousing(g, 3, 3, w - 6, h - 6, 6, Material.CAST_IRON);
        g.setPaint(new GradientPaint(0f, (float) (h * 0.25), RAW_WATER, 0f, (float) (h - 6), SLUDGE_BROWN));
        g.fill(new Rectangle2D.Double(6, h * 0.22, w - 12, h * 0.70));
        // Slow picket fence rake
        double shift = (state == EquipmentState.RUNNING) ? Math.sin(phase * 0.05) * (w * 0.12) : 0;
        g.setColor(new Color(0xCF, 0xD8, 0xDC));
        g.setStroke(new BasicStroke(1.6f));
        g.draw(new Line2D.Double(w / 2.0, 4, w / 2.0, h * 0.85));
        g.draw(new Line2D.Double(w * 0.25 + shift, h * 0.65, w * 0.75 + shift, h * 0.65));
    }

    private static void paintAnaerobicDigester(Graphics2D g, double w, double h, EquipmentState state) {
        // Egg / dome biogas digester
        g.setPaint(new GradientPaint(0f, 0f, new Color(0x78, 0x90, 0x9C), (float) w, 0f, new Color(0x45, 0x5A, 0x64)));
        g.fill(new Arc2D.Double(4, 4, w - 8, h * 0.55, 0, 180, Arc2D.CHORD));
        g.fill(new Rectangle2D.Double(4, h * 0.30, w - 8, h * 0.50));
        Polygon cone = new Polygon(
                new int[]{4, (int) (w - 4), (int) (w / 2.0)},
                new int[]{(int) (h * 0.80), (int) (h * 0.80), (int) (h - 4)},
                3
        );
        g.fill(cone);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new Arc2D.Double(4, 4, w - 8, h * 0.55, 0, 180, Arc2D.OPEN));
        g.draw(new Rectangle2D.Double(4, h * 0.30, w - 8, h * 0.50));
        drawBadge(g, "BIOGAS", w / 2.0, h * 0.26, new Color(0xFF, 0xCA, 0x28), 9);
    }

    private static void paintFilterPress(Graphics2D g, double w, double h, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 4, Material.STAINLESS_STEEL);
        int plates = Math.max(4, (int) (w / 10));
        double step = (w - 18) / plates;
        for (int i = 0; i < plates; i++) {
            double px = 9 + i * step;
            g.setColor(i % 2 == 0 ? new Color(0x37, 0x47, 0x4F) : SLUDGE_BROWN);
            g.fill(new Rectangle2D.Double(px, h * 0.22, Math.max(3, step * 0.65), h * 0.56));
        }
        g.setColor(state == EquipmentState.RUNNING ? ScadaPalette.STATE_RUNNING_LED : new Color(0x78, 0x90, 0x9C));
        g.fill(new Rectangle2D.Double(4, h * 0.44, w - 8, 4));
    }

    private static void paintUvDisinfection(Graphics2D g, double w, double h, EquipmentState state, double phase) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 4, Material.STAINLESS_STEEL);
        g.setColor(CLARIFIED_WATER);
        g.fill(new Rectangle2D.Double(5, h * 0.20, w - 10, h * 0.60));
        g.setColor(state == EquipmentState.RUNNING ? UV_GLOW : new Color(0x90, 0xA4, 0xAE));
        g.setStroke(new BasicStroke(2.8f));
        for (int i = 0; i < 3; i++) {
            double ly = h * (0.32 + i * 0.18);
            g.draw(new Line2D.Double(8, ly, w - 8, ly));
        }
        drawBadge(g, "UV", w / 2.0, h * 0.16, ScadaPalette.TEXT_PRIMARY, 9);
    }

    private static void paintDosingStation(Graphics2D g, double w, double h, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 5, Material.STAINLESS_STEEL);
        g.setColor(new Color(0xAB, 0x47, 0xBC));
        g.fill(new Rectangle2D.Double(6, h * 0.40, w - 12, h * 0.48));
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(new Rectangle2D.Double(6, h * 0.20, w - 12, h * 0.68));
        drawBadge(g, "DOSING", w / 2.0, h * 0.16, ScadaPalette.TEXT_PRIMARY, 8);
    }

    private static void paintSubmersibleMixer(Graphics2D g, double w, double h, EquipmentState state, double phase) {
        ScadaShader.paintBeveledHousing(g, 4, h * 0.25, w * 0.55, h * 0.50, 6, Material.FLANGE_STEEL);
        double px = w * 0.72;
        double py = h * 0.50;
        double span = (state == EquipmentState.RUNNING) ? Math.sin(phase * 0.4) * (h * 0.34) : h * 0.34;
        g.setColor(new Color(0x37, 0x47, 0x4F));
        g.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(w * 0.59, py, px, py));
        g.draw(new Line2D.Double(px, py - span, px, py + span));
    }

    private static void paintArchimedesScrew(Graphics2D g, double w, double h, EquipmentState state, double phase) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 4, Material.CAST_IRON);
        g.setColor(new Color(0x45, 0x5A, 0x64));
        g.setStroke(new BasicStroke(2.2f));
        g.draw(new Line2D.Double(6, h - 6, w - 6, 6));
        int coils = Math.max(4, (int) (w / 10));
        double off = (state == EquipmentState.RUNNING) ? (phase * 0.15) % 1.0 : 0.0;
        g.setColor(new Color(0xB0, 0xBE, 0xC5));
        for (int i = 0; i < coils; i++) {
            double t = (i + off) / coils;
            if (t > 1.0) continue;
            double cx = 6 + t * (w - 12);
            double cy = (h - 6) - t * (h - 12);
            g.draw(new Ellipse2D.Double(cx - 4, cy - 4, 8, 8));
        }
    }

    private static void drawBadge(Graphics2D g, String text, double cx, double y, Color color, int size) {
        g.setColor(color);
        g.setFont(new Font("Segoe UI", Font.BOLD, size));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) y);
    }
}
