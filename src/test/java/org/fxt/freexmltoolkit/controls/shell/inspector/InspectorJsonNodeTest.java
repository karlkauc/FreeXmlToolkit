package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.view.JsonTreeView;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Selecting a JSON node in the Tree populates the inspector with key / kind / JSONPath / value;
 * the key and the scalar value are editable and commit through the shared JSON context.
 */
@ExtendWith(ApplicationExtension.class)
class InspectorJsonNodeTest {

    private EditorHost host;
    private InspectorPanel inspector;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        inspector = new InspectorPanel(host);
        stage.setScene(new Scene(new HBox(host, inspector), 1100, 700));
        stage.show();
    }

    @Test
    void selectingJsonNodeShowsReadOnlyKeyKindValue(@TempDir Path tmp) throws Exception {
        Path json = tmp.resolve("data.json");
        Files.writeString(json, "{\n  \"name\": \"Alice\",\n  \"age\": 30\n}\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("Alice")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        Thread.sleep(300);

        // lookupAll must run on the FX thread (scene graph may still be mutating)
        JsonTreeView tree = WaitForAsyncUtils.waitForAsyncFx(2000, () -> (JsonTreeView) host.lookupAll("*").stream()
                .filter(n -> n instanceof JsonTreeView).findFirst().orElseThrow());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            JsonObject root = (JsonObject) tree.getDocument().getRootValue();
            JsonNode name = root.getProperty("name");
            tree.selectNode(name);
            return null;
        });

        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> "name".equals(inspector.getNodeNameText())
                        && "Alice".equals(inspector.getJsonValueText()));
        assertEquals("name", inspector.getNodeNameText());
        assertEquals("Alice", inspector.getJsonValueText());
        assertTrue(inspector.getKindText().toUpperCase().contains("STRING"),
                "kind must reflect the JSON node type, was: " + inspector.getKindText());
    }

    @Test
    void editingValueAndKeyCommitsThroughTheSharedContext(@TempDir Path tmp) throws Exception {
        Path json = tmp.resolve("data.json");
        Files.writeString(json, "{\n  \"name\": \"Alice\",\n  \"age\": 30,\n  \"items\": [1, 2]\n}\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("Alice")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.TREE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        JsonTreeView tree = WaitForAsyncUtils.waitForAsyncFx(2000, () -> (JsonTreeView) host.lookupAll("*").stream()
                .filter(n -> n instanceof JsonTreeView).findFirst().orElseThrow());
        var document = tree.getDocument();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            tree.selectNode(((JsonObject) tree.getDocument().getRootValue()).getProperty("age"));
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> "age".equals(inspector.getNodeNameText()));

        // Value edit keeps the number type and round-trips into the text
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            inspector.setJsonValueTextForTest("31");
            inspector.commitValueTextForTest();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> host.getActiveText().orElse("").contains("\"age\": 31"));
        assertSame(document, tree.getDocument(), "the tree keeps the shared document (no re-parse)");

        // Key rename
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            inspector.setNodeNameTextForTest("years");
            inspector.commitNameForTest();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> host.getActiveText().orElse("").contains("\"years\": 31"));

        // Array items show their index path
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            tree.selectNode(((JsonObject) tree.getDocument().getRootValue()).getProperty("items").getChild(1));
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> "$.items[1]".equals(inspector.getXPathText()));
    }
}
