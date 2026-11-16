package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.fxt.freexmltoolkit.service.xsltproc.ExternalXsltRunner;
import org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorConfig;
import org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorPreset;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.util.ExternalCommands;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/**
 * Adds or edits an external XSLT processor (Settings → XSLT Processors): preset, name, program, argument
 * template, parameter template, working folder and time limit, with a test run that transforms a small
 * document with one parameter. The result is a new {@link XsltProcessorConfig} with the same id.
 */
public class XsltProcessorDialog extends Dialog<XsltProcessorConfig> {

    /** The stylesheet and input the Test button transforms. */
    static final String TEST_XSL = "/xslt/processor-test.xsl";
    static final String TEST_XML = "/xslt/processor-test.xml";
    /** What the test stylesheet writes with the test parameter. */
    static final String TEST_EXPECTED = "processor test: 3 items, parameter ok";

    @FXML private ComboBox<XsltProcessorPreset> presetChoice;
    @FXML private TextField nameField;
    @FXML private TextField executableField;
    @FXML private TextField argumentsField;
    @FXML private Tooltip argumentsTooltip;
    @FXML private Label placeholderHelp;
    @FXML private TextField parametersField;
    @FXML private TextField workingDirField;
    @FXML private Spinner<Integer> timeoutSpinner;
    @FXML private Button testButton;
    @FXML private Label testStatus;
    @FXML private TextArea testOutput;
    @FXML private Label error;

    private final String id;
    private final Set<String> otherNames;
    /** Whether the name still follows the preset (it is replaced when the preset changes). */
    private boolean nameFromPreset;

    /**
     * @param processor  the processor to edit, or {@code null} for a new one
     * @param otherNames names of the other processors (a name must be unique, ignoring case)
     */
    public XsltProcessorDialog(XsltProcessorConfig processor, Set<String> otherNames) {
        DialogHelper.prepare(this);
        XsltProcessorConfig start = processor != null ? processor : XsltProcessorConfig.fromPreset(XsltProcessorPreset.SAXON);
        this.id = start.id();
        this.otherNames = otherNames.stream().map(n -> n.toLowerCase(java.util.Locale.ROOT)).collect(Collectors.toSet());
        setTitle(I18n.t(processor == null ? "XsltProcessorDialog.title.add" : "XsltProcessorDialog.title.edit"));
        setHeaderText(I18n.t("XsltProcessorDialog.header"));
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/XsltProcessorDialog.fxml"));
        presetChoice.getItems().setAll(XsltProcessorPreset.values());
        presetChoice.setValue(start.preset());
        nameField.setText(start.name());
        nameFromPreset = start.name().equals(start.preset().displayName());
        executableField.setText(start.executable());
        argumentsField.setText(start.arguments());
        parametersField.setText(start.parameterTemplate());
        workingDirField.setText(start.workingDir());
        timeoutSpinner.getValueFactory().setValue(start.timeoutSeconds());
        placeholderHelp.setText(I18n.t("XsltProcessorDialog.placeholders",
                XsltProcessorConfig.TEMPLATE.names().stream().map(n -> "${" + n + "}").collect(Collectors.joining(" "))));
        argumentsTooltip.setText(I18n.t("XsltProcessorDialog.arguments.tooltip"));
        presetChoice.valueProperty().addListener((obs, old, preset) -> applyPreset(old, preset));
        nameField.textProperty().addListener((obs, o, n) -> {
            if (!n.equals(presetChoice.getValue() == null ? "" : presetChoice.getValue().displayName())) {
                nameFromPreset = false;
            }
            validate();
        });
        argumentsField.textProperty().addListener((obs, o, n) -> validate());
        parametersField.textProperty().addListener((obs, o, n) -> validate());
        executableField.textProperty().addListener((obs, o, n) -> validate());
        workingDirField.textProperty().addListener((obs, o, n) -> validate());
        validate();
        setResultConverter(button -> button == ButtonType.OK && validate() == null ? config() : null);
    }

    /** A new preset replaces the templates it filled in and, while unchanged, the name. */
    private void applyPreset(XsltProcessorPreset old, XsltProcessorPreset preset) {
        if (preset == null) {
            return;
        }
        String arguments = argumentsField.getText() == null ? "" : argumentsField.getText().strip();
        if (arguments.isEmpty() || (old != null && arguments.equals(old.arguments()))) {
            argumentsField.setText(preset.arguments());
        }
        String parameters = parametersField.getText() == null ? "" : parametersField.getText().strip();
        if (parameters.isEmpty() || (old != null && parameters.equals(old.parameters()))) {
            parametersField.setText(preset.parameters());
        }
        if (nameFromPreset || nameField.getText() == null || nameField.getText().isBlank()) {
            nameField.setText(preset.displayName());
            nameFromPreset = true;
        }
        validate();
    }

    /** @return the processor the fields describe */
    XsltProcessorConfig config() {
        return new XsltProcessorConfig(id, nameField.getText(), presetChoice.getValue(), executableField.getText(),
                argumentsField.getText(), parametersField.getText() == null ? "" : parametersField.getText(),
                workingDirField.getText(), timeoutValue());
    }

    private int timeoutValue() {
        Integer value = timeoutSpinner.getValue();
        return value == null ? XsltProcessorConfig.DEFAULT_TIMEOUT_SECONDS : value;
    }

