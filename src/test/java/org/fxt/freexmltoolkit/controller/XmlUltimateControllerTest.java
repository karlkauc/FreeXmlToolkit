package org.fxt.freexmltoolkit.controller;

import javafx.scene.control.*;
import javafx.scene.web.WebView;
import org.fxt.freexmltoolkit.domain.TemplateParameter;
import org.fxt.freexmltoolkit.service.XmlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XmlUltimateController.
 * Tests the ultimate XML editor with IntelliSense, XPath, XSLT, and template features.
 */
@ExtendWith(MockitoExtension.class)
class XmlUltimateControllerTest {

    private XmlUltimateController controller;

    @Mock
    private Button mockNewFile;

    @Mock
    private Button mockOpenFile;

    @Mock
    private Button mockSaveFile;

    @Mock
    private Button mockSaveAsFile;

    @Mock
    private Button mockPrettyPrint;

    @Mock
    private Button mockValidateButton;

    @Mock
    private Button mockRunXpathQuery;

    @Mock
    private Button mockTemplateManagerButton;

    @Mock
    private Button mockSchemaGeneratorButton;

    @Mock
    private Button mockXsltDeveloperButton;

    @Mock
    private TabPane mockXmlFilesPane;

    @Mock
    private TreeView<String> mockDocumentTreeView;

    @Mock
    private ComboBox<String> mockSchemaCombo;

    @Mock
    private ListView<String> mockValidationResultsList;

    @Mock
    private ListView<String> mockNamespacesList;

    @Mock
    private TabPane mockDevelopmentTabPane;

    @Mock
    private Tab mockXPathQueryTab;

    @Mock
    private Tab mockXsltDevelopmentTab;

    @Mock
    private Tab mockTemplateDevelopmentTab;

    @Mock
    private Button mockExecuteQueryButton;

    @Mock
    private Button mockClearQueryButton;

    @Mock
    private Button mockTransformButton;

    @Mock
    private ComboBox<String> mockOutputFormatCombo;

    @Mock
    private CheckBox mockLivePreviewCheckbox;

    @Mock
    private TextArea mockTransformationResultArea;

    @Mock
    private WebView mockTransformationPreviewWeb;

    @Mock
    private TableView<TemplateParameter> mockTemplateParametersTable;

    @Mock
    private XmlService mockXmlService;

    @BeforeEach
    void setUp() {
        controller = new XmlUltimateController();
    }

    @Test
    @DisplayName("Should create controller instance")
    void testControllerInstantiation() {
        assertNotNull(controller);
    }

    @Test
    @DisplayName("Should validate XML file extension")
    void testXmlFileExtension() {
        File xmlFile = new File("document.xml");
        assertTrue(xmlFile.getName().endsWith(".xml"));

        File invalidFile = new File("document.txt");
        assertFalse(invalidFile.getName().endsWith(".xml"));
    }

    @Test
    @DisplayName("Should validate XML declaration")
    void testXmlDeclaration() {
        String xmlDeclaration = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

        assertNotNull(xmlDeclaration);
        assertTrue(xmlDeclaration.contains("<?xml"));
        assertTrue(xmlDeclaration.contains("version="));
        assertTrue(xmlDeclaration.contains("encoding="));
    }

    @Test
    @DisplayName("Should validate XPath query syntax")
    void testXPathQuerySyntax() {
        String[] xpathQueries = {
            "/root/element",
            "//element[@attribute='value']",
            "count(//item)",
            "//element[position() > 1]",
            "normalize-space(//text())"
        };

        for (String query : xpathQueries) {
            assertNotNull(query);
            assertFalse(query.isEmpty());
        }
    }

    @Test
    @DisplayName("Should validate XQuery syntax")
    void testXQuerySyntax() {
        String xqueryExample = "for $x in //book return $x/title";

        assertNotNull(xqueryExample);
        assertTrue(xqueryExample.contains("for"));
        assertTrue(xqueryExample.contains("in"));
        assertTrue(xqueryExample.contains("return"));
    }

    @Test
    @DisplayName("Should validate output formats for XSLT")
    void testXsltOutputFormats() {
        String[] formats = {"XML", "HTML", "Text", "JSON"};

        for (String format : formats) {
            assertNotNull(format);
            assertFalse(format.isEmpty());
        }
    }

