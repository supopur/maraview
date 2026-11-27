package cz.maramat.scada.ui;

import com.formdev.flatlaf.FlatLaf;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/**
 * Programmatic 16x16 Graphics2D vector icons matching MaraFlow's VectorIcons style (strict zero-emoji policy).
 */
public final class ScadaVectorIcons {

    private static final Color INK_LIGHT = new Color(0x22, 0x2A, 0x35);
    private static final Color INK_DARK = new Color(0xF5, 0xF7, 0xFA);
    private static final Color ACCENT_BLUE = new Color(0x1F, 0x61, 0x8D);
    private static final Color ACCENT_GREEN = new Color(0x14, 0x94, 0x14);
    private static final Color ACCENT_RED = new Color(0xB8, 0x14, 0x14);
    private static final Color ACCENT_AMBER = new Color(0xC8, 0x7A, 0x10);
    private static final Color DISABLED_LIGHT = new Color(0x99, 0x99, 0x99);
    private static final Color DISABLED_DARK = new Color(0x7E, 0x86, 0x94);
    private static final Color CROSS_LIGHT = new Color(0x33, 0x3C, 0x48);
    private static final Color CROSS_DARK = new Color(0xF5, 0xF7, 0xFA);

    private static Color ink() {
        return FlatLaf.isLafDark() ? INK_DARK : INK_LIGHT;
    }

    private static Color disabledColor() {
        return FlatLaf.isLafDark() ? DISABLED_DARK : DISABLED_LIGHT;
    }

    private static Color crossColor() {
        return FlatLaf.isLafDark() ? CROSS_DARK : CROSS_LIGHT;
    }
    private static final BasicStroke STROKE_1_2 = new BasicStroke(1.2f);
    private static final BasicStroke STROKE_1_5 = new BasicStroke(1.5f);
    private static final BasicStroke STROKE_PIPE = new BasicStroke(3.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER);

    private ScadaVectorIcons() {
    }

    @FunctionalInterface
    private interface Painter {
        void paint(Graphics2D g, int w, int h);
    }