    /** @return the problem with the input, or {@code null} */
    String validate() {
        String name = nameField.getText() == null ? "" : nameField.getText().strip();
        String working = workingDirField.getText() == null ? "" : workingDirField.getText().strip();
        String problem;
        if (name.isEmpty()) {
            problem = I18n.t("XsltProcessorDialog.error.name");
        } else if (otherNames.contains(name.toLowerCase(java.util.Locale.ROOT)) || "builtin".equalsIgnoreCase(name)) {
            problem = I18n.t("XsltProcessorDialog.error.duplicate");
        } else {
            problem = config().validate();
            if (problem == null && !working.isEmpty() && !Files.isDirectory(Path.of(working))) {
                problem = I18n.t("XsltProcessorDialog.error.workingDir");
            }
        }
        boolean show = problem != null;
        error.setText(problem == null ? "" : problem);
        error.setVisible(show);
        error.setManaged(show);
        getDialogPane().lookupButton(ButtonType.OK).setDisable(problem != null);
        testButton.setDisable(problem != null);
        return problem;
    }

    @FXML
    public void onBrowseExecutable() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("XsltProcessorDialog.chooseProgram"));
        File current = existingParent(executableField.getText());
        if (current != null) {
            chooser.setInitialDirectory(current);
        }
        File file = FileChooserHelper.showOpenDialog(chooser, getDialogPane().getScene().getWindow());
        if (file != null) {
            executableField.setText(file.getAbsolutePath());
        }
    }

    @FXML
    public void onBrowseWorkingDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        File current = workingDirField.getText() == null || workingDirField.getText().isBlank() ? null
                : new File(workingDirField.getText().strip());
        if (current != null && current.isDirectory()) {
            chooser.setInitialDirectory(current);
        }
        File dir = FileChooserHelper.showDialog(chooser, getDialogPane().getScene().getWindow());
        if (dir != null) {
            workingDirField.setText(dir.getAbsolutePath());
        }
    }

    @FXML
    public void onFindOnPath() {
        XsltProcessorPreset preset = presetChoice.getValue();
        List<String> names = preset == null ? List.of() : preset.programNames();
        var found = names.isEmpty() ? java.util.Optional.<Path>empty()
                : ExternalCommands.findOnPath(names.toArray(String[]::new));
        if (found.isPresent()) {
            executableField.setText(found.get().toString());
            testStatus.setText(I18n.t("XsltProcessorDialog.found", found.get()));
        } else {
            testStatus.setText(names.isEmpty() ? I18n.t("XsltProcessorDialog.notFoundCustom")
                    : I18n.t("XsltProcessorDialog.notFound", String.join(", ", names)));
        }
    }

    @FXML
    public void onTest() {
        if (validate() != null) {
            return;
        }
        XsltProcessorConfig config = config();
        testButton.setDisable(true);
        testStatus.setText(I18n.t("XsltProcessorDialog.testing", config.name()));
        testOutput.setVisible(false);
        testOutput.setManaged(false);
        FxtGui.executorService.submit(() -> {
            TestOutcome outcome = runTest(config);
            Platform.runLater(() -> showTest(outcome));
        });
    }

    /** The outcome of a test run: success, a short status and what to show below it. */
    record TestOutcome(boolean ok, String status, String output) {
    }

    /** Transforms the test document with the processor in a temporary folder (off the FX thread). */
    static TestOutcome runTest(XsltProcessorConfig config) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("fxt-xslt-test");
            Path xsl = Files.writeString(dir.resolve("test.xsl"), resource(TEST_XSL), StandardCharsets.UTF_8);
            Path xml = Files.writeString(dir.resolve("test.xml"), resource(TEST_XML), StandardCharsets.UTF_8);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("check", "ok");
            long t0 = System.nanoTime();
            XsltTransformationResult result = ExternalXsltRunner.transform(config,
                    new ExternalXsltRunner.Request(null, xml, null, xsl, params, null));
            long ms = (System.nanoTime() - t0) / 1_000_000;
            String messages = result.getMessages() == null ? "" : result.getMessages().stream()
                    .map(m -> "[" + m.getLevel() + "] " + m.getMessage()).collect(Collectors.joining("\n"));
            if (!result.isSuccess()) {
                return new TestOutcome(false, I18n.t("XsltProcessorDialog.testFailed"),
                        result.getErrorMessage() + (messages.isEmpty() ? "" : "\n\n" + messages));
            }
            String output = result.getOutputContent().strip();
            boolean ok = output.contains(TEST_EXPECTED);
            String status = ok ? I18n.t("XsltProcessorDialog.testOk", ms)
                    : output.contains("parameter missing") ? I18n.t("XsltProcessorDialog.testNoParameter", ms)
                    : I18n.t("XsltProcessorDialog.testUnexpected");
            return new TestOutcome(ok, status, output + (messages.isEmpty() ? "" : "\n\n" + messages));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TestOutcome(false, I18n.t("XsltProcessorDialog.testFailed"), "Interrupted.");
        } catch (IOException | RuntimeException e) {
            return new TestOutcome(false, I18n.t("XsltProcessorDialog.testFailed"), String.valueOf(e.getMessage()));
        } finally {
            deleteQuietly(dir);
        }
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = XsltProcessorDialog.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException(name + " is missing");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void showTest(TestOutcome outcome) {
        testButton.setDisable(validate() != null);
        testStatus.setText(outcome.status());
        testOutput.setText(outcome.output());
        boolean show = outcome.output() != null && !outcome.output().isBlank();
        testOutput.setVisible(show);
        testOutput.setManaged(show);
        getDialogPane().getScene().getWindow().sizeToScene();
    }

    private static File existingParent(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        File parent = new File(path.strip()).getParentFile();
        return parent != null && parent.isDirectory() ? parent : null;
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var files = Files.walk(dir)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // a temp folder left behind is harmless
        }
    }
}
