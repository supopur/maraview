package cz.maramat.scada.ui;

import com.formdev.flatlaf.FlatLaf;
import cz.maramat.Main;
import cz.maramat.scada.alarms.AlarmEngine;
import cz.maramat.scada.config.UserConfig;
import cz.maramat.scada.config.UserConfigStore;
import cz.maramat.scada.io.MslProjectSerializer;
import cz.maramat.scada.model.CommSettings;
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
import cz.maramat.scada.tags.MaraflowBridgeTagProvider;
import cz.maramat.scada.tags.MaraflowSimClient;
import cz.maramat.scada.tags.MflTagImporter;
import cz.maramat.scada.tags.RuntimeTagProvider;
import cz.maramat.scada.tags.ScadaTag;
import cz.maramat.scada.tags.TagQuality;
import cz.maramat.scada.tags.TagSourceType;
import cz.maramat.scada.ui.ScadaCanvas.SimState;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main Maraview SCADA Designer & Operator Runtime window matching MaraFlow's Win95/Win7 chrome.
 */
public class MaraviewWindow extends JFrame {

    private static final String CARD_HOME = "HOME";
    private static final String CARD_WORKSPACE = "WORKSPACE";

    private ScadaProject project;
    private boolean projectOpen = false;
    private UserConfig userConfig;
    private final CardLayout rootCardLayout = new CardLayout();
    private final JPanel rootCardPanel = new JPanel(rootCardLayout);
    private HomeScreenPanel homeScreenPanel;
    private final InternalTagProvider internalTagProvider = new InternalTagProvider();
    private final MaraflowSimClient maraflowSimClient = new MaraflowSimClient();
    private final MaraflowBridgeTagProvider maraflowTagProvider = new MaraflowBridgeTagProvider(maraflowSimClient);
    private RuntimeTagProvider tagProvider = internalTagProvider;
    private final ExecutorService simPollExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Maraview-Sim-Poll");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean simPollInFlight = new AtomicBoolean(false);
    private long lastUiRefreshMs = 0L;
    private boolean tagTableDirty = false;
    private static final long UI_REFRESH_INTERVAL_MS = 250L;
    private final AlarmEngine alarmEngine = new AlarmEngine();
    private final AlarmBannerPanel alarmBanner = new AlarmBannerPanel(alarmEngine);
    private final JTabbedPane screenTabs = new JTabbedPane();
    private final JTabbedPane leftDockTabs = new JTabbedPane();
    private final DefaultTableModel tagTableModel;
    private final JTable tagTable;
    private final JLabel zoomLabel = new JLabel(" 100% ");
    private final JLabel statusLabel = new JLabel(" Ready");
    private JPanel statusBar;
    private JMenu recentMenu;
    private JCheckBoxMenuItem viewGridItem;
    private JCheckBoxMenuItem viewFlowItem;
    private JCheckBoxMenuItem viewStatusBarItem;
    private JCheckBoxMenuItem viewAlarmBannerItem;
    private JRadioButtonMenuItem simInternalItem;
    private JRadioButtonMenuItem simMaraflowItem;

