package cz.maramat;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;
import cz.maramat.scada.config.UserConfig;
import cz.maramat.scada.config.UserConfigStore;
import cz.maramat.scada.io.MslProjectSerializer;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.ui.DisplayWakeRepaintFix;
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
            DisplayWakeRepaintFix.install();
            UserConfig userConfig = UserConfigStore.load();
            applyLookAndFeel(userConfig);

            File toLoad = fileFinal;
            if (toLoad == null && userConfig.isReopenLastProjectOnStartup()
                    && !userConfig.getLastProjectPath().isBlank()) {
                File last = new File(userConfig.getLastProjectPath());
                if (last.isFile()) {
                    toLoad = last;
                }
            }

            ScadaProject project = null;
            if (toLoad != null && toLoad.isFile()) {
                try {
                    project = MslProjectSerializer.loadProject(toLoad);
                    userConfig.addRecentProject(toLoad.getAbsolutePath());
                } catch (IOException ex) {
                    System.err.println("Failed to load project " + toLoad + ": " + ex.getMessage());
                }
            }
            MaraviewWindow window = new MaraviewWindow(project, opModeFinal, userConfig);
            window.setVisible(true);
        });
    }

    public static void applyLookAndFeel(UserConfig config) {
        UserConfig.Theme theme = (config != null) ? config.getTheme() : UserConfig.Theme.LIGHT;
        switch (theme) {
            case DARK -> FlatDarkLaf.setup();
            case INTELLIJ -> FlatIntelliJLaf.setup();
            case DARCULA -> FlatDarculaLaf.setup();
            case LIGHT -> FlatLightLaf.setup();
        }

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
        if (theme == UserConfig.Theme.LIGHT || theme == UserConfig.Theme.INTELLIJ) {
            clearDarkContrastOverrides();
            UIManager.put("TabbedPane.selectedBackground", Color.WHITE);
        } else {
            applyDarkContrastOverrides();
        }
        UIManager.put("Tree.paintLines", true);
        UIManager.put("Tree.lineTypeDashed", true);
        UIManager.put("Tree.hash", new Color(0x90, 0x90, 0x90));
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", true);

        String fontFamily = (config != null) ? config.getUiFontFamily() : "Segoe UI";
        int fontSize = (config != null) ? config.getUiFontSize() : 11;
        UIManager.put("defaultFont", new Font(fontFamily, Font.PLAIN, fontSize));
    }

    private static final String[] DARK_FG_KEYS = {
            "foreground", "text", "Label.foreground", "Button.foreground", "ToggleButton.foreground",
            "CheckBox.foreground", "RadioButton.foreground", "ComboBox.foreground",
            "TextField.foreground", "TextArea.foreground", "FormattedTextField.foreground",
            "PasswordField.foreground", "Spinner.foreground", "List.foreground",
            "Table.foreground", "TableHeader.foreground", "Tree.foreground", "Tree.textForeground",
            "MenuBar.foreground", "Menu.foreground", "MenuItem.foreground",
            "CheckBoxMenuItem.foreground", "RadioButtonMenuItem.foreground", "PopupMenu.foreground",
            "TabbedPane.foreground", "TabbedPane.selectedForeground", "TitledBorder.titleColor",
            "ToolTip.foreground"
    };

    private static final String[] DARK_DISABLED_FG_KEYS = {
            "Component.disabledForeground", "Label.disabledForeground",
            "Button.disabledText", "MenuItem.disabledForeground"
    };

    private static final String[] DARK_PANEL_BG_KEYS = {
            "Panel.background", "OptionPane.background", "ScrollPane.background",
            "SplitPane.background", "MenuBar.background", "ToolBar.background"
    };

    private static final String[] DARK_FIELD_BG_KEYS = {
            "Table.background", "Tree.background", "List.background",
            "TextField.background", "TextArea.background", "FormattedTextField.background",
            "PasswordField.background", "Spinner.background"
    };

    private static void applyDarkContrastOverrides() {
        Color whiteFg = new Color(0xF5, 0xF7, 0xFA);
        Color disabledFg = new Color(0xA6, 0xAE, 0xBA);
        Color panelBg = new Color(0x23, 0x26, 0x2B);
        Color fieldBg = new Color(0x1B, 0x1D, 0x21);

        for (String k : DARK_FG_KEYS) UIManager.put(k, whiteFg);
        for (String k : DARK_DISABLED_FG_KEYS) UIManager.put(k, disabledFg);
        for (String k : DARK_PANEL_BG_KEYS) UIManager.put(k, panelBg);
        for (String k : DARK_FIELD_BG_KEYS) UIManager.put(k, fieldBg);

        UIManager.put("TableHeader.background", new Color(0x2B, 0x2F, 0x36));
        UIManager.put("TabbedPane.selectedBackground", new Color(0x30, 0x35, 0x3E));
        UIManager.put("Separator.foreground", new Color(0x45, 0x4B, 0x54));
        UIManager.put("Component.borderColor", new Color(0x45, 0x4B, 0x54));
    }

    private static void clearDarkContrastOverrides() {
        for (String k : DARK_FG_KEYS) UIManager.put(k, null);
        for (String k : DARK_DISABLED_FG_KEYS) UIManager.put(k, null);
        for (String k : DARK_PANEL_BG_KEYS) UIManager.put(k, null);
        for (String k : DARK_FIELD_BG_KEYS) UIManager.put(k, null);
        UIManager.put("TableHeader.background", null);
        UIManager.put("Separator.foreground", null);
        UIManager.put("Component.borderColor", null);
    }
}
