package org.fxt.freexmltoolkit.test;

import org.fxt.freexmltoolkit.di.FxtTestModule;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

/**
 * Base class for unit tests with ServiceRegistry DI support.
 *
 * <p>This class sets up the ServiceRegistry with mock services before each test
 * and cleans up after each test. Subclasses have direct access to mock
 * services for configuring test behavior.</p>
 *
 * <p>Note: Originally designed for Guice, now uses ServiceRegistry for
 * Java 25 compatibility (Guice 7.0.0's ASM doesn't support class file version 69).</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * class MyControllerTest extends GuiceTestBase {
 *
 *     private MyController controller;
 *
 *     @BeforeEach
 *     void setUp() {
 *         super.setUpGuice();
 *         // Create controller - it will get mocks from ServiceRegistry
 *         controller = new MyController();
 *     }
 *
 *     @Test
 *     void testSomething() {
 *         when(mockPropertiesService.get("key")).thenReturn("value");
 *         assertThat(controller.doSomething()).isEqualTo("expected");
 *     }
 * }
 * }</pre>
 *
 * @see ServiceRegistry
 * @see FxtTestModule
 */
public abstract class GuiceTestBase {

    /**
     * The test module containing mock bindings.
     */
    protected FxtTestModule testModule;

    // Mock service instances - subclasses can configure these

    /**
     * Mock PropertiesService for configuring application settings behavior.
     */
    protected PropertiesService mockPropertiesService;

    /**
     * Mock ConnectionService for configuring HTTP/network behavior.
     */
    protected ConnectionService mockConnectionService;

    /**
     * Mock XmlService for configuring XML parsing/validation behavior.
     */
    protected XmlService mockXmlService;

    /**
     * Mock UpdateCheckService for configuring version checking behavior.
     */
    protected UpdateCheckService mockUpdateCheckService;

    /**
     * Mock SchematronService for configuring Schematron validation behavior.
     */
    protected SchematronService mockSchematronService;

    /**
     * Mock FavoritesService for configuring favorites management behavior.
     */
    protected FavoritesService mockFavoritesService;

    /**
     * Sets up the ServiceRegistry with mock services.
     * Call this in your {@code @BeforeEach} method.
     *
     * @deprecated Method name retained for backward compatibility.
     * Consider using {@link #setUpServiceRegistry()} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @BeforeEach
    protected void setUpGuice() {
        setUpServiceRegistry();
    }

    /**
     * Sets up the ServiceRegistry with mock services.
     * Call this in your {@code @BeforeEach} method.
     */
    protected void setUpServiceRegistry() {
        // Create fresh test module with new mocks
        testModule = new FxtTestModule();

        // Configure ServiceRegistry with mocks
        testModule.configure();

        // Store references to mocks for easy access
        mockPropertiesService = testModule.mockPropertiesService();
        mockConnectionService = testModule.mockConnectionService();
        mockXmlService = testModule.mockXmlService();
        mockUpdateCheckService = testModule.mockUpdateCheckService();
        mockSchematronService = testModule.mockSchematronService();
        mockFavoritesService = testModule.mockFavoritesService();

        // Reset all mocks to clean state
        Mockito.reset(
                mockPropertiesService,
                mockConnectionService,
                mockXmlService,
                mockUpdateCheckService,
                mockSchematronService,
                mockFavoritesService
        );
    }

    /**
     * Cleans up the ServiceRegistry after each test.
     *
     * @deprecated Method name retained for backward compatibility.
     * Consider using {@link #tearDownServiceRegistry()} instead.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    @AfterEach
    protected void tearDownGuice() {
        tearDownServiceRegistry();
    }

    /**
     * Cleans up the ServiceRegistry after each test.
     */
    protected void tearDownServiceRegistry() {
        ServiceRegistry.reset();
        testModule = null;
    }

    /**
     * Gets an instance of the specified type from the ServiceRegistry.
     *
     * @param type the class to get an instance of
     * @param <T>  the type
     * @return the service instance (mock in test context)
     */
    protected <T> T getInstance(Class<T> type) {
        return ServiceRegistry.get(type);
    }
}
