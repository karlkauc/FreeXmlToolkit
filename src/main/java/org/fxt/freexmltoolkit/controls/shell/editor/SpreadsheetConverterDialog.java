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
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.CsvHandler;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.ConversionConfig;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/**
 * Collects settings for the XML &harr; spreadsheet conversion: direction
 * (XML&rarr;spreadsheet or spreadsheet&rarr;XML), format (Excel/CSV), CSV
 * delimiter, the {@link ConversionConfig} options and, for an import, the XSD whose
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

    /**
     * The settings chosen by the user.
     *
     * @param input for an import, the file to read without asking (the active CSV document), or {@code null}
     *              to ask for one
     */
    public record Settings(Direction direction, Format format, Delimiter delimiter, ConversionConfig config,
                           File schema, File input) {

        /** Settings without an import schema. */
        public Settings(Direction direction, Format format, Delimiter delimiter, ConversionConfig config) {
            this(direction, format, delimiter, config, null, null);
        }

        /** Settings that ask for the import file. */
        public Settings(Direction direction, Format format, Delimiter delimiter, ConversionConfig config,
                        File schema) {
            this(direction, format, delimiter, config, schema, null);
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
    @FXML private CheckBox useActiveInput;

    /** The active CSV document offered as the import's input, or {@code null}. */
    private File activeInput;

    public SpreadsheetConverterDialog() {
        DialogHelper.prepare(this);
        setTitle(I18n.t("SpreadsheetConverterDialog.spreadsheet-converter"));
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/SpreadsheetConverterDialog.fxml"));

        format.getItems().setAll(Format.values());
        format.setValue(Format.EXCEL);
        delimiter.getItems().setAll(Delimiter.values());
        delimiter.setValue(Delimiter.COMMA);
        delimiter.disableProperty().bind(format.valueProperty().isNotEqualTo(Format.CSV));
        // the schema types an import; an export takes the XSD bound to the document
        schemaFile.disableProperty().bind(toXml.selectedProperty().not());
        browseSchema.disableProperty().bind(toXml.selectedProperty().not());

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
        File schemaChoice = direction == Direction.SPREADSHEET_TO_XML && !schema.isEmpty() ? new File(schema) : null;
        File input = direction == Direction.SPREADSHEET_TO_XML && activeInput != null && useActiveInput.isSelected()
                ? activeInput : null;
        return new Settings(direction, format.getValue(), delimiter.getValue(), config, schemaChoice, input);
    }

    /** Pre-selects the direction (Spreadsheet → XML when the active document is no XML). */
    public void setDirection(Direction direction) {
        (direction == Direction.SPREADSHEET_TO_XML ? toXml : toSpreadsheet).setSelected(true);
    }

    /**
     * Offers the active CSV document as the import's input ("Convert the active document (orders.csv)",
     * checked): the import then reads it instead of asking for a file. {@code null} hides the option.
     */
    public void setActiveInput(File csv) {
        activeInput = csv;
        boolean show = csv != null;
        useActiveInput.setVisible(show);
        useActiveInput.setManaged(show);
        if (show) {
            useActiveInput.setText(I18n.t("SpreadsheetConverterDialog.use-active-input", csv.getName()));
            useActiveInput.disableProperty().bind(toXml.selectedProperty().not());
            // the delimiter is only editable for CSV, and the file's own one is the likely choice
            format.setValue(Format.CSV);
            delimiter.setValue(guessDelimiter(csv));
        }
    }

    /** The delimiter that occurs most often in the first line of {@code csv} (tab for .tsv), comma by default. */
    static Delimiter guessDelimiter(File csv) {
        if (csv.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".tsv")) {
            return Delimiter.TAB;
        }
        String first;
        try (var reader = java.nio.file.Files.newBufferedReader(csv.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
            first = reader.readLine();
        } catch (java.io.IOException | java.io.UncheckedIOException e) {
            return Delimiter.COMMA;
        }
        if (first == null) {
            return Delimiter.COMMA;
        }
        Delimiter best = Delimiter.COMMA;
        long bestCount = 0;
        for (Delimiter d : Delimiter.values()) {
            char c = d.config().getDelimiter();
            long count = first.chars().filter(ch -> ch == c).count();
            if (count > bestCount) {
                best = d;
                bestCount = count;
            }
        }
        return best;
    }

    /** Picks the XSD the import reads its values with. */
    @FXML
    public void onBrowseSchema() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("SpreadsheetConverterDialog.schema-for-the-import"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Schema", "*.xsd"));
        File file = FileChooserHelper.showOpenDialog(chooser, getDialogPane().getScene() == null
                ? null : getDialogPane().getScene().getWindow());
        if (file != null) {
            schemaFile.setText(file.getAbsolutePath());
        }
    }

    /** Pre-selects the XSD for an import (e.g. for tests or a remembered choice). */
    public void setImportSchema(File xsd) {
        schemaFile.setText(xsd == null ? "" : xsd.getAbsolutePath());
    }
}
