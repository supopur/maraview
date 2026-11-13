package cz.maramat.scada.render;

import cz.maramat.scada.model.CardinalDirection;
import cz.maramat.scada.render.ScadaPalette.Material;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Reusable Graphics2D 3D metallic/cylindrical shading toolkit for SCADA equipment rendering.
 */
public final class ScadaShader {

    private static final BasicStroke STROKE_1PX = new BasicStroke(1.0f);
    private static final BasicStroke STROKE_2PX = new BasicStroke(2.0f);

    private ScadaShader() {
    }

    /**
     * Paints a horizontal 3D shaded cylinder with 4-stop vertical gradient,
     * 1px dark outer contour, and 1px inner specular bevel highlight.
     */
    public static void paintCylinderH(Graphics2D g, double x, double y, double w, double h, Material material) {
        if (w <= 0 || h <= 0) return;
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();

        double endY = y + Math.max(1.0, h);
        Rectangle2D.Double rect = new Rectangle2D.Double(x, y, w, h);
        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(x, y),
                new Point2D.Double(x, endY),
                ScadaPalette.STANDARD_FRACTIONS,
                material.getStops()
        ));
        g.fill(rect);

        if (h >= 4.0) {
            g.setColor(ScadaPalette.BEVEL_HIGHLIGHT_SOFT);
            g.setStroke(STROKE_1PX);
            g.draw(new Line2D.Double(x + 1.0, y + 1.0, x + w - 1.0, y + 1.0));
        }

        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(STROKE_1PX);
        g.draw(rect);

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
    }

    /**
     * Paints a vertical 3D shaded cylinder with 4-stop horizontal gradient,
     * 1px dark outer contour, and 1px inner left-edge specular highlight.
     */
    public static void paintCylinderV(Graphics2D g, double x, double y, double w, double h, Material material) {
        if (w <= 0 || h <= 0) return;
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();

        double endX = x + Math.max(1.0, w);
        Rectangle2D.Double rect = new Rectangle2D.Double(x, y, w, h);
        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(x, y),
                new Point2D.Double(endX, y),
                ScadaPalette.STANDARD_FRACTIONS,
                material.getStops()
        ));
        g.fill(rect);

        if (w >= 4.0) {
            g.setColor(ScadaPalette.BEVEL_HIGHLIGHT_SOFT);
            g.setStroke(STROKE_1PX);
            g.draw(new Line2D.Double(x + 1.0, y + 1.0, x + 1.0, y + h - 1.0));
        }

        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(STROKE_1PX);
        g.draw(rect);

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
    }

    /**
     * Paints a 3D shaded elliptical dished tank head (top or bottom cap).
     */
    public static void paintDishedTankHead(Graphics2D g, double x, double y, double w, double h,
                                           boolean topCap, Material material) {
        if (w <= 0 || h <= 0) return;
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();

        double arcHeight = h * 2.0;
        double arcY = topCap ? y : (y - h);
        double startAngle = topCap ? 0.0 : 180.0;
        Arc2D.Double dome = new Arc2D.Double(x, arcY, w, arcHeight, startAngle, 180.0, Arc2D.CHORD);

        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(x, y),
                new Point2D.Double(x + Math.max(1.0, w), y),
                ScadaPalette.STANDARD_FRACTIONS,
                material.getStops()
        ));
        g.fill(dome);

        if (w >= 8.0 && h >= 4.0) {
            g.setColor(ScadaPalette.BEVEL_HIGHLIGHT_SOFT);
            g.setStroke(STROKE_1PX);
            Arc2D.Double innerHighlight = new Arc2D.Double(
                    x + 2.0, arcY + (topCap ? 1.5 : -1.5),
                    w - 4.0, Math.max(2.0, arcHeight - 3.0),
                    topCap ? 25.0 : 205.0, 130.0, Arc2D.OPEN
            );
            g.draw(innerHighlight);
        }

        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(STROKE_1PX);
        g.draw(dome);

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
    }

    /**
     * Paints a raised bolted pipe flange collar centered at (cx, cy) facing the given CardinalDirection.
     */
    public static void paintFlange(Graphics2D g, double cx, double cy, double span, double thickness,
                                   CardinalDirection orientation) {
        if (span <= 0 || thickness <= 0) return;
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();

        boolean horizontalCollar = orientation == CardinalDirection.NORTH || orientation == CardinalDirection.SOUTH;
        double fx = horizontalCollar ? (cx - span / 2.0) : (cx - thickness / 2.0);
        double fy = horizontalCollar ? (cy - thickness / 2.0) : (cy - span / 2.0);
        double fw = horizontalCollar ? span : thickness;
        double fh = horizontalCollar ? thickness : span;

        RoundRectangle2D.Double collar = new RoundRectangle2D.Double(fx, fy, fw, fh, 2.0, 2.0);
        Point2D.Double p1 = new Point2D.Double(fx, fy);
        Point2D.Double p2 = horizontalCollar
                ? new Point2D.Double(fx + Math.max(1.0, fw), fy)
                : new Point2D.Double(fx, fy + Math.max(1.0, fh));

        g.setPaint(new LinearGradientPaint(p1, p2, ScadaPalette.STANDARD_FRACTIONS, Material.FLANGE_STEEL.getStops()));
        g.fill(collar);

        // Bolt heads near ends of flange
        if (span >= 12.0 && thickness >= 4.0) {
            double boltR = Math.min(1.8, thickness * 0.28);
            g.setColor(ScadaPalette.BEVEL_HIGHLIGHT);
            if (horizontalCollar) {
                g.fill(new Ellipse2D.Double(fx + 2.5 - boltR, cy - boltR, boltR * 2, boltR * 2));
                g.fill(new Ellipse2D.Double(fx + fw - 2.5 - boltR, cy - boltR, boltR * 2, boltR * 2));
            } else {
                g.fill(new Ellipse2D.Double(cx - boltR, fy + 2.5 - boltR, boltR * 2, boltR * 2));
                g.fill(new Ellipse2D.Double(cx - boltR, fy + fh - 2.5 - boltR, boltR * 2, boltR * 2));
            }
        }

        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(STROKE_1PX);
        g.draw(collar);

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
    }

    /**
     * Paints a beveled 3D rectangular housing (motor body, valve actuator, junction box).
     */
    public static void paintBeveledHousing(Graphics2D g, double x, double y, double w, double h,
                                           double cornerArc, Material material) {
        if (w <= 0 || h <= 0) return;
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();

        RoundRectangle2D.Double body = new RoundRectangle2D.Double(x, y, w, h, cornerArc, cornerArc);
        g.setPaint(new LinearGradientPaint(
                new Point2D.Double(x, y),
                new Point2D.Double(x, y + Math.max(1.0, h)),
                ScadaPalette.STANDARD_FRACTIONS,
                material.getStops()
        ));
        g.fill(body);

        if (w >= 6.0 && h >= 6.0) {
            g.setColor(ScadaPalette.BEVEL_HIGHLIGHT_SOFT);
            g.setStroke(STROKE_1PX);
            g.draw(new Line2D.Double(x + 1.5, y + 1.0, x + w - 1.5, y + 1.0));
            g.draw(new Line2D.Double(x + 1.0, y + 1.5, x + 1.0, y + h - 1.5));
        }

        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(STROKE_1PX);
        g.draw(body);

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
    }

    /**
     * Paints a 3D cast-iron centrifugal pump volute casing with a recessed circular sight-glass cutout
     * and drop-shadow inner ring.
     */
    public static void paintVoluteCasing(Graphics2D g, double cx, double cy,
                                         double outerRadius, double cutoutRadius, Material material) {
        if (outerRadius <= 1.0) return;
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();

        Ellipse2D.Double outer = new Ellipse2D.Double(
                cx - outerRadius, cy - outerRadius, outerRadius * 2.0, outerRadius * 2.0
        );
        Color[] stops = material.getStops();
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(cx - outerRadius * 0.25, cy - outerRadius * 0.25),
                (float) Math.max(2.0, outerRadius * 1.15),
                ScadaPalette.STANDARD_FRACTIONS,
                new Color[]{stops[1], stops[2], stops[0], stops[3]}
        ));
        g.fill(outer);

        // Subtle outer bevel highlight arc on top-left
        g.setColor(ScadaPalette.BEVEL_HIGHLIGHT_SOFT);
        g.setStroke(STROKE_1PX);
        g.draw(new Arc2D.Double(
                cx - outerRadius + 1.0, cy - outerRadius + 1.0,
                outerRadius * 2.0 - 2.0, outerRadius * 2.0 - 2.0,
                45.0, 180.0, Arc2D.OPEN
        ));

        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(STROKE_1PX);
        g.draw(outer);

        if (cutoutRadius > 1.0 && cutoutRadius < outerRadius) {
            Ellipse2D.Double cavity = new Ellipse2D.Double(
                    cx - cutoutRadius, cy - cutoutRadius, cutoutRadius * 2.0, cutoutRadius * 2.0
            );
            g.setPaint(new LinearGradientPaint(
                    new Point2D.Double(cx, cy - cutoutRadius),
                    new Point2D.Double(cx, cy + cutoutRadius),
                    new float[]{0.0f, 1.0f},
                    new Color[]{ScadaPalette.CUTOUT_DARK, ScadaPalette.CUTOUT_LIGHT}
            ));
            g.fill(cavity);

            // Recessed shadow ring
            g.setColor(ScadaPalette.OUTLINE_DARK);
            g.setStroke(STROKE_1PX);
            g.draw(cavity);
        }

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
    }

    /**
     * Paints an n-blade alternating blue/navy rotor impeller wheel with metallic center hub cap.
     */
    public static void paintRotorImpeller(Graphics2D g, double cx, double cy,
                                          double radius, int bladeCount, double angleRad) {
        if (radius <= 1.0 || bladeCount <= 0) return;
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();

        double step = (Math.PI * 2.0) / bladeCount;
        double innerR = radius * 0.24;

        for (int i = 0; i < bladeCount; i++) {
            double a0 = angleRad + i * step;
            double a1 = a0 + step * 0.84;

            Path2D.Double blade = new Path2D.Double();
            blade.moveTo(cx + Math.cos(a0) * innerR, cy + Math.sin(a0) * innerR);
            blade.lineTo(cx + Math.cos(a0 + step * 0.12) * radius, cy + Math.sin(a0 + step * 0.12) * radius);
            blade.lineTo(cx + Math.cos(a1) * radius, cy + Math.sin(a1) * radius);
            blade.lineTo(cx + Math.cos(a1 - step * 0.08) * innerR, cy + Math.sin(a1 - step * 0.08) * innerR);
            blade.closePath();

            boolean primary = (i & 1) == 0;
            if (primary) {
                g.setPaint(new LinearGradientPaint(
                        new Point2D.Double(cx + Math.cos(a0) * innerR, cy + Math.sin(a0) * innerR),
                        new Point2D.Double(cx + Math.cos(a1) * radius, cy + Math.sin(a1) * radius),
                        new float[]{0.0f, 1.0f},
                        new Color[]{ScadaPalette.ROTOR_BLADE_A_HIGHLIGHT, ScadaPalette.ROTOR_BLADE_A}
                ));
            } else {
                g.setColor(ScadaPalette.ROTOR_BLADE_B);
            }
            g.fill(blade);

            g.setColor(ScadaPalette.OUTLINE_DARK);
            g.setStroke(STROKE_1PX);
            g.draw(blade);
        }

        // Center metallic hub cap
        double hubR = Math.max(2.0, radius * 0.28);
        Ellipse2D.Double hub = new Ellipse2D.Double(cx - hubR, cy - hubR, hubR * 2.0, hubR * 2.0);
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(cx - hubR * 0.3, cy - hubR * 0.3),
                (float) Math.max(1.5, hubR * 1.2),
                ScadaPalette.STANDARD_FRACTIONS,
                Material.STAINLESS_STEEL.getStops()
        ));
        g.fill(hub);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(STROKE_1PX);
        g.draw(hub);

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
    }

    /**
     * Paints a 3D domed LED indicator lamp with dark bezel and radial specular highlight dot.
     */
    public static void paintLedIndicator(Graphics2D g, double cx, double cy, double radius, Color baseColor, boolean lit) {
        if (radius <= 0.5) return;
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();

        Color c = (baseColor == null) ? ScadaPalette.STATE_STOPPED_LED : baseColor;
        Color centerColor = lit ? brighten(c, 1.35f) : darken(c, 0.75f);
        Color edgeColor = lit ? darken(c, 0.7f) : darken(c, 0.45f);

        Ellipse2D.Double dome = new Ellipse2D.Double(cx - radius, cy - radius, radius * 2.0, radius * 2.0);
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(cx - radius * 0.3, cy - radius * 0.3),
                (float) Math.max(1.0, radius * 1.15),
                new float[]{0.0f, 0.7f, 1.0f},
                new Color[]{centerColor, c, edgeColor}
        ));
        g.fill(dome);

        if (lit && radius >= 2.5) {
            double specR = Math.max(1.0, radius * 0.25);
            g.setColor(new Color(255, 255, 255, 210));
            g.fill(new Ellipse2D.Double(cx - radius * 0.45, cy - radius * 0.45, specR * 2.0, specR * 2.0));
        }

        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(STROKE_1PX);
        g.draw(dome);

        g.setPaint(oldPaint);
        g.setStroke(oldStroke);
    }

    /**
     * Paints the equipment state overlay (amber warning border or 2 Hz blinking 2px red fault halo).
     */
    public static void paintStateOverlay(Graphics2D g, double x, double y, double w, double h,
                                         EquipmentState state, long timeMs) {
        if (state == null || state == EquipmentState.STOPPED || state == EquipmentState.RUNNING) {
            return;
        }
        Stroke oldStroke = g.getStroke();
        Color oldColor = g.getColor();

        if (state == EquipmentState.WARNING) {
            g.setColor(ScadaPalette.STATE_WARNING);
            g.setStroke(STROKE_2PX);
            g.draw(new Rectangle2D.Double(x - 1.0, y - 1.0, w + 2.0, h + 2.0));
        } else if (state == EquipmentState.FAULT && EquipmentState.isFaultFlashOn(timeMs)) {
            g.setColor(ScadaPalette.STATE_FAULT);
            g.setStroke(STROKE_2PX);
            Shape halo = new Rectangle2D.Double(x - 2.0, y - 2.0, w + 4.0, h + 4.0);
            g.draw(halo);
        }

        g.setColor(oldColor);
        g.setStroke(oldStroke);
    }

    private static Color brighten(Color c, float factor) {
        int r = Math.min(255, Math.round(c.getRed() * factor));
        int g = Math.min(255, Math.round(c.getGreen() * factor));
        int b = Math.min(255, Math.round(c.getBlue() * factor));
        return new Color(r, g, b, c.getAlpha());
    }

    private static Color darken(Color c, float factor) {
        int r = Math.max(0, Math.round(c.getRed() * factor));
        int g = Math.max(0, Math.round(c.getGreen() * factor));
        int b = Math.max(0, Math.round(c.getBlue() * factor));
        return new Color(r, g, b, c.getAlpha());
    }
}
