package cz.maramat.scada.render;

import cz.maramat.scada.model.ScadaGraphicElement;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders ScadaGraphicElement shapes, text labels, and embedded pictures on ScadaCanvas.
 */
public final class GraphicElementRenderer {

    private static final int MAX_CACHED_IMAGES = 64;
    private static final Map<String, BufferedImage> IMAGE_CACHE = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
                    if (size() > MAX_CACHED_IMAGES) {
                        if (eldest.getValue() != null) eldest.getValue().flush();
                        return true;
                    }
                    return false;
                }
            }
    );

    public static void clearImageCache() {
        synchronized (IMAGE_CACHE) {
            for (BufferedImage img : IMAGE_CACHE.values()) {
                if (img != null) img.flush();
            }
            IMAGE_CACHE.clear();
        }
    }

    private GraphicElementRenderer() {
    }

    public static void paint(Graphics2D g, ScadaGraphicElement elem, int gridSizePx, boolean selected) {
        paint(g, elem, gridSizePx, selected, false);
    }

    public static void paint(Graphics2D g, ScadaGraphicElement elem, int gridSizePx, boolean selected,
                             boolean indicatorActive) {
        if (elem == null) return;

        Composite oldComp = g.getComposite();
        Stroke oldStroke = g.getStroke();
        Color oldColor = g.getColor();
        Font oldFont = g.getFont();

        if (elem.getOpacity() < 0.99) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) elem.getOpacity()));
        }

        double x1 = elem.getGridX() * (double) gridSizePx;
        double y1 = elem.getGridY() * (double) gridSizePx;
        double x2 = (elem.getGridX() + elem.getGridW()) * (double) gridSizePx;
        double y2 = (elem.getGridY() + elem.getGridH()) * (double) gridSizePx;
        double x = Math.min(x1, x2);
        double y = Math.min(y1, y2);
        double w = Math.max(gridSizePx, Math.abs(x2 - x1));
        double h = Math.max(gridSizePx, Math.abs(y2 - y1));

        float sw = Math.max(1.0f, (float) elem.getStrokeWidth());
        Stroke stroke = elem.isDashed()
                ? new BasicStroke(sw, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f)
                : new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

        switch (elem.getKind()) {
            case LINE -> {
                g.setColor(elem.getStrokeColor());
                g.setStroke(stroke);
                g.draw(new Line2D.Double(x1, y1, x2, y2));
            }
            case TEXT -> {
                if (elem.isFilled()) {
                    g.setColor(elem.getFillColor());
                    g.fill(new Rectangle2D.Double(x, y, w, h));
                }
                g.setColor(elem.getStrokeColor());
                float scale = Math.max(0.6f, gridSizePx / 10.0f);
                g.setFont(new Font("Segoe UI", Font.PLAIN, Math.max(7, Math.round(elem.getFontSize() * scale))));
                FontMetrics fm = g.getFontMetrics();
                String txt = elem.getText();
                if (txt != null && !txt.isEmpty()) {
                    g.drawString(txt, (float) (x + 2), (float) (y + fm.getAscent() + 2));
                }
            }
            case IMAGE -> {
                BufferedImage img = decodeImage(elem.getImageBase64());
                if (img != null) {
                    if (elem.isPreserveAspectRatio()) {
                        double imgRatio = (double) img.getWidth() / Math.max(1, img.getHeight());
                        double boxRatio = w / h;
                        double dw = w;
                        double dh = h;
                        if (imgRatio > boxRatio) {
                            dh = w / imgRatio;
                        } else {
                            dw = h * imgRatio;
                        }
                        double dx = x + (w - dw) / 2.0;
                        double dy = y + (h - dh) / 2.0;
                        g.drawImage(img, (int) Math.round(dx), (int) Math.round(dy),
                                (int) Math.max(1, Math.round(dw)), (int) Math.max(1, Math.round(dh)), null);
                    } else {
                        g.drawImage(img, (int) Math.round(x), (int) Math.round(y),
                                (int) Math.max(1, Math.round(w)), (int) Math.max(1, Math.round(h)), null);
                    }
                } else {
                    g.setColor(new Color(0xE8, 0xEE, 0xF5));
                    g.fill(new Rectangle2D.Double(x, y, w, h));
                    g.setColor(new Color(0x90, 0xA4, 0xAE));
                    g.setStroke(stroke);
                    g.draw(new Rectangle2D.Double(x, y, w, h));
                    g.draw(new Line2D.Double(x, y, x + w, y + h));
                    g.draw(new Line2D.Double(x + w, y, x, y + h));
                }
            }
            default -> {
                Shape shape = buildShape(elem, x, y, w, h);
                if (shape != null) {
                    boolean isIndicator = elem.isIndicatorEnabled()
                            && elem.getIndicatorPin() != null
                            && !elem.getIndicatorPin().isBlank();
                    boolean shouldFill = elem.isFilled() || isIndicator;
                    Color effectiveFill = isIndicator
                            ? (indicatorActive ? elem.getIndicatorActiveColor() : elem.getIndicatorInactiveColor())
                            : elem.getFillColor();
                    if (shouldFill) {
                        g.setColor(effectiveFill);
                        g.fill(shape);
                    }
                    g.setColor(elem.getStrokeColor());
                    g.setStroke(stroke);
                    g.draw(shape);
                }
            }
        }

        if (selected) {
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(ScadaPalette.SELECTION_BLUE);
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[]{4f, 3f}, 0f));
            g.draw(new Rectangle2D.Double(x - 2, y - 2, w + 4, h + 4));
        }

        g.setComposite(oldComp);
        g.setStroke(oldStroke);
        g.setColor(oldColor);
        g.setFont(oldFont);
    }

    private static Shape buildShape(ScadaGraphicElement elem, double x, double y, double w, double h) {
        return switch (elem.getKind()) {
            case RECTANGLE -> new Rectangle2D.Double(x, y, w, h);
            case ROUNDED_RECTANGLE -> {
                double arc = Math.min(w, h) * 0.25;
                yield new RoundRectangle2D.Double(x, y, w, h, arc, arc);
            }
            case CIRCLE -> {
                double d = Math.min(w, h);
                yield new Ellipse2D.Double(x + (w - d) / 2.0, y + (h - d) / 2.0, d, d);
            }
            case ELLIPSE -> new Ellipse2D.Double(x, y, w, h);
            case ARC -> new Arc2D.Double(x, y, w, h, 30, 120, Arc2D.OPEN);
            case SECTOR -> new Arc2D.Double(x, y, w, h, 0, 270, Arc2D.PIE);
            case TRIANGLE -> buildRegularPolygon(x, y, w, h, 3);
            case PENTAGON -> buildRegularPolygon(x, y, w, h, 5);
            case HEXAGON -> buildRegularPolygon(x, y, w, h, 6);
            case OCTAGON -> buildRegularPolygon(x, y, w, h, 8);
            case CUSTOM_NGON -> buildRegularPolygon(x, y, w, h, elem.getNgonSides());
            default -> new Rectangle2D.Double(x, y, w, h);
        };
    }

    private static Polygon buildRegularPolygon(double x, double y, double w, double h, int sides) {
        int n = Math.max(3, sides);
        int[] xs = new int[n];
        int[] ys = new int[n];
        double cx = x + w / 2.0;
        double cy = y + h / 2.0;
        double rx = w / 2.0;
        double ry = h / 2.0;
        for (int i = 0; i < n; i++) {
            double ang = -Math.PI / 2.0 + 2.0 * Math.PI * i / n;
            xs[i] = (int) Math.round(cx + rx * Math.cos(ang));
            ys[i] = (int) Math.round(cy + ry * Math.sin(ang));
        }
        return new Polygon(xs, ys, n);
    }

    private static BufferedImage decodeImage(String base64) {
        if (base64 == null || base64.isBlank()) return null;
        String key = digestKey(base64);
        BufferedImage cached = IMAGE_CACHE.get(key);
        if (cached != null) return cached;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img != null) {
                IMAGE_CACHE.put(key, img);
            }
            return img;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String digestKey(String base64) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(base64.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash) + ":" + base64.length();
        } catch (Exception e) {
            return base64.length() + ":" + base64.hashCode();
        }
    }
}
