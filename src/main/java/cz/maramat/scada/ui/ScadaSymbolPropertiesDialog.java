package cz.maramat.scada.ui;

import cz.maramat.scada.model.EquipmentDomain;
import cz.maramat.scada.model.FluidType;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.model.SymbolCatalog;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Domain-aware properties dialog for SCADA, Electrical, Heating/HVAC, and MaR elements.
 * Only displays properties and tag bindings relevant to the element's EquipmentDomain.
 */
public class ScadaSymbolPropertiesDialog extends JDialog {

    private final ScadaProject project;
    private final ScadaSymbolInstance symbol;
    private final EquipmentDomain domain;
    private final boolean resizable;

    private final JTextField nameField = new JTextField(16);
    private final JCheckBox showLabelCheck = new JCheckBox("Show Name Label");
    private final JComboBox<ScadaSymbolInstance.LabelPosition> labelPosCombo = new JComboBox<>(ScadaSymbolInstance.LabelPosition.values());
    private boolean resetLabelOffset = false;
    private final JTextField tooltipField = new JTextField(16);
    private final JSpinner xSpin;
    private final JSpinner ySpin;
    private final JSpinner wSpin;
    private final JSpinner hSpin;
    private final JComboBox<Integer> rotCombo = new JComboBox<>(new Integer[]{0, 90, 180, 270});

    // Domain-specific & simulation-default fields
    private JCheckBox manualStateCheck;
    private String manualStateKey;
    private JSpinner manualLevelOrPosSpin;
    private String manualLevelOrPosKey;
    private JComboBox<FluidType> fluidCombo;
    private JComboBox<String> buttonModeCombo;
    private JComboBox<String> voltageCombo;
    private JTextField ratedPowerField;
    private JTextField setpointField;
    private JTextField minField;
    private JTextField maxField;
    private JTextField unitsField;

    private final Map<String, JTextField> tagFields = new LinkedHashMap<>();
    private boolean confirmed = false;

    public ScadaSymbolPropertiesDialog(Window owner, ScadaProject project, ScadaSymbolInstance symbol) {
        super(owner, symbol.getName() + " (" + symbol.getSymbolType() + ") - Properties", ModalityType.APPLICATION_MODAL);
        this.project = project;
        this.symbol = symbol;
        this.domain = SymbolCatalog.getDomain(symbol.getSymbolType());
        this.resizable = SymbolCatalog.isResizable(symbol.getSymbolType());

        setSize(740, 440);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(6, 6));

        nameField.setText(symbol.getName());
        showLabelCheck.setSelected(symbol.isShowLabel());
        labelPosCombo.setSelectedItem(symbol.getLabelPosition());
        tooltipField.setText(symbol.getTooltip());
        xSpin = new JSpinner(new SpinnerNumberModel(symbol.getGridX(), 0, 500, 1));
        ySpin = new JSpinner(new SpinnerNumberModel(symbol.getGridY(), 0, 500, 1));
        int minW = SymbolCatalog.getMinGridW(symbol.getSymbolType());
        int minH = SymbolCatalog.getMinGridH(symbol.getSymbolType());
        wSpin = new JSpinner(new SpinnerNumberModel(Math.max(minW, symbol.getGridW()), minW, 100, 1));
        hSpin = new JSpinner(new SpinnerNumberModel(Math.max(minH, symbol.getGridH()), minH, 100, 1));
        wSpin.setEnabled(resizable);
        hSpin.setEnabled(resizable);
        rotCombo.setSelectedItem(symbol.getRotationDeg());

        // Left: General & Domain-Specific Parameters
        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Element Settings (" + domain.name() + ")"));
        GridBagConstraints lgbc = new GridBagConstraints();
        lgbc.insets = new Insets(4, 5, 4, 5);
        lgbc.fill = GridBagConstraints.HORIZONTAL;
        int lr = 0;

        addRow(leftPanel, lgbc, lr++, "Name:", nameField);
        JPanel labelCtrlRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        labelCtrlRow.add(showLabelCheck);
        labelCtrlRow.add(labelPosCombo);
        JButton resetOffBtn = new JButton("Reset");
        resetOffBtn.setToolTipText("Reset dragged label offset to 0,0");
        resetOffBtn.addActionListener(e -> resetLabelOffset = true);
        labelCtrlRow.add(resetOffBtn);
        addRow(leftPanel, lgbc, lr++, "Label Display:", labelCtrlRow);

