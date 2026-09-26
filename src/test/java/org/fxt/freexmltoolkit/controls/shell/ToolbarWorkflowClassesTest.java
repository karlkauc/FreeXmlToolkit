package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** shell.fxml toolbar buttons carry the workflow / action colour classes of the spec (§2, toolbar row). */
class ToolbarWorkflowClassesTest {

    private static String classesOf(String fxml, String id) {
        Matcher m = Pattern.compile("id=\"" + Pattern.quote(id) + "\"[^>]*?styleClass=\"([^\"]+)\"", Pattern.DOTALL)
                .matcher(fxml);
        assertTrue(m.find(), () -> "button " + id + " not found");
        return m.group(1);
    }

    @Test
    void toolbarButtonsCarryWorkflowClasses() throws IOException {
        String fxml = Files.readString(Path.of("src/main/resources/pages/shell.fxml"));
        assertTrue(classesOf(fxml, "action-new").contains("fxt-tool-create"), "New = CREATE green");
        assertTrue(classesOf(fxml, "action-open").contains("fxt-tool-primary"), "Open = workspace blue (was warning)");
        assertTrue(classesOf(fxml, "action-save").contains("fxt-tool-primary"));
        assertTrue(classesOf(fxml, "action-format").contains("fxt-tool-neutral"), "Format is neutral (was info)");
        assertTrue(classesOf(fxml, "action-insert-template").contains("fxt-tool-neutral"));
        assertTrue(classesOf(fxml, "action-compare").contains("fxt-tool-neutral"));
        assertTrue(classesOf(fxml, "action-spreadsheet").contains("fxt-tool-neutral"));
        assertTrue(classesOf(fxml, "doc-action-transform").contains("fxt-tool-transform"));
        assertTrue(classesOf(fxml, "doc-action-run").contains("fxt-tool-transform"));
        assertTrue(classesOf(fxml, "action-set-schema").contains("fxt-tool-schema"));
        assertTrue(classesOf(fxml, "doc-action-validate").contains("fxt-tool-accent"));
    }

    @Test
    void cssDefinesTheNewToolbarClasses() throws IOException {
        String css = Files.readString(Path.of("src/main/resources/css/unified-shell.css"));
        assertTrue(css.contains(".fxt-tool-button.fxt-tool-create .iconify-icon"));
        assertTrue(css.contains(".fxt-tool-button.fxt-tool-transform .iconify-icon"));
        assertTrue(css.contains(".fxt-tool-button.fxt-tool-schema .iconify-icon"));
        assertTrue(css.contains("-fxt-wf-validation-fill"), "Validate is filled in the validation colour");
    }
}
