package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the Query Console wires up XPath/XQuery IntelliSense: both inputs get an
 * (enabled) engine, and the engine is fed the active document's content so element
 * suggestions are available. The completion behaviour itself is covered by the
 * engine/provider/library tests under {@code controls.v2.editor.intellisense}.
 */
@ExtendWith(ApplicationExtension.class)
class QueryConsoleIntelliSenseTest {

    private EditorHost host;
    private Stage stage;

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        this.stage = stage;
        host = new EditorHost();
    }

    @Test
    void bothInputsHaveAnEnabledIntelliSenseEngine() {
        QueryConsole console = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new QueryConsole(host));
        WaitForAsyncUtils.waitForFxEvents();

        assertNotNull(console.xpathIntelliSenseForTest(), "XPath input must have an IntelliSense engine");
        assertNotNull(console.xqueryIntelliSenseForTest(), "XQuery input must have an IntelliSense engine");
        assertTrue(console.xpathIntelliSenseForTest().isEnabled());
        assertTrue(console.xqueryIntelliSenseForTest().isEnabled());
    }

    @Test
    void completionsSeeTheActiveDocumentElements(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("doc.xml");
        Files.writeString(file, "<library><book><title>x</title></book></library>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("library")).orElse(false));

        QueryConsole console = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            QueryConsole c = new QueryConsole(host);
            // Put the console in a shown scene so the engine has a window for its popup.
            stage.setScene(new Scene(new StackPane(c), 900, 400));
            stage.show();
            return c;
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Triggering completions refreshes the engine's element extractor from the active doc.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("/");
            console.xpathIntelliSenseForTest().showCompletions();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        var elements = console.xpathIntelliSenseForTest().getElementExtractor().getAllElements();
        assertTrue(elements.contains("library") && elements.contains("book") && elements.contains("title"),
                "the engine should see the active document's elements for completion: " + elements);

        // Clean up the popup so it doesn't leak into other tests.
        WaitForAsyncUtils.waitForAsyncFx(1000, () -> {
            console.xpathIntelliSenseForTest().hidePopup();
            return null;
        });
    }
}
