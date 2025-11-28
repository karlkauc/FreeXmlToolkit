package org.fxt.freexmltoolkit.di;

import org.fxt.freexmltoolkit.service.*;

import static org.mockito.Mockito.mock;

/**
 * Test module providing mock implementations for unit testing.
 *
 * <p>This module creates Mockito mocks for all core services and registers
 * them with {@link ServiceRegistry}, allowing tests to configure behavior
 * without actual implementations.</p>
 *
 * <p>Usage in tests:</p>
 * <pre>{@code
 * class MyControllerTest {
 *     private FxtTestModule testModule;
 *
 *     @BeforeEach
 *     void setUp() {
 *         testModule = new FxtTestModule();
 *         testModule.configure();
 *
 *         when(testModule.getMockPropertiesService().get("key")).thenReturn("value");
 *     }
 *
 *     @AfterEach
 *     void tearDown() {
 *         ServiceRegistry.reset();
 *     }
 * }
 * }</pre>
 */
public record FxtTestModule(PropertiesService mockPropertiesService, ConnectionService mockConnectionService,
                            XmlService mockXmlService, UpdateCheckService mockUpdateCheckService,
                            SchematronService mockSchematronService, FavoritesService mockFavoritesService) {

    /**
     * Creates a new test module with fresh mock instances.
     */
    public FxtTestModule() {
        this(mock(PropertiesService.class), mock(ConnectionService.class), mock(XmlService.class), mock(UpdateCheckService.class), mock(SchematronService.class), mock(FavoritesService.class));
    }

    /**
     * Creates a test module with custom mock instances.
     * Useful when you need more control over mock creation.
     */
    public FxtTestModule {
    }

    /**
     * Configures the ServiceRegistry with mock instances.
     * Call this in your test's {@code @BeforeEach} method after creating the module.
     */
    public void configure() {
        ServiceRegistry.reset();
        ServiceRegistry.register(PropertiesService.class, mockPropertiesService);
        ServiceRegistry.register(ConnectionService.class, mockConnectionService);
        ServiceRegistry.register(XmlService.class, mockXmlService);
        ServiceRegistry.register(UpdateCheckService.class, mockUpdateCheckService);
        ServiceRegistry.register(SchematronService.class, mockSchematronService);
        ServiceRegistry.register(FavoritesService.class, mockFavoritesService);
    }

    // Getters for test access to mocks
}
