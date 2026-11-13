package cz.maramat.scada.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/**
 * Programmatic 16x16 Graphics2D vector icons (strict zero-emoji policy).
 */
public final class ScadaVectorIcons {

    private static final Color INK = new Color(0x2B, 0x30, 0x36);
    private static final Color ACCENT_BLUE = new Color(0x1F, 0x61, 0x8D);
    private static final Color ACCENT_GREEN = new Color(0x1E, 0x84, 0x49);

    private ScadaVectorIcons() {
    }

    @FunctionalInterface
    private interface Painter {
        void paint(Graphics2D g, int w, int h);
    }

    private static Icon icon(Painter painter) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics gRaw, int x, int y) {
                Graphics2D g = (Graphics2D) gRaw.create();
                try {
                    g.translate(x, y);
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                    painter.paint(g, 16, 16);
                } finally {
                    g.dispose();
                }
            }

            @Override
            public int getIconWidth() {
                return 16;
            }

            @Override
            public int getIconHeight() {
                return 16;
            }
        };
    }

    public static Icon cursor() {
        return icon((g, w, h) -> {
            Path2D.Double p = new Path2D.Double();
            p.moveTo(3, 2);
            p.lineTo(3, 13);
            p.lineTo(6.5, 10);
            p.lineTo(9, 14);
            p.lineTo(10.8, 13.2);
            p.lineTo(8.3, 9.2);
            p.lineTo(12.5, 9.2);
            p.closePath();
            g.setColor(Color.WHITE);
            g.fill(p);
            g.setColor(INK);
            g.setStroke(new BasicStroke(1.2f));
            g.draw(p);
        });
    }

    public static Icon pipe() {
        return icon((g, w, h) -> {
            g.setColor(ACCENT_BLUE);
            g.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            g.drawLine(3, 12, 3, 5);
            g.drawLine(3, 5, 12, 5);
            g.setColor(INK);
            g.fillRect(1, 11, 4, 3);
            g.fillRect(11, 3, 3, 4);
        });
    }

    public static Icon rotate90() {
        return icon((g, w, h) -> {
            g.setColor(INK);
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
            g.setColor(INK);
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine(8, 2, 8, 14);
            Path2D.Double left = new Path2D.Double();
            left.moveTo(2, 8);
            left.lineTo(6, 4);
            left.lineTo(6, 12);
            left.closePath();
            g.fill(left);
            Path2D.Double right = new Path2D.Double();
            right.moveTo(14, 8);
            right.lineTo(10, 4);
            right.lineTo(10, 12);
            right.closePath();
            g.draw(right);
        });
    }

    public static Icon flipV() {
        return icon((g, w, h) -> {
            g.setColor(INK);
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine(2, 8, 14, 8);
            Path2D.Double top = new Path2D.Double();
            top.moveTo(8, 2);
            top.lineTo(4, 6);
            top.lineTo(12, 6);
            top.closePath();
            g.fill(top);
            Path2D.Double bot = new Path2D.Double();
            bot.moveTo(8, 14);
            bot.lineTo(4, 10);
            bot.lineTo(12, 10);
            bot.closePath();
            g.draw(bot);
        });
    }

    public static Icon undo() {
        return icon((g, w, h) -> {
            g.setColor(INK);
            g.setStroke(new BasicStroke(1.6f));
            g.drawArc(4, 5, 9, 8, 0, 180);
            Path2D.Double arrow = new Path2D.Double();
            arrow.moveTo(2, 8);
            arrow.lineTo(6, 5);
            arrow.lineTo(6, 11);
            arrow.closePath();
            g.fill(arrow);
        });
    }

    public static Icon redo() {
        return icon((g, w, h) -> {
            g.setColor(INK);
            g.setStroke(new BasicStroke(1.6f));
            g.drawArc(3, 5, 9, 8, 0, 180);
            Path2D.Double arrow = new Path2D.Double();
            arrow.moveTo(14, 8);
            arrow.lineTo(10, 5);
            arrow.lineTo(10, 11);
            arrow.closePath();
            g.fill(arrow);
        });
    }

    public static Icon zoomIn() {
        return icon((g, w, h) -> {
            g.setColor(INK);
            g.setStroke(new BasicStroke(1.4f));
            g.drawOval(2, 2, 9, 9);
            g.drawLine(10, 10, 14, 14);
            g.drawLine(4, 6, 9, 6);
            g.drawLine(6, 4, 6, 9);
        });
    }

    public static Icon zoomOut() {
        return icon((g, w, h) -> {
            g.setColor(INK);
            g.setStroke(new BasicStroke(1.4f));
            g.drawOval(2, 2, 9, 9);
            g.drawLine(10, 10, 14, 14);
            g.drawLine(4, 6, 9, 6);
        });
    }

    public static Icon playSim() {
        return icon((g, w, h) -> {
            Path2D.Double p = new Path2D.Double();
            p.moveTo(4, 2);
            p.lineTo(13, 8);
            p.lineTo(4, 14);
            p.closePath();
            g.setColor(ACCENT_GREEN);
            g.fill(p);
            g.setColor(INK);
            g.setStroke(new BasicStroke(1.0f));
            g.draw(p);
        });
    }

    public static Icon operatorMode() {
        return icon((g, w, h) -> {
            g.setColor(ACCENT_BLUE);
            g.fillRect(2, 3, 12, 8);
            g.setColor(INK);
            g.setStroke(new BasicStroke(1.2f));
            g.drawRect(2, 3, 12, 8);
            g.drawLine(6, 11, 6, 13);
            g.drawLine(10, 11, 10, 13);
            g.drawLine(4, 13, 12, 13);
        });
    }

    public static Icon linkMfl() {
        return icon((g, w, h) -> {
            g.setColor(INK);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(2, 5, 6, 6, 3, 3);
            g.drawRoundRect(8, 5, 6, 6, 3, 3);
            g.setColor(ACCENT_BLUE);
            g.drawLine(6, 8, 10, 8);
        });
    }
}
