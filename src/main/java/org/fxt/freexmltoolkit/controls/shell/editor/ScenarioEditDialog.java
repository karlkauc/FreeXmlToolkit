package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff;
import org.fxt.freexmltoolkit.service.project.FxtProject;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/**
 * Edits every field of a transformation scenario (Project Settings → Scenarios): name, stylesheet /
 * query / pipeline, input ({@code ${file}} or a fixed file), parameters, output method (incl. PDF) and
 * output file. The result is a new {@link FxtProject.Scenario}.
 */
public class ScenarioEditDialog extends Dialog<FxtProject.Scenario> {

    /** The output methods offered; "" = from the stylesheet's xsl:output. */
    static final List<String> FORMATS = List.of("", "XML", "HTML", "XHTML", "TEXT", "JSON", "PDF");

    @FXML private TextField nameField;
    @FXML private TextField stylesheetField;
    @FXML private TextField inputField;
    @FXML private TextArea parametersArea;
    @FXML private ComboBox<String> formatChoice;
    @FXML private ComboBox<String> processorChoice;
    @FXML private TextField outputField;
    @FXML private TextField expectedField;
    @FXML private TextArea ignoreArea;
    @FXML private TextField toleranceField;
    @FXML private javafx.scene.control.CheckBox ignoreOrderBox;
    @FXML private TextField keysField;
    @FXML private Label error;
    /** Optional language version of the scenario's stylesheet / query (expert mode only). */
    private javafx.scene.control.ComboBox<String> languageVersionChoice;
    /** The XSLT processor (empty = the default of the settings, {@code builtin} = Saxon HE, else a name). */
    private final javafx.scene.control.ComboBox<String> xsltProcessorChoice = new javafx.scene.control.ComboBox<>();

    private final FxtProject project;
    private final Set<String> otherNames;
    /** Compare settings the dialog does not edit (placeholders, comments, whitespace) are kept. */
    private FxtProject.CompareSettings originalCompare;

