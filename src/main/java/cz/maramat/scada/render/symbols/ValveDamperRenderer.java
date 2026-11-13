package cz.maramat.scada.render.symbols;

import cz.maramat.scada.render.ScadaPalette;
import cz.maramat.scada.render.ScadaPalette.Material;
import cz.maramat.scada.render.ScadaShader;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * 3D procedural renderers for all valve types and dampers, including position % fill and fail-open/fail-closed badges.
 */
public final class ValveDamperRenderer {

    private ValveDamperRenderer() {
    }

    public static void paint(Graphics2D g, String type, int w, int h, int gPx,
                             boolean open, double positionPct) {
        paint(g, type, w, h, gPx, open, positionPct, null);
    }

    public static void paint(Graphics2D g, String type, int w, int h, int gPx,
                             boolean open, double positionPct, String failSafe) {
        double cy = (type.equals("HandValve") || type.equals("CheckValve")
                || type.equals("BallValve") || type.equals("ButterflyValve")
                || type.equals("PlugValve") || type.equals("FourWayValve")) ? h * 0.50
                : (type.equals("MotorizedValve") || type.equals("PneumaticValve") ? h * 0.65 : h * 0.60);
        double cx = w * 0.50;
        double clampedPos = Math.max(0.0, Math.min(100.0, positionPct));

        switch (type) {
            case "HandValve" -> {
                paintValveBowtieBody(g, cx, cy, w, h, open);
                ScadaShader.paintCylinderV(g, cx - 2, h * 0.18, 4, cy - h * 0.18, Material.FLANGE_STEEL);
                ScadaShader.paintCylinderH(g, cx - w * 0.28, h * 0.10, w * 0.56, Math.max(4.0, gPx * 0.45), Material.CAST_IRON);
            }
            case "GateValve" -> {
                paintValveBowtieBody(g, cx, cy, w, h, open);
                // Vertical gate bar across seat
                g.setColor(ScadaPalette.BEVEL_HIGHLIGHT);
                g.fill(new Rectangle2D.Double(cx - 2.5, cy - h * 0.22, 5.0, h * 0.44));
                g.setColor(ScadaPalette.OUTLINE_DARK);
                g.draw(new Rectangle2D.Double(cx - 2.5, cy - h * 0.22, 5.0, h * 0.44));
                ScadaShader.paintCylinderV(g, cx - 2, h * 0.14, 4, cy - h * 0.14, Material.FLANGE_STEEL);
                ScadaShader.paintCylinderH(g, cx - w * 0.26, h * 0.08, w * 0.52, Math.max(4.0, gPx * 0.40), Material.CAST_IRON);
            }
            case "GlobeValve" -> {
                paintValveBowtieBody(g, cx, cy, w, h, open);
                // Center globe disc
                double r = Math.min(w, h) * 0.15;
                g.setColor(ScadaPalette.OUTLINE_DARK);
                g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2.0, r * 2.0));
                ScadaShader.paintCylinderV(g, cx - 2, h * 0.14, 4, cy - h * 0.14, Material.FLANGE_STEEL);
                ScadaShader.paintCylinderH(g, cx - w * 0.26, h * 0.08, w * 0.52, Math.max(4.0, gPx * 0.40), Material.CAST_IRON);
            }
            case "BallValve" -> {
                paintValveBowtieBody(g, cx, cy, w, h, open);
                double br = Math.min(w, h) * 0.18;
                g.setColor(open ? Color.WHITE : ScadaPalette.CUTOUT_DARK);
                g.fill(new Ellipse2D.Double(cx - br, cy - br, br * 2.0, br * 2.0));
                g.setColor(ScadaPalette.OUTLINE_DARK);
                g.draw(new Ellipse2D.Double(cx - br, cy - br, br * 2.0, br * 2.0));
                // Quarter-turn lever
                g.setStroke(new BasicStroke(2.2f));
                if (open) {
                    g.draw(new Line2D.Double(cx - w * 0.20, cy - br - 3, cx + w * 0.20, cy - br - 3));
                } else {
                    g.draw(new Line2D.Double(cx, cy - br - 8, cx, cy - br));
                }
            }
            case "ButterflyValve" -> {
                paintValveBowtieBody(g, cx, cy, w, h, open);
                double r = Math.min(w, h) * 0.22;
                g.setColor(ScadaPalette.OUTLINE_DARK);
                g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2.0, r * 2.0));
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2.4f));
                if (open) {
                    g.draw(new Line2D.Double(cx - r * 0.85, cy, cx + r * 0.85, cy));
                } else {
                    g.draw(new Line2D.Double(cx, cy - r * 0.85, cx, cy + r * 0.85));
                }
            }
            case "NeedleValve" -> {
                paintValveBowtieBody(g, cx, cy, w, h, open);
                // Sharp needle triangle pointing down into seat
                Path2D.Double needle = new Path2D.Double();
                needle.moveTo(cx - 3.5, cy - h * 0.20);
                needle.lineTo(cx + 3.5, cy - h * 0.20);
                needle.lineTo(cx, cy + 2.0);
                needle.closePath();
                g.setColor(Color.WHITE);
                g.fill(needle);
                ScadaShader.paintCylinderV(g, cx - 1.5, h * 0.14, 3, cy - h * 0.20, Material.STAINLESS_STEEL);
                ScadaShader.paintCylinderH(g, cx - w * 0.22, h * 0.08, w * 0.44, Math.max(3.5, gPx * 0.35), Material.CAST_IRON);
            }
            case "PlugValve" -> {
                paintValveBowtieBody(g, cx, cy, w, h, open);
                double pw = w * 0.18;
                double ph = h * 0.36;
                g.setColor(open ? Color.WHITE : ScadaPalette.CUTOUT_DARK);
                g.fill(new Rectangle2D.Double(cx - pw / 2.0, cy - ph / 2.0, pw, ph));
                g.setColor(ScadaPalette.OUTLINE_DARK);
                g.draw(new Rectangle2D.Double(cx - pw / 2.0, cy - ph / 2.0, pw, ph));
            }
            case "DiaphragmValve" -> {
                paintValveBowtieBody(g, cx, cy, w, h, open);
                g.setColor(ScadaPalette.BEVEL_HIGHLIGHT);
                g.setStroke(new BasicStroke(2.2f));
                g.draw(new Arc2D.Double(cx - w * 0.20, cy - h * 0.18, w * 0.40, h * 0.26, 0, 180, Arc2D.OPEN));
                ScadaShader.paintCylinderV(g, cx - 2, h * 0.18, 4, cy - h * 0.18, Material.FLANGE_STEEL);
                ScadaShader.paintDishedTankHead(g, cx - w * 0.24, h * 0.08, w * 0.48, h * 0.14, true, Material.CAST_IRON);
            }
            case "FourWayValve" -> {
                // Horizontal + vertical cross bowties
                paintValveBowtieBody(g, cx, cy, w, h, open);
                double halfW = h * 0.24;
                double halfH = h * 0.46;
                Path2D.Double vert = new Path2D.Double();
                vert.moveTo(cx - halfW, cy - halfH);
                vert.lineTo(cx + halfW, cy + halfH);
                vert.lineTo(cx - halfW, cy + halfH);
                vert.lineTo(cx + halfW, cy - halfH);
                vert.closePath();
                g.setColor(open ? new Color(0x1E, 0x84, 0x49) : new Color(0x63, 0x6D, 0x7A));
                g.fill(vert);
                g.setColor(ScadaPalette.OUTLINE_DARK);
                g.draw(vert);
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double(cx - 4, cy - 4, 8, 8));
            }
            case "SolenoidValve" -> {
                paintValveBowtieBody(g, cx, cy, w, h, open);
                ScadaShader.paintCylinderV(g, cx - 2, h * 0.28, 4, cy - h * 0.28, Material.FLANGE_STEEL);
                ScadaShader.paintBeveledHousing(g, cx - w * 0.22, h * 0.08, w * 0.44, h * 0.26, 3.0, Material.CAST_IRON);
                Color coilLed = open ? ScadaPalette.STATE_RUNNING_LED : ScadaPalette.STATE_STOPPED_LED;
                ScadaShader.paintLedIndicator(g, cx, h * 0.21, Math.max(2.5, gPx * 0.3), coilLed, open);
            }
            case "ControlValve" -> {
                paintValveBowtieBody(g, cx, cy, w, h, clampedPos > 0.0);
                double lift = (clampedPos / 100.0) * (h * 0.12);
                ScadaShader.paintCylinderV(g, cx - 2, h * 0.28 - lift, 4, cy - h * 0.28 + lift, Material.STAINLESS_STEEL);
                ScadaShader.paintDishedTankHead(g, cx - w * 0.30, h * 0.08, w * 0.60, h * 0.22, true, Material.CAST_IRON);
                paintPositionFillBar(g, w, h, clampedPos);
            }
            case "MotorizedValve" -> {
                paintValveBowtieBody(g, cx, cy, w, h, clampedPos > 0.0 || open);
                ScadaShader.paintCylinderV(g, cx - 2, h * 0.32, 4, cy - h * 0.32, Material.FLANGE_STEEL);
                ScadaShader.paintBeveledHousing(g, cx - w * 0.26, h * 0.06, w * 0.52, h * 0.28, 4.0, Material.CAST_IRON);
                drawSmallText(g, "M", cx, h * 0.24, Color.WHITE, 9);
                paintPositionFillBar(g, w, h, clampedPos);
            }
            case "PneumaticValve" -> {
                paintValveBowtieBody(g, cx, cy, w, h, clampedPos > 0.0 || open);
                ScadaShader.paintCylinderV(g, cx - 2, h * 0.30, 4, cy - h * 0.30, Material.STAINLESS_STEEL);
                ScadaShader.paintDishedTankHead(g, cx - w * 0.30, h * 0.06, w * 0.60, h * 0.24, true, Material.FLANGE_STEEL);
                drawSmallText(g, "P", cx, h * 0.22, Color.WHITE, 8);
                paintPositionFillBar(g, w, h, clampedPos);
            }
            case "CheckValve" -> {
                paintValveBowtieBody(g, cx, cy, w, h, open);
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(1.6f));
                g.draw(new Line2D.Double(cx - w * 0.16, cy, cx + w * 0.16, cy));
                g.draw(new Line2D.Double(cx + w * 0.04, cy - h * 0.10, cx + w * 0.16, cy));
                g.draw(new Line2D.Double(cx + w * 0.04, cy + h * 0.10, cx + w * 0.16, cy));
            }
            case "Damper" -> {
                ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 2.0, Material.CAST_IRON);
                // Position-based blade angle: 0% = closed (85 deg), 100% = open (5 deg)
                double effectivePct = (positionPct >= 0.0) ? clampedPos : (open ? 100.0 : 0.0);
                double bladeAngle = Math.toRadians(85.0 - (effectivePct / 100.0) * 80.0);
                double len = Math.min(w, h) * 0.36;
                double dx = Math.cos(bladeAngle) * len;
                double dy = Math.sin(bladeAngle) * len;
                g.setColor(ScadaPalette.BEVEL_HIGHLIGHT);
                g.setStroke(new BasicStroke(3.0f));
                g.draw(new Line2D.Double(w / 2.0 - dx, h / 2.0 - dy, w / 2.0 + dx, h / 2.0 + dy));
                g.setColor(ScadaPalette.OUTLINE_DARK);
                g.fill(new Ellipse2D.Double(w / 2.0 - 3, h / 2.0 - 3, 6, 6));
                paintPositionFillBar(g, w, h, effectivePct);
            }
            case "Inlet" -> paintBoundaryPort(g, w, h, true);
            case "Outlet" -> paintBoundaryPort(g, w, h, false);
            default -> paintValveBowtieBody(g, cx, cy, w, h, open);
        }

        if (failSafe != null && !failSafe.isBlank()) {
            paintFailSafeBadge(g, w, h, failSafe);
        }
    }

    private static void paintPositionFillBar(Graphics2D g, int w, int h, double pct) {
        double barW = w * 0.68;
        double barH = Math.max(4.0, h * 0.11);
        double bx = (w - barW) / 2.0;
        double by = h - barH - 2.0;
        g.setColor(ScadaPalette.CUTOUT_DARK);
        g.fill(new Rectangle2D.Double(bx, by, barW, barH));
        double fillW = (Math.max(0.0, Math.min(100.0, pct)) / 100.0) * barW;
        g.setColor(pct > 0.0 ? new Color(0x26, 0xA6, 0x9A) : ScadaPalette.STATE_STOPPED_LED);
        g.fill(new Rectangle2D.Double(bx, by, fillW, barH));
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(0.9f));
        g.draw(new Rectangle2D.Double(bx, by, barW, barH));
    }

    private static void paintFailSafeBadge(Graphics2D g, int w, int h, String failSafe) {
        String code;
        Color bg;
        if ("FO".equalsIgnoreCase(failSafe) || "FAIL_OPEN".equalsIgnoreCase(failSafe)) {
            code = "FO";
            bg = new Color(0x1E, 0x84, 0x49);
        } else if ("FC".equalsIgnoreCase(failSafe) || "FAIL_CLOSED".equalsIgnoreCase(failSafe)) {
            code = "FC";
            bg = new Color(0xC6, 0x28, 0x28);
        } else {
            return;
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 7));
        FontMetrics fm = g.getFontMetrics();
        int bw = fm.stringWidth(code) + 4;
        int bh = 10;
        int bx = w - bw - 1;
        int by = 1;
        g.setColor(bg);
        g.fillRoundRect(bx, by, bw, bh, 3, 3);
        g.setColor(Color.WHITE);
        g.drawString(code, bx + 2, by + bh - 2);
    }

    private static void drawSmallText(Graphics2D g, String text, double cx, double y, Color color, int size) {
        g.setColor(color);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) y);
    }

    private static void paintBoundaryPort(Graphics2D g, int w, int h, boolean isInlet) {
        double midY = h * 0.50;
        double bodyW = w * 0.72;
        double bodyH = h * 0.64;
        double bx = isInlet ? 2.0 : (w - bodyW - 2.0);
        double by = (h - bodyH) / 2.0;

        ScadaShader.paintBeveledHousing(g, bx, by, bodyW, bodyH, 5.0, Material.FLANGE_STEEL);

        // Directional chevron pointing EAST (into process for Inlet, out to drain for Outlet)
        double acx = bx + bodyW * 0.50;
        double aw = bodyW * 0.34;
        double ah = bodyH * 0.28;
        Path2D.Double arrow = new Path2D.Double();
        arrow.moveTo(acx - aw, midY - ah * 0.55);
        arrow.lineTo(acx, midY - ah * 0.55);
        arrow.lineTo(acx, midY - ah);
        arrow.lineTo(acx + aw, midY);
        arrow.lineTo(acx, midY + ah);
        arrow.lineTo(acx, midY + ah * 0.55);
        arrow.lineTo(acx - aw, midY + ah * 0.55);
        arrow.closePath();

        g.setColor(isInlet ? new Color(0x1E, 0x88, 0xE5) : new Color(0x00, 0x89, 0x7B));
        g.fill(arrow);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.1f));
        g.draw(arrow);
    }

    private static void paintValveBowtieBody(Graphics2D g, double cx, double cy, int w, int h, boolean open) {
        double halfW = w * 0.50;
        double halfH = h * 0.24;
        Path2D.Double bowtie = new Path2D.Double();
        bowtie.moveTo(cx - halfW, cy - halfH);
        bowtie.lineTo(cx + halfW, cy + halfH);
        bowtie.lineTo(cx + halfW, cy - halfH);
        bowtie.lineTo(cx - halfW, cy + halfH);
        bowtie.closePath();

        g.setColor(open ? new Color(0x1E, 0x84, 0x49) : new Color(0x63, 0x6D, 0x7A));
        g.fill(bowtie);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(bowtie);

        // Center hub
        double hubR = Math.max(3.0, Math.min(w, h) * 0.12);
        ScadaShader.paintVoluteCasing(g, cx, cy, hubR, 0.0, Material.FLANGE_STEEL);
    }
}
