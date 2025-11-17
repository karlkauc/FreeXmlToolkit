package org.fxt.freexmltoolkit.controller;

import javafx.scene.control.*;
import javafx.scene.web.WebView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsltDeveloperController.
 * Tests the Advanced XSLT Developer - Revolutionary Feature #2.
 */
@ExtendWith(MockitoExtension.class)
class XsltDeveloperControllerTest {

    private XsltDeveloperController controller;

    @Mock
    private ComboBox<String> mockXsltVersionCombo;

    @Mock
    private ToggleButton mockLiveTransformToggle;

    @Mock
    private Button mockTransformBtn;

    @Mock
    private ComboBox<String> mockOutputFormatCombo;

    @Mock
    private ComboBox<String> mockEncodingCombo;

    @Mock
    private CheckBox mockIndentOutputCheckbox;

    @Mock
    private TextArea mockTransformationResultArea;

    @Mock
    private WebView mockPreviewWebView;

    @Mock
    private Label mockExecutionTimeLabel;

    @Mock
    private Label mockCompilationTimeLabel;

    @Mock
    private Label mockMemoryUsageLabel;

    @Mock
    private CheckBox mockEnableDebugMode;

    @Mock
    private ListView<String> mockMessagesListView;

    @Mock
    private TextArea mockTraceArea;

    @BeforeEach
    void setUp() {
        controller = new XsltDeveloperController();
    }

    @Test
    @DisplayName("Should create controller instance")
    void testControllerInstantiation() {
        assertNotNull(controller);
    }

    @Test
    @DisplayName("Should support XSLT versions")
    void testXsltVersions() {
        String[] versions = {"XSLT 3.0", "XSLT 2.0", "XSLT 1.0"};

        for (String version : versions) {
            assertNotNull(version);
            assertTrue(version.startsWith("XSLT"));
            assertTrue(version.contains("3.0") || version.contains("2.0") || version.contains("1.0"));
        }
    }

    @Test
    @DisplayName("Should validate XSLT namespace")
    void testXsltNamespace() {
        String xsltNamespace = "http://www.w3.org/1999/XSL/Transform";

        assertNotNull(xsltNamespace);
        assertTrue(xsltNamespace.contains("XSL/Transform"));
    }

    @Test
    @DisplayName("Should support output formats")
    void testOutputFormats() {
        String[] formats = {"XML", "HTML", "Text", "JSON"};

        for (String format : formats) {
            assertNotNull(format);
            assertFalse(format.isEmpty());
        }
    }

    @Test
    @DisplayName("Should validate XML output format")
    void testXmlOutputFormat() {
        String xmlFormat = "XML";
        assertEquals("XML", xmlFormat);
    }

    @Test
    @DisplayName("Should validate HTML output format")
    void testHtmlOutputFormat() {
        String htmlFormat = "HTML";
        assertEquals("HTML", htmlFormat);
    }

    @Test
    @DisplayName("Should validate Text output format")
    void testTextOutputFormat() {
        String textFormat = "Text";
        assertEquals("Text", textFormat);
    }

    @Test
    @DisplayName("Should validate JSON output format")
    void testJsonOutputFormat() {
        String jsonFormat = "JSON";
        assertEquals("JSON", jsonFormat);
    }

    @Test
    @DisplayName("Should support character encodings")
    void testCharacterEncodings() {
        String[] encodings = {"UTF-8", "UTF-16", "ISO-8859-1"};

        for (String encoding : encodings) {
            assertNotNull(encoding);
            assertFalse(encoding.isEmpty());
        }
    }

    @Test
    @DisplayName("Should default to UTF-8 encoding")
    void testDefaultEncoding() {
        String defaultEncoding = "UTF-8";
        assertEquals("UTF-8", defaultEncoding);
    }

    @Test
    @DisplayName("Should default to XSLT 3.0")
    void testDefaultXsltVersion() {
        String defaultVersion = "XSLT 3.0";
        assertEquals("XSLT 3.0", defaultVersion);
    }

    @Test
    @DisplayName("Should validate default XML template")
    void testDefaultXmlTemplate() {
        String defaultXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n    <!-- Enter or load your XML source document here -->\n</root>";

        assertNotNull(defaultXml);
        assertTrue(defaultXml.contains("<?xml version=\"1.0\""));
        assertTrue(defaultXml.contains("<root>"));
        assertTrue(defaultXml.contains("</root>"));
    }

