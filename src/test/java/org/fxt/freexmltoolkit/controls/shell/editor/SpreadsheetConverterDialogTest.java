package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the converter dialog's settings mapping (form ↔ Settings), without
 * showing it modally.
 */
@ExtendWith(ApplicationExtension.class)
class SpreadsheetConverterDialogTest {

    private SpreadsheetConverterDialog dialog;

    @Start
    void start(Stage stage) {
        dialog = new SpreadsheetConverterDialog();
    }

    @Test
    void defaultsToXmlToExcelWithAllOptionsOn() {
        var settings = WaitForAsyncUtils.waitForAsyncFx(2000, () -> dialog.currentSettings());
        assertEquals(SpreadsheetConverterDialog.Direction.XML_TO_SPREADSHEET, settings.direction());
        assertEquals(SpreadsheetConverterDialog.Format.EXCEL, settings.format());
        assertTrue(settings.config().isIncludeComments());
        assertTrue(settings.config().isIncludeNamespaces());
        assertTrue(settings.config().isPrettyPrintXml());
    }

    @Test
    void schemaIsOfferedForAnImportOnly() {
        var schemaField = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> (javafx.scene.control.TextField) dialog.getDialogPane().lookup("#spreadsheet-import-schema"));
        assertTrue(schemaField.isDisabled(), "an export uses the bound XSD");
        var settings = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            dialog.getDialogPane().lookupAll(".radio-button").stream()
                    .map(javafx.scene.control.RadioButton.class::cast)
                    .filter(rb -> rb.getText().startsWith("Spreadsheet"))
                    .findFirst().orElseThrow().setSelected(true);
            dialog.setImportSchema(new java.io.File("/tmp/order.xsd"));
            return dialog.currentSettings();
        });
        assertFalse(schemaField.isDisabled());
        assertEquals(new java.io.File("/tmp/order.xsd"), settings.schema());
    }

    @Test
    void delimiterMapsToCsvConfig() {
        var settings = WaitForAsyncUtils.waitForAsyncFx(2000, () -> dialog.currentSettings());
        assertEquals(',', settings.delimiter().config().getDelimiter());
    }

    @Test
    void startsWithTheGivenDirectionAndOffersTheActiveCsv() {
        java.io.File csv = new java.io.File("/tmp/orders.csv");
        var settings = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            dialog.setActiveInput(csv);
            dialog.setDirection(SpreadsheetConverterDialog.Direction.SPREADSHEET_TO_XML);
            return dialog.currentSettings();
        });
        assertEquals(SpreadsheetConverterDialog.Direction.SPREADSHEET_TO_XML, settings.direction());
        assertEquals(csv, settings.input(), "the active CSV is read without asking for a file");
        var useActive = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> (javafx.scene.control.CheckBox) dialog.getDialogPane().lookup("#spreadsheet-use-active-input"));
        assertTrue(useActive.isVisible());
        assertTrue(useActive.getText().contains("orders.csv"));

        var unchecked = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            useActive.setSelected(false);
            return dialog.currentSettings();
        });
        assertNull(unchecked.input(), "unchecked: the import asks for a file");
        var export = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            useActive.setSelected(true);
            dialog.setDirection(SpreadsheetConverterDialog.Direction.XML_TO_SPREADSHEET);
            return dialog.currentSettings();
        });
        assertNull(export.input(), "an export has no input file");
        assertTrue(useActive.isDisabled());
    }

    @Test
    void theActiveInputOptionIsHiddenWithoutACsv() {
        var useActive = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> (javafx.scene.control.CheckBox) dialog.getDialogPane().lookup("#spreadsheet-use-active-input"));
        assertFalse(useActive.isVisible());
        assertNull(WaitForAsyncUtils.waitForAsyncFx(2000, () -> dialog.currentSettings()).input());
    }

    @Test
    void theActiveCsvSetsFormatAndItsDelimiter(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp)
            throws Exception {
        var csv = java.nio.file.Files.writeString(tmp.resolve("orders.csv"), "id;name;qty\n1;a,b;2\n").toFile();
        var settings = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            dialog.setActiveInput(csv);
            dialog.setDirection(SpreadsheetConverterDialog.Direction.SPREADSHEET_TO_XML);
            return dialog.currentSettings();
        });
        assertEquals(SpreadsheetConverterDialog.Format.CSV, settings.format());
        assertEquals(SpreadsheetConverterDialog.Delimiter.SEMICOLON, settings.delimiter());
        assertEquals(SpreadsheetConverterDialog.Delimiter.TAB,
                SpreadsheetConverterDialog.guessDelimiter(tmp.resolve("x.tsv").toFile()));
        assertEquals(SpreadsheetConverterDialog.Delimiter.COMMA,
                SpreadsheetConverterDialog.guessDelimiter(tmp.resolve("missing.csv").toFile()));
    }
}
