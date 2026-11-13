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
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Vector IEC/SCADA renderer for Electrical symbols:
 * PowerSource400V, PowerSource230V, PowerSource24V, CircuitBreaker, Contactor,
 * VfdDrive, Fuse, OverloadRelay, Transformer, DistributionPanel, ElectricMotor.
 */
public final class ElectricalSymbolRenderer {

    private static final Color COPPER = new Color(0xD8, 0x7A, 0x24);
    private static final Color AMBER_ON = new Color(0xFF, 0x98, 0x00);

    private ElectricalSymbolRenderer() {
    }

    public static boolean paint(Graphics2D g, ScadaSymbolInstance sym,
                                double w, double h, EquipmentState state, double phase) {
        return switch (sym.getSymbolType()) {
            case "PowerSource400V", "PowerSource230V", "PowerSource24V" -> {
                paintPowerSource(g, sym, w, h, state);
                yield true;
            }
            case "CircuitBreaker" -> {
                paintCircuitBreaker(g, w, h, state);
                yield true;
            }
            case "Contactor" -> {
                paintContactor(g, w, h, state);
                yield true;
            }
            case "VfdDrive" -> {
                paintVfdDrive(g, sym, w, h, state, phase);
                yield true;
            }
            case "Fuse" -> {
                paintFuse(g, w, h, state);
                yield true;
            }
            case "OverloadRelay" -> {
                paintOverloadRelay(g, w, h, state);
                yield true;
            }
            case "Transformer" -> {
                paintTransformer(g, w, h, state);
                yield true;
            }
            case "DistributionPanel" -> {
                paintDistributionPanel(g, sym, w, h, state);
                yield true;
            }
            case "ElectricMotor" -> {
                paintElectricMotor(g, sym, w, h, state, phase);
                yield true;
            }
            default -> false;
        };
    }

    private static void paintPowerSource(Graphics2D g, ScadaSymbolInstance sym, double w, double h, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 6, Material.FLANGE_STEEL);
        Color accent = state == EquipmentState.FAULT ? ScadaPalette.STATE_FAULT
                : (state == EquipmentState.RUNNING ? AMBER_ON : new Color(0x90, 0xA4, 0xAE));
        g.setColor(accent);
        g.setStroke(new BasicStroke(2.0f));
        double cx = w / 2.0;
        double cy = h * 0.42;
        double r = Math.min(w, h) * 0.22;
        g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        // Lightning / AC symbol
        g.draw(new Line2D.Double(cx - r * 0.5, cy, cx, cy - r * 0.4));
        g.draw(new Line2D.Double(cx, cy - r * 0.4, cx, cy + r * 0.4));
        g.draw(new Line2D.Double(cx, cy + r * 0.4, cx + r * 0.5, cy));