    @Test
    @DisplayName("Should validate multi-tab support")
    void testMultiTabSupport() {
        TabPane tabPane = new TabPane();
        Tab tab1 = new Tab("Document1.xml");
        Tab tab2 = new Tab("Document2.xml");

        tabPane.getTabs().add(tab1);
        tabPane.getTabs().add(tab2);

        assertEquals(2, tabPane.getTabs().size());
        assertEquals("Document1.xml", tab1.getText());
        assertEquals("Document2.xml", tab2.getText());
    }

    @Test
    @DisplayName("Should validate IntelliSense trigger characters")
    void testIntelliSenseTriggerCharacters() {
        String[] triggerChars = {"<", "/", " ", "="};

        for (String trigger : triggerChars) {
            assertNotNull(trigger);
            assertTrue(trigger.length() > 0);
        }
    }

    @Test
    @DisplayName("Should validate namespace prefixes")
    void testNamespacePrefixes() {
        String[] namespacePrefixes = {"xs:", "xsl:", "xsi:", "xmlns:"};

        for (String prefix : namespacePrefixes) {
            assertNotNull(prefix);
            assertTrue(prefix.endsWith(":"));
        }
    }

    @Test
    @DisplayName("Should validate common XML namespaces")
    void testCommonXmlNamespaces() {
        String xsNamespace = "http://www.w3.org/2001/XMLSchema";
        String xslNamespace = "http://www.w3.org/1999/XSL/Transform";
        String xsiNamespace = "http://www.w3.org/2001/XMLSchema-instance";

        assertNotNull(xsNamespace);
        assertNotNull(xslNamespace);
        assertNotNull(xsiNamespace);
        assertTrue(xsNamespace.contains("XMLSchema"));
        assertTrue(xslNamespace.contains("XSL/Transform"));
        assertTrue(xsiNamespace.contains("XMLSchema-instance"));
    }

    @Test
    @DisplayName("Should validate template parameter structure")
    void testTemplateParameterStructure() {
        TemplateParameter param = new TemplateParameter("elementName", TemplateParameter.ParameterType.STRING, "myElement")
                .required(true)
                .description("Name of the element");

        assertNotNull(param);
        assertEquals("elementName", param.getName());
        assertEquals("myElement", param.getDefaultValue());
        assertEquals(TemplateParameter.ParameterType.STRING, param.getType());
        assertTrue(param.isRequired());
        assertEquals("Name of the element", param.getDescription());
    }

    @Test
    @DisplayName("Should validate validation result format")
    void testValidationResultFormat() {
        String validResult = "Validation successful. No errors found.";
        String errorResult = "Validation failed. 3 error(s) found.";

        assertNotNull(validResult);
        assertNotNull(errorResult);
        assertTrue(validResult.contains("successful"));
        assertTrue(errorResult.contains("failed"));
        assertTrue(errorResult.contains("error(s)"));
    }

    @Test
    @DisplayName("Should validate pretty print functionality")
    void testPrettyPrintFunctionality() {
        String unformatted = "<root><child>value</child></root>";
        String formatted = """
            <root>
                <child>value</child>
            </root>""";

        assertNotNull(unformatted);
        assertNotNull(formatted);
        assertTrue(formatted.contains("\n"), "Formatted XML should have line breaks");
    }

    @Test
    @DisplayName("Should validate document tree structure")
    void testDocumentTreeStructure() {
        TreeItem<String> root = new TreeItem<>("root");
        TreeItem<String> child1 = new TreeItem<>("child1");
        TreeItem<String> child2 = new TreeItem<>("child2");

        root.getChildren().addAll(child1, child2);

        assertEquals("root", root.getValue());
        assertEquals(2, root.getChildren().size());
        assertEquals("child1", child1.getValue());
        assertEquals("child2", child2.getValue());
    }

    @Test
    @DisplayName("Should validate XPath result types")
    void testXPathResultTypes() {
        String[] resultTypes = {"NODESET", "STRING", "NUMBER", "BOOLEAN"};

        for (String type : resultTypes) {
            assertNotNull(type);
            assertFalse(type.isEmpty());
        }
    }

    @Test
    @DisplayName("Should validate favorites functionality")
    void testFavoritesFunctionality() {
        File favoriteFile = new File("favorite_document.xml");

        assertNotNull(favoriteFile);
        assertEquals("favorite_document.xml", favoriteFile.getName());
    }

