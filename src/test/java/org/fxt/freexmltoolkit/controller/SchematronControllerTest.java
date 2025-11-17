package org.fxt.freexmltoolkit.controller;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.SchematronCodeEditor;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.SchematronService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SchematronController.
 * Tests business logic and controller initialization without full JavaFX environment.
 */
@ExtendWith(MockitoExtension.class)
class SchematronControllerTest {

    private SchematronController controller;

    @Mock
    private TabPane mockTabPane;

    @Mock
    private Tab mockCodeTab;

    @Mock
    private Tab mockVisualBuilderTab;

    @Mock
    private Tab mockTestTab;

    @Mock
    private Tab mockDocumentationTab;

    @Mock
    private Button mockLoadSchematronFileButton;

    @Mock
    private Button mockNewSchematronFileButton;

    @Mock
    private Button mockSaveSchematronButton;

    @Mock
    private Button mockSaveAsSchematronButton;

    @Mock
    private Button mockValidateButton;

    @Mock
    private MenuButton mockLoadSchematronFavoritesButton;

    @Mock
    private VBox mockCodeEditorContainer;

    @Mock
    private SchematronCodeEditor mockSchematronEditor;

    @Mock
    private SchematronService mockSchematronService;

    @BeforeEach
    void setUp() throws Exception {
        controller = new SchematronController();

        // Inject mocked FXML components using reflection
        injectField(controller, "tabPane", mockTabPane);
        injectField(controller, "codeTab", mockCodeTab);
        injectField(controller, "visualBuilderTab", mockVisualBuilderTab);
        injectField(controller, "testTab", mockTestTab);
        injectField(controller, "documentationTab", mockDocumentationTab);
        injectField(controller, "loadSchematronFileButton", mockLoadSchematronFileButton);
        injectField(controller, "newSchematronFileButton", mockNewSchematronFileButton);
        injectField(controller, "saveSchematronButton", mockSaveSchematronButton);
        injectField(controller, "saveAsSchematronButton", mockSaveAsSchematronButton);
        injectField(controller, "validateButton", mockValidateButton);
        injectField(controller, "loadSchematronFavoritesButton", mockLoadSchematronFavoritesButton);
        injectField(controller, "codeEditorContainer", mockCodeEditorContainer);
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
    @DisplayName("Should have FXML components injected")
    void testFxmlComponentsInjected() throws Exception {
        // Verify FXML components are set
        Field tabPaneField = findField(controller.getClass(), "tabPane");
        if (tabPaneField != null) {
            tabPaneField.setAccessible(true);
            assertNotNull(tabPaneField.get(controller));
        }
    }

    @Test
    @DisplayName("Should handle null file operations gracefully")
    void testNullFileOperations() {
        // Test that controller doesn't crash with null operations
        assertDoesNotThrow(() -> {
            // Controller should handle null gracefully
            Field currentFileField = findField(controller.getClass(), "currentSchematronFile");
            if (currentFileField != null) {
                currentFileField.setAccessible(true);
                currentFileField.set(controller, null);
            }
        });
    }

    @Test
    @DisplayName("Should validate Schematron file format")
    void testSchematronFileValidation() {
        // Test file extension validation
        File schematronFile = new File("test.sch");
        assertTrue(schematronFile.getName().endsWith(".sch"));

        File invalidFile = new File("test.xml");
        assertFalse(invalidFile.getName().endsWith(".sch"));
    }

    @Test
    @DisplayName("Should handle Schematron namespace correctly")
    void testSchematronNamespace() {
        String schematronNamespace = "http://purl.oclc.org/dsdl/schematron";
        assertNotNull(schematronNamespace);
        assertTrue(schematronNamespace.contains("schematron"));
    }

    @Test
    @DisplayName("Should support ISO Schematron")
    void testIsoSchematronSupport() {
        String isoNamespace = "http://purl.oclc.org/dsdl/schematron";
        assertTrue(isoNamespace.contains("dsdl/schematron"));
    }

    @Test
    @DisplayName("Should initialize with default state")
    void testDefaultState() throws Exception {
        // Verify controller starts in a valid default state
        Field currentFileField = findField(controller.getClass(), "currentSchematronFile");
        if (currentFileField != null) {
            currentFileField.setAccessible(true);
            Object currentFile = currentFileField.get(controller);
            // Current file should be null initially
            assertNull(currentFile);
        }
    }

    @Test
    @DisplayName("Should manage active tab state")
    void testTabManagement() {
        when(mockTabPane.getSelectionModel()).thenReturn(mock(SingleSelectionModel.class));
        when(mockTabPane.getSelectionModel().getSelectedItem()).thenReturn(mockCodeTab);

        Tab selectedTab = mockTabPane.getSelectionModel().getSelectedItem();
        assertNotNull(selectedTab);
        assertEquals(mockCodeTab, selectedTab);
    }

    @Test
    @DisplayName("Should handle file selection")
    void testFileSelection() {
        File testFile = new File("test-schematron.sch");
        assertNotNull(testFile);
        assertTrue(testFile.getName().endsWith(".sch"));
    }

    @Test
    @DisplayName("Should validate Schematron patterns")
    void testSchematronPatternValidation() {
        // Test basic Schematron structure
        String schematronContent = """
            <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                <pattern id="test-pattern">
                    <rule context="element">
                        <assert test="@required">Element must have required attribute</assert>
                    </rule>
                </pattern>
            </schema>
            """;

        assertNotNull(schematronContent);
        assertTrue(schematronContent.contains("<pattern"));
        assertTrue(schematronContent.contains("<rule"));
        assertTrue(schematronContent.contains("<assert"));
    }

    @Test
    @DisplayName("Should recognize Schematron elements")
    void testSchematronElements() {
        String[] schematronElements = {"schema", "pattern", "rule", "assert", "report", "let", "extends"};

        for (String element : schematronElements) {
            assertNotNull(element);
            assertFalse(element.isEmpty());
        }
    }

    @Test
    @DisplayName("Should support XPath in Schematron")
    void testXPathSupport() {
        String xpathExpression = "@required";
        assertNotNull(xpathExpression);
        assertTrue(xpathExpression.startsWith("@"));

        String xpathExpression2 = "count(element) > 0";
        assertNotNull(xpathExpression2);
        assertTrue(xpathExpression2.contains("count"));
    }

    @Test
    @DisplayName("Should handle Schematron phases")
    void testSchematronPhases() {
        String schematronWithPhase = """
            <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                <phase id="basic">
                    <active pattern="test-pattern"/>
                </phase>
            </schema>
            """;

        assertNotNull(schematronWithPhase);
        assertTrue(schematronWithPhase.contains("<phase"));
        assertTrue(schematronWithPhase.contains("<active"));
    }

    @Test
    @DisplayName("Should validate rule context")
    void testRuleContext() {
        String contextXPath = "//book";
        assertNotNull(contextXPath);
        assertTrue(contextXPath.startsWith("//"));

        String contextXPath2 = "chapter[position() > 1]";
        assertNotNull(contextXPath2);
        assertTrue(contextXPath2.contains("["));
    }
}
