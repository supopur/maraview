package cz.maramat.scada.render.symbols;

import cz.maramat.scada.render.ScadaPalette;
import cz.maramat.scada.render.ScadaPalette.Material;
import cz.maramat.scada.render.ScadaShader;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

/**
 * 3D procedural renderers for HandValve, SolenoidValve, ControlValve, CheckValve, and Damper.
 */
public final class ValveDamperRenderer {

    private ValveDamperRenderer() {
    }

    public static void paint(Graphics2D g, String type, int w, int h, int gPx,
                             boolean open, double positionPct) {
        double cy = h * 0.60;
        double cx = w * 0.50;

        switch (type) {
            case "HandValve" -> {
                paintValveBowtieBody(g, cx, cy, w, h, open);
                // Stem + handwheel
                ScadaShader.paintCylinderV(g, cx - 2, h * 0.18, 4, cy - h * 0.18, Material.FLANGE_STEEL);
                ScadaShader.paintCylinderH(g, cx - w * 0.28, h * 0.10, w * 0.56, Math.max(4.0, gPx * 0.45), Material.CAST_IRON);
            }
            case "SolenoidValve" -> {
                paintValveBowtieBody(g, cx, cy, w, h, open);
                ScadaShader.paintCylinderV(g, cx - 2, h * 0.28, 4, cy - h * 0.28, Material.FLANGE_STEEL);
                ScadaShader.paintBeveledHousing(g, cx - w * 0.22, h * 0.08, w * 0.44, h * 0.26, 3.0, Material.CAST_IRON);
                Color coilLed = open ? ScadaPalette.STATE_RUNNING_LED : ScadaPalette.STATE_STOPPED_LED;
                ScadaShader.paintLedIndicator(g, cx, h * 0.21, Math.max(2.5, gPx * 0.3), coilLed, open);
            }
            case "ControlValve" -> {
                paintValveBowtieBody(g, cx, cy, w, h, positionPct > 0.0);
                // Rising stem proportional to 0..100%
                double lift = (Math.max(0.0, Math.min(100.0, positionPct)) / 100.0) * (h * 0.12);
                ScadaShader.paintCylinderV(g, cx - 2, h * 0.28 - lift, 4, cy - h * 0.28 + lift, Material.STAINLESS_STEEL);
                // Diaphragm dome actuator
                ScadaShader.paintDishedTankHead(g, cx - w * 0.30, h * 0.08, w * 0.60, h * 0.22, true, Material.CAST_IRON);
            }
            case "CheckValve" -> {
                paintValveBowtieBody(g, cx, cy, w, h, open);
                // Directional check arrow
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(1.6f));
                g.draw(new Line2D.Double(cx - w * 0.16, cy, cx + w * 0.16, cy));
                g.draw(new Line2D.Double(cx + w * 0.04, cy - h * 0.10, cx + w * 0.16, cy));
                g.draw(new Line2D.Double(cx + w * 0.04, cy + h * 0.10, cx + w * 0.16, cy));
            }
            case "Damper" -> {
                ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 2.0, Material.CAST_IRON);
                // Rotating internal louvre blade
                double bladeAngle = Math.toRadians(open ? 10.0 : 85.0);
                double len = Math.min(w, h) * 0.36;
                double dx = Math.cos(bladeAngle) * len;
                double dy = Math.sin(bladeAngle) * len;
                g.setColor(ScadaPalette.BEVEL_HIGHLIGHT);
                g.setStroke(new BasicStroke(3.0f));
                g.draw(new Line2D.Double(w / 2.0 - dx, h / 2.0 - dy, w / 2.0 + dx, h / 2.0 + dy));
                g.setColor(ScadaPalette.OUTLINE_DARK);
                g.fill(new Ellipse2D.Double(w / 2.0 - 3, h / 2.0 - 3, 6, 6));
            }
            default -> paintValveBowtieBody(g, cx, cy, w, h, open);
        }
    }

    private static void paintValveBowtieBody(Graphics2D g, double cx, double cy, int w, int h, boolean open) {
        double halfW = w * 0.42;
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
