package cz.maramat.scada.ui;

import cz.maramat.scada.model.CommSettings;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.tags.MaraflowSimClient;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

/**
 * Dialog for editing per-project metadata and PLC/simulation communication settings.
 */
public final class ProjectSettingsDialog {

    private ProjectSettingsDialog() {
    }

    public static boolean showDialog(Window owner, ScadaProject project) {
        return showDialog(owner, project, 0);
    }

    public static boolean showDialog(Window owner, ScadaProject project, int initialTab) {
        if (project == null) return false;
        JDialog dlg = new JDialog(owner, "Project & Communication Settings", JDialog.ModalityType.APPLICATION_MODAL);
        final boolean[] confirmed = {false};

        // --- Tab 1: Project Info ---
        JTextField nameField = new JTextField(project.getProjectName(), 22);
        JTextField authorField = new JTextField(project.getAuthor(), 22);
        JTextArea descArea = new JTextArea(project.getDescription(), 4, 22);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        JTextField mflField = new JTextField(project.getLinkedMflPath(), 18);
        JButton browseMflBtn = new JButton("Browse...");
        browseMflBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("Maraflow PLC Project (*.mfl)", "mfl"));
            if (fc.showOpenDialog(dlg) == JFileChooser.APPROVE_OPTION && fc.getSelectedFile() != null) {
                mflField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });

        JPanel infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        GridBagConstraints g1 = createGbc();
        int r1 = 0;
        addRow(infoPanel, g1, r1++, "Project Name:", nameField);
        addRow(infoPanel, g1, r1++, "Author:", authorField);
        addRow(infoPanel, g1, r1++, "Description:", new JScrollPane(descArea));
        JPanel mflRow = new JPanel(new BorderLayout(4, 0));
        mflRow.add(mflField, BorderLayout.CENTER);
        mflRow.add(browseMflBtn, BorderLayout.EAST);
        addRow(infoPanel, g1, r1++, "Linked .mfl Path:", mflRow);

        // --- Tab 2: Communication (TCP MaraProt) ---
        CommSettings comm = project.getCommSettings();
        JComboBox<CommSettings.Mode> modeCombo = new JComboBox<>(CommSettings.Mode.values());
        modeCombo.setSelectedItem(comm.getMode());
        JTextField hostField = new JTextField(comm.getHost(), 18);
        JTextField portField = ConnectServerDialog.createDigitsOnlyField(String.valueOf(comm.getTcpPort()), 8);
        JTextField stationField = ConnectServerDialog.createDigitsOnlyField(String.valueOf(comm.getStationId()), 6);
        JSpinner pollSpin = new JSpinner(new SpinnerNumberModel(comm.getPollIntervalMs(), 10, 10000, 10));

        JPanel commPanel = new JPanel(new GridBagLayout());
        commPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        GridBagConstraints g2 = createGbc();
        int r2 = 0;
        addRow(commPanel, g2, r2++, "Mode:", modeCombo);
        addRow(commPanel, g2, r2++, "IP Address:", hostField);
        addRow(commPanel, g2, r2++, "TCP Port:", portField);
        addRow(commPanel, g2, r2++, "Station ID:", stationField);
        addRow(commPanel, g2, r2++, "Poll Interval (ms):", pollSpin);

        JButton testConnBtn = new JButton("Test MaraProt Connection");
        JLabel testStatusLabel = new JLabel(" ");
        testConnBtn.setEnabled(comm.isRemoteMaraProt());
        modeCombo.addActionListener(e -> {
            CommSettings.Mode sel = (CommSettings.Mode) modeCombo.getSelectedItem();
            if (sel == CommSettings.Mode.MARAFLOW_SIM) {
                portField.setText(String.valueOf(CommSettings.DEFAULT_MARAFLOW_SIM_PORT));
            } else if (sel == CommSettings.Mode.MARAPROT_TCP) {
                portField.setText(String.valueOf(CommSettings.DEFAULT_MARAPROT_TCP_PORT));
            }
            testConnBtn.setEnabled(sel == CommSettings.Mode.MARAFLOW_SIM || sel == CommSettings.Mode.MARAPROT_TCP);
            testStatusLabel.setText(" ");
        });
        testConnBtn.addActionListener(e -> {
            String host = hostField.getText().trim();
            int port = parseIntOrDefault(portField.getText(), CommSettings.DEFAULT_MARAPROT_TCP_PORT);
            int station = parseIntOrDefault(stationField.getText(), 1);
            String pw = ConnectServerDialog.loadSavedPassword();
            testConnBtn.setEnabled(false);
            testStatusLabel.setForeground(Color.DARK_GRAY);
            testStatusLabel.setText("Connecting...");
            Thread.ofVirtual().start(() -> {
                MaraflowSimClient.SimStatus st = MaraflowSimClient.testConnection(host, port, station, pw);
                SwingUtilities.invokeLater(() -> {
                    CommSettings.Mode sel = (CommSettings.Mode) modeCombo.getSelectedItem();
                    testConnBtn.setEnabled(sel == CommSettings.Mode.MARAFLOW_SIM || sel == CommSettings.Mode.MARAPROT_TCP);
                    if (st.online()) {
                        statusOnline(testStatusLabel, st);
                    } else {
                        testStatusLabel.setForeground(new Color(0xD9, 0x26, 0x26));
                        testStatusLabel.setText("Offline: " + (st.errorMessage().isBlank() ? "Connection refused" : st.errorMessage()));
                    }
                });
            });
        });
        JPanel testRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        testRow.add(testConnBtn);
        testRow.add(testStatusLabel);
        addRow(commPanel, g2, r2++, "Diagnostics:", testRow);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Project Info", infoPanel);
        tabs.addTab("Communication", commPanel);
        if (initialTab >= 0 && initialTab < tabs.getTabCount()) {
            tabs.setSelectedIndex(initialTab);
        }

        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(e -> {
            project.setProjectName(nameField.getText());
            project.setAuthor(authorField.getText());
            project.setDescription(descArea.getText());
            project.setLinkedMflPath(mflField.getText());

            comm.setMode((CommSettings.Mode) modeCombo.getSelectedItem());
            comm.setHost(hostField.getText());
            comm.setTcpPort(parseIntOrDefault(portField.getText(), CommSettings.DEFAULT_MARAPROT_TCP_PORT));
            comm.setStationId(parseIntOrDefault(stationField.getText(), 1));
            comm.setPollIntervalMs(((Number) pollSpin.getValue()).intValue());

            project.setDirty(true);
            confirmed[0] = true;
            dlg.dispose();
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dlg.dispose());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btns.add(okBtn);
        btns.add(cancelBtn);

        dlg.setLayout(new BorderLayout());
        dlg.add(tabs, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.getRootPane().setDefaultButton(okBtn);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
        return confirmed[0];
    }

    private static GridBagConstraints createGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
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

    private static void statusOnline(JLabel lbl, MaraflowSimClient.SimStatus st) {
        lbl.setForeground(new Color(0x1E, 0x84, 0x49));
        String projInfo = st.projectName().isBlank() ? "" : (" [" + st.projectName() + "]");
        lbl.setText("Connected: " + st.simState() + projInfo);
    }

    private static int parseIntOrDefault(String s, int def) {
        if (s == null || s.isBlank()) return def;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }
}
