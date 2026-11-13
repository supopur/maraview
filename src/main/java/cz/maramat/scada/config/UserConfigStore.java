package cz.maramat.scada.config;

import cz.maramat.scada.io.JsonCodec;
import cz.maramat.scada.model.CommSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and saves {@link UserConfig} to JSON in ~/.maraview/config.json (or a custom path).
 */
public final class UserConfigStore {

    private static final Path DEFAULT_PATH = Path.of(
            System.getProperty("user.home"), ".maraview", "config.json");

    private UserConfigStore() {
    }

    public static Path getDefaultConfigPath() {
        return DEFAULT_PATH;
    }

    public static UserConfig load() {
        return load(DEFAULT_PATH);
    }

    public static UserConfig load(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return new UserConfig();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            return fromJsonString(json);
        } catch (IOException ex) {
            System.err.println("Failed to read user config from " + path + ": " + ex.getMessage());
            return new UserConfig();
        }
    }

    public static void save(UserConfig config) throws IOException {
        save(config, DEFAULT_PATH);
    }

    public static void save(UserConfig config, Path path) throws IOException {
        if (config == null) {
            throw new IllegalArgumentException("UserConfig cannot be null.");
        }
        if (path == null) {
            throw new IllegalArgumentException("Config path cannot be null.");
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, toJsonString(config), StandardCharsets.UTF_8);
    }

    public static String toJsonString(UserConfig config) {
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> appearance = new LinkedHashMap<>();
        appearance.put("theme", config.getTheme().name());
        appearance.put("uiFontFamily", config.getUiFontFamily());
        appearance.put("uiFontSize", config.getUiFontSize());
        appearance.put("windowWidth", config.getWindowWidth());
        appearance.put("windowHeight", config.getWindowHeight());
        appearance.put("windowMaximized", config.isWindowMaximized());
        appearance.put("rememberWindowBounds", config.isRememberWindowBounds());
        appearance.put("leftDockWidth", config.getLeftDockWidth());
        appearance.put("showStatusBar", config.isShowStatusBar());
        appearance.put("showAlarmBanner", config.isShowAlarmBanner());
        root.put("appearance", appearance);

        Map<String, Object> canvas = new LinkedHashMap<>();
        canvas.put("defaultZoomPercent", config.getDefaultZoomPercent());
        canvas.put("defaultScreenBackgroundHex", config.getDefaultScreenBackgroundHex());
        canvas.put("showGridByDefault", config.isShowGridByDefault());
        canvas.put("gridDotColorHex", config.getGridDotColorHex());
        canvas.put("canvasGridWidth", config.getCanvasGridWidth());
        canvas.put("canvasGridHeight", config.getCanvasGridHeight());
        canvas.put("antiAliasing", config.isAntiAliasing());
        canvas.put("mouseWheelZoom", config.isMouseWheelZoom());
        canvas.put("confirmUnsavedChanges", config.isConfirmUnsavedChanges());
        root.put("canvas", canvas);

        Map<String, Object> simulation = new LinkedHashMap<>();
        simulation.put("runtimeSyncIntervalMs", config.getRuntimeSyncIntervalMs());
        simulation.put("showFlowAnimation", config.isShowFlowAnimation());
        simulation.put("flowAnimationSpeedPxPerSec", config.getFlowAnimationSpeedPxPerSec());
        simulation.put("autoStartSimulationInOperatorMode", config.isAutoStartSimulationInOperatorMode());
        simulation.put("resetIoOnSimStop", config.isResetIoOnSimStop());
        simulation.put("acknowledgeAlarmsOnSimStop", config.isAcknowledgeAlarmsOnSimStop());
        simulation.put("alarmFlashEnabled", config.isAlarmFlashEnabled());
        root.put("simulation", simulation);

        Map<String, Object> workspace = new LinkedHashMap<>();
        workspace.put("defaultProjectDir", config.getDefaultProjectDir());
        workspace.put("lastOpenedDir", config.getLastOpenedDir());
        workspace.put("reopenLastProjectOnStartup", config.isReopenLastProjectOnStartup());
        workspace.put("lastProjectPath", config.getLastProjectPath());
        workspace.put("maxRecentProjects", config.getMaxRecentProjects());
        workspace.put("recentProjects", new ArrayList<>(config.getRecentProjects()));
        root.put("workspace", workspace);

        CommSettings comm = config.getDefaultCommSettings();
        Map<String, Object> commMap = new LinkedHashMap<>();
        commMap.put("mode", comm.getMode().name());
        commMap.put("host", comm.getHost());
        commMap.put("tcpPort", comm.getTcpPort());
        commMap.put("serialPort", comm.getSerialPort());
        commMap.put("baudRate", comm.getBaudRate());
        commMap.put("slaveId", comm.getSlaveId());
        commMap.put("pollIntervalMs", comm.getPollIntervalMs());
        root.put("defaultCommSettings", commMap);

        return JsonCodec.stringify(root);
    }

    public static UserConfig fromJsonString(String json) throws IOException {
        Map<String, Object> root = JsonCodec.parseObject(json);
        UserConfig config = new UserConfig();

        Map<String, Object> appearance = getMap(root, "appearance");
        if (appearance != null) {
            config.setTheme(parseEnum(UserConfig.Theme.class, getString(appearance, "theme", "LIGHT"), UserConfig.Theme.LIGHT));
            config.setUiFontFamily(getString(appearance, "uiFontFamily", config.getUiFontFamily()));
            config.setUiFontSize(getInt(appearance, "uiFontSize", config.getUiFontSize()));
            config.setWindowWidth(getInt(appearance, "windowWidth", config.getWindowWidth()));
            config.setWindowHeight(getInt(appearance, "windowHeight", config.getWindowHeight()));
            config.setWindowMaximized(getBoolean(appearance, "windowMaximized", config.isWindowMaximized()));
            config.setRememberWindowBounds(getBoolean(appearance, "rememberWindowBounds", config.isRememberWindowBounds()));
            config.setLeftDockWidth(getInt(appearance, "leftDockWidth", config.getLeftDockWidth()));
            config.setShowStatusBar(getBoolean(appearance, "showStatusBar", config.isShowStatusBar()));
            config.setShowAlarmBanner(getBoolean(appearance, "showAlarmBanner", config.isShowAlarmBanner()));
        }

        Map<String, Object> canvas = getMap(root, "canvas");
        if (canvas != null) {
            config.setDefaultZoomPercent(getInt(canvas, "defaultZoomPercent", config.getDefaultZoomPercent()));
            config.setDefaultScreenBackgroundHex(getString(canvas, "defaultScreenBackgroundHex", config.getDefaultScreenBackgroundHex()));
            config.setShowGridByDefault(getBoolean(canvas, "showGridByDefault", config.isShowGridByDefault()));
            config.setGridDotColorHex(getString(canvas, "gridDotColorHex", config.getGridDotColorHex()));
            config.setCanvasGridWidth(getInt(canvas, "canvasGridWidth", config.getCanvasGridWidth()));
            config.setCanvasGridHeight(getInt(canvas, "canvasGridHeight", config.getCanvasGridHeight()));
            config.setAntiAliasing(getBoolean(canvas, "antiAliasing", config.isAntiAliasing()));
            config.setMouseWheelZoom(getBoolean(canvas, "mouseWheelZoom", config.isMouseWheelZoom()));
            config.setConfirmUnsavedChanges(getBoolean(canvas, "confirmUnsavedChanges", config.isConfirmUnsavedChanges()));
        }

        Map<String, Object> simulation = getMap(root, "simulation");
        if (simulation != null) {
            config.setRuntimeSyncIntervalMs(getInt(simulation, "runtimeSyncIntervalMs", config.getRuntimeSyncIntervalMs()));
            config.setShowFlowAnimation(getBoolean(simulation, "showFlowAnimation", config.isShowFlowAnimation()));
            config.setFlowAnimationSpeedPxPerSec(getDouble(simulation, "flowAnimationSpeedPxPerSec", config.getFlowAnimationSpeedPxPerSec()));
            config.setAutoStartSimulationInOperatorMode(getBoolean(simulation, "autoStartSimulationInOperatorMode", config.isAutoStartSimulationInOperatorMode()));
            config.setResetIoOnSimStop(getBoolean(simulation, "resetIoOnSimStop", config.isResetIoOnSimStop()));
            config.setAcknowledgeAlarmsOnSimStop(getBoolean(simulation, "acknowledgeAlarmsOnSimStop", config.isAcknowledgeAlarmsOnSimStop()));
            config.setAlarmFlashEnabled(getBoolean(simulation, "alarmFlashEnabled", config.isAlarmFlashEnabled()));
        }

        Map<String, Object> workspace = getMap(root, "workspace");
        if (workspace != null) {
            config.setDefaultProjectDir(getString(workspace, "defaultProjectDir", config.getDefaultProjectDir()));
            config.setLastOpenedDir(getString(workspace, "lastOpenedDir", config.getLastOpenedDir()));
            config.setReopenLastProjectOnStartup(getBoolean(workspace, "reopenLastProjectOnStartup", config.isReopenLastProjectOnStartup()));
            config.setLastProjectPath(getString(workspace, "lastProjectPath", config.getLastProjectPath()));
            config.setMaxRecentProjects(getInt(workspace, "maxRecentProjects", config.getMaxRecentProjects()));
            Object recentObj = workspace.get("recentProjects");
            if (recentObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null) {
                        String s = String.valueOf(item).trim();
                        if (!s.isEmpty() && !config.getRecentProjects().contains(s)
                                && config.getRecentProjects().size() < config.getMaxRecentProjects()) {
                            config.getRecentProjects().add(s);
                        }
                    }
                }
            }
        }

        Map<String, Object> commMap = getMap(root, "defaultCommSettings");
        if (commMap != null) {
            CommSettings comm = new CommSettings();
            comm.setMode(parseEnum(CommSettings.Mode.class, getString(commMap, "mode", "INTERNAL_SIM"), CommSettings.Mode.INTERNAL_SIM));
            comm.setHost(getString(commMap, "host", comm.getHost()));
            comm.setTcpPort(getInt(commMap, "tcpPort", comm.getTcpPort()));
            comm.setSerialPort(getString(commMap, "serialPort", comm.getSerialPort()));
            comm.setBaudRate(getInt(commMap, "baudRate", comm.getBaudRate()));
            comm.setSlaveId(getInt(commMap, "slaveId", comm.getSlaveId()));
            comm.setPollIntervalMs(getInt(commMap, "pollIntervalMs", comm.getPollIntervalMs()));
            config.setDefaultCommSettings(comm);
        }

        return config;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> parent, String key) {
        Object val = parent.get(key);
        if (val instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return null;
    }

    private static String getString(Map<String, Object> map, String key, String fallback) {
        Object val = map.get(key);
        return (val == null) ? fallback : String.valueOf(val);
    }

    private static int getInt(Map<String, Object> map, String key, int fallback) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static double getDouble(Map<String, Object> map, String key, double fallback) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        if (val instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean fallback) {
        Object val = map.get(key);
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        return fallback;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String name, E fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            return Enum.valueOf(enumType, name.trim());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
