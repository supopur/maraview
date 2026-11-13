package cz.maramat.scada.ui;

import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.model.SymbolCatalog;

import javax.swing.BorderFactory;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Left dock Symbol Library JTree styled identically to MaraFlow's LibraryTreePanel,
 * supporting pure-Swing drag-and-drop of leaf symbols onto ScadaCanvas (avoids macOS AWT CDragSource NSExceptions).
 */
public final class SymbolCatalogTree {

    private SymbolCatalogTree() {
    }

    public static JTree createTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Symbol Library");

        for (java.util.Map.Entry<String, List<String>> entry : SymbolCatalog.getCategorizedTypes().entrySet()) {
            DefaultMutableTreeNode catNode = new DefaultMutableTreeNode(entry.getKey());
            for (String type : entry.getValue()) {
                catNode.add(new DefaultMutableTreeNode(type));
            }
            root.add(catNode);
        }

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

        // Pure-Swing leaf drag-and-drop (never invokes native AppKit CDragSource on macOS)
        MouseAdapter dragAdapter = new MouseAdapter() {
            private String draggingType;
            private Point pressScreen;
            private boolean dragging;

            @Override
            public void mousePressed(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path != null) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    if (node.isLeaf()) {
                        draggingType = String.valueOf(node.getUserObject());
                        pressScreen = e.getLocationOnScreen();
                        dragging = false;
                        return;
                    }
                }
                draggingType = null;
                pressScreen = null;
                dragging = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggingType == null || pressScreen == null) return;
                Point cur = e.getLocationOnScreen();
                if (!dragging && pressScreen.distance(cur) >= 5.0) {
                    dragging = true;
                    Window w = SwingUtilities.getWindowAncestor(tree);
                    if (w != null) {
                        w.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                Window w = SwingUtilities.getWindowAncestor(tree);
                if (w != null) {
                    w.setCursor(Cursor.getDefaultCursor());
                }
                if (dragging && draggingType != null && w != null) {
                    Point winPt = new Point(e.getLocationOnScreen());
                    SwingUtilities.convertPointFromScreen(winPt, w);
                    Component deepest = SwingUtilities.getDeepestComponentAt(w, winPt.x, winPt.y);
                    ScadaCanvas canvas = null;
                    for (Component c = deepest; c != null; c = c.getParent()) {
                        if (c instanceof ScadaCanvas sc) {
                            canvas = sc;
                            break;
                        }
                    }
                    if (canvas != null) {
                        Point canvasPt = new Point(e.getLocationOnScreen());
                        SwingUtilities.convertPointFromScreen(canvasPt, canvas);
                        canvas.dropSymbolAtPixel(draggingType, canvasPt.x, canvasPt.y);
                    }
                }
                draggingType = null;
                pressScreen = null;
                dragging = false;
            }
        };
        tree.addMouseListener(dragAdapter);
        tree.addMouseMotionListener(dragAdapter);

        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        return tree;
    }

    public static ScadaSymbolInstance createDefaultInstance(String symbolType, String name, int gridX, int gridY) {
        ScadaSymbolInstance sym = SymbolCatalog.createInstance(symbolType, gridX, gridY, 1);
        if (name != null && !name.isBlank()) {
            sym.setName(name);
        }
        return sym;
    }
}
