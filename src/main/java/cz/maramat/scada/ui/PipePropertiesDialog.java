package cz.maramat.scada.ui;

import cz.maramat.scada.model.ConnectionType;
import cz.maramat.scada.model.FluidType;
import cz.maramat.scada.model.PipeDiameter;
import cz.maramat.scada.model.ScadaPipe;
import cz.maramat.scada.model.ScadaProject;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

/**
 * Properties dialog for editing a process pipe or electrical/signal line.
 */
public class PipePropertiesDialog extends JDialog {

    private final ScadaPipe pipe;
    private final JComboBox<ConnectionType> connCombo = new JComboBox<>(ConnectionType.values());
    private final JComboBox<PipeDiameter> diameterCombo = new JComboBox<>(PipeDiameter.values());
    private final JComboBox<FluidType> fluidCombo = new JComboBox<>(FluidType.values());
    private final JComboBox<ScadaPipe.FlowMode> flowModeCombo = new JComboBox<>(ScadaPipe.FlowMode.values());
    private final JComboBox<String> flowTagCombo = new JComboBox<>();
    private final JTextField labelField = new JTextField(16);
    private final JCheckBox showLabelCheck = new JCheckBox("Show Label on Pipe");
    private final JCheckBox showFlangesCheck = new JCheckBox("Show End Flanges");
    private final JTextField colorHexField = new JTextField(8);
    private boolean confirmed;

    public PipePropertiesDialog(Window owner, ScadaProject project, ScadaPipe pipe) {
        super(owner, "Pipe Properties", ModalityType.APPLICATION_MODAL);
        this.pipe = pipe;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(460, 420);
        setLocationRelativeTo(owner);

        connCombo.setSelectedItem(pipe.getConnectionType());
        diameterCombo.setSelectedItem(pipe.getDiameter());
        fluidCombo.setSelectedItem(pipe.getFluidType());
        flowModeCombo.setSelectedItem(pipe.getFlowMode());

        flowTagCombo.setEditable(true);
        flowTagCombo.addItem("");
        if (project != null) {
            for (String tagName : project.getTags().keySet()) {
                flowTagCombo.addItem(tagName);
            }
        }
        flowTagCombo.setSelectedItem(pipe.getFlowTag() != null ? pipe.getFlowTag() : "");

        labelField.setText(pipe.getLabel() != null ? pipe.getLabel() : "");
        showLabelCheck.setSelected(pipe.isShowLabel());
        showFlangesCheck.setSelected(pipe.isShowFlanges());
        colorHexField.setText(pipe.getCustomColorHex() != null ? pipe.getCustomColorHex() : "");

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 5, 5, 5);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(form, gc, row++, "Connection Type:", connCombo);
        addRow(form, gc, row++, "Diameter:", diameterCombo);
        addRow(form, gc, row++, "Fluid Type:", fluidCombo);
        addRow(form, gc, row++, "Flow Behavior:", flowModeCombo);
        addRow(form, gc, row++, "Flow Tag:", flowTagCombo);
        addRow(form, gc, row++, "Pipe Label:", labelField);

        JPanel colorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        colorRow.add(colorHexField);
        JButton pickColorBtn = new JButton("Pick...");
        pickColorBtn.addActionListener(e -> {
            Color initial = parseHex(colorHexField.getText(), pipe.getFluidType().getBaseColor());
            Color chosen = JColorChooser.showDialog(this, "Choose Pipe Color", initial);
            if (chosen != null) {
                colorHexField.setText(String.format("#%02X%02X%02X", chosen.getRed(), chosen.getGreen(), chosen.getBlue()));
            }
        });
        JButton clearColorBtn = new JButton("Auto");
        clearColorBtn.addActionListener(e -> colorHexField.setText(""));
        colorRow.add(pickColorBtn);
        colorRow.add(clearColorBtn);
        addRow(form, gc, row++, "Custom Color:", colorRow);

        gc.gridx = 1;
        gc.gridy = row++;
        gc.weightx = 1.0;
        form.add(showLabelCheck, gc);

        gc.gridy = row++;
        form.add(showFlangesCheck, gc);

        flowModeCombo.addActionListener(e -> updateFieldStates());
        connCombo.addActionListener(e -> updateFieldStates());
        updateFieldStates();

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        JButton applyBtn = new JButton("Apply");
        applyBtn.addActionListener(e -> {
            applyToModel();
            confirmed = true;
            dispose();
        });
        buttons.add(cancelBtn);
        buttons.add(applyBtn);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(applyBtn);
    }

    private void addRow(JPanel panel, GridBagConstraints gc, int row, String label, java.awt.Component comp) {
        gc.gridx = 0;
        gc.gridy = row;
        gc.weightx = 0.0;
        panel.add(new JLabel(label), gc);
        gc.gridx = 1;
        gc.weightx = 1.0;
        panel.add(comp, gc);
    }

    private void updateFieldStates() {
        ConnectionType ct = (ConnectionType) connCombo.getSelectedItem();
        boolean isPipe = ct == null || ct == ConnectionType.PIPE;
        diameterCombo.setEnabled(isPipe);
        fluidCombo.setEnabled(isPipe);
        showFlangesCheck.setEnabled(isPipe);

        ScadaPipe.FlowMode mode = (ScadaPipe.FlowMode) flowModeCombo.getSelectedItem();
        flowTagCombo.setEnabled(mode == ScadaPipe.FlowMode.TAG);
    }

    private void applyToModel() {
        pipe.setConnectionType((ConnectionType) connCombo.getSelectedItem());
        pipe.setDiameter((PipeDiameter) diameterCombo.getSelectedItem());
        pipe.setFluidType((FluidType) fluidCombo.getSelectedItem());
        pipe.setFlowMode((ScadaPipe.FlowMode) flowModeCombo.getSelectedItem());
        Object tagVal = flowTagCombo.getEditor().getItem();
        pipe.setFlowTag(tagVal == null ? "" : tagVal.toString().trim());
        pipe.setLabel(labelField.getText());
        pipe.setShowLabel(showLabelCheck.isSelected());
        pipe.setShowFlanges(showFlangesCheck.isSelected());
        pipe.setCustomColorHex(colorHexField.getText());
    }

    private static Color parseHex(String hex, Color fallback) {
        if (hex == null || hex.isBlank()) return fallback;
        String clean = hex.trim();
        if (clean.startsWith("#")) clean = clean.substring(1);
        if (clean.length() != 6) return fallback;
        try {
            return new Color(Integer.parseInt(clean, 16));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public boolean isConfirmed() {
        return confirmed;
    }
}
