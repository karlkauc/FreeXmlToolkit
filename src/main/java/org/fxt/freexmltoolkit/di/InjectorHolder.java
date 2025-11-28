package org.fxt.freexmltoolkit.di;

/**
 * Backward-compatible holder for dependency injection.
 *
 * <p>Originally designed for Guice, this class now delegates to
 * {@link ServiceRegistry} for Java 25 compatibility.</p>
 *
 * @see ServiceRegistry
 * @deprecated Use {@link ServiceRegistry} directly instead
 */
@Deprecated(since = "2.0", forRemoval = true)
public final class InjectorHolder {

    private InjectorHolder() {
        // Utility class
    }

    /**
     * Initializes the service registry.
     *
     * @deprecated Use {@link ServiceRegistry#initialize()} instead
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public static void initialize(Object injector) {
        ServiceRegistry.initialize();
    }

    /**
     * Gets an instance of the specified type.
     *
     * @param type the class to get an instance of
     * @param <T>  the type
     * @return the service instance
     * @deprecated Use {@link ServiceRegistry#get(Class)} instead
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public static <T> T getInstance(Class<T> type) {
        return ServiceRegistry.get(type);
    }

    /**
     * Checks if initialized.
     *
     * @return true if initialized
     * @deprecated Use {@link ServiceRegistry#isInitialized()} instead
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public static boolean isInitialized() {
        return ServiceRegistry.isInitialized();
    }

    /**
     * Resets for testing.
     *
     * @deprecated Use {@link ServiceRegistry#reset()} instead
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public static void reset() {
        ServiceRegistry.reset();
    }
}
