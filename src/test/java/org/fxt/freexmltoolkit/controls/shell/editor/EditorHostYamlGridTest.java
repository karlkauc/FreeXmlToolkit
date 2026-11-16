package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.jsoneditor.commands.MoveNodeCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonCanvasView;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * YAML documents offer the Tree and the Grid over the shared JSON model; edits there are written
 * into the YAML text in place, so comments stay, and a change that cannot be placed asks before the
 * whole text is written again.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostYamlGridTest {

    private static final String YAML = """
            # the app
            name: web   # its name
            replicas: 2
            ports:
            - 80
            - 443
            """;

    private EditorHost host;
    private final java.util.function.BooleanSupplier originalConfirmer = EditorTab.yamlRewriteConfirmer;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @AfterEach
    void restore() {
        EditorTab.yamlRewriteConfirmer = originalConfirmer;
    }

    private void open(Path tmp) throws Exception {
        Path yaml = tmp.resolve("app.yaml");
        Files.writeString(yaml, YAML);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(yaml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("replicas")).orElse(false));
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

    private void select(String key) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            var context = grid().getContext();
            JsonObject root = (JsonObject) context.getDocument().getRootValue();
            context.getSelectionModel().setSelectedNode(root.getProperty(key));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void gridEditsAreWrittenIntoTheYamlInPlace(@TempDir Path tmp) throws Exception {
        open(tmp);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeSupportsView(ViewMode.GRAPHIC)));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeSupportsView(ViewMode.TREE)));
        switchTo(ViewMode.GRAPHIC);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.lookupAll("*").stream().anyMatch(n -> n instanceof JsonCanvasView)));

        select("replicas");
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveJsonValue("5")));
        assertEquals(YAML.replace("replicas: 2", "replicas: 5"), host.getActiveText().orElseThrow());

        select("name");
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.renameActiveJsonKey("title")));
        assertEquals(YAML.replace("replicas: 2", "replicas: 5").replace("name: web", "title: web"),
                host.getActiveText().orElseThrow());

        // the Tree shows the same model, and undo takes the edits back in the text
        switchTo(ViewMode.TREE);
        switchTo(ViewMode.GRAPHIC);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.undoJson()));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.undoJson()));
        assertEquals(YAML, host.getActiveText().orElseThrow());
    }

    @Test
    void aChangeThatCannotBePlacedAsksBeforeRewriting(@TempDir Path tmp) throws Exception {
        open(tmp);
        switchTo(ViewMode.GRAPHIC);
        AtomicInteger asked = new AtomicInteger();
        EditorTab.yamlRewriteConfirmer = () -> {
            asked.incrementAndGet();
            return false;
        };
        // moving "name" behind "ports" reorders a block holding comments: not placeable in place
        boolean applied = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            JsonObject root = (JsonObject) grid().getContext().getDocument().getRootValue();
            return activeTab().editJson(new MoveNodeCommand(root.getProperty("name"), 2));
        });
        assertTrue(applied);
        assertEquals(1, asked.get());
        assertEquals(YAML, host.getActiveText().orElseThrow(), "refused: the text stays");
        JsonObject root = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> (JsonObject) grid().getContext().getDocument().getRootValue());
        assertEquals("name", root.getPropertyKeys().iterator().next(), "refused: the model follows back");

        EditorTab.yamlRewriteConfirmer = () -> {
            asked.incrementAndGet();
            return true;
        };
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            JsonObject r = (JsonObject) grid().getContext().getDocument().getRootValue();
            return activeTab().editJson(new MoveNodeCommand(r.getProperty("name"), 2));
        });
        assertEquals(2, asked.get());
        assertEquals("replicas: 2\nports:\n- 80\n- 443\nname: web\n", host.getActiveText().orElseThrow());
    }

    private EditorTab activeTab() {
        return (EditorTab) host.lookupAll(".tab-pane").stream()
                .map(n -> ((javafx.scene.control.TabPane) n).getSelectionModel().getSelectedItem())
                .filter(t -> t instanceof EditorTab).findFirst().orElseThrow();
    }
}
