package cz.maramat.scada.ui;

import cz.maramat.scada.model.CardinalDirection;
import cz.maramat.scada.model.PipePort;
import cz.maramat.scada.model.ScadaSymbolInstance;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.Font;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.List;

/**
 * Left dock Symbol Library JTree styled identically to MaraFlow's LibraryTreePanel,
 * supporting drag-and-drop of leaf symbols onto ScadaCanvas.
 */
public final class SymbolCatalogTree {

    private SymbolCatalogTree() {
    }

    public static JTree createTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Symbol Library");

        DefaultMutableTreeNode piping = new DefaultMutableTreeNode("Piping & Valves");
        for (String s : List.of("HandValve", "SolenoidValve", "ControlValve", "CheckValve", "Damper")) {
            piping.add(new DefaultMutableTreeNode(s));
        }

        DefaultMutableTreeNode pumps = new DefaultMutableTreeNode("Pumps, Motors & Fans");
        for (String s : List.of("CentrifugalPump", "InlinePump", "MeteringPump", "ElectricMotor", "BlowerFan")) {
            pumps.add(new DefaultMutableTreeNode(s));
        }

        DefaultMutableTreeNode tanks = new DefaultMutableTreeNode("Tanks, Vessels & Reactors");
        for (String s : List.of("CylindricalTank", "HorizontalTank", "HopperSilo", "OpenBasin", "TankAgitator")) {
            tanks.add(new DefaultMutableTreeNode(s));
        }

        DefaultMutableTreeNode machines = new DefaultMutableTreeNode("Material Handling & Machines");
        for (String s : List.of("ConveyorBelt", "ScrewAuger", "PneumaticCylinder", "HydraulicPress", "HeaterCoil")) {
            machines.add(new DefaultMutableTreeNode(s));
        }

        DefaultMutableTreeNode sensors = new DefaultMutableTreeNode("Sensors & Instrumentation");
        for (String s : List.of("PressureSensor", "TempSensor", "FlowMeter", "LevelSwitch", "ProximitySensor")) {
            sensors.add(new DefaultMutableTreeNode(s));
        }

        DefaultMutableTreeNode controls = new DefaultMutableTreeNode("Operator Controls & Readouts");
        for (String s : List.of("PushButton", "SelectorSwitch", "EStopMushroom", "PilotLamp",
                "NumericReadout", "NumericSetpoint", "BarGraph", "TrendChart")) {
            controls.add(new DefaultMutableTreeNode(s));
        }

        root.add(piping);
        root.add(pumps);
        root.add(tanks);
        root.add(machines);
        root.add(sensors);
        root.add(controls);

        JTree tree = new JTree(new DefaultTreeModel(root));
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(18);
        tree.putClientProperty("JTree.lineStyle", "Angled");
        tree.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        if (tree.getCellRenderer() instanceof DefaultTreeCellRenderer renderer) {
            renderer.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            renderer.setOpenIcon(null);
            renderer.setClosedIcon(null);
            renderer.setLeafIcon(null);
        }

        // MaraFlow-style drag-and-drop for leaf nodes
        tree.setDragEnabled(true);
        tree.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                TreePath path = tree.getSelectionPath();
                if (path == null) return null;
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (!node.isLeaf()) return null;
                return new StringSelection(String.valueOf(node.getUserObject()));
            }
        });

        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        return tree;
    }

    public static ScadaSymbolInstance createDefaultInstance(String symbolType, String name, int gridX, int gridY) {
        if ("CentrifugalPump".equals(symbolType)) {
            ScadaSymbolInstance s = new ScadaSymbolInstance(symbolType, name, gridX, gridY, 8, 10);
            s.getPorts().add(new PipePort("suction", "Suction", 0, 5, CardinalDirection.WEST));
            s.getPorts().add(new PipePort("discharge", "Discharge", 3, 0, CardinalDirection.NORTH));
            return s;
        }
        if (symbolType.endsWith("Valve") || "Damper".equals(symbolType)) {
            ScadaSymbolInstance s = new ScadaSymbolInstance(symbolType, name, gridX, gridY, 5, 5);
            s.getPorts().add(new PipePort("inlet", "Inlet", 0, 3, CardinalDirection.WEST));
            s.getPorts().add(new PipePort("outlet", "Outlet", 5, 3, CardinalDirection.EAST));
            return s;
        }
        if (symbolType.contains("Tank") || "HopperSilo".equals(symbolType) || "OpenBasin".equals(symbolType)) {
            ScadaSymbolInstance s = new ScadaSymbolInstance(symbolType, name, gridX, gridY, 10, 14);
            s.getPorts().add(new PipePort("inlet", "Top Inlet", 5, 0, CardinalDirection.NORTH));
            s.getPorts().add(new PipePort("outlet", "Bottom Outlet", 0, 12, CardinalDirection.WEST));
            s.getPorts().add(new PipePort("drain", "East Outlet", 10, 12, CardinalDirection.EAST));
            return s;
        }
        if ("FlowMeter".equals(symbolType) || "PressureSensor".equals(symbolType) || "TempSensor".equals(symbolType)) {
            ScadaSymbolInstance s = new ScadaSymbolInstance(symbolType, name, gridX, gridY, 4, 4);
            s.getPorts().add(new PipePort("inlet", "Inlet", 0, 2, CardinalDirection.WEST));
            s.getPorts().add(new PipePort("outlet", "Outlet", 4, 2, CardinalDirection.EAST));
            return s;
        }
        ScadaSymbolInstance s = new ScadaSymbolInstance(symbolType, name, gridX, gridY, 6, 6);
        s.getPorts().add(new PipePort("inlet", "Inlet", 0, 3, CardinalDirection.WEST));
        s.getPorts().add(new PipePort("outlet", "Outlet", 6, 3, CardinalDirection.EAST));
        return s;
    }
}
