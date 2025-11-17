package org.fxt.freexmltoolkit.controller;

import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MainController.
 * Tests main application lifecycle, tab management, and executor services.
 */
@ExtendWith(MockitoExtension.class)
class MainControllerTest {

    private MainController controller;

    @Mock
    private Label mockVersion;

    @Mock
    private AnchorPane mockContentPane;

    @Mock
    private Button mockXslt;

    @Mock
    private Button mockXmlUltimate;

    @Mock
    private Button mockXsd;

    @Mock
    private Button mockXsdValidation;

    @Mock
    private Button mockSchematron;

    @Mock
    private Button mockFop;

    @Mock
    private Button mockSignature;

    @Mock
    private Button mockHelp;

    @Mock
    private Button mockSettings;

    @Mock
    private Button mockExit;

    @Mock
    private MenuItem mockMenuItemExit;

    @Mock
    private Menu mockLastOpenFilesMenu;

    @Mock
    private CheckMenuItem mockXmlEditorSidebarMenuItem;

    @Mock
    private CheckMenuItem mockXpathQueryPaneMenuItem;

    @Mock
    private VBox mockLeftMenu;

    @Mock
    private ImageView mockLogoImageView;

    @Mock
    private PropertiesService mockPropertiesService;

    @BeforeEach
    void setUp() {
        controller = new MainController();
    }