    private static Icon icon(int w, int h, Painter painter) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics gRaw, int x, int y) {
                Graphics2D g = (Graphics2D) gRaw.create();
                try {
                    g.translate(x, y);
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                    if (c != null && !c.isEnabled()) {
                        g.setColor(disabledColor());
                    }
                    painter.paint(g, w, h);
                } finally {
                    g.dispose();
                }
            }

            @Override
            public int getIconWidth() {
                return w;
            }

            @Override
            public int getIconHeight() {
                return h;
            }
        };
    }

    private static Icon icon(Painter painter) {
        return icon(16, 16, painter);
    }

    public static Icon closePanelCrossIcon() {
        return icon(10, 10, (g, w, h) -> {
            g.setColor(crossColor());
            g.setStroke(STROKE_1_5);
            g.drawLine(2, 2, 8, 8);
            g.drawLine(8, 2, 2, 8);
        });
    }

    public static Icon cursor() {
        return icon((g, w, h) -> {
            Polygon arrow = new Polygon(
                    new int[]{3, 3, 6, 8, 10, 8, 12},
                    new int[]{2, 13, 10, 14, 13, 9, 9},
                    7
            );
            g.setColor(Color.WHITE);
            g.fillPolygon(arrow);
            g.setColor(ink());
            g.setStroke(STROKE_1_2);
            g.drawPolygon(arrow);
        });
    }

    public static Icon pipe() {
        return icon((g, w, h) -> {
            g.setColor(ACCENT_BLUE);
            g.setStroke(STROKE_PIPE);
            g.drawLine(3, 12, 3, 5);
            g.drawLine(3, 5, 12, 5);
            g.setColor(ink());
            g.fillRect(1, 11, 4, 3);
            g.fillRect(11, 3, 3, 4);
        });
    }

    public static Icon rotate90() {
        return icon((g, w, h) -> {
            g.setColor(ink());
            g.setStroke(new BasicStroke(1.5f));
            g.drawArc(3, 3, 10, 10, 45, 250);
            Path2D.Double arrow = new Path2D.Double();
            arrow.moveTo(11, 2);
            arrow.lineTo(14, 5);
            arrow.lineTo(10, 6);
            arrow.closePath();
            g.fill(arrow);
        });
    }

    public static Icon flipH() {
        return icon((g, w, h) -> {
            g.setColor(ink());
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine(8, 2, 8, 14);
            Polygon left = new Polygon(new int[]{2, 6, 6}, new int[]{8, 4, 12}, 3);
            g.fillPolygon(left);
            Polygon right = new Polygon(new int[]{14, 10, 10}, new int[]{8, 4, 12}, 3);
            g.drawPolygon(right);
        });
    }

    public static Icon flipV() {
        return icon((g, w, h) -> {
            g.setColor(ink());
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine(2, 8, 14, 8);
            Polygon top = new Polygon(new int[]{8, 4, 12}, new int[]{2, 6, 6}, 3);
            g.fillPolygon(top);
            Polygon bot = new Polygon(new int[]{8, 4, 12}, new int[]{14, 10, 10}, 3);
            g.drawPolygon(bot);
        });
    }

    public static Icon undo() {
        return icon((g, w, h) -> {
            g.setColor(ink());
            g.setStroke(new BasicStroke(1.6f));
            g.drawArc(4, 5, 9, 8, 0, 180);
            Polygon arrow = new Polygon(new int[]{2, 6, 6}, new int[]{8, 5, 11}, 3);
            g.fillPolygon(arrow);
        });
    }

    public static Icon redo() {
        return icon((g, w, h) -> {
            g.setColor(ink());
            g.setStroke(new BasicStroke(1.6f));
            g.drawArc(3, 5, 9, 8, 0, 180);
            Polygon arrow = new Polygon(new int[]{14, 10, 10}, new int[]{8, 5, 11}, 3);
            g.fillPolygon(arrow);
        });
    }

    public static Icon zoomIn() {
        return icon((g, w, h) -> {
            g.setColor(ink());
            g.setStroke(new BasicStroke(1.4f));
            g.drawOval(2, 2, 9, 9);
            g.drawLine(10, 10, 14, 14);
            g.drawLine(4, 6, 9, 6);
            g.drawLine(6, 4, 6, 9);
        });
    }

    public static Icon zoomOut() {
        return icon((g, w, h) -> {
            g.setColor(ink());
            g.setStroke(new BasicStroke(1.4f));
            g.drawOval(2, 2, 9, 9);
            g.drawLine(10, 10, 14, 14);
            g.drawLine(4, 6, 9, 6);
        });
    }

    public static Icon playIcon() {
        return icon((g, w, h) -> {
            Polygon p = new Polygon(new int[]{4, 13, 4}, new int[]{2, 8, 14}, 3);
            g.setColor(ACCENT_GREEN);
            g.fillPolygon(p);
            g.setColor(ink());
            g.setStroke(new BasicStroke(1.0f));
            g.drawPolygon(p);
        });
    }

    public static Icon pauseIcon() {
        return icon((g, w, h) -> {
            g.setColor(ACCENT_AMBER);
            g.fillRect(3, 3, 4, 10);
            g.fillRect(9, 3, 4, 10);
            g.setColor(ink());
            g.setStroke(new BasicStroke(1.0f));
            g.drawRect(3, 3, 4, 10);
            g.drawRect(9, 3, 4, 10);
        });
    }

    public static Icon stopIcon() {
        return icon((g, w, h) -> {
            g.setColor(ACCENT_RED);
            g.fillRect(3, 3, 10, 10);
            g.setColor(ink());
            g.setStroke(new BasicStroke(1.0f));
            g.drawRect(3, 3, 10, 10);
        });
    }

    public static Icon stepIcon() {
        return icon((g, w, h) -> {
            Polygon p = new Polygon(new int[]{3, 10, 3}, new int[]{3, 8, 13}, 3);
            g.setColor(ACCENT_BLUE);
            g.fillPolygon(p);
            g.setColor(ink());
            g.drawPolygon(p);
            g.fillRect(11, 3, 2, 10);
        });
    }

    public static Icon operatorMode() {
        return icon((g, w, h) -> {
            g.setColor(ACCENT_BLUE);
            g.fillRect(2, 3, 12, 8);
            g.setColor(ink());
            g.setStroke(new BasicStroke(1.2f));
            g.drawRect(2, 3, 12, 8);
            g.drawLine(6, 11, 6, 13);
            g.drawLine(10, 11, 10, 13);
            g.drawLine(4, 13, 12, 13);
        });
    }

    public static Icon linkMfl() {
        return icon((g, w, h) -> {
            g.setColor(ink());
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(2, 5, 6, 6, 3, 3);
            g.drawRoundRect(8, 5, 6, 6, 3, 3);
            g.setColor(ACCENT_BLUE);
            g.drawLine(6, 8, 10, 8);
        });
    }

    public static Icon lineIcon() {
        return icon((g, w, h) -> {
            g.setColor(ink());
            g.setStroke(new BasicStroke(1.6f));
            g.drawLine(2, 13, 13, 2);
            g.setColor(ACCENT_BLUE);
            g.fillRect(1, 12, 3, 3);
            g.fillRect(12, 1, 3, 3);
        });
    }

    public static Icon rectIcon() {
        return icon((g, w, h) -> {
            g.setColor(new Color(0xE8, 0xEE, 0xF5));
            g.fillRect(2, 3, 12, 10);
            g.setColor(ink());
            g.setStroke(new BasicStroke(1.3f));
            g.drawRect(2, 3, 12, 10);
        });
    }

    public static Icon circleIcon() {
        return icon((g, w, h) -> {
            g.setColor(new Color(0xE8, 0xEE, 0xF5));
            g.fillOval(2, 2, 12, 12);
            g.setColor(ink());
            g.setStroke(new BasicStroke(1.3f));
            g.drawOval(2, 2, 12, 12);
        });
    }

    public static Icon textIcon() {
        return icon((g, w, h) -> {
            g.setColor(ink());
            g.setFont(new java.awt.Font("Serif", java.awt.Font.BOLD, 13));
            g.drawString("T", 4, 13);
        });
    }

    public static Icon imageIcon() {
        return icon((g, w, h) -> {
            g.setColor(new Color(0xE8, 0xEE, 0xF5));
            g.fillRect(2, 2, 12, 12);
            g.setColor(ACCENT_GREEN);
            g.fillPolygon(new int[]{3, 7, 10, 13, 3}, new int[]{13, 7, 10, 6, 13}, 5);
            g.setColor(new Color(0xFF, 0xB3, 0x00));
            g.fillOval(10, 4, 3, 3);
            g.setColor(ink());
            g.setStroke(new BasicStroke(1.2f));
            g.drawRect(2, 2, 12, 12);
        });
    }

    public static Icon settingsIcon() {
        return icon((g, w, h) -> {
            g.setColor(ink());
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double cx = 8.0, cy = 8.0;
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI / 4.0;
                int x1 = (int) Math.round(cx + Math.cos(ang) * 4.2);
                int y1 = (int) Math.round(cy + Math.sin(ang) * 4.2);
                int x2 = (int) Math.round(cx + Math.cos(ang) * 6.3);
                int y2 = (int) Math.round(cy + Math.sin(ang) * 6.3);
                g.drawLine(x1, y1, x2, y2);
            }
            g.setColor(new Color(0xE8, 0xEE, 0xF5));
            g.fillOval(4, 4, 8, 8);
            g.setColor(ink());
            g.setStroke(new BasicStroke(1.3f));
            g.drawOval(4, 4, 8, 8);
            g.fillOval(6, 6, 4, 4);
        });
    }
}
