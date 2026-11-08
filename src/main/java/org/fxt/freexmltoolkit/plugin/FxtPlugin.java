package org.fxt.freexmltoolkit.plugin;

/**
 * A FreeXmlToolkit plugin. Implementations are found with {@link java.util.ServiceLoader}: list the
 * class in {@code META-INF/services/org.fxt.freexmltoolkit.plugin.FxtPlugin} of the plugin's jar and
 * put the jar into the plugins folder ({@link PluginManager#pluginsFolder()}).
 *
 * <p>{@link #register(PluginContext)} runs once at start, before the window opens (also in
 * {@code fxt-cli}, where only validators are used): a plugin declares what it contributes there —
 * activities with a side panel, menu items, validators, startup tasks, document listeners, stylesheets.
 * UI parts are created later, on the JavaFX thread, through the factories it registers.
 */
public interface FxtPlugin {

    /** @return a stable, unique id ({@code fundsxml}, {@code com.example.orders}); also the settings key */
    String id();

    /** @return the name shown in Settings → Plugins */
    String name();

    /** @return the plugin's version */
    default String version() {
        return "";
    }

    /** @return one sentence for Settings → Plugins */
    default String description() {
        return "";
    }

    /** Declares the plugin's contributions. Must not block and must not touch the UI. */
    void register(PluginContext context);
}
