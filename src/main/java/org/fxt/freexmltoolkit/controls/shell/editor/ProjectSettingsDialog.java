package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.shell.FxmlColumns;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.project.FxtProject;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/**
 * File → Project → Project Settings…: edits the open project without touching its JSON — name and
 * folders, the bindings (files → schema, Schematron, scenario; order matters), the scenarios and the
 * catalogs. The dialog works on copies; {@link #applyTo(FxtProject)} writes them to the project
 * (the caller saves it). Renaming a scenario renames it in the bindings too.
 */
public class ProjectSettingsDialog extends Dialog<ButtonType> {

    @FXML private TabPane tabs;
    @FXML private TextField nameField;
    @FXML private ListView<String> folderList;
    @FXML private Button folderRemove;
    @FXML private Button folderUp;
    @FXML private Button folderDown;
    @FXML private Label fileLabel;
    @FXML private TableView<FxtProject.SchemaBinding> bindingTable;
    @FXML private TableColumn<FxtProject.SchemaBinding, String> bindingFilesCol;
    @FXML private TableColumn<FxtProject.SchemaBinding, String> bindingSchemaCol;
    @FXML private TableColumn<FxtProject.SchemaBinding, String> bindingSchematronCol;
    @FXML private TableColumn<FxtProject.SchemaBinding, String> bindingScenarioCol;
    @FXML private Button bindingEdit;
    @FXML private Button bindingRemove;
    @FXML private Button bindingUp;
    @FXML private Button bindingDown;
    @FXML private TableView<FxtProject.Scenario> scenarioTable;
    @FXML private TableColumn<FxtProject.Scenario, String> scenarioNameCol;
    @FXML private TableColumn<FxtProject.Scenario, String> scenarioStylesheetCol;
    @FXML private TableColumn<FxtProject.Scenario, String> scenarioInputCol;
    @FXML private TableColumn<FxtProject.Scenario, String> scenarioFormatCol;
    @FXML private TableColumn<FxtProject.Scenario, String> scenarioOutputCol;
    @FXML private Button scenarioEdit;
    @FXML private Button scenarioDuplicate;
    @FXML private Button scenarioRemove;
    @FXML private Button scenarioUp;
    @FXML private Button scenarioDown;
    @FXML private ListView<String> catalogList;
    @FXML private Button catalogRemove;

    private final FxtProject project;
    private final ObservableList<String> folders = FXCollections.observableArrayList();
    private final ObservableList<FxtProject.SchemaBinding> bindings = FXCollections.observableArrayList();
    private final ObservableList<FxtProject.Scenario> scenarios = FXCollections.observableArrayList();
    private final ObservableList<String> catalogs = FXCollections.observableArrayList();

    /** Test hooks: the dialogs a button opens (replaced by tests). */
    java.util.function.Function<FxtProject.SchemaBinding, java.util.Optional<FxtProject.SchemaBinding>> bindingEditor;
    java.util.function.BiFunction<FxtProject.Scenario, Set<String>, java.util.Optional<FxtProject.Scenario>> scenarioEditor;

