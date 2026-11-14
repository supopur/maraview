package cz.maramat.scada.ui;

import cz.maramat.scada.config.UserConfig;
import cz.maramat.scada.model.ConnectionType;
import cz.maramat.scada.model.FluidType;
import cz.maramat.scada.model.GridPoint;
import cz.maramat.scada.model.PipeDiameter;
import cz.maramat.scada.model.PipePort;
import cz.maramat.scada.model.ScadaGraphicElement;
import cz.maramat.scada.model.ScadaPipe;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaScreen;
import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.model.SymbolCatalog;
import cz.maramat.scada.render.GraphicElementRenderer;
import cz.maramat.scada.render.PipeRenderer;
import cz.maramat.scada.render.SymbolLayerCache;
import cz.maramat.scada.render.symbols.EquipmentSymbolRenderer;
import cz.maramat.scada.routing.FlowPropagationSolver;
import cz.maramat.scada.routing.FlowState;
import cz.maramat.scada.routing.PipeEdgeDragController;
import cz.maramat.scada.routing.PipeRouter;
import cz.maramat.scada.tags.InternalTagProvider;
import cz.maramat.scada.tags.RuntimeTagProvider;
import cz.maramat.scada.undo.UndoManager50;

import java.awt.AlphaComposite;
import java.awt.GraphicsConfiguration;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import javax.swing.AbstractAction;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Interactive SCADA Designer & Operator canvas with strict integer grid, discrete zoom (50%..300%),
 * MMB pan, marquee selection, UndoManager50, port-to-port PipeRouter, and Blender-style segment dragging.
 */
public class ScadaCanvas extends JComponent implements Scrollable {

    public enum ToolMode {
        CURSOR,
        PIPE,
        DRAW_GRAPHIC,
        PLACE_TEXT
    }

    public enum ResizeHandle {
        NW, N, NE, E, SE, S, SW, W, LINE_START, LINE_END
    }

    public enum SimState {
        STOPPED,
        RUNNING,
        PAUSED
    }

    public static final int[] ZOOM_PERCENTS = {50, 70, 80, 100, 120, 150, 200, 300};
    public static final int[] ZOOM_GRID_PX = {5, 7, 8, 10, 12, 15, 20, 30};
    private static final int DEFAULT_CANVAS_GRID_W = 160;
    private static final int DEFAULT_CANVAS_GRID_H = 100;

    private ScadaProject project;
    private ScadaScreen screen;
    private RuntimeTagProvider tagProvider = new InternalTagProvider();
    private final UndoManager50 undoManager = new UndoManager50();
    private final SymbolLayerCache layerCache = new SymbolLayerCache();
    private SymbolRenderer symbolRenderer = defaultSymbolRenderer();

    private ToolMode toolMode = ToolMode.CURSOR;
    private SimState simState = SimState.STOPPED;
    private boolean operatorMode = false;
    private ScadaSymbolInstance activeMomentarySymbol;
    private boolean showFlowAnimation = true;
    private int zoomIndex = 3; // 100% -> 10px
    private int canvasGridWidth = DEFAULT_CANVAS_GRID_W;
    private int canvasGridHeight = DEFAULT_CANVAS_GRID_H;
    private Color gridDotColor = new Color(0xCC, 0xCC, 0xCC);
    private boolean antiAliasing = true;
    private double flowAnimationSpeedPxPerSec = 36.0;
    private boolean mouseWheelZoom = false;
    private Runnable onStepCallback;
    private Runnable onZoomChanged;
    private Runnable onDirtyCallback;

    private static final List<ScadaSymbolInstance> clipboardSymbols = new ArrayList<>();
    private static final List<ScadaPipe> clipboardPipes = new ArrayList<>();
    private static final List<ScadaGraphicElement> clipboardGraphics = new ArrayList<>();
    private static int pasteCascadeCount = 0;

    private final Set<String> selectedSymbolIds = new HashSet<>();
    private final Set<String> selectedPipeIds = new HashSet<>();
    private final Set<String> selectedGraphicIds = new HashSet<>();

    private ScadaGraphicElement.Kind currentShapeKind = ScadaGraphicElement.Kind.RECTANGLE;
    private ScadaGraphicElement creatingGraphic = null;
    private boolean draggingGraphics = false;

    // Resize state
    private ScadaSymbolInstance resizingSymbol = null;
    private ScadaGraphicElement resizingGraphic = null;
    private ResizeHandle activeResizeHandle = null;
    private int resizeOrigX, resizeOrigY, resizeOrigW, resizeOrigH;
    private boolean resizeSnapshotRecorded = false;

    // Interaction state
    private PortHit hoveredPort;
    private PipeEdgeDragController.SegmentHit hoveredPipeSeg;
    private Point panStartScreen;
    private Point panStartViewPos;

    // Symbol drag
    private boolean draggingSymbols = false;
    private int dragStartGridX;
    private int dragStartGridY;
    private boolean dragSnapshotRecorded = false;

    // Label drag
    private ScadaSymbolInstance draggingLabelSymbol = null;
    private int labelDragStartPxX;
    private int labelDragStartPxY;
    private int labelOrigOffX;
    private int labelOrigOffY;
    private boolean labelDragSnapshotRecorded = false;

    // Marquee selection
    private boolean marqueeActive = false;
    private Point marqueeStartPx;
    private Point marqueeEndPx;

    // Pipe routing drag
    private PortHit routingSourcePort;
    private String routingTeePipeId;
    private GridPoint routingTeeStart;
    private List<GridPoint> rubberBandVertices = List.of();

    // Pipe segment drag
    private ScadaPipe draggingPipe;
    private int draggingSegIndex = -1;
    private boolean draggingSegHoriz = false;
    private int lastSegDragGrid = 0;
    private boolean pipeDragSnapshotRecorded = false;

    // Adaptive animation loop (defaults to 30 FPS; only repaints dirty animated bounds)
    private final Timer animTimer;
    private int animationFps = 30;
    private double flowDashPhasePx = 0.0;
    private long simTimeMs = 0L;
    private long lastTickNs = System.nanoTime();
    private Map<String, FlowState> lastFlowMap = Map.of();

    // Cached static pipe shell layer (rebuilt only on topology/zoom/AA/size changes)
    private BufferedImage staticPipeLayer;
    private int staticPipeLayerW = -1;
    private int staticPipeLayerH = -1;
    private int staticPipeLayerGPx = -1;
    private boolean staticPipeLayerAA = false;
    private long staticPipeLayerHash = 0L;

    // Cached flow-relevant tags & solver result
    private List<ScadaSymbolInstance> cachedOrderedSymbols = List.of();
    private long cachedSymbolOrderHash = 0L;
    private ScadaScreen cachedSymbolOrderScreen;
    private Set<String> cachedFlowRelevantTags = Set.of();
    private ScadaScreen cachedFlowTagsScreen;
    private long cachedFlowTagsPipeHash = 0L;
    private int cachedFlowTagsSymCount = -1;
    private long cachedFlowTagsDesignRev = -1L;
    private long cachedFlowRevision = Long.MIN_VALUE;
    private long cachedFlowTagSignature = Long.MIN_VALUE;
    private long cachedFlowPipeHash = 0L;
    private int cachedFlowSymCount = -1;
    private long cachedFlowDesignRev = -1L;
    private ScadaScreen cachedFlowScreen;
    private RuntimeTagProvider cachedFlowProvider;
    private long designRevision = 0L;
    private final Map<Long, BufferedImage> portSpriteCache = new HashMap<>();

    public record PortHit(ScadaSymbolInstance symbol, PipePort port, PipePort.WorldPort worldPort) {
    }

    public ScadaCanvas(ScadaProject project, ScadaScreen screen) {
        this.project = project;
        this.screen = screen;
        setFocusable(true);
        setOpaque(true);
        updatePreferredSize();
        installMouseHandlers();
        installKeyBindings();
        setupDropTarget();

        this.animTimer = new Timer(1000 / animationFps, e -> {
            long now = System.nanoTime();
            double dt = Math.min(0.1, (now - lastTickNs) / 1_000_000_000.0);
            lastTickNs = now;
            if (simState == SimState.RUNNING && showFlowAnimation) {
                flowDashPhasePx -= dt * flowAnimationSpeedPxPerSec;
                simTimeMs += Math.round(dt * 1000.0);
                if (!isCanvasVisibleAndActive()) {
                    return;
                }
                repaintAnimatedRegions();
            }
        });
    }

    public void setAnimationFps(int fps) {
        int clamped = (fps <= 15) ? 15 : (fps >= 60 ? 60 : 30);
        if (this.animationFps != clamped) {
            this.animationFps = clamped;
            this.animTimer.setDelay(Math.max(16, 1000 / clamped));
        }
    }

    public int getAnimationFps() {
        return animationFps;
    }

    private boolean isCanvasVisibleAndActive() {
        if (!isShowing() || getWidth() <= 0 || getHeight() <= 0) {
            return false;
        }
        java.awt.Window win = SwingUtilities.getWindowAncestor(this);
        if (win instanceof java.awt.Frame frame) {
            return (frame.getExtendedState() & java.awt.Frame.ICONIFIED) == 0;
        }
        return true;
    }

