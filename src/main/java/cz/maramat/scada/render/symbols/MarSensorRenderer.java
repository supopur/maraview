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
 * Vector renderer for MaR (Měření a regulace) sensors, thermostats, and DDC/PID controllers.
 */
public final class MarSensorRenderer {

    private MarSensorRenderer() {
    }

    public static boolean paint(Graphics2D g, ScadaSymbolInstance sym,
                                double w, double h, EquipmentState state, double numericValue) {
        return switch (sym.getSymbolType()) {
            case "OutdoorTempSensor" -> { paintBadgeSensor(g, "To", w, h, state); yield true; }
            case "DiffPressureSensor" -> { paintBadgeSensor(g, "dP", w, h, state); yield true; }
            case "HumiditySensor" -> { paintBadgeSensor(g, "%RH", w, h, state); yield true; }
            case "Co2Sensor" -> { paintBadgeSensor(g, "CO2", w, h, state); yield true; }
            case "Calorimeter" -> { paintCalorimeter(g, w, h, state, numericValue); yield true; }
            case "RoomThermostat" -> { paintRoomThermostat(g, sym, w, h, state, numericValue); yield true; }
            case "MarController" -> { paintMarController(g, sym, w, h, state); yield true; }
            case "PidController" -> { paintPidController(g, sym, w, h, state, numericValue); yield true; }
            default -> false;
        };
    }

    private static void paintBadgeSensor(Graphics2D g, String tag, double w, double h, EquipmentState state) {
        double d = Math.min(w, h) * 0.72;
        double cx = w / 2.0;
        double cy = h * 0.45;
        Ellipse2D.Double dial = new Ellipse2D.Double(cx - d / 2.0, cy - d / 2.0, d, d);
        g.setColor(new Color(0xE8, 0xEE, 0xF5));
        g.fill(dial);
        g.setColor(state == EquipmentState.FAULT ? ScadaPalette.STATE_FAULT
                : (state == EquipmentState.RUNNING ? new Color(0x00, 0x89, 0x7B) : ScadaPalette.OUTLINE_DARK));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(dial);
        g.draw(new Line2D.Double(cx, cy + d / 2.0, cx, h - 2));

        g.setColor(ScadaPalette.TEXT_PRIMARY);
        g.setFont(new Font("Segoe UI", Font.BOLD, Math.max(8, (int) (d * 0.34))));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(tag, (float) (cx - fm.stringWidth(tag) / 2.0), (float) (cy + fm.getAscent() * 0.35));
    }

    private static void paintCalorimeter(Graphics2D g, double w, double h, EquipmentState state, double val) {
        ScadaShader.paintBeveledHousing(g, 3, 3, w - 6, h - 6, 5, Material.STAINLESS_STEEL);
        g.setColor(new Color(0x1E, 0x24, 0x2B));
        g.fill(new RoundRectangle2D.Double(6, 6, w - 12, h * 0.42, 3, 3));
        g.setColor(new Color(0x69, 0xF0, 0xAE));
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.drawString(String.format("%.1fkW", val), 9, (float) (6 + h * 0.28));
    }

    private static void paintRoomThermostat(Graphics2D g, ScadaSymbolInstance sym,
                                            double w, double h, EquipmentState state, double val) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 6, Material.STAINLESS_STEEL);
        g.setColor(new Color(0xE3, 0xF2, 0xFD));
        g.fill(new RoundRectangle2D.Double(6, 6, w - 12, h * 0.50, 4, 4));
        g.setColor(new Color(0x0D, 0x47, 0xA1));
        g.setFont(new Font("Segoe UI", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        String txt = String.format("%.1f°C", val > 0.1 ? val : 21.5);
        g.drawString(txt, (float) (w / 2.0 - fm.stringWidth(txt) / 2.0), (float) (h * 0.36));
    }

    private static void paintMarController(Graphics2D g, ScadaSymbolInstance sym, double w, double h, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 5, Material.FLANGE_STEEL);

        // Status LEDs
        g.setColor(state == EquipmentState.RUNNING ? new Color(0x00, 0xE6, 0x76) : new Color(0x90, 0xA4, 0xAE));
        g.fill(new Ellipse2D.Double(8, 8, 6, 6));
        g.setColor(state == EquipmentState.FAULT ? ScadaPalette.STATE_FAULT : new Color(0x45, 0x5A, 0x64));
        g.fill(new Ellipse2D.Double(18, 8, 6, 6));

        // I/O terminal strip along bottom
        g.setColor(new Color(0x00, 0x89, 0x7B));
        g.fill(new Rectangle2D.Double(6, h - 12, w - 12, 7));

        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, Math.max(9, (int) (h * 0.16))));
        FontMetrics fm = g.getFontMetrics();
        String title = "DDC / MaR";
        g.drawString(title, (float) (w / 2.0 - fm.stringWidth(title) / 2.0), (float) (h * 0.52));
    }

    private static void paintPidController(Graphics2D g, ScadaSymbolInstance sym,
                                           double w, double h, EquipmentState state, double val) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 5, Material.FLANGE_STEEL);
        g.setColor(new Color(0x11, 0x16, 0x1B));
        g.fill(new RoundRectangle2D.Double(6, 18, w - 12, h - 24, 3, 3));

        g.setColor(new Color(0xB0, 0xBE, 0xC5));
        g.setFont(new Font("Segoe UI", Font.BOLD, 9));
        g.drawString("PID", 8, 14);

        g.setColor(new Color(0x69, 0xF0, 0xAE));
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.drawString(String.format("PV: %.1f", val), 10, 34);
        g.setColor(new Color(0x40, 0xC4, 0xFF));
        g.drawString("SP: " + sym.getProperty("setpoint", "21.0"), 10, 48);
    }
}
