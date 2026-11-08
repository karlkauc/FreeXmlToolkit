package org.fxt.freexmltoolkit.plugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.AdminPolicy;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * Finds, registers and holds the plugins ({@link FxtPlugin}): the built-in ones on the application's
 * class path and the jars in {@link #pluginsFolder()} (each in its own class loader; switched off by the
 * policy key {@value AdminPolicy#KEY_EXTERNAL_PLUGINS_ALLOWED}). A plugin is skipped when the setting
 * {@code plugin.<id>.enabled} is {@code false}; a plugin whose {@code register} fails is reported and
 * contributes nothing. Changes take effect at the next start.
 */
public final class PluginManager {

    private static final Logger logger = LogManager.getLogger(PluginManager.class);
    private static final PluginManager INSTANCE = new PluginManager();

    /** Where a plugin came from. */
    public enum Origin { BUILT_IN, EXTERNAL }

    /** What became of a plugin. */
    public enum State { ACTIVE, DISABLED, FAILED }

    /**
     * A found plugin.
     *
     * @param jar   the jar of an external plugin, or {@code null}
     * @param error why it failed, or {@code null}
     */
    public record PluginInfo(String id, String name, String version, String description, Origin origin, Path jar,
                             State state, String error) {
    }

    private final List<PluginInfo> plugins = new CopyOnWriteArrayList<>();
    private final List<PluginActivity> activities = new CopyOnWriteArrayList<>();
    private final List<PluginMenuItem> menuItems = new CopyOnWriteArrayList<>();
    private final List<DocumentValidator> validators = new CopyOnWriteArrayList<>();
    private final List<Runnable> startupTasks = new CopyOnWriteArrayList<>();
    private final List<DocumentListener> documentListeners = new CopyOnWriteArrayList<>();
    private final List<String> stylesheets = new CopyOnWriteArrayList<>();
    private volatile boolean loaded;

    private PluginManager() {
    }

    public static PluginManager getInstance() {
        return INSTANCE;
    }

    /** @return the settings key that switches a plugin on or off */
    public static String enabledKey(String pluginId) {
        return "plugin." + pluginId + ".enabled";
    }

    /** @return the folder whose jars are loaded as plugins ({@code ~/.freeXmlToolkit/plugins}, or {@code -Dfxt.plugins.dir}) */
    public static Path pluginsFolder() {
        String configured = System.getProperty("fxt.plugins.dir");
        return configured != null && !configured.isBlank() ? Path.of(configured)
                : Path.of(System.getProperty("user.home"), ".freeXmlToolkit", "plugins");
    }

    /** Loads the plugins on first use (the application loads them at start; tools and tests on demand). */
    private void ensureLoaded() {
        if (!loaded) {
            loadAll();
        }
    }

    /** Loads all plugins once (later calls do nothing). */
    public synchronized void loadAll() {
        if (loaded) {
            return;
        }
        loaded = true;
        Set<String> ids = new HashSet<>();
        try {
            for (FxtPlugin plugin : ServiceLoader.load(FxtPlugin.class, PluginManager.class.getClassLoader())) {
                add(plugin, Origin.BUILT_IN, null, ids);
            }
        } catch (ServiceConfigurationError e) {
            logger.warn("A built-in plugin could not be loaded: {}", e.getMessage());
        }
        if (AdminPolicy.current().externalPluginsAllowed()) {
            loadFolder(pluginsFolder(), ids);
        } else {
            logger.info("Plugins from {} are disabled by the administrator policy", pluginsFolder());
        }
        logger.info("Plugins: {}", plugins.stream().map(p -> p.id() + "=" + p.state()).toList());
    }

    private void loadFolder(Path folder, Set<String> ids) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.jar")) {
            stream.forEach(jars::add);
        } catch (IOException e) {
            logger.warn("Cannot list the plugins folder {}: {}", folder, e.getMessage());
            return;
        }
        jars.sort(null);
        for (Path jar : jars) {
            try {
                // Never closed: the plugin's classes are used for the whole session.
                URLClassLoader loader = new URLClassLoader("plugin:" + jar.getFileName(),
                        new URL[] {jar.toUri().toURL()}, PluginManager.class.getClassLoader());
                boolean any = false;
                for (FxtPlugin plugin : ServiceLoader.load(FxtPlugin.class, loader)) {
                    if (plugin.getClass().getClassLoader() == loader) { // not a built-in one seen again
                        add(plugin, Origin.EXTERNAL, jar, ids);
                        any = true;
                    }
                }
                if (!any) {
                    plugins.add(new PluginInfo(jar.getFileName().toString(), jar.getFileName().toString(), "", "",
                            Origin.EXTERNAL, jar, State.FAILED,
                            "No plugin found (META-INF/services/" + FxtPlugin.class.getName() + " is missing)."));
                }
            } catch (IOException | ServiceConfigurationError | LinkageError e) {
                logger.warn("Plugin jar {} could not be loaded", jar, e);
                plugins.add(new PluginInfo(jar.getFileName().toString(), jar.getFileName().toString(), "", "",
                        Origin.EXTERNAL, jar, State.FAILED, String.valueOf(e.getMessage())));
            }
        }
    }

    /** Registers one plugin (its contributions are kept only when {@code register} completes). */
    private void add(FxtPlugin plugin, Origin origin, Path jar, Set<String> ids) {
        String id;
        String name;
        String version;
        String description;
        try {
            id = plugin.id();
            name = plugin.name();
            version = plugin.version();
            description = plugin.description();
        } catch (RuntimeException | LinkageError e) {
            plugins.add(new PluginInfo(plugin.getClass().getName(), plugin.getClass().getSimpleName(), "", "", origin,
                    jar, State.FAILED, String.valueOf(e.getMessage())));
            return;
        }
        if (id == null || id.isBlank() || !ids.add(id.toLowerCase(Locale.ROOT))) {
            plugins.add(new PluginInfo(String.valueOf(id), name, version, description, origin, jar, State.FAILED,
                    id == null || id.isBlank() ? "The plugin has no id." : "Another plugin has the id " + id + "."));
            return;
        }
        if ("false".equalsIgnoreCase(setting(enabledKey(id)))) {
            plugins.add(new PluginInfo(id, name, version, description, origin, jar, State.DISABLED, null));
            return;
        }
        Collector collector = new Collector(id);
        try {
            plugin.register(collector);
        } catch (RuntimeException | LinkageError e) {
            logger.warn("Plugin {} failed to register", id, e);
            plugins.add(new PluginInfo(id, name, version, description, origin, jar, State.FAILED,
                    String.valueOf(e.getMessage())));
            return;
        }
        activities.addAll(collector.activities);
        menuItems.addAll(collector.menuItems);
        validators.addAll(collector.validators);
        startupTasks.addAll(collector.startupTasks);
        documentListeners.addAll(collector.documentListeners);
        stylesheets.addAll(collector.stylesheets);
        plugins.add(new PluginInfo(id, name, version, description, origin, jar, State.ACTIVE, null));
    }

    private static String setting(String key) {
        try {
            return ServiceRegistry.get(PropertiesService.class).get(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- what the plugins contributed --------------------------------------------------------

    public List<PluginInfo> plugins() {
        ensureLoaded();
        return List.copyOf(plugins);
    }

    public List<PluginActivity> activities() {
        ensureLoaded();
        return List.copyOf(activities);
    }

    public List<PluginMenuItem> menuItems() {
        ensureLoaded();
        return List.copyOf(menuItems);
    }

    public List<DocumentValidator> validators() {
        ensureLoaded();
        return List.copyOf(validators);
    }

    public List<Runnable> startupTasks() {
        ensureLoaded();
        return List.copyOf(startupTasks);
    }

    public List<DocumentListener> documentListeners() {
        ensureLoaded();
        return List.copyOf(documentListeners);
    }

    public List<String> stylesheets() {
        ensureLoaded();
        return List.copyOf(stylesheets);
    }

    /**
     * A problem found by a plugin validator.
     *
     * @param source the validator's name
     */
    public record ValidatorProblem(String source, String severity, int line, String message) {
    }

    /** Runs the validators that accept the document; a failing validator becomes one error. */
    public List<ValidatorProblem> validate(String content, String documentName) {
        ensureLoaded();
        List<ValidatorProblem> problems = new ArrayList<>();
        for (DocumentValidator validator : validators) {
            String source;
            try {
                source = validator.name();
                if (!validator.accepts(content, documentName)) {
                    continue;
                }
            } catch (RuntimeException | LinkageError e) {
                continue;
            }
            try {
                for (DocumentValidator.Problem p : validator.validate(content, documentName)) {
                    problems.add(new ValidatorProblem(source, p.severity() == null ? "error" : p.severity(),
                            Math.max(0, p.line()), p.message()));
                }
            } catch (RuntimeException | LinkageError e) {
                logger.warn("Validator {} failed", source, e);
                problems.add(new ValidatorProblem(source, "error", 0, "The check could not run: " + e.getMessage()));
            }
        }
        return problems;
    }

    // ---- tests ---------------------------------------------------------------------------------

    /** Test hook: registers a plugin as if found on the class path (also before {@link #loadAll()}). */
    public synchronized void registerForTesting(FxtPlugin plugin) {
        Set<String> ids = new HashSet<>();
        plugins.forEach(p -> ids.add(p.id().toLowerCase(Locale.ROOT)));
        add(plugin, Origin.BUILT_IN, null, ids);
    }

    /** Test hook: forgets everything; the next {@link #loadAll()} loads again. */
    public synchronized void resetForTesting() {
        plugins.clear();
        activities.clear();
        menuItems.clear();
        validators.clear();
        startupTasks.clear();
        documentListeners.clear();
        stylesheets.clear();
        loaded = false;
    }

    /** Test hook: loads the jars of a folder (as {@link #loadAll()} does for the plugins folder). */
    synchronized void loadFolderForTesting(Path folder) {
        Set<String> ids = new HashSet<>();
        plugins.forEach(p -> ids.add(p.id().toLowerCase(Locale.ROOT)));
        loadFolder(folder, ids);
    }

    /** Collects one plugin's contributions until its {@code register} has completed. */
    private static final class Collector implements PluginContext {
        private final String pluginId;
        final List<PluginActivity> activities = new ArrayList<>();
        final List<PluginMenuItem> menuItems = new ArrayList<>();
        final List<DocumentValidator> validators = new ArrayList<>();
        final List<Runnable> startupTasks = new ArrayList<>();
        final List<DocumentListener> documentListeners = new ArrayList<>();
        final List<String> stylesheets = new ArrayList<>();

        Collector(String pluginId) {
            this.pluginId = pluginId;
        }

        @Override public String pluginId() { return pluginId; }
        @Override public void addActivity(PluginActivity activity) { activities.add(activity); }
        @Override public void addMenuItem(PluginMenuItem item) { menuItems.add(item); }
        @Override public void addValidator(DocumentValidator validator) { validators.add(validator); }
        @Override public void addStartupTask(Runnable task) { startupTasks.add(task); }
        @Override public void addDocumentListener(DocumentListener listener) { documentListeners.add(listener); }
        @Override public void addStylesheet(String url) { stylesheets.add(url); }
        @Override public <T> T service(Class<T> type) { return ServiceRegistry.get(type); }
    }
}
