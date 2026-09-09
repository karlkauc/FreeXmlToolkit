package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonCanvasView;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the JSON Grid view: JSON documents offer a Graphic mode backed by the
 * Canvas-based {@link JsonCanvasView} (same look as the XML grid) over ONE shared
 * {@code JsonEditorContext}; edits round-trip into the text and undo survives a view switch.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostJsonGridTest {

    private static final String JSON = """
            {
              "name": "Alice",
              "age": 30,
              "items": [
                {"id": 1, "n": "a"},
                {"id": 2, "n": "b"}
              ]
            }
            """;

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    private void openJson(Path tmp) throws Exception {
        Path json = tmp.resolve("data.json");
        Files.writeString(json, JSON);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("Alice")).orElse(false));
    }

    private void switchTo(ViewMode mode) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(mode);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private JsonGridView grid() {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> (JsonGridView) host.lookupAll("*").stream()
                .filter(n -> n instanceof JsonGridView).findFirst().orElseThrow());
    }

    @Test
    void jsonOffersAGridViewBackedByTheSharedCanvas(@TempDir Path tmp) throws Exception {
        openJson(tmp);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeSupportsView(ViewMode.GRAPHIC)),
                "JSON must offer the Grid view");

        switchTo(ViewMode.GRAPHIC);

        assertEquals(ViewMode.GRAPHIC, host.activeViewModeProperty().get(), "Graphic mode must stick");
        boolean hasGrid = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                host.lookupAll("*").stream().anyMatch(n -> n instanceof JsonCanvasView));
        assertTrue(hasGrid, "Grid mode must embed the Canvas-based JsonCanvasView grid");
        assertNotNull(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.lookup("#grid-collapse-all")),
                "the grid header (Collapse all) is shared with the XML grid");
        assertInstanceOf(JsonCanvasView.class,
                WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveSearchTarget()),
                "Graphic mode exposes the JSON grid canvas as the shell's search target");
    }

    @Test
    void inspectorEditRoundTripsIntoTheTextAndUndoSurvivesViewSwitch(@TempDir Path tmp) throws Exception {
        openJson(tmp);
        switchTo(ViewMode.GRAPHIC);
        JsonGridView grid = grid();

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            JsonObject root = (JsonObject) grid.getContext().getDocument().getRootValue();
            grid.getContext().getSelectionModel().setSelectedNode(root.getProperty("name"));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveJsonValue("Bob")));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveText().orElse("").contains("\"name\": \"Bob\""));
        String text = host.getActiveText().orElse("");
        assertTrue(text.contains("\"age\": 30"), "untouched properties stay: " + text);
        assertTrue(text.contains("\"id\": 2"), "the array of objects stays: " + text);

        switchTo(ViewMode.TEXT);
        switchTo(ViewMode.GRAPHIC);
        assertSame(grid.getContext(), grid().getContext(), "the shared context must survive the view switch");

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.undoJson()));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveText().orElse("").contains("\"name\": \"Alice\""));
    }

    @Test
    void undoActiveRoutesToTheJsonModelStackInGraphicMode(@TempDir Path tmp) throws Exception {
        openJson(tmp);
        switchTo(ViewMode.GRAPHIC);
        JsonGridView grid = grid();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            JsonObject root = (JsonObject) grid.getContext().getDocument().getRootValue();
            grid.getContext().getSelectionModel().setSelectedNode(root.getProperty("age"));
            return null;
        });
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveJsonValue("31")));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveText().orElse("").contains("\"age\": 31"));
        JsonPrimitive age = (JsonPrimitive) ((JsonObject) grid.getContext().getDocument().getRootValue()).getProperty("age");
        assertTrue(age.isNumber(), "the number type is kept");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.undoActive();
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveText().orElse("").contains("\"age\": 30"));
        assertTrue(grid.getContext().canRedo(), "Ctrl+Z went through the JSON command stack");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.redoActive();
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveText().orElse("").contains("\"age\": 31"));
    }

    @Test
    void invalidJsonShowsAPlaceholderInsteadOfTheGrid(@TempDir Path tmp) throws Exception {
        Path json = tmp.resolve("broken.json");
        Files.writeString(json, "{\"a\": }");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("a")).orElse(false));

        switchTo(ViewMode.GRAPHIC);

        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                host.lookupAll("*").stream().anyMatch(n -> n instanceof JsonCanvasView)));
        assertNotNull(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.lookup(".fxt-empty-state")));
    }
}