        String v = sym.getProperty("voltage",
                sym.getSymbolType().contains("24") ? "24V" : (sym.getSymbolType().contains("230") ? "230V" : "400V"));
        drawCenteredLabel(g, v, w / 2.0, h * 0.84, Color.WHITE, Math.max(8, (int) (h * 0.18)));
    }

    private static void paintCircuitBreaker(Graphics2D g, double w, double h, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 5, Material.STAINLESS_STEEL);
        boolean closed = state == EquipmentState.RUNNING;
        g.setColor(state == EquipmentState.FAULT ? ScadaPalette.STATE_FAULT : (closed ? AMBER_ON : ScadaPalette.OUTLINE_DARK));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double cx = w / 2.0;
        g.draw(new Line2D.Double(cx, 3, cx, h * 0.30));
        g.draw(new Line2D.Double(cx, h * 0.70, cx, h - 3));
        if (closed) {
            g.draw(new Line2D.Double(cx, h * 0.30, cx, h * 0.70));
        } else {
            g.draw(new Line2D.Double(cx, h * 0.70, cx - w * 0.25, h * 0.32));
        }
        // Crossbar 'x' at breaker pivot
        double px = cx;
        double py = h * 0.30;
        g.draw(new Line2D.Double(px - 3, py - 3, px + 3, py + 3));
        g.draw(new Line2D.Double(px - 3, py + 3, px + 3, py - 3));
        // Status pill
        g.setColor(state == EquipmentState.FAULT ? ScadaPalette.STATE_FAULT : (closed ? ScadaPalette.STATE_RUNNING : new Color(0x90, 0xA4, 0xAE)));
        g.fill(new Ellipse2D.Double(w - 10, 5, 5, 5));
    }

    private static void paintContactor(Graphics2D g, double w, double h, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 5, Material.STAINLESS_STEEL);
        boolean closed = state == EquipmentState.RUNNING;
        // Coil box on left
        g.setColor(closed ? AMBER_ON : new Color(0x60, 0x7D, 0x8B));
        g.fill(new Rectangle2D.Double(5, h * 0.35, w * 0.24, h * 0.30));
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Rectangle2D.Double(5, h * 0.35, w * 0.24, h * 0.30));
        // Dashed mechanical link
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{2f, 2f}, 0f));
        g.draw(new Line2D.Double(5 + w * 0.24, h * 0.5, w * 0.62, h * 0.5));
        // Main contact
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double cx = w * 0.68;
        g.setColor(closed ? AMBER_ON : ScadaPalette.OUTLINE_DARK);
        g.draw(new Line2D.Double(cx, 3, cx, h * 0.30));
        g.draw(new Line2D.Double(cx, h * 0.70, cx, h - 3));
        if (closed) {
            g.draw(new Line2D.Double(cx, h * 0.30, cx, h * 0.70));
        } else {
            g.draw(new Line2D.Double(cx, h * 0.70, cx - w * 0.18, h * 0.34));
        }
    }

    private static void paintVfdDrive(Graphics2D g, ScadaSymbolInstance sym, double w, double h,
                                     EquipmentState state, double phase) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 6, Material.FLANGE_STEEL);

        // Keypad LCD screen
        RoundRectangle2D.Double lcd = new RoundRectangle2D.Double(w * 0.14, h * 0.12, w * 0.72, h * 0.30, 4, 4);
        g.setColor(state == EquipmentState.FAULT ? new Color(0x7F, 0x1D, 0x1D)
                : (state == EquipmentState.RUNNING ? new Color(0x0D, 0x47, 0xA1) : new Color(0x26, 0x32, 0x38)));
        g.fill(lcd);
        g.setColor(new Color(0x90, 0xA4, 0xAE));
        g.setStroke(new BasicStroke(1.0f));
        g.draw(lcd);

        String hzText = (state == EquipmentState.RUNNING)
                ? String.format("%.1f Hz", 35.0 + 15.0 * Math.abs(Math.sin(phase * 0.03)))
                : (state == EquipmentState.FAULT ? "TRIP" : "0.0 Hz");
        drawCenteredLabel(g, hzText, w / 2.0, h * 0.30, Color.WHITE, Math.max(8, (int) (h * 0.12)));

        // Cooling vents + VFD badge
        g.setColor(new Color(0x54, 0x6E, 0x7A));
        for (int i = 0; i < 4; i++) {
            double vy = h * 0.54 + i * (h * 0.08);
            g.fill(new Rectangle2D.Double(w * 0.16, vy, w * 0.68, Math.max(1.5, h * 0.03)));
        }
        drawCenteredLabel(g, "VFD", w / 2.0, h * 0.92, new Color(0xCF, 0xD8, 0xDC), Math.max(8, (int) (h * 0.11)));
    }

    private static void paintFuse(Graphics2D g, double w, double h, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, w * 0.2, 2, w * 0.6, h - 4, 4, Material.STAINLESS_STEEL);
        g.setColor(state == EquipmentState.FAULT ? ScadaPalette.STATE_FAULT : COPPER);
        g.setStroke(new BasicStroke(2.0f));
        g.draw(new Line2D.Double(w / 2.0, 2, w / 2.0, h - 2));
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Rectangle2D.Double(w * 0.3, h * 0.25, w * 0.4, h * 0.5));
    }

    private static void paintOverloadRelay(Graphics2D g, double w, double h, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 4, Material.STAINLESS_STEEL);
        g.setColor(state == EquipmentState.FAULT ? ScadaPalette.STATE_FAULT : ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.8f));
        double cx = w / 2.0;
        g.draw(new Line2D.Double(cx, 3, cx, h * 0.35));
        g.draw(new Line2D.Double(cx, h * 0.35, cx - w * 0.18, h * 0.35));
        g.draw(new Line2D.Double(cx - w * 0.18, h * 0.35, cx - w * 0.18, h * 0.65));
        g.draw(new Line2D.Double(cx - w * 0.18, h * 0.65, cx, h * 0.65));
        g.draw(new Line2D.Double(cx, h * 0.65, cx, h - 3));
    }

    private static void paintTransformer(Graphics2D g, double w, double h, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 5, Material.STAINLESS_STEEL);
        double r = Math.min(w, h) * 0.23;
        double cx = w / 2.0;
        g.setColor(state == EquipmentState.RUNNING ? AMBER_ON : COPPER);
        g.setStroke(new BasicStroke(2.0f));
        g.draw(new Ellipse2D.Double(cx - r, h * 0.36 - r, r * 2, r * 2));
        g.draw(new Ellipse2D.Double(cx - r, h * 0.64 - r, r * 2, r * 2));
    }

    private static void paintDistributionPanel(Graphics2D g, ScadaSymbolInstance sym, double w, double h, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 5, Material.STAINLESS_STEEL);
        // Busbars
        g.setColor(state == EquipmentState.RUNNING ? AMBER_ON : COPPER);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new Line2D.Double(w * 0.20, h * 0.22, w * 0.80, h * 0.22));
        g.draw(new Line2D.Double(w * 0.20, h * 0.78, w * 0.80, h * 0.78));
        // Breaker rows
        g.setColor(new Color(0x37, 0x47, 0x4F));
        int cols = Math.max(3, (int) (w / 14));
        double cw = (w * 0.64) / cols;
        for (int i = 0; i < cols; i++) {
            g.fill(new Rectangle2D.Double(w * 0.18 + i * cw + 1, h * 0.36, Math.max(3, cw - 3), h * 0.28));
        }
        drawCenteredLabel(g, sym.getName(), w / 2.0, h * 0.92, ScadaPalette.TEXT_PRIMARY, 9);
    }

    private static void paintElectricMotor(Graphics2D g, ScadaSymbolInstance sym, double w, double h,
                                           EquipmentState state, double phase) {
        // Cooling-finned stator housing + IEC M circle
        ScadaShader.paintBeveledHousing(g, w * 0.08, h * 0.12, w * 0.84, h * 0.76, 8, Material.FLANGE_STEEL);

        // Cooling fins
        g.setColor(new Color(0x54, 0x6E, 0x7A));
        for (int i = 0; i < 4; i++) {
            double fy = h * 0.22 + i * (h * 0.16);
            g.draw(new Line2D.Double(w * 0.12, fy, w * 0.88, fy));
        }

        // Terminal box on top
        g.setColor(new Color(0x45, 0x5A, 0x64));
        g.fill(new Rectangle2D.Double(w * 0.36, 2, w * 0.28, h * 0.14));
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(new Rectangle2D.Double(w * 0.36, 2, w * 0.28, h * 0.14));

        // Center IEC M circle
        double d = Math.min(w, h) * 0.56;
        double cx = w / 2.0;
        double cy = h / 2.0;
        Ellipse2D.Double badge = new Ellipse2D.Double(cx - d / 2.0, cy - d / 2.0, d, d);
        g.setColor(state == EquipmentState.FAULT ? ScadaPalette.STATE_FAULT
                : (state == EquipmentState.RUNNING ? new Color(0x1B, 0x5E, 0x20) : new Color(0x26, 0x32, 0x38)));
        g.fill(badge);
        g.setColor(state == EquipmentState.RUNNING ? AMBER_ON : Color.WHITE);
        g.setStroke(new BasicStroke(1.6f));
        g.draw(badge);

        drawCenteredLabel(g, "M", cx, cy + 4, Color.WHITE, Math.max(10, (int) (d * 0.45)));
    }

    private static void drawCenteredLabel(Graphics2D g, String text, double cx, double baselineY, Color color, int size) {
        if (text == null || text.isEmpty()) return;
        g.setColor(color);
        g.setFont(new Font("Segoe UI", Font.BOLD, size));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(text);
        g.drawString(text, (float) (cx - tw / 2.0), (float) baselineY);
    }
}
