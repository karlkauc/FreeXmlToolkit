package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.standards.XQueryVersion;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Query Console runs an XQuery with the saved document's location as base URI, so relative
 * {@code doc()} / {@code unparsed-text()} resolve next to the document (telemetry: FODC0005 / XTDE1162
 * when they had no base URI).
 */
class QueryBaseUriTest {

    @TempDir
    Path dir;

    private String baseUri() throws Exception {
        Files.writeString(dir.resolve("sibling.xml"), "<v>42</v>");
        Files.writeString(dir.resolve("note.txt"), "hello");
        return dir.resolve("main.xml").toUri().toString();
    }

    @Test
    void relativeDocResolvesNextToTheDocument() throws Exception {
        String base = baseUri();
        TransformRunner.QueryRun run = TransformRunner.xquery("<root/>", "string(doc('sibling.xml')/v)",
                Map.of(), OutputFormat.TEXT, XQueryVersion.V3_1, base);
        assertNull(run.failure(), run.text());
        assertTrue(run.text().contains("42"), run.text());
    }

    @Test
    void relativeUnparsedTextResolvesNextToTheDocument() throws Exception {
        String base = baseUri();
        TransformRunner.QueryRun run = TransformRunner.xquery("<root/>", "unparsed-text('note.txt')",
                Map.of(), OutputFormat.TEXT, XQueryVersion.V3_1, base);
        assertNull(run.failure(), run.text());
        assertTrue(run.text().contains("hello"), run.text());
    }

    @Test
    void tableAndHitsUseTheBaseUriToo() throws Exception {
        String base = baseUri();
        var table = XQueryTableRunner.run("<root/>", "doc('sibling.xml')/v", base);
        assertNull(table.error(), table.error());
        var items = XPathMatchLocator.locateXQueryItems("<root/>", "string(doc('sibling.xml')/v)", Map.of(), base);
        assertEquals(1, items.size());
    }
}
