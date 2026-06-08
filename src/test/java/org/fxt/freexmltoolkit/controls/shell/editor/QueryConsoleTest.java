package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of {@link QueryConsole}: running an XPath against the
 * active document fills the results pane, and with no document open the console
 * disables Run / reports that no document is open.
 */
@ExtendWith(ApplicationExtension.class)
class QueryConsoleTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        host = new EditorHost();
    }

    @Test
    void runningXPathAgainstTheActiveDocumentShowsResults(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("doc.xml");
        Files.writeString(file, "<root><item id=\"1\">a</item><item id=\"2\">b</item></root>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("item")).orElse(false));

        QueryConsole console = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new QueryConsole(host));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("//item");
            console.runForTest();
            return null;
        });

        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> {
                    String r = console.getResultsText();
                    return r != null && !r.isBlank() && !"Running…".equals(r);
                });

        String results = console.getResultsText();
        assertTrue(results.contains("item"), "results should contain the matched elements: " + results);
        assertTrue(results.contains("a") && results.contains("b"),
                "results should contain both item values: " + results);
    }

    @Test
    void runIsDisabledWhenNoDocumentIsOpen() {
        QueryConsole console = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new QueryConsole(host));
        WaitForAsyncUtils.waitForFxEvents();

        // With no document open, Run is disabled.
        assertTrue(console.isRunDisabledForTest(), "Run must be disabled when no document is open");

        // Even if invoked, it reports there is no document and does not produce results.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.runForTest();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(console.getResultsText().contains("item"),
                "no results should be produced without an active document");
        assertTrue(console.getResultsText().contains("No document open."),
                "the console should report that no document is open");
    }
}