        addRow(leftPanel, lgbc, lr++, "Grid X:", xSpin);
        addRow(leftPanel, lgbc, lr++, "Grid Y:", ySpin);
        addRow(leftPanel, lgbc, lr++, resizable ? "Width (grid):" : "Width (fixed 1:1):", wSpin);
        addRow(leftPanel, lgbc, lr++, resizable ? "Height (grid):" : "Height (fixed 1:1):", hSpin);
        addRow(leftPanel, lgbc, lr++, "Rotation (deg):", rotCombo);

        String st = symbol.getSymbolType();
        if (st.equals("HandValve") || st.equals("GateValve") || st.equals("GlobeValve")
                || st.equals("BallValve") || st.equals("ButterflyValve") || st.equals("NeedleValve")
                || st.equals("PlugValve") || st.equals("DiaphragmValve") || st.equals("SolenoidValve")
                || st.equals("ZoneValve") || st.equals("Damper")) {
            manualStateKey = "Open";
            manualStateCheck = new JCheckBox("Open (Manual / Initial State)",
                    Boolean.parseBoolean(symbol.getProperties().getOrDefault("Open", "true")));
            addRow(leftPanel, lgbc, lr++, "Valve State:", manualStateCheck);
        } else if (st.endsWith("Pump") || st.contains("Fan") || st.equals("AirBlower")
                || st.contains("Compressor") || st.contains("Boiler")
                || st.contains("HeatPump") || st.equals("Chiller")) {
            manualStateKey = "Running";
            manualStateCheck = new JCheckBox("Running (Manual / Initial State)",
                    Boolean.parseBoolean(symbol.getProperties().getOrDefault("Running", "false")));
            addRow(leftPanel, lgbc, lr++, "Equipment State:", manualStateCheck);
        } else if (st.equals("CircuitBreaker") || st.equals("Contactor") || st.equals("OverloadRelay")) {
            manualStateKey = "Closed";
            manualStateCheck = new JCheckBox("Closed (Energized)",
                    Boolean.parseBoolean(symbol.getProperties().getOrDefault("Closed", "false")));
            addRow(leftPanel, lgbc, lr++, "Switch State:", manualStateCheck);
        } else if (isTankLikeSymbol(st)) {
            manualLevelOrPosKey = "Level";
            double initLvl = parseDouble(symbol.getProperties().getOrDefault("Level", "65"), 65.0);
            manualLevelOrPosSpin = new JSpinner(new SpinnerNumberModel(initLvl, 0.0, 100.0, 5.0));
            addRow(leftPanel, lgbc, lr++, "Initial Level (%):", manualLevelOrPosSpin);
        } else if (st.equals("ControlValve") || st.equals("TwoWayControlValve") || st.equals("ThreeWayMixingValve")
                || st.equals("MotorizedValve") || st.equals("PneumaticValve") || st.equals("VavBox")) {
            manualLevelOrPosKey = "Position";
            double initPos = parseDouble(symbol.getProperties().getOrDefault("Position", "100"), 100.0);
            manualLevelOrPosSpin = new JSpinner(new SpinnerNumberModel(initPos, 0.0, 100.0, 5.0));
            addRow(leftPanel, lgbc, lr++, "Opening (%):", manualLevelOrPosSpin);
        }

