package cz.maramat.scada.render.symbols;

import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.render.EquipmentState;
import cz.maramat.scada.render.ScadaPalette;
import cz.maramat.scada.render.ScadaPalette.Material;
import cz.maramat.scada.render.ScadaShader;
import cz.maramat.scada.render.SymbolLayerCache;
import cz.maramat.scada.render.SymbolLayerCache.CacheKey;
import cz.maramat.scada.tags.RuntimeTagProvider;
import cz.maramat.scada.tags.ScadaTag;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3D procedural renderers for Epoch 5 Sensors, Instrumentation, Operator Controls, and Displays.
 */
public final class InstrumentationControlRenderer implements EquipmentSymbolRenderer.ExtensionRenderer {

    private static final int MAX_TREND_POINTS = 64;
    private static final long TREND_SAMPLE_INTERVAL_MS = 200L;
    private static final Color TREND_GRID_COLOR = new Color(0x2E, 0x35, 0x3E);
    private static final Color TREND_LINE_COLOR = new Color(0x2E, 0xCC, 0x71);
    private static final java.awt.Stroke TREND_STROKE = new BasicStroke(1.5f);
    private static final Color NUM_EDIT_BG = new Color(0x1B, 0x26, 0x31);
    private static final Color NUM_EDIT_BORDER = new Color(0x54, 0x99, 0xC7);
    private static final Color NUM_EDIT_TEXT = new Color(0xAE, 0xD6, 0xF1);
    private static final Color NUM_RO_TEXT = new Color(0x2E, 0xCC, 0x71);
    private static final Map<Integer, Font> MONO_BOLD_FONTS = new ConcurrentHashMap<>();
    private static final Map<String, Deque<Double>> trendBuffers = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastTrendSampleMs = new ConcurrentHashMap<>();

    public static void clearTrendBuffers() {
        trendBuffers.clear();
        lastTrendSampleMs.clear();
    }

    @Override
    public boolean paint(Graphics2D g, ScadaSymbolInstance sym, int w, int h, int gPx,
                         ScadaProject project, RuntimeTagProvider tags, EquipmentState state, long timeMs) {
        return paint(g, sym, w, h, gPx, project, tags, state, timeMs, null);
    }

