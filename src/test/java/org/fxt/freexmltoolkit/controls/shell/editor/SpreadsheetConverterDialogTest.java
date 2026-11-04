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
    void schemaIsOfferedForAnExcelImportOnly() {
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
}
