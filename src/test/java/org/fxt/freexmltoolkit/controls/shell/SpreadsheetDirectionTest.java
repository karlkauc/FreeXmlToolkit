package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.OpenDocument;
import org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetActionRunner;
import org.fxt.freexmltoolkit.service.telemetry.ElementCounter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXParseException;

/**
 * A CSV document in the editor is converted Spreadsheet → XML, and "export to a spreadsheet" of a document
 * without any XML element is told apart from a broken XML document (telemetry 3.2.0: a CSV was exported as
 * XML three times and failed as "not well-formed").
 */
class SpreadsheetDirectionTest {

    @Test
    void aSavedCsvDocumentIsOfferedAsTheImportInput(@TempDir Path tmp) throws Exception {
        Path csv = Files.writeString(tmp.resolve("orders.csv"), "id;name\n1;a\n");
        assertEquals(csv.toFile(), UnifiedShellView.activeCsvFile(OpenDocument.forPath(csv)));
        assertEquals(tmp.resolve("x.tsv").toFile(),
                UnifiedShellView.activeCsvFile(OpenDocument.forPath(tmp.resolve("x.tsv"))));

        OpenDocument dirty = OpenDocument.forPath(csv);
        dirty.setDirty(true);
        assertNull(UnifiedShellView.activeCsvFile(dirty), "unsaved edits: the file on disk is not what is shown");
        assertNull(UnifiedShellView.activeCsvFile(OpenDocument.forPath(tmp.resolve("a.xml"))));
        assertNull(UnifiedShellView.activeCsvFile(OpenDocument.untitled("Untitled", EditorFileType.XML)));
        assertNull(UnifiedShellView.activeCsvFile(null));
    }

    @Test
    void aCsvFailsWithoutAnyElementWhileBrokenXmlHasElements(@TempDir Path tmp) {
        String csv = "id;name\n1;a\n2;b\n";
        var result = SpreadsheetActionRunner.exportToExcel(csv, new File(tmp.toFile(), "out.xlsx"));
        assertInstanceOf(SAXParseException.class, result.error());
        assertEquals(0, ElementCounter.countStartTags(csv), "no element: reported as \"not XML\"");

        String broken = "<root><a></root>";
        var brokenResult = SpreadsheetActionRunner.exportToExcel(broken, new File(tmp.toFile(), "out2.xlsx"));
        assertInstanceOf(SAXParseException.class, brokenResult.error());
        assertTrue(ElementCounter.countStartTags(broken) > 0, "elements: reported with line and column");
    }
}
