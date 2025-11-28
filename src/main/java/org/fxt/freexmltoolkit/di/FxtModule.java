package org.fxt.freexmltoolkit.di;

/**
 * Configuration module for FreeXmlToolkit dependency injection.
 *
 * <p>This module defines the service bindings for the application.
 * The actual registration is handled by {@link ServiceRegistry}.</p>
 *
 * <p>Note: Guice was originally planned but doesn't support Java 25's
 * class file version 69. Using manual DI via ServiceRegistry instead.</p>
 *
 * @see ServiceRegistry
 */
public final class FxtModule {

    private FxtModule() {
        // Configuration class, not instantiable
    }

    /**
     * Service binding order (respecting dependencies):
     * <ol>
     *   <li>PropertiesService - no dependencies</li>
     *   <li>FavoritesService - no dependencies</li>
     *   <li>ConnectionService - depends on PropertiesService</li>
     *   <li>XmlService - no constructor dependencies</li>
     *   <li>UpdateCheckService - depends on PropertiesService, ConnectionService</li>
     *   <li>SchematronService - no constructor dependencies</li>
     * </ol>
     *
     * <p>This order is implemented in {@link ServiceRegistry#initialize()}.</p>
     */
    public static void configure() {
        ServiceRegistry.initialize();
    }
}
