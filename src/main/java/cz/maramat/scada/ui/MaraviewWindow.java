package cz.maramat.scada.ui;

import cz.maramat.scada.alarms.AlarmEngine;
import cz.maramat.scada.io.MslProjectSerializer;
import cz.maramat.scada.model.DataType;
import cz.maramat.scada.model.FluidType;
import cz.maramat.scada.model.PipeDiameter;
import cz.maramat.scada.model.ScadaPipe;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaScreen;
import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.routing.PipeRouter;
import cz.maramat.scada.tags.InternalTagProvider;
import cz.maramat.scada.tags.MflTagImporter;
import cz.maramat.scada.tags.ScadaTag;
import cz.maramat.scada.tags.TagSourceType;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;

/**
 * Main Maraview SCADA Designer & Operator Runtime window.
 */
public class MaraviewWindow extends JFrame {

    private ScadaProject project;
    private final InternalTagProvider tagProvider = new InternalTagProvider();
    private final AlarmEngine alarmEngine = new AlarmEngine();
    private final AlarmBannerPanel alarmBanner = new AlarmBannerPanel(alarmEngine);
    private final JTabbedPane screenTabs = new JTabbedPane();
    private final JTabbedPane leftDockTabs = new JTabbedPane();
    private final DefaultTableModel tagTableModel;
    private final JTable tagTable;
    private final JLabel zoomLabel = new JLabel(" 100% ");
    private final JLabel statusLabel = new JLabel(" Ready");
    private final JToggleButton simPreviewBtn;
    private final JToggleButton operatorModeBtn;
    private final JPanel topBarHost = new JPanel(new BorderLayout());
    private final JPanel operatorBar = new JPanel(new BorderLayout());
    private final JPanel operatorScreenButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    private final JLabel connStatusLabel = new JLabel(" ONLINE ");
    private final Timer runtimeSyncTimer;
    private JSplitPane mainSplit;
    private JToolBar quickToolbar;
    private boolean operatorMode = false;

