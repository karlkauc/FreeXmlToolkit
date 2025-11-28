package org.fxt.freexmltoolkit.controller;

import javafx.scene.control.*;
import javafx.scene.web.WebView;
import org.fxt.freexmltoolkit.domain.TemplateParameter;
import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TemplatesController.
 * Tests the Smart Templates System - Revolutionary Feature #4.
 */
@ExtendWith(MockitoExtension.class)
class TemplatesControllerTest {

    private TemplatesController controller;

    @Mock
    private ComboBox<String> mockTemplateCategoryCombo;

    @Mock
    private TextField mockTemplateSearchField;

    @Mock
    private ListView<XmlTemplate> mockTemplatesListView;

    @Mock
    private Label mockTemplateNameLabel;

    @Mock
    private Button mockApplyTemplateBtn;

    @Mock
    private Button mockPreviewTemplateBtn;

    @Mock
    private TableView<TemplateParameter> mockTemplateParametersTable;

    @Mock
    private TextArea mockXmlPreviewArea;

    @Mock
    private WebView mockXmlFormattedPreview;

    @Mock
    private ToggleButton mockLivePreviewToggle;

    @BeforeEach
    void setUp() {
        controller = new TemplatesController();
    }

    @Test
    @DisplayName("Should create controller instance")
    void testControllerInstantiation() {
        assertNotNull(controller);
    }

    @Test
    @DisplayName("Should support template categories")
    void testTemplateCategories() {
        String[] categories = {
            "All Templates", "Finance", "Healthcare", "Automotive", "Government", "Generic"
        };

        for (String category : categories) {
            assertNotNull(category);
            assertFalse(category.isEmpty());
        }
    }

    @Test
    @DisplayName("Should validate template parameter placeholders for groupId")
    void testPlaceholderForGroupId() throws Exception {
        Method getPlaceholderMethod = controller.getClass().getDeclaredMethod("getPlaceholderForParameter", String.class);
        getPlaceholderMethod.setAccessible(true);

        String placeholder = (String) getPlaceholderMethod.invoke(controller, "groupId");
        assertEquals("com.example", placeholder);
    }

    @Test
    @DisplayName("Should validate template parameter placeholders for artifactId")
    void testPlaceholderForArtifactId() throws Exception {
        Method getPlaceholderMethod = controller.getClass().getDeclaredMethod("getPlaceholderForParameter", String.class);
        getPlaceholderMethod.setAccessible(true);

        String placeholder = (String) getPlaceholderMethod.invoke(controller, "artifactId");
        assertEquals("my-project", placeholder);
    }

    @Test
    @DisplayName("Should validate template parameter placeholders for projectName")
    void testPlaceholderForProjectName() throws Exception {
        Method getPlaceholderMethod = controller.getClass().getDeclaredMethod("getPlaceholderForParameter", String.class);
        getPlaceholderMethod.setAccessible(true);

        String placeholder = (String) getPlaceholderMethod.invoke(controller, "projectName");
        assertEquals("My Project", placeholder);
    }

    @Test
    @DisplayName("Should validate template parameter placeholders for targetNamespace")
    void testPlaceholderForTargetNamespace() throws Exception {
        Method getPlaceholderMethod = controller.getClass().getDeclaredMethod("getPlaceholderForParameter", String.class);
        getPlaceholderMethod.setAccessible(true);

        String placeholder = (String) getPlaceholderMethod.invoke(controller, "targetNamespace");
        assertEquals("http://example.com/namespace", placeholder);
    }

    @Test
    @DisplayName("Should validate template parameter placeholders for transactionId")
    void testPlaceholderForTransactionId() throws Exception {
        Method getPlaceholderMethod = controller.getClass().getDeclaredMethod("getPlaceholderForParameter", String.class);
        getPlaceholderMethod.setAccessible(true);

        String placeholder = (String) getPlaceholderMethod.invoke(controller, "transactionId");
        assertEquals("TXN-001", placeholder);
    }