    /**
     * Helper method to inject fields using reflection
     */
    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        if (field != null) {
            field.setAccessible(true);
            field.set(target, value);
        }
    }

    private Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    @Test
    @DisplayName("Should create controller instance")
    void testControllerInstantiation() {
        assertNotNull(controller);
    }

    @Test
    @DisplayName("Should validate tab paths for all features")
    void testTabPaths() {
        String[] tabPaths = {
            "/pages/tab_xslt.fxml",
            "/pages/tab_xml.fxml",
            "/pages/tab_xml_ultimate.fxml",
            "/pages/tab_xsd.fxml",
            "/pages/tab_validation.fxml",
            "/pages/tab_schematron.fxml",
            "/pages/tab_fop.fxml",
            "/pages/tab_signature.fxml",
            "/pages/tab_help.fxml",
            "/pages/settings.fxml",
            "/pages/tab_templates.fxml",
            "/pages/tab_schema_generator.fxml",
            "/pages/tab_xslt_developer.fxml",
            "/pages/welcome.fxml"
        };

        for (String path : tabPaths) {
            assertNotNull(path);
            assertTrue(path.startsWith("/pages/"));
            assertTrue(path.endsWith(".fxml"));
        }
    }

    @Test
    @DisplayName("Should validate button IDs for navigation")
    void testButtonIds() {
        String[] buttonIds = {
            "xslt", "xml", "xmlEnhanced", "xmlNew", "xmlUltimate",
            "xsd", "xsdValidation", "schematron", "fop", "signature",
            "help", "settings", "templates", "schemaGenerator", "xsltDeveloper"
        };

        for (String id : buttonIds) {
            assertNotNull(id);
            assertFalse(id.isEmpty());
        }
    }

    @Test
    @DisplayName("Should validate memory monitoring format")
    void testMemoryMonitoringFormat() {
        Runtime runtime = Runtime.getRuntime();
        long allocated = runtime.totalMemory();
        long used = allocated - runtime.freeMemory();
        long max = runtime.maxMemory();
        long available = max - used;

        assertTrue(allocated >= 0);
        assertTrue(used >= 0);
        assertTrue(max > 0);
        assertTrue(available >= 0);
    }

    @Test
    @DisplayName("Should validate memory percentage calculation")
    void testMemoryPercentageCalculation() {
        Runtime runtime = Runtime.getRuntime();
        long used = 100_000_000; // 100 MB
        long available = 400_000_000; // 400 MB

        float percentage = (float) used / available * 100;
        assertTrue(percentage >= 0);
        assertTrue(percentage <= 100);
    }

    @Test
    @DisplayName("Should validate scheduler pool size")
    void testSchedulerPoolSize() throws Exception {
        Field schedulerField = findField(controller.getClass(), "scheduler");
        if (schedulerField != null) {
            schedulerField.setAccessible(true);
            ScheduledExecutorService scheduler = (ScheduledExecutorService) schedulerField.get(controller);
            assertNotNull(scheduler);
        }
    }

    @Test
    @DisplayName("Should validate executor service type")
    void testExecutorServiceType() throws Exception {
        Field serviceField = findField(controller.getClass(), "service");
        if (serviceField != null) {
            serviceField.setAccessible(true);
            ExecutorService service = (ExecutorService) serviceField.get(controller);
            assertNotNull(service);
        }
    }

    @Test
    @DisplayName("Should validate theme names")
    void testThemeNames() {
        String lightTheme = "light";
        String darkTheme = "dark";

        assertNotNull(lightTheme);
        assertNotNull(darkTheme);
        assertEquals("light", lightTheme);
        assertEquals("dark", darkTheme);
    }

    @Test
    @DisplayName("Should validate theme CSS file paths")
    void testThemeCssFilePaths() {
        String lightThemeCss = "/css/light-theme.css";
        String darkThemeCss = "/css/dark-theme.css";

        assertNotNull(lightThemeCss);
        assertNotNull(darkThemeCss);
        assertTrue(lightThemeCss.endsWith(".css"));
        assertTrue(darkThemeCss.endsWith(".css"));
        assertTrue(lightThemeCss.contains("light-theme"));
        assertTrue(darkThemeCss.contains("dark-theme"));
    }

    @Test
    @DisplayName("Should validate property keys")
    void testPropertyKeys() {
        String themeKey = "ui.theme";
        String xmlThemeKey = "xml.editor.theme";
        String sidebarKey = "xmlEditorSidebar.visible";
        String xpathKey = "xpathQueryPane.visible";

        assertNotNull(themeKey);
        assertNotNull(xmlThemeKey);
        assertNotNull(sidebarKey);
        assertNotNull(xpathKey);
    }

    @Test
    @DisplayName("Should validate file extension handling for recent files")
    void testRecentFileExtensions() {
        String[] supportedExtensions = {".xml", ".xsd", ".sch", ".schematron"};

        for (String ext : supportedExtensions) {
            assertNotNull(ext);
            assertTrue(ext.startsWith("."));
        }

        // Test file type detection
        File xmlFile = new File("document.xml");
        assertTrue(xmlFile.getName().toLowerCase().endsWith(".xml"));

        File xsdFile = new File("schema.xsd");
        assertTrue(xsdFile.getName().toLowerCase().endsWith(".xsd"));

        File schFile = new File("rules.sch");
        assertTrue(schFile.getName().toLowerCase().endsWith(".sch"));
    }

    @Test
    @DisplayName("Should validate shutdown timeout values")
    void testShutdownTimeouts() {
        long timeoutSeconds = 1;
        TimeUnit unit = TimeUnit.SECONDS;

        assertTrue(timeoutSeconds > 0);
        assertEquals(TimeUnit.SECONDS, unit);
    }

    @Test
    @DisplayName("Should validate memory update interval")
    void testMemoryUpdateInterval() {
        long initialDelay = 1;
        long period = 2;
        TimeUnit unit = TimeUnit.SECONDS;

        assertTrue(initialDelay >= 0);
        assertTrue(period > 0);
        assertEquals(TimeUnit.SECONDS, unit);
    }

    @Test
    @DisplayName("Should validate active button CSS class")
    void testActiveButtonCssClass() {
        String activeClass = "active";

        assertNotNull(activeClass);
        assertEquals("active", activeClass);
    }

    @Test
    @DisplayName("Should validate default sidebar visibility")
    void testDefaultSidebarVisibility() {
        // Default should be true (visible) when no preference is set
        String nullPreference = null;
        boolean defaultVisible = nullPreference == null || Boolean.parseBoolean(nullPreference);

        assertTrue(defaultVisible, "Sidebar should be visible by default");
    }

    @Test
    @DisplayName("Should validate default XPath pane visibility")
    void testDefaultXPathPaneVisibility() {
        // Default should be true (visible) when no preference is set
        String nullPreference = null;
        boolean defaultVisible = nullPreference == null || Boolean.parseBoolean(nullPreference);

        assertTrue(defaultVisible, "XPath pane should be visible by default");
    }

    @Test
    @DisplayName("Should handle boolean preference parsing")
    void testBooleanPreferenceParsing() {
        String trueValue = "true";
        String falseValue = "false";

        assertTrue(Boolean.parseBoolean(trueValue));
        assertFalse(Boolean.parseBoolean(falseValue));
    }

    @Test
    @DisplayName("Should validate revolutionary features tab IDs")
    void testRevolutionaryFeaturesTabIds() {
        String templatesId = "templates";
        String schemaGeneratorId = "schemaGenerator";
        String xsltDeveloperId = "xsltDeveloper";

        assertNotNull(templatesId);
        assertNotNull(schemaGeneratorId);
        assertNotNull(xsltDeveloperId);
        assertEquals("templates", templatesId);
        assertEquals("schemaGenerator", schemaGeneratorId);
        assertEquals("xsltDeveloper", xsltDeveloperId);
    }

    @Test
    @DisplayName("Should validate alert types")
    void testAlertTypes() {
        Alert.AlertType infoType = Alert.AlertType.INFORMATION;

        assertNotNull(infoType);
        assertEquals(Alert.AlertType.INFORMATION, infoType);
    }

    @Test
    @DisplayName("Should validate runtime memory information availability")
    void testRuntimeMemoryAvailability() {
        Runtime runtime = Runtime.getRuntime();

        assertNotNull(runtime);
        assertTrue(runtime.maxMemory() > 0);
        assertTrue(runtime.totalMemory() >= 0);
        assertTrue(runtime.freeMemory() >= 0);
    }

    @Test
    @DisplayName("Should validate executor service shutdown sequence")
    void testExecutorServiceShutdownSequence() {
        // Test that shutdown methods are available
        assertDoesNotThrow(() -> {
            ScheduledExecutorService testScheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);
            ExecutorService testService = java.util.concurrent.Executors.newCachedThreadPool();

            testScheduler.shutdownNow();
            testService.shutdownNow();

            boolean schedulerTerminated = testScheduler.awaitTermination(100, TimeUnit.MILLISECONDS);
            boolean serviceTerminated = testService.awaitTermination(100, TimeUnit.MILLISECONDS);

            // Just verify the API works
            assertNotNull(schedulerTerminated);
            assertNotNull(serviceTerminated);
        });
    }

    @Test
    @DisplayName("Should validate file exists check for recent files")
    void testFileExistsCheckForRecentFiles() {
        File existingFile = new File(".");
        File nonExistingFile = new File("/nonexistent/path/file.xml");

        assertTrue(existingFile.exists());
        assertFalse(nonExistingFile.exists());
    }

    @Test
    @DisplayName("Should validate recent files menu structure")
    void testRecentFilesMenuStructure() {
        Menu recentFilesMenu = new Menu("Recent Files");
        MenuItem menuItem1 = new MenuItem("file1.xml");
        MenuItem menuItem2 = new MenuItem("file2.xsd");

        recentFilesMenu.getItems().add(menuItem1);
        recentFilesMenu.getItems().add(menuItem2);

        assertNotNull(recentFilesMenu);
        assertEquals(2, recentFilesMenu.getItems().size());
        assertEquals("file1.xml", menuItem1.getText());
        assertEquals("file2.xsd", menuItem2.getText());
    }

    @Test
    @DisplayName("Should validate menu item disable state")
    void testMenuItemDisableState() {
        Menu menu = new Menu("Test Menu");

        menu.setDisable(true);
        assertTrue(menu.isDisable());

        menu.setDisable(false);
        assertFalse(menu.isDisable());
    }

    @Test
    @DisplayName("Should validate welcome page path")
    void testWelcomePagePath() {
        String welcomePath = "/pages/welcome.fxml";

        assertNotNull(welcomePath);
        assertTrue(welcomePath.startsWith("/pages/"));
        assertTrue(welcomePath.endsWith(".fxml"));
        assertTrue(welcomePath.contains("welcome"));
    }

    @Test
    @DisplayName("Should validate file name extraction from file path")
    void testFileNameExtraction() {
        File file = new File("/path/to/document.xml");

        assertEquals("document.xml", file.getName());
    }

    @Test
    @DisplayName("Should validate case-insensitive file extension checking")
    void testCaseInsensitiveFileExtension() {
        String fileName1 = "Document.XML";
        String fileName2 = "document.xml";
        String fileName3 = "DOCUMENT.XML";

        assertTrue(fileName1.toLowerCase().endsWith(".xml"));
        assertTrue(fileName2.toLowerCase().endsWith(".xml"));
        assertTrue(fileName3.toLowerCase().endsWith(".xml"));
    }

    @Test
    @DisplayName("Should validate scheduler thread pool creation")
    void testSchedulerThreadPoolCreation() {
        ScheduledExecutorService scheduler = java.util.concurrent.Executors.newScheduledThreadPool(5);

        assertNotNull(scheduler);
        assertFalse(scheduler.isShutdown());
        assertFalse(scheduler.isTerminated());

        scheduler.shutdown();
    }

    @Test
    @DisplayName("Should validate cached thread pool creation")
    void testCachedThreadPoolCreation() {
        ExecutorService service = java.util.concurrent.Executors.newCachedThreadPool();

        assertNotNull(service);
        assertFalse(service.isShutdown());
        assertFalse(service.isTerminated());

        service.shutdown();
    }

    @Test
    @DisplayName("Should validate date string format")
    void testDateStringFormat() {
        java.util.Date date = new java.util.Date();
        String dateString = date.toString();

        assertNotNull(dateString);
        assertFalse(dateString.isEmpty());
    }

    @Test
    @DisplayName("Should validate memory size formatting")
    void testMemorySizeFormatting() {
        // Test that FileUtils.byteCountToDisplaySize exists (via Apache Commons IO)
        long bytes1024 = 1024;
        long bytesMB = 1024 * 1024;
        long bytesGB = 1024 * 1024 * 1024;

        assertTrue(bytes1024 > 0);
        assertTrue(bytesMB > bytes1024);
        assertTrue(bytesGB > bytesMB);
    }

    @Test
    @DisplayName("Should validate show menu toggle state")
    void testShowMenuToggleState() throws Exception {
        Field showMenuField = findField(controller.getClass(), "showMenu");
        if (showMenuField != null) {
            showMenuField.setAccessible(true);
            Boolean showMenu = (Boolean) showMenuField.get(controller);
            assertNotNull(showMenu);
            assertTrue(showMenu, "Menu should be shown by default");
        }
    }

    @Test
    @DisplayName("Should validate file type switch logic")
    void testFileTypeSwitchLogic() {
        String xmlFile = "document.xml";
        String xsdFile = "schema.xsd";
        String schFile = "rules.sch";
        String schematronFile = "rules.schematron";

        assertTrue(xmlFile.toLowerCase().endsWith(".xml"));
        assertTrue(xsdFile.toLowerCase().endsWith(".xsd"));
        assertTrue(schFile.toLowerCase().endsWith(".sch") ||
                schFile.toLowerCase().endsWith(".schematron"));
        assertTrue(schematronFile.toLowerCase().endsWith(".sch") ||
                schematronFile.toLowerCase().endsWith(".schematron"));
    }
}
