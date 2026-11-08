package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.service.project.FxtProject;
import org.fxt.freexmltoolkit.service.project.ProjectService;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/**
 * File → Project: create, open and close a project ({@link ProjectService}), bind a schema to the
 * project's XML files, add a catalog, and edit the project file.
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
            DialogHelper.showError("New Project", "The project could not be created.", e.getMessage());
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
            DialogHelper.showError("Open Project", "The project could not be opened.", e.getMessage());
            return false;
        }
    }

    public static void closeProject() {
        ProjectService.getInstance().close();
    }

    /** Opens the project file itself as a document. */
    public static void editProjectFile(EditorHost editorHost) {
        ProjectService.getInstance().current().ifPresent(p -> editorHost.openFile(p.file()));
    }

    /**
     * Binds a schema to XML files of the project; starts with the active document and its schema.
     * The active document is bound right away when the pattern matches it.
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
        File schema = active == null ? null : editorHost.schemaFileOf(active);
        BindSchemaDialog dialog = new BindSchemaDialog(project, files, schema == null ? null : schema.toPath());
        dialog.initOwner(owner);
        dialog.showAndWait().ifPresent(request -> {
            project.bindSchema(request.files(), request.schema());
            try {
                ProjectService.getInstance().save();
            } catch (IOException e) {
                DialogHelper.showError("Bind Schema in Project", "The project could not be saved.", e.getMessage());
                return;
            }
            if (activePath != null && project.schemaFor(activePath).isPresent()) {
                editorHost.setSchemaForActiveDocument(project.schemaFor(activePath).get().toFile());
            }
        });
    }

    /** Adds an XML catalog (OASIS) to the project. */
    public static void addCatalog(Window owner) {
        Optional<FxtProject> open = ProjectService.getInstance().current();
        if (open.isEmpty()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add Catalog to Project");
        chooser.setInitialDirectory(open.get().root().toFile());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML catalog", "*.xml", "*.cat"));
        File file = FileChooserHelper.showOpenDialog(chooser, owner);
        if (file == null) {
            return;
        }
        open.get().addCatalog(file.toPath());
        try {
            ProjectService.getInstance().save();
        } catch (IOException e) {
            DialogHelper.showError("Add Catalog to Project", "The project could not be saved.", e.getMessage());
        }
    }
}
