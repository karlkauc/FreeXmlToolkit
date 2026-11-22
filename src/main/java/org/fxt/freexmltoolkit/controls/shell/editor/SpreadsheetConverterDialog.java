package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.CsvHandler;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.ConversionConfig;
import org.fxt.freexmltoolkit.service.XmlSpreadsheetConverterService.Layout;
import org.fxt.freexmltoolkit.service.spreadsheet.RecordTableLayout;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.util.FileChooserHelper;
import org.fxt.freexmltoolkit.util.ProjectLinks;

/**
 * Collects settings for the XML &harr; spreadsheet conversion and shows what will come out.
 * <ul>
 *   <li>An export chooses the layout: a table (one row per record, one column per value; the record is a
 *       repeating element picked from the document) or the XPath list (one row per node, the exact way back).</li>
 *   <li>An import names its input file (the active CSV document by default) and, for a foreign table, the
 *       document element and the element each row becomes; a sheet exported here is read back as it was.</li>
 *   <li>The explanation at the top follows the choices, every option has a tooltip, "?" opens the
 *       documentation, and the preview on the right is recomputed in the background after each change.</li>
 * </ul>
 * The dialog only gathers settings — the caller runs {@link SpreadsheetActionRunner}.
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
     * @param config the options, the layout and record of an export and the names a foreign table's rows get
     * @param input  for an import, the file to read, or {@code null} to ask for one
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

    /** Setting that remembers the export layout ({@code table} or {@code xpath}). */
    static final String LAYOUT_KEY = "spreadsheet.layout";
    /** Page of the documentation the "?" button opens. */
    static final String DOCS_URL = ProjectLinks.DOCS_URL + "/guides/convert/#xmlexcelcsv-converter";
    /** Rows an export preview shows. */
    static final int PREVIEW_ROWS = 8;
    /** Lines an import preview shows. */
    static final int PREVIEW_LINES = 24;

    /** Opens the documentation; replaceable by tests. */
    static Consumer<String> browser = ProjectLinks::openInBrowser;

    @FXML private Label helpText;
    @FXML private Button helpButton;
    @FXML private RadioButton toSpreadsheet;
    @FXML private RadioButton toXml;
    @FXML private Label layoutLabel;
    @FXML private Node layoutBox;
    @FXML private RadioButton layoutTable;
    @FXML private RadioButton layoutList;
    @FXML private Label recordLabel;
    @FXML private ComboBox<RecordTableLayout.Candidate> recordChoice;
    @FXML private Label inputLabel;
    @FXML private Node inputBox;
    @FXML private TextField inputFile;
    @FXML private Button browseInput;
    @FXML private Label rootLabel;
    @FXML private TextField rootName;
    @FXML private Label recordNameLabel;
    @FXML private TextField recordName;
    @FXML private Label formatLabel;
    @FXML private ComboBox<Format> format;
    @FXML private Label delimiterLabel;
    @FXML private ComboBox<Delimiter> delimiter;
    @FXML private Label schemaLabel;
    @FXML private Node schemaBox;
    @FXML private TextField schemaFile;
    @FXML private Button browseSchema;
    @FXML private CheckBox includeComments;
    @FXML private CheckBox includeCData;
    @FXML private CheckBox includeTypeColumn;
    @FXML private CheckBox prettyPrintXml;
    @FXML private Label previewNote;
    @FXML private TableView<List<String>> previewTable;
    @FXML private TextArea previewText;

    /** The XML of the active document, read for the export preview (on a worker thread). */
    private Supplier<String> source = () -> null;
    /** Bumped by every preview request; an older result is dropped. */
    private final AtomicLong previewRevision = new AtomicLong();
    private final PauseTransition previewDelay = new PauseTransition(Duration.millis(250));
    /** Guards the candidate list while it is refilled (its value listener would start a new preview). */
    private boolean fillingCandidates;

    public SpreadsheetConverterDialog() {
        DialogHelper.prepare(this);
        setTitle(I18n.t("SpreadsheetConverterDialog.spreadsheet-converter"));
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/SpreadsheetConverterDialog.fxml"));
        setResizable(true);

        format.getItems().setAll(Format.values());
        format.setValue(Format.EXCEL);
        delimiter.getItems().setAll(Delimiter.values());
        delimiter.setValue(Delimiter.COMMA);
        rootName.setText("Records");
        recordName.setText("Record");
        recordChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(RecordTableLayout.Candidate c) {
                return c == null ? I18n.t("SpreadsheetConverterDialog.record-automatic")
                        : I18n.t("SpreadsheetConverterDialog.record-choice", c.name(), c.count(), c.columns(), c.path());
            }

            @Override
            public RecordTableLayout.Candidate fromString(String s) {
                return null;
            }
        });
        // the labels keep their width; the long record choices are cut instead
        for (Label label : List.of(layoutLabel, recordLabel, inputLabel, rootLabel, recordNameLabel, formatLabel,
                delimiterLabel, schemaLabel)) {
            label.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        }
        (Layout.XPATH_LIST.name().equals(rememberedLayout()) ? layoutList : layoutTable).setSelected(true);
        installTooltips();

        previewDelay.setOnFinished(e -> refreshPreview());
        Runnable changed = () -> {
            updateRows();
            schedulePreview();
        };
        toXml.selectedProperty().addListener((obs, o, n) -> changed.run());
        layoutTable.selectedProperty().addListener((obs, o, n) -> changed.run());
        format.valueProperty().addListener((obs, o, n) -> changed.run());
        for (var control : List.of(includeComments, includeCData, includeTypeColumn, prettyPrintXml)) {
            control.selectedProperty().addListener((obs, o, n) -> schedulePreview());
        }
        delimiter.valueProperty().addListener((obs, o, n) -> schedulePreview());
        recordChoice.valueProperty().addListener((obs, o, n) -> {
            if (!fillingCandidates) {
                schedulePreview();
            }
        });
        inputFile.textProperty().addListener((obs, o, n) -> {
            File file = inputFileValue();
            if (file != null && isCsv(file)) {
                delimiter.setValue(guessDelimiter(file));
            }
            changed.run();
        });
        rootName.textProperty().addListener((obs, o, n) -> schedulePreview());
        recordName.textProperty().addListener((obs, o, n) -> schedulePreview());

        updateRows();
        setResultConverter(button -> {
            previewRevision.incrementAndGet(); // a running preview is no longer wanted
            if (button != ButtonType.OK) {
                return null;
            }
            rememberLayout();
            return currentSettings();
        });
    }

    // ------------------------------------------------------------------ settings

    /** @return the settings reflecting the current control state. */
    public Settings currentSettings() {
        ConversionConfig config = new ConversionConfig();
        config.setIncludeComments(includeComments.isSelected());
        config.setIncludeCData(includeCData.isSelected());
        config.setIncludeTypeColumn(includeTypeColumn.isSelected());
        config.setPrettyPrintXml(prettyPrintXml.isSelected());
        config.setLayout(layoutTable.isSelected() ? Layout.TABLE : Layout.XPATH_LIST);
        RecordTableLayout.Candidate record = recordChoice.getValue();
        config.setRecordPath(record == null ? null : record.path());
        config.setRootName(rootName.getText());
        config.setRecordName(recordName.getText());
        Direction direction = toXml.isSelected() ? Direction.SPREADSHEET_TO_XML : Direction.XML_TO_SPREADSHEET;
        String schema = schemaFile.getText() == null ? "" : schemaFile.getText().strip();
        File schemaChoice = direction == Direction.SPREADSHEET_TO_XML && !schema.isEmpty() ? new File(schema) : null;
        File input = direction == Direction.SPREADSHEET_TO_XML ? inputFileValue() : null;
        return new Settings(direction, format.getValue(), delimiter.getValue(), config, schemaChoice, input);
    }

    /** Pre-selects the direction (Spreadsheet → XML when the active document is no XML). */
    public void setDirection(Direction direction) {
        (direction == Direction.SPREADSHEET_TO_XML ? toXml : toSpreadsheet).setSelected(true);
    }

    /**
     * Pre-fills the import's input with the active CSV document; the format becomes CSV and the delimiter
     * the one its first line uses. {@code null} leaves the field empty.
     */
    public void setActiveInput(File csv) {
        if (csv == null) {
            return;
        }
        inputFile.setText(csv.getAbsolutePath());
        format.setValue(Format.CSV);
        delimiter.setValue(guessDelimiter(csv));
    }

    /** The active document's XML for the export preview and the record choice; read on a worker thread. */
    public void setSource(Supplier<String> xml) {
        source = xml == null ? () -> null : xml;
        schedulePreview();
    }

    /** Pre-selects the XSD for an import (e.g. for tests or a remembered choice). */
    public void setImportSchema(File xsd) {
        schemaFile.setText(xsd == null ? "" : xsd.getAbsolutePath());
    }

    private File inputFileValue() {
        String text = inputFile.getText() == null ? "" : inputFile.getText().strip();
        return text.isEmpty() ? null : new File(text);
    }

    private static boolean isCsv(File file) {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".csv") || name.endsWith(".tsv") || name.endsWith(".txt");
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

    // ------------------------------------------------------------------ layout of the form

    /** Shows the rows that apply to the direction and layout, and the matching explanation. */
    private void updateRows() {
        boolean export = !toXml.isSelected();
        boolean table = layoutTable.isSelected();
        show(export, layoutLabel, layoutBox, formatLabel, format, includeComments, includeCData);
        show(export && table, recordLabel, recordChoice);
        show(export && !table, includeTypeColumn);
        File input = inputFileValue();
        boolean csvInput = input != null && isCsv(input);
        show(!export, inputLabel, inputBox, rootLabel, rootName, recordNameLabel, recordName, schemaLabel, schemaBox,
                prettyPrintXml);
        show(export ? format.getValue() == Format.CSV : csvInput || input == null, delimiterLabel, delimiter);
        helpText.setText(I18n.t(export
                ? (table ? "SpreadsheetConverterDialog.help-export-table" : "SpreadsheetConverterDialog.help-export-list")
                : "SpreadsheetConverterDialog.help-import"));
        previewTable.setVisible(export);
        previewText.setVisible(!export);
    }

    private static void show(boolean visible, Node... nodes) {
        for (Node node : nodes) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    private void installTooltips() {
        tip(toSpreadsheet, "SpreadsheetConverterDialog.tip-to-spreadsheet");
        tip(toXml, "SpreadsheetConverterDialog.tip-to-xml");
        tip(layoutTable, "SpreadsheetConverterDialog.tip-layout-table");
        tip(layoutList, "SpreadsheetConverterDialog.tip-layout-list");
        tip(recordChoice, "SpreadsheetConverterDialog.tip-record");
        tip(inputFile, "SpreadsheetConverterDialog.tip-input");
        tip(rootName, "SpreadsheetConverterDialog.tip-root");
        tip(recordName, "SpreadsheetConverterDialog.tip-record-name");
        tip(format, "SpreadsheetConverterDialog.tip-format");
        tip(delimiter, "SpreadsheetConverterDialog.tip-delimiter");
        tip(schemaFile, "SpreadsheetConverterDialog.tip-schema");
        tip(includeComments, "SpreadsheetConverterDialog.tip-comments");
        tip(includeCData, "SpreadsheetConverterDialog.tip-cdata");
        tip(includeTypeColumn, "SpreadsheetConverterDialog.tip-type-column");
        tip(prettyPrintXml, "SpreadsheetConverterDialog.tip-pretty-print");
    }

    private static void tip(Control control, String key) {
        Tooltip tooltip = new Tooltip(I18n.t(key));
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(420);
        tooltip.setShowDuration(Duration.seconds(30));
        control.setTooltip(tooltip);
    }

    // ------------------------------------------------------------------ preview

    private void schedulePreview() {
        previewDelay.playFromStart();
    }

    /** Computes the preview for the current settings on a worker thread; only the newest result is shown. */
    void refreshPreview() {
        long revision = previewRevision.incrementAndGet();
        Settings settings = currentSettings();
        previewNote.setText(I18n.t("SpreadsheetConverterDialog.preview-working"));
        if (settings.direction() == Direction.XML_TO_SPREADSHEET) {
            Supplier<String> xml = source;
            Thread.ofVirtual().name("spreadsheet-preview").start(() -> {
                var preview = SpreadsheetActionRunner.exportPreview(xml.get(), settings.config(), PREVIEW_ROWS);
                Platform.runLater(() -> {
                    if (previewRevision.get() == revision) {
                        showExportPreview(preview);
                    }
                });
            });
        } else {
            Thread.ofVirtual().name("spreadsheet-preview").start(() -> {
                String text = SpreadsheetActionRunner.importPreview(settings.input(), settings.delimiter().config(),
                        settings.config(), PREVIEW_LINES);
                Platform.runLater(() -> {
                    if (previewRevision.get() == revision) {
                        previewText.setText(text);
                        previewNote.setText(settings.input() == null ? ""
                                : I18n.t("SpreadsheetConverterDialog.preview-import-note"));
                    }
                });
            });
        }
    }

    private void showExportPreview(SpreadsheetActionRunner.ExportPreview preview) {
        if (!preview.candidates().isEmpty() && !preview.candidates().equals(recordChoice.getItems())) {
            RecordTableLayout.Candidate chosen = recordChoice.getValue();
            fillingCandidates = true;
            try {
                recordChoice.getItems().setAll(preview.candidates());
                recordChoice.setValue(chosen != null && preview.candidates().contains(chosen) ? chosen
                        : preview.candidates().stream().filter(c -> c.path().equals(preview.recordPath()))
                        .findFirst().orElse(preview.candidates().getFirst()));
            } finally {
                fillingCandidates = false;
            }
        }
        previewTable.getColumns().clear();
        previewTable.getItems().clear();
        if (preview.error() != null) {
            previewNote.setText(preview.error());
            return;
        }
        List<String> headers = preview.headers();
        for (int i = 0; i < headers.size(); i++) {
            int column = i;
            TableColumn<List<String>, String> tc = new TableColumn<>(headers.get(i));
            tc.setCellValueFactory(row -> new ReadOnlyStringWrapper(
                    column < row.getValue().size() ? row.getValue().get(column) : ""));
            tc.setSortable(false);
            tc.setPrefWidth(Math.max(70, Math.min(220, headers.get(i).length() * 8 + 24)));
            previewTable.getColumns().add(tc);
        }
        previewTable.getItems().setAll(preview.rows());
        String note;
        if (preview.recordPath() == null) {
            note = I18n.t("SpreadsheetConverterDialog.preview-list-note", preview.rows().size(), preview.totalRows());
        } else {
            String name = preview.recordPath().substring(preview.recordPath().lastIndexOf('/') + 1);
            note = I18n.t("SpreadsheetConverterDialog.preview-table-note", preview.rows().size(), preview.totalRows(),
                    name, headers.size());
            if (preview.otherData()) {
                note += " " + I18n.t(format.getValue() == Format.CSV
                        ? "SpreadsheetConverterDialog.preview-other-csv" : "SpreadsheetConverterDialog.preview-other-excel");
            }
        }
        previewNote.setText(note);
    }

    /** @return the shown preview's header row (tests) */
    List<String> previewHeaders() {
        return previewTable.getColumns().stream().map(TableColumn::getText).toList();
    }

    /** @return the shown import preview (tests) */
    String previewXml() {
        return previewText.getText();
    }

    /** @return the record choices (tests) */
    List<RecordTableLayout.Candidate> recordChoices() {
        return List.copyOf(recordChoice.getItems());
    }

    // ------------------------------------------------------------------ actions

    /** Opens the converter's documentation. */
    @FXML
    public void onHelp() {
        browser.accept(DOCS_URL);
    }

    /** Picks the spreadsheet to import. */
    @FXML
    public void onBrowseInput() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("UnifiedShellView.import-spreadsheet"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel / CSV", "*.xlsx", "*.xls", "*.csv", "*.tsv"));
        File file = FileChooserHelper.showOpenDialog(chooser, getDialogPane().getScene() == null
                ? null : getDialogPane().getScene().getWindow());
        if (file != null) {
            inputFile.setText(file.getAbsolutePath());
        }
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

    // ------------------------------------------------------------------ remembered layout

    private static String rememberedLayout() {
        try {
            String value = ServiceRegistry.get(PropertiesService.class).get(LAYOUT_KEY);
            return "xpath".equals(value) ? Layout.XPATH_LIST.name() : Layout.TABLE.name();
        } catch (Throwable t) {
            return Layout.TABLE.name(); // no settings (isolated tests)
        }
    }

    private void rememberLayout() {
        if (toXml.isSelected()) {
            return; // an import reads the layout from the file
        }
        try {
            ServiceRegistry.get(PropertiesService.class).set(LAYOUT_KEY, layoutTable.isSelected() ? "table" : "xpath");
        } catch (Throwable t) {
            // settings unavailable: the choice holds for this dialog only
        }
    }
}
