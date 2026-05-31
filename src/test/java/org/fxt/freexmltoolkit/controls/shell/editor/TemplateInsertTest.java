package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies template insertion: the dialog lists/returns a template, and the
 * EditorHost inserts rendered content at the caret.
 */
@ExtendWith(ApplicationExtension.class)
class TemplateInsertTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void dialogListsAndReturnsTheSelectedTemplate() {
        XmlTemplate a = new XmlTemplate("Alpha", "<a/>", "cat");
        XmlTemplate b = new XmlTemplate("Beta", "<b/>", "cat");
        XmlTemplate result = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            TemplateInsertDialog dialog = new TemplateInsertDialog(List.of(a, b));
            assertEquals(2, dialog.templateCount());
            dialog.select(b);
            return dialog.getResultConverter().call(javafx.scene.control.ButtonType.OK);
        });
        assertSame(b, result, "OK must return the selected template");
    }

    @Test
    void insertsRenderedTemplateAtCaret(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root></root>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));

        XmlTemplate snippet = new XmlTemplate("Child", "<child/>", "cat");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.insertTextAtCaret(TemplateRunner.render(snippet, Map.of()));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(host.getActiveText().orElse("").contains("<child/>"),
                "rendered template must be inserted into the active editor");
    }
}
