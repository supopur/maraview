package cz.maramat.scada.ui;

import cz.maramat.scada.config.SecretsStore;
import cz.maramat.scada.config.UserConfig;
import cz.maramat.scada.config.UserConfigStore;
import cz.maramat.scada.model.CommSettings;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.tags.MaraflowSimClient;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;

/**
 * TCP MaraProt v1 connection dialog ("Communication -> Connect to server").
 * Remembers IP, Port, Station ID, and Password (via OS secrets store with fallback).
 */
public final class ConnectServerDialog {
    private static final String SECRET_KEY = "maraprot.server.password";

    private ConnectServerDialog() {
    }

    public static String loadSavedPassword() {
        return new SecretsStore("MaraView").loadSecret(SECRET_KEY, "admin");
    }

    public static void savePassword(String password) {
        new SecretsStore("MaraView").saveSecret(SECRET_KEY, password);
    }

    public static boolean showDialog(Window owner, UserConfig userConfig, ScadaProject project) {
        JDialog dlg = new JDialog(owner, "Connect to Server (MaraProt TCP)", JDialog.ModalityType.APPLICATION_MODAL);
        final boolean[] confirmed = {false};

        CommSettings def = (userConfig != null && userConfig.getDefaultCommSettings() != null)
                ? userConfig.getDefaultCommSettings()
                : new CommSettings();
        CommSettings projComm = (project != null && project.getCommSettings() != null)
                ? project.getCommSettings()
                : null;

        String initIp = (projComm != null && projComm.isRemoteMaraProt()) ? projComm.getHost() : def.getHost();
        int initPort = (projComm != null && projComm.isRemoteMaraProt()) ? projComm.getTcpPort() : def.getTcpPort();
        int initStation = (projComm != null && projComm.isRemoteMaraProt()) ? projComm.getStationId() : def.getStationId();
        String initPass = loadSavedPassword();

        JTextField ipField = new JTextField(initIp, 18);
        JTextField portField = createDigitsOnlyField(String.valueOf(initPort), 8);
        JTextField stationField = createDigitsOnlyField(String.valueOf(initStation), 6);
        JPasswordField passField = new JPasswordField(initPass, 18);

        JButton testBtn = new JButton("Test Connection");
        JLabel statusLabel = new JLabel(" ");
        testBtn.addActionListener(e -> {
            String host = ipField.getText().trim();
            int port = parseIntOrDefault(portField.getText(), CommSettings.DEFAULT_MARAPROT_TCP_PORT, 1, 65535);
            int station = parseIntOrDefault(stationField.getText(), 1, 0, 254);
            String pw = new String(passField.getPassword());
            testBtn.setEnabled(false);
            statusLabel.setForeground(com.formdev.flatlaf.FlatLaf.isLafDark()
                    ? new Color(0xF5, 0xF7, 0xFA) : Color.DARK_GRAY);
            statusLabel.setText("Connecting...");
            Thread.ofVirtual().start(() -> {
                MaraflowSimClient.SimStatus st = MaraflowSimClient.testConnection(host, port, station, pw);
                SwingUtilities.invokeLater(() -> {
                    testBtn.setEnabled(true);
                    if (st.online()) {
                        statusLabel.setForeground(new Color(0x1E, 0x84, 0x49));
                        String info = st.projectName().isBlank() ? "" : (" [" + st.projectName() + "]");
                        statusLabel.setText("Connected: " + st.simState() + info);
                    } else {
                        statusLabel.setForeground(new Color(0xD9, 0x26, 0x26));
                        statusLabel.setText("Offline: " + (st.errorMessage().isBlank() ? "Connection refused" : st.errorMessage()));
                    }
                });
            });
        });

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        int r = 0;
        addRow(form, gbc, r++, "IP Address:", ipField);
        addRow(form, gbc, r++, "Port:", portField);
        addRow(form, gbc, r++, "Station ID:", stationField);
        addRow(form, gbc, r++, "Password:", passField);
        JPanel testRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        testRow.add(testBtn);
        testRow.add(statusLabel);
        addRow(form, gbc, r, "Diagnostics:", testRow);

        JButton connectBtn = new JButton("Connect");
        connectBtn.addActionListener(e -> {
            String host = ipField.getText().trim();
            int port = parseIntOrDefault(portField.getText(), CommSettings.DEFAULT_MARAPROT_TCP_PORT, 1, 65535);
            int station = parseIntOrDefault(stationField.getText(), 1, 0, 254);
            String pw = new String(passField.getPassword());

            def.setMode(port == CommSettings.DEFAULT_MARAFLOW_SIM_PORT
                    ? CommSettings.Mode.MARAFLOW_SIM
                    : CommSettings.Mode.MARAPROT_TCP);
            def.setHost(host);
            def.setTcpPort(port);
            def.setStationId(station);
            savePassword(pw);

            if (userConfig != null) {
                try {
                    UserConfigStore.save(userConfig);
                } catch (IOException ignored) {
                }
            }

            if (projComm != null) {
                projComm.setMode(def.getMode());
                projComm.setHost(host);
                projComm.setTcpPort(port);
                projComm.setStationId(station);
                project.setDirty(true);
            }

            confirmed[0] = true;
            dlg.dispose();
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dlg.dispose());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btns.add(connectBtn);
        btns.add(cancelBtn);

        dlg.setLayout(new BorderLayout());
        dlg.add(form, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.getRootPane().setDefaultButton(connectBtn);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
        return confirmed[0];
    }

    public static JTextField createDigitsOnlyField(String initial, int cols) {
        JTextField tf = new JTextField(initial, cols);
        ((AbstractDocument) tf.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                if (string != null && string.chars().allMatch(Character::isDigit)) {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                if (text != null && text.chars().allMatch(Character::isDigit)) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }
        });
        return tf;
    }

    private static int parseIntOrDefault(String s, int def, int min, int max) {
        if (s == null || s.isBlank()) return def;
        try {
            int v = Integer.parseInt(s.trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, java.awt.Component comp) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(comp, gbc);
    }
}
