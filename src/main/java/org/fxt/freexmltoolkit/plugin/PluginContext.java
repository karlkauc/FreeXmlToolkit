package org.fxt.freexmltoolkit.plugin;

/**
 * What a plugin can contribute, handed to {@link FxtPlugin#register(PluginContext)}.
 * Application services (properties, favorites, snippets, templates, Schema Library …) are available
 * through {@link #service(Class)}.
 */
public interface PluginContext {

    /** @return the id of the plugin being registered */
    String pluginId();

    /** Adds an activity: an icon in the Activity Bar and its side panel. */
    void addActivity(PluginActivity activity);

    /** Adds a menu item to the Tools or Help menu. */
    void addMenuItem(PluginMenuItem item);

    /** Adds a validator that runs with every XML validation (Validation panel, F5 and fxt-cli). */
    void addValidator(DocumentValidator validator);

    /** Adds a describer of XML elements (Inspector documentation, grid tooltip). */
    default void addElementDescriber(ElementDescriber describer) {
    }

    /** Adds a task that runs once on a background thread a few seconds after the window opened. */
    void addStartupTask(Runnable task);

    /** Adds a listener called on the JavaFX thread when a document has been opened in the editor. */
    void addDocumentListener(DocumentListener listener);

    /** Adds a status-bar mark shown while the active document is one the plugin recognises. */
    void addDocumentBadge(DocumentBadge badge);

    /** Adds an XML Schema to the Schema Library while the plugin is on. */
    void addSchema(PluginSchema schema);

    /** Adds a stylesheet (URL, e.g. from {@code getClass().getResource(…)}) to the window. */
    void addStylesheet(String url);

    /**
     * @return an application service ({@code PropertiesService}, {@code FavoritesService}, …) from the
     *         application's service registry
     */
    <T> T service(Class<T> type);
}