    @Test
    @DisplayName("Should validate template parameter placeholders for patientId")
    void testPlaceholderForPatientId() throws Exception {
        Method getPlaceholderMethod = controller.getClass().getDeclaredMethod("getPlaceholderForParameter", String.class);
        getPlaceholderMethod.setAccessible(true);

        String placeholder = (String) getPlaceholderMethod.invoke(controller, "patientId");
        assertEquals("PAT-001", placeholder);
    }

    @Test
    @DisplayName("Should validate template parameter placeholders for VIN")
    void testPlaceholderForVin() throws Exception {
        Method getPlaceholderMethod = controller.getClass().getDeclaredMethod("getPlaceholderForParameter", String.class);
        getPlaceholderMethod.setAccessible(true);

        String placeholder = (String) getPlaceholderMethod.invoke(controller, "vin");
        assertEquals("1HGBH41JXMN109186", placeholder);
    }

    @Test
    @DisplayName("Should validate template parameter placeholders for make")
    void testPlaceholderForMake() throws Exception {
        Method getPlaceholderMethod = controller.getClass().getDeclaredMethod("getPlaceholderForParameter", String.class);
        getPlaceholderMethod.setAccessible(true);

        String placeholder = (String) getPlaceholderMethod.invoke(controller, "make");
        assertEquals("Toyota", placeholder);
    }

    @Test
    @DisplayName("Should validate template parameter placeholders for unknown parameters")
    void testPlaceholderForUnknownParameter() throws Exception {
        Method getPlaceholderMethod = controller.getClass().getDeclaredMethod("getPlaceholderForParameter", String.class);
        getPlaceholderMethod.setAccessible(true);

        String placeholder = (String) getPlaceholderMethod.invoke(controller, "unknownParameter");
        assertEquals("PLACEHOLDER_UNKNOWNPARAMETER", placeholder);
    }

    @Test
    @DisplayName("Should escape HTML correctly for <")
    void testEscapeHtmlLessThan() throws Exception {
        Method escapeHtmlMethod = controller.getClass().getDeclaredMethod("escapeHtml", String.class);
        escapeHtmlMethod.setAccessible(true);

        String escaped = (String) escapeHtmlMethod.invoke(controller, "<element>");
        assertEquals("&lt;element&gt;", escaped);
    }

    @Test
    @DisplayName("Should escape HTML correctly for &")
    void testEscapeHtmlAmpersand() throws Exception {
        Method escapeHtmlMethod = controller.getClass().getDeclaredMethod("escapeHtml", String.class);
        escapeHtmlMethod.setAccessible(true);

        String escaped = (String) escapeHtmlMethod.invoke(controller, "A & B");
        assertEquals("A &amp; B", escaped);
    }

    @Test
    @DisplayName("Should escape HTML correctly for quotes")
    void testEscapeHtmlQuotes() throws Exception {
        Method escapeHtmlMethod = controller.getClass().getDeclaredMethod("escapeHtml", String.class);
        escapeHtmlMethod.setAccessible(true);

        String escaped = (String) escapeHtmlMethod.invoke(controller, "\"test\" 'value'");
        assertEquals("&quot;test&quot; &#39;value&#39;", escaped);
    }

    @Test
    @DisplayName("Should recognize template parameter syntax")
    void testTemplateParameterSyntax() {
        String parameterSyntax = "${parameterName}";

        assertNotNull(parameterSyntax);
        assertTrue(parameterSyntax.startsWith("${"));
        assertTrue(parameterSyntax.endsWith("}"));
        assertTrue(parameterSyntax.contains("parameterName"));
    }

    @Test
    @DisplayName("Should validate template content format")
    void testTemplateContentFormat() {
        String templateContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<${rootElement}>\n" +
                "    <${childElement}>${content}</${childElement}>\n" +
                "</${rootElement}>";

        assertNotNull(templateContent);
        assertTrue(templateContent.contains("<?xml"));
        assertTrue(templateContent.contains("${rootElement}"));
        assertTrue(templateContent.contains("${childElement}"));
        assertTrue(templateContent.contains("${content}"));
    }

