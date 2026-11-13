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

        // --- Tab 2: Communication ---
        CommSettings comm = project.getCommSettings();
        JComboBox<CommSettings.Mode> modeCombo = new JComboBox<>(CommSettings.Mode.values());
        modeCombo.setSelectedItem(comm.getMode());
        JTextField hostField = new JTextField(comm.getHost(), 18);
        JSpinner portSpin = new JSpinner(new SpinnerNumberModel(comm.getTcpPort(), 1, 65535, 1));
        JTextField serialField = new JTextField(comm.getSerialPort(), 18);
        JSpinner baudSpin = new JSpinner(new SpinnerNumberModel(comm.getBaudRate(), 1200, 921600, 9600));
        JSpinner slaveSpin = new JSpinner(new SpinnerNumberModel(comm.getSlaveId(), 0, 255, 1));
        JSpinner pollSpin = new JSpinner(new SpinnerNumberModel(comm.getPollIntervalMs(), 10, 10000, 10));

        JPanel commPanel = new JPanel(new GridBagLayout());
        commPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        GridBagConstraints g2 = createGbc();
        int r2 = 0;
        addRow(commPanel, g2, r2++, "Driver / Mode:", modeCombo);
        addRow(commPanel, g2, r2++, "Host / IP:", hostField);
        addRow(commPanel, g2, r2++, "TCP Port:", portSpin);
        addRow(commPanel, g2, r2++, "Serial Port:", serialField);
        addRow(commPanel, g2, r2++, "Baud Rate:", baudSpin);
        addRow(commPanel, g2, r2++, "Slave / Unit ID:", slaveSpin);
        addRow(commPanel, g2, r2++, "Poll Interval (ms):", pollSpin);

        JButton testConnBtn = new JButton("Test Maraflow Sim Connection");
        JLabel testStatusLabel = new JLabel(" ");
        testConnBtn.setEnabled(comm.getMode() == CommSettings.Mode.MARAFLOW_SIM);
        modeCombo.addActionListener(e -> {
            CommSettings.Mode sel = (CommSettings.Mode) modeCombo.getSelectedItem();
            int curPort = ((Number) portSpin.getValue()).intValue();
            if (sel == CommSettings.Mode.MARAFLOW_SIM && curPort == 502) {
                portSpin.setValue(CommSettings.DEFAULT_MARAFLOW_SIM_PORT);
            } else if (sel == CommSettings.Mode.MODBUS_TCP && curPort == CommSettings.DEFAULT_MARAFLOW_SIM_PORT) {
                portSpin.setValue(502);
            }
            testConnBtn.setEnabled(sel == CommSettings.Mode.MARAFLOW_SIM);
            testStatusLabel.setText(" ");
        });
        testConnBtn.addActionListener(e -> {
            String host = hostField.getText().trim();
            int port = ((Number) portSpin.getValue()).intValue();
            testConnBtn.setEnabled(false);
            testStatusLabel.setForeground(Color.DARK_GRAY);
            testStatusLabel.setText("Connecting...");
            Thread.ofVirtual().start(() -> {
                MaraflowSimClient.SimStatus st = MaraflowSimClient.testConnection(host, port);
                SwingUtilities.invokeLater(() -> {
                    testConnBtn.setEnabled(modeCombo.getSelectedItem() == CommSettings.Mode.MARAFLOW_SIM);
                    if (st.online()) {
                        testStatusLabel.setForeground(new Color(0x1E, 0x84, 0x49));
                        String projInfo = st.projectName().isBlank() ? "" : (" [" + st.projectName() + "]");
                        testStatusLabel.setText("Connected: " + st.simState() + projInfo);
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
            comm.setTcpPort(((Number) portSpin.getValue()).intValue());
            comm.setSerialPort(serialField.getText());
            comm.setBaudRate(((Number) baudSpin.getValue()).intValue());
            comm.setSlaveId(((Number) slaveSpin.getValue()).intValue());
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
}
