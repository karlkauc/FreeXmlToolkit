package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
import org.fxt.freexmltoolkit.service.FopEvent;
import org.fxt.freexmltoolkit.service.fo.ArgumentTemplate;
import org.fxt.freexmltoolkit.service.fo.ExternalFoRenderer;
import org.fxt.freexmltoolkit.service.fo.FoProcessorConfig;
import org.fxt.freexmltoolkit.service.fo.FoProcessorException;
import org.fxt.freexmltoolkit.service.fo.FoProcessorPreset;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.util.ExternalCommands;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/**
 * Adds or edits an external XSL-FO processor (Settings → XSL-FO Processors): preset, name,
 * program, argument template, working folder and time limit, with a test run that renders a
 * one-page document. The result is a new {@link FoProcessorConfig} with the same id.
 */
public class FoProcessorDialog extends Dialog<FoProcessorConfig> {

    /** The page the Test button renders. */
    static final String TEST_FO = "/fo/processor-test.fo";

    @FXML private ComboBox<FoProcessorPreset> presetChoice;
    @FXML private TextField nameField;
    @FXML private TextField executableField;
    @FXML private TextField argumentsField;
    @FXML private Tooltip argumentsTooltip;
    @FXML private Label placeholderHelp;
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
    public FoProcessorDialog(FoProcessorConfig processor, Set<String> otherNames) {
        DialogHelper.prepare(this);
        FoProcessorConfig start = processor != null ? processor : FoProcessorConfig.fromPreset(FoProcessorPreset.APACHE_FOP);
        this.id = start.id();
        this.otherNames = otherNames.stream().map(n -> n.toLowerCase(java.util.Locale.ROOT)).collect(Collectors.toSet());
        setTitle(I18n.t(processor == null ? "FoProcessorDialog.title.add" : "FoProcessorDialog.title.edit"));
        setHeaderText(I18n.t("FoProcessorDialog.header"));
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/FoProcessorDialog.fxml"));

        presetChoice.getItems().setAll(FoProcessorPreset.values());
        presetChoice.setValue(start.preset());
        nameField.setText(start.name());
        nameFromPreset = start.name().equals(start.preset().displayName());
        executableField.setText(start.executable());
        argumentsField.setText(start.arguments());
        workingDirField.setText(start.workingDir());
        timeoutSpinner.getValueFactory().setValue(start.timeoutSeconds());
        String help = I18n.t("FoProcessorDialog.placeholders",
                ArgumentTemplate.NAMES.stream().map(n -> "${" + n + "}").collect(Collectors.joining(" ")));
        placeholderHelp.setText(help);
        argumentsTooltip.setText(I18n.t("FoProcessorDialog.arguments.tooltip"));

        presetChoice.valueProperty().addListener((obs, old, preset) -> applyPreset(old, preset));
        nameField.textProperty().addListener((obs, o, n) -> {
            if (!n.equals(presetChoice.getValue() == null ? "" : presetChoice.getValue().displayName())) {
                nameFromPreset = false;
            }
            validate();
        });
        argumentsField.textProperty().addListener((obs, o, n) -> validate());
        executableField.textProperty().addListener((obs, o, n) -> validate());
        workingDirField.textProperty().addListener((obs, o, n) -> validate());
        validate();
        setResultConverter(button -> button == ButtonType.OK && validate() == null ? config() : null);
    }

    /** A new preset replaces the arguments it filled in and, while unchanged, the name. */
    private void applyPreset(FoProcessorPreset old, FoProcessorPreset preset) {
        if (preset == null) {
            return;
        }
        String arguments = argumentsField.getText() == null ? "" : argumentsField.getText().strip();
        if (arguments.isEmpty() || (old != null && arguments.equals(old.arguments()))) {
            argumentsField.setText(preset.arguments());
        }
        if (nameFromPreset || nameField.getText() == null || nameField.getText().isBlank()) {
            nameField.setText(preset.displayName());
            nameFromPreset = true;
        }
        validate();
    }

