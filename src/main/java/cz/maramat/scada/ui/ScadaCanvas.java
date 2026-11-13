package cz.maramat.scada.ui;

import cz.maramat.scada.model.GridPoint;
import cz.maramat.scada.model.PipePort;
import cz.maramat.scada.model.ScadaPipe;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaScreen;
import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.render.PipeRenderer;
import cz.maramat.scada.render.ScadaPalette;
import cz.maramat.scada.render.ScadaShader;
import cz.maramat.scada.render.SymbolLayerCache;
import cz.maramat.scada.routing.FlowPropagationSolver;
import cz.maramat.scada.routing.FlowState;
import cz.maramat.scada.routing.PipeEdgeDragController;
import cz.maramat.scada.routing.PipeRouter;
import cz.maramat.scada.tags.InternalTagProvider;
import cz.maramat.scada.tags.RuntimeTagProvider;
import cz.maramat.scada.undo.UndoManager50;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interactive SCADA Designer & Operator canvas with strict integer grid, discrete zoom (50%..300%),
 * MMB pan, marquee selection, UndoManager50, port-to-port PipeRouter, and Blender-style segment dragging.
 */
public class ScadaCanvas extends JComponent implements Scrollable {

    public enum ToolMode {
        CURSOR,
        PIPE
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
    private boolean operatorMode = false;
    private boolean showFlowAnimation = true;
    private int zoomIndex = 3; // 100% -> 10px

    private final Set<String> selectedSymbolIds = new HashSet<>();
    private final Set<String> selectedPipeIds = new HashSet<>();

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

    // 60 FPS animation loop
    private final Timer animTimer;
    private double flowDashPhasePx = 0.0;
    private long lastTickNs = System.nanoTime();

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

        this.animTimer = new Timer(16, e -> {
            long now = System.nanoTime();
            double dt = Math.min(0.1, (now - lastTickNs) / 1_000_000_000.0);
            lastTickNs = now;
            if (showFlowAnimation) {
                flowDashPhasePx -= dt * 36.0;
                repaint();
            }
        });
        this.animTimer.start();
    }

    public void dispose() {
        animTimer.stop();
        layerCache.invalidateAll();
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
        rubberBandVertices = List.of();
        repaint();
    }

    public boolean isOperatorMode() {
        return operatorMode;
    }

    public void setOperatorMode(boolean operatorMode) {
        this.operatorMode = operatorMode;
        if (operatorMode) {
            selectedSymbolIds.clear();
            selectedPipeIds.clear();
            setCursor(Cursor.getDefaultCursor());
        }
        repaint();
    }

    public boolean isShowFlowAnimation() {
        return showFlowAnimation;
    }