        switch (domain) {
            case FLUID_EQUIPMENT, HEATING_HVAC, WASTEWATER -> {
                fluidCombo = new JComboBox<>(FluidType.values());
                fluidCombo.setSelectedItem(parseFluid(symbol.getProperties().getOrDefault("fluidType",
                        symbol.getProperties().getOrDefault("FluidType", FluidType.WATER.name()))));
                addRow(leftPanel, lgbc, lr++, "Fluid / Medium:", fluidCombo);
                if (domain == EquipmentDomain.HEATING_HVAC && !ScadaSymbolInstance.isDefaultHiddenLabelType(st)) {
                    setpointField = new JTextField(symbol.getProperties().getOrDefault("setpoint", "55.0"), 12);
                    addRow(leftPanel, lgbc, lr++, "Design Temp / SP:", setpointField);
                }
                if (SymbolCatalog.hasEngineeringScale(st)) {
                    minField = new JTextField(symbol.getProperties().getOrDefault("rangeMin",
                            symbol.getProperties().getOrDefault("Min", "0")), 12);
                    maxField = new JTextField(symbol.getProperties().getOrDefault("rangeMax",
                            symbol.getProperties().getOrDefault("Max", "100")), 12);
                    addRow(leftPanel, lgbc, lr++, "Min Level:", minField);
                    addRow(leftPanel, lgbc, lr++, "Max Level:", maxField);
                }
            }
            case ELECTRICAL -> {
                voltageCombo = new JComboBox<>(new String[]{"24V DC", "24V AC", "230V AC", "400V 3~ AC", "690V 3~ AC"});
                voltageCombo.setEditable(true);
                voltageCombo.setSelectedItem(symbol.getProperties().getOrDefault("voltage", "400V 3~ AC"));
                addRow(leftPanel, lgbc, lr++, "Rated Voltage:", voltageCombo);
                if (st.equals("ElectricMotor") || st.equals("VfdDrive") || st.equals("Transformer")) {
                    ratedPowerField = new JTextField(symbol.getProperties().getOrDefault("ratedPower", "4.0 kW"), 12);
                    addRow(leftPanel, lgbc, lr++, "Rated Power / Current:", ratedPowerField);
                }
            }
            case MAR_SENSOR -> {
                unitsField = new JTextField(symbol.getProperties().getOrDefault("unit",
                        symbol.getProperties().getOrDefault("EngineeringUnits", "°C")), 12);
                minField = new JTextField(symbol.getProperties().getOrDefault("rangeMin",
                        symbol.getProperties().getOrDefault("Min", "0")), 12);
                maxField = new JTextField(symbol.getProperties().getOrDefault("rangeMax",
                        symbol.getProperties().getOrDefault("Max", "120")), 12);
                addRow(leftPanel, lgbc, lr++, "Engineering Unit:", unitsField);
                addRow(leftPanel, lgbc, lr++, "Range Min:", minField);
                addRow(leftPanel, lgbc, lr++, "Range Max:", maxField);
            }
            case OPERATOR_CONTROL -> {
                if (st.equals("PushButton") || st.equals("EStopMushroom")) {
                    buttonModeCombo = new JComboBox<>(new String[]{"Toggle", "Momentary"});
                    String savedMode = symbol.getProperties().getOrDefault("ButtonMode", "Toggle");
                    buttonModeCombo.setSelectedItem("Momentary".equalsIgnoreCase(savedMode) ? "Momentary" : "Toggle");
                    addRow(leftPanel, lgbc, lr++, "Button Mode:", buttonModeCombo);
                }
                if (SymbolCatalog.hasEngineeringScale(st)) {
                    unitsField = new JTextField(symbol.getProperties().getOrDefault("EngineeringUnits", ""), 12);
                    minField = new JTextField(symbol.getProperties().getOrDefault("Min", "0"), 12);
                    maxField = new JTextField(symbol.getProperties().getOrDefault("Max", "100"), 12);
                    addRow(leftPanel, lgbc, lr++, "Units:", unitsField);
                    addRow(leftPanel, lgbc, lr++, "Scale Min:", minField);
                    addRow(leftPanel, lgbc, lr++, "Scale Max:", maxField);
                }
            }
        }
        addRow(leftPanel, lgbc, lr, "Tooltip:", tooltipField);

        // Right: Domain-Relevant Tag Bindings
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Tag Bindings"));
        GridBagConstraints rgbc = new GridBagConstraints();
        rgbc.insets = new Insets(4, 4, 4, 4);
        rgbc.fill = GridBagConstraints.HORIZONTAL;
        int rr = 0;

