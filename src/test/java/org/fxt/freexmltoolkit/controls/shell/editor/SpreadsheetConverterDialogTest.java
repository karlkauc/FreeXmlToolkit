package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.control.Control;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.Layout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The converter dialog: settings mapping (form ↔ Settings), rows per direction, the record choice and
 * preview, help and tooltips - without showing it modally.
 */
@ExtendWith(ApplicationExtension.class)
class SpreadsheetConverterDialogTest {

    private static final String ORDERS = "<Orders><Order id=\"1\"><Qty>3</Qty></Order><Order id=\"2\"><Qty>1</Qty></Order>"
            + "<Order id=\"3\"><Qty>5</Qty></Order></Orders>";

    private SpreadsheetConverterDialog dialog;

    @Start
    void start(Stage stage) {
        dialog = new SpreadsheetConverterDialog();
    }

    @AfterEach
    void restoreBrowser() {
        SpreadsheetConverterDialog.browser = org.fxt.freexmltoolkit.util.ProjectLinks::openInBrowser;
    }

    private <T> T fx(java.util.concurrent.Callable<T> action) {
        return WaitForAsyncUtils.waitForAsyncFx(3000, action);
    }

    private javafx.scene.Node node(String id) {
        return fx(() -> dialog.getDialogPane().lookup(id));
    }

    /** @return whether the node and all its parents are visible */
    private boolean shown(String id) {
        return fx(() -> {
            for (javafx.scene.Node n = dialog.getDialogPane().lookup(id); n != null; n = n.getParent()) {
                if (!n.isVisible()) {
                    return false;
                }
            }
            return true;
        });
    }

    @Test
    void defaultsToAnExcelTableWithTheOptionsOn() {
        var settings = fx(() -> dialog.currentSettings());
        assertEquals(SpreadsheetConverterDialog.Direction.XML_TO_SPREADSHEET, settings.direction());
        assertEquals(SpreadsheetConverterDialog.Format.EXCEL, settings.format());
        assertEquals(Layout.TABLE, settings.config().getLayout(), "the table is the layout for people who do not know XML");
        assertNull(settings.config().getRecordPath(), "no record chosen: the default one");
        assertTrue(settings.config().isIncludeComments());
        assertTrue(settings.config().isPrettyPrintXml());
        assertNull(settings.input());
    }

    @Test
    void eachDirectionShowsOnlyItsRows() {
        assertTrue(shown("#spreadsheet-layout-table"));
        assertTrue(shown("#spreadsheet-record"));
        assertFalse(shown("#spreadsheet-input"));
        assertFalse(shown("#spreadsheet-type-column"), "the type column belongs to the XPath list");
        fx(() -> {
            dialog.setDirection(SpreadsheetConverterDialog.Direction.SPREADSHEET_TO_XML);
            return null;
        });
        assertTrue(shown("#spreadsheet-input"));
        assertTrue(shown("#spreadsheet-root-name"));
        assertTrue(shown("#spreadsheet-import-schema"));
        assertFalse(shown("#spreadsheet-layout-table"), "an import reads the layout from the file");
        assertFalse(shown("#spreadsheet-format"));
    }

    @Test
    void theXPathListOffersTheTypeColumn() {
        var settings = fx(() -> {
            ((javafx.scene.control.RadioButton) dialog.getDialogPane().lookup("#spreadsheet-layout-list")).setSelected(true);
            return dialog.currentSettings();
        });
        assertEquals(Layout.XPATH_LIST, settings.config().getLayout());
        assertTrue(shown("#spreadsheet-type-column"));
        assertFalse(shown("#spreadsheet-record"));
    }

