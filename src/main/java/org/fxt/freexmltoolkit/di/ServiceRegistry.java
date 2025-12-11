package org.fxt.freexmltoolkit.di;

import org.fxt.freexmltoolkit.service.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Central registry for service instances with support for dependency injection.
 *
 * <p>This is a lightweight DI container that doesn't require bytecode manipulation
 * (unlike Guice), making it compatible with all Java versions including Java 25.</p>
 *
 * <p>Production usage:</p>
 * <pre>{@code
 * // In FxtGui.init()
 * ServiceRegistry.initialize();
 *
 * // Get services anywhere
 * PropertiesService props = ServiceRegistry.get(PropertiesService.class);
 * }</pre>
 *
 * <p>Test usage:</p>
 * <pre>{@code
 * @BeforeEach
 * void setUp() {
 *     ServiceRegistry.reset();
 *     ServiceRegistry.register(PropertiesService.class, mockPropertiesService);
 *     ServiceRegistry.register(XmlService.class, mockXmlService);
 * }
 * }</pre>
 */
public final class ServiceRegistry {

    private static final Map<Class<?>, Object> instances = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Supplier<?>> factories = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;

    private ServiceRegistry() {
        // Utility class
    }

    /**
     * Initializes the registry with production service implementations.
     * Should be called once during application startup.
     *
     * <p>During the transition period, services use their existing
     * getInstance() methods. This will be migrated to constructor
     * injection in future updates.</p>
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        // Register service factories using existing getInstance() methods
        // This allows gradual migration without breaking existing code

        // 1. PropertiesService - no dependencies
        registerFactory(PropertiesService.class, PropertiesServiceImpl::getInstance);

        // 2. FavoritesService - no dependencies
        registerFactory(FavoritesService.class, FavoritesService::getInstance);

        // 3. ConnectionService - uses existing singleton
        registerFactory(ConnectionService.class, ConnectionServiceImpl::getInstance);

        // 4. XmlService - uses existing singleton
        registerFactory(XmlService.class, XmlServiceImpl::getInstance);

        // 5. UpdateCheckService - uses existing singleton
        registerFactory(UpdateCheckService.class, UpdateCheckServiceImpl::getInstance);

        // 6. SchematronService - no singleton pattern, create new instance
        registerFactory(SchematronService.class, SchematronServiceImpl::new);

        // 7. ExportMetadataService - for metadata in exported files
        registerFactory(ExportMetadataService.class, ExportMetadataService::new);

        initialized = true;
    }

    /**
     * Registers a factory for creating instances of a service type.
     * The factory is called lazily on first access.
     *
     * @param type    the service interface or class
     * @param factory supplier that creates new instances
     * @param <T>     the service type
     */
    public static <T> void registerFactory(Class<T> type, Supplier<T> factory) {
        factories.put(type, factory);
    }

    /**
     * Registers a specific instance for a service type.
     * Useful for testing with mock objects.
     *
     * @param type     the service interface or class
     * @param instance the instance to use
     * @param <T>      the service type
     */
    public static <T> void register(Class<T> type, T instance) {
        instances.put(type, instance);
    }

    /**
     * Gets an instance of the specified service type.
     * Creates the instance on first access (singleton pattern).
     *
     * <p>If the registry has not been initialized, it will be auto-initialized
     * with production services. This ensures backward compatibility with code
     * that doesn't explicitly call initialize().</p>
     *
     * @param type the service interface or class
     * @param <T>  the service type
     * @return the service instance
     * @throws IllegalStateException if no factory or instance is registered
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> type) {
        // Check for direct instance first (useful for test mocks)
        Object instance = instances.get(type);
        if (instance != null) {
            return (T) instance;
        }

        // Auto-initialize if not yet initialized and no mock registered
        if (!initialized && factories.isEmpty()) {
            initialize();
        }

        // Create instance using factory (thread-safe singleton)
        return (T) instances.computeIfAbsent(type, t -> {
            Supplier<?> factory = factories.get(t);
            if (factory == null) {
                // Fallback: try legacy getInstance() pattern
                return getLegacyInstance(type);
            }
            return factory.get();
        });
    }

    /**
     * Fallback for services still using getInstance() pattern.
     */
    @SuppressWarnings("unchecked")
    private static <T> T getLegacyInstance(Class<T> type) {
        // Try to call getInstance() via reflection for backward compatibility
        try {
            var method = type.getMethod("getInstance");
            return (T) method.invoke(null);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No factory registered for " + type.getName() +
                            " and no getInstance() method found", e);
        }
    }

    /**
     * Checks if an instance is registered for the given type.
     *
     * @param type the service type
     * @return true if registered
     */
    public static boolean isRegistered(Class<?> type) {
        return instances.containsKey(type) || factories.containsKey(type);
    }

    /**
     * Checks if the registry has been initialized.
     *
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Resets the registry. Used for testing to ensure clean state.
     */
    public static synchronized void reset() {
        instances.clear();
        factories.clear();
        initialized = false;
    }
}
