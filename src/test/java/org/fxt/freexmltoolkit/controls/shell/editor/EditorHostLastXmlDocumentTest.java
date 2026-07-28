package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies {@link EditorHost#getLastXmlFamilyDocument()} — the run target for
 * XPath/XQuery documents: it tracks the most recently active XML-family tab,
 * survives switching to a query tab, and falls back / empties when the
 * remembered tab is closed.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostLastXmlDocumentTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void queryTabKeepsTheXmlDocumentAsRunTarget(@TempDir Path tmp) throws Exception {
        Path xml = openAndAwait(tmp, "doc.xml", "<root><a>x</a></root>", "root");
        openAndAwait(tmp, "query.xq", "/root/a", "root");

        // The query tab is active, but the run target stays the XML document.
        assertEquals(EditorFileType.XQUERY,
                host.getActiveDocument().orElseThrow().getFileType());
        var target = host.getLastXmlFamilyDocument();
        assertTrue(target.isPresent(), "the previously active XML document must be the run target");
        assertEquals(xml, target.get().getPath());

        // Its text is read through getDocumentText (reflects unsaved edits too).
        assertEquals("<root><a>x</a></root>",
                host.getDocumentText(target.get()).orElseThrow());
    }

    @Test
    void emptyWithoutAnyXmlFamilyDocument(@TempDir Path tmp) throws Exception {
        openAndAwait(tmp, "query.xq", "/root", "root");
        assertTrue(host.getLastXmlFamilyDocument().isEmpty(),
                "with only a query document open there is no run target");
    }

    @Test
    void closingTheRememberedTabFallsBackToAnotherOpenXmlTab(@TempDir Path tmp) throws Exception {
        Path first = openAndAwait(tmp, "first.xml", "<first/>", "first");
        Path second = openAndAwait(tmp, "second.xml", "<second/>", "second");
        openAndAwait(tmp, "query.xq", "/x", "x");

        // second.xml was active most recently → it is the target.
        assertEquals(second, host.getLastXmlFamilyDocument().orElseThrow().getPath());

        // Close the remembered tab (second.xml) → fall back to the remaining XML tab.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            TabPane tabPane = (TabPane) host.lookup(".fxt-editor-tabpane");
            tabPane.getTabs().removeIf(t -> t.getText() != null && t.getText().contains("second.xml"));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(first, host.getLastXmlFamilyDocument().orElseThrow().getPath(),
                "after closing the remembered tab, another open XML tab must be the target");
    }

    private Path openAndAwait(Path tmp, String name, String content, String marker) throws Exception {
        Path file = tmp.resolve(name);
        Files.writeString(file, content);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains(marker)).orElse(false));
        WaitForAsyncUtils.waitForFxEvents();
        return file;
    }
}