    public ProjectSettingsDialog(FxtProject project) {
        this.project = project;
        setTitle(I18n.t("ProjectSettingsDialog.title"));
        setHeaderText(I18n.t("ProjectSettingsDialog.header", project.name()));
        setResizable(true);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/ProjectSettingsDialog.fxml"));

        nameField.setText(project.name());
        nameField.setPromptText(project.file().getFileName().toString());
        fileLabel.setText(I18n.t("ProjectSettingsDialog.file", project.file()));
        folders.setAll(project.folderEntries());
        project.schemaBindings().forEach(b -> bindings.add(b.copy()));
        project.scenarios().forEach(s -> scenarios.add(s.copy()));
        catalogs.setAll(project.catalogEntries());

        folderList.setItems(folders);
        catalogList.setItems(catalogs);
        bindingTable.setItems(bindings);
        scenarioTable.setItems(scenarios);
        FxmlColumns.text(bindingFilesCol, b -> b.files);
        FxmlColumns.text(bindingSchemaCol, b -> b.schema);
        FxmlColumns.text(bindingSchematronCol, b -> b.schematron);
        FxmlColumns.text(bindingScenarioCol, b -> b.scenario);
        FxmlColumns.text(scenarioNameCol, s -> s.name);
        FxmlColumns.text(scenarioStylesheetCol, s -> s.stylesheet);
        FxmlColumns.text(scenarioInputCol, s -> s.input);
        FxmlColumns.text(scenarioFormatCol, s -> s.outputFormat);
        FxmlColumns.text(scenarioOutputCol, s -> s.output);

        bindingEditor = binding -> {
            BindSchemaDialog dialog = new BindSchemaDialog(project, binding, scenarioNames());
            dialog.initOwner(getDialogPane().getScene().getWindow());
            return dialog.showAndWait().map(request -> request.toBinding(project));
        };
        scenarioEditor = (scenario, others) -> {
            ScenarioEditDialog dialog = new ScenarioEditDialog(project, scenario, others);
            dialog.initOwner(getDialogPane().getScene().getWindow());
            return dialog.showAndWait();
        };

        folderList.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> updateButtons());
        catalogList.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> updateButtons());
        bindingTable.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> updateButtons());
        scenarioTable.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> updateButtons());
        updateButtons();
    }

    private void updateButtons() {
        int folder = folderList.getSelectionModel().getSelectedIndex();
        folderRemove.setDisable(folder < 0 || folders.size() <= 1);
        folderUp.setDisable(folder <= 0);
        folderDown.setDisable(folder < 0 || folder >= folders.size() - 1);
        int binding = bindingTable.getSelectionModel().getSelectedIndex();
        bindingEdit.setDisable(binding < 0);
        bindingRemove.setDisable(binding < 0);
        bindingUp.setDisable(binding <= 0);
        bindingDown.setDisable(binding < 0 || binding >= bindings.size() - 1);
        int scenario = scenarioTable.getSelectionModel().getSelectedIndex();
        scenarioEdit.setDisable(scenario < 0);
        scenarioDuplicate.setDisable(scenario < 0);
        scenarioRemove.setDisable(scenario < 0);
        scenarioUp.setDisable(scenario <= 0);
        scenarioDown.setDisable(scenario < 0 || scenario >= scenarios.size() - 1);
        catalogRemove.setDisable(catalogList.getSelectionModel().getSelectedIndex() < 0);
    }

    /** Writes the edited settings to the project (not saved). */
    public void applyTo(FxtProject target) {
        String name = nameField.getText() == null ? "" : nameField.getText().strip();
        target.setName(name.isEmpty() ? null : name);
        target.setFolders(List.copyOf(folders));
        target.setBindings(List.copyOf(bindings));
        target.setScenarios(List.copyOf(scenarios));
        target.setCatalogs(List.copyOf(catalogs));
    }

    private List<String> scenarioNames() {
        return scenarios.stream().map(s -> s.name).toList();
    }

    // ---- folders -----------------------------------------------------------------------------

    @FXML
    public void onAddFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setInitialDirectory(project.root().toFile());
        File dir = FileChooserHelper.showDialog(chooser, getDialogPane().getScene().getWindow());
        if (dir != null) {
            addFolder(project.relativize(dir.toPath()));
        }
    }

    void addFolder(String folder) {
        if (!folders.contains(folder)) {
            folders.add(folder);
        }
        updateButtons();
    }

    @FXML
    public void onRemoveFolder() {
        remove(folderList.getSelectionModel().getSelectedIndex(), folders);
    }

    @FXML
    public void onFolderUp() {
        move(folderList.getSelectionModel(), folders, -1);
    }

    @FXML
    public void onFolderDown() {
        move(folderList.getSelectionModel(), folders, 1);
    }

    // ---- bindings ----------------------------------------------------------------------------

    @FXML
    public void onAddBinding() {
        bindingEditor.apply(new FxtProject.SchemaBinding("**/*.xml", null)).ifPresent(binding -> {
            int same = indexOfPattern(binding.files, -1);
            if (same >= 0) {
                bindings.set(same, binding);
            } else {
                bindings.add(binding);
            }
            bindingTable.getSelectionModel().select(binding);
            updateButtons();
        });
    }

    @FXML
    public void onEditBinding() {
        int index = bindingTable.getSelectionModel().getSelectedIndex();
        if (index < 0) {
            return;
        }
        bindingEditor.apply(bindings.get(index).copy()).ifPresent(binding -> {
            int same = indexOfPattern(binding.files, index);
            if (same >= 0) {
                bindings.remove(same); // the edited one takes the pattern
            }
            int at = same >= 0 && same < index ? index - 1 : index;
            bindings.set(at, binding);
            bindingTable.getSelectionModel().select(at);
            updateButtons();
        });
    }

    private int indexOfPattern(String files, int except) {
        for (int i = 0; i < bindings.size(); i++) {
            if (i != except && files.equals(bindings.get(i).files)) {
                return i;
            }
        }
        return -1;
    }

    /** A double click edits the binding. */
    @FXML
    public void onBindingClicked(MouseEvent event) {
        if (event.getClickCount() == 2) {
            onEditBinding();
        }
    }

    @FXML
    public void onRemoveBinding() {
        remove(bindingTable.getSelectionModel().getSelectedIndex(), bindings);
    }

    @FXML
    public void onBindingUp() {
        move(bindingTable.getSelectionModel(), bindings, -1);
    }

    @FXML
    public void onBindingDown() {
        move(bindingTable.getSelectionModel(), bindings, 1);
    }

    // ---- scenarios ---------------------------------------------------------------------------

    @FXML
    public void onAddScenario() {
        FxtProject.Scenario fresh = new FxtProject.Scenario(null, null, FxtProject.VAR_FILE, null, null, null);
        scenarioEditor.apply(fresh, Set.copyOf(scenarioNames())).ifPresent(scenario -> {
            scenarios.add(scenario);
            scenarioTable.getSelectionModel().select(scenario);
            updateButtons();
        });
    }

    @FXML
    public void onEditScenario() {
        int index = scenarioTable.getSelectionModel().getSelectedIndex();
        if (index < 0) {
            return;
        }
        FxtProject.Scenario old = scenarios.get(index);
        Set<String> others = new HashSet<>(scenarioNames());
        others.remove(old.name);
        scenarioEditor.apply(old.copy(), others).ifPresent(scenario -> {
            scenarios.set(index, scenario);
            if (old.name != null && !old.name.equals(scenario.name)) {
                renameInBindings(old.name, scenario.name);
            }
            scenarioTable.getSelectionModel().select(index);
            updateButtons();
        });
    }

    /** The bindings follow a renamed scenario. */
    void renameInBindings(String oldName, String newName) {
        for (int i = 0; i < bindings.size(); i++) {
            FxtProject.SchemaBinding binding = bindings.get(i);
            if (oldName.equals(binding.scenario)) {
                FxtProject.SchemaBinding renamed = binding.copy();
                renamed.scenario = newName;
                bindings.set(i, renamed);
            }
        }
    }

    /** A double click edits the scenario. */
    @FXML
    public void onScenarioClicked(MouseEvent event) {
        if (event.getClickCount() == 2) {
            onEditScenario();
        }
    }

    @FXML
    public void onDuplicateScenario() {
        int index = scenarioTable.getSelectionModel().getSelectedIndex();
        if (index < 0) {
            return;
        }
        FxtProject.Scenario copy = scenarios.get(index).copy();
        String base = copy.name + " " + I18n.t("ProjectSettingsDialog.copy-suffix");
        String name = base;
        Set<String> names = Set.copyOf(scenarioNames());
        for (int n = 2; names.contains(name); n++) {
            name = base + " " + n;
        }
        copy.name = name;
        scenarios.add(index + 1, copy);
        scenarioTable.getSelectionModel().select(index + 1);
        updateButtons();
    }

    @FXML
    public void onRemoveScenario() {
        int index = scenarioTable.getSelectionModel().getSelectedIndex();
        if (index < 0) {
            return;
        }
        String name = scenarios.get(index).name;
        remove(index, scenarios);
        // bindings that only named this scenario go, the others forget it
        for (int i = bindings.size() - 1; i >= 0; i--) {
            FxtProject.SchemaBinding binding = bindings.get(i);
            if (name != null && name.equals(binding.scenario)) {
                FxtProject.SchemaBinding without = binding.copy();
                without.scenario = null;
                if (without.isEmpty()) {
                    bindings.remove(i);
                } else {
                    bindings.set(i, without);
                }
            }
        }
    }

    @FXML
    public void onScenarioUp() {
        move(scenarioTable.getSelectionModel(), scenarios, -1);
    }

    @FXML
    public void onScenarioDown() {
        move(scenarioTable.getSelectionModel(), scenarios, 1);
    }

    // ---- catalogs ----------------------------------------------------------------------------

    @FXML
    public void onAddCatalog() {
        FileChooser chooser = new FileChooser();
        chooser.setInitialDirectory(project.root().toFile());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML catalog", "*.xml", "*.cat"));
        File file = FileChooserHelper.showOpenDialog(chooser, getDialogPane().getScene().getWindow());
        if (file != null) {
            String relative = project.relativize(file.toPath());
            if (!catalogs.contains(relative)) {
                catalogs.add(relative);
            }
        }
    }

    @FXML
    public void onRemoveCatalog() {
        remove(catalogList.getSelectionModel().getSelectedIndex(), catalogs);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private <T> void remove(int index, ObservableList<T> list) {
        if (index >= 0 && index < list.size()) {
            list.remove(index);
        }
        updateButtons();
    }

    private <T> void move(javafx.scene.control.SelectionModel<T> selection, ObservableList<T> list, int delta) {
        int index = selection.getSelectedIndex();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= list.size()) {
            return;
        }
        T item = list.remove(index);
        list.add(target, item);
        selection.select(target);
        updateButtons();
    }

    /** Selects a tab by its index (tests, "Bindings" from the Explorer). */
    void selectTab(int index) {
        tabs.getSelectionModel().select(index);
    }

    // test access
    ObservableList<FxtProject.SchemaBinding> bindings() {
        return bindings;
    }

    ObservableList<FxtProject.Scenario> scenarioRows() {
        return scenarios;
    }

    ObservableList<String> folderRows() {
        return folders;
    }

    TableView<FxtProject.SchemaBinding> bindingTable() {
        return bindingTable;
    }

    TableView<FxtProject.Scenario> scenarioTable() {
        return scenarioTable;
    }
}
