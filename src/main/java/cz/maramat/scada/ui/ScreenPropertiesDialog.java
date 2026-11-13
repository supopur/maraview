package cz.maramat.scada.ui;

import cz.maramat.scada.model.ScadaScreen;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

/**
 * Dialog for editing properties of a single {@link ScadaScreen} tab.
 */
public final class ScreenPropertiesDialog {

    private ScreenPropertiesDialog() {
    }

    public static boolean showDialog(Window owner, ScadaScreen screen) {
        if (screen == null) return false;
        JDialog dlg = new JDialog(owner, "Screen Properties", JDialog.ModalityType.APPLICATION_MODAL);
        final boolean[] confirmed = {false};

        JTextField nameField = new JTextField(screen.getName(), 20);
        final Color[] bgCol = {screen.getBackgroundColor()};
        JButton bgBtn = new JButton("Choose Color...");
        bgBtn.setBackground(bgCol[0]);
        bgBtn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(dlg, "Screen Background Color", bgCol[0]);
            if (chosen != null) {
                bgCol[0] = chosen;
                bgBtn.setBackground(chosen);
            }
        });
        JButton slateBtn = new JButton("Slate");
        slateBtn.addActionListener(e -> {
            bgCol[0] = ScadaScreen.parseHexColor(ScadaScreen.BG_LIGHT_SLATE, Color.LIGHT_GRAY);
            bgBtn.setBackground(bgCol[0]);
        });
        JButton darkBtn = new JButton("Dark");
        darkBtn.addActionListener(e -> {
            bgCol[0] = ScadaScreen.parseHexColor(ScadaScreen.BG_DARK_CONTROL_ROOM, Color.DARK_GRAY);
            bgBtn.setBackground(bgCol[0]);
        });
        JButton whiteBtn = new JButton("White");
        whiteBtn.addActionListener(e -> {
            bgCol[0] = Color.WHITE;
            bgBtn.setBackground(Color.WHITE);
        });

        JCheckBox showGridChk = new JCheckBox("Show grid dots on this screen", screen.isShowGrid());
        JSpinner gridPxSpin = new JSpinner(new SpinnerNumberModel(screen.getGridSizePx(), 4, 40, 1));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(form, gbc, row++, "Screen Name:", nameField);

        JPanel bgRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        bgRow.add(bgBtn);
        bgRow.add(slateBtn);
        bgRow.add(darkBtn);
        bgRow.add(whiteBtn);
        addRow(form, gbc, row++, "Background:", bgRow);
        addRow(form, gbc, row++, "Base Grid Size (px):", gridPxSpin);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        form.add(showGridChk, gbc);

        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(e -> {
            screen.setName(nameField.getText());
            screen.setBackgroundColorHex(String.format("#%02X%02X%02X",
                    bgCol[0].getRed(), bgCol[0].getGreen(), bgCol[0].getBlue()));
            screen.setShowGrid(showGridChk.isSelected());
            screen.setGridSizePx(((Number) gridPxSpin.getValue()).intValue());
            confirmed[0] = true;
            dlg.dispose();
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dlg.dispose());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btns.add(okBtn);
        btns.add(cancelBtn);

        dlg.setLayout(new BorderLayout());
        dlg.add(form, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.getRootPane().setDefaultButton(okBtn);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
        return confirmed[0];
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
}