    /** @return the processor the fields describe */
    FoProcessorConfig config() {
        return new FoProcessorConfig(id, nameField.getText(), presetChoice.getValue(), executableField.getText(),
                argumentsField.getText(), workingDirField.getText(), timeoutValue());
    }

    private int timeoutValue() {
        Integer value = timeoutSpinner.getValue();
        return value == null ? FoProcessorConfig.DEFAULT_TIMEOUT_SECONDS : value;
    }

    /** @return the problem with the input, or {@code null} */
    String validate() {
        String name = nameField.getText() == null ? "" : nameField.getText().strip();
        String working = workingDirField.getText() == null ? "" : workingDirField.getText().strip();
        String problem;
        if (name.isEmpty()) {
            problem = I18n.t("FoProcessorDialog.error.name");
        } else if (otherNames.contains(name.toLowerCase(java.util.Locale.ROOT))
                || "builtin".equalsIgnoreCase(name)) {
            problem = I18n.t("FoProcessorDialog.error.duplicate");
        } else {
            problem = ArgumentTemplate.validate(argumentsField.getText());
            if (problem == null && !working.isEmpty() && !Files.isDirectory(Path.of(working))) {
                problem = I18n.t("FoProcessorDialog.error.workingDir");
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
        chooser.setTitle(I18n.t("FoProcessorDialog.chooseProgram"));
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
        FoProcessorPreset preset = presetChoice.getValue();
        List<String> names = preset == null ? List.of() : preset.programNames();
        var found = names.isEmpty() ? java.util.Optional.<Path>empty()
                : ExternalCommands.findOnPath(names.toArray(String[]::new));
        if (found.isPresent()) {
            executableField.setText(found.get().toString());
            testStatus.setText(I18n.t("FoProcessorDialog.found", found.get()));
        } else {
            testStatus.setText(names.isEmpty() ? I18n.t("FoProcessorDialog.notFoundCustom")
                    : I18n.t("FoProcessorDialog.notFound", String.join(", ", names)));
        }
    }

    @FXML
    public void onTest() {
        if (validate() != null) {
            return;
        }
        FoProcessorConfig config = config();
        testButton.setDisable(true);
        testStatus.setText(I18n.t("FoProcessorDialog.testing", config.name()));
        testOutput.setVisible(false);
        testOutput.setManaged(false);
        FxtGui.executorService.submit(() -> {
            TestOutcome outcome = runTest(config);
            Platform.runLater(() -> showTest(outcome));
        });
    }

    /** The outcome of a test run: success, a short status and the program's output. */
    record TestOutcome(boolean ok, String status, String output) {
    }

    /** Renders the test page with the processor into a temporary folder (off the FX thread). */
    static TestOutcome runTest(FoProcessorConfig config) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("fxt-fo-test");
            String fo;
            try (InputStream in = FoProcessorDialog.class.getResourceAsStream(TEST_FO)) {
                fo = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            Path pdf = dir.resolve("test.pdf");
            Path foFile = dir.resolve("test.fo");
            long t0 = System.nanoTime();
            List<FopEvent> events = ExternalFoRenderer.render(config, new ExternalFoRenderer.Request(fo, foFile, pdf,
                    null, null, "Test", "", "", false));
            long ms = (System.nanoTime() - t0) / 1_000_000;
            return new TestOutcome(true, I18n.t("FoProcessorDialog.testOk", Files.size(pdf), ms), text(events));
        } catch (FoProcessorException e) {
            return new TestOutcome(false, I18n.t("FoProcessorDialog.testFailed"), e.getMessage()
                    + (e.events().isEmpty() ? "" : "\n\n" + text(e.events())));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TestOutcome(false, I18n.t("FoProcessorDialog.testFailed"), "Interrupted.");
        } catch (IOException | RuntimeException e) {
            return new TestOutcome(false, I18n.t("FoProcessorDialog.testFailed"), String.valueOf(e.getMessage()));
        } finally {
            deleteQuietly(dir);
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

    private static String text(List<FopEvent> events) {
        return events.stream().map(e -> "[" + e.severity() + "] " + e.message()).collect(Collectors.joining("\n"));
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
