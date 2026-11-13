package cz.maramat.scada.ui;

import cz.maramat.scada.config.UserConfig;
import cz.maramat.scada.model.CommSettings;
import cz.maramat.scada.model.ScadaScreen;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.util.function.Consumer;

/**
 * Multi-tabbed User Preferences dialog for configuring workspace, appearance,
 * canvas behavior, simulation/alarms, and default communication settings.
 */
public final class UserPreferencesDialog {

    private UserPreferencesDialog() {
    }

    public static boolean showDialog(Window owner, UserConfig liveConfig, Consumer<UserConfig> onApply) {
        JDialog dlg = new JDialog(owner, "User Preferences", JDialog.ModalityType.APPLICATION_MODAL);
        final boolean[] confirmed = {false};

        UserConfig working = liveConfig.copy();

        // --- Tab 1: General & Files ---
        JCheckBox reopenLastChk = new JCheckBox("Reopen last project on startup", working.isReopenLastProjectOnStartup());
        JCheckBox confirmUnsavedChk = new JCheckBox("Confirm before discarding unsaved changes", working.isConfirmUnsavedChanges());
        JCheckBox rememberBoundsChk = new JCheckBox("Remember window size and maximized state", working.isRememberWindowBounds());
        JTextField defaultDirField = new JTextField(working.getDefaultProjectDir(), 22);
        JButton browseDirBtn = new JButton("Browse...");
        browseDirBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (!defaultDirField.getText().isBlank()) {
                File f = new File(defaultDirField.getText().trim());
                if (f.isDirectory()) fc.setCurrentDirectory(f);
            }
            if (fc.showOpenDialog(dlg) == JFileChooser.APPROVE_OPTION && fc.getSelectedFile() != null) {
                defaultDirField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        JSpinner maxRecentSpin = new JSpinner(new SpinnerNumberModel(working.getMaxRecentProjects(), 0, 20, 1));
        JButton clearRecentBtn = new JButton("Clear Recent History");
        clearRecentBtn.addActionListener(e -> {
            working.clearRecentProjects();
            clearRecentBtn.setEnabled(false);
        });
        clearRecentBtn.setEnabled(!working.getRecentProjects().isEmpty());

        JPanel generalPanel = createFormPanel();
        GridBagConstraints g1 = createGbc();
        int r1 = 0;
        addFullRow(generalPanel, g1, r1++, reopenLastChk);
        addFullRow(generalPanel, g1, r1++, confirmUnsavedChk);
        addFullRow(generalPanel, g1, r1++, rememberBoundsChk);
        JPanel dirRow = new JPanel(new BorderLayout(4, 0));
        dirRow.add(defaultDirField, BorderLayout.CENTER);
        dirRow.add(browseDirBtn, BorderLayout.EAST);
        addRow(generalPanel, g1, r1++, "Default Project Folder:", dirRow);
        JPanel recentRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        recentRow.add(maxRecentSpin);
        recentRow.add(clearRecentBtn);
        addRow(generalPanel, g1, r1++, "Max Recent Projects:", recentRow);
        addFiller(generalPanel, g1, r1);

        // --- Tab 2: Appearance ---
        JComboBox<UserConfig.Theme> themeCombo = new JComboBox<>(UserConfig.Theme.values());
        themeCombo.setSelectedItem(working.getTheme());

        JComboBox<String> fontCombo = new JComboBox<>(new String[]{working.getUiFontFamily()});
        fontCombo.setEditable(true);
        fontCombo.setSelectedItem(working.getUiFontFamily());
        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() {
                return GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            }

            @Override
            protected void done() {
                try {
                    String[] availableFonts = get();
                    Object current = fontCombo.getEditor().getItem();
                    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(availableFonts);
                    fontCombo.setModel(model);
                    fontCombo.setSelectedItem(current);
                } catch (Exception ignored) {
                }
            }
        }.execute();

        JSpinner fontSizeSpin = new JSpinner(new SpinnerNumberModel(working.getUiFontSize(), 9, 20, 1));
        JSpinner dockWidthSpin = new JSpinner(new SpinnerNumberModel(working.getLeftDockWidth(), 160, 500, 10));
        JCheckBox showStatusBarChk = new JCheckBox("Show bottom status bar", working.isShowStatusBar());
        JCheckBox showAlarmBannerChk = new JCheckBox("Show live alarm banner", working.isShowAlarmBanner());

        JPanel appearancePanel = createFormPanel();
        GridBagConstraints g2 = createGbc();
        int r2 = 0;
        addRow(appearancePanel, g2, r2++, "UI Theme:", themeCombo);
        addRow(appearancePanel, g2, r2++, "UI Font Family:", fontCombo);
        addRow(appearancePanel, g2, r2++, "UI Font Size (pt):", fontSizeSpin);
        addRow(appearancePanel, g2, r2++, "Left Dock Width (px):", dockWidthSpin);
        addFullRow(appearancePanel, g2, r2++, showStatusBarChk);
        addFullRow(appearancePanel, g2, r2++, showAlarmBannerChk);
        addFiller(appearancePanel, g2, r2);

        // --- Tab 3: Canvas & Editor ---
        Integer[] zoomChoices = new Integer[UserConfig.SUPPORTED_ZOOMS.length];
        for (int i = 0; i < UserConfig.SUPPORTED_ZOOMS.length; i++) {
            zoomChoices[i] = UserConfig.SUPPORTED_ZOOMS[i];
        }
        JComboBox<Integer> defaultZoomCombo = new JComboBox<>(zoomChoices);
        defaultZoomCombo.setSelectedItem(working.getDefaultZoomPercent());

        JSpinner canvasWSpin = new JSpinner(new SpinnerNumberModel(working.getCanvasGridWidth(), 40, 600, 10));
        JSpinner canvasHSpin = new JSpinner(new SpinnerNumberModel(working.getCanvasGridHeight(), 30, 400, 10));
        final Color[] screenBg = {working.getDefaultScreenBackgroundColor()};
        JButton screenBgBtn = new JButton("Choose Color...");
        screenBgBtn.setBackground(screenBg[0]);
        screenBgBtn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(dlg, "Default Screen Background", screenBg[0]);
            if (chosen != null) {
                screenBg[0] = chosen;
                screenBgBtn.setBackground(chosen);
            }
        });
        JButton presetSlateBtn = new JButton("Slate");
        presetSlateBtn.addActionListener(e -> {
            screenBg[0] = ScadaScreen.parseHexColor(ScadaScreen.BG_LIGHT_SLATE, Color.LIGHT_GRAY);
            screenBgBtn.setBackground(screenBg[0]);
        });
        JButton presetDarkBtn = new JButton("Dark");
        presetDarkBtn.addActionListener(e -> {
            screenBg[0] = ScadaScreen.parseHexColor(ScadaScreen.BG_DARK_CONTROL_ROOM, Color.DARK_GRAY);
            screenBgBtn.setBackground(screenBg[0]);
        });
        JButton presetWhiteBtn = new JButton("White");
        presetWhiteBtn.addActionListener(e -> {
            screenBg[0] = Color.WHITE;
            screenBgBtn.setBackground(Color.WHITE);
        });

        final Color[] gridDotCol = {working.getGridDotColor()};
        JButton gridDotBtn = new JButton("Choose Color...");
        gridDotBtn.setBackground(gridDotCol[0]);
        gridDotBtn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(dlg, "Grid Dot Color", gridDotCol[0]);
            if (chosen != null) {
                gridDotCol[0] = chosen;
                gridDotBtn.setBackground(chosen);
            }
        });

        JCheckBox showGridChk = new JCheckBox("Show grid dots by default", working.isShowGridByDefault());
        JCheckBox aaChk = new JCheckBox("Enable canvas anti-aliasing", working.isAntiAliasing());
        JCheckBox wheelZoomChk = new JCheckBox("Mouse wheel zooms canvas directly (without Ctrl/Cmd)", working.isMouseWheelZoom());

        JPanel canvasPanel = createFormPanel();
        GridBagConstraints g3 = createGbc();
        int r3 = 0;
        addRow(canvasPanel, g3, r3++, "Default Zoom (%):", defaultZoomCombo);
        JPanel sizeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sizeRow.add(canvasWSpin);
        sizeRow.add(new JLabel("x"));
        sizeRow.add(canvasHSpin);
        sizeRow.add(new JLabel("grid units"));
        addRow(canvasPanel, g3, r3++, "Canvas Grid Size:", sizeRow);
        JPanel bgRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        bgRow.add(screenBgBtn);
        bgRow.add(presetSlateBtn);
        bgRow.add(presetDarkBtn);
        bgRow.add(presetWhiteBtn);
        addRow(canvasPanel, g3, r3++, "Default Screen BG:", bgRow);
        addRow(canvasPanel, g3, r3++, "Grid Dot Color:", gridDotBtn);
        addFullRow(canvasPanel, g3, r3++, showGridChk);
        addFullRow(canvasPanel, g3, r3++, aaChk);
        addFullRow(canvasPanel, g3, r3++, wheelZoomChk);
        addFiller(canvasPanel, g3, r3);

        // --- Tab 4: Simulation & Alarms ---
        JSpinner syncIntervalSpin = new JSpinner(new SpinnerNumberModel(working.getRuntimeSyncIntervalMs(), 50, 5000, 25));
        JComboBox<Integer> animFpsCombo = new JComboBox<>(new Integer[]{15, 30, 60});
        animFpsCombo.setSelectedItem(working.getAnimationFps());
        JCheckBox flowAnimChk = new JCheckBox("Animate fluid flow in active pipes", working.isShowFlowAnimation());
        JSpinner flowSpeedSpin = new JSpinner(new SpinnerNumberModel(working.getFlowAnimationSpeedPxPerSec(), 5.0, 200.0, 2.0));
        JCheckBox autoStartOpChk = new JCheckBox("Auto-start simulation when entering Operator Mode", working.isAutoStartSimulationInOperatorMode());
        JCheckBox resetIoChk = new JCheckBox("Reset all tag I/O values to 0 on simulation Stop", working.isResetIoOnSimStop());
        JCheckBox ackAlarmsChk = new JCheckBox("Acknowledge all alarms on simulation Stop", working.isAcknowledgeAlarmsOnSimStop());
        JCheckBox alarmFlashChk = new JCheckBox("Enable 2 Hz UNACK alarm badge flashing", working.isAlarmFlashEnabled());

        JPanel simPanel = createFormPanel();
        GridBagConstraints g4 = createGbc();
        int r4 = 0;
        addRow(simPanel, g4, r4++, "Runtime Sync Interval (ms):", syncIntervalSpin);
        addRow(simPanel, g4, r4++, "Animation Frame Rate (FPS):", animFpsCombo);
        addRow(simPanel, g4, r4++, "Flow Animation Speed (px/s):", flowSpeedSpin);
        addFullRow(simPanel, g4, r4++, flowAnimChk);
        addFullRow(simPanel, g4, r4++, autoStartOpChk);
        addFullRow(simPanel, g4, r4++, resetIoChk);
        addFullRow(simPanel, g4, r4++, ackAlarmsChk);
        addFullRow(simPanel, g4, r4++, alarmFlashChk);
        addFiller(simPanel, g4, r4);

        // --- Tab 5: Default Communication ---
        CommSettings defComm = working.getDefaultCommSettings();
        JComboBox<CommSettings.Mode> commModeCombo = new JComboBox<>(CommSettings.Mode.values());
        commModeCombo.setSelectedItem(defComm.getMode());
        JTextField hostField = new JTextField(defComm.getHost(), 16);
        JSpinner tcpPortSpin = new JSpinner(new SpinnerNumberModel(defComm.getTcpPort(), 1, 65535, 1));
        JTextField serialField = new JTextField(defComm.getSerialPort(), 16);
        JSpinner baudSpin = new JSpinner(new SpinnerNumberModel(defComm.getBaudRate(), 1200, 921600, 9600));
        JSpinner slaveIdSpin = new JSpinner(new SpinnerNumberModel(defComm.getSlaveId(), 0, 255, 1));
        JSpinner pollSpin = new JSpinner(new SpinnerNumberModel(defComm.getPollIntervalMs(), 10, 10000, 10));

        JPanel commPanel = createFormPanel();
        GridBagConstraints g5 = createGbc();
        int r5 = 0;
        addRow(commPanel, g5, r5++, "Default Mode:", commModeCombo);
        addRow(commPanel, g5, r5++, "Default Host / IP:", hostField);
        addRow(commPanel, g5, r5++, "Default TCP Port:", tcpPortSpin);
        addRow(commPanel, g5, r5++, "Default Serial Port:", serialField);
        addRow(commPanel, g5, r5++, "Default Baud Rate:", baudSpin);
        addRow(commPanel, g5, r5++, "Default Slave ID:", slaveIdSpin);
        addRow(commPanel, g5, r5++, "Default Poll Interval (ms):", pollSpin);
        addFiller(commPanel, g5, r5);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("General & Files", generalPanel);
        tabs.addTab("Appearance", appearancePanel);
        tabs.addTab("Canvas & Editor", canvasPanel);
        tabs.addTab("Simulation & Alarms", simPanel);
        tabs.addTab("Default Comm", commPanel);

        Runnable applyFieldsToWorking = () -> {
            working.setReopenLastProjectOnStartup(reopenLastChk.isSelected());
            working.setConfirmUnsavedChanges(confirmUnsavedChk.isSelected());
            working.setRememberWindowBounds(rememberBoundsChk.isSelected());
            working.setDefaultProjectDir(defaultDirField.getText());
            working.setMaxRecentProjects(((Number) maxRecentSpin.getValue()).intValue());

            working.setTheme((UserConfig.Theme) themeCombo.getSelectedItem());
            Object fontSel = fontCombo.getSelectedItem();
            working.setUiFontFamily(fontSel != null ? fontSel.toString() : "Segoe UI");
            working.setUiFontSize(((Number) fontSizeSpin.getValue()).intValue());
            working.setLeftDockWidth(((Number) dockWidthSpin.getValue()).intValue());
            working.setShowStatusBar(showStatusBarChk.isSelected());
            working.setShowAlarmBanner(showAlarmBannerChk.isSelected());

            working.setDefaultZoomPercent((Integer) defaultZoomCombo.getSelectedItem());
            working.setCanvasGridWidth(((Number) canvasWSpin.getValue()).intValue());
            working.setCanvasGridHeight(((Number) canvasHSpin.getValue()).intValue());
            working.setDefaultScreenBackgroundHex(toHex(screenBg[0]));
            working.setGridDotColorHex(toHex(gridDotCol[0]));
            working.setShowGridByDefault(showGridChk.isSelected());
            working.setAntiAliasing(aaChk.isSelected());
            working.setMouseWheelZoom(wheelZoomChk.isSelected());

            working.setRuntimeSyncIntervalMs(((Number) syncIntervalSpin.getValue()).intValue());
            Integer fpsSel = (Integer) animFpsCombo.getSelectedItem();
            if (fpsSel != null) {
                working.setAnimationFps(fpsSel);
            }
            working.setFlowAnimationSpeedPxPerSec(((Number) flowSpeedSpin.getValue()).doubleValue());
            working.setShowFlowAnimation(flowAnimChk.isSelected());
            working.setAutoStartSimulationInOperatorMode(autoStartOpChk.isSelected());
            working.setResetIoOnSimStop(resetIoChk.isSelected());
            working.setAcknowledgeAlarmsOnSimStop(ackAlarmsChk.isSelected());
            working.setAlarmFlashEnabled(alarmFlashChk.isSelected());

            CommSettings c = working.getDefaultCommSettings();
            c.setMode((CommSettings.Mode) commModeCombo.getSelectedItem());
            c.setHost(hostField.getText());
            c.setTcpPort(((Number) tcpPortSpin.getValue()).intValue());
            c.setSerialPort(serialField.getText());
            c.setBaudRate(((Number) baudSpin.getValue()).intValue());
            c.setSlaveId(((Number) slaveIdSpin.getValue()).intValue());
            c.setPollIntervalMs(((Number) pollSpin.getValue()).intValue());
        };

        JButton restoreBtn = new JButton("Restore Defaults");
        restoreBtn.addActionListener(e -> {
            UserConfig def = new UserConfig();
            reopenLastChk.setSelected(def.isReopenLastProjectOnStartup());
            confirmUnsavedChk.setSelected(def.isConfirmUnsavedChanges());
            rememberBoundsChk.setSelected(def.isRememberWindowBounds());
            defaultDirField.setText(def.getDefaultProjectDir());
            maxRecentSpin.setValue(def.getMaxRecentProjects());

            themeCombo.setSelectedItem(def.getTheme());
            fontCombo.setSelectedItem(def.getUiFontFamily());
            fontSizeSpin.setValue(def.getUiFontSize());
            dockWidthSpin.setValue(def.getLeftDockWidth());
            showStatusBarChk.setSelected(def.isShowStatusBar());
            showAlarmBannerChk.setSelected(def.isShowAlarmBanner());

            defaultZoomCombo.setSelectedItem(def.getDefaultZoomPercent());
            canvasWSpin.setValue(def.getCanvasGridWidth());
            canvasHSpin.setValue(def.getCanvasGridHeight());
            screenBg[0] = def.getDefaultScreenBackgroundColor();
            screenBgBtn.setBackground(screenBg[0]);
            gridDotCol[0] = def.getGridDotColor();
            gridDotBtn.setBackground(gridDotCol[0]);
            showGridChk.setSelected(def.isShowGridByDefault());
            aaChk.setSelected(def.isAntiAliasing());
            wheelZoomChk.setSelected(def.isMouseWheelZoom());

            syncIntervalSpin.setValue(def.getRuntimeSyncIntervalMs());
            flowSpeedSpin.setValue(def.getFlowAnimationSpeedPxPerSec());
            flowAnimChk.setSelected(def.isShowFlowAnimation());
            autoStartOpChk.setSelected(def.isAutoStartSimulationInOperatorMode());
            resetIoChk.setSelected(def.isResetIoOnSimStop());
            ackAlarmsChk.setSelected(def.isAcknowledgeAlarmsOnSimStop());
            alarmFlashChk.setSelected(def.isAlarmFlashEnabled());

            CommSettings dc = def.getDefaultCommSettings();
            commModeCombo.setSelectedItem(dc.getMode());
            hostField.setText(dc.getHost());
            tcpPortSpin.setValue(dc.getTcpPort());
            serialField.setText(dc.getSerialPort());
            baudSpin.setValue(dc.getBaudRate());
            slaveIdSpin.setValue(dc.getSlaveId());
            pollSpin.setValue(dc.getPollIntervalMs());
        });

        JButton applyBtn = new JButton("Apply");
        applyBtn.addActionListener(e -> {
            applyFieldsToWorking.run();
            if (onApply != null) {
                onApply.accept(working.copy());
            }
        });

        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(e -> {
            applyFieldsToWorking.run();
            if (onApply != null) {
                onApply.accept(working.copy());
            }
            confirmed[0] = true;
            dlg.dispose();
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dlg.dispose());

        JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftBtns.add(restoreBtn);
        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightBtns.add(applyBtn);
        rightBtns.add(okBtn);
        rightBtns.add(cancelBtn);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));
        footer.add(leftBtns, BorderLayout.WEST);
        footer.add(rightBtns, BorderLayout.EAST);

        dlg.setLayout(new BorderLayout());
        dlg.add(tabs, BorderLayout.CENTER);
        dlg.add(footer, BorderLayout.SOUTH);
        dlg.getRootPane().setDefaultButton(okBtn);
        dlg.pack();
        dlg.setMinimumSize(new java.awt.Dimension(540, 420));
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
        return confirmed[0];
    }

    private static JPanel createFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        return panel;
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
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(comp, gbc);
    }

    private static void addFullRow(JPanel panel, GridBagConstraints gbc, int row, java.awt.Component comp) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        panel.add(comp, gbc);
    }

    private static void addFiller(JPanel panel, GridBagConstraints gbc, int row) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weighty = 1.0;
        panel.add(new JPanel(), gbc);
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}