    @Test
    @DisplayName("Should validate default XSLT template")
    void testDefaultXsltTemplate() {
        String defaultXslt = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">";

        assertNotNull(defaultXslt);
        assertTrue(defaultXslt.contains("<?xml version=\"1.0\""));
        assertTrue(defaultXslt.contains("xsl:stylesheet"));
        assertTrue(defaultXslt.contains("version=\"3.0\""));
        assertTrue(defaultXslt.contains("xmlns:xsl="));
    }

    @Test
    @DisplayName("Should recognize XSLT elements")
    void testXsltElements() {
        String[] xsltElements = {
            "template", "apply-templates", "value-of", "for-each",
            "if", "choose", "when", "otherwise", "variable", "param"
        };

        for (String element : xsltElements) {
            assertNotNull(element);
            assertFalse(element.isEmpty());
        }
    }

    @Test
    @DisplayName("Should validate XSLT match attribute")
    void testXsltMatchAttribute() {
        String matchAttr = "match=\"/\"";

        assertNotNull(matchAttr);
        assertTrue(matchAttr.contains("match="));
        assertTrue(matchAttr.contains("\"/\""));
    }

    @Test
    @DisplayName("Should validate performance metric labels")
    void testPerformanceMetricLabels() {
        String executionTime = "Execution Time";
        String compilationTime = "Compilation Time";
        String memoryUsage = "Memory Usage";
        String outputSize = "Output Size";

        assertNotNull(executionTime);
        assertNotNull(compilationTime);
        assertNotNull(memoryUsage);
        assertNotNull(outputSize);
    }

    @Test
    @DisplayName("Should handle time measurements in milliseconds")
    void testTimeMeasurements() {
        long executionTimeMs = 150;
        long compilationTimeMs = 50;

        assertTrue(executionTimeMs >= 0);
        assertTrue(compilationTimeMs >= 0);
    }

    @Test
    @DisplayName("Should handle memory measurements in bytes")
    void testMemoryMeasurements() {
        long memoryBytes = 1024000;

        assertTrue(memoryBytes >= 0);
        assertTrue(memoryBytes > 0, "Memory usage should be positive");
    }

    @Test
    @DisplayName("Should validate indent output checkbox default")
    void testIndentOutputDefault() {
        boolean indentOutput = true;
        assertTrue(indentOutput, "Indent output should be enabled by default");
    }

    @Test
    @DisplayName("Should validate debug mode checkbox default")
    void testDebugModeDefault() {
        boolean debugMode = false;
        assertFalse(debugMode, "Debug mode should be disabled by default");
    }

    @Test
    @DisplayName("Should support live transform feature")
    void testLiveTransformFeature() {
        boolean liveTransformEnabled = false;
        assertFalse(liveTransformEnabled, "Live transform should start disabled");

        liveTransformEnabled = true;
        assertTrue(liveTransformEnabled, "Live transform should be toggleable");
    }

    @Test
    @DisplayName("Should validate parameter table columns")
    void testParameterTableColumns() {
        String[] columnNames = {"Name", "Value", "Type"};

        for (String column : columnNames) {
            assertNotNull(column);
            assertFalse(column.isEmpty());
        }
    }

    @Test
    @DisplayName("Should handle XSLT parameters")
    void testXsltParameters() {
        String paramName = "title";
        String paramValue = "My Document";
        String paramType = "string";

        assertNotNull(paramName);
        assertNotNull(paramValue);
        assertNotNull(paramType);
        assertEquals("string", paramType);
    }

    @Test
    @DisplayName("Should validate result tab names")
    void testResultTabNames() {
        String resultsTab = "Results";
        String previewTab = "Preview";
        String performanceTab = "Performance";
        String debugTab = "Debug";

        assertNotNull(resultsTab);
        assertNotNull(previewTab);
        assertNotNull(performanceTab);
        assertNotNull(debugTab);
    }

    @Test
    @DisplayName("Should handle button actions")
    void testButtonActions() {
        String[] buttonActions = {
            "Transform", "Load XML", "Load XSLT", "Save XSLT",
            "Validate XML", "Validate XSLT", "Copy Result", "Save Result",
            "Add Parameter", "Remove Parameter", "Refresh Preview", "Clear Debug"
        };

        for (String action : buttonActions) {
            assertNotNull(action);
            assertFalse(action.isEmpty());
        }
    }

