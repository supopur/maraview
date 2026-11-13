package cz.maramat.scada.config;

import cz.maramat.scada.model.CommSettings;
import cz.maramat.scada.model.ScadaScreen;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent user-level preferences and workspace configuration for Maraview.
 * Stored independently of individual .msl project files.
 */
public class UserConfig {

    public enum Theme {
        LIGHT("Light (Classic)"),
        DARK("Dark (Control Room)"),
        INTELLIJ("IntelliJ Light"),
        DARCULA("Darcula Dark");

        private final String displayName;

        Theme(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public static final int[] SUPPORTED_ZOOMS = {50, 70, 80, 100, 120, 150, 200, 300};

    // Appearance & Window
    private Theme theme = Theme.LIGHT;
    private String uiFontFamily = "Segoe UI";
    private int uiFontSize = 11;
    private int windowWidth = 1280;
    private int windowHeight = 800;
    private boolean windowMaximized = false;
    private boolean rememberWindowBounds = true;
    private int leftDockWidth = 245;
    private boolean showStatusBar = true;
    private boolean showAlarmBanner = true;

    // Canvas & Editor
    private int defaultZoomPercent = 100;
    private String defaultScreenBackgroundHex = ScadaScreen.BG_LIGHT_SLATE;
    private boolean showGridByDefault = true;
    private String gridDotColorHex = "#CCCCCC";
    private int canvasGridWidth = 160;
    private int canvasGridHeight = 100;
    private boolean antiAliasing = true;
    private boolean mouseWheelZoom = false;
    private boolean confirmUnsavedChanges = true;

    // Simulation & Alarms
    private int runtimeSyncIntervalMs = 250;
    private int animationFps = 30;
    private boolean showFlowAnimation = true;
    private double flowAnimationSpeedPxPerSec = 36.0;
    private boolean autoStartSimulationInOperatorMode = true;
    private boolean resetIoOnSimStop = true;
    private boolean acknowledgeAlarmsOnSimStop = true;
    private boolean alarmFlashEnabled = true;

    // Files & Workspace
    private String defaultProjectDir = "";
    private String lastOpenedDir = "";
    private boolean reopenLastProjectOnStartup = false;
    private String lastProjectPath = "";
    private int maxRecentProjects = 8;
    private final List<String> recentProjects = new ArrayList<>();

    // Default Communication Settings for new projects
    private CommSettings defaultCommSettings = new CommSettings();

    public UserConfig copy() {
        UserConfig c = new UserConfig();
        c.theme = this.theme;
        c.uiFontFamily = this.uiFontFamily;
        c.uiFontSize = this.uiFontSize;
        c.windowWidth = this.windowWidth;
        c.windowHeight = this.windowHeight;
        c.windowMaximized = this.windowMaximized;
        c.rememberWindowBounds = this.rememberWindowBounds;
        c.leftDockWidth = this.leftDockWidth;
        c.showStatusBar = this.showStatusBar;
        c.showAlarmBanner = this.showAlarmBanner;

        c.defaultZoomPercent = this.defaultZoomPercent;
        c.defaultScreenBackgroundHex = this.defaultScreenBackgroundHex;
        c.showGridByDefault = this.showGridByDefault;
        c.gridDotColorHex = this.gridDotColorHex;
        c.canvasGridWidth = this.canvasGridWidth;
        c.canvasGridHeight = this.canvasGridHeight;
        c.antiAliasing = this.antiAliasing;
        c.mouseWheelZoom = this.mouseWheelZoom;
        c.confirmUnsavedChanges = this.confirmUnsavedChanges;

        c.runtimeSyncIntervalMs = this.runtimeSyncIntervalMs;
        c.animationFps = this.animationFps;
        c.showFlowAnimation = this.showFlowAnimation;
        c.flowAnimationSpeedPxPerSec = this.flowAnimationSpeedPxPerSec;
        c.autoStartSimulationInOperatorMode = this.autoStartSimulationInOperatorMode;
        c.resetIoOnSimStop = this.resetIoOnSimStop;
        c.acknowledgeAlarmsOnSimStop = this.acknowledgeAlarmsOnSimStop;
        c.alarmFlashEnabled = this.alarmFlashEnabled;

        c.defaultProjectDir = this.defaultProjectDir;
        c.lastOpenedDir = this.lastOpenedDir;
        c.reopenLastProjectOnStartup = this.reopenLastProjectOnStartup;
        c.lastProjectPath = this.lastProjectPath;
        c.maxRecentProjects = this.maxRecentProjects;
        c.recentProjects.clear();
        c.recentProjects.addAll(this.recentProjects);
        c.defaultCommSettings = this.defaultCommSettings.copy();
        return c;
    }

    public void addRecentProject(String path) {
        if (path == null || path.isBlank()) return;
        String clean = path.trim();
        recentProjects.remove(clean);
        if (maxRecentProjects > 0) {
            recentProjects.addFirst(clean);
            while (recentProjects.size() > maxRecentProjects) {
                recentProjects.removeLast();
            }
        }
        lastProjectPath = clean;
    }

    public void clearRecentProjects() {
        recentProjects.clear();
    }

    public Color getGridDotColor() {
        return ScadaScreen.parseHexColor(gridDotColorHex, new Color(0xCC, 0xCC, 0xCC));
    }

    public Color getDefaultScreenBackgroundColor() {
        return ScadaScreen.parseHexColor(defaultScreenBackgroundHex, new Color(0xE2, 0xE6, 0xEA));
    }

    public Theme getTheme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = (theme == null) ? Theme.LIGHT : theme;
    }