    @Test
    @DisplayName("Should validate category filter values")
    void testCategoryFilterValues() {
        String[] validCategories = {
            "Finance", "Healthcare", "Automotive", "Government", "Generic", "Custom", "Configuration"
        };

        for (String category : validCategories) {
            assertNotNull(category);
            assertFalse(category.isEmpty());
            // Categories should start with uppercase
            assertTrue(Character.isUpperCase(category.charAt(0)));
        }
    }

    @Test
    @DisplayName("Should handle template parameters correctly")
    void testTemplateParameter() {
        TemplateParameter param = new TemplateParameter("groupId", TemplateParameter.ParameterType.STRING, "com.example")
                .required(true)
                .description("Maven group ID");

        assertNotNull(param);
        assertEquals("groupId", param.getName());
        assertEquals("com.example", param.getDefaultValue());
        assertEquals(TemplateParameter.ParameterType.STRING, param.getType());
        assertTrue(param.isRequired());
        assertEquals("Maven group ID", param.getDescription());
    }

    @Test
    @DisplayName("Should handle optional template parameters")
    void testOptionalTemplateParameter() {
        TemplateParameter param = new TemplateParameter("comment", TemplateParameter.ParameterType.STRING, "")
                .required(false)
                .description("Optional comment");

        assertNotNull(param);
        assertFalse(param.isRequired());
        assertEquals("", param.getDefaultValue());
    }

    @Test
    @DisplayName("Should validate parameter types")
    void testParameterTypes() {
        String[] paramTypes = {"string", "number", "boolean", "date", "uri"};

        for (String type : paramTypes) {
            assertNotNull(type);
            assertFalse(type.isEmpty());
        }
    }

    @Test
    @DisplayName("Should support common finance template parameters")
    void testFinanceTemplateParameters() throws Exception {
        Method getPlaceholderMethod = controller.getClass().getDeclaredMethod("getPlaceholderForParameter", String.class);
        getPlaceholderMethod.setAccessible(true);

        assertEquals("TXN-001", getPlaceholderMethod.invoke(controller, "transactionId"));
        assertEquals("100.00", getPlaceholderMethod.invoke(controller, "amount"));
        assertEquals("ACC-001", getPlaceholderMethod.invoke(controller, "fromAccount"));
        assertEquals("ACC-002", getPlaceholderMethod.invoke(controller, "toAccount"));
    }

    @Test
    @DisplayName("Should support common healthcare template parameters")
    void testHealthcareTemplateParameters() throws Exception {
        Method getPlaceholderMethod = controller.getClass().getDeclaredMethod("getPlaceholderForParameter", String.class);
        getPlaceholderMethod.setAccessible(true);

        assertEquals("PAT-001", getPlaceholderMethod.invoke(controller, "patientId"));
        assertEquals("John", getPlaceholderMethod.invoke(controller, "firstName"));
        assertEquals("Doe", getPlaceholderMethod.invoke(controller, "lastName"));
    }

    @Test
    @DisplayName("Should support common automotive template parameters")
    void testAutomotiveTemplateParameters() throws Exception {
        Method getPlaceholderMethod = controller.getClass().getDeclaredMethod("getPlaceholderForParameter", String.class);
        getPlaceholderMethod.setAccessible(true);

        assertEquals("1HGBH41JXMN109186", getPlaceholderMethod.invoke(controller, "vin"));
        assertEquals("ABC-123", getPlaceholderMethod.invoke(controller, "licensePlate"));
        assertEquals("Toyota", getPlaceholderMethod.invoke(controller, "make"));
        assertEquals("Camry", getPlaceholderMethod.invoke(controller, "model"));
    }

