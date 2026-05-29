package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TestFX verification that the Favorites panel adds the active document to the
 * favorites store. Cleans up the test favorite afterwards so the shared store is
 * left unchanged.
 */
@ExtendWith(ApplicationExtension.class)
class FavoritesActivityPanelTest {

    private EditorHost host;
    private FavoritesActivityPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new FavoritesActivityPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1000, 600));
        stage.show();
    }

    @Test
    void addsActiveDocumentToFavorites(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("fav_panel_test.xml");
        Files.writeString(xml, "<root/>");
        String path = xml.toString();

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));

        try {
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                panel.addCurrent();
                return null;
            });
            WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () ->
                    FavoritesService.getInstance().getAllFavorites().stream()
                            .anyMatch(f -> path.equals(f.getFilePath())));
            assertTrue(FavoritesService.getInstance().getAllFavorites().stream()
                    .anyMatch(f -> path.equals(f.getFilePath())), "active document must be added to favorites");
            assertTrue(panel.getFavoriteCount() > 0);
        } finally {
            FavoritesService.getInstance().removeFavoriteByPath(path);
        }
    }
}