    @Test
    void theExportPreviewShowsTheTableAndOffersTheRecords() throws Exception {
        fx(() -> {
            dialog.setSource(() -> ORDERS);
            dialog.refreshPreview();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !fx(() -> dialog.previewHeaders()).isEmpty());
        assertEquals(List.of("@id", "Qty"), fx(() -> dialog.previewHeaders()));
        assertEquals("/Orders/Order", fx(() -> dialog.recordChoices()).getFirst().path());
        assertEquals("/Orders/Order", fx(() -> dialog.currentSettings()).config().getRecordPath());
        String note = fx(() -> ((javafx.scene.control.Label) dialog.getDialogPane().lookup("#spreadsheet-preview-note")).getText());
        assertTrue(note.contains("3"), note);
    }

    @Test
    void theImportPreviewShowsTheXmlOfAForeignCsv(@TempDir Path tmp) throws Exception {
        File csv = Files.writeString(tmp.resolve("people.csv"), "id;name\n1;Anna\n").toFile();
        fx(() -> {
            dialog.setDirection(SpreadsheetConverterDialog.Direction.SPREADSHEET_TO_XML);
            dialog.setActiveInput(csv);
            ((TextField) dialog.getDialogPane().lookup("#spreadsheet-root-name")).setText("People");
            ((TextField) dialog.getDialogPane().lookup("#spreadsheet-record-name")).setText("Person");
            dialog.refreshPreview();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> fx(() -> dialog.previewXml()).contains("<Person>"));
        String xml = fx(() -> dialog.previewXml());
        assertTrue(xml.contains("<People>") && xml.contains("<name>Anna</name>"), xml);
        var settings = fx(() -> dialog.currentSettings());
        assertEquals(csv.getAbsoluteFile(), settings.input().getAbsoluteFile());
        assertEquals(SpreadsheetConverterDialog.Format.CSV, settings.format());
        assertEquals(SpreadsheetConverterDialog.Delimiter.SEMICOLON, settings.delimiter());
        assertEquals("People", settings.config().getRootName());
    }

    @Test
    void schemaIsOfferedForAnImportOnly() {
        var settings = fx(() -> {
            dialog.setImportSchema(new File("/tmp/order.xsd"));
            return dialog.currentSettings();
        });
        assertNull(settings.schema(), "an export uses the bound XSD");
        settings = fx(() -> {
            dialog.setDirection(SpreadsheetConverterDialog.Direction.SPREADSHEET_TO_XML);
            return dialog.currentSettings();
        });
        assertEquals(new File("/tmp/order.xsd"), settings.schema());
    }

    @Test
    void theDelimiterIsGuessedFromTheFile(@TempDir Path tmp) throws Exception {
        assertEquals(SpreadsheetConverterDialog.Delimiter.SEMICOLON, SpreadsheetConverterDialog.guessDelimiter(
                Files.writeString(tmp.resolve("a.csv"), "id;name;qty\n1;a,b;2\n").toFile()));
        assertEquals(SpreadsheetConverterDialog.Delimiter.TAB,
                SpreadsheetConverterDialog.guessDelimiter(tmp.resolve("x.tsv").toFile()));
        assertEquals(SpreadsheetConverterDialog.Delimiter.COMMA,
                SpreadsheetConverterDialog.guessDelimiter(tmp.resolve("missing.csv").toFile()));
    }

    @Test
    void helpOpensTheDocumentationAndEveryOptionExplainsItself() {
        String[] opened = new String[1];
        SpreadsheetConverterDialog.browser = url -> opened[0] = url;
        fx(() -> {
            dialog.onHelp();
            return null;
        });
        assertTrue(opened[0].endsWith("/xml-editor/#xmlexcelcsv-converter"), opened[0]);
        String help = fx(() -> ((javafx.scene.control.Label) dialog.getDialogPane().lookup("#spreadsheet-help-text")).getText());
        assertFalse(help.isBlank());
        for (String id : List.of("#spreadsheet-layout-table", "#spreadsheet-layout-list", "#spreadsheet-record",
                "#spreadsheet-format", "#spreadsheet-delimiter", "#spreadsheet-comments", "#spreadsheet-cdata",
                "#spreadsheet-type-column", "#spreadsheet-pretty-print", "#spreadsheet-input", "#spreadsheet-root-name",
                "#spreadsheet-record-name", "#spreadsheet-import-schema")) {
            Control control = (Control) node(id);
            assertNotNull(control.getTooltip(), id + " has a tooltip");
        }
    }
}
