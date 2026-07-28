package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Verifies the per-document run-target model on {@link EditorHost}: the
 * {@link QueryTarget} get/set round-trip, per-document independence, resolution
 * of open-document / file-system / Automatic targets ({@link EditorHost#resolveQueryTarget}),
 * self-exclusion for XSLT, and the defensive fallback when a chosen target
 * document was closed.
 */
@ExtendWith(ApplicationExtension.class)
class QueryTargetResolutionTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void automaticIsTheDefaultAndSettingAutomaticClearsTheEntry(@TempDir Path tmp) throws Exception {
        openAndAwait(tmp, "doc.xml", "<root/>", "root");
        var queryDoc = openAndAwaitDoc(tmp, "q.xq", "/root", "root");
        var xmlDoc = docNamed("doc.xml");

        assertTrue(host.getQueryTarget(queryDoc) instanceof QueryTarget.Automatic,
                "with no explicit choice the target must be Automatic");

        host.setQueryTarget(queryDoc, new QueryTarget.OpenDoc(xmlDoc));
        assertTrue(host.getQueryTarget(queryDoc) instanceof QueryTarget.OpenDoc,
                "an explicit open-document target must be stored");

        host.setQueryTarget(queryDoc, QueryTarget.AUTOMATIC);
        assertTrue(host.getQueryTarget(queryDoc) instanceof QueryTarget.Automatic,
                "setting Automatic must clear the stored entry");
    }

    @Test
    void targetsAreIndependentPerDocument(@TempDir Path tmp) throws Exception {
        openAndAwait(tmp, "a.xml", "<a/>", "a");
        openAndAwait(tmp, "b.xml", "<b/>", "b");
        var q1 = openAndAwaitDoc(tmp, "one.xq", "/a", "a");
        var q2 = openAndAwaitDoc(tmp, "two.xq", "/b", "b");

        host.setQueryTarget(q1, new QueryTarget.OpenDoc(docNamed("a.xml")));
        host.setQueryTarget(q2, new QueryTarget.OpenDoc(docNamed("b.xml")));

        assertEquals("a.xml", host.resolveQueryTarget(q1).orElseThrow().displayName());
        assertEquals("b.xml", host.resolveQueryTarget(q2).orElseThrow().displayName());
    }

    @Test
    void openDocumentTargetResolvesToItsInMemoryText(@TempDir Path tmp) throws Exception {
        openAndAwait(tmp, "a.xml", "<a>ORIGINAL</a>", "ORIGINAL");
        openAndAwait(tmp, "b.xml", "<b/>", "b");
        var queryDoc = openAndAwaitDoc(tmp, "q.xq", "/a", "a");

        host.setQueryTarget(queryDoc, new QueryTarget.OpenDoc(docNamed("a.xml")));
        var resolved = host.resolveQueryTarget(queryDoc).orElseThrow();
        assertEquals("a.xml", resolved.displayName());
        assertEquals("<a>ORIGINAL</a>", resolved.xmlText(),
                "an open-document target must resolve to the live editor text");
        assertNull(resolved.file());
        assertEquals("<a>ORIGINAL</a>", resolved.loadXml());
    }

    @Test
    void fileSystemTargetDefersTheReadToLoadXml(@TempDir Path tmp) throws Exception {
        Path ext = tmp.resolve("ext.xml");
        Files.writeString(ext, "<r>FROM-DISK</r>");
        var queryDoc = openAndAwaitDoc(tmp, "q.xq", "//r", "r");

        host.setQueryTarget(queryDoc, new QueryTarget.FsFile(ext.toFile()));
        var resolved = host.resolveQueryTarget(queryDoc).orElseThrow();
        assertEquals("ext.xml", resolved.displayName());
        assertNull(resolved.xmlText(), "the file must not be read at resolve time");
        assertEquals(ext.toFile(), resolved.file());
        assertEquals("<r>FROM-DISK</r>", resolved.loadXml());
    }

    @Test
    void automaticNeverResolvesToTheDocumentItself(@TempDir Path tmp) throws Exception {
        openAndAwait(tmp, "input.xml", "<input/>", "input");
        // The XSLT tab is XML-family and active — but must not be its own target.
        var xsltDoc = openAndAwaitDoc(tmp, "sheet.xsl",
                "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"/>",
                "stylesheet");

        var resolved = host.resolveQueryTarget(xsltDoc).orElseThrow();
        assertEquals("input.xml", resolved.displayName(),
                "Automatic must skip the document itself and pick the open XML document");

        // With nothing else open, resolution must be empty rather than self-referential.
        closeTabNamed("input.xml");
        assertTrue(host.resolveQueryTarget(xsltDoc).isEmpty(),
                "an XSLT alone must have no Automatic target");
    }

    @Test
    void staleOpenDocTargetFallsBackAndCleansTheEntry(@TempDir Path tmp) throws Exception {
        openAndAwait(tmp, "a.xml", "<a/>", "a");
        openAndAwait(tmp, "b.xml", "<b/>", "b");
        var queryDoc = openAndAwaitDoc(tmp, "q.xq", "/a", "a");

        host.setQueryTarget(queryDoc, new QueryTarget.OpenDoc(docNamed("b.xml")));
        // Programmatic removal does not fire onClosed — the defensive path must handle it.
        closeTabNamed("b.xml");

        var resolved = host.resolveQueryTarget(queryDoc).orElseThrow();
        assertEquals("a.xml", resolved.displayName(),
                "a closed target must fall back to the remaining XML document");
        assertTrue(host.getQueryTarget(queryDoc) instanceof QueryTarget.Automatic,
                "the stale entry must be dropped during resolution");
    }

    @Test
    void xprocDocumentResolvesAutomaticTargetToTheLastXmlDocument(@TempDir Path tmp) throws Exception {
        openAndAwait(tmp, "input.xml", "<input/>", "input");
        var xprocDoc = openAndAwaitDoc(tmp, "pipe.xpl",
                "<p:declare-step xmlns:p=\"http://www.w3.org/ns/xproc\" version=\"3.0\"/>",
                "declare-step");

        var resolved = host.resolveQueryTarget(xprocDoc).orElseThrow();
        assertEquals("input.xml", resolved.displayName(),
                "an XProc pipeline must resolve its Automatic target to the open XML document");
    }

    @Test
    void openXprocPipelineIsAnXmlFamilyTargetCandidate(@TempDir Path tmp) throws Exception {
        openAndAwait(tmp, "pipe.xpl",
                "<p:declare-step xmlns:p=\"http://www.w3.org/ns/xproc\" version=\"3.0\"/>",
                "declare-step");
        var queryDoc = openAndAwaitDoc(tmp, "q.xq", "/x", "x");

        var resolved = host.resolveQueryTarget(queryDoc).orElseThrow();
        assertEquals("pipe.xpl", resolved.displayName(),
                "a .xpl document is XML-family and therefore a legitimate query target");
    }

    // ----- helpers ---------------------------------------------------------

    private OpenDocument docNamed(String displayName) {
        return host.getOpenDocuments().stream()
                .filter(d -> displayName.equals(d.getDisplayName()))
                .findFirst().orElseThrow();
    }

    private void closeTabNamed(String name) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            TabPane tabPane = (TabPane) host.lookup(".fxt-editor-tabpane");
            tabPane.getTabs().removeIf(t -> t.getText() != null && t.getText().contains(name));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void openAndAwait(Path tmp, String name, String content, String marker) throws Exception {
        openAndAwaitDoc(tmp, name, content, marker);
    }

    private OpenDocument openAndAwaitDoc(Path tmp, String name, String content, String marker)
            throws Exception {
        Path file = tmp.resolve(name);
        Files.writeString(file, content);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains(marker)).orElse(false));
        WaitForAsyncUtils.waitForFxEvents();
        return host.getActiveDocument().orElseThrow();
    }
}
