package cz.maramat.scada.ui;

import cz.maramat.scada.model.ScadaGraphicElement;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;

/**
 * Properties dialog for ScadaGraphicElement (shapes, text labels, and embedded pictures),
 * matching MaraFlow's VisualElementDialogs.showGraphicPropertiesDialog.
 */
public final class GraphicElementPropertiesDialog {

    private GraphicElementPropertiesDialog() {
    }

    public static boolean showDialog(Window owner, ScadaGraphicElement elem) {
        JDialog dlg = new JDialog(owner, elem.getKind().getDisplayName() + " Properties",
                JDialog.ModalityType.APPLICATION_MODAL);
        final boolean[] confirmed = {false};

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        final Color[] strokeCol = {elem.getStrokeColor()};
        final Color[] fillCol = {elem.getFillColor()};

        JTextField textField = new JTextField(elem.getText(), 18);
        JSpinner fontSpin = new JSpinner(new SpinnerNumberModel(elem.getFontSize(), 6, 96, 1));
        JSpinner strokeSpin = new JSpinner(new SpinnerNumberModel(elem.getStrokeWidth(), 1, 12, 1));
        JCheckBox dashedChk = new JCheckBox("Dashed Stroke", elem.isDashed());
        JCheckBox filledChk = new JCheckBox("Filled", elem.isFilled());
        JSpinner sidesSpin = new JSpinner(new SpinnerNumberModel(elem.getNgonSides(), 3, 24, 1));
        JSlider opacitySlider = new JSlider(10, 100, (int) Math.round(elem.getOpacity() * 100.0));
        JCheckBox aspectChk = new JCheckBox("Preserve Aspect Ratio", elem.isPreserveAspectRatio());
        JTextField imgPathField = new JTextField(elem.getImagePath(), 14);
        imgPathField.setEditable(false);
        final String[] imgBase64 = {elem.getImageBase64()};

        if (elem.getKind() == ScadaGraphicElement.Kind.TEXT) {
            addRow(form, gbc, row++, "Text:", textField);
            addRow(form, gbc, row++, "Font Size:", fontSpin);
        }

        if (elem.getKind() == ScadaGraphicElement.Kind.IMAGE) {
            JPanel imgRow = new JPanel(new BorderLayout(4, 0));
            imgRow.add(imgPathField, BorderLayout.CENTER);
            JButton browseBtn = new JButton("Choose Image...");
            browseBtn.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                fc.setFileFilter(new FileNameExtensionFilter("Images (PNG, JPG, GIF)", "png", "jpg", "jpeg", "gif"));
                if (fc.showOpenDialog(dlg) == JFileChooser.APPROVE_OPTION) {
                    File f = fc.getSelectedFile();
                    try {
                        byte[] bytes = Files.readAllBytes(f.toPath());
                        imgBase64[0] = Base64.getEncoder().encodeToString(bytes);
                        imgPathField.setText(f.getName());
                    } catch (Exception ignored) {
                    }
                }
            });
            imgRow.add(browseBtn, BorderLayout.EAST);
            addRow(form, gbc, row++, "Picture:", imgRow);
            addRow(form, gbc, row++, "Scaling:", aspectChk);
        } else {
            JButton strokeBtn = new JButton("Choose Color...");
            strokeBtn.setBackground(strokeCol[0]);
            strokeBtn.addActionListener(e -> {
                Color c = JColorChooser.showDialog(dlg, "Stroke / Text Color", strokeCol[0]);
                if (c != null) {
                    strokeCol[0] = c;
                    strokeBtn.setBackground(c);
                }
            });
            addRow(form, gbc, row++, elem.getKind() == ScadaGraphicElement.Kind.TEXT ? "Text Color:" : "Stroke Color:", strokeBtn);
            addRow(form, gbc, row++, "Stroke Width:", strokeSpin);
            if (elem.getKind() != ScadaGraphicElement.Kind.TEXT) {
                addRow(form, gbc, row++, "Style:", dashedChk);
            }
            if (elem.getKind() != ScadaGraphicElement.Kind.LINE) {
                JPanel fillPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                fillPanel.add(filledChk);
                JButton fillBtn = new JButton("Fill Color...");
                fillBtn.setBackground(fillCol[0]);
                fillBtn.addActionListener(e -> {
                    Color c = JColorChooser.showDialog(dlg, "Fill Color", fillCol[0]);
                    if (c != null) {
                        fillCol[0] = c;
                        fillBtn.setBackground(c);
                    }
                });
                fillPanel.add(fillBtn);
                addRow(form, gbc, row++, "Fill:", fillPanel);
            }
            if (elem.getKind() == ScadaGraphicElement.Kind.CUSTOM_NGON) {
                addRow(form, gbc, row++, "Polygon Sides:", sidesSpin);
            }
        }
        addRow(form, gbc, row, "Opacity (%):", opacitySlider);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");
        okBtn.addActionListener(e -> {
            elem.setStrokeColor(strokeCol[0]);
            elem.setFillColor(fillCol[0]);
            elem.setFilled(filledChk.isSelected());
            elem.setStrokeWidth((Integer) strokeSpin.getValue());
            elem.setDashed(dashedChk.isSelected());
            elem.setNgonSides((Integer) sidesSpin.getValue());
            elem.setText(textField.getText());
            elem.setFontSize((Integer) fontSpin.getValue());
            elem.setImageBase64(imgBase64[0]);
            elem.setImagePath(imgPathField.getText());
            elem.setPreserveAspectRatio(aspectChk.isSelected());
            elem.setOpacity(opacitySlider.getValue() / 100.0);
            confirmed[0] = true;
            dlg.dispose();
        });
        cancelBtn.addActionListener(e -> dlg.dispose());
        btnBar.add(okBtn);
        btnBar.add(cancelBtn);

        dlg.setLayout(new BorderLayout());
        dlg.add(form, BorderLayout.CENTER);
        dlg.add(btnBar, BorderLayout.SOUTH);
        dlg.getRootPane().setDefaultButton(okBtn);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
        return confirmed[0];
    }

    private static void addRow(JPanel p, GridBagConstraints gbc, int row, String label, java.awt.Component comp) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0.0;
        p.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        p.add(comp, gbc);
    }
}
