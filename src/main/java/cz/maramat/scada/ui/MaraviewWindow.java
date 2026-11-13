package cz.maramat.scada.ui;

import cz.maramat.scada.alarms.AlarmEngine;
import cz.maramat.scada.io.MslProjectSerializer;
import cz.maramat.scada.model.ConnectionType;
import cz.maramat.scada.model.DataType;
import cz.maramat.scada.model.FluidType;
import cz.maramat.scada.model.PipeDiameter;
import cz.maramat.scada.model.ScadaGraphicElement;
import cz.maramat.scada.model.ScadaPipe;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaScreen;
import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.routing.PipeRouter;
import cz.maramat.scada.tags.InternalTagProvider;
import cz.maramat.scada.tags.MflTagImporter;
import cz.maramat.scada.tags.ScadaTag;
import cz.maramat.scada.tags.TagQuality;
import cz.maramat.scada.tags.TagSourceType;
import cz.maramat.scada.ui.ScadaCanvas.SimState;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;

/**
 * Main Maraview SCADA Designer & Operator Runtime window matching MaraFlow's Win95/Win7 chrome.
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

    private JButton playPauseBtn;
    private JButton stopBtn;
    private JButton stepBtn;
    private JToggleButton simPreviewBtn;
    private JToggleButton operatorModeBtn;

    private final JPanel topBarHost = new JPanel(new BorderLayout());
    private final JPanel operatorBar = new JPanel(new BorderLayout());
    private final JPanel operatorScreenButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    private final JLabel connStatusLabel = new JLabel(" ONLINE ");
    private final Timer runtimeSyncTimer;
    private JSplitPane mainSplit;
    private DockableSectionPanel leftDockSection;
    private JToolBar quickToolbar;
    private SimState currentSimState = SimState.STOPPED;
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
        JScrollPane treeScroll = new JScrollPane(symbolTree);
        treeScroll.setBorder(null);
        JScrollPane tagScroll = new JScrollPane(tagTable);
        tagScroll.setBorder(null);

        leftDockTabs.addTab("Symbols", treeScroll);
        leftDockTabs.addTab("Tags", tagScroll);
        leftDockSection = new DockableSectionPanel("Library & Tags", leftDockTabs,
                () -> leftDockSection.setVisible(false));
        leftDockSection.setPreferredSize(new Dimension(245, 600));

        DockableSectionPanel viewportSection = new DockableSectionPanel("Process Viewport", screenTabs, null);

        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftDockSection, viewportSection);
        mainSplit.setDividerSize(5);
        mainSplit.setDividerLocation(245);
        add(mainSplit, BorderLayout.CENTER);

        JPanel bottomStack = new JPanel(new BorderLayout());
        bottomStack.add(alarmBanner, BorderLayout.NORTH);
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xB8, 0xBE, 0xC6)));
        statusBar.add(statusLabel, BorderLayout.WEST);
        bottomStack.add(statusBar, BorderLayout.SOUTH);
        add(bottomStack, BorderLayout.SOUTH);

        this.runtimeSyncTimer = new Timer(250, e -> onRuntimeTick());
        this.runtimeSyncTimer.start();

        loadProjectIntoUi(this.project);
        updateSimUiState(SimState.STOPPED);
        if (startInOperatorMode) {
            setOperatorMode(true);
            onSimPlay();
        }
    }

    private void onRuntimeTick() {
        if (currentSimState == SimState.RUNNING) {
            tagProvider.syncAll(project);
            alarmEngine.evaluate(project, System.currentTimeMillis());
            refreshTagTable();
        }
        alarmBanner.refresh(System.currentTimeMillis());
        updateConnectionBadge();
    }

    private JMenuBar buildMenuBar() {
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
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
            if (c != null && c.getUndoManager().undo(c.getScreen())) c.repaint();
        });
        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null && c.getUndoManager().redo(c.getScreen())) c.repaint();
        });
        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask));
        copyItem.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null) c.copySelection();
        });
        JMenuItem pasteItem = new JMenuItem("Paste");
        pasteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask));
        pasteItem.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null) c.pasteFromClipboard();
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
        editMenu.add(copyItem);
        editMenu.add(pasteItem);
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
        JMenuItem showLibItem = new JMenuItem("Show Library & Tags");
        showLibItem.addActionListener(e -> {
            leftDockSection.setVisible(true);
            mainSplit.setDividerLocation(245);
        });
        JMenuItem opItem = new JMenuItem("Toggle Operator Mode");
        opItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        opItem.addActionListener(e -> setOperatorMode(!operatorMode));
        windowMenu.add(showLibItem);
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
        tb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xB8, 0xB8, 0xB8)));
        tb.setMargin(new Insets(2, 4, 2, 4));

        JToggleButton cursorBtn = createToggleBtn(ScadaVectorIcons.cursor(), "Cursor / Select Tool");
        cursorBtn.setSelected(true);
        JToggleButton pipeBtn = createToggleBtn(ScadaVectorIcons.pipe(), "Pipe / Wire Routing Tool");
        JToggleButton lineBtn = createToggleBtn(ScadaVectorIcons.lineIcon(), "Draw Line");
        JToggleButton rectBtn = createToggleBtn(ScadaVectorIcons.rectIcon(), "Draw Rectangle");
        JToggleButton circleBtn = createToggleBtn(ScadaVectorIcons.circleIcon(), "Draw Circle");
        JToggleButton textBtn = createToggleBtn(ScadaVectorIcons.textIcon(), "Place Text Label");
        ButtonGroup toolGroup = new ButtonGroup();
        toolGroup.add(cursorBtn);
        toolGroup.add(pipeBtn);
        toolGroup.add(lineBtn);
        toolGroup.add(rectBtn);
        toolGroup.add(circleBtn);
        toolGroup.add(textBtn);
        cursorBtn.addActionListener(e -> setActiveToolMode(ScadaCanvas.ToolMode.CURSOR));
        pipeBtn.addActionListener(e -> setActiveToolMode(ScadaCanvas.ToolMode.PIPE));
        lineBtn.addActionListener(e -> setActiveShapeTool(ScadaGraphicElement.Kind.LINE));
        rectBtn.addActionListener(e -> setActiveShapeTool(ScadaGraphicElement.Kind.RECTANGLE));
        circleBtn.addActionListener(e -> setActiveShapeTool(ScadaGraphicElement.Kind.CIRCLE));
        textBtn.addActionListener(e -> setActiveShapeTool(ScadaGraphicElement.Kind.TEXT));

        JButton imageBtn = createActionBtn(ScadaVectorIcons.imageIcon(), "Insert Picture / Image...");
        imageBtn.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null) c.insertImageFromDialog(10, 10);
        });

        JButton rotBtn = createActionBtn(ScadaVectorIcons.rotate90(), "Rotate 90 deg (R)");
        rotBtn.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null) c.rotateSelected90CW();
        });
        JButton flipHBtn = createActionBtn(ScadaVectorIcons.flipH(), "Flip Horizontal");
        flipHBtn.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null) c.flipSelectedHorizontal();
        });
        JButton flipVBtn = createActionBtn(ScadaVectorIcons.flipV(), "Flip Vertical");
        flipVBtn.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null) c.flipSelectedVertical();
        });

        JButton undoBtn = createActionBtn(ScadaVectorIcons.undo(), "Undo");
        undoBtn.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null && c.getUndoManager().undo(c.getScreen())) c.repaint();
        });
        JButton redoBtn = createActionBtn(ScadaVectorIcons.redo(), "Redo");
        redoBtn.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null && c.getUndoManager().redo(c.getScreen())) c.repaint();
        });

        JButton zoomOutBtn = createActionBtn(ScadaVectorIcons.zoomOut(), "Zoom Out");
        zoomOutBtn.addActionListener(e -> onZoomOut());
        JButton zoomInBtn = createActionBtn(ScadaVectorIcons.zoomIn(), "Zoom In");
        zoomInBtn.addActionListener(e -> onZoomIn());
        zoomLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        // MaraFlow-style Play / Pause / Stop / Step simulation controls
        playPauseBtn = createActionBtn(ScadaVectorIcons.playIcon(), "Play Simulation");
        playPauseBtn.addActionListener(e -> {
            if (currentSimState == SimState.RUNNING) {
                onSimPause();
            } else {
                onSimPlay();
            }
        });
        stopBtn = createActionBtn(ScadaVectorIcons.stopIcon(), "Halt / Stop Simulation (Resets all I/O)");
        stopBtn.addActionListener(e -> onSimStop());

        stepBtn = createActionBtn(ScadaVectorIcons.stepIcon(), "Step Simulation (1 Tick)");
        stepBtn.addActionListener(e -> onSimStep());

        simPreviewBtn = new JToggleButton("Preview", false);
        simPreviewBtn.setFocusable(false);
        simPreviewBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        simPreviewBtn.setMargin(new Insets(2, 6, 2, 6));
        simPreviewBtn.setToolTipText("Toggle Live Simulation Preview (Off resets all I/O to 0)");
        simPreviewBtn.addActionListener(e -> {
            if (simPreviewBtn.isSelected()) {
                onSimPlay();
            } else {
                onSimStop();
            }
        });

        operatorModeBtn = createToggleBtn(ScadaVectorIcons.operatorMode(), "Operator View Mode (F5)");
        operatorModeBtn.addActionListener(e -> setOperatorMode(operatorModeBtn.isSelected()));

        JButton linkMflBtn = createActionBtn(ScadaVectorIcons.linkMfl(), "Link Maraflow .mfl Project");
        linkMflBtn.addActionListener(e -> onLinkMfl());

        tb.add(cursorBtn);
        tb.add(pipeBtn);
        addSplitter(tb);
        tb.add(lineBtn);
        tb.add(rectBtn);
        tb.add(circleBtn);
        tb.add(textBtn);
        tb.add(imageBtn);
        addSplitter(tb);
        tb.add(rotBtn);
        tb.add(flipHBtn);
        tb.add(flipVBtn);
        addSplitter(tb);
        tb.add(undoBtn);
        tb.add(redoBtn);
        addSplitter(tb);
        tb.add(zoomOutBtn);
        tb.add(zoomLabel);
        tb.add(zoomInBtn);
        addSplitter(tb);
        tb.add(playPauseBtn);
        tb.add(stopBtn);
        tb.add(stepBtn);
        tb.add(Box.createHorizontalStrut(4));
        tb.add(simPreviewBtn);
        addSplitter(tb);
        tb.add(operatorModeBtn);
        tb.add(linkMflBtn);
        return tb;
    }

    private static JButton createActionBtn(Icon icon, String tooltip) {
        JButton b = new JButton(icon);
        b.setFocusable(false);
        b.setToolTipText(tooltip);
        b.setMargin(new Insets(3, 4, 3, 4));
        return b;
    }

    private static JToggleButton createToggleBtn(Icon icon, String tooltip) {
        JToggleButton b = new JToggleButton(icon);
        b.setFocusable(false);
        b.setToolTipText(tooltip);
        b.setMargin(new Insets(3, 4, 3, 4));
        return b;
    }

    private static void addSplitter(JToolBar tb) {
        tb.add(Box.createHorizontalStrut(4));
        tb.add(new JToolBar.Separator(new Dimension(6, 20)));
        tb.add(Box.createHorizontalStrut(4));
    }

    public void onSimPlay() {
        if (currentSimState == SimState.STOPPED) {
            restoreInitialTagValues();
        }
        updateSimUiState(SimState.RUNNING);
        statusLabel.setText(" Simulation Running");
    }

    public void onSimPause() {
        updateSimUiState(SimState.PAUSED);
        statusLabel.setText(" Simulation Paused");
    }

    public void onSimStop() {
        resetAllIoAndStates();
        updateSimUiState(SimState.STOPPED);
        statusLabel.setText(" Simulation Stopped (All I/O Reset to 0)");
    }

    public void onSimStep() {
        if (currentSimState == SimState.STOPPED) {
            restoreInitialTagValues();
        }
        ScadaCanvas c = getActiveCanvas();
        if (c != null) {
            c.stepSimulation();
            updateSimUiState(c.getSimState());
            statusLabel.setText(" Simulation Stepped");
        }
    }

    private void restoreInitialTagValues() {
        if (project != null) {
            for (ScadaTag tag : project.getTags().values()) {
                tag.resetRuntimeToInitial();
            }
            alarmEngine.evaluate(project, System.currentTimeMillis());
        }
        refreshTagTable();
        alarmBanner.refresh(System.currentTimeMillis());
    }

    private void resetAllIoAndStates() {
        if (project != null) {
            for (ScadaTag tag : project.getTags().values()) {
                tag.setRuntimeValue(0.0);
                tag.setRuntimeQuality(TagQuality.GOOD);
            }
            alarmEngine.acknowledgeAll();
            alarmEngine.evaluate(project, System.currentTimeMillis());
        }
        refreshTagTable();
        alarmBanner.refresh(System.currentTimeMillis());
    }

    private void updateSimUiState(SimState state) {
        this.currentSimState = state;
        boolean running = (state == SimState.RUNNING);
        playPauseBtn.setIcon(running ? ScadaVectorIcons.pauseIcon() : ScadaVectorIcons.playIcon());
        playPauseBtn.setToolTipText(running ? "Pause Simulation" : (state == SimState.PAUSED ? "Resume Simulation" : "Play Simulation"));
        stopBtn.setEnabled(state != SimState.STOPPED);
        simPreviewBtn.setSelected(state != SimState.STOPPED);

        for (int i = 0; i < screenTabs.getTabCount(); i++) {
            if (screenTabs.getComponentAt(i) instanceof JScrollPane sp
                    && sp.getViewport().getView() instanceof ScadaCanvas c) {
                c.setSimState(state);
            }
        }
    }

    private void loadProjectIntoUi(ScadaProject newProject) {
        disposeAllCanvases();
        this.project = newProject;
        screenTabs.removeAll();
        for (ScadaScreen screen : project.getScreens()) {
            addScreenTab(screen);
        }
        refreshTagTable();
        rebuildOperatorScreenButtons();
        updateTitle();
    }

    private void addScreenTab(ScadaScreen screen) {
        ScadaCanvas canvas = new ScadaCanvas(project, screen);
        canvas.setTagProvider(tagProvider);
        canvas.setSimState(currentSimState);
        canvas.setOperatorMode(operatorMode);
        canvas.setOnStepCallback(() -> {
            updateSimUiState(canvas.getSimState());
            alarmEngine.evaluate(project, System.currentTimeMillis());
            refreshTagTable();
            alarmBanner.refresh(System.currentTimeMillis());
        });
        JScrollPane scroll = new JScrollPane(canvas);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.getHorizontalScrollBar().setUnitIncrement(20);
        screenTabs.addTab(screen.getName(), scroll);
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

    private void setActiveShapeTool(ScadaGraphicElement.Kind kind) {
        ScadaCanvas c = getActiveCanvas();
        if (c != null) c.setShapeTool(kind);
    }

    private void setOperatorMode(boolean opMode) {
        this.operatorMode = opMode;
        operatorModeBtn.setSelected(opMode);
        leftDockSection.setVisible(!opMode);
        topBarHost.removeAll();
        topBarHost.add(opMode ? operatorBar : quickToolbar, BorderLayout.CENTER);
        topBarHost.revalidate();
        topBarHost.repaint();
        if (getJMenuBar() != null) {
            getJMenuBar().setVisible(!opMode);
        }
        if (!opMode) {
            mainSplit.setDividerLocation(245);
        }
        for (int i = 0; i < screenTabs.getTabCount(); i++) {
            if (screenTabs.getComponentAt(i) instanceof JScrollPane sp
                    && sp.getViewport().getView() instanceof ScadaCanvas c) {
                c.setOperatorMode(opMode);
            }
        }
        updateTitle();
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
        addScreenTab(screen);
        screenTabs.setSelectedIndex(screenTabs.getTabCount() - 1);
        rebuildOperatorScreenButtons();
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
        p.setProjectName("WWTP_Municipal_COV");
        p.setDescription("Municipal Wastewater Treatment Plant (ČOV) SCADA HMI");

        addTag(p, "InfluentPump_Run", DataType.BOOL, "1", "");
        addTag(p, "BarScreen_Run", DataType.BOOL, "1", "");
        addTag(p, "Clarifier1_Run", DataType.BOOL, "1", "");
        addTag(p, "Blower1_Run", DataType.BOOL, "1", "");
        addTag(p, "Blower1_Speed", DataType.FLOAT, "78.0", "%");
        addTag(p, "Aeration_Run", DataType.BOOL, "1", "");
        addTag(p, "Clarifier2_Run", DataType.BOOL, "1", "");
        addTag(p, "Uv_Run", DataType.BOOL, "1", "");
        addTag(p, "Influent_Flow", DataType.FLOAT, "425.0", "m³/h");
        addTag(p, "DO_Conc", DataType.FLOAT, "2.4", "mg/L");
        addTag(p, "Effluent_Flow", DataType.FLOAT, "418.5", "m³/h");
        addTag(p, "Digester_Temp", DataType.FLOAT, "37.4", "°C");
        addTag(p, "Thickener_Run", DataType.BOOL, "1", "");
        addTag(p, "Boiler_Run", DataType.BOOL, "1", "");

        // Screen 1: Water Line (ČOV)
        ScadaScreen waterLine = p.getScreens().getFirst();
        waterLine.setName("Water Line (ČOV)");

        // Graphical zone frames & titles
        addZoneFrame(waterLine, "1. MECHANICAL PRE-TREATMENT", 2, 2, 46, 20, new Color(0xE3, 0xF2, 0xFD), new Color(0x15, 0x65, 0xC0));
        addZoneFrame(waterLine, "2. BIOLOGICAL TREATMENT & AERATION", 50, 2, 48, 30, new Color(0xE8, 0xF5, 0xE9), new Color(0x2E, 0x7D, 0x32));
        addZoneFrame(waterLine, "3. CLARIFICATION & UV DISINFECTION", 100, 2, 48, 20, new Color(0xE0, 0xF7, 0xFA), new Color(0x00, 0x83, 0x8F));
        addZoneFrame(waterLine, "4. OPERATOR TELEMETRY & POWER FEED", 2, 24, 46, 16, new Color(0xFF, 0xF8, 0xE1), new Color(0xEF, 0x6C, 0x00));

        // Equipment - Zone 1
        ScadaSymbolInstance screwPump = SymbolCatalogTree.createDefaultInstance("ArchimedesScrewPump", "P-101 Screw", 4, 8);
        screwPump.getTagBindings().put("RunTag", "InfluentPump_Run");
        ScadaSymbolInstance barScreen = SymbolCatalogTree.createDefaultInstance("BarScreen", "SCR-101", 19, 8);
        barScreen.getTagBindings().put("RunTag", "BarScreen_Run");
        ScadaSymbolInstance grit = SymbolCatalogTree.createDefaultInstance("GritChamber", "GC-101", 34, 8);

        // Equipment - Zone 2
        ScadaSymbolInstance primClar = SymbolCatalogTree.createDefaultInstance("PrimaryClarifier", "CL-201", 53, 7);
        primClar.getTagBindings().put("RunTag", "Clarifier1_Run");
        ScadaSymbolInstance aeration = SymbolCatalogTree.createDefaultInstance("AerationBasin", "AT-201", 72, 8);
        aeration.getTagBindings().put("RunTag", "Aeration_Run");
        ScadaSymbolInstance blower = SymbolCatalogTree.createDefaultInstance("AirBlower", "BL-201", 75, 23);
        blower.getTagBindings().put("RunTag", "Blower1_Run");
        blower.getTagBindings().put("SpeedTag", "Blower1_Speed");
        ScadaSymbolInstance vfd = SymbolCatalogTree.createDefaultInstance("VfdDrive", "VFD-201", 60, 22);
        vfd.getTagBindings().put("RunTag", "Blower1_Run");
        vfd.getTagBindings().put("SpeedTag", "Blower1_Speed");

        // Equipment - Zone 3
        ScadaSymbolInstance secClar = SymbolCatalogTree.createDefaultInstance("SecondaryClarifier", "CL-301", 103, 7);
        secClar.getTagBindings().put("RunTag", "Clarifier2_Run");
        ScadaSymbolInstance uv = SymbolCatalogTree.createDefaultInstance("UvDisinfection", "UV-301", 121, 9);
        uv.getTagBindings().put("RunTag", "Uv_Run");
        ScadaSymbolInstance flowOut = SymbolCatalogTree.createDefaultInstance("FlowMeter", "FIT-301", 137, 9);
        flowOut.getTagBindings().put("ValueTag", "Effluent_Flow");

        // Equipment - Zone 4 (Power + Readouts + Controls)
        ScadaSymbolInstance pwr400 = SymbolCatalogTree.createDefaultInstance("PowerSource400V", "MCC-400V", 4, 29);
        pwr400.getTagBindings().put("RunTag", "Blower1_Run");
        ScadaSymbolInstance roIn = SymbolCatalogTree.createDefaultInstance("NumericReadout", "Influent m³/h", 14, 28);
        roIn.getTagBindings().put("ValueTag", "Influent_Flow");
        ScadaSymbolInstance roDo = SymbolCatalogTree.createDefaultInstance("NumericReadout", "O2 mg/L", 25, 28);
        roDo.getTagBindings().put("ValueTag", "DO_Conc");
        ScadaSymbolInstance roOut = SymbolCatalogTree.createDefaultInstance("NumericReadout", "Effluent m³/h", 36, 28);
        roOut.getTagBindings().put("ValueTag", "Effluent_Flow");
        ScadaSymbolInstance btnStart = SymbolCatalogTree.createDefaultInstance("PushButton", "AUTO START", 14, 34);
        btnStart.getTagBindings().put("RunTag", "InfluentPump_Run");
        ScadaSymbolInstance lampRun = SymbolCatalogTree.createDefaultInstance("PilotLamp", "PLANT RUN", 22, 34);
        lampRun.getTagBindings().put("RunTag", "InfluentPump_Run");

        for (ScadaSymbolInstance sym : java.util.List.of(
                screwPump, barScreen, grit, primClar, aeration, blower, vfd,
                secClar, uv, flowOut, pwr400, roIn, roDo, roOut, btnStart, lampRun)) {
            waterLine.getSymbols().add(sym);
        }

        // Fluid Pipes
        connect(waterLine, screwPump, "out", barScreen, "in", ConnectionType.PIPE, FluidType.WATER, PipeDiameter.LARGE);
        connect(waterLine, barScreen, "out", grit, "in", ConnectionType.PIPE, FluidType.WATER, PipeDiameter.LARGE);
        connect(waterLine, grit, "out", primClar, "in", ConnectionType.PIPE, FluidType.WATER, PipeDiameter.LARGE);
        connect(waterLine, primClar, "out", aeration, "in", ConnectionType.PIPE, FluidType.WATER, PipeDiameter.LARGE);
        connect(waterLine, aeration, "out", secClar, "in", ConnectionType.PIPE, FluidType.WATER, PipeDiameter.LARGE);
        connect(waterLine, secClar, "out", uv, "in", ConnectionType.PIPE, FluidType.WATER, PipeDiameter.LARGE);
        connect(waterLine, uv, "out", flowOut, "inlet", ConnectionType.PIPE, FluidType.WATER, PipeDiameter.MEDIUM);

        // Air supply pipe from Blower -> Aeration Basin
        connect(waterLine, blower, "air_out", aeration, "air", ConnectionType.PIPE, FluidType.WATER, PipeDiameter.MEDIUM);

        // Electrical Power Cables (400V MCC -> VFD -> Blower)
        connect(waterLine, pwr400, "out", vfd, "in", ConnectionType.ELECTRICAL_POWER, FluidType.EMPTY, PipeDiameter.SMALL);
        connect(waterLine, vfd, "out", blower, "pwr", ConnectionType.ELECTRICAL_POWER, FluidType.EMPTY, PipeDiameter.SMALL);

        // Screen 2: Sludge & Biogas
        ScadaScreen sludgeLine = new ScadaScreen("Sludge & Biogas");
        addZoneFrame(sludgeLine, "SLUDGE THICKENING & DEWATERING", 2, 2, 54, 24, new Color(0xEF, 0xEB, 0xE9), new Color(0x5D, 0x40, 0x37));
        addZoneFrame(sludgeLine, "ANAEROBIC DIGESTION & BIOGAS HEATING", 58, 2, 52, 24, new Color(0xFF, 0xF3, 0xE0), new Color(0xE6, 0x51, 0x00));

        ScadaSymbolInstance thickener = SymbolCatalogTree.createDefaultInstance("SludgeThickener", "THK-401", 6, 8);
        thickener.getTagBindings().put("RunTag", "Thickener_Run");
        ScadaSymbolInstance dosing = SymbolCatalogTree.createDefaultInstance("DosingStation", "DOS-401", 22, 16);
        ScadaSymbolInstance press = SymbolCatalogTree.createDefaultInstance("FilterPress", "FP-401", 36, 9);
        press.getTagBindings().put("RunTag", "Thickener_Run");

        ScadaSymbolInstance digester = SymbolCatalogTree.createDefaultInstance("AnaerobicDigester", "DIG-501", 64, 7);
        ScadaSymbolInstance boiler = SymbolCatalogTree.createDefaultInstance("Boiler", "BLR-501", 86, 8);
        boiler.getTagBindings().put("RunTag", "Boiler_Run");
        ScadaSymbolInstance digTemp = SymbolCatalogTree.createDefaultInstance("NumericReadout", "Digester °C", 74, 20);
        digTemp.getTagBindings().put("ValueTag", "Digester_Temp");

        for (ScadaSymbolInstance sym : java.util.List.of(thickener, dosing, press, digester, boiler, digTemp)) {
            sludgeLine.getSymbols().add(sym);
        }
        connect(sludgeLine, thickener, "out", press, "in", ConnectionType.PIPE, FluidType.SLURRY, PipeDiameter.MEDIUM);
        connect(sludgeLine, dosing, "out", press, "in", ConnectionType.PIPE, FluidType.CHEMICAL_GAS, PipeDiameter.SMALL);
        connect(sludgeLine, digester, "gas", boiler, "gas", ConnectionType.PIPE, FluidType.CHEMICAL_GAS, PipeDiameter.MEDIUM);

        p.getScreens().add(sludgeLine);
        return p;
    }

    private static void addTag(ScadaProject p, String name, DataType dt, String initVal, String units) {
        ScadaTag tag = new ScadaTag(name, dt, TagSourceType.INTERNAL);
        tag.setInitialValue(initVal);
        tag.setEngineeringUnits(units);
        tag.resetRuntimeToInitial();
        p.putTag(tag);
    }

    private static void addZoneFrame(ScadaScreen screen, String title, int gx, int gy, int gw, int gh,
                                     Color fill, Color stroke) {
        ScadaGraphicElement box = new ScadaGraphicElement(ScadaGraphicElement.Kind.ROUNDED_RECTANGLE, gx, gy, gw, gh);
        box.setFilled(true);
        box.setFillColor(fill);
        box.setStrokeColor(stroke);
        box.setStrokeWidth(1);
        box.setDashed(true);
        box.setOpacity(0.55);
        screen.getGraphics().add(box);

        ScadaGraphicElement label = new ScadaGraphicElement(ScadaGraphicElement.Kind.TEXT, gx + 1, gy + 1, gw - 2, 2);
        label.setText(title);
        label.setFontSize(11);
        label.setStrokeColor(stroke);
        screen.getGraphics().add(label);
    }

    private static void connect(ScadaScreen screen, ScadaSymbolInstance src, String srcPort,
                                ScadaSymbolInstance dst, String dstPort,
                                ConnectionType type, FluidType fluid, PipeDiameter dia) {
        ScadaPipe pipe = new ScadaPipe();
        pipe.setConnectionType(type);
        pipe.setSourceSymbolId(src.getId());
        pipe.setSourcePortId(srcPort);
        pipe.setTargetSymbolId(dst.getId());
        pipe.setTargetPortId(dstPort);
        pipe.setFluidType(fluid);
        pipe.setDiameter(dia);
        pipe.setVertices(PipeRouter.route(src.resolveWorldPort(srcPort), dst.resolveWorldPort(dstPort)));
        screen.getPipes().add(pipe);
    }
}