        java.util.List<String> roles = SymbolCatalog.getRelevantTagRoles(symbol.getSymbolType());
        if (roles.isEmpty()) {
            setSize(450, 420);
            add(new JScrollPane(leftPanel), BorderLayout.CENTER);
        } else {
            for (String role : roles) {
                rgbc.gridy = rr;
                rgbc.gridx = 0;
                rgbc.weightx = 0.0;
                rightPanel.add(new JLabel(role + ":"), rgbc);

                JTextField tf = new JTextField(symbol.getTagBindings().getOrDefault(role, ""), 14);
                tagFields.put(role, tf);
                rgbc.gridx = 1;
                rgbc.weightx = 1.0;
                rightPanel.add(tf, rgbc);

                JButton pickBtn = new JButton("...");
                pickBtn.addActionListener(e -> {
                    OperandPickerDialog dlg = new OperandPickerDialog(this, project, tf.getText());
                    dlg.setVisible(true);
                    if (dlg.getSelectedTagName() != null) {
                        tf.setText(dlg.getSelectedTagName());
                    }
                });
                rgbc.gridx = 2;
                rgbc.weightx = 0.0;
                rightPanel.add(pickBtn, rgbc);
                rr++;
            }

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    new JScrollPane(leftPanel), new JScrollPane(rightPanel));
            split.setDividerLocation(370);
            add(split, BorderLayout.CENTER);
        }

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");
        okBtn.addActionListener(e -> applyAndClose());
        cancelBtn.addActionListener(e -> dispose());
        btnBar.add(okBtn);
        btnBar.add(cancelBtn);
        add(btnBar, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(okBtn);
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, java.awt.Component comp) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        panel.add(comp, gbc);
    }

    private static double parseDouble(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void applyAndClose() {
        symbol.setName(nameField.getText().trim());
        symbol.setShowLabel(showLabelCheck.isSelected());
        if (labelPosCombo.getSelectedItem() instanceof ScadaSymbolInstance.LabelPosition lp) {
            symbol.setLabelPosition(lp);
        }
        if (resetLabelOffset) {
            symbol.setLabelOffsetX(0);
            symbol.setLabelOffsetY(0);
        }
        if (manualStateCheck != null && manualStateKey != null) {
            symbol.setProperty(manualStateKey, String.valueOf(manualStateCheck.isSelected()));
        }
        if (manualLevelOrPosSpin != null && manualLevelOrPosKey != null) {
            symbol.setProperty(manualLevelOrPosKey, String.valueOf(manualLevelOrPosSpin.getValue()));
        }
        symbol.setGridX((Integer) xSpin.getValue());
        symbol.setGridY((Integer) ySpin.getValue());
        if (resizable) {
            int nw = (Integer) wSpin.getValue();
            int nh = (Integer) hSpin.getValue();
            if (nw != symbol.getGridW() || nh != symbol.getGridH()) {
                symbol.setGridW(nw);
                symbol.setGridH(nh);
                SymbolCatalog.rebuildPortsForSize(symbol);
            }
        }
        Integer rot = (Integer) rotCombo.getSelectedItem();
        if (rot != null) symbol.setRotationDeg(rot);
        symbol.setTooltip(tooltipField.getText());

        if (fluidCombo != null && fluidCombo.getSelectedItem() instanceof FluidType ft) {
            symbol.setProperty("fluidType", ft.name());
            symbol.setProperty("FluidType", ft.name());
        }
        if (buttonModeCombo != null && buttonModeCombo.getSelectedItem() != null) {
            symbol.setProperty("ButtonMode", String.valueOf(buttonModeCombo.getSelectedItem()));
        }
        if (voltageCombo != null && voltageCombo.getSelectedItem() != null) {
            symbol.setProperty("voltage", String.valueOf(voltageCombo.getSelectedItem()).trim());
        }
        if (ratedPowerField != null) {
            symbol.setProperty("ratedPower", ratedPowerField.getText().trim());
        }
        if (setpointField != null) {
            symbol.setProperty("setpoint", setpointField.getText().trim());
        }
        if (minField != null) {
            symbol.setProperty("rangeMin", minField.getText().trim());
            symbol.setProperty("Min", minField.getText().trim());
        }
        if (maxField != null) {
            symbol.setProperty("rangeMax", maxField.getText().trim());
            symbol.setProperty("Max", maxField.getText().trim());
        }
        if (unitsField != null) {
            symbol.setProperty("unit", unitsField.getText().trim());
            symbol.setProperty("EngineeringUnits", unitsField.getText().trim());
        }

        for (Map.Entry<String, JTextField> e : tagFields.entrySet()) {
            String val = e.getValue().getText().trim();
            if (val.isEmpty()) {
                symbol.getTagBindings().remove(e.getKey());
            } else {
                symbol.getTagBindings().put(e.getKey(), val);
            }
        }
        confirmed = true;
        dispose();
    }

    private static boolean isTankLikeSymbol(String type) {
        return type.contains("Tank") || type.equals("HopperSilo") || type.equals("OpenBasin")
                || type.equals("PrimaryClarifier") || type.equals("AerationBasin")
                || type.equals("AnaerobicDigester") || type.equals("SludgeThickener");
    }

    private static FluidType parseFluid(String raw) {
        if (raw == null || raw.isBlank()) return FluidType.WATER;
        try {
            return FluidType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return FluidType.WATER;
        }
    }

    public boolean isConfirmed() {
        return confirmed;
    }
}
