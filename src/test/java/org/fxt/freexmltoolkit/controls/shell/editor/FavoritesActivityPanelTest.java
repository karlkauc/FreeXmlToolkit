package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

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

/**
 * TestFX verification that the Favorites panel adds the active document to the
 * favorites store. Cleans up the test favorite afterwards so the shared store is
 * left unchanged.
 */
@ExtendWith(ApplicationExtension.class)
class FavoritesActivityPanelTest {

    private static final String DEMO_FAV =
            new java.io.File("release/examples/xsd/context-sensitive-demo.xsd").getAbsolutePath();

    private EditorHost host;
    private FavoritesActivityPanel panel;
    private final java.util.concurrent.atomic.AtomicReference<Throwable> fxError =
            new java.util.concurrent.atomic.AtomicReference<>();

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        // Pre-add the demo XSD so the panel lists it on construction (repro for the
        // "open from favorites" crash); capture any FX-thread exception.
        FavoritesService.getInstance().addFavorite(new java.io.File(DEMO_FAV));
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> fxError.set(e));
        host = new EditorHost();
        panel = new FavoritesActivityPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1000, 600));
        stage.show();
    }

    @org.junit.jupiter.api.AfterEach
    void cleanupDemoFavorite() {
        FavoritesService.getInstance().removeFavoriteByPath(DEMO_FAV);
    }

    @Test
    void openingAFavoriteDoesNotCrashTheListView() throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(panel.getFavoriteCount() > 0, "the demo XSD favorite must be listed");

        // Selecting the favorite opens it — must not raise an FX-thread exception
        // (JavaFX ListViewBehavior IndexOutOfBoundsException). Row 0 is the
        // type-group header, so select the first actual favorite row.
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            javafx.scene.control.ListView<?> list = (javafx.scene.control.ListView<?>) panel.lookupAll(".list-view")
                    .stream().filter(n -> n instanceof javafx.scene.control.ListView).findFirst().orElseThrow();
            int index = panel.firstFavoriteRowIndex();
            assertTrue(index >= 0, "a favorite row must exist below its group header");
            list.getSelectionModel().select(index);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> !host.getOpenDocuments().isEmpty() || fxError.get() != null);

        assertNull(fxError.get(), "opening a favorite must not crash on the FX thread: " + fxError.get());
        assertFalse(host.getOpenDocuments().isEmpty(), "the favorite should have opened");
    }

    @Test
    void favoritesAreGroupedByFileType() {
        WaitForAsyncUtils.waitForFxEvents();
        // The demo favorite is an XSD, so its type-group header must be shown.
        var headers = panel.groupHeaderTexts();
        assertTrue(headers.contains("XSD Schema"),
                "the XSD group header must be listed, got: " + headers);
        assertTrue(panel.firstFavoriteRowIndex() > 0,
                "favorites must be listed below their group header");
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
