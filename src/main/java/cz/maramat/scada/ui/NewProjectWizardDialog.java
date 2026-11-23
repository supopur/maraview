package cz.maramat.scada.ui;

import cz.maramat.scada.config.UserConfig;
import cz.maramat.scada.io.MslProjectSerializer;
import cz.maramat.scada.model.CommSettings;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaScreen;
import cz.maramat.scada.tags.MflTagImporter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;

/**
 * 3-Step New Project Wizard for Maraview matching Maraflow's wizard layout:
 * Step 1: Project Identity & File Location
 * Step 2: PLC / Simulator Communication & Optional .mfl Link
 * Step 3: Initial HMI Screen Configuration
 */
public class NewProjectWizardDialog extends JDialog {

    private int currentStep = 0;
    private ScadaProject createdProject = null;
    private final UserConfig userConfig;

    private final JLabel[] stepLabels = new JLabel[3];
    private final CardLayout stepCards = new CardLayout();
    private final JPanel centerCardPanel = new JPanel(stepCards);

    // Step 1 controls
    private JTextField projectNameField;
    private JTextField locationDirField;
    private JCheckBox saveImmediatelyCheck;
    private JTextField authorField;
    private JTextArea descriptionArea;

    // Step 2 controls
    private JComboBox<CommSettings.Mode> commModeCombo;
    private JTextField hostField;
    private JTextField portField;
    private JSpinner pollSpin;
    private JTextField mflPathField;
    private JCheckBox importMflTagsCheck;

    // Step 3 controls
    private JTextField initialScreenNameField;
    private JComboBox<String> bgPresetCombo;
    private JSpinner gridSizeSpin;
    private JCheckBox showGridCheck;

    // Navigation buttons
    private JButton backBtn;
    private JButton nextBtn;
    private JButton finishBtn;

    private static final String[] BG_PRESETS = {
            "Industrial Light Slate (#E2E6EA)",
            "Classic White (#FFFFFF)",
            "Dark Control Room (#2B3036)"
    };

    public NewProjectWizardDialog(Frame owner, UserConfig userConfig, boolean preselectMaraflowSim) {
        super(owner, "New Project Wizard - Maraview SCADA", true);
        this.userConfig = (userConfig == null) ? new UserConfig() : userConfig;
        buildUi(preselectMaraflowSim);
        updateStepAppearance();
        pack();
        setMinimumSize(new Dimension(680, 440));
        setLocationRelativeTo(owner);
    }

