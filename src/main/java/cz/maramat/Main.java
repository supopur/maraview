package cz.maramat;

import com.formdev.flatlaf.FlatLightLaf;
import cz.maramat.scada.io.MslProjectSerializer;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.ui.MaraviewWindow;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
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
            UIManager.put("Button.arc", 0);
            UIManager.put("Component.arc", 0);
            UIManager.put("TextComponent.arc", 0);
            UIManager.put("ScrollBar.width", 14);

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
