package cz.maramat;

import com.formdev.flatlaf.FlatLightLaf;
import cz.maramat.scada.io.MslProjectSerializer;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.ui.MaraviewWindow;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "Maraview");
        boolean operatorMode = false;
        File projectFile = null;
        if (args != null) {
            for (String arg : args) {
                if ("--operator".equals(arg)) {
                    operatorMode = true;
                } else if (!arg.startsWith("-") && projectFile == null) {
                    projectFile = new File(arg);
                }
            }
        }

        final boolean opModeFinal = operatorMode;
        final File fileFinal = projectFile;

        SwingUtilities.invokeLater(() -> {
            FlatLightLaf.setup();

            // Utilitarian Win95 / Win7 crossover CAD / PLC engineering look-and-feel (matching MaraFlow)
            UIManager.put("Component.arrowType", "triangle");
            UIManager.put("Button.arc", 0);
            UIManager.put("Component.arc", 0);
            UIManager.put("CheckBox.arc", 0);
            UIManager.put("ProgressBar.arc", 0);
            UIManager.put("TextComponent.arc", 0);
            UIManager.put("ScrollBar.width", 14);
            UIManager.put("ScrollBar.showButtons", true);
            UIManager.put("TabbedPane.tabHeight", 22);
            UIManager.put("TabbedPane.showTabSeparators", true);
            UIManager.put("TabbedPane.tabInsets", new Insets(2, 8, 2, 8));
            UIManager.put("TabbedPane.selectedBackground", Color.WHITE);
            UIManager.put("Tree.paintLines", true);
            UIManager.put("Tree.lineTypeDashed", true);
            UIManager.put("Tree.hash", new Color(0x90, 0x90, 0x90));
            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("Table.showVerticalLines", true);
            UIManager.put("defaultFont", new Font("Segoe UI", Font.PLAIN, 11));

            ScadaProject project = null;
            if (fileFinal != null && fileFinal.isFile()) {
                try {
                    project = MslProjectSerializer.loadProject(fileFinal);
                } catch (IOException ex) {
                    System.err.println("Failed to load project " + fileFinal + ": " + ex.getMessage());
                }
            }
            MaraviewWindow window = new MaraviewWindow(project, opModeFinal);
            window.setVisible(true);
        });
    }
}
