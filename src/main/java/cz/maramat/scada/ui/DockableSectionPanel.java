package cz.maramat.scada.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;

/**
 * Utilitarian Win95/Win7 dockable sub-window panel matching MaraFlow's DockableSectionPanel.
 */
public class DockableSectionPanel extends JPanel {
    private static final Color HEADER_TOP = new Color(0xE8, 0xEE, 0xF5);
    private static final Color HEADER_BOTTOM = new Color(0xCD, 0xD7, 0xE4);
    private static final Color HEADER_BORDER = new Color(0x9A, 0xA6, 0xB4);
    private static final Color TITLE_FG = new Color(0x22, 0x2A, 0x35);
    private static final Font HEADER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 11);

    private final JLabel titleLabel;

    public DockableSectionPanel(String title, JComponent content, Runnable onCloseClicked) {
        super(new BorderLayout());
        setBorder(BorderFactory.createLineBorder(HEADER_BORDER, 1));

        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                GradientPaint gp = new GradientPaint(0, 0, HEADER_TOP, 0, getHeight(), HEADER_BOTTOM);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        header.setPreferredSize(new Dimension(0, 22));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, HEADER_BORDER),
                BorderFactory.createEmptyBorder(1, 6, 1, 3)
        ));

        titleLabel = new JLabel(title);
        titleLabel.setFont(HEADER_FONT);
        titleLabel.setForeground(TITLE_FG);
        header.add(titleLabel, BorderLayout.WEST);

        if (onCloseClicked != null) {
            JButton closeBtn = new JButton(ScadaVectorIcons.closePanelCrossIcon());
            closeBtn.setFocusable(false);
            closeBtn.setMargin(new Insets(1, 3, 1, 3));
            closeBtn.setPreferredSize(new Dimension(18, 16));
            closeBtn.setToolTipText("Close Panel");
            closeBtn.addActionListener(e -> onCloseClicked.run());
            header.add(closeBtn, BorderLayout.EAST);
        }

        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    public void setTitle(String title) {
        titleLabel.setText(title);
    }
}
