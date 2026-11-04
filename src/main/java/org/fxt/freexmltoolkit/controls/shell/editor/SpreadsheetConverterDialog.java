package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.service.CsvHandler;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.ConversionConfig;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/**
 * Collects settings for the XML &harr; spreadsheet conversion: direction
 * (XML&rarr;spreadsheet or spreadsheet&rarr;XML), format (Excel/CSV), CSV
 * delimiter, the {@link ConversionConfig} options and, for an Excel import, the XSD whose
 * types the values are read with (empty: the one the sheet declares). The dialog only gathers
 * settings — the caller performs the file chooser + {@link SpreadsheetActionRunner}
 * call, keeping the conversion logic UI-free and testable.
 */
public class SpreadsheetConverterDialog extends Dialog<SpreadsheetConverterDialog.Settings> {

    /** Conversion direction. */
    public enum Direction { XML_TO_SPREADSHEET, SPREADSHEET_TO_XML }

    /** Target/source spreadsheet format. */
    public enum Format { EXCEL, CSV }

    /** Delimiter choice for CSV. */
    public enum Delimiter {
        COMMA(CsvHandler.CsvConfig.comma()),
        SEMICOLON(CsvHandler.CsvConfig.semicolon()),
        TAB(CsvHandler.CsvConfig.tab()),
        PIPE(CsvHandler.CsvConfig.pipe());

        private final CsvHandler.CsvConfig config;

        Delimiter(CsvHandler.CsvConfig config) {
            this.config = config;
        }

        public CsvHandler.CsvConfig config() {
            return config;
        }
    }

    /** The settings chosen by the user. */
    public record Settings(Direction direction, Format format, Delimiter delimiter, ConversionConfig config,
                           File schema) {

        /** Settings without an import schema. */
        public Settings(Direction direction, Format format, Delimiter delimiter, ConversionConfig config) {
            this(direction, format, delimiter, config, null);
        }
    }

    @FXML private RadioButton toSpreadsheet;
    @FXML private RadioButton toXml;
    @FXML private ComboBox<Format> format;
    @FXML private ComboBox<Delimiter> delimiter;
    @FXML private CheckBox includeComments;
    @FXML private CheckBox includeNamespaces;
    @FXML private CheckBox includeCData;
    @FXML private CheckBox includeTypeColumn;
    @FXML private CheckBox prettyPrintXml;
    @FXML private TextField schemaFile;
    @FXML private Button browseSchema;

    public SpreadsheetConverterDialog() {
        setTitle("Spreadsheet Converter");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/SpreadsheetConverterDialog.fxml"));

        format.getItems().setAll(Format.values());
        format.setValue(Format.EXCEL);
        delimiter.getItems().setAll(Delimiter.values());
        delimiter.setValue(Delimiter.COMMA);
        delimiter.disableProperty().bind(format.valueProperty().isNotEqualTo(Format.CSV));
        // the schema types an Excel import; an export takes the XSD bound to the document
        var excelImport = toXml.selectedProperty().and(format.valueProperty().isEqualTo(Format.EXCEL));
        schemaFile.disableProperty().bind(excelImport.not());
        browseSchema.disableProperty().bind(excelImport.not());

        setResultConverter(button -> button == ButtonType.OK ? currentSettings() : null);
    }

    /** @return the settings reflecting the current control state. */
    public Settings currentSettings() {
        ConversionConfig config = new ConversionConfig();
        config.setIncludeComments(includeComments.isSelected());
        config.setIncludeNamespaces(includeNamespaces.isSelected());
        config.setIncludeCData(includeCData.isSelected());
        config.setIncludeTypeColumn(includeTypeColumn.isSelected());
        config.setPrettyPrintXml(prettyPrintXml.isSelected());
        Direction direction = toXml.isSelected()
                ? Direction.SPREADSHEET_TO_XML : Direction.XML_TO_SPREADSHEET;
        String schema = schemaFile.getText() == null ? "" : schemaFile.getText().strip();
        File schemaChoice = direction == Direction.SPREADSHEET_TO_XML && format.getValue() == Format.EXCEL
                && !schema.isEmpty() ? new File(schema) : null;
        return new Settings(direction, format.getValue(), delimiter.getValue(), config, schemaChoice);
    }

    /** Picks the XSD the Excel import reads its values with. */
    @FXML
    public void onBrowseSchema() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Schema for the import");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Schema", "*.xsd"));
        File file = FileChooserHelper.showOpenDialog(chooser, getDialogPane().getScene() == null
                ? null : getDialogPane().getScene().getWindow());
        if (file != null) {
            schemaFile.setText(file.getAbsolutePath());
        }
    }

    /** Pre-selects the XSD for an Excel import (e.g. for tests or a remembered choice). */
    public void setImportSchema(File xsd) {
        schemaFile.setText(xsd == null ? "" : xsd.getAbsolutePath());
    }
}
