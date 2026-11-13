package cz.maramat.scada.ui;

import cz.maramat.scada.model.FluidType;
import cz.maramat.scada.model.PipePort;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaSymbolInstance;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Win95/7 split properties dialog:
 * Left JTable: Name, X, Y, Width, Height, Rotation, FluidType, Tooltip.
 * Right panel: Tag bindings (RunTag, SpeedTag, FaultTag, LevelTag, OpenTag, ValueTag, SetpointTag, Min, Max, EngineeringUnits)
 * with OperandPickerDialog buttons.
 */
public class ScadaSymbolPropertiesDialog extends JDialog {

    private static final String[] TAG_ROLES = {
            "RunTag", "SpeedTag", "FaultTag", "LevelTag", "OpenTag", "ValueTag", "SetpointTag"
    };

    private final ScadaProject project;
    private final ScadaSymbolInstance symbol;
    private final DefaultTableModel geoModel;
    private final Map<String, JTextField> tagFields = new LinkedHashMap<>();
    private final JTextField minField = new JTextField(10);
    private final JTextField maxField = new JTextField(10);
    private final JTextField unitsField = new JTextField(10);
    private boolean confirmed = false;

    public ScadaSymbolPropertiesDialog(Window owner, ScadaProject project, ScadaSymbolInstance symbol) {
        super(owner, symbol.getName() + " - Properties", ModalityType.APPLICATION_MODAL);
        this.project = project;
        this.symbol = symbol;
        setSize(720, 420);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(6, 6));

        // Left: Geometry & Appearance JTable
        geoModel = new DefaultTableModel(new Object[]{"Property", "Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1;
            }
        };
        geoModel.addRow(new Object[]{"Name", symbol.getName()});
        geoModel.addRow(new Object[]{"X", Integer.toString(symbol.getGridX())});
        geoModel.addRow(new Object[]{"Y", Integer.toString(symbol.getGridY())});
        geoModel.addRow(new Object[]{"Width", Integer.toString(symbol.getGridW())});
        geoModel.addRow(new Object[]{"Height", Integer.toString(symbol.getGridH())});
        geoModel.addRow(new Object[]{"Rotation", Integer.toString(symbol.getRotationDeg())});
        geoModel.addRow(new Object[]{"FluidType", symbol.getProperties().getOrDefault("FluidType", FluidType.WATER.name())});
        geoModel.addRow(new Object[]{"Tooltip", symbol.getTooltip()});

        JTable geoTable = new JTable(geoModel);
        geoTable.setRowHeight(22);
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Geometry & Appearance"));
        leftPanel.add(new JScrollPane(geoTable), BorderLayout.CENTER);

        // Right: Tag Bindings & Engineering Range
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Tag Bindings & Scaling"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        for (String role : TAG_ROLES) {
            gbc.gridy = row;
            gbc.gridx = 0;
            gbc.weightx = 0.0;
            rightPanel.add(new JLabel(role + ":"), gbc);

            JTextField tf = new JTextField(symbol.getTagBindings().getOrDefault(role, ""), 14);
            tagFields.put(role, tf);
            gbc.gridx = 1;
            gbc.weightx = 1.0;
            rightPanel.add(tf, gbc);

            JButton pickBtn = new JButton("...");
            pickBtn.addActionListener(e -> {
                OperandPickerDialog dlg = new OperandPickerDialog(this, project, tf.getText());
                dlg.setVisible(true);
                if (dlg.getSelectedTagName() != null) {
                    tf.setText(dlg.getSelectedTagName());
                }
            });
            gbc.gridx = 2;
            gbc.weightx = 0.0;
            rightPanel.add(pickBtn, gbc);
            row++;
        }

        minField.setText(symbol.getProperties().getOrDefault("Min", "0"));
        maxField.setText(symbol.getProperties().getOrDefault("Max", "100"));
        unitsField.setText(symbol.getProperties().getOrDefault("EngineeringUnits", ""));

        addFieldRow(rightPanel, gbc, row++, "Min:", minField);
        addFieldRow(rightPanel, gbc, row++, "Max:", maxField);
        addFieldRow(rightPanel, gbc, row, "EngineeringUnits:", unitsField);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, new JScrollPane(rightPanel));
        split.setDividerLocation(280);
        add(split, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(e -> {
            if (geoTable.isEditing()) {
                geoTable.getCellEditor().stopCellEditing();
            }
            applyChanges();
            confirmed = true;
            dispose();
        });
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        buttons.add(okBtn);
        buttons.add(cancelBtn);
        add(buttons, BorderLayout.SOUTH);
    }

    private static void addFieldRow(JPanel panel, GridBagConstraints gbc, int row, String label, JTextField field) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        panel.add(field, gbc);
        gbc.gridwidth = 1;
    }

    private void applyChanges() {
        symbol.setName(String.valueOf(geoModel.getValueAt(0, 1)));
        symbol.setGridX(parseIntSafe(geoModel.getValueAt(1, 1), symbol.getGridX()));
        symbol.setGridY(parseIntSafe(geoModel.getValueAt(2, 1), symbol.getGridY()));
        symbol.setGridW(parseIntSafe(geoModel.getValueAt(3, 1), symbol.getGridW()));
        symbol.setGridH(parseIntSafe(geoModel.getValueAt(4, 1), symbol.getGridH()));
        symbol.setRotationDeg(PipePort.normalizeRotation(parseIntSafe(geoModel.getValueAt(5, 1), symbol.getRotationDeg())));
        symbol.getProperties().put("FluidType", String.valueOf(geoModel.getValueAt(6, 1)).trim());
        symbol.setTooltip(String.valueOf(geoModel.getValueAt(7, 1)));

        for (Map.Entry<String, JTextField> e : tagFields.entrySet()) {
            String val = e.getValue().getText().trim();
            if (val.isEmpty()) {
                symbol.getTagBindings().remove(e.getKey());
            } else {
                symbol.getTagBindings().put(e.getKey(), val);
            }
        }
        symbol.getProperties().put("Min", minField.getText().trim());
        symbol.getProperties().put("Max", maxField.getText().trim());
        symbol.getProperties().put("EngineeringUnits", unitsField.getText().trim());
    }

    private static int parseIntSafe(Object raw, int fallback) {
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public boolean isConfirmed() {
        return confirmed;
    }
}
