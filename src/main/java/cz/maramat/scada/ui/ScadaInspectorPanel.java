package cz.maramat.scada.ui;

import cz.maramat.scada.model.ConnectionType;
import cz.maramat.scada.model.FluidType;
import cz.maramat.scada.model.PipeDiameter;
import cz.maramat.scada.model.ScadaPipe;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaScreen;
import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.model.SymbolCatalog;
import cz.maramat.scada.render.ScadaPalette;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.function.Supplier;

/**
 * Live right-docked Properties Inspector for the currently selected SCADA symbol or pipe.
 */
public class ScadaInspectorPanel extends JPanel {

    private final Supplier<ScadaCanvas> canvasSupplier;
    private final JPanel body = new JPanel();
    private boolean updating = false;

    public ScadaInspectorPanel(Supplier<ScadaCanvas> canvasSupplier) {
        this.canvasSupplier = canvasSupplier;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(260, 400));
        setMinimumSize(new Dimension(210, 200));

        JLabel title = new JLabel("Inspector");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        title.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        add(title, BorderLayout.NORTH);

        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        refresh();
    }

    public void refresh() {
        updating = true;
        try {
            body.removeAll();
            ScadaCanvas canvas = canvasSupplier != null ? canvasSupplier.get() : null;
            if (canvas == null) {
                JLabel empty = new JLabel("No active screen");
                empty.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(empty);
                revalidate();
                repaint();
                return;
            }
            ScadaSymbolInstance sym = canvas.getSingleSelectedSymbol();
            ScadaPipe pipe = canvas.getSingleSelectedPipe();

            if (sym != null) {
                buildSymbolInspector(canvas, sym);
            } else if (pipe != null) {
                buildPipeInspector(canvas, pipe);
            } else {
                int symCount = canvas.getSelectedSymbolIds().size();
                int pipeCount = canvas.getSelectedPipeIds().size();
                String msg = (symCount + pipeCount > 1)
                        ? (symCount + pipeCount) + " items selected"
                        : "Select a symbol or pipe to inspect";
                JLabel empty = new JLabel(msg);
                empty.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(empty);
            }
            body.add(Box.createVerticalGlue());
            revalidate();
            repaint();
        } finally {
            updating = false;
        }
    }

    private void buildSymbolInspector(ScadaCanvas canvas, ScadaSymbolInstance sym) {
        JPanel form = createFormPanel(sym.getSymbolType());
        GridBagConstraints gc = baseGc();
        int row = 0;

        JTextField nameField = new JTextField(sym.getName(), 12);
        nameField.addActionListener(e -> {
            if (updating) return;
            canvas.recordUndoSnapshot("Rename Element");
            sym.setName(nameField.getText().trim());
            canvas.refreshAfterExternalEdit();
        });
        nameField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (updating) return;
                String v = nameField.getText().trim();
                if (!v.equals(sym.getName())) {
                    canvas.recordUndoSnapshot("Rename Element");
                    sym.setName(v);
                    canvas.refreshAfterExternalEdit();
                }
            }
        });
        addRow(form, gc, row++, "Name:", nameField);

        JCheckBox showLabel = new JCheckBox("Show Name Label", sym.isShowLabel());
        showLabel.addActionListener(e -> {
            if (updating) return;
            canvas.recordUndoSnapshot("Toggle Label");
            sym.setShowLabel(showLabel.isSelected());
            canvas.refreshAfterExternalEdit();
        });
        addFullRow(form, gc, row++, showLabel);

        JComboBox<ScadaSymbolInstance.LabelPosition> posCombo = new JComboBox<>(ScadaSymbolInstance.LabelPosition.values());
        posCombo.setSelectedItem(sym.getLabelPosition());
        posCombo.addActionListener(e -> {
            if (updating) return;
            if (posCombo.getSelectedItem() instanceof ScadaSymbolInstance.LabelPosition lp) {
                canvas.recordUndoSnapshot("Label Position");
                sym.setLabelPosition(lp);
                sym.setLabelOffsetX(0);
                sym.setLabelOffsetY(0);
                canvas.refreshAfterExternalEdit();
            }
        });
        addRow(form, gc, row++, "Label Pos:", posCombo);

        JButton resetOffBtn = new JButton("Reset Label Offset");
        resetOffBtn.addActionListener(e -> {
            canvas.recordUndoSnapshot("Reset Label Offset");
            sym.setLabelOffsetX(0);
            sym.setLabelOffsetY(0);
            canvas.refreshAfterExternalEdit();
        });
        addFullRow(form, gc, row++, resetOffBtn);

        String st = sym.getSymbolType();
        if (st.equals("HandValve") || st.equals("BallValve") || st.equals("GateValve")
                || st.equals("GlobeValve") || st.equals("ButterflyValve") || st.equals("NeedleValve")
                || st.equals("PlugValve") || st.equals("DiaphragmValve") || st.equals("SolenoidValve")
                || st.equals("ZoneValve") || st.equals("Damper")) {
            JCheckBox openChk = new JCheckBox("Open", Boolean.parseBoolean(sym.getProperties().getOrDefault("Open", "true")));
            openChk.addActionListener(e -> {
                if (updating) return;
                canvas.recordUndoSnapshot("Toggle Valve");
                sym.setProperty("Open", String.valueOf(openChk.isSelected()));
                canvas.refreshAfterExternalEdit();
            });
            addRow(form, gc, row++, "State:", openChk);
        } else if (st.endsWith("Pump") || st.contains("Fan") || st.equals("AirBlower")
                || st.contains("Compressor") || st.contains("Boiler") || st.contains("HeatPump") || st.equals("Chiller")) {
            JCheckBox runChk = new JCheckBox("Running", Boolean.parseBoolean(sym.getProperties().getOrDefault("Running", "false")));
            runChk.addActionListener(e -> {
                if (updating) return;
                canvas.recordUndoSnapshot("Toggle Equipment");
                sym.setProperty("Running", String.valueOf(runChk.isSelected()));
                canvas.refreshAfterExternalEdit();
            });
            addRow(form, gc, row++, "State:", runChk);
        } else if (st.equals("CircuitBreaker") || st.equals("Contactor") || st.equals("OverloadRelay")) {
            JCheckBox closedChk = new JCheckBox("Closed", Boolean.parseBoolean(sym.getProperties().getOrDefault("Closed", "false")));
            closedChk.addActionListener(e -> {
                if (updating) return;
                canvas.recordUndoSnapshot("Toggle Switch");
                sym.setProperty("Closed", String.valueOf(closedChk.isSelected()));
                canvas.refreshAfterExternalEdit();
            });
            addRow(form, gc, row++, "State:", closedChk);
        } else if (st.contains("Tank") || st.equals("HopperSilo") || st.equals("OpenBasin")) {
            double lvl = 65.0;
            try {
                lvl = Double.parseDouble(sym.getProperties().getOrDefault("Level", "65"));
            } catch (NumberFormatException ignored) {
            }
            JSpinner lvlSpin = new JSpinner(new SpinnerNumberModel(Math.max(0.0, Math.min(100.0, lvl)), 0.0, 100.0, 5.0));
            lvlSpin.addChangeListener(e -> {
                if (updating) return;
                sym.setProperty("Level", String.valueOf(lvlSpin.getValue()));
                canvas.refreshAfterExternalEdit();
            });
            addRow(form, gc, row++, "Level (%):", lvlSpin);
        } else if (st.equals("PushButton") || st.equals("PilotLamp")) {
            Color defaultCol = st.equals("PushButton")
                    ? new Color(0x2E, 0xCC, 0x71)
                    : ScadaPalette.STATE_RUNNING_LED;
            Color currentCol = ScadaScreen.parseHexColor(sym.getProperties().get("Color"), defaultCol);
            JButton colorBtn = new JButton("Choose Color...");
            colorBtn.setBackground(currentCol);
            colorBtn.addActionListener(e -> {
                if (updating) return;
                Color cur = ScadaScreen.parseHexColor(sym.getProperties().get("Color"), defaultCol);
                Color picked = JColorChooser.showDialog(
                        SwingUtilities.getWindowAncestor(this), st + " Color", cur);
                if (picked != null) {
                    canvas.recordUndoSnapshot("Change Color");
                    sym.setProperty("Color", String.format("#%06X", picked.getRGB() & 0xFFFFFF));
                    colorBtn.setBackground(picked);
                    canvas.refreshAfterExternalEdit();
                }
            });
            addRow(form, gc, row++, "Color:", colorBtn);
        }

        body.add(form);

        List<String> roles = SymbolCatalog.getRelevantTagRoles(st);
        if (!roles.isEmpty()) {
            JPanel tagPanel = createFormPanel("Tag Bindings");
            GridBagConstraints tgc = baseGc();
            int tr = 0;
            ScadaProject project = canvas.getProject();
            for (String role : roles) {
                JTextField tf = new JTextField(sym.getTagBindings().getOrDefault(role, ""), 10);
                tf.addActionListener(e -> {
                if (updating) return;
                applyTagBinding(canvas, sym, role, tf.getText().trim());
            });
            tf.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    if (updating) return;
                    applyTagBinding(canvas, sym, role, tf.getText().trim());
                }
            });

            JPanel rowP = new JPanel(new BorderLayout(2, 0));
            rowP.add(tf, BorderLayout.CENTER);
            JButton pickBtn = new JButton("...");
            pickBtn.setMargin(new Insets(1, 5, 1, 5));
            pickBtn.addActionListener(e -> {
                OperandPickerDialog dlg = new OperandPickerDialog(
                        SwingUtilities.getWindowAncestor(this), project, tf.getText());
                dlg.setVisible(true);
                if (dlg.getSelectedTagName() != null) {
                    tf.setText(dlg.getSelectedTagName());
                    applyTagBinding(canvas, sym, role, dlg.getSelectedTagName());
                }
            });
                rowP.add(pickBtn, BorderLayout.EAST);
                addRow(tagPanel, tgc, tr++, role + ":", rowP);
            }
            body.add(Box.createVerticalStrut(6));
            body.add(tagPanel);
        }

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 6));
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton fullPropsBtn = new JButton("All Properties...");
        fullPropsBtn.addActionListener(e -> {
            ScadaSymbolPropertiesDialog dlg = new ScadaSymbolPropertiesDialog(
                    SwingUtilities.getWindowAncestor(this), canvas.getProject(), sym);
            dlg.setVisible(true);
            if (dlg.isConfirmed()) {
                canvas.refreshAfterExternalEdit();
                refresh();
            }
        });
        btnRow.add(fullPropsBtn);
        body.add(btnRow);
    }

    private void buildPipeInspector(ScadaCanvas canvas, ScadaPipe pipe) {
        JPanel form = createFormPanel("Pipe / Line");
        GridBagConstraints gc = baseGc();
        int row = 0;

        JComboBox<ConnectionType> connCombo = new JComboBox<>(ConnectionType.values());
        connCombo.setSelectedItem(pipe.getConnectionType());
        connCombo.addActionListener(e -> {
            if (updating) return;
            canvas.recordUndoSnapshot("Change Connection Type");
            pipe.setConnectionType((ConnectionType) connCombo.getSelectedItem());
            canvas.refreshAfterExternalEdit();
        });
        addRow(form, gc, row++, "Type:", connCombo);

        JComboBox<PipeDiameter> diamCombo = new JComboBox<>(PipeDiameter.values());
        diamCombo.setSelectedItem(pipe.getDiameter());
        diamCombo.addActionListener(e -> {
            if (updating) return;
            canvas.recordUndoSnapshot("Change Pipe Diameter");
            pipe.setDiameter((PipeDiameter) diamCombo.getSelectedItem());
            canvas.refreshAfterExternalEdit();
        });
        addRow(form, gc, row++, "Diameter:", diamCombo);

        JComboBox<FluidType> fluidCombo = new JComboBox<>(FluidType.values());
        fluidCombo.setSelectedItem(pipe.getFluidType());
        fluidCombo.addActionListener(e -> {
            if (updating) return;
            canvas.recordUndoSnapshot("Change Fluid Type");
            pipe.setFluidType((FluidType) fluidCombo.getSelectedItem());
            canvas.refreshAfterExternalEdit();
        });
        addRow(form, gc, row++, "Fluid:", fluidCombo);

        JComboBox<ScadaPipe.FlowMode> flowCombo = new JComboBox<>(ScadaPipe.FlowMode.values());
        flowCombo.setSelectedItem(pipe.getFlowMode());
        flowCombo.addActionListener(e -> {
            if (updating) return;
            canvas.recordUndoSnapshot("Change Flow Behavior");
            pipe.setFlowMode((ScadaPipe.FlowMode) flowCombo.getSelectedItem());
            canvas.refreshAfterExternalEdit();
        });
        addRow(form, gc, row++, "Flow:", flowCombo);

        JTextField labelField = new JTextField(pipe.getLabel() != null ? pipe.getLabel() : "", 12);
        labelField.addActionListener(e -> {
            if (updating) return;
            canvas.recordUndoSnapshot("Edit Pipe Label");
            pipe.setLabel(labelField.getText());
            canvas.refreshAfterExternalEdit();
        });
        addRow(form, gc, row++, "Label:", labelField);

        JCheckBox showLblChk = new JCheckBox("Show Pipe Label", pipe.isShowLabel());
        showLblChk.addActionListener(e -> {
            if (updating) return;
            pipe.setShowLabel(showLblChk.isSelected());
            canvas.refreshAfterExternalEdit();
        });
        addFullRow(form, gc, row++, showLblChk);

        JCheckBox flangeChk = new JCheckBox("Show End Flanges", pipe.isShowFlanges());
        flangeChk.addActionListener(e -> {
            if (updating) return;
            pipe.setShowFlanges(flangeChk.isSelected());
            canvas.refreshAfterExternalEdit();
        });
        addFullRow(form, gc, row++, flangeChk);

        body.add(form);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 6));
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton fullPropsBtn = new JButton("More / Color...");
        fullPropsBtn.addActionListener(e -> {
            PipePropertiesDialog dlg = new PipePropertiesDialog(
                    SwingUtilities.getWindowAncestor(this), canvas.getProject(), pipe);
            dlg.setVisible(true);
            if (dlg.isConfirmed()) {
                canvas.refreshAfterExternalEdit();
                refresh();
            }
        });
        btnRow.add(fullPropsBtn);
        body.add(btnRow);
    }

    private void applyTagBinding(ScadaCanvas canvas, ScadaSymbolInstance sym, String role, String tag) {
        String existing = sym.getTagBindings().getOrDefault(role, "");
        if (existing.equals(tag)) return;
        canvas.recordUndoSnapshot("Edit Tag Binding");
        if (tag.isEmpty()) {
            sym.getTagBindings().remove(role);
        } else {
            sym.getTagBindings().put(role, tag);
        }
        canvas.refreshAfterExternalEdit();
    }

    private static JPanel createFormPanel(String title) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createTitledBorder(title));
        return p;
    }

    private static GridBagConstraints baseGc() {
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(3, 4, 3, 4);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;
        return gc;
    }

    private static void addRow(JPanel panel, GridBagConstraints gc, int row, String label, Component comp) {
        gc.gridy = row;
        gc.gridx = 0;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        panel.add(new JLabel(label), gc);
        gc.gridx = 1;
        gc.weightx = 1.0;
        panel.add(comp, gc);
    }

    private static void addFullRow(JPanel panel, GridBagConstraints gc, int row, Component comp) {
        gc.gridy = row;
        gc.gridx = 0;
        gc.gridwidth = 2;
        gc.weightx = 1.0;
        panel.add(comp, gc);
    }
}