    /**
     * @param scenario   the scenario to start with (a new one has no name)
     * @param otherNames names of the project's other scenarios (a name must be unique)
     */
    public ScenarioEditDialog(FxtProject project, FxtProject.Scenario scenario, Set<String> otherNames) {
        DialogHelper.prepare(this);
        this.project = project;
        this.otherNames = otherNames;
        setTitle(I18n.t("ScenarioEditDialog.title"));
        setHeaderText(I18n.t("ScenarioEditDialog.header", project.name()));
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        javafx.scene.Node content = FxmlViews.loadContent(this, "/pages/dialogs/ScenarioEditDialog.fxml");
        getDialogPane().setContent(content);
        languageVersionChoice = org.fxt.freexmltoolkit.controls.shell.editor.standards.OptionalVersionRows.combo(
                List.of("1.0", "2.0", "3.0", "3.1"), scenario.languageVersion);
        if (content instanceof javafx.scene.layout.GridPane grid) {
            org.fxt.freexmltoolkit.controls.shell.editor.standards.OptionalVersionRows.addRow(grid,
                    I18n.t("ScenarioEditDialog.languageVersion"), languageVersionChoice, "scenario-language-version");
            addXsltProcessorRow(grid, scenario.xsltProcessor);
        }
        formatChoice.getItems().setAll(FORMATS);
        formatChoice.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(String format) {
                return format == null || format.isEmpty() ? I18n.t("ScenarioEditDialog.auto") : format;
            }

            @Override
            public String fromString(String text) {
                return text;
            }
        });
        nameField.setText(nullToEmpty(scenario.name));
        stylesheetField.setText(nullToEmpty(scenario.stylesheet));
        inputField.setText(nullToEmpty(scenario.input));
        parametersArea.setText(parametersText(scenario.parameters));
        String format = scenario.outputFormat == null ? "" : scenario.outputFormat.strip().toUpperCase(Locale.ROOT);
        formatChoice.setValue(FORMATS.contains(format) ? format : "");
        outputField.setText(nullToEmpty(scenario.output));
        processorChoice.getItems().setAll(processorNames());
        processorChoice.setValue(nullToEmpty(scenario.processor));
        processorChoice.visibleProperty().bind(formatChoice.valueProperty().isEqualTo("PDF"));
        processorChoice.managedProperty().bind(processorChoice.visibleProperty());
        expectedField.setText(nullToEmpty(scenario.expected));
        FxtProject.CompareSettings compare = scenario.compare;
        if (compare != null) {
            ignoreArea.setText(compare.ignore == null ? "" : String.join("\n", compare.ignore));
            toleranceField.setText(compare.tolerance == null || compare.tolerance == 0 ? ""
                    : java.math.BigDecimal.valueOf(compare.tolerance).stripTrailingZeros().toPlainString());
            ignoreOrderBox.setSelected(Boolean.TRUE.equals(compare.ignoreOrder));
            keysField.setText(compare.keys == null ? "" : String.join(", ", compare.keys));
        }
        this.originalCompare = compare;
        for (var field : List.of(nameField, stylesheetField, inputField, outputField, expectedField, toleranceField)) {
            field.textProperty().addListener((obs, o, n) -> validate());
        }
        parametersArea.textProperty().addListener((obs, o, n) -> validate());
        ignoreArea.textProperty().addListener((obs, o, n) -> validate());
        formatChoice.valueProperty().addListener((obs, o, n) -> validate());
        validate();
        setResultConverter(button -> button == ButtonType.OK && validate() == null ? scenario() : null);
    }

    private FxtProject.Scenario scenario() {
        FxtProject.Scenario scenario = new FxtProject.Scenario(nameField.getText().strip(), stylesheetField.getText().strip(),
                blankToNull(inputField.getText()), parseParameters(parametersArea.getText()),
                blankToNull(formatChoice.getValue()), blankToNull(outputField.getText()));
        scenario.expected = blankToNull(expectedField.getText());
        scenario.processor = scenario.isPdf() ? blankToNull(processorChoice.getEditor().getText()) : null;
        FxtProject.CompareSettings compare = originalCompare == null ? new FxtProject.CompareSettings()
                : originalCompare.copy();
        compare.ignore = new java.util.ArrayList<>(XmlStructuralDiff.Options.parseXPaths(ignoreArea.getText()));
        compare.tolerance = tolerance() == 0 ? null : tolerance();
        compare.ignoreOrder = ignoreOrderBox.isSelected() ? Boolean.TRUE : null;
        compare.keys = new java.util.ArrayList<>(XmlStructuralDiff.Options.parseKeys(keysField.getText()));
        scenario.compare = compare.isDefault() ? null : compare;
        scenario.languageVersion = org.fxt.freexmltoolkit.controls.shell.editor.standards.OptionalVersionRows.value(
                languageVersionChoice);
        String xsltProcessor = xsltProcessorChoice.getValue();
        scenario.xsltProcessor = scenario.stylesheet.toLowerCase(Locale.ROOT).matches(".*\\.(xsl|xslt)$")
                ? blankToNull(xsltProcessor) : null;
        return scenario;
    }

    /** The row "XSLT processor": empty (default of the settings), builtin (Saxon HE) or a configured processor's name. */
    private void addXsltProcessorRow(javafx.scene.layout.GridPane grid, String current) {
        List<String> items = new java.util.ArrayList<>();
        items.add("");
        items.add(org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorStore.BUILTIN_ID);
        try {
            org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorStore.settings().list().forEach(p -> items.add(p.name()));
        } catch (RuntimeException e) {
            // no settings (tests): Saxon HE only
        }
        String value = current == null ? "" : current.strip();
        if (!items.contains(value)) {
            items.add(value); // a name the settings do not know (yet): kept, the run reports it
        }
        xsltProcessorChoice.getItems().setAll(items);
        xsltProcessorChoice.setValue(value);
        xsltProcessorChoice.setMaxWidth(Double.MAX_VALUE);
        xsltProcessorChoice.setId("scenario-xslt-processor");
        xsltProcessorChoice.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(String name) {
                return name == null || name.isEmpty() ? I18n.t("ScenarioEditDialog.xsltProcessor.default")
                        : org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorStore.BUILTIN_ID.equals(name)
                        ? I18n.t("XsltProcessorsSettings.builtin") : name;
            }

            @Override
            public String fromString(String text) {
                return text;
            }
        });
        xsltProcessorChoice.setTooltip(new javafx.scene.control.Tooltip(I18n.t("ScenarioEditDialog.xsltProcessor.tooltip")));
        int row = grid.getChildren().stream().mapToInt(n -> {
            Integer r = javafx.scene.layout.GridPane.getRowIndex(n);
            return r == null ? 0 : r;
        }).max().orElse(-1) + 1;
        javafx.scene.control.Label label = new javafx.scene.control.Label(I18n.t("ScenarioEditDialog.xsltProcessor"));
        label.setLabelFor(xsltProcessorChoice);
        grid.add(label, 0, row);
        grid.add(xsltProcessorChoice, 1, row);
    }

    /** @return the processors a PDF scenario can name: the bundled FOP and the configured ones */
    private static List<String> processorNames() {
        List<String> names = new java.util.ArrayList<>();
        names.add(org.fxt.freexmltoolkit.service.fo.FoProcessorStore.BUILTIN_ID);
        try {
            org.fxt.freexmltoolkit.service.fo.FoProcessorStore.settings().list().forEach(p -> names.add(p.name()));
        } catch (RuntimeException e) {
            // no settings (tests): the bundled FOP only
        }
        return names;
    }

    /** @return the tolerance entered (0 when empty), or -1 when it is not a number */
    private double tolerance() {
        String text = toleranceField.getText() == null ? "" : toleranceField.getText().strip().replace(',', '.');
        if (text.isEmpty()) {
            return 0;
        }
        try {
            double value = Double.parseDouble(text);
            return value >= 0 && Double.isFinite(value) ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** @return the problem with the input, or {@code null} */
    String validate() {
        String name = nameField.getText() == null ? "" : nameField.getText().strip();
        String problem = null;
        if (name.isEmpty()) {
            problem = I18n.t("ScenarioEditDialog.enter-name");
        } else if (otherNames.contains(name)) {
            problem = I18n.t("ScenarioEditDialog.name-taken");
        } else if (stylesheetField.getText() == null || stylesheetField.getText().isBlank()) {
            problem = I18n.t("ScenarioEditDialog.choose-stylesheet");
        } else if ("PDF".equals(formatChoice.getValue()) && (outputField.getText() == null || outputField.getText().isBlank())) {
            problem = I18n.t("ScenarioEditDialog.pdf-needs-output");
        } else if (!parametersArea.getText().isBlank() && invalidParameterLine(parametersArea.getText()) != null) {
            problem = I18n.t("ScenarioEditDialog.not-a-parameter", invalidParameterLine(parametersArea.getText()));
        } else if (tolerance() < 0) {
            problem = I18n.t("ScenarioEditDialog.tolerance");
        } else if (XmlStructuralDiff.Options.DEFAULTS.withIgnoreXPaths(
                XmlStructuralDiff.Options.parseXPaths(ignoreArea.getText())).invalidIgnoreXPath() != null) {
            problem = I18n.t("ScenarioEditDialog.not-an-xpath", XmlStructuralDiff.Options.DEFAULTS.withIgnoreXPaths(
                    XmlStructuralDiff.Options.parseXPaths(ignoreArea.getText())).invalidIgnoreXPath());
        }
        boolean show = problem != null && !name.isEmpty();
        error.setText(problem == null ? "" : problem);
        error.setVisible(show);
        error.setManaged(show);
        getDialogPane().lookupButton(ButtonType.OK).setDisable(problem != null);
        return problem;
    }

    /** @return the parameters as {@code name=value} lines */
    static String parametersText(Map<String, String> parameters) {
        StringBuilder text = new StringBuilder();
        if (parameters != null) {
            parameters.forEach((k, v) -> text.append(k).append('=').append(v == null ? "" : v).append('\n'));
        }
        return text.toString().strip();
    }

    /** @return the parameters of {@code name=value} lines (blank lines are skipped) */
    static Map<String, String> parseParameters(String text) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (text == null) {
            return parameters;
        }
        for (String line : text.lines().map(String::strip).filter(l -> !l.isEmpty()).toList()) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                parameters.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
            }
        }
        return parameters;
    }

    private static String invalidParameterLine(String text) {
        return text.lines().map(String::strip).filter(l -> !l.isEmpty()).filter(l -> l.indexOf('=') <= 0)
                .findFirst().orElse(null);
    }

    @FXML
    public void onBrowseStylesheet() {
        browse(stylesheetField, new FileChooser.ExtensionFilter("XSLT, XQuery, XProc",
                "*.xsl", "*.xslt", "*.xq", "*.xquery", "*.xqy", "*.xql", "*.xqm", "*.xpl", "*.xproc"));
    }

    @FXML
    public void onBrowseInput() {
        browse(inputField, new FileChooser.ExtensionFilter("XML", "*.xml", "*.*"));
    }

    @FXML
    public void onBrowseExpected() {
        browse(expectedField, new FileChooser.ExtensionFilter(I18n.t("ScenarioEditDialog.all-files"), "*.*"));
    }

    private void browse(TextField field, FileChooser.ExtensionFilter filter) {
        FileChooser chooser = new FileChooser();
        chooser.setInitialDirectory(project.root().toFile());
        chooser.getExtensionFilters().add(filter);
        File file = FileChooserHelper.showOpenDialog(chooser, getDialogPane().getScene().getWindow());
        if (file != null) {
            field.setText(project.relativize(file.toPath()));
        }
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    // test access
    void fillForTesting(String name, String stylesheet, String input, String parameters, String format, String output) {
        nameField.setText(name);
        stylesheetField.setText(stylesheet);
        inputField.setText(input);
        parametersArea.setText(parameters);
        formatChoice.setValue(format);
        outputField.setText(output);
    }

    void fillCheckForTesting(String expected, String ignore, String tolerance, boolean ignoreOrder, String keys) {
        expectedField.setText(expected);
        ignoreArea.setText(ignore);
        toleranceField.setText(tolerance);
        ignoreOrderBox.setSelected(ignoreOrder);
        keysField.setText(keys);
    }

    String validationForTesting() {
        return validate();
    }

    FxtProject.Scenario scenarioForTesting() {
        return validate() == null ? scenario() : null;
    }
}
