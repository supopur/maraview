package cz.maramat.scada.ui;

import com.formdev.flatlaf.FlatLaf;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Utilitarian Homescreen displayed when no SCADA project is open, matching Maraflow's layout.
 */
public class HomeScreenPanel extends JPanel {

    public interface HomeScreenListener {
        void onNewProjectWizard(boolean promptMflLink);
        void onOpenProjectDialog();
        void onOpenRecentFile(File file);
        void onClearRecentFiles();
    }

    private final HomeScreenListener listener;
    private JPanel topBar;
    private JPanel recentBox;
    private DefaultTableModel recentTableModel;
    private JTable recentTable;
    private List<File> currentRecentFiles = List.of();

    public HomeScreenPanel(HomeScreenListener listener) {
        super(new BorderLayout(0, 8));
        this.listener = listener;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        buildUi();
        applyThemeColors();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyThemeColors();
    }

    private void applyThemeColors() {
        boolean dark = FlatLaf.isLafDark();
        setBackground(dark ? new Color(0x1B, 0x1D, 0x21) : new Color(0xEC, 0xF0, 0xF5));
        if (topBar != null) {
            topBar.setBackground(dark ? new Color(0x23, 0x26, 0x2B) : Color.WHITE);
            topBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(dark ? new Color(0x45, 0x4B, 0x54) : new Color(0xB8, 0xB8, 0xB8), 1),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)
            ));
        }
        if (recentBox != null) {
            recentBox.setBackground(dark ? new Color(0x23, 0x26, 0x2B) : Color.WHITE);
        }
    }

    private void buildUi() {
        topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));

        JButton newProjBtn = createCompactButton("New Project... (Ctrl+N)");
        newProjBtn.addActionListener(e -> listener.onNewProjectWizard(false));

        JButton newFromMflBtn = createCompactButton("New from Maraflow (.mfl)...");
        newFromMflBtn.addActionListener(e -> listener.onNewProjectWizard(true));

        JButton openBtn = createCompactButton("Open Project... (Ctrl+O)");
        openBtn.addActionListener(e -> listener.onOpenProjectDialog());

        topBar.add(newProjBtn);
        topBar.add(newFromMflBtn);
        topBar.add(openBtn);
        add(topBar, BorderLayout.NORTH);

        recentBox = new JPanel(new BorderLayout(0, 6));
        recentBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Recent Projects (.msl)"),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

        recentTableModel = new DefaultTableModel(new Object[]{"Project Name", "File Path", "Last Modified"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        recentTable = new JTable(recentTableModel);
        recentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recentTable.setRowHeight(22);
        recentTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        recentTable.getColumnModel().getColumn(1).setPreferredWidth(420);
        recentTable.getColumnModel().getColumn(2).setPreferredWidth(140);

        recentTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedRecent();
                }
            }
        });

        recentBox.add(new JScrollPane(recentTable), BorderLayout.CENTER);

        JPanel recentBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        recentBtns.setOpaque(false);
        JButton openSelBtn = new JButton("Open Selected");
        openSelBtn.addActionListener(e -> openSelectedRecent());
        JButton clearBtn = new JButton("Clear Recent");
        clearBtn.addActionListener(e -> listener.onClearRecentFiles());
        recentBtns.add(openSelBtn);
        recentBtns.add(clearBtn);
        recentBox.add(recentBtns, BorderLayout.SOUTH);

        add(recentBox, BorderLayout.CENTER);
    }

    private static JButton createCompactButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 11));
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(190, 28));
        return b;
    }

    private void openSelectedRecent() {
        int r = recentTable.getSelectedRow();
        if (r >= 0 && r < currentRecentFiles.size()) {
            listener.onOpenRecentFile(currentRecentFiles.get(r));
        }
    }

    public void setRecentPaths(List<String> paths) {
        List<File> files = new ArrayList<>();
        if (paths != null) {
            for (String p : paths) {
                if (p != null && !p.isBlank()) {
                    files.add(new File(p));
                }
            }
        }
        setRecentFiles(files);
    }

    public void setRecentFiles(List<File> files) {
        this.currentRecentFiles = List.copyOf(files);
        recentTableModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (File f : currentRecentFiles) {
            String name = f.getName();
            String mod = f.exists() ? sdf.format(new Date(f.lastModified())) : "Missing";
            recentTableModel.addRow(new Object[]{name, f.getAbsolutePath(), mod});
        }
    }
}