    @Override
    public boolean paint(Graphics2D g, ScadaSymbolInstance sym, int w, int h, int gPx,
                         ScadaProject project, RuntimeTagProvider tags, EquipmentState state,
                         long timeMs, SymbolLayerCache layerCache) {
        String type = sym.getSymbolType();
        double val = resolvePrimaryValue(sym, project, tags);
        double min = parseDoubleSafe(sym.getProperties().getOrDefault("Min", sym.getProperties().get("rangeMin")), 0.0);
        double max = parseDoubleSafe(sym.getProperties().getOrDefault("Max", sym.getProperties().get("rangeMax")), 100.0);
        if (max <= min) max = min + 100.0;
        double norm = Math.max(0.0, Math.min(1.0, (val - min) / (max - min)));
        String units = sym.getProperties().getOrDefault("EngineeringUnits",
                sym.getProperties().getOrDefault("unit", ""));

        switch (type) {
            case "PressureSensor" -> paintPressureSensor(g, w, h, gPx, val, norm, units);
            case "TempSensor" -> paintTempSensor(g, w, h, val, norm, units);
            case "FlowMeter" -> paintFlowMeter(g, w, h, gPx, val, units);
            case "LevelSwitch" -> paintCachedDiscrete(g, layerCache, type, w, h,
                    gPx + "|" + (val != 0.0), cg -> paintLevelSwitch(cg, w, h, gPx, val != 0.0));
            case "ProximitySensor" -> paintCachedDiscrete(g, layerCache, type, w, h,
                    gPx + "|" + (val != 0.0), cg -> paintProximitySensor(cg, w, h, gPx, val != 0.0));
            case "PushButton" -> paintCachedDiscrete(g, layerCache, type, w, h,
                    gPx + "|" + (val != 0.0), cg -> paintPushButton(cg, w, h, gPx, val != 0.0));
            case "SelectorSwitch" -> {
                int pos = (int) Math.round(val);
                paintCachedDiscrete(g, layerCache, type, w, h,
                        String.valueOf(pos), cg -> paintSelectorSwitch(cg, w, h, pos));
            }
            case "EStopMushroom" -> paintCachedDiscrete(g, layerCache, type, w, h,
                    String.valueOf(val != 0.0), cg -> paintEStopMushroom(cg, w, h, val != 0.0));
            case "PilotLamp" -> paintCachedDiscrete(g, layerCache, type, w, h,
                    (val != 0.0) + "|" + state.name(), cg -> paintPilotLamp(cg, w, h, val != 0.0, state));
            case "NumericReadout" -> paintNumericBox(g, w, h, gPx, val, units, false);
            case "NumericSetpoint" -> paintNumericBox(g, w, h, gPx, val, units, true);
            case "BarGraph" -> paintBarGraph(g, w, h, norm, sym);
            case "TrendChart" -> paintTrendChart(g, sym.getId(), w, h, val, min, max, timeMs);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void paintCachedDiscrete(Graphics2D g, SymbolLayerCache cache, String type,
                                            int w, int h, String variant,
                                            java.util.function.Consumer<Graphics2D> painter) {
        if (cache != null) {
            CacheKey key = new CacheKey("IC:" + type, w, h, 0, false, false, variant);
            BufferedImage img = cache.getOrCreateBackground(key, (cg, cw, ch) -> painter.accept(cg));
            g.drawImage(img, 0, 0, null);
        } else {
            painter.accept(g);
        }
    }

    private static double resolvePrimaryValue(ScadaSymbolInstance sym, ScadaProject project, RuntimeTagProvider tags) {
        for (String role : new String[]{"ValueTag", "SetpointTag", "RunTag", "LevelTag", "OpenTag"}) {
            String tagName = sym.getTagBindings().get(role);
            if (tagName != null && !tagName.isBlank() && project != null && project.getTag(tagName) != null && tags != null) {
                return tags.readTag(project, tagName);
            }
        }
        return parseDoubleSafe(sym.getProperties().get("DefaultValue"), 0.0);
    }

    private static void paintPressureSensor(Graphics2D g, int w, int h, int gPx, double val, double norm, String units) {
        double r = Math.min(w, h * 0.78) * 0.46;
        double cx = w / 2.0;
        double cy = r + 2.0;
        ScadaShader.paintVoluteCasing(g, cx, cy, r, r * 0.80, Material.STAINLESS_STEEL);
        // Dial face
        g.setColor(Color.WHITE);
        double dialR = r * 0.76;
        g.fill(new Ellipse2D.Double(cx - dialR, cy - dialR, dialR * 2, dialR * 2));
        // Rotating needle (-135 deg to +135 deg)
        double angle = Math.toRadians(-225.0 + norm * 270.0);
        g.setColor(ScadaPalette.STATE_FAULT);
        g.setStroke(new BasicStroke(1.6f));
        g.draw(new Line2D.Double(cx, cy, cx + Math.cos(angle) * dialR * 0.8, cy + Math.sin(angle) * dialR * 0.8));
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.fill(new Ellipse2D.Double(cx - 2, cy - 2, 4, 4));
        // Digital box below
        paintNumericBox(g, 2, (int) (h * 0.70), w - 4, (int) (h * 0.28), gPx, val, units, false);
    }

    private static void paintTempSensor(Graphics2D g, int w, int h, double val, double norm, String units) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 3.0, Material.CAST_IRON);
        double tubeX = w * 0.22;
        double tubeW = Math.max(6.0, w * 0.18);
        double tubeH = h * 0.72;
        g.setColor(ScadaPalette.CUTOUT_DARK);
        g.fill(new Rectangle2D.Double(tubeX, h * 0.14, tubeW, tubeH));
        double fillH = tubeH * norm;
        g.setColor(new Color(0xC0, 0x39, 0x2B));
        g.fill(new Rectangle2D.Double(tubeX + 1, h * 0.14 + tubeH - fillH, tubeW - 2, fillH));
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, Math.max(9, h / 5)));
        String txt = String.format(Locale.US, "%.1f%s", val, units.isEmpty() ? "" : " " + units);
        g.drawString(txt, (float) (w * 0.46), (float) (h * 0.56));
    }

    private static void paintFlowMeter(Graphics2D g, int w, int h, int gPx, double val, String units) {
        ScadaShader.paintCylinderH(g, 2, h * 0.35, w - 4, h * 0.30, Material.STAINLESS_STEEL);
        ScadaShader.paintBeveledHousing(g, w * 0.20, 2, w * 0.60, h * 0.50, 3.0, Material.CAST_IRON);
        paintNumericBox(g, (int) (w * 0.24), 5, (int) (w * 0.52), (int) (h * 0.40), gPx, val, units, false);
    }

    private static void paintLevelSwitch(Graphics2D g, int w, int h, int gPx, boolean active) {
        ScadaShader.paintBeveledHousing(g, 2, h * 0.22, w * 0.55, h * 0.56, 3.0, Material.CAST_IRON);
        ScadaShader.paintCylinderH(g, w * 0.57, h * 0.42, w * 0.38, h * 0.16, Material.STAINLESS_STEEL);
        ScadaShader.paintLedIndicator(g, w * 0.28, h * 0.50, Math.max(3.0, gPx * 0.35),
                active ? ScadaPalette.STATE_WARNING : ScadaPalette.STATE_STOPPED_LED, active);
    }

    private static void paintProximitySensor(Graphics2D g, int w, int h, int gPx, boolean active) {
        ScadaShader.paintCylinderH(g, 4, h * 0.30, w * 0.78, h * 0.40, Material.STAINLESS_STEEL);
        g.setColor(new Color(0x1F, 0x61, 0x8D));
        g.fill(new Rectangle2D.Double(w * 0.82, h * 0.32, w * 0.14, h * 0.36));
        ScadaShader.paintLedIndicator(g, w * 0.22, h * 0.50, Math.max(2.5, gPx * 0.30),
                active ? new Color(0xF1, 0xC4, 0x0F) : ScadaPalette.STATE_STOPPED_LED, active);
    }

    private static void paintPushButton(Graphics2D g, int w, int h, int gPx, boolean pressed) {
        double r = Math.min(w, h) * 0.44;
        double cx = w / 2.0;
        double cy = h / 2.0;
        ScadaShader.paintVoluteCasing(g, cx, cy, r, 0.0, Material.FLANGE_STEEL);
        Color btnCol = pressed ? new Color(0x2E, 0xCC, 0x71) : new Color(0x1E, 0x84, 0x49);
        ScadaShader.paintLedIndicator(g, cx, cy, r * 0.68, btnCol, pressed);
    }

    private static void paintSelectorSwitch(Graphics2D g, int w, int h, int pos) {
        double r = Math.min(w, h) * 0.44;
        double cx = w / 2.0;
        double cy = h / 2.0;
        ScadaShader.paintVoluteCasing(g, cx, cy, r, r * 0.72, Material.FLANGE_STEEL);
        // 0 -> -45 deg (Left/Hand), 1 -> 0 deg (Off/Center), 2 -> +45 deg (Right/Auto)
        int clamped = Math.max(0, Math.min(2, pos));
        double angle = Math.toRadians(-90.0 + (clamped - 1) * 45.0);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(Math.max(3.0f, (float) (r * 0.22)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(cx - Math.cos(angle) * r * 0.45, cy - Math.sin(angle) * r * 0.45,
                cx + Math.cos(angle) * r * 0.55, cy + Math.sin(angle) * r * 0.55));
    }

    private static void paintEStopMushroom(Graphics2D g, int w, int h, boolean engaged) {
        double r = Math.min(w, h) * 0.46;
        double cx = w / 2.0;
        double cy = h / 2.0;
        // Yellow safety collar
        g.setColor(new Color(0xF1, 0xC4, 0x0F));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        // Red mushroom cap
        ScadaShader.paintLedIndicator(g, cx, cy, r * (engaged ? 0.62 : 0.72), ScadaPalette.STATE_FAULT, engaged);
    }

    private static void paintPilotLamp(Graphics2D g, int w, int h, boolean lit, EquipmentState state) {
        double r = Math.min(w, h) * 0.42;
        double cx = w / 2.0;
        double cy = h / 2.0;
        ScadaShader.paintVoluteCasing(g, cx, cy, r, 0.0, Material.FLANGE_STEEL);
        Color col = (state == EquipmentState.FAULT) ? ScadaPalette.STATE_FAULT : ScadaPalette.STATE_RUNNING_LED;
        ScadaShader.paintLedIndicator(g, cx, cy, r * 0.70, col, lit);
    }

    private static void paintNumericBox(Graphics2D g, int w, int h, int gPx, double val, String units, boolean editable) {
        paintNumericBox(g, 2, 2, w - 4, h - 4, gPx, val, units, editable);
    }

    private static void paintNumericBox(Graphics2D g, int x, int y, int w, int h, int gPx,
                                        double val, String units, boolean editable) {
        g.setColor(editable ? NUM_EDIT_BG : ScadaPalette.CUTOUT_DARK);
        g.fillRect(x, y, w, h);
        g.setColor(editable ? NUM_EDIT_BORDER : ScadaPalette.OUTLINE_DARK);
        g.drawRect(x, y, w, h);

        String txt = (Math.abs(val - Math.rint(val)) < 1e-4)
                ? String.format(Locale.US, "%.0f%s", val, units.isEmpty() ? "" : " " + units)
                : String.format(Locale.US, "%.2f%s", val, units.isEmpty() ? "" : " " + units);
        g.setColor(editable ? NUM_EDIT_TEXT : NUM_RO_TEXT);
        int fontSize = Math.max(9, Math.min(h - 4, gPx + 2));
        Font font = MONO_BOLD_FONTS.computeIfAbsent(fontSize, s -> new Font(Font.MONOSPACED, Font.BOLD, s));
        g.setFont(font);
        g.drawString(txt, x + 4, y + h / 2 + fontSize / 3);
    }

    private static void paintBarGraph(Graphics2D g, int w, int h, double norm, ScadaSymbolInstance sym) {
        g.setColor(ScadaPalette.CUTOUT_DARK);
        g.fillRect(2, 2, w - 4, h - 4);
        int fillH = (int) Math.round((h - 8) * norm);
        g.setColor(new Color(0x1F, 0x61, 0x8D));
        g.fillRect(4, h - 4 - fillH, w - 8, fillH);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.drawRect(2, 2, w - 4, h - 4);
    }

    private void paintTrendChart(Graphics2D g, String symId, int w, int h, double val, double min, double max, long timeMs) {
        Deque<Double> buf = trendBuffers.computeIfAbsent(symId, k -> new ArrayDeque<>(MAX_TREND_POINTS));
        Long prevMs = lastTrendSampleMs.get(symId);
        if (prevMs == null || (timeMs - prevMs) >= TREND_SAMPLE_INTERVAL_MS || timeMs < prevMs) {
            lastTrendSampleMs.put(symId, timeMs);
            synchronized (buf) {
                if (buf.size() >= MAX_TREND_POINTS) buf.removeFirst();
                buf.addLast(val);
            }
        }

        g.setColor(ScadaPalette.CUTOUT_DARK);
        g.fillRect(2, 2, w - 4, h - 4);
        g.setColor(TREND_GRID_COLOR);
        g.drawLine(2, h / 2, w - 2, h / 2);

        Double[] pts;
        synchronized (buf) {
            pts = buf.toArray(Double[]::new);
        }
        if (pts.length >= 2) {
            Path2D.Double path = new Path2D.Double();
            double stepX = (w - 8.0) / Math.max(1, MAX_TREND_POINTS - 1);
            for (int i = 0; i < pts.length; i++) {
                double n = Math.max(0.0, Math.min(1.0, (pts[i] - min) / (max - min)));
                double px = 4.0 + i * stepX;
                double py = (h - 4.0) - n * (h - 8.0);
                if (i == 0) path.moveTo(px, py);
                else path.lineTo(px, py);
            }
            g.setColor(TREND_LINE_COLOR);
            g.setStroke(TREND_STROKE);
            g.draw(path);
        }
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.0f));
        g.drawRect(2, 2, w - 4, h - 4);
    }

    private static double parseDoubleSafe(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