    public void setShowFlowAnimation(boolean showFlowAnimation) {
        this.showFlowAnimation = showFlowAnimation;
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
        }
    }

    public void zoomIn() {
        if (zoomIndex < ZOOM_PERCENTS.length - 1) {
            zoomIndex++;
            updatePreferredSize();
            revalidate();
            repaint();
        }
    }

    public void zoomOut() {
        if (zoomIndex > 0) {
            zoomIndex--;
            updatePreferredSize();
            revalidate();
            repaint();
        }
    }

    public Set<String> getSelectedSymbolIds() {
        return selectedSymbolIds;
    }

    public Set<String> getSelectedPipeIds() {
        return selectedPipeIds;
    }

    public void rotateSelected90CW() {
        if (operatorMode || screen == null || selectedSymbolIds.isEmpty()) return;
        undoManager.recordSnapshot("Rotate 90", screen);
        for (ScadaSymbolInstance sym : screen.getSymbols()) {
            if (selectedSymbolIds.contains(sym.getId())) {
                sym.setRotationDeg((sym.getRotationDeg() + 90) % 360);
            }
        }
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
        if (selectedSymbolIds.isEmpty() && selectedPipeIds.isEmpty()) return;
        undoManager.recordSnapshot("Delete Selection", screen);
        screen.getSymbols().removeIf(s -> selectedSymbolIds.contains(s.getId()));
        screen.getPipes().removeIf(p -> selectedPipeIds.contains(p.getId()));
        selectedSymbolIds.clear();
        selectedPipeIds.clear();
        markDirty();
        repaint();
    }

    private void updatePreferredSize() {
        int g = getGridSizePx();
        setPreferredSize(new Dimension(DEFAULT_CANVAS_GRID_W * g, DEFAULT_CANVAS_GRID_H * g));
    }

    private void markDirty() {
        if (project != null) {
            project.setDirty(true);
        }
    }

    @Override
    protected void paintComponent(Graphics gRaw) {
        super.paintComponent(gRaw);
        Graphics2D g = (Graphics2D) gRaw.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int gPx = getGridSizePx();
            Color bg = (screen != null) ? screen.getBackgroundColor() : ScadaScreen.parseHexColor(ScadaScreen.BG_LIGHT_SLATE, Color.LIGHT_GRAY);
            g.setColor(bg);
            g.fillRect(0, 0, getWidth(), getHeight());

            if (!operatorMode && (screen == null || screen.isShowGrid())) {
                paintGridDots(g, gPx);
            }

            if (screen == null) return;

            Map<String, FlowState> flowMap = FlowPropagationSolver.solve(screen, project, tagProvider);
            long nowMs = System.currentTimeMillis();

            // 1. Pipes (rendered under equipment housings)
            for (ScadaPipe pipe : screen.getPipes()) {
                FlowState fs = flowMap.getOrDefault(pipe.getId(), FlowState.IDLE);
                PipeRenderer.paintPipe(g, pipe, gPx, fs, showFlowAnimation, flowDashPhasePx, selectedPipeIds.contains(pipe.getId()));
            }

            // 2. Symbols sorted by zIndex
            List<ScadaSymbolInstance> ordered = new ArrayList<>(screen.getSymbols());
            ordered.sort(Comparator.comparingInt(ScadaSymbolInstance::getZIndex));
            for (ScadaSymbolInstance sym : ordered) {
                symbolRenderer.paintSymbol(g, sym, gPx, project, tagProvider, layerCache, nowMs, operatorMode);
                if (!operatorMode && selectedSymbolIds.contains(sym.getId())) {
                    paintSymbolSelectionBox(g, sym, gPx);
                }
            }

            // 3. Port snap highlight box (1G x 1G)
            if (!operatorMode && hoveredPort != null) {
                GridPoint pos = hoveredPort.worldPort().position();
                int hx = pos.x() * gPx - gPx / 2;
                int hy = pos.y() * gPx - gPx / 2;
                g.setColor(new Color(0x00, 0x78, 0xD7, 70));
                g.fillRect(hx, hy, gPx, gPx);
                g.setColor(new Color(0x00, 0x78, 0xD7));
                g.setStroke(new BasicStroke(1.5f));
                g.drawRect(hx, hy, gPx, gPx);
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

    private void paintGridDots(Graphics2D g, int gPx) {
        Rectangle clip = g.getClipBounds();
        int startX = (clip.x / gPx) * gPx;
        int startY = (clip.y / gPx) * gPx;
        int endX = clip.x + clip.width;
        int endY = clip.y + clip.height;
        g.setColor(new Color(0xCC, 0xCC, 0xCC));
        for (int y = startY; y <= endY; y += gPx) {
            for (int x = startX; x <= endX; x += gPx) {
                g.fillRect(x, y, 1, 1);
            }
        }
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
                if (operatorMode || screen == null) return;

                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(e)) return;

                int gPx = getGridSizePx();
                double gx = e.getX() / (double) gPx;
                double gy = e.getY() / (double) gPx;

                // 1. Port hit starts pipe routing
                PortHit portHit = hitTestPort(gx, gy, 0.7);
                if (portHit != null) {
                    routingSourcePort = portHit;
                    routingTeePipeId = null;
                    rubberBandVertices = List.of(portHit.worldPort().position());
                    return;
                }

                // 2. Pipe segment hit
                PipeEdgeDragController.SegmentHit segHit = PipeEdgeDragController.hitTest(screen.getPipes(), gx, gy, 0.65);
                if (segHit != null && toolMode == ToolMode.CURSOR) {
                    selectedSymbolIds.clear();
                    selectedPipeIds.clear();
                    selectedPipeIds.add(segHit.pipe().getId());
                    draggingPipe = segHit.pipe();
                    draggingSegIndex = segHit.segmentIndex();
                    draggingSegHoriz = segHit.horizontal();
                    lastSegDragGrid = draggingSegHoriz ? Math.round((float) gy) : Math.round((float) gx);
                    pipeDragSnapshotRecorded = false;
                    repaint();
                    return;
                }

                // 3. Symbol hit
                ScadaSymbolInstance symHit = hitTestSymbol(gx, gy);
                if (symHit != null) {
                    selectedPipeIds.clear();
                    if (e.isShiftDown()) {
                        if (!selectedSymbolIds.add(symHit.getId())) {
                            selectedSymbolIds.remove(symHit.getId());
                        }
                    } else if (!selectedSymbolIds.contains(symHit.getId())) {
                        selectedSymbolIds.clear();
                        selectedSymbolIds.add(symHit.getId());
                    }
                    draggingSymbols = true;
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
                if (operatorMode || screen == null) return;

                int gPx = getGridSizePx();
                double gx = e.getX() / (double) gPx;
                double gy = e.getY() / (double) gPx;
                int igx = Math.round((float) gx);
                int igy = Math.round((float) gy);

                if (routingSourcePort != null) {
                    PortHit target = hitTestPort(gx, gy, 0.8);
                    hoveredPort = target;
                    if (target != null && !target.symbol().getId().equals(routingSourcePort.symbol().getId())) {
                        rubberBandVertices = PipeRouter.route(routingSourcePort.worldPort(), target.worldPort());
                    } else {
                        rubberBandVertices = PipeRouter.routeToPoint(routingSourcePort.worldPort(), new GridPoint(igx, igy));
                    }
                    repaint();
                    return;
                }

                if (routingTeePipeId != null && routingTeeStart != null) {
                    PortHit target = hitTestPort(gx, gy, 0.8);
                    hoveredPort = target;
                    GridPoint endPt = (target != null) ? target.worldPort().position() : new GridPoint(igx, igy);
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
                if (operatorMode || screen == null) return;
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                    return;
                }

                int gPx = getGridSizePx();
                double gx = e.getX() / (double) gPx;
                double gy = e.getY() / (double) gPx;

                if (routingSourcePort != null) {
                    PortHit target = hitTestPort(gx, gy, 0.85);
                    if (target != null && !target.symbol().getId().equals(routingSourcePort.symbol().getId())) {
                        undoManager.recordSnapshot("Route Pipe", screen);
                        ScadaPipe pipe = new ScadaPipe();
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

                draggingSymbols = false;

                if (marqueeActive && marqueeStartPx != null && marqueeEndPx != null) {
                    Rectangle rect = new Rectangle(
                            Math.min(marqueeStartPx.x, marqueeEndPx.x),
                            Math.min(marqueeStartPx.y, marqueeEndPx.y),
                            Math.abs(marqueeEndPx.x - marqueeStartPx.x),
                            Math.abs(marqueeEndPx.y - marqueeStartPx.y)
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
                    marqueeActive = false;
                    marqueeStartPx = null;
                    marqueeEndPx = null;
                    repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (operatorMode || screen == null) return;
                int gPx = getGridSizePx();
                double gx = e.getX() / (double) gPx;
                double gy = e.getY() / (double) gPx;

                PortHit nextPort = hitTestPort(gx, gy, 0.7);
                if (nextPort != hoveredPort) {
                    hoveredPort = nextPort;
                    repaint();
                }

                if (hoveredPort != null) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    return;
                }

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
    }

    private void showContextMenu(MouseEvent e) {
        if (screen == null) return;
        int gPx = getGridSizePx();
        double gx = e.getX() / (double) gPx;
        double gy = e.getY() / (double) gPx;

        PipeEdgeDragController.SegmentHit segHit = PipeEdgeDragController.hitTest(screen.getPipes(), gx, gy, 0.75);
        if (segHit != null) {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem addBend = new JMenuItem("Add Bend / Step");
            addBend.addActionListener(ev -> {
                undoManager.recordSnapshot("Add Bend", screen);
                PipeEdgeDragController.addBendAt(segHit.pipe(), segHit.segmentIndex(), segHit.snappedPoint());
                markDirty();
                repaint();
            });
            JMenuItem teeBranch = new JMenuItem("Create Tee Branch");
            teeBranch.addActionListener(ev -> {
                routingTeePipeId = segHit.pipe().getId();
                routingTeeStart = segHit.snappedPoint();
                rubberBandVertices = List.of(routingTeeStart, routingTeeStart);
            });
            JMenuItem delPipe = new JMenuItem("Delete Pipe");
            delPipe.addActionListener(ev -> {
                undoManager.recordSnapshot("Delete Pipe", screen);
                screen.getPipes().remove(segHit.pipe());
                selectedPipeIds.remove(segHit.pipe().getId());
                markDirty();
                repaint();
            });
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
                selectedSymbolIds.add(symHit.getId());
                repaint();
            }
            JPopupMenu menu = new JPopupMenu();
            JMenuItem rot = new JMenuItem("Rotate 90 deg (R)");
            rot.addActionListener(ev -> rotateSelected90CW());
            JMenuItem fh = new JMenuItem("Flip Horizontal");
            fh.addActionListener(ev -> flipSelectedHorizontal());
            JMenuItem fv = new JMenuItem("Flip Vertical");
            fv.addActionListener(ev -> flipSelectedVertical());
            JMenuItem front = new JMenuItem("Bring to Front");
            front.addActionListener(ev -> bringSelectedToFront());
            JMenuItem back = new JMenuItem("Send to Back");
            back.addActionListener(ev -> sendSelectedToBack());
            JMenuItem del = new JMenuItem("Delete");
            del.addActionListener(ev -> deleteSelection());
            menu.add(rot);
            menu.add(fh);
            menu.add(fv);
            menu.addSeparator();
            menu.add(front);
            menu.add(back);
            menu.addSeparator();
            menu.add(del);
            menu.show(this, e.getX(), e.getY());
        }
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
                    repaint();
                }
            }
        });
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

    private static SymbolRenderer defaultSymbolRenderer() {
        return (g, sym, gPx, project, tags, cache, timeMs, opMode) -> {
            int x = sym.getGridX() * gPx;
            int y = sym.getGridY() * gPx;
            int w = sym.getEffectiveGridW() * gPx;
            int h = sym.getEffectiveGridH() * gPx;
            ScadaShader.paintBeveledHousing(g, x, y, w, h, 4.0, ScadaPalette.Material.CAST_IRON);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(9, gPx)));
            Rectangle2D bounds = g.getFontMetrics().getStringBounds(sym.getName(), g);
            g.drawString(sym.getName(), (float) (x + (w - bounds.getWidth()) / 2.0), (float) (y + h / 2.0 + bounds.getHeight() * 0.3));
        };
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
