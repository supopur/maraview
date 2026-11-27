package cz.maramat.scada.ui;

import com.formdev.flatlaf.FlatLaf;

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
    private static final Color LIGHT_HEADER_TOP = new Color(0xE8, 0xEE, 0xF5);
    private static final Color LIGHT_HEADER_BOTTOM = new Color(0xCD, 0xD7, 0xE4);
    private static final Color LIGHT_HEADER_BORDER = new Color(0x9A, 0xA6, 0xB4);
    private static final Color LIGHT_TITLE_FG = new Color(0x22, 0x2A, 0x35);

    private static final Color DARK_HEADER_TOP = new Color(0x32, 0x36, 0x3F);
    private static final Color DARK_HEADER_BOTTOM = new Color(0x27, 0x2A, 0x31);
    private static final Color DARK_HEADER_BORDER = new Color(0x45, 0x4B, 0x54);
    private static final Color DARK_TITLE_FG = new Color(0xF5, 0xF7, 0xFA);

    private static final Font HEADER_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 11);

    private final JLabel titleLabel;
    private final JPanel header;

    public DockableSectionPanel(String title, JComponent content, Runnable onCloseClicked) {
        super(new BorderLayout());

        header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                boolean dark = FlatLaf.isLafDark();
                GradientPaint gp = new GradientPaint(
                        0, 0, dark ? DARK_HEADER_TOP : LIGHT_HEADER_TOP,
                        0, getHeight(), dark ? DARK_HEADER_BOTTOM : LIGHT_HEADER_BOTTOM
                );
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        header.setPreferredSize(new Dimension(0, 22));

        titleLabel = new JLabel(title);
        titleLabel.setFont(HEADER_FONT);
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
        applyThemeColors();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyThemeColors();
    }

    private void applyThemeColors() {
        if (header == null || titleLabel == null) return;
        boolean dark = FlatLaf.isLafDark();
        Color borderCol = dark ? DARK_HEADER_BORDER : LIGHT_HEADER_BORDER;
        setBorder(BorderFactory.createLineBorder(borderCol, 1));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderCol),
                BorderFactory.createEmptyBorder(1, 6, 1, 3)
        ));
        titleLabel.setForeground(dark ? DARK_TITLE_FG : LIGHT_TITLE_FG);
    }

    public void setTitle(String title) {
        titleLabel.setText(title);
    }
}