    private JButton playPauseBtn;
    private JButton stopBtn;
    private JButton stepBtn;
    private JButton undoBtn;
    private JButton redoBtn;
    private JToggleButton cursorToolBtn;
    private JToggleButton simPreviewBtn;
    private JToggleButton operatorModeBtn;
    private final List<JComponent> projectOnlyControls = new ArrayList<>();

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
        this(initialProject, startInOperatorMode, UserConfigStore.load());
    }

    public MaraviewWindow(ScadaProject initialProject, boolean startInOperatorMode, UserConfig userConfig) {
        this.userConfig = (userConfig == null) ? new UserConfig() : userConfig;
        this.maraflowTagProvider.setWriteExecutor(this.simPollExecutor);
        this.project = (initialProject == null) ? new ScadaProject() : initialProject;
        this.projectOpen = (initialProject != null);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(this.userConfig.getWindowWidth(), this.userConfig.getWindowHeight());
        setLocationRelativeTo(null);
        if (this.userConfig.isWindowMaximized()) {
            setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
        }
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                attemptExit();
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

        this.homeScreenPanel = new HomeScreenPanel(new HomeScreenPanel.HomeScreenListener() {
            @Override
            public void onNewProjectWizard(boolean promptMflLink) {
                onNewProject(promptMflLink);
            }

            @Override
            public void onOpenProjectDialog() {
                onOpenMsl();
            }

            @Override
            public void onOpenRecentFile(File file) {
                openProjectFile(file);
            }

            @Override
            public void onClearRecentFiles() {
                MaraviewWindow.this.userConfig.clearRecentProjects();
                rebuildRecentMenu();
                saveUserConfigQuietly();
                homeScreenPanel.setRecentPaths(MaraviewWindow.this.userConfig.getRecentProjects());
            }
        });

        this.quickToolbar = buildToolbar();
        buildOperatorBar();
        topBarHost.add(quickToolbar, BorderLayout.CENTER);

        JTree symbolTree = SymbolCatalogTree.createTree();
        JScrollPane treeScroll = new JScrollPane(symbolTree);
        treeScroll.setBorder(null);
        JScrollPane tagScroll = new JScrollPane(tagTable);
        tagScroll.setBorder(null);

        leftDockTabs.addTab("Symbols", treeScroll);
        leftDockTabs.addTab("Tags", tagScroll);
        leftDockSection = new DockableSectionPanel("Library & Tags", leftDockTabs,
                () -> leftDockSection.setVisible(false));
        leftDockSection.setPreferredSize(new Dimension(this.userConfig.getLeftDockWidth(), 600));

        DockableSectionPanel viewportSection = new DockableSectionPanel("Process Viewport", screenTabs, null);

        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftDockSection, viewportSection);
        mainSplit.setDividerSize(5);
        mainSplit.setDividerLocation(this.userConfig.getLeftDockWidth());

        JPanel northStack = new JPanel(new BorderLayout());
        northStack.add(topBarHost, BorderLayout.NORTH);
        alarmBanner.setVisible(this.userConfig.isShowAlarmBanner());
        northStack.add(alarmBanner, BorderLayout.SOUTH);

        statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xD0, 0xD4, 0xD8)));
        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(zoomLabel, BorderLayout.EAST);
        statusBar.setVisible(this.userConfig.isShowStatusBar());

        JPanel workspacePanel = new JPanel(new BorderLayout());
        workspacePanel.add(northStack, BorderLayout.NORTH);
        workspacePanel.add(mainSplit, BorderLayout.CENTER);
        workspacePanel.add(statusBar, BorderLayout.SOUTH);

        rootCardPanel.add(homeScreenPanel, CARD_HOME);
        rootCardPanel.add(workspacePanel, CARD_WORKSPACE);
        add(rootCardPanel, BorderLayout.CENTER);

        rootCardPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "toggleOperatorMode");
        rootCardPanel.getActionMap().put("toggleOperatorMode", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (projectOpen) {
                    setOperatorMode(!operatorMode);
                }
            }
        });

        leftDockTabs.addChangeListener(e -> {
            if (leftDockTabs.getSelectedIndex() == 1 && projectOpen) {
                refreshTagTable(true);
            }
        });

        this.runtimeSyncTimer = new Timer(this.userConfig.getRuntimeSyncIntervalMs(), e -> onRuntimeTick());
        this.runtimeSyncTimer.start();

        screenTabs.addChangeListener(e -> syncViewMenuWithActiveCanvas());

        if (this.projectOpen) {
            loadProjectIntoUi(this.project);
            updateSimUiState(SimState.STOPPED);
            if (startInOperatorMode) {
                setOperatorMode(true);
                if (this.userConfig.isAutoStartSimulationInOperatorMode()) {
                    onSimPlay();
                }
            }
        } else {
            showHomeScreen();
        }
    }

    private void onRuntimeTick() {
        if (!projectOpen) {
            return;
        }
        if (isMaraflowSimMode()) {
            pollMaraflowSimAsync();
            return;
        }
        if (currentSimState == SimState.RUNNING) {
            tagProvider.syncAll(project);
            long now = System.currentTimeMillis();
            alarmEngine.evaluate(project, now);
            maybeRefreshPeriodicUi(now, true);
        } else {
            maybeRefreshPeriodicUi(System.currentTimeMillis(), false);
        }
    }

    private void maybeRefreshPeriodicUi(long now, boolean includeTagTable) {
        if (includeTagTable) {
            tagTableDirty = true;
        }
        if (now - lastUiRefreshMs < UI_REFRESH_INTERVAL_MS) {
            return;
        }
        lastUiRefreshMs = now;
        if (tagTableDirty) {
            tagTableDirty = false;
            refreshTagTable();
        }
        alarmBanner.refresh(now);
        updateConnectionBadge();
    }

    private boolean isMaraflowSimMode() {
        return project != null
                && project.getCommSettings() != null
                && project.getCommSettings().getMode() == CommSettings.Mode.MARAFLOW_SIM;
    }

    private void pollMaraflowSimAsync() {
        if (!simPollInFlight.compareAndSet(false, true)) {
            return;
        }
        final ScadaProject snapshotProject = this.project;
        simPollExecutor.submit(() -> {
            try {
                MaraflowSimClient.SimStatus status = maraflowSimClient.refreshStatus();
                final MaraflowBridgeTagProvider.Snapshot snap =
                        (snapshotProject != null && status.online() && tagProvider == maraflowTagProvider)
                                ? maraflowTagProvider.fetchSnapshot(snapshotProject)
                                : null;
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (snapshotProject == this.project && status.online()) {
                            boolean changed = false;
                            if (snapshotProject != null) {
                                if (snap != null) {
                                    changed = maraflowTagProvider.applySnapshot(snapshotProject, snap);
                                } else {
                                    tagProvider.syncAll(snapshotProject);
                                    changed = true;
                                }
                            }
                            SimState mapped = mapRemoteSimState(status.simState());
                            if (mapped != currentSimState) {
                                updateSimUiState(mapped);
                                changed = true;
                            }
                            long now = System.currentTimeMillis();
                            alarmEngine.evaluate(project, now);
                            if (changed) {
                                repaintActiveCanvas();
                            }
                            maybeRefreshPeriodicUi(now, changed);
                        } else {
                            maybeRefreshPeriodicUi(System.currentTimeMillis(), false);
                        }
                    } finally {
                        simPollInFlight.set(false);
                    }
                });
            } catch (Exception ex) {
                simPollInFlight.set(false);
            }
        });
    }

    private static SimState mapRemoteSimState(String remoteState) {
        if (remoteState == null) return SimState.STOPPED;
        return switch (remoteState.toUpperCase()) {
            case "RUNNING", "WATCH_MODE" -> SimState.RUNNING;
            case "PAUSED" -> SimState.PAUSED;
            default -> SimState.STOPPED;
        };
    }

    private JMenuBar buildMenuBar() {
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuBar bar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenuItem newItem = new JMenuItem("New Project");
        newItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, menuMask));
        newItem.addActionListener(e -> onNewProject());
        JMenuItem openItem = new JMenuItem("Open .msl...");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, menuMask));
        openItem.addActionListener(e -> onOpenMsl());
        recentMenu = new JMenu("Open Recent");
        rebuildRecentMenu();
        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask));
        saveItem.addActionListener(e -> onSaveMsl(false));
        JMenuItem saveAsItem = new JMenuItem("Save As...");
        saveAsItem.addActionListener(e -> onSaveMsl(true));
        JMenuItem projectSettingsItem = new JMenuItem("Project & Comm Settings...");
        projectSettingsItem.addActionListener(e -> onProjectSettings());
        JMenuItem linkMflItem = new JMenuItem("Link .mfl Project...");
        linkMflItem.addActionListener(e -> onLinkMfl());
        JMenuItem closeProjItem = new JMenuItem("Close Project");
        closeProjItem.addActionListener(e -> {
            if (confirmDiscardUnsavedChanges()) {
                showHomeScreen();
            }
        });
        projectOnlyControls.add(closeProjItem);
        projectOnlyControls.add(saveItem);
        projectOnlyControls.add(saveAsItem);
        projectOnlyControls.add(projectSettingsItem);
        projectOnlyControls.add(linkMflItem);
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> attemptExit());
        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.add(recentMenu);
        fileMenu.add(closeProjItem);
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.addSeparator();
        fileMenu.add(projectSettingsItem);
        fileMenu.add(linkMflItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        JMenu editMenu = new JMenu("Edit");
        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask));
        undoItem.addActionListener(e -> performUndo());
        JMenuItem redoItem = new JMenuItem("Redo");
        redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menuMask));
        redoItem.addActionListener(e -> performRedo());
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
        JMenuItem prefsItem = new JMenuItem("Preferences...");
        prefsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, menuMask));
        prefsItem.addActionListener(e -> onOpenPreferences());
        editMenu.add(undoItem);
        editMenu.add(redoItem);
        editMenu.addSeparator();
        editMenu.add(copyItem);
        editMenu.add(pasteItem);
        editMenu.addSeparator();
        editMenu.add(rotItem);
        editMenu.add(delItem);
        editMenu.addSeparator();
        editMenu.add(prefsItem);

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
        viewGridItem = new JCheckBoxMenuItem("Show Grid Dots", userConfig.isShowGridByDefault());
        viewGridItem.addActionListener(e -> {
            ScadaCanvas c = getActiveCanvas();
            if (c != null && c.getScreen() != null) {
                c.getScreen().setShowGrid(viewGridItem.isSelected());
                project.setDirty(true);
                c.repaint();
            }
        });
        viewFlowItem = new JCheckBoxMenuItem("Animate Pipe Flow", userConfig.isShowFlowAnimation());
        viewFlowItem.addActionListener(e -> {
            userConfig.setShowFlowAnimation(viewFlowItem.isSelected());
            for (int i = 0; i < screenTabs.getTabCount(); i++) {
                if (screenTabs.getComponentAt(i) instanceof JScrollPane sp
                        && sp.getViewport().getView() instanceof ScadaCanvas c) {
                    c.setShowFlowAnimation(viewFlowItem.isSelected());
                }
            }
            saveUserConfigQuietly();
        });
        viewStatusBarItem = new JCheckBoxMenuItem("Show Status Bar", userConfig.isShowStatusBar());
        viewStatusBarItem.addActionListener(e -> {
            userConfig.setShowStatusBar(viewStatusBarItem.isSelected());
            if (statusBar != null) {
                statusBar.setVisible(userConfig.isShowStatusBar());
            }
            saveUserConfigQuietly();
        });
        viewAlarmBannerItem = new JCheckBoxMenuItem("Show Alarm Banner", userConfig.isShowAlarmBanner());
        viewAlarmBannerItem.addActionListener(e -> {
            userConfig.setShowAlarmBanner(viewAlarmBannerItem.isSelected());
            alarmBanner.setVisible(userConfig.isShowAlarmBanner());
            saveUserConfigQuietly();
        });
        viewMenu.add(zoomInItem);
        viewMenu.add(zoomOutItem);
        viewMenu.add(zoom100Item);
        viewMenu.addSeparator();
        viewMenu.add(viewGridItem);
        viewMenu.add(viewFlowItem);
        viewMenu.add(viewStatusBarItem);
        viewMenu.add(viewAlarmBannerItem);

        JMenu screenMenu = new JMenu("Screen");
        projectOnlyControls.add(editMenu);
        projectOnlyControls.add(viewMenu);
        projectOnlyControls.add(screenMenu);
        JMenuItem addScreenItem = new JMenuItem("Add Screen...");
        addScreenItem.addActionListener(e -> onAddScreen());
        JMenuItem screenPropsItem = new JMenuItem("Screen Properties...");
        screenPropsItem.addActionListener(e -> onScreenProperties());
        JMenuItem deleteScreenItem = new JMenuItem("Delete Current Screen");
        deleteScreenItem.addActionListener(e -> onDeleteCurrentScreen());
        screenMenu.add(addScreenItem);
        screenMenu.add(screenPropsItem);
        screenMenu.addSeparator();
        screenMenu.add(deleteScreenItem);

        JMenu tagsMenu = new JMenu("Tags");
        projectOnlyControls.add(tagsMenu);
        JMenuItem importTagsItem = new JMenuItem("Import from Linked .mfl...");
        importTagsItem.addActionListener(e -> onLinkMfl());
        JMenuItem commSettingsItem = new JMenuItem("Communication Settings...");
        commSettingsItem.addActionListener(e -> onCommSettings());
        tagsMenu.add(importTagsItem);
        tagsMenu.addSeparator();
        tagsMenu.add(commSettingsItem);

        JMenu simMenu = new JMenu("Simulation");
        simInternalItem = new JRadioButtonMenuItem("Internal Simulator", true);
        simInternalItem.addActionListener(e -> {
            if (project != null) {
                project.getCommSettings().setMode(CommSettings.Mode.INTERNAL_SIM);
                project.setDirty(true);
                applyCommunicationSettings();
                statusLabel.setText(" Switched to Internal Simulator");
            }
        });
        simMaraflowItem = new JRadioButtonMenuItem("Maraflow Simulator (Live)", false);
        simMaraflowItem.addActionListener(e -> {
            if (project != null) {
                CommSettings comm = project.getCommSettings();
                comm.setMode(CommSettings.Mode.MARAFLOW_SIM);
                if (comm.getTcpPort() == 502) {
                    comm.setTcpPort(CommSettings.DEFAULT_MARAFLOW_SIM_PORT);
                }
                project.setDirty(true);
                applyCommunicationSettings();
                statusLabel.setText(" Switched to Maraflow Simulator (" + comm.getHost() + ":" + comm.getTcpPort() + ")");
            }
        });
        ButtonGroup simSourceGroup = new ButtonGroup();
        simSourceGroup.add(simInternalItem);
        simSourceGroup.add(simMaraflowItem);
        JMenuItem simPlayItem = new JMenuItem("Start / Play");
        simPlayItem.addActionListener(e -> onSimPlay());
        JMenuItem simPauseItem = new JMenuItem("Pause");
        simPauseItem.addActionListener(e -> onSimPause());
        JMenuItem simStopItem = new JMenuItem("Stop");
        simStopItem.addActionListener(e -> onSimStop());
        JMenuItem simCommItem = new JMenuItem("Simulator & Comm Settings...");
        simCommItem.addActionListener(e -> onCommSettings());
        simMenu.add(simInternalItem);
        simMenu.add(simMaraflowItem);
        simMenu.addSeparator();
        simMenu.add(simPlayItem);
        simMenu.add(simPauseItem);
        simMenu.add(simStopItem);
        simMenu.addSeparator();
        simMenu.add(simCommItem);

        JMenu windowMenu = new JMenu("Window");
        JMenuItem homeItem = new JMenuItem("Home Screen");
        homeItem.addActionListener(e -> {
            if (confirmDiscardUnsavedChanges()) {
                showHomeScreen();
            }
        });
        JMenuItem showLibItem = new JMenuItem("Show Library & Tags");
        showLibItem.addActionListener(e -> {
            leftDockSection.setVisible(true);
            mainSplit.setDividerLocation(userConfig.getLeftDockWidth());
        });
        JMenuItem opItem = new JMenuItem("Toggle Operator Mode");
        opItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        opItem.addActionListener(e -> setOperatorMode(!operatorMode));
        windowMenu.add(homeItem);
        windowMenu.addSeparator();
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
        bar.add(simMenu);
        bar.add(windowMenu);
        bar.add(helpMenu);
        return bar;
    }

    private JToolBar buildToolbar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xB8, 0xB8, 0xB8)));
        tb.setMargin(new Insets(2, 4, 2, 4));

        cursorToolBtn = createToggleBtn(ScadaVectorIcons.cursor(), "Cursor / Select Tool");
        cursorToolBtn.setSelected(true);
        JToggleButton pipeBtn = createToggleBtn(ScadaVectorIcons.pipe(), "Pipe / Wire Routing Tool");
        JToggleButton lineBtn = createToggleBtn(ScadaVectorIcons.lineIcon(), "Draw Line");
        JToggleButton rectBtn = createToggleBtn(ScadaVectorIcons.rectIcon(), "Draw Rectangle");
        JToggleButton circleBtn = createToggleBtn(ScadaVectorIcons.circleIcon(), "Draw Circle");
        JToggleButton textBtn = createToggleBtn(ScadaVectorIcons.textIcon(), "Place Text Label");
        ButtonGroup toolGroup = new ButtonGroup();
        toolGroup.add(cursorToolBtn);
        toolGroup.add(pipeBtn);
        toolGroup.add(lineBtn);
        toolGroup.add(rectBtn);
        toolGroup.add(circleBtn);
        toolGroup.add(textBtn);
        cursorToolBtn.addActionListener(e -> setActiveToolMode(ScadaCanvas.ToolMode.CURSOR));
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

        undoBtn = createActionBtn(ScadaVectorIcons.undo(), "Undo");
        undoBtn.addActionListener(e -> performUndo());
        redoBtn = createActionBtn(ScadaVectorIcons.redo(), "Redo");
        redoBtn.addActionListener(e -> performRedo());

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

        JButton prefsBtn = createActionBtn(ScadaVectorIcons.settingsIcon(), "User Preferences (Ctrl+, / Cmd+,)");
        prefsBtn.addActionListener(e -> onOpenPreferences());

        tb.add(cursorToolBtn);
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
        tb.add(prefsBtn);
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
        if (isMaraflowSimMode()) {
            simPollExecutor.submit(() -> {
                boolean ok = maraflowSimClient.startSimulation();
                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        updateSimUiState(SimState.RUNNING);
                        statusLabel.setText(" Maraflow Simulation Running");
                    } else {
                        statusLabel.setText(" Maraflow Simulator Offline (cannot start)");
                    }
                    updateConnectionBadge();
                });
            });
            return;
        }
        if (currentSimState == SimState.STOPPED) {
            restoreInitialTagValues();
        }
        updateSimUiState(SimState.RUNNING);
        statusLabel.setText(" Simulation Running");
    }

    public void onSimPause() {
        if (isMaraflowSimMode()) {
            simPollExecutor.submit(() -> {
                boolean ok = maraflowSimClient.pauseSimulation();
                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        updateSimUiState(SimState.PAUSED);
                        statusLabel.setText(" Maraflow Simulation Paused");
                    }
                    updateConnectionBadge();
                });
            });
            return;
        }
        updateSimUiState(SimState.PAUSED);
        statusLabel.setText(" Simulation Paused");
    }

    public void onSimStop() {
        if (isMaraflowSimMode()) {
            simPollExecutor.submit(() -> {
                boolean ok = maraflowSimClient.stopSimulation();
                if (ok && project != null) {
                    tagProvider.syncAll(project);
                }
                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        updateSimUiState(SimState.STOPPED);
                        refreshTagTable();
                        repaintActiveCanvas();
                        statusLabel.setText(" Maraflow Simulation Stopped");
                    }
                    updateConnectionBadge();
                });
            });
            return;
        }
        resetAllIoAndStates();
        updateSimUiState(SimState.STOPPED);
        statusLabel.setText(userConfig.isResetIoOnSimStop()
                ? " Simulation Stopped (All I/O Reset to 0)"
                : " Simulation Stopped");
    }

    private void performUndo() {
        ScadaCanvas c = getActiveCanvas();
        if (c != null && !c.isOperatorMode() && c.getUndoManager().undo(c.getScreen())) {
            c.markDirty();
            c.repaint();
            updateUndoRedoButtons();
        }
    }

    private void performRedo() {
        ScadaCanvas c = getActiveCanvas();
        if (c != null && !c.isOperatorMode() && c.getUndoManager().redo(c.getScreen())) {
            c.markDirty();
            c.repaint();
            updateUndoRedoButtons();
        }
    }

    private void updateUndoRedoButtons() {
        ScadaCanvas c = getActiveCanvas();
        boolean canUndo = c != null && !c.isOperatorMode() && c.getUndoManager().canUndo();
        boolean canRedo = c != null && !c.isOperatorMode() && c.getUndoManager().canRedo();
        if (undoBtn != null) undoBtn.setEnabled(canUndo);
        if (redoBtn != null) redoBtn.setEnabled(canRedo);
    }

    private void updateProjectControlsEnabled() {
        for (JComponent comp : projectOnlyControls) {
            comp.setEnabled(projectOpen);
        }
    }

    public void onSimStep() {
        if (isMaraflowSimMode()) {
            return;
        }
        if (currentSimState == SimState.STOPPED) {
            restoreInitialTagValues();
        }
        ScadaCanvas active = getActiveCanvas();
        for (int i = 0; i < screenTabs.getTabCount(); i++) {
            if (screenTabs.getComponentAt(i) instanceof JScrollPane sp
                    && sp.getViewport().getView() instanceof ScadaCanvas c) {
                c.stepSimulation();
            }
        }
        if (active != null) {
            updateSimUiState(active.getSimState());
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
            if (userConfig.isResetIoOnSimStop()) {
                for (ScadaTag tag : project.getTags().values()) {
                    tag.setRuntimeValue(0.0);
                    tag.setRuntimeQuality(TagQuality.GOOD);
                }
            }
            if (userConfig.isAcknowledgeAlarmsOnSimStop()) {
                alarmEngine.acknowledgeAll();
            }
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
        if (stepBtn != null) {
            stepBtn.setEnabled(!isMaraflowSimMode());
        }
        simPreviewBtn.setSelected(state != SimState.STOPPED);

        for (int i = 0; i < screenTabs.getTabCount(); i++) {
            if (screenTabs.getComponentAt(i) instanceof JScrollPane sp
                    && sp.getViewport().getView() instanceof ScadaCanvas c) {
                c.setSimState(state);
            }
        }
    }

    private void showHomeScreen() {
        maraflowSimClient.disconnect();
        disposeAllCanvases();
        cz.maramat.scada.render.GraphicElementRenderer.clearImageCache();
        cz.maramat.scada.render.symbols.InstrumentationControlRenderer.clearTrendBuffers();
        this.projectOpen = false;
        updateProjectControlsEnabled();
        updateSimUiState(SimState.STOPPED);
        if (operatorMode) {
            setOperatorMode(false);
        }
        if (homeScreenPanel != null) {
            homeScreenPanel.setRecentPaths(userConfig.getRecentProjects());
        }
        rootCardLayout.show(rootCardPanel, CARD_HOME);
        setTitle("Maraview V" + ScadaProject.VERSION + " - SCADA Designer & Operator Runtime");
        statusLabel.setText(" Ready");
    }

    private void loadProjectIntoUi(ScadaProject newProject) {
        disposeAllCanvases();
        cz.maramat.scada.render.GraphicElementRenderer.clearImageCache();
        cz.maramat.scada.render.symbols.InstrumentationControlRenderer.clearTrendBuffers();
        this.project = newProject;
        this.projectOpen = true;
        updateProjectControlsEnabled();
        applyCommunicationSettings();
        screenTabs.removeAll();
        for (ScadaScreen screen : project.getScreens()) {
            addScreenTab(screen);
        }
        refreshTagTable(true);
        rebuildOperatorScreenButtons();
        rootCardLayout.show(rootCardPanel, CARD_WORKSPACE);
        updateTitle();
        updateUndoRedoButtons();
        if (runtimeSyncTimer != null && !runtimeSyncTimer.isRunning()) {
            runtimeSyncTimer.start();
        }
    }

    private void applyCommunicationSettings() {
        if (project == null) return;
        CommSettings comm = project.getCommSettings();
        if (comm != null && comm.getMode() == CommSettings.Mode.MARAFLOW_SIM) {
            maraflowSimClient.configure(comm.getHost(), comm.getTcpPort());
            this.tagProvider = maraflowTagProvider;
            if (runtimeSyncTimer != null) {
                runtimeSyncTimer.setDelay(comm.getPollIntervalMs());
            }
        } else {
            maraflowSimClient.disconnect();
            this.tagProvider = internalTagProvider;
            if (runtimeSyncTimer != null && userConfig != null) {
                runtimeSyncTimer.setDelay(userConfig.getRuntimeSyncIntervalMs());
            }
        }
        for (int i = 0; i < screenTabs.getTabCount(); i++) {
            if (screenTabs.getComponentAt(i) instanceof JScrollPane sp
                    && sp.getViewport().getView() instanceof ScadaCanvas c) {
                c.setTagProvider(this.tagProvider);
            }
        }
        if (simInternalItem != null && simMaraflowItem != null && comm != null) {
            boolean mf = comm.getMode() == CommSettings.Mode.MARAFLOW_SIM;
            simMaraflowItem.setSelected(mf);
            simInternalItem.setSelected(!mf);
        }
        if (stepBtn != null) {
            stepBtn.setEnabled(!isMaraflowSimMode());
        }
        updateConnectionBadge();
    }

    private void addScreenTab(ScadaScreen screen) {
        ScadaCanvas canvas = new ScadaCanvas(project, screen);
        canvas.applyUserConfig(userConfig);
        canvas.setZoomPercent(userConfig.getDefaultZoomPercent());
        canvas.setTagProvider(tagProvider);
        canvas.setSimState(currentSimState);
        canvas.setOperatorMode(operatorMode);
        canvas.setOnToolModeChanged(() -> {
            if (cursorToolBtn != null) {
                cursorToolBtn.setSelected(true);
            }
        });
        canvas.setOnDirtyCallback(() -> {
            updateTitle();
            updateUndoRedoButtons();
        });
        canvas.setOnZoomChanged(() -> {
            ScadaCanvas active = getActiveCanvas();
            if (active == canvas) {
                zoomLabel.setText(" " + canvas.getZoomPercent() + "% ");
            }
        });
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
        syncViewMenuWithActiveCanvas();
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
        String text;
        Color bg;
        if (isMaraflowSimMode()) {
            MaraflowSimClient.SimStatus st = maraflowSimClient.getLastStatus();
            if (online && st.online()) {
                if (st.eStopActive()) {
                    text = " MARAFLOW SIM: E-STOP ";
                    bg = new Color(0xD9, 0x26, 0x26);
                } else if ("PAUSED".equalsIgnoreCase(st.simState())) {
                    text = " MARAFLOW SIM: PAUSED ";
                    bg = new Color(0xD6, 0x89, 0x10);
                } else {
                    text = " MARAFLOW SIM: " + st.simState() + " ";
                    bg = new Color(0x1E, 0x84, 0x49);
                }
            } else {
                text = " MARAFLOW SIM: OFFLINE ";
                bg = new Color(0xD9, 0x26, 0x26);
            }
        } else {
            text = online ? " INTERNAL SIM " : " OFFLINE ";
            bg = online ? new Color(0x1E, 0x84, 0x49) : new Color(0xD9, 0x26, 0x26);
        }
        connStatusLabel.setText(text);
        connStatusLabel.setBackground(bg);
    }

    private void disposeAllCanvases() {
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
            mainSplit.setDividerLocation(userConfig.getLeftDockWidth());
        } else if (userConfig.isAutoStartSimulationInOperatorMode() && currentSimState != SimState.RUNNING) {
            onSimPlay();
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

    private void onNewProject() {
        onNewProject(false);
    }

    private void onNewProject(boolean preselectMaraflowSim) {
        if (!confirmDiscardUnsavedChanges()) return;
        NewProjectWizardDialog wizard = new NewProjectWizardDialog(this, userConfig, preselectMaraflowSim);
        wizard.setVisible(true);
        ScadaProject created = wizard.getCreatedProject();
        if (created != null) {
            if (created.getProjectFile() != null) {
                userConfig.addRecentProject(created.getProjectFile().getAbsolutePath());
                rebuildRecentMenu();
                saveUserConfigQuietly();
            }
            loadProjectIntoUi(created);
            statusLabel.setText(" Created project " + created.getProjectName());
        }
    }

    private void onAddScreen() {
        String name = JOptionPane.showInputDialog(this, "Screen Name:", "Screen" + (project.getScreens().size() + 1));
        if (name == null || name.isBlank()) return;
        ScadaScreen screen = new ScadaScreen(name.trim());
        screen.setBackgroundColorHex(userConfig.getDefaultScreenBackgroundHex());
        screen.setShowGrid(userConfig.isShowGridByDefault());
        project.getScreens().add(screen);
        addScreenTab(screen);
        screenTabs.setSelectedIndex(screenTabs.getTabCount() - 1);
        rebuildOperatorScreenButtons();
        project.setDirty(true);
    }

    private void onScreenProperties() {
        ScadaCanvas c = getActiveCanvas();
        if (c == null || c.getScreen() == null) return;
        int idx = screenTabs.getSelectedIndex();
        if (ScreenPropertiesDialog.showDialog(this, c.getScreen())) {
            if (idx >= 0) {
                screenTabs.setTitleAt(idx, c.getScreen().getName());
            }
            rebuildOperatorScreenButtons();
            syncViewMenuWithActiveCanvas();
            project.setDirty(true);
            c.repaint();
        }
    }

    private void onDeleteCurrentScreen() {
        if (project == null || project.getScreens().size() <= 1) {
            JOptionPane.showMessageDialog(this, "A project must contain at least one screen.",
                    "Delete Screen", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int idx = screenTabs.getSelectedIndex();
        if (idx < 0 || idx >= project.getScreens().size()) return;
        ScadaScreen scr = project.getScreens().get(idx);
        int res = JOptionPane.showConfirmDialog(this,
                "Delete screen \"" + scr.getName() + "\"?",
                "Delete Screen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res != JOptionPane.YES_OPTION) return;
        if (screenTabs.getComponentAt(idx) instanceof JScrollPane sp
                && sp.getViewport().getView() instanceof ScadaCanvas c) {
            c.dispose();
        }
        project.getScreens().remove(idx);
        screenTabs.removeTabAt(idx);
        rebuildOperatorScreenButtons();
        syncViewMenuWithActiveCanvas();
        project.setDirty(true);
    }

    private void onProjectSettings() {
        if (ProjectSettingsDialog.showDialog(this, project, 0)) {
            applyCommunicationSettings();
            updateTitle();
            statusLabel.setText(" Updated project & communication settings");
        }
    }

    private void onCommSettings() {
        if (ProjectSettingsDialog.showDialog(this, project, 1)) {
            applyCommunicationSettings();
            updateTitle();
            statusLabel.setText(" Updated communication settings (" + project.getCommSettings().getMode() + ")");
        }
    }

    private void onOpenPreferences() {
        UserPreferencesDialog.showDialog(this, userConfig, this::applyUserConfig);
    }

    private void applyUserConfig(UserConfig updated) {
        if (updated == null) return;
        boolean lafChanged = updated.getTheme() != userConfig.getTheme()
                || !updated.getUiFontFamily().equals(userConfig.getUiFontFamily())
                || updated.getUiFontSize() != userConfig.getUiFontSize();
        this.userConfig = updated;

        if (lafChanged) {
            Main.applyLookAndFeel(userConfig);
            FlatLaf.updateUI();
            SwingUtilities.updateComponentTreeUI(this);
            SwingUtilities.updateComponentTreeUI(operatorMode ? quickToolbar : operatorBar);
        }

        runtimeSyncTimer.setDelay(userConfig.getRuntimeSyncIntervalMs());
        alarmBanner.setVisible(userConfig.isShowAlarmBanner());
        alarmBanner.setFlashEnabled(userConfig.isAlarmFlashEnabled());
        if (statusBar != null) {
            statusBar.setVisible(userConfig.isShowStatusBar());
        }
        leftDockSection.setPreferredSize(new Dimension(userConfig.getLeftDockWidth(), 600));
        if (!operatorMode && leftDockSection.isVisible()) {
            mainSplit.setDividerLocation(userConfig.getLeftDockWidth());
        }

        for (int i = 0; i < screenTabs.getTabCount(); i++) {
            if (screenTabs.getComponentAt(i) instanceof JScrollPane sp
                    && sp.getViewport().getView() instanceof ScadaCanvas c) {
                c.applyUserConfig(userConfig);
            }
        }
        syncViewMenuWithActiveCanvas();
        rebuildRecentMenu();
        saveUserConfigQuietly();
        revalidate();
        repaint();
    }

    private void onOpenMsl() {
        if (!confirmDiscardUnsavedChanges()) return;
        JFileChooser fc = createFileChooser("Maraview SCADA Project (*.msl)", "msl");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openProjectFile(fc.getSelectedFile());
        }
    }

    private void openProjectFile(File file) {
        if (file == null) return;
        try {
            ScadaProject loaded = MslProjectSerializer.loadProject(file);
            if (file.getParentFile() != null) {
                userConfig.setLastOpenedDir(file.getParentFile().getAbsolutePath());
            }
            userConfig.addRecentProject(file.getAbsolutePath());
            rebuildRecentMenu();
            saveUserConfigQuietly();
            loadProjectIntoUi(loaded);
            statusLabel.setText(" Opened " + file.getName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to open .msl: " + ex.getMessage(),
                    "Open Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onSaveMsl(boolean saveAs) {
        File target = project.getProjectFile();
        if (saveAs || target == null) {
            JFileChooser fc = createFileChooser("Maraview SCADA Project (*.msl)", "msl");
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            target = fc.getSelectedFile();
            if (!target.getName().toLowerCase().endsWith(".msl")) {
                target = new File(target.getParentFile(), target.getName() + ".msl");
            }
        }
        try {
            MslProjectSerializer.saveProject(project, target);
            if (target.getParentFile() != null) {
                userConfig.setLastOpenedDir(target.getParentFile().getAbsolutePath());
            }
            userConfig.addRecentProject(target.getAbsolutePath());
            rebuildRecentMenu();
            saveUserConfigQuietly();
            updateTitle();
            statusLabel.setText(" Saved " + target.getName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save .msl: " + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onLinkMfl() {
        JFileChooser fc = createFileChooser("Maraflow PLC Project (*.mfl)", "mfl");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File selected = fc.getSelectedFile();
                if (selected.getParentFile() != null) {
                    userConfig.setLastOpenedDir(selected.getParentFile().getAbsolutePath());
                    saveUserConfigQuietly();
                }
                int count = MflTagImporter.importIntoProject(selected, project);
                refreshTagTable();
                statusLabel.setText(" Imported " + count + " tags from " + selected.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to import .mfl: " + ex.getMessage(),
                        "Import Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private JFileChooser createFileChooser(String description, String ext) {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter(description, ext));
        String dir = !userConfig.getLastOpenedDir().isBlank()
                ? userConfig.getLastOpenedDir()
                : userConfig.getDefaultProjectDir();
        if (!dir.isBlank()) {
            File f = new File(dir);
            if (f.isDirectory()) {
                fc.setCurrentDirectory(f);
            }
        }
        return fc;
    }

    private void rebuildRecentMenu() {
        if (recentMenu == null) return;
        recentMenu.removeAll();
        if (userConfig.getRecentProjects().isEmpty()) {
            JMenuItem empty = new JMenuItem("(None)");
            empty.setEnabled(false);
            recentMenu.add(empty);
            return;
        }
        for (String path : userConfig.getRecentProjects()) {
            File f = new File(path);
            JMenuItem item = new JMenuItem(f.getName() + " — " + path);
            item.addActionListener(e -> {
                if (!confirmDiscardUnsavedChanges()) return;
                openProjectFile(f);
            });
            recentMenu.add(item);
        }
        recentMenu.addSeparator();
        JMenuItem clearItem = new JMenuItem("Clear Recent Projects");
        clearItem.addActionListener(e -> {
            userConfig.clearRecentProjects();
            rebuildRecentMenu();
            saveUserConfigQuietly();
        });
        recentMenu.add(clearItem);
    }

    private void syncViewMenuWithActiveCanvas() {
        ScadaCanvas c = getActiveCanvas();
        if (c != null) {
            zoomLabel.setText(" " + c.getZoomPercent() + "% ");
            if (viewGridItem != null && c.getScreen() != null) {
                viewGridItem.setSelected(c.getScreen().isShowGrid());
            }
        }
        if (viewFlowItem != null) {
            viewFlowItem.setSelected(userConfig.isShowFlowAnimation());
        }
        if (viewStatusBarItem != null) {
            viewStatusBarItem.setSelected(userConfig.isShowStatusBar());
        }
        if (viewAlarmBannerItem != null) {
            viewAlarmBannerItem.setSelected(userConfig.isShowAlarmBanner());
        }
    }

    private boolean confirmDiscardUnsavedChanges() {
        if (!projectOpen || !userConfig.isConfirmUnsavedChanges() || project == null || !project.isDirty()) {
            return true;
        }
        int res = JOptionPane.showConfirmDialog(this,
                "Project \"" + project.getProjectName() + "\" has unsaved changes. Save before continuing?",
                "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res == JOptionPane.YES_OPTION) {
            onSaveMsl(false);
            return !project.isDirty();
        }
        return res == JOptionPane.NO_OPTION;
    }

    private void attemptExit() {
        if (!confirmDiscardUnsavedChanges()) return;
        if (userConfig.isRememberWindowBounds()) {
            boolean maximized = (getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0;
            userConfig.setWindowMaximized(maximized);
            if (!maximized) {
                userConfig.setWindowWidth(getWidth());
                userConfig.setWindowHeight(getHeight());
            }
            if (!operatorMode && leftDockSection.isVisible()) {
                userConfig.setLeftDockWidth(mainSplit.getDividerLocation());
            }
        }
        saveUserConfigQuietly();
        if (runtimeSyncTimer != null) {
            runtimeSyncTimer.stop();
        }
        simPollExecutor.shutdownNow();
        maraflowSimClient.close();
        disposeAllCanvases();
        dispose();
    }

    private void saveUserConfigQuietly() {
        try {
            UserConfigStore.save(userConfig);
        } catch (IOException ex) {
            System.err.println("Failed to save user config: " + ex.getMessage());
        }
    }

    private void refreshTagTable() {
        refreshTagTable(false);
    }

    private void refreshTagTable(boolean force) {
        if (!force && (leftDockSection == null || !leftDockSection.isVisible() || leftDockTabs.getSelectedIndex() != 1)) {
            return;
        }
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
        if (!projectOpen || project == null) {
            setTitle("Maraview V" + ScadaProject.VERSION + " - SCADA Designer & Operator Runtime");
            return;
        }
        String dirty = project.isDirty() ? "* " : "";
        String path = (project.getProjectFile() != null) ? project.getProjectFile().getAbsolutePath() : "unsaved";
        String mode = operatorMode ? "[OPERATOR]" : "[DESIGN]";
        setTitle(dirty + project.getProjectName() + " (" + path + ") - Maraview V" + ScadaProject.VERSION + " " + mode);
    }
}