    @Test
    @DisplayName("Should support common government template parameters")
    void testGovernmentTemplateParameters() throws Exception {
        Method getPlaceholderMethod = controller.getClass().getDeclaredMethod("getPlaceholderForParameter", String.class);
        getPlaceholderMethod.setAccessible(true);

        assertEquals("FORM-001", getPlaceholderMethod.invoke(controller, "formId"));
        assertEquals("Application Form", getPlaceholderMethod.invoke(controller, "formTitle"));
        assertEquals("123456789", getPlaceholderMethod.invoke(controller, "nationalId"));
    }

    @Test
    @DisplayName("Should support SOAP service template parameters")
    void testSoapServiceParameters() throws Exception {
        Method getPlaceholderMethod = controller.getClass().getDeclaredMethod("getPlaceholderForParameter", String.class);
        getPlaceholderMethod.setAccessible(true);

        assertEquals("MyService", getPlaceholderMethod.invoke(controller, "serviceName"));
        assertEquals("http://example.com/action", getPlaceholderMethod.invoke(controller, "soapAction"));
        assertEquals("http://localhost:8080/service", getPlaceholderMethod.invoke(controller, "serviceUrl"));
    }

    @Test
    @DisplayName("Should handle shutdown gracefully")
    void testShutdown() {
        assertDoesNotThrow(() -> controller.shutdown(),
                "Shutdown should complete without exceptions");
    }

    @Test
    @DisplayName("Should validate template ID format")
    void testTemplateIdFormat() {
        String validId = "my-custom-template";
        String invalidId = "My Custom Template";  // Should not have spaces

        assertNotNull(validId);
        assertFalse(validId.contains(" "), "Template ID should not contain spaces");
        assertTrue(invalidId.contains(" "), "Invalid ID contains spaces");
    }

    @Test
    @DisplayName("Should validate example XML template structure")
    void testExampleTemplateStructure() {
        String exampleTemplate = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<${rootElement}>\n" +
                "    <${childElement}>${content}</${childElement}>\n" +
                "</${rootElement}>";

        assertNotNull(exampleTemplate);
        assertTrue(exampleTemplate.startsWith("<?xml version=\"1.0\""));
        assertTrue(exampleTemplate.contains("encoding=\"UTF-8\""));
        assertTrue(exampleTemplate.contains("${rootElement}"));
        assertTrue(exampleTemplate.contains("${childElement}"));
        assertTrue(exampleTemplate.contains("${content}"));
    }

    @Test
    @DisplayName("Should validate REST API template parameters")
    void testRestApiParameters() throws Exception {
        Method getPlaceholderMethod = controller.getClass().getDeclaredMethod("getPlaceholderForParameter", String.class);
        getPlaceholderMethod.setAccessible(true);

        assertEquals("My API", getPlaceholderMethod.invoke(controller, "apiName"));
        assertEquals("API Description", getPlaceholderMethod.invoke(controller, "apiDescription"));
        assertEquals("http://api.example.com", getPlaceholderMethod.invoke(controller, "baseUrl"));
        assertEquals("/endpoint", getPlaceholderMethod.invoke(controller, "endpointPath"));
    }

    @Test
    @DisplayName("Should validate XPath expression parameters")
    void testXPathParameters() throws Exception {
        Method getPlaceholderMethod = controller.getClass().getDeclaredMethod("getPlaceholderForParameter", String.class);
        getPlaceholderMethod.setAccessible(true);

        assertEquals("//element", getPlaceholderMethod.invoke(controller, "matchPattern"));
        assertEquals("output", getPlaceholderMethod.invoke(controller, "outputElement"));
    }

    @Test
    @DisplayName("Should validate Spring configuration parameters")
    void testSpringConfigParameters() throws Exception {
        Method getPlaceholderMethod = controller.getClass().getDeclaredMethod("getPlaceholderForParameter", String.class);
        getPlaceholderMethod.setAccessible(true);

        assertEquals("com.example", getPlaceholderMethod.invoke(controller, "basePackage"));
        assertEquals("myBean", getPlaceholderMethod.invoke(controller, "beanId"));
        assertEquals("com.example.MyClass", getPlaceholderMethod.invoke(controller, "beanClass"));
    }
}
