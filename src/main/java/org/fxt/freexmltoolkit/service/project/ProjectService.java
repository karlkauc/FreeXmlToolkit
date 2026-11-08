package org.fxt.freexmltoolkit.service.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final ProjectService INSTANCE = new ProjectService();

    private volatile FxtProject current;
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
        applyCatalogs(project);
        setPreference(LAST_PROJECT, project.file().toString());
        fire(project);
    }

    /** Closes the open project. */
    public void close() {
        current = null;
        applyCatalogs(null);
        setPreference(LAST_PROJECT, "");
        fire(null);
    }

    /** Saves the open project after a change and tells the listeners. */
    public void save() throws IOException {
        FxtProject project = current;
        if (project != null) {
            project.save();
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