    public String getUiFontFamily() {
        return uiFontFamily;
    }

    public void setUiFontFamily(String uiFontFamily) {
        this.uiFontFamily = (uiFontFamily == null || uiFontFamily.isBlank()) ? "Segoe UI" : uiFontFamily.trim();
    }

    public int getUiFontSize() {
        return uiFontSize;
    }

    public void setUiFontSize(int uiFontSize) {
        this.uiFontSize = Math.max(9, Math.min(20, uiFontSize));
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(int windowWidth) {
        this.windowWidth = Math.max(640, Math.min(7680, windowWidth));
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(int windowHeight) {
        this.windowHeight = Math.max(480, Math.min(4320, windowHeight));
    }

    public boolean isWindowMaximized() {
        return windowMaximized;
    }

    public void setWindowMaximized(boolean windowMaximized) {
        this.windowMaximized = windowMaximized;
    }

    public boolean isRememberWindowBounds() {
        return rememberWindowBounds;
    }

    public void setRememberWindowBounds(boolean rememberWindowBounds) {
        this.rememberWindowBounds = rememberWindowBounds;
    }

    public int getLeftDockWidth() {
        return leftDockWidth;
    }

    public void setLeftDockWidth(int leftDockWidth) {
        this.leftDockWidth = Math.max(160, Math.min(500, leftDockWidth));
    }

    public boolean isShowStatusBar() {
        return showStatusBar;
    }

    public void setShowStatusBar(boolean showStatusBar) {
        this.showStatusBar = showStatusBar;
    }

    public boolean isShowAlarmBanner() {
        return showAlarmBanner;
    }

    public void setShowAlarmBanner(boolean showAlarmBanner) {
        this.showAlarmBanner = showAlarmBanner;
    }

    public int getDefaultZoomPercent() {
        return defaultZoomPercent;
    }

    public void setDefaultZoomPercent(int defaultZoomPercent) {
        int best = 100;
        int bestDiff = Integer.MAX_VALUE;
        for (int z : SUPPORTED_ZOOMS) {
            int diff = Math.abs(z - defaultZoomPercent);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = z;
            }
        }
        this.defaultZoomPercent = best;
    }

    public String getDefaultScreenBackgroundHex() {
        return defaultScreenBackgroundHex;
    }

    public void setDefaultScreenBackgroundHex(String defaultScreenBackgroundHex) {
        this.defaultScreenBackgroundHex = ScadaScreen.normalizeHexColor(
                defaultScreenBackgroundHex, ScadaScreen.BG_LIGHT_SLATE);
    }

    public boolean isShowGridByDefault() {
        return showGridByDefault;
    }

    public void setShowGridByDefault(boolean showGridByDefault) {
        this.showGridByDefault = showGridByDefault;
    }

    public String getGridDotColorHex() {
        return gridDotColorHex;
    }

    public void setGridDotColorHex(String gridDotColorHex) {
        this.gridDotColorHex = ScadaScreen.normalizeHexColor(gridDotColorHex, "#CCCCCC");
    }

    public int getCanvasGridWidth() {
        return canvasGridWidth;
    }

    public void setCanvasGridWidth(int canvasGridWidth) {
        this.canvasGridWidth = Math.max(40, Math.min(600, canvasGridWidth));
    }

    public int getCanvasGridHeight() {
        return canvasGridHeight;
    }

    public void setCanvasGridHeight(int canvasGridHeight) {
        this.canvasGridHeight = Math.max(30, Math.min(400, canvasGridHeight));
    }

    public boolean isAntiAliasing() {
        return antiAliasing;
    }

    public void setAntiAliasing(boolean antiAliasing) {
        this.antiAliasing = antiAliasing;
    }

    public boolean isMouseWheelZoom() {
        return mouseWheelZoom;
    }

    public void setMouseWheelZoom(boolean mouseWheelZoom) {
        this.mouseWheelZoom = mouseWheelZoom;
    }

    public boolean isConfirmUnsavedChanges() {
        return confirmUnsavedChanges;
    }

    public void setConfirmUnsavedChanges(boolean confirmUnsavedChanges) {
        this.confirmUnsavedChanges = confirmUnsavedChanges;
    }

    public int getRuntimeSyncIntervalMs() {
        return runtimeSyncIntervalMs;
    }

    public void setRuntimeSyncIntervalMs(int runtimeSyncIntervalMs) {
        this.runtimeSyncIntervalMs = Math.max(50, Math.min(5000, runtimeSyncIntervalMs));
    }

    public int getAnimationFps() {
        return animationFps;
    }

    public void setAnimationFps(int animationFps) {
        if (animationFps <= 15) {
            this.animationFps = 15;
        } else if (animationFps >= 60) {
            this.animationFps = 60;
        } else {
            this.animationFps = 30;
        }
    }

    public boolean isShowFlowAnimation() {
        return showFlowAnimation;
    }

    public void setShowFlowAnimation(boolean showFlowAnimation) {
        this.showFlowAnimation = showFlowAnimation;
    }

    public double getFlowAnimationSpeedPxPerSec() {
        return flowAnimationSpeedPxPerSec;
    }

    public void setFlowAnimationSpeedPxPerSec(double flowAnimationSpeedPxPerSec) {
        if (Double.isNaN(flowAnimationSpeedPxPerSec) || Double.isInfinite(flowAnimationSpeedPxPerSec)) {
            this.flowAnimationSpeedPxPerSec = 36.0;
        } else {
            this.flowAnimationSpeedPxPerSec = Math.max(5.0, Math.min(200.0, flowAnimationSpeedPxPerSec));
        }
    }

    public boolean isAutoStartSimulationInOperatorMode() {
        return autoStartSimulationInOperatorMode;
    }

    public void setAutoStartSimulationInOperatorMode(boolean autoStartSimulationInOperatorMode) {
        this.autoStartSimulationInOperatorMode = autoStartSimulationInOperatorMode;
    }

    public boolean isResetIoOnSimStop() {
        return resetIoOnSimStop;
    }

    public void setResetIoOnSimStop(boolean resetIoOnSimStop) {
        this.resetIoOnSimStop = resetIoOnSimStop;
    }

    public boolean isAcknowledgeAlarmsOnSimStop() {
        return acknowledgeAlarmsOnSimStop;
    }

    public void setAcknowledgeAlarmsOnSimStop(boolean acknowledgeAlarmsOnSimStop) {
        this.acknowledgeAlarmsOnSimStop = acknowledgeAlarmsOnSimStop;
    }

    public boolean isAlarmFlashEnabled() {
        return alarmFlashEnabled;
    }

    public void setAlarmFlashEnabled(boolean alarmFlashEnabled) {
        this.alarmFlashEnabled = alarmFlashEnabled;
    }

    public String getDefaultProjectDir() {
        return defaultProjectDir;
    }

    public void setDefaultProjectDir(String defaultProjectDir) {
        this.defaultProjectDir = (defaultProjectDir == null) ? "" : defaultProjectDir.trim();
    }

    public String getLastOpenedDir() {
        return lastOpenedDir;
    }

    public void setLastOpenedDir(String lastOpenedDir) {
        this.lastOpenedDir = (lastOpenedDir == null) ? "" : lastOpenedDir.trim();
    }

    public boolean isReopenLastProjectOnStartup() {
        return reopenLastProjectOnStartup;
    }

    public void setReopenLastProjectOnStartup(boolean reopenLastProjectOnStartup) {
        this.reopenLastProjectOnStartup = reopenLastProjectOnStartup;
    }

    public String getLastProjectPath() {
        return lastProjectPath;
    }

    public void setLastProjectPath(String lastProjectPath) {
        this.lastProjectPath = (lastProjectPath == null) ? "" : lastProjectPath.trim();
    }

    public int getMaxRecentProjects() {
        return maxRecentProjects;
    }

    public void setMaxRecentProjects(int maxRecentProjects) {
        this.maxRecentProjects = Math.max(0, Math.min(20, maxRecentProjects));
        while (recentProjects.size() > this.maxRecentProjects) {
            recentProjects.removeLast();
        }
    }

    public List<String> getRecentProjects() {
        return recentProjects;
    }

    public CommSettings getDefaultCommSettings() {
        return defaultCommSettings;
    }

    public void setDefaultCommSettings(CommSettings defaultCommSettings) {
        this.defaultCommSettings = (defaultCommSettings == null) ? new CommSettings() : defaultCommSettings;
    }
}
