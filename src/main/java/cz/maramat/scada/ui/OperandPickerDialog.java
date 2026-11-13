package cz.maramat.scada.ui;

import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.tags.ScadaTag;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;

/**
 * Searchable dialog for selecting a ScadaTag by name, type, or source.
 */
public class OperandPickerDialog extends JDialog {

    private final ScadaProject project;
    private final JTextField filterField = new JTextField(20);
    private final DefaultTableModel model;
    private final JTable table;
    private String selectedTagName = null;

    public OperandPickerDialog(Window owner, ScadaProject project, String initialTag) {
        super(owner, "Select Tag / Operand", ModalityType.APPLICATION_MODAL);
        this.project = project;
        setSize(520, 360);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(6, 6));

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setBorder(BorderFactory.createEmptyBorder(6, 6, 0, 6));
        top.add(new JLabel("Filter:"), BorderLayout.WEST);
        top.add(filterField, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        model = new DefaultTableModel(new Object[]{"Name", "Type", "Source", "Description"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(20);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    acceptSelection();
                }
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(e -> acceptSelection());
        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> {
            selectedTagName = "";
            dispose();
        });
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        bottom.add(clearBtn);
        bottom.add(okBtn);
        bottom.add(cancelBtn);
        add(bottom, BorderLayout.SOUTH);

        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refreshRows(); }
            @Override public void removeUpdate(DocumentEvent e) { refreshRows(); }
            @Override public void changedUpdate(DocumentEvent e) { refreshRows(); }
        });

        refreshRows();
        if (initialTag != null && !initialTag.isBlank()) {
            for (int i = 0; i < model.getRowCount(); i++) {
                if (initialTag.equals(model.getValueAt(i, 0))) {
                    table.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }
    }

    private void refreshRows() {
        model.setRowCount(0);
        if (project == null) return;
        String q = filterField.getText().trim().toLowerCase(Locale.ROOT);
        for (ScadaTag tag : project.getTags().values()) {
            if (!q.isEmpty()
                    && !tag.getName().toLowerCase(Locale.ROOT).contains(q)
                    && !tag.getDataType().name().toLowerCase(Locale.ROOT).contains(q)
                    && !tag.getDescription().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            model.addRow(new Object[]{
                    tag.getName(),
                    tag.getDataType().name(),
                    tag.getSourceType().name(),
                    tag.getDescription()
            });
        }
    }

    private void acceptSelection() {
        int row = table.getSelectedRow();
        if (row >= 0) {
            selectedTagName = String.valueOf(model.getValueAt(row, 0));
            dispose();
        }
    }

    public String getSelectedTagName() {
        return selectedTagName;
    }
}
