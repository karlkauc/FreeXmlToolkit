package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.EntryHelperModel.Entry;
import org.fxt.freexmltoolkit.service.sqf.SqfFixSuggestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The grid's entry helpers and the quick fixes work from a JSON document's bound schema. */
@ExtendWith(ApplicationExtension.class)
class EditorHostJsonSchemaToolsTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 1100, 700));
        stage.show();
    }

    private Path open(Path tmp, String json) throws Exception {
        Files.writeString(tmp.resolve("s.json"), """
                {"type": "object", "required": ["name", "size"],
                 "properties": {"$schema": {"type": "string"}, "name": {"type": "string"},
                   "size": {"enum": ["S", "M"]}, "note": {"type": "string"}}}
                """);
        Path file = tmp.resolve("data.json");
        Files.writeString(file, json);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.getActiveJsonSchema().isPresent());
        return file;
    }

    @Test
    void entryHelpersListAndAddDeclaredProperties(@TempDir Path tmp) throws Exception {
        open(tmp, "{\n  \"$schema\": \"./s.json\",\n  \"name\": \"x\",\n  \"size\": \"S\"\n}\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveViewMode(ViewMode.GRAPHIC);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        JsonGridView grid = WaitForAsyncUtils.waitForAsyncFx(2000, () -> (JsonGridView) host.lookupAll("*").stream()
                .filter(n -> n instanceof JsonGridView).findFirst().orElseThrow());
        GridEntryHelpersPanel helpers = WaitForAsyncUtils.waitForAsyncFx(2000, grid::getEntryHelpers);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            JsonObject root = (JsonObject) grid.getContext().getDocument().getRootValue();
            grid.getCanvasView().setSelectedNode(root.getProperty("size"));
            helpers.refresh();
            return null;
        });
        List<Entry> siblings = WaitForAsyncUtils.waitForAsyncFx(2000, () -> List.copyOf(helpers.siblingsList().getItems()));
        assertEquals(List.of("$schema", "name", "size", "note"), siblings.stream().map(Entry::name).toList());
        assertTrue(siblings.get(3).available());
        assertFalse(siblings.get(1).available(), "present properties are greyed");
        List<Entry> values = WaitForAsyncUtils.waitForAsyncFx(2000, () -> List.copyOf(helpers.valuesList().getItems()));
        assertEquals(List.of("\"S\"", "\"M\""), values.stream().map(Entry::name).toList());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            helpers.siblingsList().getSelectionModel().select(3);
            helpers.onAppend();
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("\"note\": \"\""));
    }

    @Test
    void quickFixAddsTheMissingRequiredProperty(@TempDir Path tmp) throws Exception {
        String json = "{\n  \"$schema\": \"./s.json\",\n  \"name\": \"x\"\n}\n";
        Path file = open(tmp, json);
        List<ValidationProblem> problems = ValidationRunner.validateJson(json, tmp.resolve("s.json").toFile());
        ValidationProblem missing = problems.stream().filter(p -> "required".equals(p.ruleId())).findFirst().orElseThrow();
        SqfFixSuggestion fix = missing.fixes().get(0);
        assertEquals("Add required property \"size\"", fix.title());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(problems);
            host.getQuickFixController().applyFix(missing, fix);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().orElse("").contains("\"name\": \"x\",\n  \"size\": \"S\"\n}"));
        assertNotNull(file);
    }
}
