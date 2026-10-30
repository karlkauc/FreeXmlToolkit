package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Settings → Log Files: limits are saved, and "Clean Up Now" deletes old logs in the log folder. */
@ExtendWith(ApplicationExtension.class)
class SettingsPanelLogFilesTest {

    @TempDir
    Path logs;

    private final java.util.function.Supplier<Path> originalDirectory = SettingsPanel.logDirectory;
    private final java.util.function.Predicate<Path> originalConfirmer = SettingsPanel.logCleanupConfirmer;

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        ServiceRegistry.initialize();
    }

    /** A panel shown in the stage, so the controls inside its scroll pane can be looked up. */
    private SettingsPanel showPanel() {
        return WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            SettingsPanel panel = new SettingsPanel();
            stage.setScene(new javafx.scene.Scene(panel, 900, 700));
            stage.show();
            panel.applyCss();
            panel.layout();
            return panel;
        });
    }

    @AfterEach
    void restore() {
        SettingsPanel.logDirectory = originalDirectory;
        SettingsPanel.logCleanupConfirmer = originalConfirmer;
    }

    @SuppressWarnings("unchecked")
    private static <T> T lookup(SettingsPanel panel, String id) {
        return (T) panel.lookup("#" + id);
    }

    @Test
    void limitsRoundTripThroughProperties() {
        SettingsPanel.logDirectory = () -> logs;
        SettingsPanel panel = showPanel();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            CheckBox enabled = lookup(panel, "settings-log-cleanup");
            Spinner<Integer> age = lookup(panel, "settings-log-max-age");
            Spinner<Integer> size = lookup(panel, "settings-log-max-size");
            enabled.setSelected(false);
            assertTrue(age.isDisabled(), "limits are off while the clean-up is off");
            enabled.setSelected(true);
            age.getValueFactory().setValue(7);
            size.getValueFactory().setValue(250);
            panel.saveSettings();
            return null;
        });
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        assertTrue(props.isLogCleanupEnabled());
        assertEquals(7, props.getLogCleanupMaxAgeDays());
        assertEquals(250, props.getLogCleanupMaxSizeMb());
    }

    @Test
    void cleanUpNowDeletesOldLogsAndReports() throws Exception {
        Path old = logs.resolve("fxt-test.log-20250101.gz");
        Files.writeString(old, "old");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(Duration.ofDays(400))));
        Path recent = logs.resolve("fxt-recent.log.gz");
        Files.writeString(recent, "new");
        SettingsPanel.logDirectory = () -> logs;
        SettingsPanel.logCleanupConfirmer = dir -> true;

        SettingsPanel panel = showPanel();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            Spinner<Integer> age = lookup(panel, "settings-log-max-age");
            age.getValueFactory().setValue(14);
            panel.onCleanLogsNow();
            return null;
        });

        Label status = lookup(panel, "settings-log-status");
        WaitForAsyncUtils.waitFor(5, java.util.concurrent.TimeUnit.SECONDS,
                () -> status.getText() != null && status.getText().startsWith("Deleted 1 file(s)"));
        assertFalse(Files.exists(old));
        assertTrue(Files.exists(recent));
        assertTrue(status.getText().contains(logs.toString()), status.getText());
    }
}
