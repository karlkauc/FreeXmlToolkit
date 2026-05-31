package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the EditorHost can minify XML and JSON documents.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostMinifyTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 800, 600));
        stage.show();
    }

    @Test
    void minifiesXml(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<a>\n  <b>x</b>\n  <c>y</c>\n</a>\n");
        open(xml, "b>x");

        boolean ok = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.minifyActive());
        assertTrue(ok);
        assertEquals("<a><b>x</b><c>y</c></a>", host.getActiveText().orElse("").strip());
    }

    @Test
    void minifiesJson(@TempDir Path tmp) throws Exception {
        Path json = tmp.resolve("doc.json");
        Files.writeString(json, "{\n  \"a\": 1,\n  \"b\": [1, 2]\n}\n");
        open(json, "\"a\"");

        boolean ok = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.minifyActive());
        assertTrue(ok);
        String text = host.getActiveText().orElse("").strip();
        assertFalse(text.contains("\n"), "minified JSON must be single-line: " + text);
        assertTrue(text.contains("\"a\":1") || text.contains("\"a\": 1"), text);
    }

    @Test
    void exposesActiveCodeAreaForSearch(@TempDir Path tmp) throws Exception {
        assertNull(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveCodeArea()),
                "no code area when nothing is open");
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<a>hello</a>");
        open(xml, "hello");
        assertNotNull(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveCodeArea()),
                "the active editor exposes its code area (find/replace target)");
    }

    private void open(Path file, String marker) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains(marker)).orElse(false));
    }
}