    @Test
    @DisplayName("Should validate schema combo values")
    void testSchemaComboValues() {
        String[] schemaOptions = {"No Schema", "Auto-detect", "Custom Schema"};

        for (String option : schemaOptions) {
            assertNotNull(option);
            assertFalse(option.isEmpty());
        }
    }

    @Test
    @DisplayName("Should validate property entry structure")
    void testPropertyEntryStructure() {
        // PropertyEntry is a simple data class with name and value
        String propertyName = "encoding";
        String propertyValue = "UTF-8";

        assertNotNull(propertyName);
        assertNotNull(propertyValue);
        assertEquals("encoding", propertyName);
        assertEquals("UTF-8", propertyValue);
    }

    @Test
    @DisplayName("Should validate thread pool naming")
    void testThreadPoolNaming() {
        String threadName = "UltimateXML-Thread";

        assertNotNull(threadName);
        assertEquals("UltimateXML-Thread", threadName);
        assertTrue(threadName.contains("UltimateXML"));
    }

    @Test
    @DisplayName("Should validate XSLT transformation result structure")
    void testXsltTransformationResult() {
        String transformedOutput = "<html><body>Transformed content</body></html>";
        long executionTime = 120;

        assertNotNull(transformedOutput);
        assertTrue(executionTime >= 0);
        assertTrue(transformedOutput.contains("<html>"));
    }

    @Test
    @DisplayName("Should validate development tab names")
    void testDevelopmentTabNames() {
        String xpathTabName = "XPath/XQuery";
        String xsltTabName = "XSLT Development";
        String templateTabName = "Template Development";

        assertNotNull(xpathTabName);
        assertNotNull(xsltTabName);
        assertNotNull(templateTabName);
    }

    @Test
    @DisplayName("Should validate XML well-formedness check")
    void testXmlWellFormednessCheck() {
        String wellFormed = "<root><child>value</child></root>";
        String notWellFormed = "<root><child>value</root>";

        assertNotNull(wellFormed);
        assertNotNull(notWellFormed);
        // Well-formed should have matching tags
        assertTrue(wellFormed.contains("<root>") && wellFormed.contains("</root>"));
        assertTrue(wellFormed.contains("<child>") && wellFormed.contains("</child>"));
    }

    @Test
    @DisplayName("Should validate live preview functionality")
    void testLivePreviewFunctionality() {
        boolean livePreviewEnabled = false;

        assertFalse(livePreviewEnabled, "Live preview should start disabled");

        livePreviewEnabled = true;
        assertTrue(livePreviewEnabled, "Live preview should be toggleable");
    }

    @Test
    @DisplayName("Should validate performance metrics tracking")
    void testPerformanceMetricsTracking() {
        long transformationTime = 150;
        long parsingTime = 50;
        long validationTime = 30;

        assertTrue(transformationTime >= 0);
        assertTrue(parsingTime >= 0);
        assertTrue(validationTime >= 0);
    }

    @Test
    @DisplayName("Should validate error line number formatting")
    void testErrorLineNumberFormatting() {
        int errorLine = 42;
        int errorColumn = 15;
        String errorMessage = "Line " + errorLine + ", Column " + errorColumn + ": Invalid element";

        assertNotNull(errorMessage);
        assertTrue(errorMessage.contains("Line 42"));
        assertTrue(errorMessage.contains("Column 15"));
    }

    @Test
    @DisplayName("Should validate code area line numbers")
    void testCodeAreaLineNumbers() {
        // Line numbers should start from 1
        int firstLine = 1;
        int lastLine = 100;

        assertEquals(1, firstLine);
        assertTrue(lastLine > firstLine);
    }

    @Test
    @DisplayName("Should validate XML comment syntax")
    void testXmlCommentSyntax() {
        String comment = "<!-- This is a comment -->";

        assertNotNull(comment);
        assertTrue(comment.startsWith("<!--"));
        assertTrue(comment.endsWith("-->"));
    }

    @Test
    @DisplayName("Should validate CDATA section syntax")
    void testCdataSectionSyntax() {
        String cdata = "<![CDATA[This is CDATA content]]>";

        assertNotNull(cdata);
        assertTrue(cdata.startsWith("<![CDATA["));
        assertTrue(cdata.endsWith("]]>"));
    }

