package org.fxt.freexmltoolkit.service.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl;

/**
 * The open project ({@link FxtProject}), at most one: opening it registers its catalogs with the
 * Schema Library (for the session only) and remembers it, so it opens again at the next start.
 * Listeners hear about every open, close and change (on the calling thread).
 */
public final class ProjectService {

    private static final Logger logger = LogManager.getLogger(ProjectService.class);
    /** Settings key of the last open project file. */
    public static final String LAST_PROJECT = "project.last";
    /** Settings key of the recently opened project files (newest first, one per line). */
    public static final String RECENT_PROJECTS = "project.recent";
    /** How many recent projects are remembered. */
    static final int MAX_RECENT = 8;

    private static final ProjectService INSTANCE = new ProjectService();

    private volatile FxtProject current;
    /** Modification time and size of the open project's file as last read or written. */
    private volatile String stamp;
    private final List<Consumer<FxtProject>> listeners = new CopyOnWriteArrayList<>();

    private ProjectService() {
    }

    public static ProjectService getInstance() {
        return INSTANCE;
    }

    /** @return the open project, if any */
    public Optional<FxtProject> current() {
        return Optional.ofNullable(current);
    }

    /** Called with the project after it opened, changed or closed ({@code null}). */
    public void addListener(Consumer<FxtProject> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<FxtProject> listener) {
        listeners.remove(listener);
    }

    /** Opens a project file (closing the open one). */
    public FxtProject open(Path file) throws IOException {
        FxtProject project = FxtProject.load(file);
        activate(project);
        return project;
    }

    /** Creates and saves a new project and opens it. */
    public FxtProject create(Path file, String name) throws IOException {
        FxtProject project = FxtProject.create(file, name);
        project.save();
        activate(project);
        return project;
    }

    private void activate(FxtProject project) {
        current = project;
        stamp = stampOf(project.file());
        applyCatalogs(project);
        setPreference(LAST_PROJECT, project.file().toString());
        remember(project.file());
        fire(project);
    }

    /** Puts a project file at the top of the recent list. */
    private static void remember(Path file) {
        List<String> recent = new ArrayList<>(recentEntries());
        String entry = file.toAbsolutePath().normalize().toString();
        recent.remove(entry);
        recent.add(0, entry);
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
        setPreference(RECENT_PROJECTS, String.join("\n", recent));
    }

    private static List<String> recentEntries() {
        String value = preference(RECENT_PROJECTS);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
    }

    /** @return the recently opened project files that still exist, newest first */
    public List<Path> recent() {
        List<Path> files = new ArrayList<>();
        for (String entry : recentEntries()) {
            try {
                Path path = Path.of(entry);
                if (Files.isRegularFile(path)) {
                    files.add(path);
                }
            } catch (java.nio.file.InvalidPathException e) {
                logger.debug("Skipping recent project {}: {}", entry, e.getMessage());
            }
        }
        return files;
    }

    /** Forgets the recent projects. */
    public void clearRecent() {
        setPreference(RECENT_PROJECTS, "");
    }

    /**
     * Reads the open project's file again (it was changed outside, e.g. edited as JSON or updated
     * by a pull) and tells the listeners.
     *
     * @return whether a project was reloaded
     * @throws IOException when the changed file is no valid project file; the project stays as it was
     */
    public boolean reload() throws IOException {
        FxtProject project = current;
        if (project == null || !Files.isRegularFile(project.file())) {
            return false;
        }
        String read = stampOf(project.file());
        FxtProject fresh = FxtProject.load(project.file());
        current = fresh;
        stamp = read;
        applyCatalogs(fresh);
        fire(fresh);
        return true;
    }

    /** Closes the open project. */
    public void close() {
        current = null;
        stamp = null;
        applyCatalogs(null);
        setPreference(LAST_PROJECT, "");
        fire(null);
    }

    /** Saves the open project after a change and tells the listeners. */
    public void save() throws IOException {
        FxtProject project = current;
        if (project != null) {
            project.save();
            stamp = stampOf(project.file());
            applyCatalogs(project);
            fire(project);
        }
    }

    /** Opens the project of the last session, if its file still exists. */
    public Optional<FxtProject> reopenLast() {
        String last = preference(LAST_PROJECT);
        if (last == null || last.isBlank()) {
            return Optional.empty();
        }
        Path file = Path.of(last);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(open(file));
        } catch (IOException e) {
            logger.warn("Cannot reopen the project {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Reloads the open project when its file changed since it was read or saved here (edited in the
     * editor, updated by a pull). A file that is no valid project any more is reported in the log
     * and the project stays as it was until the file is valid again.
     *
     * @return whether the project was reloaded
     */
    public boolean reloadIfChanged() {
        FxtProject project = current;
        if (project == null) {
            return false;
        }
        String now = stampOf(project.file());
        if (now == null || now.equals(stamp)) {
            return false;
        }
        try {
            return reload();
        } catch (IOException e) {
            stamp = now; // report a broken file once, not every few seconds
            logger.warn("The changed project file is not used: {}", e.getMessage());
            return false;
        }
    }

    private static String stampOf(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis() + ":" + Files.size(file);
        } catch (IOException e) {
            return null;
        }
    }

    /** @return what the open project assigns to a file (schema, Schematron, scenario) */
    public FxtProject.Assignment assignmentFor(Path file) {
        FxtProject project = current;
        return project == null || file == null ? FxtProject.Assignment.NONE : project.assignmentFor(file);
    }

    /** @return the schema the open project binds to an XML file */
    public Optional<Path> schemaFor(Path xml) {
        FxtProject project = current;
        return project == null || xml == null ? Optional.empty() : project.schemaFor(xml);
    }

    private static void applyCatalogs(FxtProject project) {
        try {
            SchemaLibraryServiceImpl.shared().setProjectCatalogs(project == null ? List.of() : project.catalogs());
        } catch (RuntimeException e) {
            logger.debug("No Schema Library for project catalogs: {}", e.getMessage());
        }
    }

    private void fire(FxtProject project) {
        for (Consumer<FxtProject> listener : listeners) {
            try {
                listener.accept(project);
            } catch (RuntimeException e) {
                logger.warn("Project listener failed", e);
            }
        }
    }

    private static String preference(String key) {
        try {
            return ServiceRegistry.get(PropertiesService.class).get(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void setPreference(String key, String value) {
        try {
            ServiceRegistry.get(PropertiesService.class).set(key, value);
        } catch (RuntimeException e) {
            logger.debug("Project setting not saved: {}", e.getMessage());
        }
    }

    /** Test hook: forget the open project without touching the settings. */
    void resetForTesting() {
        current = null;
        applyCatalogs(null);
    }
}
