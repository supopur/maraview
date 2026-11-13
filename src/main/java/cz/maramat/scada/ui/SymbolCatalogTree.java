package cz.maramat.scada.ui;

import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.model.SymbolCatalog;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
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

        // MaraFlow-style drag-and-drop for leaf nodes
        tree.setDragEnabled(true);
        tree.setTransferHandler(new TransferHandler() {
            {
                BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = img.createGraphics();
                g2.setColor(new Color(0x00, 0x78, 0xD7, 180));
                g2.fillRect(1, 1, 14, 14);
                g2.setColor(Color.WHITE);
                g2.drawRect(1, 1, 13, 13);
                g2.dispose();
                setDragImage(img);
            }

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
        ScadaSymbolInstance sym = SymbolCatalog.createInstance(symbolType, gridX, gridY, 1);
        if (name != null && !name.isBlank()) {
            sym.setName(name);
        }
        return sym;
    }
}