    public MaraviewWindow(ScadaProject initialProject, boolean startInOperatorMode) {
        this.project = (initialProject == null) ? createSampleProject() : initialProject;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1280, 800);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disposeAllCanvases();
                dispose();
            }
        });

        this.simPreviewBtn = new JToggleButton("Sim Preview", ScadaVectorIcons.playSim(), true);
        this.operatorModeBtn = new JToggleButton("Operator Mode", ScadaVectorIcons.operatorMode(), false);

        this.tagTableModel = new DefaultTableModel(new Object[]{"Tag", "Type", "Source", "Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.tagTable = new JTable(tagTableModel);
        this.tagTable.setRowHeight(20);
        this.tagTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = tagTable.getSelectedRow();
                    if (row >= 0) {
                        String tagName = String.valueOf(tagTableModel.getValueAt(row, 0));
                        ScadaTag tag = project.getTag(tagName);
                        if (tag != null && tag.getDataType() == DataType.BOOL) {
                            tagProvider.writeTag(project, tagName, tag.asBoolean() ? 0.0 : 1.0);
                            refreshTagTable();
                            repaintActiveCanvas();
                        }
                    }
                }
            }
        });

        setJMenuBar(buildMenuBar());
        setLayout(new BorderLayout());
        this.quickToolbar = buildToolbar();
        buildOperatorBar();
        topBarHost.add(quickToolbar, BorderLayout.CENTER);
        add(topBarHost, BorderLayout.NORTH);

        JTree symbolTree = SymbolCatalogTree.createTree();
        symbolTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && !operatorMode) {
                    Object sel = symbolTree.getLastSelectedPathComponent();
                    if (sel instanceof DefaultMutableTreeNode node && node.isLeaf()) {
                        placeSymbolFromLibrary(String.valueOf(node.getUserObject()));
                    }
                }
            }
        });

        leftDockTabs.addTab("Symbol Library", new JScrollPane(symbolTree));
        leftDockTabs.addTab("Tag Browser", new JScrollPane(tagTable));
        leftDockTabs.setPreferredSize(new Dimension(250, 600));

        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftDockTabs, screenTabs);
        mainSplit.setDividerSize(5);
        mainSplit.setDividerLocation(250);
        add(mainSplit, BorderLayout.CENTER);

        JPanel bottomStack = new JPanel(new BorderLayout());
        bottomStack.add(alarmBanner, BorderLayout.NORTH);
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xB8, 0xBE, 0xC6)));
        statusBar.add(statusLabel, BorderLayout.WEST);
        bottomStack.add(statusBar, BorderLayout.SOUTH);
        add(bottomStack, BorderLayout.SOUTH);

        this.runtimeSyncTimer = new Timer(250, e -> {
            tagProvider.syncAll(project);
            alarmEngine.evaluate(project, System.currentTimeMillis());
            alarmBanner.refresh(System.currentTimeMillis());
            updateConnectionBadge();
        });
        this.runtimeSyncTimer.start();

        loadProjectIntoUi(this.project);
        if (startInOperatorMode) {
            setOperatorMode(true);
        }
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenuItem newItem = new JMenuItem("New Project");
        newItem.addActionListener(e -> loadProjectIntoUi(new ScadaProject()));
        JMenuItem openItem = new JMenuItem("Open .msl...");
        openItem.addActionListener(e -> onOpenMsl());
        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.addActionListener(e -> onSaveMsl(false));
        JMenuItem saveAsItem = new JMenuItem("Save As...");
        saveAsItem.addActionListener(e -> onSaveMsl(true));
        JMenuItem linkMflItem = new JMenuItem("Link .mfl Project...");
        linkMflItem.addActionListener(e -> onLinkMfl());
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> {
            disposeAllCanvases();
            dispose();
        });
        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.addSeparator();
        fileMenu.add(linkMflItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        JMenu editMenu = new JMenu("Edit");
        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null && c.getUndoManager().undo(c.getScreen())) {
                c.repaint();
            }
        });
        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null && c.getUndoManager().redo(c.getScreen())) {
                c.repaint();
            }
        });
        JMenuItem rotItem = new JMenuItem("Rotate 90 deg");
        rotItem.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null) c.rotateSelected90CW();
        });
        JMenuItem delItem = new JMenuItem("Delete Selection");
        delItem.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null) c.deleteSelection();
        });
        editMenu.add(undoItem);
        editMenu.add(redoItem);
        editMenu.addSeparator();
        editMenu.add(rotItem);
        editMenu.add(delItem);

        JMenu viewMenu = new JMenu("View");
        JMenuItem zoomInItem = new JMenuItem("Zoom In");
        zoomInItem.addActionListener(e -> onZoomIn());
        JMenuItem zoomOutItem = new JMenuItem("Zoom Out");
        zoomOutItem.addActionListener(e -> onZoomOut());
        JMenuItem zoom100Item = new JMenuItem("Zoom 100%");
        zoom100Item.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null) {
                c.setZoomPercent(100);
                zoomLabel.setText(" 100% ");
            }
        });
        viewMenu.add(zoomInItem);
        viewMenu.add(zoomOutItem);
        viewMenu.add(zoom100Item);

        JMenu screenMenu = new JMenu("Screen");
        JMenuItem addScreenItem = new JMenuItem("Add Screen...");
        addScreenItem.addActionListener(e -> onAddScreen());
        screenMenu.add(addScreenItem);

        JMenu tagsMenu = new JMenu("Tags");
        JMenuItem importTagsItem = new JMenuItem("Import from Linked .mfl...");
        importTagsItem.addActionListener(e -> onLinkMfl());
        tagsMenu.add(importTagsItem);

        JMenu windowMenu = new JMenu("Window");
        JMenuItem opItem = new JMenuItem("Toggle Operator Mode");
        opItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        opItem.addActionListener(e -> setOperatorMode(!operatorMode));
        windowMenu.add(opItem);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About Maraview");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Maraview SCADA Designer & Operator Runtime V" + ScadaProject.VERSION,
                "About Maraview", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(aboutItem);

        bar.add(fileMenu);
        bar.add(editMenu);
        bar.add(viewMenu);
        bar.add(screenMenu);
        bar.add(tagsMenu);
        bar.add(windowMenu);
        bar.add(helpMenu);
        return bar;
    }

    private JToolBar buildToolbar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);

        JToggleButton cursorBtn = new JToggleButton("Cursor", ScadaVectorIcons.cursor(), true);
        JToggleButton pipeBtn = new JToggleButton("Pipe", ScadaVectorIcons.pipe(), false);
        ButtonGroup toolGroup = new ButtonGroup();
        toolGroup.add(cursorBtn);
        toolGroup.add(pipeBtn);
        cursorBtn.addActionListener(e -> setActiveToolMode(ScadaCanvas.ToolMode.CURSOR));
        pipeBtn.addActionListener(e -> setActiveToolMode(ScadaCanvas.ToolMode.PIPE));

        JButton rotBtn = new JButton(ScadaVectorIcons.rotate90());
        rotBtn.setToolTipText("Rotate 90 deg (R)");
        rotBtn.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null) c.rotateSelected90CW();
        });

        JButton flipHBtn = new JButton(ScadaVectorIcons.flipH());
        flipHBtn.setToolTipText("Flip Horizontal");
        flipHBtn.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null) c.flipSelectedHorizontal();
        });

        JButton flipVBtn = new JButton(ScadaVectorIcons.flipV());
        flipVBtn.setToolTipText("Flip Vertical");
        flipVBtn.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null) c.flipSelectedVertical();
        });

        JButton undoBtn = new JButton(ScadaVectorIcons.undo());
        undoBtn.setToolTipText("Undo");
        undoBtn.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null && c.getUndoManager().undo(c.getScreen())) {
                c.repaint();
            }
        });

        JButton redoBtn = new JButton(ScadaVectorIcons.redo());
        redoBtn.setToolTipText("Redo");
        redoBtn.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null && c.getUndoManager().redo(c.getScreen())) {
                c.repaint();
            }
        });

        JButton zoomOutBtn = new JButton(ScadaVectorIcons.zoomOut());
        zoomOutBtn.setToolTipText("Zoom Out");
        zoomOutBtn.addActionListener(e -> onZoomOut());

        JButton zoomInBtn = new JButton(ScadaVectorIcons.zoomIn());
        zoomInBtn.setToolTipText("Zoom In");
        zoomInBtn.addActionListener(e -> onZoomIn());

        simPreviewBtn.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null) c.setShowFlowAnimation(simPreviewBtn.isSelected());
        });

        operatorModeBtn.addActionListener(e -> setOperatorMode(operatorModeBtn.isSelected()));

        JButton linkMflBtn = new JButton("Link .mfl", ScadaVectorIcons.linkMfl());
        linkMflBtn.addActionListener(e -> onLinkMfl());

        tb.add(cursorBtn);
        tb.add(pipeBtn);
        tb.addSeparator();
        tb.add(rotBtn);
        tb.add(flipHBtn);
        tb.add(flipVBtn);
        tb.addSeparator();
        tb.add(undoBtn);
        tb.add(redoBtn);
        tb.addSeparator();
        tb.add(zoomOutBtn);
        tb.add(zoomLabel);
        tb.add(zoomInBtn);
        tb.addSeparator();
        tb.add(simPreviewBtn);
        tb.add(operatorModeBtn);
        tb.addSeparator();
        tb.add(linkMflBtn);
        return tb;
    }

    private void loadProjectIntoUi(ScadaProject newProject) {
        disposeAllCanvases();
        this.project = newProject;
        screenTabs.removeAll();
        for (ScadaScreen screen : project.getScreens()) {
            ScadaCanvas canvas = new ScadaCanvas(project, screen);
            canvas.setTagProvider(tagProvider);
            canvas.setShowFlowAnimation(simPreviewBtn.isSelected());
            canvas.setOperatorMode(operatorMode);
            JScrollPane scroll = new JScrollPane(canvas);
            scroll.getVerticalScrollBar().setUnitIncrement(20);
            scroll.getHorizontalScrollBar().setUnitIncrement(20);
            screenTabs.addTab(screen.getName(), scroll);
        }
        refreshTagTable();
        rebuildOperatorScreenButtons();
        updateTitle();
    }

    private void buildOperatorBar() {
        operatorBar.setBackground(new Color(0x2B, 0x30, 0x36));
        operatorBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x14, 0x17, 0x1A)));
        operatorScreenButtons.setOpaque(false);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        right.setOpaque(false);
        connStatusLabel.setOpaque(true);
        connStatusLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        connStatusLabel.setBackground(new Color(0x1E, 0x84, 0x49));
        connStatusLabel.setForeground(Color.WHITE);

        JButton exitOpBtn = new JButton("Exit Operator Mode");
        exitOpBtn.addActionListener(e -> setOperatorMode(false));
        right.add(connStatusLabel);
        right.add(exitOpBtn);

        operatorBar.add(operatorScreenButtons, BorderLayout.CENTER);
        operatorBar.add(right, BorderLayout.EAST);
    }

    private void rebuildOperatorScreenButtons() {
        operatorScreenButtons.removeAll();
        if (project == null) return;
        for (int i = 0; i < project.getScreens().size(); i++) {
            final int idx = i;
            ScadaScreen scr = project.getScreens().get(i);
            JButton btn = new JButton(scr.getName());
            btn.addActionListener(e -> screenTabs.setSelectedIndex(idx));
            operatorScreenButtons.add(btn);
        }
        operatorScreenButtons.revalidate();
        operatorScreenButtons.repaint();
    }

    private void updateConnectionBadge() {
        boolean online = tagProvider.isConnected();
        connStatusLabel.setText(online ? " ONLINE " : " OFFLINE ");
        connStatusLabel.setBackground(online ? new Color(0x1E, 0x84, 0x49) : new Color(0xD9, 0x26, 0x26));
    }

    private void disposeAllCanvases() {
        if (runtimeSyncTimer != null) {
            runtimeSyncTimer.stop();
        }
        for (int i = 0; i < screenTabs.getTabCount(); i++) {
            if (screenTabs.getComponentAt(i) instanceof JScrollPane sp
                    && sp.getViewport().getView() instanceof ScadaCanvas c) {
                c.dispose();
            }
        }
    }

    private ScadaCanvas getActiveCanvas() {
        int idx = screenTabs.getSelectedIndex();
        if (idx < 0) return null;
        if (screenTabs.getComponentAt(idx) instanceof JScrollPane sp
                && sp.getViewport().getView() instanceof ScadaCanvas c) {
            return c;
        }
        return null;
    }

    private void repaintActiveCanvas() {
        ScadaCanvas c = getActiveCanvas();
        if (c != null) c.repaint();
    }

    private void setActiveToolMode(ScadaCanvas.ToolMode mode) {
        ScadaCanvas c = getActiveCanvas();
        if (c != null) c.setToolMode(mode);
    }

    private void setOperatorMode(boolean opMode) {
        this.operatorMode = opMode;
        operatorModeBtn.setSelected(opMode);
        leftDockTabs.setVisible(!opMode);
        topBarHost.removeAll();
        topBarHost.add(opMode ? operatorBar : quickToolbar, BorderLayout.CENTER);
        topBarHost.revalidate();
        topBarHost.repaint();
        if (getJMenuBar() != null) {
            getJMenuBar().setVisible(!opMode);
        }
        if (!opMode) {
            mainSplit.setDividerLocation(250);
        }
        for (int i = 0; i < screenTabs.getTabCount(); i++) {
            if (screenTabs.getComponentAt(i) instanceof JScrollPane sp
                    && sp.getViewport().getView() instanceof ScadaCanvas c) {
                c.setOperatorMode(opMode);
            }
        }
        updateTitle();
    }

    private void placeSymbolFromLibrary(String symbolType) {
        ScadaCanvas c = getActiveCanvas();
        if (c == null || c.getScreen() == null) return;
        ScadaScreen scr = c.getScreen();
        c.getUndoManager().recordSnapshot("Add " + symbolType, scr);
        int idx = scr.getSymbols().size() + 1;
        ScadaSymbolInstance sym = SymbolCatalogTree.createDefaultInstance(symbolType, symbolType + "-" + idx, 10, 10);
        scr.getSymbols().add(sym);
        c.getSelectedSymbolIds().clear();
        c.getSelectedSymbolIds().add(sym.getId());
        project.setDirty(true);
        c.repaint();
        statusLabel.setText(" Placed " + sym.getName());
    }

    private void onZoomIn() {
        ScadaCanvas c = getActiveCanvas();
        if (c != null) {
            c.zoomIn();
            zoomLabel.setText(" " + c.getZoomPercent() + "% ");
        }
    }

    private void onZoomOut() {
        ScadaCanvas c = getActiveCanvas();
        if (c != null) {
            c.zoomOut();
            zoomLabel.setText(" " + c.getZoomPercent() + "% ");
        }
    }

    private void onAddScreen() {
        String name = JOptionPane.showInputDialog(this, "Screen Name:", "Screen" + (project.getScreens().size() + 1));
        if (name == null || name.isBlank()) return;
        ScadaScreen screen = new ScadaScreen(name.trim());
        project.getScreens().add(screen);
        ScadaCanvas canvas = new ScadaCanvas(project, screen);
        canvas.setTagProvider(tagProvider);
        screenTabs.addTab(screen.getName(), new JScrollPane(canvas));
        screenTabs.setSelectedIndex(screenTabs.getTabCount() - 1);
        project.setDirty(true);
    }

    private void onOpenMsl() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Maraview SCADA Project (*.msl)", "msl"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                ScadaProject loaded = MslProjectSerializer.loadProject(fc.getSelectedFile());
                loadProjectIntoUi(loaded);
                statusLabel.setText(" Opened " + fc.getSelectedFile().getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to open .msl: " + ex.getMessage(),
                        "Open Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onSaveMsl(boolean saveAs) {
        File target = project.getProjectFile();
        if (saveAs || target == null) {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("Maraview SCADA Project (*.msl)", "msl"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            target = fc.getSelectedFile();
            if (!target.getName().toLowerCase().endsWith(".msl")) {
                target = new File(target.getParentFile(), target.getName() + ".msl");
            }
        }
        try {
            MslProjectSerializer.saveProject(project, target);
            updateTitle();
            statusLabel.setText(" Saved " + target.getName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save .msl: " + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onLinkMfl() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Maraflow PLC Project (*.mfl)", "mfl"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                int count = MflTagImporter.importIntoProject(fc.getSelectedFile(), project);
                refreshTagTable();
                statusLabel.setText(" Imported " + count + " tags from " + fc.getSelectedFile().getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to import .mfl: " + ex.getMessage(),
                        "Import Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void refreshTagTable() {
        tagTableModel.setRowCount(0);
        for (ScadaTag tag : project.getTags().values()) {
            tagTableModel.addRow(new Object[]{
                    tag.getName(),
                    tag.getDataType().name(),
                    tag.getSourceType().name(),
                    tag.getFormattedValue(true)
            });
        }
    }

    private void updateTitle() {
        String path = (project.getProjectFile() != null) ? project.getProjectFile().getAbsolutePath() : "unsaved";
        String mode = operatorMode ? "[OPERATOR]" : "[DESIGN]";
        setTitle(project.getProjectName() + " (" + path + ") - Maraview V" + ScadaProject.VERSION + " " + mode);
    }

    private static ScadaProject createSampleProject() {
        ScadaProject p = new ScadaProject();
        p.setProjectName("DemoLoop");

        ScadaTag pumpRun = new ScadaTag("Pump1_Run", DataType.BOOL, TagSourceType.INTERNAL);
        pumpRun.setInitialValue("1");
        pumpRun.resetRuntimeToInitial();
        p.putTag(pumpRun);

        ScadaTag valveOpen = new ScadaTag("Valve1_Open", DataType.BOOL, TagSourceType.INTERNAL);
        valveOpen.setInitialValue("1");
        valveOpen.resetRuntimeToInitial();
        p.putTag(valveOpen);

        ScadaScreen screen = p.getScreens().getFirst();
        ScadaSymbolInstance pump = SymbolCatalogTree.createDefaultInstance("CentrifugalPump", "P-101", 10, 18);
        pump.getTagBindings().put("RunTag", "Pump1_Run");

        ScadaSymbolInstance valve = SymbolCatalogTree.createDefaultInstance("SolenoidValve", "XV-101", 26, 10);
        valve.getTagBindings().put("OpenTag", "Valve1_Open");

        ScadaSymbolInstance tank = SymbolCatalogTree.createDefaultInstance("CylindricalTank", "TK-101", 44, 8);

        screen.getSymbols().add(pump);
        screen.getSymbols().add(valve);
        screen.getSymbols().add(tank);

        ScadaPipe pipe1 = new ScadaPipe();
        pipe1.setSourceSymbolId(pump.getId());
        pipe1.setSourcePortId("discharge");
        pipe1.setTargetSymbolId(valve.getId());
        pipe1.setTargetPortId("inlet");
        pipe1.setDiameter(PipeDiameter.MEDIUM);
        pipe1.setFluidType(FluidType.WATER);
        pipe1.setVertices(PipeRouter.route(pump.resolveWorldPort("discharge"), valve.resolveWorldPort("inlet")));

        ScadaPipe pipe2 = new ScadaPipe();
        pipe2.setSourceSymbolId(valve.getId());
        pipe2.setSourcePortId("outlet");
        pipe2.setTargetSymbolId(tank.getId());
        pipe2.setTargetPortId("inlet");
        pipe2.setDiameter(PipeDiameter.MEDIUM);
        pipe2.setFluidType(FluidType.WATER);
        pipe2.setVertices(PipeRouter.route(valve.resolveWorldPort("outlet"), tank.resolveWorldPort("inlet")));

        screen.getPipes().add(pipe1);
        screen.getPipes().add(pipe2);
        return p;
    }
}