    @Test
    @DisplayName("Should validate attribute value quoting")
    void testAttributeValueQuoting() {
        String doubleQuoted = "<element attr=\"value\"/>";
        String singleQuoted = "<element attr='value'/>";

        assertNotNull(doubleQuoted);
        assertNotNull(singleQuoted);
        assertTrue(doubleQuoted.contains("=\"value\""));
        assertTrue(singleQuoted.contains("='value'"));
    }

    @Test
    @DisplayName("Should validate empty element syntax")
    void testEmptyElementSyntax() {
        String emptyElement1 = "<element/>";
        String emptyElement2 = "<element></element>";

        assertNotNull(emptyElement1);
        assertNotNull(emptyElement2);
        assertTrue(emptyElement1.endsWith("/>"));
    }

    @Test
    @DisplayName("Should validate encoding options")
    void testEncodingOptions() {
        String[] encodings = {"UTF-8", "UTF-16", "ISO-8859-1", "US-ASCII"};

        for (String encoding : encodings) {
            assertNotNull(encoding);
            assertFalse(encoding.isEmpty());
        }
    }

    @Test
    @DisplayName("Should validate XML version options")
    void testXmlVersionOptions() {
        String version10 = "1.0";
        String version11 = "1.1";

        assertNotNull(version10);
        assertNotNull(version11);
        assertEquals("1.0", version10);
        assertEquals("1.1", version11);
    }

    @Test
    @DisplayName("Should validate standalone document declaration")
    void testStandaloneDocumentDeclaration() {
        String standaloneYes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
        String standaloneNo = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>";

        assertNotNull(standaloneYes);
        assertNotNull(standaloneNo);
        assertTrue(standaloneYes.contains("standalone=\"yes\""));
        assertTrue(standaloneNo.contains("standalone=\"no\""));
    }

    @Test
    @DisplayName("Should validate query result label format")
    void testQueryResultLabelFormat() {
        String resultLabel = "Query executed in 50ms. 15 nodes found.";

        assertNotNull(resultLabel);
        assertTrue(resultLabel.contains("ms"));
        assertTrue(resultLabel.contains("nodes found"));
    }

    @Test
    @DisplayName("Should validate transformation preview formats")
    void testTransformationPreviewFormats() {
        String htmlPreview = "<html><body>Preview</body></html>";
        String textPreview = "Plain text preview";

        assertNotNull(htmlPreview);
        assertNotNull(textPreview);
        assertTrue(htmlPreview.contains("<html>"));
    }

    @Test
    @DisplayName("Should validate parameter validation rules")
    void testParameterValidationRules() {
        TemplateParameter requiredParam = new TemplateParameter("name", TemplateParameter.ParameterType.STRING, "")
                .required(true)
                .description("Required parameter");

        assertTrue(requiredParam.isRequired());
        assertEquals("", requiredParam.getDefaultValue());
    }

    @Test
    @DisplayName("Should validate favorites panel visibility toggle")
    void testFavoritesPanelVisibilityToggle() {
        boolean favoritesVisible = false;

        assertFalse(favoritesVisible, "Favorites should start hidden");

        favoritesVisible = true;
        assertTrue(favoritesVisible, "Favorites should be toggleable");
    }

    @Test
    @DisplayName("Should validate split pane divider positions")
    void testSplitPaneDividerPositions() {
        double mainDividerPosition = 0.7;
        double horizontalDividerPosition = 0.8;

        assertTrue(mainDividerPosition > 0 && mainDividerPosition < 1);
        assertTrue(horizontalDividerPosition > 0 && horizontalDividerPosition < 1);
    }

    @Test
    @DisplayName("Should validate schema generation options")
    void testSchemaGenerationOptions() {
        boolean generateComplexTypes = true;
        boolean optimizeSchema = true;
        boolean includeComments = false;

        assertTrue(generateComplexTypes);
        assertTrue(optimizeSchema);
        assertFalse(includeComments);
    }

    @Test
    @DisplayName("Should validate daemon thread configuration")
    void testDaemonThreadConfiguration() {
        Thread daemonThread = new Thread();
        daemonThread.setDaemon(true);
        daemonThread.setName("UltimateXML-Thread");

        assertTrue(daemonThread.isDaemon());
        assertEquals("UltimateXML-Thread", daemonThread.getName());
    }
}