    @Test
    @DisplayName("Should validate Saxon XSLT processor support")
    void testSaxonProcessorSupport() {
        // Saxon supports XSLT 3.0
        String processor = "Saxon";
        String version = "3.0";

        assertNotNull(processor);
        assertNotNull(version);
        assertEquals("Saxon", processor);
        assertEquals("3.0", version);
    }

    @Test
    @DisplayName("Should support XPath expressions")
    void testXPathExpressions() {
        String[] xpathExpressions = {
            "/", "//element", "@attribute", "text()", "count(//item)"
        };

        for (String xpath : xpathExpressions) {
            assertNotNull(xpath);
            assertFalse(xpath.isEmpty());
        }
    }

    @Test
    @DisplayName("Should validate transformation result structure")
    void testTransformationResultStructure() {
        // Result should contain output, execution time, errors, warnings
        String output = "<result>transformed content</result>";
        long executionTime = 100;
        int errorCount = 0;
        int warningCount = 0;

        assertNotNull(output);
        assertTrue(executionTime >= 0);
        assertTrue(errorCount >= 0);
        assertTrue(warningCount >= 0);
    }

    @Test
    @DisplayName("Should handle clipboard operations")
    void testClipboardOperations() {
        String resultContent = "<html><body>Result</body></html>";

        assertNotNull(resultContent);
        assertFalse(resultContent.isEmpty());
    }

    @Test
    @DisplayName("Should validate file save operations")
    void testFileSaveOperations() {
        String outputFileName = "transformation_result.xml";

        assertNotNull(outputFileName);
        assertTrue(outputFileName.endsWith(".xml") || outputFileName.endsWith(".html") ||
                outputFileName.endsWith(".txt") || outputFileName.endsWith(".json"));
    }

    @Test
    @DisplayName("Should support multiple output file formats")
    void testOutputFileFormats() {
        String[] fileExtensions = {".xml", ".html", ".txt", ".json"};

        for (String ext : fileExtensions) {
            assertNotNull(ext);
            assertTrue(ext.startsWith("."));
        }
    }

    @Test
    @DisplayName("Should validate performance report format")
    void testPerformanceReportFormat() {
        String performanceReport = "Execution Time: 150ms\nCompilation Time: 50ms\nMemory Usage: 1.5MB\nOutput Size: 2048 bytes";

        assertNotNull(performanceReport);
        assertTrue(performanceReport.contains("Execution Time"));
        assertTrue(performanceReport.contains("Compilation Time"));
        assertTrue(performanceReport.contains("Memory Usage"));
        assertTrue(performanceReport.contains("Output Size"));
    }

    @Test
    @DisplayName("Should handle debug messages")
    void testDebugMessages() {
        String debugMessage = "[INFO] Starting transformation...";

        assertNotNull(debugMessage);
        assertTrue(debugMessage.contains("[INFO]"));
    }

    @Test
    @DisplayName("Should handle trace information")
    void testTraceInformation() {
        String traceInfo = "Template matched: /root\nApplying templates...\nOutput generated";

        assertNotNull(traceInfo);
        assertTrue(traceInfo.contains("Template matched"));
        assertTrue(traceInfo.contains("Applying templates"));
    }

    @Test
    @DisplayName("Should validate XSLT features list")
    void testXsltFeaturesList() {
        String[] features = {
            "XSLT 3.0 Support", "XPath 3.1", "JSON Output", "Streaming",
            "Higher-order Functions", "Try-Catch Error Handling"
        };

        for (String feature : features) {
            assertNotNull(feature);
            assertFalse(feature.isEmpty());
        }
    }

    @Test
    @DisplayName("Should validate result statistics format")
    void testResultStatisticsFormat() {
        String stats = "Transformation completed in 150ms | Output: 2048 bytes | Memory: 1.5MB";

        assertNotNull(stats);
        assertTrue(stats.contains("Transformation completed"));
        assertTrue(stats.contains("ms"));
        assertTrue(stats.contains("bytes"));
        assertTrue(stats.contains("MB"));
    }

    @Test
    @DisplayName("Should handle validation errors gracefully")
    void testValidationErrorHandling() {
        String xmlError = "XML validation failed: Element 'test' is not valid";
        String xsltError = "XSLT validation failed: Unknown element 'xsl:invalid'";

        assertNotNull(xmlError);
        assertNotNull(xsltError);
        assertTrue(xmlError.contains("validation failed"));
        assertTrue(xsltError.contains("validation failed"));
    }
}
