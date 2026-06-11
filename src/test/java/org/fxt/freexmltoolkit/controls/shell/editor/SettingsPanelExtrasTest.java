package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class SettingsPanelExtrasTest {

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
    }

    @Test
    void userInfoRoundTripsThroughProperties() {
        SettingsPanel panel = WaitForAsyncUtils.waitForAsyncFx(3000, SettingsPanel::new);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setUserName("Ada Lovelace");
            panel.setUserEmail("ada@example.com");
            panel.setUserCompany("Analytical Engines");
            panel.saveSettings();
            return null;
        });
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        assertEquals("Ada Lovelace", props.get("user.name"));
        assertEquals("ada@example.com", props.get("user.email"));
        assertEquals("Analytical Engines", props.get("user.company"));
    }

    @Test
    void cacheFolderPointsIntoTheUserConfigDirectory() {
        File cache = SettingsPanel.cacheFolder();
        assertEquals(new File(System.getProperty("user.home"),
                ".freeXmlToolkit" + File.separator + "cache"), cache);
    }

    @Test
    void deleteContentsRemovesFilesAndSubfoldersButKeepsTheFolder(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("a.xsd"), "<a/>");
        Path sub = Files.createDirectory(tmp.resolve("schemas"));
        Files.writeString(sub.resolve("b.xsd"), "<b/>");

        int deleted = SettingsPanel.deleteContents(tmp.toFile());

        assertEquals(2, deleted, "both cached files must be deleted");
        File[] remaining = tmp.toFile().listFiles();
        assertEquals(0, remaining == null ? -1 : remaining.length,
                "the cache folder itself stays, but must be empty");
        assertTrue(Files.exists(tmp), "the cache folder itself is kept");
    }
}