    private void buildUi(boolean preselectMaraflowSim) {
        JPanel root = new JPanel(new BorderLayout(0, 0));

        // Top Banner (Win7/95 engineering dialog header matching Maraflow)
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(new Color(0x1F, 0x4E, 0x79));
        banner.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JLabel bannerTitle = new JLabel("Create New Maraview SCADA Project");
        bannerTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        bannerTitle.setForeground(Color.WHITE);
        JLabel bannerSub = new JLabel("Configure project identity, PLC/simulator link, and initial screen.");
        bannerSub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        bannerSub.setForeground(new Color(0xD6, 0xEA, 0xF8));
        banner.add(bannerTitle, BorderLayout.NORTH);
        banner.add(bannerSub, BorderLayout.SOUTH);
        root.add(banner, BorderLayout.NORTH);

        // Left Step Sidebar
        JPanel sidebar = new JPanel(new GridLayout(6, 1, 0, 4));
        sidebar.setBackground(new Color(0xE6, 0xEC, 0xF2));
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0xB8, 0xB8, 0xB8)),
                BorderFactory.createEmptyBorder(12, 10, 12, 10)
        ));
        sidebar.setPreferredSize(new Dimension(185, 0));

        String[] stepTitles = {
                "1. Project Identity",
                "2. PLC & Simulator",
                "3. Initial Screen"
        };
        for (int i = 0; i < 3; i++) {
            JLabel lbl = new JLabel("  " + stepTitles[i]);
            lbl.setOpaque(true);
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
            lbl.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
            stepLabels[i] = lbl;
            sidebar.add(lbl);
        }
        root.add(sidebar, BorderLayout.WEST);

        // Center Cards
        centerCardPanel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        centerCardPanel.add(buildStep1Panel(), "STEP_0");
        centerCardPanel.add(buildStep2Panel(preselectMaraflowSim), "STEP_1");
        centerCardPanel.add(buildStep3Panel(), "STEP_2");
        root.add(centerCardPanel, BorderLayout.CENTER);

        // Bottom Navigation Bar
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        bottomBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xB8, 0xB8, 0xB8)));

        backBtn = new JButton("< Back");
        backBtn.setPreferredSize(new Dimension(90, 28));
        backBtn.addActionListener(e -> {
            if (currentStep > 0) {
                currentStep--;
                updateStepAppearance();
            }
        });

        nextBtn = new JButton("Next >");
        nextBtn.setPreferredSize(new Dimension(90, 28));
        nextBtn.addActionListener(e -> {
            if (validateCurrentStep() && currentStep < 2) {
                currentStep++;
                updateStepAppearance();
            }
        });

        finishBtn = new JButton("Finish");
        finishBtn.setPreferredSize(new Dimension(100, 28));
        finishBtn.addActionListener(e -> onFinish());

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setPreferredSize(new Dimension(95, 28));
        cancelBtn.addActionListener(e -> dispose());

        bottomBar.add(backBtn);
        bottomBar.add(nextBtn);
        bottomBar.add(finishBtn);
        bottomBar.add(cancelBtn);
        root.add(bottomBar, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildStep1Panel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        String defaultDir = !userConfig.getDefaultProjectDir().isBlank()
                ? userConfig.getDefaultProjectDir()
                : (!userConfig.getLastOpenedDir().isBlank()
                ? userConfig.getLastOpenedDir()
                : System.getProperty("user.home"));

        projectNameField = new JTextField("scada_project_1", 24);
        locationDirField = new JTextField(defaultDir, 20);
        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(locationDirField.getText());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                locationDirField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });

        saveImmediatelyCheck = new JCheckBox("Create .msl project file on disk immediately", false);
        authorField = new JTextField(System.getProperty("user.name", ""), 24);
        descriptionArea = new JTextArea(4, 24);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);

        int row = 0;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        p.add(new JLabel("Project Name:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        p.add(projectNameField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        p.add(new JLabel("Project Directory:"), gbc);
        JPanel dirPanel = new JPanel(new BorderLayout(4, 0));
        dirPanel.add(locationDirField, BorderLayout.CENTER);
        dirPanel.add(browseBtn, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 1.0;
        p.add(dirPanel, gbc);

        row++;
        gbc.gridx = 1; gbc.gridy = row;
        p.add(saveImmediatelyCheck, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        p.add(new JLabel("Author / Engineer:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        p.add(authorField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        p.add(new JLabel("Description:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        p.add(new JScrollPane(descriptionArea), gbc);

        return p;
    }

    private JPanel buildStep2Panel(boolean preselectMaraflowSim) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        CommSettings def = userConfig.getDefaultCommSettings();
        CommSettings.Mode initialMode = preselectMaraflowSim ? CommSettings.Mode.MARAFLOW_SIM : def.getMode();
        int initialPort = (initialMode == CommSettings.Mode.MARAFLOW_SIM && def.getTcpPort() == CommSettings.DEFAULT_MARAPROT_TCP_PORT)
                ? CommSettings.DEFAULT_MARAFLOW_SIM_PORT
                : def.getTcpPort();

        commModeCombo = new JComboBox<>(CommSettings.Mode.values());
        commModeCombo.setSelectedItem(initialMode);
        hostField = new JTextField(def.getHost(), 20);
        portField = ConnectServerDialog.createDigitsOnlyField(String.valueOf(initialPort), 8);
        pollSpin = new JSpinner(new SpinnerNumberModel(def.getPollIntervalMs(), 10, 10000, 10));

        commModeCombo.addActionListener(e -> {
            CommSettings.Mode sel = (CommSettings.Mode) commModeCombo.getSelectedItem();
            if (sel == CommSettings.Mode.MARAFLOW_SIM) {
                portField.setText(String.valueOf(CommSettings.DEFAULT_MARAFLOW_SIM_PORT));
            } else if (sel == CommSettings.Mode.MARAPROT_TCP) {
                portField.setText(String.valueOf(CommSettings.DEFAULT_MARAPROT_TCP_PORT));
            }
        });

        mflPathField = new JTextField("", 20);
        JButton browseMflBtn = new JButton("Browse...");
        browseMflBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(locationDirField.getText());
            fc.setFileFilter(new FileNameExtensionFilter("Maraflow PLC Project (*.mfl)", "mfl"));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION && fc.getSelectedFile() != null) {
                mflPathField.setText(fc.getSelectedFile().getAbsolutePath());
                importMflTagsCheck.setSelected(true);
            }
        });
        importMflTagsCheck = new JCheckBox("Import tags from linked .mfl immediately", preselectMaraflowSim);

        int row = 0;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        p.add(new JLabel("Driver / Mode:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        p.add(commModeCombo, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        p.add(new JLabel("Host / IP:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        p.add(hostField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        p.add(new JLabel("TCP Port:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        p.add(portField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        p.add(new JLabel("Poll Interval (ms):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        p.add(pollSpin, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        p.add(new JLabel("Linked .mfl (Optional):"), gbc);
        JPanel mflRow = new JPanel(new BorderLayout(4, 0));
        mflRow.add(mflPathField, BorderLayout.CENTER);
        mflRow.add(browseMflBtn, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 1.0;
        p.add(mflRow, gbc);

        row++;
        gbc.gridx = 1; gbc.gridy = row;
        p.add(importMflTagsCheck, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weighty = 1.0;
        p.add(new JLabel(), gbc);

        return p;
    }

    private JPanel buildStep3Panel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        initialScreenNameField = new JTextField("Overview", 20);
        bgPresetCombo = new JComboBox<>(BG_PRESETS);
        gridSizeSpin = new JSpinner(new SpinnerNumberModel(ScadaScreen.DEFAULT_GRID_SIZE_PX, 5, 40, 1));
        showGridCheck = new JCheckBox("Show alignment grid dots in Design Mode", userConfig.isShowGridByDefault());

        int row = 0;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        p.add(new JLabel("Initial Screen Name:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        p.add(initialScreenNameField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        p.add(new JLabel("Background Style:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        p.add(bgPresetCombo, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        p.add(new JLabel("Grid Size (px):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        p.add(gridSizeSpin, gbc);

        row++;
        gbc.gridx = 1; gbc.gridy = row;
        p.add(showGridCheck, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weighty = 1.0;
        p.add(new JLabel(), gbc);

        return p;
    }

    private void updateStepAppearance() {
        stepCards.show(centerCardPanel, "STEP_" + currentStep);
        for (int i = 0; i < stepLabels.length; i++) {
            if (i == currentStep) {
                stepLabels[i].setBackground(new Color(0x1F, 0x4E, 0x79));
                stepLabels[i].setForeground(Color.WHITE);
            } else {
                stepLabels[i].setBackground(new Color(0xE6, 0xEC, 0xF2));
                stepLabels[i].setForeground(new Color(0x33, 0x33, 0x33));
            }
        }
        backBtn.setEnabled(currentStep > 0);
        nextBtn.setEnabled(currentStep < 2);
        finishBtn.setEnabled(currentStep == 2 || !projectNameField.getText().trim().isEmpty());
    }

    private boolean validateCurrentStep() {
        if (currentStep == 0 && projectNameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Project name cannot be empty.", "Validation", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private void onFinish() {
        String name = projectNameField.getText().trim();
        if (name.isEmpty()) {
            currentStep = 0;
            updateStepAppearance();
            JOptionPane.showMessageDialog(this, "Project name cannot be empty.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ScadaProject p = new ScadaProject();
        p.setProjectName(name);
        p.setAuthor(authorField.getText().trim());
        p.setDescription(descriptionArea.getText().trim());

        CommSettings comm = p.getCommSettings();
        comm.setMode((CommSettings.Mode) commModeCombo.getSelectedItem());
        comm.setHost(hostField.getText());
        int parsedPort = CommSettings.DEFAULT_MARAPROT_TCP_PORT;
        try { parsedPort = Integer.parseInt(portField.getText().trim()); } catch (NumberFormatException ignored) {}
        comm.setTcpPort(parsedPort);
        comm.setPollIntervalMs(((Number) pollSpin.getValue()).intValue());

        ScadaScreen scr = p.getScreens().getFirst();
        String scrName = initialScreenNameField.getText().trim();
        scr.setName(scrName.isEmpty() ? "Overview" : scrName);
        int bgIdx = bgPresetCombo.getSelectedIndex();
        scr.setBackgroundColorHex(switch (bgIdx) {
            case 1 -> ScadaScreen.BG_WHITE;
            case 2 -> ScadaScreen.BG_DARK_CONTROL_ROOM;
            default -> ScadaScreen.BG_LIGHT_SLATE;
        });
        scr.setGridSizePx(((Number) gridSizeSpin.getValue()).intValue());
        scr.setShowGrid(showGridCheck.isSelected());

        String mflRaw = mflPathField.getText().trim();
        if (!mflRaw.isEmpty()) {
            p.setLinkedMflPath(mflRaw);
            File mflFile = new File(mflRaw);
            if (importMflTagsCheck.isSelected() && mflFile.isFile()) {
                try {
                    MflTagImporter.importIntoProject(mflFile, p);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this,
                            "Project created, but failed to import .mfl tags:\n" + ex.getMessage(),
                            "Import Warning", JOptionPane.WARNING_MESSAGE);
                }
            }
        }

        if (saveImmediatelyCheck.isSelected()) {
            File dir = new File(locationDirField.getText().trim());
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String fileName = name.endsWith(".msl") ? name : (name + ".msl");
            File target = new File(dir, fileName);
            try {
                MslProjectSerializer.saveProject(p, target);
                p.setDirty(false);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to save .msl file:\n" + ex.getMessage(),
                        "Save Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            p.setDirty(false);
        }

        this.createdProject = p;
        dispose();
    }

    public ScadaProject getCreatedProject() {
        return createdProject;
    }
}
