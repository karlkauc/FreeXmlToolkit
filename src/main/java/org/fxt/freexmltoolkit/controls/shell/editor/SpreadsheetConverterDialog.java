package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;

import org.fxt.freexmltoolkit.service.CsvHandler;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.ConversionConfig;

/**
 * Collects settings for the XML &harr; spreadsheet conversion: direction
 * (XML&rarr;spreadsheet or spreadsheet&rarr;XML), format (Excel/CSV), CSV
 * delimiter, and the {@link ConversionConfig} options. The dialog only gathers
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
    public record Settings(Direction direction, Format format, Delimiter delimiter, ConversionConfig config) {
    }

    private final ToggleGroup directionGroup = new ToggleGroup();
    private final RadioButton toSpreadsheet = new RadioButton("XML → Spreadsheet");
    private final RadioButton toXml = new RadioButton("Spreadsheet → XML");
    private final ComboBox<Format> format = new ComboBox<>();
    private final ComboBox<Delimiter> delimiter = new ComboBox<>();
    private final CheckBox includeComments = new CheckBox("Include comments");
    private final CheckBox includeNamespaces = new CheckBox("Include namespaces");
    private final CheckBox includeCData = new CheckBox("Include CDATA");
    private final CheckBox includeTypeColumn = new CheckBox("Include type column");
    private final CheckBox prettyPrintXml = new CheckBox("Pretty-print XML");

    public SpreadsheetConverterDialog() {
        setTitle("Spreadsheet Converter");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        toSpreadsheet.setToggleGroup(directionGroup);
        toXml.setToggleGroup(directionGroup);
        toSpreadsheet.setSelected(true);

        format.getItems().setAll(Format.values());
        format.setValue(Format.EXCEL);
        delimiter.getItems().setAll(Delimiter.values());
        delimiter.setValue(Delimiter.COMMA);
        delimiter.disableProperty().bind(format.valueProperty().isNotEqualTo(Format.CSV));

        // Defaults mirror the service defaults (everything on).
        includeComments.setSelected(true);
        includeNamespaces.setSelected(true);
        includeCData.setSelected(true);
        includeTypeColumn.setSelected(true);
        prettyPrintXml.setSelected(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(16));
        int r = 0;
        grid.add(new Label("Direction:"), 0, r);
        grid.add(toSpreadsheet, 1, r++);
        grid.add(toXml, 1, r++);
        grid.add(new Label("Format:"), 0, r);
        grid.add(format, 1, r++);
        grid.add(new Label("CSV delimiter:"), 0, r);
        grid.add(delimiter, 1, r++);
        grid.add(new Label("Options:"), 0, r);
        grid.add(includeComments, 1, r++);
        grid.add(includeNamespaces, 1, r++);
        grid.add(includeCData, 1, r++);
        grid.add(includeTypeColumn, 1, r++);
        grid.add(prettyPrintXml, 1, r);

        getDialogPane().setContent(grid);
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
        return new Settings(direction, format.getValue(), delimiter.getValue(), config);
    }
}
