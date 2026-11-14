package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.jsonschema.JsonCompletionProvider.Proposal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** A JSON document bound to a schema ({@code "$schema"}) gets completion and hover help. */
@ExtendWith(ApplicationExtension.class)
class EditorHostJsonCompletionTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 700));
        stage.show();
    }

    @Test
    void ctrlSpaceOffersMissingPropertiesAndInsertsOne(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("s.json"), """
                {"type": "object", "required": ["status"],
                 "properties": {"$schema": {"type": "string"}, "status": {"enum": ["open", "closed"],
                 "description": "Workflow state"}, "note": {"type": "string"}}}
                """);
        String text = "{\n  \"$schema\": \"./s.json\",\n  \n}\n";
        Path json = tmp.resolve("data.json");
        Files.writeString(json, text);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.getActiveJsonSchema().isPresent());
        JsonEditorView view = (JsonEditorView) WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeEditorView());
        int caret = text.indexOf("\n  \n") + 3;

        List<Proposal> proposals = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.getCodeArea().requestFocus();
            view.getCodeArea().moveTo(caret);
            return view.getEditor().getIntelliSense().show(true);
        });
        assertEquals(List.of("status", "note"), proposals.stream().map(Proposal::label).toList());
        assertTrue(proposals.get(0).required());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.getEditor().getIntelliSense().apply(proposals.get(0));
            return null;
        });
        String after = host.getActiveText().orElseThrow();
        assertTrue(after.contains("\"status\": "), after);

        // the value position offers the enum
        List<Proposal> values = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> view.getEditor().getIntelliSense().show(true));
        assertEquals(List.of("open", "closed"), values.stream().map(Proposal::label).toList());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.getEditor().getIntelliSense().hide();
            return null;
        });

        // hover help on the key
        String now = host.getActiveText().orElseThrow();
        List<String> hover = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> view.getEditor().schemaInfoAt(now.indexOf("\"status\"") + 2));
        assertTrue(hover.get(0).contains("required"), hover.toString());
        assertTrue(hover.contains("Workflow state"), hover.toString());
    }
}
