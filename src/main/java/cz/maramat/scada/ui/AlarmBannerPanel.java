package cz.maramat.scada.ui;

import com.formdev.flatlaf.FlatLaf;

import cz.maramat.scada.alarms.AlarmEngine;
import cz.maramat.scada.alarms.AlarmEntry;
import cz.maramat.scada.render.EquipmentState;
import cz.maramat.scada.render.ScadaPalette;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;

/**
 * Live bottom industrial Alarm Banner displaying active/unacknowledged alarms with 2 Hz UNACK blinking.
 */
public class AlarmBannerPanel extends JPanel {

    private final AlarmEngine alarmEngine;
    private final JLabel stateBadge = new JLabel(" OK ");
    private final JLabel messageLabel = new JLabel(" No active alarms");
    private final JButton ackBtn = new JButton("Acknowledge");
    private final JButton ackAllBtn = new JButton("Ack All");
    private String topTagName = null;
    private boolean flashEnabled = true;

    public AlarmBannerPanel(AlarmEngine alarmEngine) {
        this.alarmEngine = alarmEngine;
        setLayout(new BorderLayout(6, 0));

        stateBadge.setOpaque(true);
        stateBadge.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        stateBadge.setBackground(new Color(0x1E, 0x84, 0x49));
        stateBadge.setForeground(Color.WHITE);

        messageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        right.setOpaque(false);
        ackBtn.addActionListener(e -> {
            if (topTagName != null) {
                alarmEngine.acknowledge(topTagName);
                refresh(System.currentTimeMillis());
            }
        });
        ackAllBtn.addActionListener(e -> {
            alarmEngine.acknowledgeAll();
            refresh(System.currentTimeMillis());
        });
        right.add(ackBtn);
        right.add(ackAllBtn);

        add(stateBadge, BorderLayout.WEST);
        add(messageLabel, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
        applyThemeColors();
        refresh(System.currentTimeMillis());
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyThemeColors();
    }

    private void applyThemeColors() {
        boolean dark = FlatLaf.isLafDark();
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                dark ? new Color(0x45, 0x4B, 0x54) : new Color(0x9A, 0xA0, 0xA6)));
        setBackground(dark ? new Color(0x26, 0x29, 0x30) : new Color(0xE8, 0xEC, 0xF0));
        if (messageLabel != null) {
            messageLabel.setForeground(dark ? new Color(0xF5, 0xF7, 0xFA) : new Color(0x1F, 0x23, 0x28));
        }
    }

    public void refresh(long nowMs) {
        List<AlarmEntry> list = alarmEngine.getAlarms();
        if (list.isEmpty()) {
            topTagName = null;
            stateBadge.setText(" NORMAL ");
            stateBadge.setBackground(new Color(0x1E, 0x84, 0x49));
            stateBadge.setForeground(Color.WHITE);
            messageLabel.setText(" No active alarms");
            ackBtn.setEnabled(false);
            ackAllBtn.setEnabled(false);
            return;
        }

        AlarmEntry top = list.getFirst();
        topTagName = top.getTagName();
        boolean unack = !top.isAcknowledged();
        boolean flashOn = !flashEnabled || EquipmentState.isFaultFlashOn(nowMs);

        if (unack) {
            stateBadge.setText(" UNACK ");
            stateBadge.setBackground(flashOn ? ScadaPalette.STATE_FAULT : new Color(0x6E, 0x16, 0x16));
        } else {
            stateBadge.setText(" ACK ");
            stateBadge.setBackground(ScadaPalette.STATE_WARNING);
        }
        stateBadge.setForeground(Color.WHITE);
        messageLabel.setText(" [" + top.getFormattedTime() + "] " + top.getSeverity() + ": " + top.getMessage()
                + (list.size() > 1 ? " (+" + (list.size() - 1) + " more)" : ""));
        ackBtn.setEnabled(unack);
        ackAllBtn.setEnabled(alarmEngine.hasUnacknowledged());
    }

    public boolean isFlashEnabled() {
        return flashEnabled;
    }

    public void setFlashEnabled(boolean flashEnabled) {
        this.flashEnabled = flashEnabled;
    }
}
