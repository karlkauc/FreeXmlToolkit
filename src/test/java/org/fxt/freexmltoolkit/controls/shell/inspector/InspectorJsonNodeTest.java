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
 * Item 3 (deferred polish): selecting a JSON node in the Tree populates the inspector with
 * read-only key / kind / value.
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

        JsonTreeView tree = (JsonTreeView) host.lookupAll("*").stream()
                .filter(n -> n instanceof JsonTreeView).findFirst().orElseThrow();
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
}
