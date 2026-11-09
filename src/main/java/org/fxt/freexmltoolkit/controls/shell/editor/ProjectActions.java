package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuItem;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.project.FxtProject;
import org.fxt.freexmltoolkit.service.project.ProjectService;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/**
 * File → Project: create, open (also recent ones) and close a project ({@link ProjectService}),
 * edit its settings, bind a schema, Schematron or scenario to files of the project, add a catalog,
 * run a scenario for files, and edit the project file.
 */
public final class ProjectActions {

    private ProjectActions() {
    }

    private static FileChooser projectChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("FreeXmlToolkit project", "*." + FxtProject.EXTENSION));
        return chooser;
    }

    /** Asks for a new project file and opens the new project. */
    public static void newProject(Window owner) {
        FileChooser chooser = projectChooser("New Project");
        chooser.setInitialFileName("project." + FxtProject.EXTENSION);
        File file = FileChooserHelper.showSaveDialog(chooser, owner);
        if (file == null) {
            return;
        }
        Path path = file.toPath();
        if (!FxtProject.isProjectFile(path)) {
            path = path.resolveSibling(path.getFileName() + "." + FxtProject.EXTENSION);
        }
        String name = path.getFileName().toString().replaceFirst("\\.[^.]*$", "");
        try {
            ProjectService.getInstance().create(path, name);
        } catch (IOException e) {
            DialogHelper.showError(I18n.t("ProjectActions.new-project"), I18n.t("ProjectActions.the-project-could-not-be"), e.getMessage());
        }
    }

    /** Asks for a project file and opens it. */
    public static void openProject(Window owner) {
        File file = FileChooserHelper.showOpenDialog(projectChooser("Open Project"), owner);
        if (file != null) {
            openProject(file.toPath());
        }
    }

    /** Opens a project file, reporting a broken one. */
    public static boolean openProject(Path file) {
        try {
            ProjectService.getInstance().open(file);
            return true;
        } catch (IOException e) {
            DialogHelper.showError(I18n.t("ProjectActions.open-project"), I18n.t("ProjectActions.the-project-could-not-be-2"), e.getMessage());
            return false;
        }
    }

    /**
     * The items of File → Project → Open Recent: one per recent project file (the open one marked),
     * then "Clear List"; a single disabled item when there are none.
     */
    public static List<MenuItem> recentProjectItems() {
        List<MenuItem> items = new ArrayList<>();
        Path open = ProjectService.getInstance().current().map(FxtProject::file).orElse(null);
        for (Path file : ProjectService.getInstance().recent()) {
            String name = file.getFileName().toString().replaceFirst("\\.[^.]*$", "");
            Path parent = file.getParent();
            MenuItem item = new MenuItem(name + (parent == null ? "" : "  —  " + parent));
            item.setGraphic(org.fxt.freexmltoolkit.controls.theme.SemanticIcon.paint(
                    new org.fxt.freexmltoolkit.controls.icons.IconifyIcon(file.equals(open) ? "bi-briefcase-fill" : "bi-briefcase"),
                    org.fxt.freexmltoolkit.controls.theme.ActionColor.NAVIGATE));
            item.setOnAction(e -> openProject(file));
            item.setId("menu-project-recent-" + items.size());
            items.add(item);
        }
        if (items.isEmpty()) {
            MenuItem none = new MenuItem(I18n.t("ProjectActions.no-recent-projects"),
                    new org.fxt.freexmltoolkit.controls.icons.IconifyIcon("bi-clock-history"));
            none.setId("menu-project-recent-none");
            none.setDisable(true);
            items.add(none);
        } else {
            items.add(new javafx.scene.control.SeparatorMenuItem());
            MenuItem clear = new MenuItem(I18n.t("ProjectActions.clear-recent-projects"));
            clear.setGraphic(org.fxt.freexmltoolkit.controls.theme.SemanticIcon.paint(
                    new org.fxt.freexmltoolkit.controls.icons.IconifyIcon("bi-trash"),
                    org.fxt.freexmltoolkit.controls.theme.ActionColor.DELETE));
            clear.setId("menu-project-recent-clear");
            clear.setOnAction(e -> ProjectService.getInstance().clearRecent());
            items.add(clear);
        }
        return items;
    }

    public static void closeProject() {
        ProjectService.getInstance().close();
    }

    /** Opens the project file itself as a document. */
    public static void editProjectFile(EditorHost editorHost) {
        ProjectService.getInstance().current().ifPresent(p -> editorHost.openFile(p.file()));
    }

    /** File → Project → Project Settings…: edits the open project and saves it on OK. */
    public static void openSettings(EditorHost editorHost, Window owner) {
        Optional<FxtProject> open = ProjectService.getInstance().current();
        if (open.isEmpty()) {
            return;
        }
        FxtProject project = open.get();
        ProjectSettingsDialog dialog = new ProjectSettingsDialog(project);
        dialog.initOwner(owner);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        dialog.applyTo(project);
        save(I18n.t("ProjectSettingsDialog.title"));
        applyToActiveDocument(editorHost);
    }

    /**
     * Binds a schema, Schematron or scenario to files of the project; starts with the active
     * document (its binding, or its path and schema).
     */
    public static void bindSchema(EditorHost editorHost, Window owner) {
        Optional<FxtProject> open = ProjectService.getInstance().current();
        if (open.isEmpty()) {
            return;
        }
        FxtProject project = open.get();
        OpenDocument active = editorHost.getActiveDocument().orElse(null);
        Path activePath = active == null ? null : active.getPath();
        String files = activePath != null && activePath.toAbsolutePath().normalize().startsWith(project.root())
                ? project.relativize(activePath) : "**/*.xml";
        FxtProject.SchemaBinding start = project.bindingWithPattern(files);
        if (start == null) {
            File schema = active == null ? null : editorHost.schemaFileOf(active);
            start = new FxtProject.SchemaBinding(files, schema == null ? null : project.relativize(schema.toPath()));
        }
        bind(editorHost, owner, project, start.copy());
    }

    /** Binds the files chosen in the Explorer: the pattern starts as {@link #patternFor}. */
    public static void bindFiles(EditorHost editorHost, Window owner, List<Path> files) {
        Optional<FxtProject> open = ProjectService.getInstance().current();
        if (open.isEmpty() || files.isEmpty()) {
            return;
        }
        FxtProject project = open.get();
        String pattern = patternFor(project, files);
        FxtProject.SchemaBinding start = project.bindingWithPattern(pattern);
        bind(editorHost, owner, project, start == null ? new FxtProject.SchemaBinding(pattern, null) : start.copy());
    }

    private static void bind(EditorHost editorHost, Window owner, FxtProject project, FxtProject.SchemaBinding start) {
        BindSchemaDialog dialog = new BindSchemaDialog(project, start);
        dialog.initOwner(owner);
        dialog.showAndWait().ifPresent(request -> {
            project.bind(request.toBinding(project));
            if (save(I18n.t("BindSchemaDialog.bind-schema-in-project"))) {
                applyToActiveDocument(editorHost);
            }
        });
    }

    /**
     * The binding pattern for files chosen together: one file → its path; files of one folder with
     * one extension → {@code folder/*.ext}; otherwise {@code **}{@code /*.ext} for a common extension, else the first file.
     */
    static String patternFor(FxtProject project, List<Path> files) {
        if (files.size() == 1) {
            return project.relativize(files.get(0));
        }
        List<String> relative = files.stream().map(project::relativize).toList();
        String extension = extension(relative.get(0));
        boolean sameExtension = !extension.isEmpty() && relative.stream().allMatch(r -> extension(r).equals(extension));
        String folder = folder(relative.get(0));
        boolean sameFolder = relative.stream().allMatch(r -> folder(r).equals(folder));
        if (sameExtension && sameFolder) {
            return (folder.isEmpty() ? "" : folder + "/") + "*." + extension;
        }
        if (sameExtension) {
            return "**/*." + extension;
        }
        return relative.get(0);
    }

    private static String extension(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash ? path.substring(dot + 1) : "";
    }

    private static String folder(String path) {
        int slash = path.lastIndexOf('/');
        return slash > 0 ? path.substring(0, slash) : "";
    }

    /** Adds an XML catalog (OASIS) to the project. */
    public static void addCatalog(Window owner) {
        Optional<FxtProject> open = ProjectService.getInstance().current();
        if (open.isEmpty()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("ProjectActions.add-catalog-to-project"));
        chooser.setInitialDirectory(open.get().root().toFile());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML catalog", "*.xml", "*.cat"));
        File file = FileChooserHelper.showOpenDialog(chooser, owner);
        if (file == null) {
            return;
        }
        open.get().addCatalog(file.toPath());
        save(I18n.t("ProjectActions.add-catalog-to-project"));
    }

    private static boolean save(String title) {
        try {
            ProjectService.getInstance().save();
            return true;
        } catch (IOException e) {
            DialogHelper.showError(title, I18n.t("ProjectActions.the-project-could-not-be-3"), e.getMessage());
            return false;
        }
    }

    /** The active document gets the schema the project now binds to it (other documents when they are opened). */
    static void applyToActiveDocument(EditorHost editorHost) {
        OpenDocument active = editorHost.getActiveDocument().orElse(null);
        Path path = active == null ? null : active.getPath();
        if (path == null) {
            return;
        }
        Path schema = ProjectService.getInstance().assignmentFor(path).schema();
        File bound = editorHost.schemaFileOf(active);
        if (schema != null && (bound == null || !bound.toPath().toAbsolutePath().normalize().equals(schema))) {
            editorHost.setSchemaForActiveDocument(schema.toFile());
        }
    }

    // ---- running scenarios for files -----------------------------------------------------------

    /**
     * Runs a scenario for files of the project (in the background): one file without an output file
     * opens the result in a new tab, a PDF opens in the PDF preview; otherwise a summary lists what
     * was written and what failed.
     */
    public static void runScenario(EditorHost editorHost, FxtProject project, FxtProject.Scenario scenario, List<Path> files) {
        FxtGui.executorService.submit(() -> {
            List<ScenarioRunner.FileResult> results = ScenarioRunner.runAll(project, scenario, files, null, null);
            Platform.runLater(() -> showScenarioResults(editorHost, project, scenario, results));
        });
    }

    static void showScenarioResults(EditorHost editorHost, FxtProject project, FxtProject.Scenario scenario,
                                    List<ScenarioRunner.FileResult> results) {
        if (results.size() == 1 && results.get(0).result().ok()) {
            ScenarioRunner.Result result = results.get(0).result();
            if (scenario.isPdf() && result.written() != null) {
                editorHost.openPdfPreview(result.written().toFile());
                return;
            }
            if (result.written() == null) {
                String name = scenario.name + " — " + results.get(0).file().getFileName();
                editorHost.openGeneratedDocument(result.text(), typeOf(result), name);
                return;
            }
        }
        DialogHelper.showInformation(I18n.t("ProjectActions.run-scenario"),
                I18n.t("ProjectActions.scenario-ran", scenario.name, results.size(),
                        results.stream().filter(r -> !r.result().ok()).count()),
                summary(project, results));
    }

    // ---- regression checks ---------------------------------------------------------------------

    /** One check of {@link #checkScenarios}. */
    record Check(FxtProject.Scenario scenario, Path file, ScenarioCheck.Result result) {
    }

    /** At most this many difference tabs open at once from the summary. */
    static final int MAX_DIFF_TABS = 5;

    /**
     * Checks every scenario of the project that names an expected result, in the background: a scenario
     * that runs for a file ({@code ${file}}) is checked for each file bound to it. A summary lists the
     * outcome; Show Differences opens the expected and actual result of each failed check.
     */
    public static void checkScenarios(EditorHost editorHost, FxtProject project) {
        FxtGui.executorService.submit(() -> {
            List<Check> checks = runChecks(project);
            Platform.runLater(() -> showChecks(editorHost, project, checks));
        });
    }

    /** Runs the checks (on the calling thread). */
    static List<Check> runChecks(FxtProject project) {
        List<Check> checks = new java.util.ArrayList<>();
        for (FxtProject.Scenario scenario : project.scenarios()) {
            if (!scenario.hasExpected()) {
                continue;
            }
            List<Path> files = scenario.takesFile() ? project.filesForScenario(scenario.name, 10_000) : List.of();
            if (scenario.takesFile() && files.isEmpty()) {
                checks.add(new Check(scenario, null, new ScenarioCheck.Result(ScenarioCheck.Status.ERROR, List.of(),
                        null, null, "No file of the project is bound to this scenario.")));
                continue;
            }
            for (ScenarioCheck.FileResult r : ScenarioCheck.checkAll(project, scenario, files, null, null)) {
                checks.add(new Check(scenario, r.file(), r.result()));
            }
        }
        return checks;
    }

    private static void showChecks(EditorHost editorHost, FxtProject project, List<Check> checks) {
        if (checks.isEmpty()) {
            DialogHelper.showInformation(I18n.t("ProjectActions.check-scenarios"),
                    I18n.t("ProjectActions.no-expected-results"), I18n.t("ProjectActions.no-expected-results-hint"));
            return;
        }
        long passed = checks.stream().filter(c -> c.result().passed()).count();
        List<Check> failed = checks.stream()
                .filter(c -> c.result().status() == ScenarioCheck.Status.FAILED && c.result().actual() != null).toList();
        StringBuilder text = new StringBuilder();
        for (Check c : checks) {
            text.append(c.result().passed() ? "✓ " : "✗ ").append(c.scenario().name);
            if (c.file() != null) {
                text.append(" (").append(project.relativize(c.file())).append(')');
            }
            if (!c.result().passed()) {
                text.append(": ").append(c.result().summary());
            }
            text.append('\n');
        }
        javafx.scene.control.Alert alert = DialogHelper.createAlert(passed == checks.size()
                ? javafx.scene.control.Alert.AlertType.INFORMATION : javafx.scene.control.Alert.AlertType.WARNING);
        alert.setTitle(I18n.t("ProjectActions.check-scenarios"));
        alert.setHeaderText(I18n.t("ProjectActions.checks-passed", passed, checks.size()));
        alert.setContentText(text.toString().strip());
        javafx.scene.control.ButtonType show = new javafx.scene.control.ButtonType(
                I18n.t("ProjectActions.show-differences"), javafx.scene.control.ButtonBar.ButtonData.OTHER);
        if (!failed.isEmpty()) {
            alert.getButtonTypes().setAll(show, javafx.scene.control.ButtonType.CLOSE);
        } else {
            alert.getButtonTypes().setAll(javafx.scene.control.ButtonType.OK);
        }
        alert.showAndWait().filter(show::equals).ifPresent(b -> {
            for (Check c : failed.stream().limit(MAX_DIFF_TABS).toList()) {
                String ext = extension(c.result().expected().getFileName().toString());
                String name = c.scenario().name + "-actual" + (ext.isEmpty() ? ".xml" : "." + ext);
                editorHost.openScenarioDiff(c.result().expected(), c.result().actual(), name,
                        FxtProject.CompareSettings.toOptions(c.scenario().compare));
            }
        });
    }

    /** @return one line per file: what was written, or why it failed */
    static String summary(FxtProject project, List<ScenarioRunner.FileResult> results) {
        StringBuilder text = new StringBuilder();
        for (ScenarioRunner.FileResult r : results) {
            text.append(project.relativize(r.file())).append(": ");
            if (!r.result().ok()) {
                text.append("✗ ").append(r.result().error());
            } else if (r.result().written() != null) {
                text.append("→ ").append(project.relativize(r.result().written()));
            } else {
                text.append("✓");
            }
            text.append('\n');
        }
        return text.toString().strip();
    }

    private static EditorFileType typeOf(ScenarioRunner.Result result) {
        if (result.format() == null) {
            return EditorFileType.XML;
        }
        return switch (result.format()) {
            case HTML, XHTML -> EditorFileType.fromFileName("result.html");
            case JSON -> EditorFileType.JSON;
            case TEXT -> EditorFileType.OTHER;
            default -> EditorFileType.XML;
        };
    }
}
