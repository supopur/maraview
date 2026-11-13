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
        JToggleButton pipeBtn = createToggleBtn(ScadaVectorIcons.pipe(), "Pipe Routing Tool");
        ButtonGroup toolGroup = new ButtonGroup();
        toolGroup.add(cursorBtn);
        toolGroup.add(pipeBtn);
        cursorBtn.addActionListener(e -> setActiveToolMode(ScadaCanvas.ToolMode.CURSOR));
        pipeBtn.addActionListener(e -> setActiveToolMode(ScadaCanvas.ToolMode.PIPE));

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
        ScadaCanvas c = getActiveCanvas();
        if (c != null) {
            c.stepSimulation();
            updateSimUiState(c.getSimState());
            statusLabel.setText(" Simulation Stepped");
        }
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