    /**
     * Triggers targeted dirty-rect repaints for changed runtime tags.
     * Full-canvas repaint occurs only when a flow-relevant tag changed.
     */
    public void repaintForChangedTags(Set<String> changedTags) {
        if (screen == null || changedTags == null || changedTags.isEmpty()) {
            return;
        }
        long pipeHash = computePipeTopologyHash(screen.getPipes());
        Set<String> flowTags = getFlowRelevantTags(pipeHash);
        for (String tag : changedTags) {
            if (flowTags.contains(tag)) {
                repaint();
                return;
            }
        }
        int gPx = getGridSizePx();
        int pad = Math.max(24, gPx * 3);
        for (ScadaSymbolInstance sym : screen.getSymbols()) {
            Map<String, String> bindings = sym.getTagBindings();
            if (bindings == null || bindings.isEmpty()) continue;
            boolean hit = false;
            for (String bound : bindings.values()) {
                if (bound != null && changedTags.contains(bound)) {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                int x = sym.getGridX() * gPx - pad;
                int y = sym.getGridY() * gPx - pad;
                int w = sym.getEffectiveGridW() * gPx + pad * 2;
                int h = sym.getEffectiveGridH() * gPx + pad * 2;
                repaint(x, y, w, h);
            }
        }
    }

    private void repaintAnimatedRegions() {
        if (screen == null) return;
        int gPx = getGridSizePx();
        Map<String, FlowState> flow = this.lastFlowMap;
        for (ScadaPipe pipe : screen.getPipes()) {
            FlowState fs = flow.get(pipe.getId());
            if (fs == null || !fs.pressurized() || fs.flowRate() <= 0.0) {
                continue;
            }
            List<GridPoint> verts = pipe.getVertices();
            if (verts.size() < 2) continue;
            int pad = Math.max(10, (int) Math.ceil(pipe.getDiameter().getGridUnits() * gPx) + 6);
            for (int i = 0; i < verts.size() - 1; i++) {
                GridPoint a = verts.get(i);
                GridPoint b = verts.get(i + 1);
                int x1 = Math.min(a.x(), b.x()) * gPx - pad;
                int y1 = Math.min(a.y(), b.y()) * gPx - pad;
                int x2 = Math.max(a.x(), b.x()) * gPx + pad;
                int y2 = Math.max(a.y(), b.y()) * gPx + pad;
                repaint(x1, y1, x2 - x1, y2 - y1);
            }
        }

        for (ScadaSymbolInstance sym : screen.getSymbols()) {
            cz.maramat.scada.render.EquipmentState st =
                    cz.maramat.scada.render.symbols.EquipmentSymbolRenderer.resolveState(sym, project, tagProvider);
            if (st == cz.maramat.scada.render.EquipmentState.RUNNING
                    || st == cz.maramat.scada.render.EquipmentState.FAULT) {
                int pad = 6;
                repaint(sym.getGridX() * gPx - pad, sym.getGridY() * gPx - pad,
                        sym.getEffectiveGridW() * gPx + pad * 2, sym.getEffectiveGridH() * gPx + pad * 2);
            }
        }
    }

    private void syncAnimTimer() {
        if (simState == SimState.RUNNING && showFlowAnimation) {
            if (!animTimer.isRunning()) {
                lastTickNs = System.nanoTime();
                animTimer.start();
            }
        } else {
            if (animTimer.isRunning()) {
                animTimer.stop();
            }
        }
    }

    public void dispose() {
        animTimer.stop();
        layerCache.invalidateAll();
        if (staticPipeLayer != null) {
            staticPipeLayer.flush();
            staticPipeLayer = null;
        }
        for (BufferedImage sprite : portSpriteCache.values()) {
            sprite.flush();
        }
        portSpriteCache.clear();
        undoManager.clear();
        this.screen = null;
        this.project = null;
        this.onStepCallback = null;
        this.onZoomChanged = null;
        this.onToolModeChanged = null;
    }

    private Runnable onToolModeChanged;
    private Runnable onSelectionChanged;

    public void setOnToolModeChanged(Runnable onToolModeChanged) {
        this.onToolModeChanged = onToolModeChanged;
    }

    public void setOnSelectionChanged(Runnable onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
    }

    private void notifySelectionChanged() {
        if (onSelectionChanged != null) {
            onSelectionChanged.run();
        }
    }

    private void resetTransientInteractionState() {
        hoveredPort = null;
        hoveredPipeSeg = null;
        creatingGraphic = null;
        resizingSymbol = null;
        resizingGraphic = null;
        draggingSymbols = false;
        draggingGraphics = false;
        draggingPipe = null;
        marqueeActive = false;
        routingSourcePort = null;
        routingTeePipeId = null;
        routingTeeStart = null;
        rubberBandVertices = List.of();
        setCursor(Cursor.getDefaultCursor());
    }

    public ScadaProject getProject() {
        return project;
    }

    public void setProject(ScadaProject project) {
        this.project = project;
        if (project != null && !project.getScreens().isEmpty()) {
            setScreen(project.getScreens().getFirst());
        }
    }

    public ScadaScreen getScreen() {
        return screen;
    }

    public void setScreen(ScadaScreen screen) {
        this.screen = screen;
        selectedSymbolIds.clear();
        selectedPipeIds.clear();
        selectedGraphicIds.clear();
        resetTransientInteractionState();
        undoManager.clear();
        updatePreferredSize();
        repaint();
    }

    public RuntimeTagProvider getTagProvider() {
        return tagProvider;
    }

    public void setTagProvider(RuntimeTagProvider tagProvider) {
        this.tagProvider = (tagProvider == null) ? new InternalTagProvider() : tagProvider;
    }

    public UndoManager50 getUndoManager() {
        return undoManager;
    }

    public SymbolLayerCache getLayerCache() {
        return layerCache;
    }

    public void setSymbolRenderer(SymbolRenderer symbolRenderer) {
        this.symbolRenderer = (symbolRenderer == null) ? defaultSymbolRenderer() : symbolRenderer;
        repaint();
    }

    public ToolMode getToolMode() {
        return toolMode;
    }

    public void setToolMode(ToolMode toolMode) {
        this.toolMode = (toolMode == null) ? ToolMode.CURSOR : toolMode;
        routingSourcePort = null;
        routingTeePipeId = null;
        creatingGraphic = null;
        rubberBandVertices = List.of();
        repaint();
    }

    public void setShapeTool(ScadaGraphicElement.Kind kind) {
        this.currentShapeKind = (kind == null) ? ScadaGraphicElement.Kind.RECTANGLE : kind;
        this.toolMode = (this.currentShapeKind == ScadaGraphicElement.Kind.TEXT)
                ? ToolMode.PLACE_TEXT : ToolMode.DRAW_GRAPHIC;
        routingSourcePort = null;
        routingTeePipeId = null;
        creatingGraphic = null;
        rubberBandVertices = List.of();
        repaint();
    }

    public Set<String> getSelectedGraphicIds() {
        return selectedGraphicIds;
    }

    public void insertImageFromDialog(int gridX, int gridY) {
        if (operatorMode || screen == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Images (PNG, JPG, GIF)", "png", "jpg", "jpeg", "gif"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        try {
            byte[] bytes = Files.readAllBytes(f.toPath());
            String b64 = Base64.getEncoder().encodeToString(bytes);
            undoManager.recordSnapshot("Insert Image", screen);
            ScadaGraphicElement imgElem = new ScadaGraphicElement(ScadaGraphicElement.Kind.IMAGE,
                    Math.max(0, gridX), Math.max(0, gridY), 12, 9);
            imgElem.setImageBase64(b64);
            imgElem.setImagePath(f.getName());
            screen.getGraphics().add(imgElem);
            selectedSymbolIds.clear();
            selectedPipeIds.clear();
            selectedGraphicIds.clear();
            selectedGraphicIds.add(imgElem.getId());
            markDirty();
            repaint();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load image: " + ex.getMessage(),
                    "Insert Image", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isOperatorMode() {
        return operatorMode;
    }

    public void setOperatorMode(boolean operatorMode) {
        if (!operatorMode) {
            releaseActiveMomentary();
        }
        this.operatorMode = operatorMode;
        if (operatorMode) {
            selectedSymbolIds.clear();
            selectedPipeIds.clear();
            selectedGraphicIds.clear();
            resetTransientInteractionState();
        }
        repaint();
    }

    public boolean isShowFlowAnimation() {
        return showFlowAnimation;
    }

    public void setShowFlowAnimation(boolean showFlowAnimation) {
        this.showFlowAnimation = showFlowAnimation;
        syncAnimTimer();
        repaint();
    }

    public void applyUserConfig(UserConfig config) {
        if (config == null) return;
        this.canvasGridWidth = config.getCanvasGridWidth();
        this.canvasGridHeight = config.getCanvasGridHeight();
        this.gridDotColor = config.getGridDotColor();
        this.antiAliasing = config.isAntiAliasing();
        this.showFlowAnimation = config.isShowFlowAnimation();
        this.flowAnimationSpeedPxPerSec = config.getFlowAnimationSpeedPxPerSec();
        this.mouseWheelZoom = config.isMouseWheelZoom();
        setAnimationFps(config.getAnimationFps());
        syncAnimTimer();
        updatePreferredSize();
        revalidate();
        repaint();
    }

    public void setOnZoomChanged(Runnable onZoomChanged) {
        this.onZoomChanged = onZoomChanged;
    }

    public SimState getSimState() {
        return simState;
    }

    public void setSimState(SimState simState) {
        this.simState = (simState == null) ? SimState.STOPPED : simState;
        if (this.simState == SimState.STOPPED) {
            flowDashPhasePx = 0.0;
            simTimeMs = 0L;
        }
        syncAnimTimer();
        repaint();
    }

    public void setOnStepCallback(Runnable onStepCallback) {
        this.onStepCallback = onStepCallback;
    }

    /**
     * Advances simulation by a single discrete step (useful when stopped/paused in editor mode).
     */
    public void stepSimulation() {
        if (simState == SimState.STOPPED) {
            simState = SimState.PAUSED;
        }
        flowDashPhasePx -= 6.0;
        simTimeMs += 100L;
        if (onStepCallback != null) {
            onStepCallback.run();
        }
        repaint();
    }

    public int getZoomPercent() {
        return ZOOM_PERCENTS[zoomIndex];
    }

    public int getGridSizePx() {
        return ZOOM_GRID_PX[zoomIndex];
    }

    public void setZoomPercent(int percent) {
        int bestIdx = zoomIndex;
        int bestDiff = Integer.MAX_VALUE;
        for (int i = 0; i < ZOOM_PERCENTS.length; i++) {
            int diff = Math.abs(ZOOM_PERCENTS[i] - percent);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestIdx = i;
            }
        }
        if (bestIdx != zoomIndex) {
            zoomIndex = bestIdx;
            updatePreferredSize();
            revalidate();
            repaint();
            if (onZoomChanged != null) onZoomChanged.run();
        }
    }

    public void zoomIn() {
        if (zoomIndex < ZOOM_PERCENTS.length - 1) {
            zoomIndex++;
            updatePreferredSize();
            revalidate();
            repaint();
            if (onZoomChanged != null) onZoomChanged.run();
        }
    }

    public void zoomOut() {
        if (zoomIndex > 0) {
            zoomIndex--;
            updatePreferredSize();
            revalidate();
            repaint();
            if (onZoomChanged != null) onZoomChanged.run();
        }
    }

    public Set<String> getSelectedSymbolIds() {
        return selectedSymbolIds;
    }

    public Set<String> getSelectedPipeIds() {
        return selectedPipeIds;
    }

    public ScadaSymbolInstance getSingleSelectedSymbol() {
        if (screen == null || selectedSymbolIds.size() != 1) return null;
        return screen.findSymbolById(selectedSymbolIds.iterator().next());
    }

    public ScadaPipe getSingleSelectedPipe() {
        if (screen == null || selectedPipeIds.size() != 1) return null;
        String id = selectedPipeIds.iterator().next();
        for (ScadaPipe p : screen.getPipes()) {
            if (p.getId().equals(id)) return p;
        }
        return null;
    }

    public void recordUndoSnapshot(String label) {
        if (screen != null) {
            undoManager.recordSnapshot(label, screen);
        }
    }

    public void refreshAfterExternalEdit() {
        markDirty();
        repaint();
    }

    public void rotateSelected90CW() {
        if (operatorMode || screen == null || selectedSymbolIds.isEmpty()) return;
        undoManager.recordSnapshot("Rotate 90", screen);
        for (ScadaSymbolInstance sym : screen.getSymbols()) {
            if (selectedSymbolIds.contains(sym.getId())) {
                sym.setRotationDeg((sym.getRotationDeg() + 90) % 360);
            }
        }
        reroutePipesForSymbols(selectedSymbolIds);
        markDirty();
        repaint();
    }

    public void flipSelectedHorizontal() {
        if (operatorMode || screen == null || selectedSymbolIds.isEmpty()) return;
        undoManager.recordSnapshot("Flip Horizontal", screen);
        for (ScadaSymbolInstance sym : screen.getSymbols()) {
            if (selectedSymbolIds.contains(sym.getId())) {
                sym.setFlipH(!sym.isFlipH());
            }
        }
        reroutePipesForSymbols(selectedSymbolIds);
        markDirty();
        repaint();
    }

    public void flipSelectedVertical() {
        if (operatorMode || screen == null || selectedSymbolIds.isEmpty()) return;
        undoManager.recordSnapshot("Flip Vertical", screen);
        for (ScadaSymbolInstance sym : screen.getSymbols()) {
            if (selectedSymbolIds.contains(sym.getId())) {
                sym.setFlipV(!sym.isFlipV());
            }
        }
        reroutePipesForSymbols(selectedSymbolIds);
        markDirty();
        repaint();
    }

    public void bringSelectedToFront() {
        if (operatorMode || screen == null || selectedSymbolIds.isEmpty()) return;
        undoManager.recordSnapshot("Bring to Front", screen);
        int maxZ = screen.getSymbols().stream().mapToInt(ScadaSymbolInstance::getZIndex).max().orElse(0);
        for (ScadaSymbolInstance sym : screen.getSymbols()) {
            if (selectedSymbolIds.contains(sym.getId())) {
                sym.setZIndex(++maxZ);
            }
        }
        markDirty();
        repaint();
    }

    public void sendSelectedToBack() {
        if (operatorMode || screen == null || selectedSymbolIds.isEmpty()) return;
        undoManager.recordSnapshot("Send to Back", screen);
        int minZ = screen.getSymbols().stream().mapToInt(ScadaSymbolInstance::getZIndex).min().orElse(0);
        for (ScadaSymbolInstance sym : screen.getSymbols()) {
            if (selectedSymbolIds.contains(sym.getId())) {
                sym.setZIndex(--minZ);
            }
        }
        markDirty();
        repaint();
    }

    public void deleteSelection() {
        if (operatorMode || screen == null) return;
        if (selectedSymbolIds.isEmpty() && selectedPipeIds.isEmpty() && selectedGraphicIds.isEmpty()) return;
        undoManager.recordSnapshot("Delete Selection", screen);
        // Cascade-delete all pipes/wires attached to deleted symbols
        screen.getPipes().removeIf(p -> selectedPipeIds.contains(p.getId())
                || (p.getSourceSymbolId() != null && selectedSymbolIds.contains(p.getSourceSymbolId()))
                || (p.getTargetSymbolId() != null && selectedSymbolIds.contains(p.getTargetSymbolId())));
        screen.getSymbols().removeIf(s -> selectedSymbolIds.contains(s.getId()));
        screen.getGraphics().removeIf(g -> selectedGraphicIds.contains(g.getId()));
        selectedSymbolIds.clear();
        selectedPipeIds.clear();
        selectedGraphicIds.clear();
        markDirty();
        repaint();
    }

    private void updatePreferredSize() {
        int g = getGridSizePx();
        setPreferredSize(new Dimension(canvasGridWidth * g, canvasGridHeight * g));
    }

    public void setOnDirtyCallback(Runnable cb) {
        this.onDirtyCallback = cb;
    }

    private BufferedImage ensureStaticPipeLayer(int gPx, int w, int h, long hash,
                                                boolean simActive, Color bg, boolean showGrid,
                                                Map<String, FlowState> flowMap) {
        if (screen == null || w <= 0 || h <= 0) {
            return null;
        }
        if (staticPipeLayer != null
                && staticPipeLayerW == w
                && staticPipeLayerH == h
                && staticPipeLayerGPx == gPx
                && staticPipeLayerAA == antiAliasing
                && staticPipeLayerHash == hash) {
            return staticPipeLayer;
        }
        if (staticPipeLayer != null && (staticPipeLayerW != w || staticPipeLayerH != h)) {
            staticPipeLayer.flush();
            staticPipeLayer = null;
        }
        if (staticPipeLayer == null) {
            GraphicsConfiguration gc = getGraphicsConfiguration();
            staticPipeLayer = (gc != null)
                    ? gc.createCompatibleImage(w, h, Transparency.OPAQUE)
                    : new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        }
        Graphics2D pg = staticPipeLayer.createGraphics();
        try {
            pg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    antiAliasing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
            pg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // 1. Opaque canvas background
            pg.setColor(bg);
            pg.fillRect(0, 0, w, h);

            // 2. Design-mode grid dots
            if (showGrid) {
                paintGridDots(pg, gPx, w, h);
            }

            // 3. Unselected background graphics
            for (ScadaGraphicElement ge : screen.getGraphics()) {
                if (!operatorMode && selectedGraphicIds.contains(ge.getId())) {
                    continue;
                }
                GraphicElementRenderer.paint(pg, ge, gPx, false);
            }

            // 4. Non-dragged pipes: metallic shell + stationary pressurized fluid / idle wire core
            for (ScadaPipe pipe : screen.getPipes()) {
                if (isActivelyDraggedPipe(pipe)) {
                    continue;
                }
                PipeRenderer.paintPipeStatic(pg, pipe, gPx);
                FlowState fs = flowMap.getOrDefault(pipe.getId(), FlowState.IDLE);
                PipeRenderer.paintPipePressurizedFluid(pg, pipe, gPx, fs, simActive);
            }
        } finally {
            pg.dispose();
        }
        staticPipeLayerW = w;
        staticPipeLayerH = h;
        staticPipeLayerGPx = gPx;
        staticPipeLayerAA = antiAliasing;
        staticPipeLayerHash = hash;
        return staticPipeLayer;
    }

    private boolean isActivelyDraggedPipe(ScadaPipe pipe) {
        if (pipe == draggingPipe) {
            return true;
        }
        if (draggingSymbols && !selectedSymbolIds.isEmpty()) {
            String src = pipe.getSourceSymbolId();
            if (src != null && selectedSymbolIds.contains(src)) {
                return true;
            }
            String tgt = pipe.getTargetSymbolId();
            if (tgt != null && selectedSymbolIds.contains(tgt)) {
                return true;
            }
        }
        return false;
    }

    private long computeStaticLayerHash(List<ScadaPipe> pipes, Map<String, FlowState> flowMap,
                                        boolean simActive, Color bg, boolean showGrid) {
        long h = 1469598103934665603L;
        h = (h ^ (simActive ? 1L : 0L)) * 1099511628211L;
        h = (h ^ (operatorMode ? 1L : 0L)) * 1099511628211L;
        h = (h ^ (showGrid ? 1L : 0L)) * 1099511628211L;
        h = (h ^ (bg != null ? bg.getRGB() : 0)) * 1099511628211L;
        h = (h ^ (gridDotColor != null ? gridDotColor.getRGB() : 0)) * 1099511628211L;

        for (ScadaGraphicElement ge : screen.getGraphics()) {
            boolean sel = !operatorMode && selectedGraphicIds.contains(ge.getId());
            h = (h ^ (sel ? 1L : 0L)) * 1099511628211L;
            if (!sel) {
                h = hashGraphicElement(h, ge);
            }
        }

        for (ScadaPipe p : pipes) {
            boolean dragged = isActivelyDraggedPipe(p);
            h = (h ^ (dragged ? 1L : 0L)) * 1099511628211L;
            if (dragged) continue;
            h = (h ^ p.getDiameter().ordinal()) * 1099511628211L;
            h = (h ^ p.getConnectionType().ordinal()) * 1099511628211L;
            h = (h ^ p.getFluidType().ordinal()) * 1099511628211L;
            List<GridPoint> verts = p.getVertices();
            h = (h ^ verts.size()) * 1099511628211L;
            for (GridPoint gp : verts) {
                h = (h ^ gp.x()) * 1099511628211L;
                h = (h ^ gp.y()) * 1099511628211L;
            }
            FlowState fs = flowMap.get(p.getId());
            boolean wireFlowing = simActive && fs != null && fs.pressurized() && fs.flowRate() > 0.0;
            boolean pressurized = simActive && fs != null && fs.pressurized();
            FluidType effFluid = (fs != null && fs.fluidType() != null) ? fs.fluidType() : p.getFluidType();
            h = (h ^ (wireFlowing ? 1L : 0L)) * 1099511628211L;
            h = (h ^ (pressurized ? 1L : 0L)) * 1099511628211L;
            h = (h ^ (effFluid != null ? effFluid.ordinal() : -1)) * 1099511628211L;
        }
        return h;
    }

    private static long hashGraphicElement(long h, ScadaGraphicElement ge) {
        h = (h ^ Objects.hashCode(ge.getId())) * 1099511628211L;
        h = (h ^ (ge.getKind() != null ? ge.getKind().ordinal() : -1)) * 1099511628211L;
        h = (h ^ ge.getGridX()) * 1099511628211L;
        h = (h ^ ge.getGridY()) * 1099511628211L;
        h = (h ^ ge.getGridW()) * 1099511628211L;
        h = (h ^ ge.getGridH()) * 1099511628211L;
        h = (h ^ (ge.getStrokeColor() != null ? ge.getStrokeColor().getRGB() : 0)) * 1099511628211L;
        h = (h ^ (ge.getFillColor() != null ? ge.getFillColor().getRGB() : 0)) * 1099511628211L;
        h = (h ^ (ge.isFilled() ? 1L : 0L)) * 1099511628211L;
        h = (h ^ ge.getStrokeWidth()) * 1099511628211L;
        h = (h ^ (ge.isDashed() ? 1L : 0L)) * 1099511628211L;
        h = (h ^ ge.getNgonSides()) * 1099511628211L;
        h = (h ^ Objects.hashCode(ge.getText())) * 1099511628211L;
        h = (h ^ ge.getFontSize()) * 1099511628211L;
        h = (h ^ Objects.hashCode(ge.getImagePath())) * 1099511628211L;
        h = (h ^ Objects.hashCode(ge.getImageBase64())) * 1099511628211L;
        h = (h ^ (ge.isPreserveAspectRatio() ? 1L : 0L)) * 1099511628211L;
        h = (h ^ Double.doubleToLongBits(ge.getOpacity())) * 1099511628211L;
        return h;
    }

    private static long computePipeTopologyHash(List<ScadaPipe> pipes) {
        long h = 1469598103934665603L;
        for (ScadaPipe p : pipes) {
            h = (h ^ p.getDiameter().ordinal()) * 1099511628211L;
            h = (h ^ p.getConnectionType().ordinal()) * 1099511628211L;
            List<GridPoint> verts = p.getVertices();
            h = (h ^ verts.size()) * 1099511628211L;
            for (GridPoint gp : verts) {
                h = (h ^ gp.x()) * 1099511628211L;
                h = (h ^ gp.y()) * 1099511628211L;
            }
        }
        return h;
    }

    private Set<String> getFlowRelevantTags(long pipeHash) {
        int symCount = screen.getSymbols().size();
        if (cachedFlowTagsScreen != screen
                || cachedFlowTagsPipeHash != pipeHash
                || cachedFlowTagsSymCount != symCount
                || cachedFlowTagsDesignRev != designRevision) {
            cachedFlowRelevantTags = FlowPropagationSolver.collectFlowRelevantTags(screen);
            cachedFlowTagsScreen = screen;
            cachedFlowTagsPipeHash = pipeHash;
            cachedFlowTagsSymCount = symCount;
            cachedFlowTagsDesignRev = designRevision;
        }
        return cachedFlowRelevantTags;
    }

    private long computeFlowTagSignature(Set<String> relevantTags) {
        long h = 1469598103934665603L;
        if (tagProvider != null && project != null) {
            for (String tagName : relevantTags) {
                double val = tagProvider.readTag(project, tagName);
                h = (h ^ Double.doubleToLongBits(val)) * 1099511628211L;
            }
        }
        return h;
    }

    private Map<String, FlowState> resolveFlowMap(long pipeHash) {
        long rev = (tagProvider != null) ? tagProvider.getRevision() : -1L;
        int symCount = screen.getSymbols().size();
        boolean structuralSame = cachedFlowProvider == tagProvider
                && cachedFlowScreen == screen
                && cachedFlowPipeHash == pipeHash
                && cachedFlowSymCount == symCount
                && cachedFlowDesignRev == designRevision
                && lastFlowMap != null;
        if (structuralSame && rev >= 0L && cachedFlowRevision == rev) {
            return lastFlowMap;
        }
        Set<String> relevantTags = getFlowRelevantTags(pipeHash);
        long sig = computeFlowTagSignature(relevantTags);
        if (structuralSame && cachedFlowTagSignature == sig) {
            cachedFlowRevision = rev;
            return lastFlowMap;
        }
        Map<String, FlowState> solved = FlowPropagationSolver.solve(screen, project, tagProvider);
        cachedFlowProvider = tagProvider;
        cachedFlowScreen = screen;
        cachedFlowRevision = rev;
        cachedFlowTagSignature = sig;
        cachedFlowPipeHash = pipeHash;
        cachedFlowSymCount = symCount;
        cachedFlowDesignRev = designRevision;
        this.lastFlowMap = solved;
        return solved;
    }

    private List<ScadaSymbolInstance> getOrderedSymbols() {
        List<ScadaSymbolInstance> syms = screen.getSymbols();
        long h = 1469598103934665603L;
        h = (h ^ syms.size()) * 1099511628211L;
        for (ScadaSymbolInstance s : syms) {
            h = (h ^ System.identityHashCode(s)) * 1099511628211L;
            h = (h ^ s.getZIndex()) * 1099511628211L;
        }
        if (cachedSymbolOrderScreen != screen || cachedSymbolOrderHash != h) {
            List<ScadaSymbolInstance> copy = new ArrayList<>(syms);
            copy.sort(Comparator.comparingInt(ScadaSymbolInstance::getZIndex));
            cachedOrderedSymbols = copy;
            cachedSymbolOrderHash = h;
            cachedSymbolOrderScreen = screen;
        }
        return cachedOrderedSymbols;
    }

    private static boolean symbolIntersectsClip(ScadaSymbolInstance sym, int gPx, int margin, Rectangle clip) {
        int sx = sym.getGridX() * gPx - margin;
        int sy = sym.getGridY() * gPx - margin;
        int sw = sym.getEffectiveGridW() * gPx + margin * 2;
        int sh = sym.getEffectiveGridH() * gPx + margin * 2 + Math.max(18, gPx * 2);
        return sx < clip.x + clip.width && sx + sw > clip.x
                && sy < clip.y + clip.height && sy + sh > clip.y;
    }

    private static boolean pipeIntersectsClip(ScadaPipe pipe, int gPx, Rectangle clip) {
        List<GridPoint> verts = pipe.getVertices();
        if (verts.isEmpty()) return false;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (GridPoint gp : verts) {
            int px = gp.x() * gPx;
            int py = gp.y() * gPx;
            if (px < minX) minX = px;
            if (py < minY) minY = py;
            if (px > maxX) maxX = px;
            if (py > maxY) maxY = py;
        }
        int pad = Math.max(12, (int) Math.ceil(pipe.getDiameter().getGridUnits() * gPx) + 8);
        return (minX - pad) < clip.x + clip.width && (maxX + pad) > clip.x
                && (minY - pad) < clip.y + clip.height && (maxY + pad) > clip.y;
    }

    private static boolean graphicIntersectsClip(ScadaGraphicElement ge, int gPx, Rectangle clip) {
        int pad = Math.max(12, gPx);
        int gx = ge.normalizedX() * gPx - pad;
        int gy = ge.normalizedY() * gPx - pad;
        int gw = Math.max(1, ge.normalizedW()) * gPx + pad * 2;
        int gh = Math.max(1, ge.normalizedH()) * gPx + pad * 2;
        return gx < clip.x + clip.width && gx + gw > clip.x
                && gy < clip.y + clip.height && gy + gh > clip.y;
    }

    public void markDirty() {
        designRevision++;
        if (project != null) {
            project.setDirty(true);
        }
        if (onDirtyCallback != null) {
            onDirtyCallback.run();
        }
    }

    @Override
    protected void paintComponent(Graphics gRaw) {
        super.paintComponent(gRaw);
        Graphics2D g = (Graphics2D) gRaw.create();
        try {
            boolean simActive = (simState != SimState.STOPPED) && showFlowAnimation;
            long renderTimeMs = (simState == SimState.STOPPED) ? 0L : simTimeMs;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    antiAliasing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    simActive ? RenderingHints.VALUE_RENDER_SPEED : RenderingHints.VALUE_RENDER_QUALITY);

            int gPx = getGridSizePx();
            Rectangle clip = g.getClipBounds();
            Color bg = (screen != null) ? screen.getBackgroundColor() : ScadaScreen.parseHexColor(ScadaScreen.BG_LIGHT_SLATE, Color.LIGHT_GRAY);
            boolean showGrid = !operatorMode && (screen == null || screen.isShowGrid());

            if (screen == null) {
                g.setColor(bg);
                if (clip != null) {
                    g.fillRect(clip.x, clip.y, clip.width, clip.height);
                } else {
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
                if (showGrid) {
                    paintGridDots(g, gPx, getWidth(), getHeight());
                }
                return;
            }

            long pipeHash = computePipeTopologyHash(screen.getPipes());
            Map<String, FlowState> flowMap = resolveFlowMap(pipeHash);
            this.lastFlowMap = flowMap;

            // 0+1: Unified opaque background + grid dots + unselected background graphics + static pipes + stationary pressurized fluid
            long layerHash = computeStaticLayerHash(screen.getPipes(), flowMap, simActive, bg, showGrid);
            BufferedImage baseLayer = ensureStaticPipeLayer(gPx, getWidth(), getHeight(), layerHash, simActive, bg, showGrid, flowMap);
            if (baseLayer != null) {
                if (clip != null) {
                    int x1 = Math.max(0, clip.x);
                    int y1 = Math.max(0, clip.y);
                    int x2 = Math.min(baseLayer.getWidth(), clip.x + clip.width);
                    int y2 = Math.min(baseLayer.getHeight(), clip.y + clip.height);
                    if (x2 > x1 && y2 > y1) {
                        g.drawImage(baseLayer, x1, y1, x2, y2, x1, y1, x2, y2, null);
                    }
                } else {
                    g.drawImage(baseLayer, 0, 0, null);
                }
            }

            // Selected / actively created graphics (unselected graphics live in baseLayer)
            if (!operatorMode) {
                for (ScadaGraphicElement ge : screen.getGraphics()) {
                    if (!selectedGraphicIds.contains(ge.getId())) {
                        continue;
                    }
                    if (clip != null && !graphicIntersectsClip(ge, gPx, clip)) {
                        continue;
                    }
                    GraphicElementRenderer.paint(g, ge, gPx, true);
                }
                if (creatingGraphic != null) {
                    GraphicElementRenderer.paint(g, creatingGraphic, gPx, true);
                }
            }

            // Dynamic flow particles & selection highlights (or full render for actively dragged pipes)
            for (ScadaPipe pipe : screen.getPipes()) {
                if (clip != null && !pipeIntersectsClip(pipe, gPx, clip)) {
                    continue;
                }
                FlowState fs = flowMap.getOrDefault(pipe.getId(), FlowState.IDLE);
                boolean selected = selectedPipeIds.contains(pipe.getId());
                if (isActivelyDraggedPipe(pipe)) {
                    PipeRenderer.paintPipe(g, pipe, gPx, fs, simActive, flowDashPhasePx, selected);
                } else {
                    PipeRenderer.paintPipeFlowParticles(g, pipe, gPx, fs, simActive, flowDashPhasePx, selected);
                }
            }

            // 2. Symbols sorted by zIndex
            int symMargin = Math.max(24, gPx * 3);
            for (ScadaSymbolInstance sym : getOrderedSymbols()) {
                if (clip != null && !symbolIntersectsClip(sym, gPx, symMargin, clip)) {
                    continue;
                }
                symbolRenderer.paintSymbol(g, sym, gPx, project, tagProvider, layerCache, renderTimeMs, operatorMode);
                if (!operatorMode) {
                    paintSymbolPorts(g, sym, gPx);
                    if (selectedSymbolIds.contains(sym.getId())) {
                        paintSymbolSelectionBox(g, sym, gPx);
                    }
                }
            }

            // 2b. Resize handles for single selected resizable symbol or single selected graphic
            if (!operatorMode) {
                if (selectedSymbolIds.size() == 1 && selectedGraphicIds.isEmpty()) {
                    ScadaSymbolInstance selSym = screen.findSymbolById(selectedSymbolIds.iterator().next());
                    if (selSym != null && SymbolCatalog.isResizable(selSym.getSymbolType())) {
                        paintResizeHandles(g, selSym.getGridX() * gPx, selSym.getGridY() * gPx,
                                selSym.getEffectiveGridW() * gPx, selSym.getEffectiveGridH() * gPx, false);
                    }
                } else if (selectedGraphicIds.size() == 1 && selectedSymbolIds.isEmpty()) {
                    ScadaGraphicElement selG = screen.findGraphicById(selectedGraphicIds.iterator().next());
                    if (selG != null) {
                        if (selG.getKind() == ScadaGraphicElement.Kind.LINE) {
                            paintLineHandles(g, selG, gPx);
                        } else {
                            paintResizeHandles(g, selG.normalizedX() * gPx, selG.normalizedY() * gPx,
                                    selG.normalizedW() * gPx, selG.normalizedH() * gPx, false);
                        }
                    }
                }
            }

            // 3. Port snap highlight box (1G x 1G) + pin name pill
            if (!operatorMode && hoveredPort != null) {
                GridPoint pos = hoveredPort.worldPort().position();
                int hx = pos.x() * gPx - gPx / 2;
                int hy = pos.y() * gPx - gPx / 2;
                g.setColor(new Color(0x00, 0x78, 0xD7, 70));
                g.fillRect(hx, hy, gPx, gPx);
                g.setColor(new Color(0x00, 0x78, 0xD7));
                g.setStroke(new BasicStroke(1.5f));
                g.drawRect(hx, hy, gPx, gPx);

                String pinName = hoveredPort.port().label();
                if (pinName == null || pinName.isBlank()) {
                    pinName = hoveredPort.port().id();
                }
                if (pinName != null && !pinName.isBlank()) {
                    Font oldFont = g.getFont();
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                    FontMetrics fm = g.getFontMetrics();
                    int padX = 6;
                    int boxW = fm.stringWidth(pinName) + padX * 2;
                    int boxH = fm.getHeight() + 4;
                    int lx = hx + gPx + 6;
                    int ly = hy + (gPx - boxH) / 2;
                    g.setColor(new Color(28, 34, 42, 230));
                    g.fillRoundRect(lx, ly, boxW, boxH, 6, 6);
                    g.setColor(new Color(0x52, 0xA8, 0xFF));
                    g.setStroke(new BasicStroke(1.0f));
                    g.drawRoundRect(lx, ly, boxW, boxH, 6, 6);
                    g.setColor(Color.WHITE);
                    g.drawString(pinName, lx + padX, ly + fm.getAscent() + 2);
                    g.setFont(oldFont);
                }
            }

            // 4. Rubber-band pipe preview
            if (!operatorMode && rubberBandVertices.size() >= 2) {
                g.setColor(new Color(0x1F, 0x61, 0x8D));
                g.setStroke(new BasicStroke(Math.max(2.0f, gPx * 0.4f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < rubberBandVertices.size() - 1; i++) {
                    GridPoint a = rubberBandVertices.get(i);
                    GridPoint b = rubberBandVertices.get(i + 1);
                    g.drawLine(a.x() * gPx, a.y() * gPx, b.x() * gPx, b.y() * gPx);
                }
            }

            // 5. Marquee selection rectangle
            if (!operatorMode && marqueeActive && marqueeStartPx != null && marqueeEndPx != null) {
                int rx = Math.min(marqueeStartPx.x, marqueeEndPx.x);
                int ry = Math.min(marqueeStartPx.y, marqueeEndPx.y);
                int rw = Math.abs(marqueeEndPx.x - marqueeStartPx.x);
                int rh = Math.abs(marqueeEndPx.y - marqueeStartPx.y);
                g.setColor(new Color(0x00, 0x78, 0xD7, 45));
                g.fillRect(rx, ry, rw, rh);
                g.setColor(new Color(0x00, 0x78, 0xD7));
                g.setStroke(new BasicStroke(1.0f));
                g.drawRect(rx, ry, rw, rh);
            }
        } finally {
            g.dispose();
        }
    }

    private int cachedGridPx = -1;
    private Color cachedGridColor = null;
    private TexturePaint cachedGridPaint = null;

    private void paintGridDots(Graphics2D g, int gPx, int w, int h) {
        if (gPx <= 0 || w <= 0 || h <= 0) return;
        if (cachedGridPaint == null || cachedGridPx != gPx || !Objects.equals(cachedGridColor, gridDotColor)) {
            BufferedImage tile = new BufferedImage(gPx, gPx, BufferedImage.TYPE_INT_ARGB);
            Graphics2D tg = tile.createGraphics();
            tg.setColor(gridDotColor);
            tg.fillRect(0, 0, 1, 1);
            tg.dispose();
            cachedGridPaint = new TexturePaint(tile, new Rectangle(0, 0, gPx, gPx));
            cachedGridPx = gPx;
            cachedGridColor = gridDotColor;
        }
        Rectangle clip = g.getClipBounds();
        if (clip == null) clip = new Rectangle(0, 0, w, h);
        Paint oldPaint = g.getPaint();
        g.setPaint(cachedGridPaint);
        g.fillRect(clip.x, clip.y, clip.width, clip.height);
        g.setPaint(oldPaint);
    }

    private void paintSymbolSelectionBox(Graphics2D g, ScadaSymbolInstance sym, int gPx) {
        int sx = sym.getGridX() * gPx;
        int sy = sym.getGridY() * gPx;
        int sw = sym.getEffectiveGridW() * gPx;
        int sh = sym.getEffectiveGridH() * gPx;
        g.setColor(new Color(0x00, 0x78, 0xD7));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 3f}, 0f));
        g.drawRect(sx - 2, sy - 2, sw + 4, sh + 4);
    }

    private void paintSymbolPorts(Graphics2D g, ScadaSymbolInstance sym, int gPx) {
        int r = Math.max(3, gPx / 3);
        int pad = 2;
        for (PipePort port : sym.getPorts()) {
            PipePort.WorldPort wp = sym.resolveWorldPort(port.id());
            if (wp == null) continue;
            int px = wp.position().x() * gPx;
            int py = wp.position().y() * gPx;
            ConnectionType ct = port.connectionType();
            int kind = (ct == ConnectionType.ELECTRICAL_POWER) ? 0
                    : (ct == ConnectionType.CONTROL_SIGNAL ? 1 : 2);
            BufferedImage sprite = getOrCreatePortSprite(kind, r, pad);
            g.drawImage(sprite, px - r - pad, py - r - pad, null);
        }
    }

    private BufferedImage getOrCreatePortSprite(int kind, int r, int pad) {
        long key = (((long) kind) << 32) | (r & 0xFFFFFFFFL);
        BufferedImage cached = portSpriteCache.get(key);
        if (cached != null) return cached;
        if (portSpriteCache.size() > 64) {
            portSpriteCache.clear();
        }
        int size = (r + pad) * 2;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = img.createGraphics();
        pg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (kind == 0) {
            pg.setColor(new Color(0xFF, 0x98, 0x00));
            pg.fillRect(pad, pad, r * 2, r * 2);
            pg.setColor(new Color(0x4E, 0x26, 0x00));
            pg.setStroke(new BasicStroke(1.0f));
            pg.drawRect(pad, pad, r * 2, r * 2);
        } else if (kind == 1) {
            pg.setColor(new Color(0x00, 0xB0, 0x9B));
            pg.fillOval(pad, pad, r * 2, r * 2);
            pg.setColor(new Color(0x00, 0x4D, 0x40));
            pg.setStroke(new BasicStroke(1.0f));
            pg.drawOval(pad, pad, r * 2, r * 2);
        } else {
            pg.setColor(new Color(0xE1, 0xE8, 0xEF));
            pg.fillOval(pad, pad, r * 2, r * 2);
            pg.setColor(new Color(0x1F, 0x61, 0x8D));
            pg.setStroke(new BasicStroke(1.2f));
            pg.drawOval(pad, pad, r * 2, r * 2);
        }
        pg.dispose();
        portSpriteCache.put(key, img);
        return img;
    }

    private void paintResizeHandles(Graphics2D g, int x, int y, int w, int h, boolean unused) {
        int hs = 6;
        int half = hs / 2;
        int[][] pts = {
                {x, y}, {x + w / 2, y}, {x + w, y},
                {x + w, y + h / 2}, {x + w, y + h}, {x + w / 2, y + h},
                {x, y + h}, {x, y + h / 2}
        };
        for (int[] p : pts) {
            g.setColor(Color.WHITE);
            g.fillRect(p[0] - half, p[1] - half, hs, hs);
            g.setColor(new Color(0x00, 0x78, 0xD7));
            g.setStroke(new BasicStroke(1.0f));
            g.drawRect(p[0] - half, p[1] - half, hs, hs);
        }
    }

    private void paintLineHandles(Graphics2D g, ScadaGraphicElement line, int gPx) {
        int x1 = line.getGridX() * gPx;
        int y1 = line.getGridY() * gPx;
        int x2 = (line.getGridX() + line.getGridW()) * gPx;
        int y2 = (line.getGridY() + line.getGridH()) * gPx;
        int hs = 6;
        int half = hs / 2;
        for (int[] p : new int[][]{{x1, y1}, {x2, y2}}) {
            g.setColor(Color.WHITE);
            g.fillRect(p[0] - half, p[1] - half, hs, hs);
            g.setColor(new Color(0x00, 0x78, 0xD7));
            g.setStroke(new BasicStroke(1.0f));
            g.drawRect(p[0] - half, p[1] - half, hs, hs);
        }
    }

    private ResizeHandle hitTestBoxHandle(int mx, int my, int x, int y, int w, int h) {
        int tol = 6;
        if (Math.abs(mx - x) <= tol && Math.abs(my - y) <= tol) return ResizeHandle.NW;
        if (Math.abs(mx - (x + w / 2)) <= tol && Math.abs(my - y) <= tol) return ResizeHandle.N;
        if (Math.abs(mx - (x + w)) <= tol && Math.abs(my - y) <= tol) return ResizeHandle.NE;
        if (Math.abs(mx - (x + w)) <= tol && Math.abs(my - (y + h / 2)) <= tol) return ResizeHandle.E;
        if (Math.abs(mx - (x + w)) <= tol && Math.abs(my - (y + h)) <= tol) return ResizeHandle.SE;
        if (Math.abs(mx - (x + w / 2)) <= tol && Math.abs(my - (y + h)) <= tol) return ResizeHandle.S;
        if (Math.abs(mx - x) <= tol && Math.abs(my - (y + h)) <= tol) return ResizeHandle.SW;
        if (Math.abs(mx - x) <= tol && Math.abs(my - (y + h / 2)) <= tol) return ResizeHandle.W;
        return null;
    }

    private ScadaGraphicElement hitTestGraphic(double gx, double gy) {
        if (screen == null) return null;
        int igx = (int) Math.floor(gx);
        int igy = (int) Math.floor(gy);
        List<ScadaGraphicElement> list = screen.getGraphics();
        for (int i = list.size() - 1; i >= 0; i--) {
            ScadaGraphicElement g = list.get(i);
            if (g.containsGridPoint(igx, igy)) {
                return g;
            }
        }
        return null;
    }

    private void installMouseHandlers() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    panStartScreen = e.getLocationOnScreen();
                    if (getParent() instanceof JViewport vp) {
                        panStartViewPos = vp.getViewPosition();
                    }
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    return;
                }
                if (operatorMode) {
                    if (SwingUtilities.isLeftMouseButton(e) && screen != null) {
                        int gPx = getGridSizePx();
                        ScadaSymbolInstance hit = hitTestSymbol(e.getX() / (double) gPx, e.getY() / (double) gPx);
                        if (hit != null) {
                            handleOperatorPress(hit);
                        }
                    }
                    return;
                }
                if (screen == null) return;

                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(e)) return;

                int gPx = getGridSizePx();
                double gx = e.getX() / (double) gPx;
                double gy = e.getY() / (double) gPx;
                int igx = Math.round((float) gx);
                int igy = Math.round((float) gy);

                // 0a. Place Text tool
                if (toolMode == ToolMode.PLACE_TEXT) {
                    String txt = JOptionPane.showInputDialog(ScadaCanvas.this, "Enter label text:", "Label");
                    if (txt != null && !txt.isBlank()) {
                        undoManager.recordSnapshot("Add Text Label", screen);
                        ScadaGraphicElement textElem = new ScadaGraphicElement(
                                ScadaGraphicElement.Kind.TEXT, igx, igy, Math.max(4, txt.length()), 2);
                        textElem.setText(txt.trim());
                        screen.getGraphics().add(textElem);
                        selectedSymbolIds.clear();
                        selectedPipeIds.clear();
                        selectedGraphicIds.clear();
                        selectedGraphicIds.add(textElem.getId());
                        markDirty();
                    }
                    toolMode = ToolMode.CURSOR;
                    if (onToolModeChanged != null) onToolModeChanged.run();
                    repaint();
                    return;
                }

                // 0b. Draw Graphic Shape tool
                if (toolMode == ToolMode.DRAW_GRAPHIC) {
                    creatingGraphic = new ScadaGraphicElement(currentShapeKind, igx, igy, 1, 1);
                    repaint();
                    return;
                }

                // 0c. Resize handle hit on single selected resizable symbol or graphic
                if (selectedSymbolIds.size() == 1 && selectedGraphicIds.isEmpty()) {
                    ScadaSymbolInstance selSym = screen.findSymbolById(selectedSymbolIds.iterator().next());
                    if (selSym != null && SymbolCatalog.isResizable(selSym.getSymbolType())) {
                        ResizeHandle rh = hitTestBoxHandle(e.getX(), e.getY(),
                                selSym.getGridX() * gPx, selSym.getGridY() * gPx,
                                selSym.getEffectiveGridW() * gPx, selSym.getEffectiveGridH() * gPx);
                        if (rh != null) {
                            resizingSymbol = selSym;
                            activeResizeHandle = rh;
                            resizeOrigX = selSym.getGridX();
                            resizeOrigY = selSym.getGridY();
                            resizeOrigW = selSym.getGridW();
                            resizeOrigH = selSym.getGridH();
                            dragStartGridX = igx;
                            dragStartGridY = igy;
                            resizeSnapshotRecorded = false;
                            return;
                        }
                    }
                } else if (selectedGraphicIds.size() == 1 && selectedSymbolIds.isEmpty()) {
                    ScadaGraphicElement selG = screen.findGraphicById(selectedGraphicIds.iterator().next());
                    if (selG != null) {
                        ResizeHandle rh;
                        if (selG.getKind() == ScadaGraphicElement.Kind.LINE) {
                            int x1 = selG.getGridX() * gPx;
                            int y1 = selG.getGridY() * gPx;
                            int x2 = (selG.getGridX() + selG.getGridW()) * gPx;
                            int y2 = (selG.getGridY() + selG.getGridH()) * gPx;
                            if (Math.abs(e.getX() - x1) <= 6 && Math.abs(e.getY() - y1) <= 6) {
                                rh = ResizeHandle.LINE_START;
                            } else if (Math.abs(e.getX() - x2) <= 6 && Math.abs(e.getY() - y2) <= 6) {
                                rh = ResizeHandle.LINE_END;
                            } else {
                                rh = null;
                            }
                        } else {
                            rh = hitTestBoxHandle(e.getX(), e.getY(),
                                    selG.normalizedX() * gPx, selG.normalizedY() * gPx,
                                    selG.normalizedW() * gPx, selG.normalizedH() * gPx);
                        }
                        if (rh != null) {
                            resizingGraphic = selG;
                            activeResizeHandle = rh;
                            resizeOrigX = selG.getGridX();
                            resizeOrigY = selG.getGridY();
                            resizeOrigW = selG.getGridW();
                            resizeOrigH = selG.getGridH();
                            dragStartGridX = igx;
                            dragStartGridY = igy;
                            resizeSnapshotRecorded = false;
                            return;
                        }
                    }
                }

                // 1. Port hit starts pipe/wire routing
                PortHit portHit = hitTestPort(gx, gy, 0.7);
                if (portHit != null) {
                    routingSourcePort = portHit;
                    routingTeePipeId = null;
                    rubberBandVertices = List.of(portHit.worldPort().position());
                    return;
                }

                // 2. Pipe segment hit (selectable in Cursor or Pipe mode; double-click opens Pipe Properties)
                PipeEdgeDragController.SegmentHit segHit = PipeEdgeDragController.hitTest(screen.getPipes(), gx, gy, 0.65);
                if (segHit != null && (toolMode == ToolMode.CURSOR || toolMode == ToolMode.PIPE)) {
                    selectedSymbolIds.clear();
                    selectedGraphicIds.clear();
                    selectedPipeIds.clear();
                    selectedPipeIds.add(segHit.pipe().getId());
                    notifySelectionChanged();
                    if (e.getClickCount() == 2) {
                        openPipeProperties(segHit.pipe());
                        return;
                    }
                    draggingPipe = segHit.pipe();
                    draggingSegIndex = segHit.segmentIndex();
                    draggingSegHoriz = segHit.horizontal();
                    lastSegDragGrid = draggingSegHoriz ? Math.round((float) gy) : Math.round((float) gx);
                    pipeDragSnapshotRecorded = false;
                    repaint();
                    return;
                }

                // 2b. Symbol label hit (drag to reposition label)
                ScadaSymbolInstance labelHit = (toolMode == ToolMode.CURSOR) ? hitTestSymbolLabel(e.getX(), e.getY()) : null;
                if (labelHit != null) {
                    selectedPipeIds.clear();
                    selectedGraphicIds.clear();
                    selectedSymbolIds.clear();
                    selectedSymbolIds.add(labelHit.getId());
                    notifySelectionChanged();
                    if (e.getClickCount() == 2) {
                        openSymbolProperties(labelHit);
                        return;
                    }
                    draggingLabelSymbol = labelHit;
                    labelDragStartPxX = e.getX();
                    labelDragStartPxY = e.getY();
                    labelOrigOffX = labelHit.getLabelOffsetX();
                    labelOrigOffY = labelHit.getLabelOffsetY();
                    labelDragSnapshotRecorded = false;
                    repaint();
                    return;
                }

                // 3. Symbol hit
                ScadaSymbolInstance symHit = hitTestSymbol(gx, gy);
                if (symHit != null) {
                    if (e.getClickCount() == 2) {
                        openSymbolProperties(symHit);
                        return;
                    }
                    selectedPipeIds.clear();
                    if (e.isShiftDown()) {
                        if (!selectedSymbolIds.add(symHit.getId())) {
                            selectedSymbolIds.remove(symHit.getId());
                        }
                    } else if (!selectedSymbolIds.contains(symHit.getId())) {
                        selectedSymbolIds.clear();
                        selectedGraphicIds.clear();
                        selectedSymbolIds.add(symHit.getId());
                    }
                    notifySelectionChanged();
                    if (simState == SimState.RUNNING && toolMode == ToolMode.CURSOR && !e.isShiftDown()
                            && isOperatorInteractive(symHit.getSymbolType())) {
                        handleOperatorPress(symHit);
                    }
                    draggingSymbols = true;
                    dragStartGridX = Math.round((float) gx);
                    dragStartGridY = Math.round((float) gy);
                    dragSnapshotRecorded = false;
                    repaint();
                    return;
                }

                // 3b. Graphic element / picture hit
                ScadaGraphicElement gHit = hitTestGraphic(gx, gy);
                if (gHit != null) {
                    if (e.getClickCount() == 2) {
                        UndoManager50.Snapshot before = UndoManager50.capture("Edit Graphic Properties", screen);
                        if (GraphicElementPropertiesDialog.showDialog(
                                SwingUtilities.getWindowAncestor(ScadaCanvas.this), gHit)) {
                            undoManager.recordSnapshot(before);
                            markDirty();
                            repaint();
                        }
                        return;
                    }
                    selectedSymbolIds.clear();
                    selectedPipeIds.clear();
                    if (e.isShiftDown()) {
                        if (!selectedGraphicIds.add(gHit.getId())) {
                            selectedGraphicIds.remove(gHit.getId());
                        }
                    } else if (!selectedGraphicIds.contains(gHit.getId())) {
                        selectedGraphicIds.clear();
                        selectedGraphicIds.add(gHit.getId());
                    }
                    draggingGraphics = true;
                    dragStartGridX = Math.round((float) gx);
                    dragStartGridY = Math.round((float) gy);
                    dragSnapshotRecorded = false;
                    repaint();
                    return;
                }

                // 4. Empty space -> marquee selection
                if (!e.isShiftDown()) {
                    selectedSymbolIds.clear();
                    selectedPipeIds.clear();
                    selectedGraphicIds.clear();
                }
                marqueeActive = true;
                marqueeStartPx = e.getPoint();
                marqueeEndPx = e.getPoint();
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (panStartScreen != null && panStartViewPos != null && getParent() instanceof JViewport vp) {
                    Point cur = e.getLocationOnScreen();
                    int dx = panStartScreen.x - cur.x;
                    int dy = panStartScreen.y - cur.y;
                    int nx = Math.max(0, Math.min(getWidth() - vp.getWidth(), panStartViewPos.x + dx));
                    int ny = Math.max(0, Math.min(getHeight() - vp.getHeight(), panStartViewPos.y + dy));
                    vp.setViewPosition(new Point(nx, ny));
                    return;
                }
                if (operatorMode) {
                    return;
                }
                if (screen == null) return;

                int gPx = getGridSizePx();
                double gx = e.getX() / (double) gPx;
                double gy = e.getY() / (double) gPx;
                int igx = Math.round((float) gx);
                int igy = Math.round((float) gy);

                if (creatingGraphic != null) {
                    creatingGraphic.setGridW(igx - creatingGraphic.getGridX());
                    creatingGraphic.setGridH(igy - creatingGraphic.getGridY());
                    repaint();
                    return;
                }

                if (resizingSymbol != null && activeResizeHandle != null) {
                    int dx = igx - dragStartGridX;
                    int dy = igy - dragStartGridY;
                    if (!resizeSnapshotRecorded && (dx != 0 || dy != 0)) {
                        undoManager.recordSnapshot("Resize Element", screen);
                        resizeSnapshotRecorded = true;
                    }
                    int minW = SymbolCatalog.getMinGridW(resizingSymbol.getSymbolType());
                    int minH = SymbolCatalog.getMinGridH(resizingSymbol.getSymbolType());
                    int nx = resizeOrigX;
                    int ny = resizeOrigY;
                    int nw = resizeOrigW;
                    int nh = resizeOrigH;
                    switch (activeResizeHandle) {
                        case NW -> { nx += dx; ny += dy; nw -= dx; nh -= dy; }
                        case N -> { ny += dy; nh -= dy; }
                        case NE -> { ny += dy; nw += dx; nh -= dy; }
                        case E -> nw += dx;
                        case SE -> { nw += dx; nh += dy; }
                        case S -> nh += dy;
                        case SW -> { nx += dx; nw -= dx; nh += dy; }
                        case W -> { nx += dx; nw -= dx; }
                        default -> {}
                    }
                    if (nw < minW) {
                        if (activeResizeHandle == ResizeHandle.NW || activeResizeHandle == ResizeHandle.W || activeResizeHandle == ResizeHandle.SW) {
                            nx = resizeOrigX + (resizeOrigW - minW);
                        }
                        nw = minW;
                    }
                    if (nh < minH) {
                        if (activeResizeHandle == ResizeHandle.NW || activeResizeHandle == ResizeHandle.N || activeResizeHandle == ResizeHandle.NE) {
                            ny = resizeOrigY + (resizeOrigH - minH);
                        }
                        nh = minH;
                    }
                    resizingSymbol.setGridX(Math.max(0, nx));
                    resizingSymbol.setGridY(Math.max(0, ny));
                    resizingSymbol.setGridW(nw);
                    resizingSymbol.setGridH(nh);
                    SymbolCatalog.rebuildPortsForSize(resizingSymbol);
                    reroutePipesForSymbols(Set.of(resizingSymbol.getId()));
                    markDirty();
                    repaint();
                    return;
                }

                if (resizingGraphic != null && activeResizeHandle != null) {
                    int dx = igx - dragStartGridX;
                    int dy = igy - dragStartGridY;
                    if (!resizeSnapshotRecorded && (dx != 0 || dy != 0)) {
                        undoManager.recordSnapshot("Resize Graphic", screen);
                        resizeSnapshotRecorded = true;
                    }
                    if (activeResizeHandle == ResizeHandle.LINE_START) {
                        resizingGraphic.setGridX(resizeOrigX + dx);
                        resizingGraphic.setGridY(resizeOrigY + dy);
                        resizingGraphic.setGridW(resizeOrigW - dx);
                        resizingGraphic.setGridH(resizeOrigH - dy);
                    } else if (activeResizeHandle == ResizeHandle.LINE_END) {
                        resizingGraphic.setGridW(resizeOrigW + dx);
                        resizingGraphic.setGridH(resizeOrigH + dy);
                    } else {
                        int nx = resizeOrigX;
                        int ny = resizeOrigY;
                        int nw = resizeOrigW;
                        int nh = resizeOrigH;
                        switch (activeResizeHandle) {
                            case NW -> { nx += dx; ny += dy; nw -= dx; nh -= dy; }
                            case N -> { ny += dy; nh -= dy; }
                            case NE -> { ny += dy; nw += dx; nh -= dy; }
                            case E -> nw += dx;
                            case SE -> { nw += dx; nh += dy; }
                            case S -> nh += dy;
                            case SW -> { nx += dx; nw -= dx; nh += dy; }
                            case W -> { nx += dx; nw -= dx; }
                            default -> {}
                        }
                        if (nw < 1) {
                            if (activeResizeHandle == ResizeHandle.NW || activeResizeHandle == ResizeHandle.W || activeResizeHandle == ResizeHandle.SW) {
                                nx = resizeOrigX + (resizeOrigW - 1);
                            }
                            nw = 1;
                        }
                        if (nh < 1) {
                            if (activeResizeHandle == ResizeHandle.NW || activeResizeHandle == ResizeHandle.N || activeResizeHandle == ResizeHandle.NE) {
                                ny = resizeOrigY + (resizeOrigH - 1);
                            }
                            nh = 1;
                        }
                        resizingGraphic.setGridX(Math.max(0, nx));
                        resizingGraphic.setGridY(Math.max(0, ny));
                        resizingGraphic.setGridW(nw);
                        resizingGraphic.setGridH(nh);
                    }
                    markDirty();
                    repaint();
                    return;
                }

                if (routingSourcePort != null) {
                    PortHit target = hitTestPort(gx, gy, 0.8);
                    boolean compatible = target != null
                            && !target.symbol().getId().equals(routingSourcePort.symbol().getId())
                            && routingSourcePort.port().connectionType().isCompatibleWith(target.port().connectionType());
                    hoveredPort = compatible ? target : null;
                    if (compatible) {
                        rubberBandVertices = PipeRouter.route(routingSourcePort.worldPort(), target.worldPort());
                    } else {
                        rubberBandVertices = PipeRouter.routeToPoint(routingSourcePort.worldPort(), new GridPoint(igx, igy));
                    }
                    repaint();
                    return;
                }

                if (routingTeePipeId != null && routingTeeStart != null) {
                    PortHit target = hitTestPort(gx, gy, 0.8);
                    ScadaPipe parentPipe = screen.findPipeById(routingTeePipeId);
                    boolean compatible = target != null && (parentPipe == null
                            || parentPipe.getConnectionType().isCompatibleWith(target.port().connectionType()));
                    hoveredPort = compatible ? target : null;
                    GridPoint endPt = compatible ? target.worldPort().position() : new GridPoint(igx, igy);
                    rubberBandVertices = List.of(routingTeeStart, new GridPoint(endPt.x(), routingTeeStart.y()), endPt);
                    repaint();
                    return;
                }

                if (draggingPipe != null) {
                    int curGrid = draggingSegHoriz ? igy : igx;
                    int delta = curGrid - lastSegDragGrid;
                    if (delta != 0) {
                        if (!pipeDragSnapshotRecorded) {
                            undoManager.recordSnapshot("Move Pipe Segment", screen);
                            pipeDragSnapshotRecorded = true;
                        }
                        draggingSegIndex = PipeEdgeDragController.moveSegment(draggingPipe, draggingSegIndex, delta);
                        lastSegDragGrid = curGrid;
                        markDirty();
                        repaint();
                    }
                    return;
                }

                if (draggingLabelSymbol != null) {
                    int dxPx = e.getX() - labelDragStartPxX;
                    int dyPx = e.getY() - labelDragStartPxY;
                    if (dxPx != 0 || dyPx != 0) {
                        if (!labelDragSnapshotRecorded) {
                            undoManager.recordSnapshot("Move Label", screen);
                            labelDragSnapshotRecorded = true;
                        }
                        draggingLabelSymbol.setLabelOffsetX(labelOrigOffX + dxPx);
                        draggingLabelSymbol.setLabelOffsetY(labelOrigOffY + dyPx);
                        markDirty();
                        repaint();
                    }
                    return;
                }

                if (draggingSymbols) {
                    int dx = igx - dragStartGridX;
                    int dy = igy - dragStartGridY;
                    if (dx != 0 || dy != 0) {
                        if (!dragSnapshotRecorded) {
                            undoManager.recordSnapshot("Move Symbol", screen);
                            dragSnapshotRecorded = true;
                        }
                        for (ScadaSymbolInstance sym : screen.getSymbols()) {
                            if (selectedSymbolIds.contains(sym.getId())) {
                                sym.setGridX(sym.getGridX() + dx);
                                sym.setGridY(sym.getGridY() + dy);
                            }
                        }
                        translateOrReroutePipes(selectedSymbolIds, dx, dy);
                        dragStartGridX = igx;
                        dragStartGridY = igy;
                        markDirty();
                        repaint();
                    }
                    return;
                }

                if (draggingGraphics) {
                    int dx = igx - dragStartGridX;
                    int dy = igy - dragStartGridY;
                    if (dx != 0 || dy != 0) {
                        if (!dragSnapshotRecorded) {
                            undoManager.recordSnapshot("Move Graphic", screen);
                            dragSnapshotRecorded = true;
                        }
                        for (ScadaGraphicElement ge : screen.getGraphics()) {
                            if (selectedGraphicIds.contains(ge.getId())) {
                                ge.setGridX(ge.getGridX() + dx);
                                ge.setGridY(ge.getGridY() + dy);
                            }
                        }
                        dragStartGridX = igx;
                        dragStartGridY = igy;
                        markDirty();
                        repaint();
                    }
                    return;
                }

                if (marqueeActive) {
                    marqueeEndPx = e.getPoint();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    panStartScreen = null;
                    panStartViewPos = null;
                    setCursor(Cursor.getDefaultCursor());
                    return;
                }
                if (operatorMode) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        releaseActiveMomentary();
                    }
                    return;
                }
                if (screen == null) return;
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                    return;
                }

                int gPx = getGridSizePx();
                double gx = e.getX() / (double) gPx;
                double gy = e.getY() / (double) gPx;

                if (creatingGraphic != null) {
                    if (creatingGraphic.getKind() != ScadaGraphicElement.Kind.LINE) {
                        int nx = creatingGraphic.normalizedX();
                        int ny = creatingGraphic.normalizedY();
                        int nw = Math.max(2, Math.abs(creatingGraphic.getGridW()));
                        int nh = Math.max(2, Math.abs(creatingGraphic.getGridH()));
                        creatingGraphic.setGridX(nx);
                        creatingGraphic.setGridY(ny);
                        creatingGraphic.setGridW(nw);
                        creatingGraphic.setGridH(nh);
                    }
                    undoManager.recordSnapshot("Draw " + creatingGraphic.getKind().getDisplayName(), screen);
                    screen.getGraphics().add(creatingGraphic);
                    selectedSymbolIds.clear();
                    selectedPipeIds.clear();
                    selectedGraphicIds.clear();
                    selectedGraphicIds.add(creatingGraphic.getId());
                    creatingGraphic = null;
                    toolMode = ToolMode.CURSOR;
                    if (onToolModeChanged != null) onToolModeChanged.run();
                    markDirty();
                    repaint();
                    return;
                }

                if (resizingSymbol != null || resizingGraphic != null) {
                    resizingSymbol = null;
                    resizingGraphic = null;
                    activeResizeHandle = null;
                    repaint();
                    return;
                }

                if (routingSourcePort != null) {
                    PortHit target = hitTestPort(gx, gy, 0.85);
                    if (target != null && !target.symbol().getId().equals(routingSourcePort.symbol().getId())
                            && routingSourcePort.port().connectionType().isCompatibleWith(target.port().connectionType())) {
                        undoManager.recordSnapshot("Route " + routingSourcePort.port().connectionType().getDisplayName(), screen);
                        ScadaPipe pipe = new ScadaPipe();
                        pipe.setConnectionType(routingSourcePort.port().connectionType());
                        pipe.setSourceSymbolId(routingSourcePort.symbol().getId());
                        pipe.setSourcePortId(routingSourcePort.port().id());
                        pipe.setTargetSymbolId(target.symbol().getId());
                        pipe.setTargetPortId(target.port().id());
                        pipe.setVertices(PipeRouter.route(routingSourcePort.worldPort(), target.worldPort()));
                        screen.getPipes().add(pipe);
                        markDirty();
                    }
                    routingSourcePort = null;
                    rubberBandVertices = List.of();
                    hoveredPort = null;
                    repaint();
                    return;
                }

                if (routingTeePipeId != null) {
                    PortHit target = hitTestPort(gx, gy, 0.85);
                    if (target != null && rubberBandVertices.size() >= 2) {
                        undoManager.recordSnapshot("Create Tee Branch", screen);
                        ScadaPipe pipe = new ScadaPipe();
                        pipe.setSourceTeePipeId(routingTeePipeId);
                        for (ScadaPipe existing : screen.getPipes()) {
                            if (routingTeePipeId.equals(existing.getId())) {
                                pipe.setConnectionType(existing.getConnectionType());
                                pipe.setFluidType(existing.getFluidType());
                                break;
                            }
                        }
                        pipe.setTargetSymbolId(target.symbol().getId());
                        pipe.setTargetPortId(target.port().id());
                        pipe.setVertices(PipeRouter.simplify(rubberBandVertices));
                        screen.getPipes().add(pipe);
                        markDirty();
                    }
                    routingTeePipeId = null;
                    routingTeeStart = null;
                    rubberBandVertices = List.of();
                    repaint();
                    return;
                }

                if (draggingPipe != null) {
                    if (pipeDragSnapshotRecorded) {
                        PipeEdgeDragController.finalizeSegmentDrag(draggingPipe);
                    }
                    draggingPipe = null;
                    draggingSegIndex = -1;
                    repaint();
                    return;
                }

                draggingLabelSymbol = null;
                draggingSymbols = false;
                draggingGraphics = false;

                if (marqueeActive && marqueeStartPx != null && marqueeEndPx != null) {
                    int mw = Math.abs(marqueeEndPx.x - marqueeStartPx.x);
                    int mh = Math.abs(marqueeEndPx.y - marqueeStartPx.y);
                    if (mw >= 4 || mh >= 4) {
                        Rectangle rect = new Rectangle(
                                Math.min(marqueeStartPx.x, marqueeEndPx.x),
                                Math.min(marqueeStartPx.y, marqueeEndPx.y),
                                mw, mh
                        );
                        for (ScadaSymbolInstance sym : screen.getSymbols()) {
                            Rectangle symRect = new Rectangle(
                                    sym.getGridX() * gPx, sym.getGridY() * gPx,
                                    sym.getEffectiveGridW() * gPx, sym.getEffectiveGridH() * gPx
                            );
                            if (rect.intersects(symRect)) {
                                selectedSymbolIds.add(sym.getId());
                            }
                        }
                        for (ScadaGraphicElement ge : screen.getGraphics()) {
                            Rectangle gRect = new Rectangle(
                                    ge.getGridX() * gPx, ge.getGridY() * gPx,
                                    Math.max(1, ge.getGridW()) * gPx, Math.max(1, ge.getGridH()) * gPx
                            );
                            if (rect.intersects(gRect)) {
                                selectedGraphicIds.add(ge.getId());
                            }
                        }
                        for (ScadaPipe pipe : screen.getPipes()) {
                            for (GridPoint gp : pipe.getVertices()) {
                                if (rect.contains(gp.x() * gPx, gp.y() * gPx)) {
                                    selectedPipeIds.add(pipe.getId());
                                    break;
                                }
                            }
                        }
                    }
                    marqueeActive = false;
                    marqueeStartPx = null;
                    marqueeEndPx = null;
                    notifySelectionChanged();
                    repaint();
                } else {
                    notifySelectionChanged();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (screen == null) return;
                int gPx = getGridSizePx();
                double gx = e.getX() / (double) gPx;
                double gy = e.getY() / (double) gPx;
                if (operatorMode) {
                    setToolTipText(null);
                    ScadaSymbolInstance hit = hitTestSymbol(gx, gy);
                    setCursor(hit != null && isOperatorInteractive(hit.getSymbolType())
                            ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                            : Cursor.getDefaultCursor());
                    return;
                }

                PortHit nextPort = hitTestPort(gx, gy, 0.7);
                if (nextPort != hoveredPort) {
                    hoveredPort = nextPort;
                    repaint();
                }

                if (hoveredPort != null) {
                    String label = hoveredPort.port().label();
                    String id = hoveredPort.port().id();
                    setToolTipText((label != null && !label.isBlank()) ? label + " (" + id + ")" : id);
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    return;
                }
                setToolTipText(null);

                hoveredPipeSeg = PipeEdgeDragController.hitTest(screen.getPipes(), gx, gy, 0.65);
                if (hoveredPipeSeg != null && toolMode == ToolMode.CURSOR) {
                    setCursor(Cursor.getPredefinedCursor(
                            hoveredPipeSeg.horizontal() ? Cursor.N_RESIZE_CURSOR : Cursor.E_RESIZE_CURSOR));
                } else {
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        };
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
        addMouseWheelListener(e -> {
            boolean shortcut = (e.getModifiersEx() & (java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.META_DOWN_MASK)) != 0;
            if (mouseWheelZoom || shortcut) {
                double oldZoom = getZoomPercent() / 100.0;
                Point mouse = e.getPoint();
                javax.swing.JViewport vp = getParent() instanceof javax.swing.JViewport v ? v : null;
                Point viewPos = vp != null ? vp.getViewPosition() : null;
                if (e.getWheelRotation() < 0) {
                    zoomIn();
                } else if (e.getWheelRotation() > 0) {
                    zoomOut();
                }
                double newZoom = getZoomPercent() / 100.0;
                if (vp != null && viewPos != null && newZoom != oldZoom && oldZoom > 0) {
                    double scale = newZoom / oldZoom;
                    int newX = Math.max(0, (int) Math.round(mouse.x * scale - (mouse.x - viewPos.x)));
                    int newY = Math.max(0, (int) Math.round(mouse.y * scale - (mouse.y - viewPos.y)));
                    vp.setViewPosition(new Point(newX, newY));
                }
                e.consume();
            } else if (getParent() != null && getParent().getParent() != null) {
                getParent().getParent().dispatchEvent(e);
            }
        });
    }

    private void showContextMenu(MouseEvent e) {
        if (screen == null) return;
        int gPx = getGridSizePx();
        double gx = e.getX() / (double) gPx;
        double gy = e.getY() / (double) gPx;

        PipeEdgeDragController.SegmentHit segHit = PipeEdgeDragController.hitTest(screen.getPipes(), gx, gy, 0.75);
        if (segHit != null) {
            ScadaPipe hitPipe = segHit.pipe();
            selectedSymbolIds.clear();
            selectedGraphicIds.clear();
            selectedPipeIds.clear();
            selectedPipeIds.add(hitPipe.getId());
            notifySelectionChanged();
            repaint();

            JPopupMenu menu = new JPopupMenu();
            JMenuItem pipeProps = new JMenuItem("Pipe Properties... (Double-Click)");
            pipeProps.setFont(new Font(pipeProps.getFont().getName(), Font.BOLD, pipeProps.getFont().getSize()));
            pipeProps.addActionListener(ev -> openPipeProperties(hitPipe));

            JMenu diamMenu = new JMenu("Pipe Diameter");
            for (PipeDiameter pd : PipeDiameter.values()) {
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(pd.getDisplayName(), hitPipe.getDiameter() == pd);
                item.addActionListener(ev -> {
                    undoManager.recordSnapshot("Change Pipe Diameter", screen);
                    hitPipe.setDiameter(pd);
                    markDirty();
                    notifySelectionChanged();
                    repaint();
                });
                diamMenu.add(item);
            }

            JMenu fluidMenu = new JMenu("Fluid Type");
            for (FluidType ft : FluidType.values()) {
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(ft.getDisplayName(), hitPipe.getFluidType() == ft);
                item.addActionListener(ev -> {
                    undoManager.recordSnapshot("Change Pipe Fluid", screen);
                    hitPipe.setFluidType(ft);
                    markDirty();
                    notifySelectionChanged();
                    repaint();
                });
                fluidMenu.add(item);
            }

            JMenuItem addBend = new JMenuItem("Add Bend / Step");
            addBend.addActionListener(ev -> {
                undoManager.recordSnapshot("Add Bend", screen);
                PipeEdgeDragController.addBendAt(hitPipe, segHit.segmentIndex(), segHit.snappedPoint());
                markDirty();
                repaint();
            });
            JMenuItem teeBranch = new JMenuItem("Create Tee Branch");
            teeBranch.addActionListener(ev -> {
                routingTeePipeId = hitPipe.getId();
                routingTeeStart = segHit.snappedPoint();
                rubberBandVertices = List.of(routingTeeStart, routingTeeStart);
            });
            JMenuItem delPipe = new JMenuItem("Delete Pipe");
            delPipe.addActionListener(ev -> {
                undoManager.recordSnapshot("Delete Pipe", screen);
                screen.getPipes().remove(hitPipe);
                selectedPipeIds.remove(hitPipe.getId());
                markDirty();
                notifySelectionChanged();
                repaint();
            });
            menu.add(pipeProps);
            menu.add(diamMenu);
            menu.add(fluidMenu);
            menu.addSeparator();
            menu.add(addBend);
            menu.add(teeBranch);
            menu.addSeparator();
            menu.add(delPipe);
            menu.show(this, e.getX(), e.getY());
            return;
        }

        ScadaSymbolInstance symHit = hitTestSymbol(gx, gy);
        if (symHit != null) {
            if (!selectedSymbolIds.contains(symHit.getId())) {
                selectedSymbolIds.clear();
                selectedGraphicIds.clear();
                selectedPipeIds.clear();
                selectedSymbolIds.add(symHit.getId());
                repaint();
            }
            JPopupMenu menu = new JPopupMenu();
            JMenuItem propsItem = new JMenuItem("Properties... (Double-Click)");
            propsItem.setFont(new Font(propsItem.getFont().getName(), Font.BOLD, propsItem.getFont().getSize()));
            propsItem.addActionListener(ev -> openSymbolProperties(symHit));
            JMenuItem renameItem = new JMenuItem("Rename Element...");
            renameItem.addActionListener(ev -> {
                String nn = JOptionPane.showInputDialog(this, "Rename '" + symHit.getName() + "':", symHit.getName());
                if (nn != null) {
                    undoManager.recordSnapshot("Rename Element", screen);
                    symHit.setName(nn.trim());
                    markDirty();
                    notifySelectionChanged();
                    repaint();
                }
            });
            JCheckBoxMenuItem showLabelItem = new JCheckBoxMenuItem("Show Name Label", symHit.isShowLabel());
            showLabelItem.addActionListener(ev -> {
                undoManager.recordSnapshot("Toggle Label Visibility", screen);
                symHit.setShowLabel(showLabelItem.isSelected());
                markDirty();
                notifySelectionChanged();
                repaint();
            });
            JMenu labelPosMenu = new JMenu("Label Position");
            for (ScadaSymbolInstance.LabelPosition lp : ScadaSymbolInstance.LabelPosition.values()) {
                JCheckBoxMenuItem lpItem = new JCheckBoxMenuItem(lp.getDisplayName(), symHit.getLabelPosition() == lp);
                lpItem.addActionListener(ev -> {
                    undoManager.recordSnapshot("Move Label Position", screen);
                    symHit.setLabelPosition(lp);
                    symHit.setLabelOffsetX(0);
                    symHit.setLabelOffsetY(0);
                    markDirty();
                    notifySelectionChanged();
                    repaint();
                });
                labelPosMenu.add(lpItem);
            }
            labelPosMenu.addSeparator();
            JMenuItem resetLabelOff = new JMenuItem("Reset Label Offset");
            resetLabelOff.addActionListener(ev -> {
                undoManager.recordSnapshot("Reset Label Offset", screen);
                symHit.setLabelOffsetX(0);
                symHit.setLabelOffsetY(0);
                markDirty();
                notifySelectionChanged();
                repaint();
            });
            labelPosMenu.add(resetLabelOff);
            JMenuItem dupItem = new JMenuItem("Duplicate (Cmd/Ctrl+D)");
            dupItem.addActionListener(ev -> {
                copySelection();
                pasteFromClipboard();
            });
            JMenuItem rot = new JMenuItem("Rotate 90 deg CW (R)");
            rot.addActionListener(ev -> rotateSelected90CW());
            JMenuItem fh = new JMenuItem("Flip Horizontal");
            fh.addActionListener(ev -> flipSelectedHorizontal());
            JMenuItem fv = new JMenuItem("Flip Vertical");
            fv.addActionListener(ev -> flipSelectedVertical());
            JMenuItem front = new JMenuItem("Send to Foreground");
            front.addActionListener(ev -> bringSelectedToFront());
            JMenuItem back = new JMenuItem("Send to Background");
            back.addActionListener(ev -> sendSelectedToBack());
            JMenuItem del = new JMenuItem("Delete (Delete / Backspace)");
            del.addActionListener(ev -> deleteSelection());

            menu.add(propsItem);
            menu.add(renameItem);
            menu.add(showLabelItem);
            menu.add(labelPosMenu);
            menu.addSeparator();
            menu.add(dupItem);
            menu.add(rot);
            menu.add(fh);
            menu.add(fv);
            menu.addSeparator();
            menu.add(front);
            menu.add(back);
            menu.addSeparator();
            menu.add(del);
            menu.show(this, e.getX(), e.getY());
            return;
        }

        ScadaGraphicElement gHit = hitTestGraphic(gx, gy);
        if (gHit != null) {
            if (!selectedGraphicIds.contains(gHit.getId())) {
                selectedSymbolIds.clear();
                selectedPipeIds.clear();
                selectedGraphicIds.clear();
                selectedGraphicIds.add(gHit.getId());
                repaint();
            }
            JPopupMenu menu = new JPopupMenu();
            JMenuItem propsItem = new JMenuItem("Properties... (Double-Click)");
            propsItem.setFont(new Font(propsItem.getFont().getName(), Font.BOLD, propsItem.getFont().getSize()));
            propsItem.addActionListener(ev -> {
                UndoManager50.Snapshot before = screen != null ? UndoManager50.capture("Edit Graphic Properties", screen) : null;
                if (GraphicElementPropertiesDialog.showDialog(SwingUtilities.getWindowAncestor(this), gHit)) {
                    if (before != null) {
                        undoManager.recordSnapshot(before);
                    }
                    markDirty();
                    repaint();
                }
            });
            menu.add(propsItem);
            menu.addSeparator();
            List<ScadaGraphicElement> graphics = screen.getGraphics();
            int idx = graphics.indexOf(gHit);
            JMenuItem toFront = new JMenuItem("Send to Foreground");
            toFront.setEnabled(idx >= 0 && idx < graphics.size() - 1);
            toFront.addActionListener(ev -> {
                undoManager.recordSnapshot("Send to Foreground", screen);
                graphics.remove(gHit);
                graphics.add(gHit);
                markDirty();
                repaint();
            });
            JMenuItem toBack = new JMenuItem("Send to Background");
            toBack.setEnabled(idx > 0);
            toBack.addActionListener(ev -> {
                undoManager.recordSnapshot("Send to Background", screen);
                graphics.remove(gHit);
                graphics.addFirst(gHit);
                markDirty();
                repaint();
            });
            menu.add(toFront);
            menu.add(toBack);
            menu.addSeparator();
            JMenuItem dupItem = new JMenuItem("Duplicate");
            dupItem.addActionListener(ev -> {
                copySelection();
                pasteFromClipboard();
            });
            JMenuItem delItem = new JMenuItem("Delete (Delete / Backspace)");
            delItem.addActionListener(ev -> deleteSelection());
            menu.add(dupItem);
            menu.add(delItem);
            menu.show(this, e.getX(), e.getY());
            return;
        }

        // Empty canvas context menu (matching MaraFlow)
        JPopupMenu canvasMenu = new JPopupMenu();
        JMenuItem pasteItem = new JMenuItem("Paste (Cmd/Ctrl+V)");
        pasteItem.setEnabled(!clipboardSymbols.isEmpty() || !clipboardGraphics.isEmpty());
        pasteItem.addActionListener(ev -> pasteFromClipboard());
        canvasMenu.add(pasteItem);
        canvasMenu.addSeparator();
        JMenu drawSub = new JMenu("Draw Graphic");
        for (ScadaGraphicElement.Kind k : new ScadaGraphicElement.Kind[]{
                ScadaGraphicElement.Kind.RECTANGLE, ScadaGraphicElement.Kind.ROUNDED_RECTANGLE,
                ScadaGraphicElement.Kind.CIRCLE, ScadaGraphicElement.Kind.ELLIPSE,
                ScadaGraphicElement.Kind.LINE, ScadaGraphicElement.Kind.TEXT}) {
            JMenuItem ki = new JMenuItem(k.getDisplayName());
            ki.addActionListener(ev -> setShapeTool(k));
            drawSub.add(ki);
        }
        canvasMenu.add(drawSub);
        JMenuItem imgItem = new JMenuItem("Insert Picture / Image...");
        imgItem.addActionListener(ev -> insertImageFromDialog((int) Math.round(gx), (int) Math.round(gy)));
        canvasMenu.add(imgItem);
        canvasMenu.addSeparator();
        JMenuItem resetZoom = new JMenuItem("Reset Zoom (100%)");
        resetZoom.addActionListener(ev -> setZoomPercent(100));
        canvasMenu.add(resetZoom);
        canvasMenu.show(this, e.getX(), e.getY());
    }

    private void installKeyBindings() {
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "rotate90");
        getActionMap().put("rotate90", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rotateSelected90CW();
            }
        });

        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "delete");
        getActionMap().put("delete", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelection();
            }
        });

        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask), "undo");
        getActionMap().put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!operatorMode && undoManager.undo(screen)) {
                    markDirty();
                    repaint();
                }
            }
        });

        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menuMask), "redo");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask | InputEvent.SHIFT_DOWN_MASK), "redo");
        getActionMap().put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!operatorMode && undoManager.redo(screen)) {
                    markDirty();
                    repaint();
                }
            }
        });

        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_A, menuMask), "selectAll");
        getActionMap().put("selectAll", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!operatorMode && screen != null) {
                    for (ScadaSymbolInstance sym : screen.getSymbols()) {
                        selectedSymbolIds.add(sym.getId());
                    }
                    for (ScadaGraphicElement g : screen.getGraphics()) {
                        selectedGraphicIds.add(g.getId());
                    }
                    repaint();
                }
            }
        });

        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelInteraction");
        getActionMap().put("cancelInteraction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetTransientInteractionState();
                if (toolMode != ToolMode.CURSOR) {
                    toolMode = ToolMode.CURSOR;
                    if (onToolModeChanged != null) {
                        onToolModeChanged.run();
                    }
                }
                repaint();
            }
        });

        int[][] nudgeDirs = {
                {KeyEvent.VK_UP, 0, -1},
                {KeyEvent.VK_DOWN, 0, 1},
                {KeyEvent.VK_LEFT, -1, 0},
                {KeyEvent.VK_RIGHT, 1, 0}
        };
        for (int[] dir : nudgeDirs) {
            int key = dir[0], dx = dir[1], dy = dir[2];
            String actionName = "nudge_" + key;
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), actionName);
            getActionMap().put(actionName, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (operatorMode || screen == null) return;
                    if (selectedSymbolIds.isEmpty() && selectedGraphicIds.isEmpty()) return;
                    int step = 1;
                    undoManager.recordSnapshot("Nudge Selection", screen);
                    for (ScadaSymbolInstance sym : screen.getSymbols()) {
                        if (selectedSymbolIds.contains(sym.getId())) {
                            sym.setGridX(Math.max(0, sym.getGridX() + dx * step));
                            sym.setGridY(Math.max(0, sym.getGridY() + dy * step));
                        }
                    }
                    if (!selectedSymbolIds.isEmpty()) {
                        translateOrReroutePipes(selectedSymbolIds, dx * step, dy * step);
                    }
                    for (ScadaGraphicElement g : screen.getGraphics()) {
                        if (selectedGraphicIds.contains(g.getId())) {
                            g.setGridX(Math.max(0, g.getGridX() + dx * step));
                            g.setGridY(Math.max(0, g.getGridY() + dy * step));
                        }
                    }
                    markDirty();
                    repaint();
                }
            });
        }

        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask), "copySelection");
        getActionMap().put("copySelection", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copySelection();
            }
        });

        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask), "pasteSelection");
        getActionMap().put("pasteSelection", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pasteFromClipboard();
            }
        });

        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_D, menuMask), "duplicateSelection");
        getActionMap().put("duplicateSelection", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copySelection();
                pasteFromClipboard();
            }
        });
    }

    public void copySelection() {
        if (operatorMode || screen == null) return;
        if (selectedSymbolIds.isEmpty() && selectedGraphicIds.isEmpty()) return;
        clipboardSymbols.clear();
        clipboardPipes.clear();
        clipboardGraphics.clear();
        pasteCascadeCount = 0;

        for (ScadaSymbolInstance sym : screen.getSymbols()) {
            if (selectedSymbolIds.contains(sym.getId())) {
                clipboardSymbols.add(sym.copy());
            }
        }
        for (ScadaPipe pipe : screen.getPipes()) {
            if (pipe.getSourceSymbolId() != null && pipe.getTargetSymbolId() != null
                    && selectedSymbolIds.contains(pipe.getSourceSymbolId())
                    && selectedSymbolIds.contains(pipe.getTargetSymbolId())) {
                clipboardPipes.add(pipe.copy());
            }
        }
        for (ScadaGraphicElement ge : screen.getGraphics()) {
            if (selectedGraphicIds.contains(ge.getId())) {
                clipboardGraphics.add(ge.copy());
            }
        }
    }

    public void pasteFromClipboard() {
        if (operatorMode || screen == null) return;
        if (clipboardSymbols.isEmpty() && clipboardGraphics.isEmpty()) return;
        undoManager.recordSnapshot("Paste Selection", screen);
        pasteCascadeCount++;
        int offset = pasteCascadeCount * 2;

        Map<String, String> idMap = new HashMap<>();
        selectedSymbolIds.clear();
        selectedPipeIds.clear();
        selectedGraphicIds.clear();

        for (ScadaSymbolInstance src : clipboardSymbols) {
            ScadaSymbolInstance copy = src.copy();
            String newId = UUID.randomUUID().toString();
            idMap.put(src.getId(), newId);
            copy.setId(newId);
            copy.setName(src.getName() + "_copy");
            copy.setGridX(src.getGridX() + offset);
            copy.setGridY(src.getGridY() + offset);
            screen.getSymbols().add(copy);
            selectedSymbolIds.add(newId);
        }

        for (ScadaPipe srcPipe : clipboardPipes) {
            String newSrc = idMap.get(srcPipe.getSourceSymbolId());
            String newDst = idMap.get(srcPipe.getTargetSymbolId());
            if (newSrc != null && newDst != null) {
                ScadaPipe pCopy = srcPipe.copy();
                pCopy.setId(UUID.randomUUID().toString());
                pCopy.setSourceSymbolId(newSrc);
                pCopy.setTargetSymbolId(newDst);
                List<GridPoint> shifted = new ArrayList<>(pCopy.getVertices().size());
                for (GridPoint gp : pCopy.getVertices()) {
                    shifted.add(new GridPoint(gp.x() + offset, gp.y() + offset));
                }
                pCopy.setVertices(shifted);
                screen.getPipes().add(pCopy);
                selectedPipeIds.add(pCopy.getId());
            }
        }

        for (ScadaGraphicElement srcG : clipboardGraphics) {
            ScadaGraphicElement gCopy = srcG.copy();
            gCopy.setId(UUID.randomUUID().toString());
            gCopy.setGridX(srcG.getGridX() + offset);
            gCopy.setGridY(srcG.getGridY() + offset);
            screen.getGraphics().add(gCopy);
            selectedGraphicIds.add(gCopy.getId());
        }
        markDirty();
        repaint();
    }

    private void translateOrReroutePipes(Set<String> movedSymbolIds, int dx, int dy) {
        if (screen == null || (dx == 0 && dy == 0)) return;
        for (ScadaPipe pipe : screen.getPipes()) {
            boolean srcMoved = pipe.getSourceSymbolId() != null && movedSymbolIds.contains(pipe.getSourceSymbolId());
            boolean dstMoved = pipe.getTargetSymbolId() != null && movedSymbolIds.contains(pipe.getTargetSymbolId());
            if (!srcMoved && !dstMoved) continue;

            if (srcMoved && dstMoved) {
                List<GridPoint> shifted = new ArrayList<>(pipe.getVertices().size());
                for (GridPoint gp : pipe.getVertices()) {
                    shifted.add(new GridPoint(gp.x() + dx, gp.y() + dy));
                }
                pipe.setVertices(shifted);
            } else {
                rerouteSinglePipe(pipe);
            }
        }
    }

    private void reroutePipesForSymbols(Set<String> symbolIds) {
        if (screen == null) return;
        for (ScadaPipe pipe : screen.getPipes()) {
            boolean srcHit = pipe.getSourceSymbolId() != null && symbolIds.contains(pipe.getSourceSymbolId());
            boolean dstHit = pipe.getTargetSymbolId() != null && symbolIds.contains(pipe.getTargetSymbolId());
            if (srcHit || dstHit) {
                rerouteSinglePipe(pipe);
            }
        }
    }

    private void rerouteSinglePipe(ScadaPipe pipe) {
        ScadaSymbolInstance srcSym = screen.findSymbolById(pipe.getSourceSymbolId());
        ScadaSymbolInstance dstSym = screen.findSymbolById(pipe.getTargetSymbolId());
        if (srcSym != null && dstSym != null) {
            PipePort.WorldPort wp1 = srcSym.resolveWorldPort(pipe.getSourcePortId());
            PipePort.WorldPort wp2 = dstSym.resolveWorldPort(pipe.getTargetPortId());
            if (wp1 != null && wp2 != null) {
                pipe.setVertices(PipeRouter.route(wp1, wp2));
            }
        }
    }

    private void setupDropTarget() {
        new DropTarget(this, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                if (operatorMode || screen == null) {
                    dtde.rejectDrop();
                    return;
                }
                try {
                    if (!dtde.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        dtde.rejectDrop();
                        return;
                    }
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    String symbolType = (String) dtde.getTransferable().getTransferData(DataFlavor.stringFlavor);
                    if (symbolType == null || symbolType.isBlank()) {
                        dtde.dropComplete(false);
                        return;
                    }
                    dropSymbolAtPixel(symbolType, dtde.getLocation().x, dtde.getLocation().y);
                    dtde.dropComplete(true);
                } catch (Exception ex) {
                    dtde.dropComplete(false);
                }
            }
        }, true);
    }

    public void dropSymbolAtPixel(String symbolType, int px, int py) {
        if (operatorMode || screen == null || symbolType == null || symbolType.isBlank()) return;
        int gPx = getGridSizePx();
        int gx = Math.max(0, Math.round(px / (float) gPx));
        int gy = Math.max(0, Math.round(py / (float) gPx));

        undoManager.recordSnapshot("Drop " + symbolType, screen);
        int idx = screen.getSymbols().size() + 1;
        ScadaSymbolInstance sym = SymbolCatalogTree.createDefaultInstance(symbolType, symbolType + "-" + idx, gx, gy);
        screen.getSymbols().add(sym);
        selectedSymbolIds.clear();
        selectedPipeIds.clear();
        selectedGraphicIds.clear();
        selectedSymbolIds.add(sym.getId());
        markDirty();
        requestFocusInWindow();
        repaint();
    }

    private PortHit hitTestPort(double gx, double gy, double toleranceGrid) {
        if (screen == null) return null;
        for (int i = screen.getSymbols().size() - 1; i >= 0; i--) {
            ScadaSymbolInstance sym = screen.getSymbols().get(i);
            for (PipePort port : sym.getPorts()) {
                PipePort.WorldPort wp = port.resolveWorldPort(
                        sym.getGridX(), sym.getGridY(), sym.getGridW(), sym.getGridH(),
                        sym.getRotationDeg(), sym.isFlipH(), sym.isFlipV()
                );
                double dx = Math.abs(gx - wp.position().x());
                double dy = Math.abs(gy - wp.position().y());
                if (dx <= toleranceGrid && dy <= toleranceGrid) {
                    return new PortHit(sym, port, wp);
                }
            }
        }
        return null;
    }

    private ScadaSymbolInstance hitTestSymbol(double gx, double gy) {
        if (screen == null) return null;
        List<ScadaSymbolInstance> ordered = new ArrayList<>(screen.getSymbols());
        ordered.sort(Comparator.comparingInt(ScadaSymbolInstance::getZIndex).reversed());
        for (ScadaSymbolInstance sym : ordered) {
            if (gx >= sym.getGridX() && gx <= sym.getGridX() + sym.getEffectiveGridW()
                    && gy >= sym.getGridY() && gy <= sym.getGridY() + sym.getEffectiveGridH()) {
                return sym;
            }
        }
        return null;
    }

    private ScadaSymbolInstance hitTestSymbolLabel(double px, double py) {
        if (screen == null) return null;
        Graphics2D g2 = (Graphics2D) getGraphics();
        if (g2 == null) return null;
        try {
            int gPx = getGridSizePx();
            List<ScadaSymbolInstance> ordered = new ArrayList<>(screen.getSymbols());
            ordered.sort(Comparator.comparingInt(ScadaSymbolInstance::getZIndex).reversed());
            for (ScadaSymbolInstance sym : ordered) {
                java.awt.geom.Rectangle2D.Double lb = EquipmentSymbolRenderer.computeLabelBounds(g2, sym, gPx);
                if (lb != null && lb.contains(px, py)) {
                    return sym;
                }
            }
            return null;
        } finally {
            g2.dispose();
        }
    }

    private void openPipeProperties(ScadaPipe pipe) {
        if (project == null || screen == null || pipe == null) return;
        UndoManager50.Snapshot before = UndoManager50.capture("Edit Pipe Properties", screen);
        PipePropertiesDialog dlg = new PipePropertiesDialog(SwingUtilities.getWindowAncestor(this), project, pipe);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            undoManager.recordSnapshot(before);
            markDirty();
            notifySelectionChanged();
            repaint();
        }
    }

    private void openSymbolProperties(ScadaSymbolInstance sym) {
        if (project == null || screen == null || sym == null) return;
        UndoManager50.Snapshot before = UndoManager50.capture("Edit Properties", screen);
        ScadaSymbolPropertiesDialog dlg = new ScadaSymbolPropertiesDialog(
                SwingUtilities.getWindowAncestor(this), project, sym);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            undoManager.recordSnapshot(before);
            markDirty();
            notifySelectionChanged();
            repaint();
        }
    }

    private void handleOperatorPress(ScadaSymbolInstance sym) {
        if (sym == null) return;
        String type = sym.getSymbolType();
        String targetTag = resolveWriteTag(sym);
        boolean hasTag = targetTag != null && !targetTag.isBlank() && project != null && tagProvider != null;

        if (!hasTag) {
            // Tagless interactive simulation toggle via symbol properties
            switch (type) {
                case "PushButton", "EStopMushroom", "SelectorSwitch" -> {
                    boolean pressed = Boolean.parseBoolean(sym.getProperties().getOrDefault("Pressed", "false"));
                    sym.setProperty("Pressed", String.valueOf(!pressed));
                    markDirty();
                    notifySelectionChanged();
                    repaint();
                }
                case "HandValve", "BallValve", "GateValve", "GlobeValve", "ButterflyValve",
                     "NeedleValve", "PlugValve", "DiaphragmValve", "SolenoidValve", "ZoneValve", "Damper" -> {
                    boolean open = Boolean.parseBoolean(sym.getProperties().getOrDefault("Open", "true"));
                    sym.setProperty("Open", String.valueOf(!open));
                    markDirty();
                    notifySelectionChanged();
                    repaint();
                }
                case "CircuitBreaker", "Contactor" -> {
                    boolean closed = Boolean.parseBoolean(sym.getProperties().getOrDefault("Closed", "false"));
                    sym.setProperty("Closed", String.valueOf(!closed));
                    markDirty();
                    notifySelectionChanged();
                    repaint();
                }
                default -> {
                    if (type.endsWith("Pump") || type.contains("Boiler") || type.equals("Chiller")
                            || type.contains("HeatPump") || type.contains("Fan") || type.equals("AirBlower")) {
                        boolean running = Boolean.parseBoolean(sym.getProperties().getOrDefault("Running", "false"));
                        sym.setProperty("Running", String.valueOf(!running));
                        markDirty();
                        notifySelectionChanged();
                        repaint();
                    }
                }
            }
            return;
        }

        double cur = tagProvider.readTag(project, targetTag);
        switch (type) {
            case "PushButton", "EStopMushroom" -> {
                String mode = sym.getProperties().getOrDefault("ButtonMode", "Toggle");
                if ("Momentary".equalsIgnoreCase(mode)) {
                    tagProvider.writeTag(project, targetTag, 1.0);
                    activeMomentarySymbol = sym;
                } else {
                    tagProvider.writeTag(project, targetTag, (cur != 0.0) ? 0.0 : 1.0);
                }
                repaint();
            }
            case "SolenoidValve", "HandValve", "BallValve", "GateValve", "GlobeValve", "ButterflyValve", "ZoneValve",
                 "CircuitBreaker", "Contactor" -> {
                tagProvider.writeTag(project, targetTag, (cur != 0.0) ? 0.0 : 1.0);
                repaint();
            }
            case "SelectorSwitch" -> {
                int next = ((int) Math.round(cur) + 1) % 3;
                tagProvider.writeTag(project, targetTag, next);
                repaint();
            }
            case "NumericSetpoint" -> {
                String input = JOptionPane.showInputDialog(this,
                        "Enter setpoint for " + sym.getName() + " (" + targetTag + "):",
                        Double.toString(cur));
                if (input != null && !input.isBlank()) {
                    try {
                        tagProvider.writeTag(project, targetTag, Double.parseDouble(input.trim()));
                        repaint();
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            default -> {
                if (type.endsWith("Pump") || type.contains("Boiler") || type.equals("Chiller")
                        || type.contains("HeatPump") || type.contains("Fan") || type.equals("AirBlower")) {
                    tagProvider.writeTag(project, targetTag, (cur != 0.0) ? 0.0 : 1.0);
                    repaint();
                }
            }
        }
    }

    private void releaseActiveMomentary() {
        if (activeMomentarySymbol == null) return;
        ScadaSymbolInstance sym = activeMomentarySymbol;
        activeMomentarySymbol = null;
        if (project == null || tagProvider == null) return;
        String targetTag = resolveWriteTag(sym);
        if (targetTag != null && !targetTag.isBlank()) {
            tagProvider.writeTag(project, targetTag, 0.0);
            repaint();
        }
    }

    private static String resolveWriteTag(ScadaSymbolInstance sym) {
        for (String role : new String[]{"TargetTag", "SetpointTag", "ValueTag", "OpenTag", "ClosedTag", "RunTag"}) {
            String t = sym.getTagBindings().get(role);
            if (t != null && !t.isBlank()) return t;
        }
        return null;
    }

    private static boolean isOperatorInteractive(String type) {
        if (type == null) return false;
        if (type.endsWith("Pump") || type.contains("Boiler") || type.equals("Chiller")
                || type.contains("HeatPump") || type.contains("Fan") || type.equals("AirBlower")) {
            return true;
        }
        return switch (type) {
            case "PushButton", "SelectorSwitch", "EStopMushroom",
                 "SolenoidValve", "HandValve", "BallValve", "GateValve", "GlobeValve",
                 "ButterflyValve", "NeedleValve", "PlugValve", "DiaphragmValve", "ZoneValve", "Damper",
                 "CircuitBreaker", "Contactor", "NumericSetpoint" -> true;
            default -> false;
        };
    }

    private static SymbolRenderer defaultSymbolRenderer() {
        return new EquipmentSymbolRenderer();
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(900, 600);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return getGridSizePx() * 2;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return getGridSizePx() * 10;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return false;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
