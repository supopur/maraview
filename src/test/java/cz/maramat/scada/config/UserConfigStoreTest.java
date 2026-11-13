package cz.maramat.scada.config;

import cz.maramat.scada.model.CommSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserConfigStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripSaveAndLoadPreservesAllSettings() throws Exception {
        UserConfig cfg = new UserConfig();
        cfg.setTheme(UserConfig.Theme.DARK);
        cfg.setUiFontFamily("Monospaced");
        cfg.setUiFontSize(13);
        cfg.setWindowWidth(1600);
        cfg.setWindowHeight(960);
        cfg.setWindowMaximized(true);
        cfg.setRememberWindowBounds(false);
        cfg.setLeftDockWidth(310);
        cfg.setShowStatusBar(false);
        cfg.setShowAlarmBanner(false);

        cfg.setDefaultZoomPercent(150);
        cfg.setDefaultScreenBackgroundHex("#2B3036");
        cfg.setShowGridByDefault(false);
        cfg.setGridDotColorHex("#556677");
        cfg.setCanvasGridWidth(220);
        cfg.setCanvasGridHeight(140);
        cfg.setAntiAliasing(false);
        cfg.setMouseWheelZoom(true);
        cfg.setConfirmUnsavedChanges(false);

        cfg.setRuntimeSyncIntervalMs(125);
        cfg.setShowFlowAnimation(false);
        cfg.setFlowAnimationSpeedPxPerSec(72.5);
        cfg.setAutoStartSimulationInOperatorMode(false);
        cfg.setResetIoOnSimStop(false);
        cfg.setAcknowledgeAlarmsOnSimStop(false);
        cfg.setAlarmFlashEnabled(false);

        cfg.setDefaultProjectDir("/tmp/scada-projects");
        cfg.setLastOpenedDir("/tmp/scada-projects/plant1");
        cfg.setReopenLastProjectOnStartup(true);
        cfg.setMaxRecentProjects(5);
        cfg.addRecentProject("/tmp/a.msl");
        cfg.addRecentProject("/tmp/b.msl");

        CommSettings comm = cfg.getDefaultCommSettings();
        comm.setMode(CommSettings.Mode.MODBUS_TCP);
        comm.setHost("192.168.10.50");
        comm.setTcpPort(1502);
        comm.setSerialPort("/dev/ttyUSB0");
        comm.setBaudRate(57600);
        comm.setSlaveId(7);
        comm.setPollIntervalMs(100);

        Path configFile = tempDir.resolve("nested/config.json");
        UserConfigStore.save(cfg, configFile);
        UserConfig loaded = UserConfigStore.load(configFile);

        assertEquals(UserConfig.Theme.DARK, loaded.getTheme());
        assertEquals("Monospaced", loaded.getUiFontFamily());
        assertEquals(13, loaded.getUiFontSize());
        assertEquals(1600, loaded.getWindowWidth());
        assertEquals(960, loaded.getWindowHeight());
        assertTrue(loaded.isWindowMaximized());
        assertFalse(loaded.isRememberWindowBounds());
        assertEquals(310, loaded.getLeftDockWidth());
        assertFalse(loaded.isShowStatusBar());
        assertFalse(loaded.isShowAlarmBanner());

        assertEquals(150, loaded.getDefaultZoomPercent());
        assertEquals("#2B3036", loaded.getDefaultScreenBackgroundHex());
        assertFalse(loaded.isShowGridByDefault());
        assertEquals("#556677", loaded.getGridDotColorHex());
        assertEquals(220, loaded.getCanvasGridWidth());
        assertEquals(140, loaded.getCanvasGridHeight());
        assertFalse(loaded.isAntiAliasing());
        assertTrue(loaded.isMouseWheelZoom());
        assertFalse(loaded.isConfirmUnsavedChanges());

        assertEquals(125, loaded.getRuntimeSyncIntervalMs());
        assertFalse(loaded.isShowFlowAnimation());
        assertEquals(72.5, loaded.getFlowAnimationSpeedPxPerSec(), 1e-6);
        assertFalse(loaded.isAutoStartSimulationInOperatorMode());
        assertFalse(loaded.isResetIoOnSimStop());
        assertFalse(loaded.isAcknowledgeAlarmsOnSimStop());
        assertFalse(loaded.isAlarmFlashEnabled());

        assertEquals("/tmp/scada-projects", loaded.getDefaultProjectDir());
        assertEquals("/tmp/scada-projects/plant1", loaded.getLastOpenedDir());
        assertTrue(loaded.isReopenLastProjectOnStartup());
        assertEquals("/tmp/b.msl", loaded.getLastProjectPath());
        assertEquals(5, loaded.getMaxRecentProjects());
        assertEquals(2, loaded.getRecentProjects().size());
        assertEquals("/tmp/b.msl", loaded.getRecentProjects().get(0));
        assertEquals("/tmp/a.msl", loaded.getRecentProjects().get(1));

        CommSettings lc = loaded.getDefaultCommSettings();
        assertEquals(CommSettings.Mode.MODBUS_TCP, lc.getMode());
        assertEquals("192.168.10.50", lc.getHost());
        assertEquals(1502, lc.getTcpPort());
        assertEquals("/dev/ttyUSB0", lc.getSerialPort());
        assertEquals(57600, lc.getBaudRate());
        assertEquals(7, lc.getSlaveId());
        assertEquals(100, lc.getPollIntervalMs());
    }

    @Test
    void validationAndRecentProjectsAndCopy() {
        UserConfig cfg = new UserConfig();
        cfg.setUiFontSize(99);
        assertEquals(20, cfg.getUiFontSize());
        cfg.setDefaultZoomPercent(145);
        assertEquals(150, cfg.getDefaultZoomPercent());
        cfg.setMaxRecentProjects(2);
        cfg.addRecentProject("/p/1.msl");
        cfg.addRecentProject("/p/2.msl");
        cfg.addRecentProject("/p/3.msl");
        cfg.addRecentProject("/p/2.msl");
        assertEquals(2, cfg.getRecentProjects().size());
        assertEquals("/p/2.msl", cfg.getRecentProjects().get(0));
        assertEquals("/p/3.msl", cfg.getRecentProjects().get(1));

        UserConfig copy = cfg.copy();
        assertNotSame(cfg, copy);
        assertNotSame(cfg.getDefaultCommSettings(), copy.getDefaultCommSettings());
        copy.getDefaultCommSettings().setTcpPort(9999);
        assertEquals(502, cfg.getDefaultCommSettings().getTcpPort());
    }

    @Test
    void missingOrMalformedFileFallsBackToDefaults() throws Exception {
        UserConfig missing = UserConfigStore.load(tempDir.resolve("does-not-exist.json"));
        assertEquals(UserConfig.Theme.LIGHT, missing.getTheme());
        assertEquals(100, missing.getDefaultZoomPercent());

        Path bad = tempDir.resolve("bad.json");
        Files.writeString(bad, "{ not valid json", StandardCharsets.UTF_8);
        UserConfig fallback = UserConfigStore.load(bad);
        assertEquals(UserConfig.Theme.LIGHT, fallback.getTheme());
        assertEquals(250, fallback.getRuntimeSyncIntervalMs());
    }
}
