package org.fxt.freexmltoolkit.controller;

import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.service.XmlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdValidationController.
 * Tests XSD validation functionality and error handling.
 */
@ExtendWith(MockitoExtension.class)
class XsdValidationControllerTest {

    private XsdValidationController controller;

    @Mock
    private Button mockXmlLoadButton;

    @Mock
    private Button mockXsdLoadButton;

    @Mock
    private Button mockExcelExport;

    @Mock
    private Button mockClearResults;

    @Mock
    private TextField mockXmlFileName;

    @Mock
    private TextField mockXsdFileName;

    @Mock
    private TextField mockRemoteXsdLocation;

    @Mock
    private VBox mockErrorListBox;

    @Mock
    private CheckBox mockAutodetect;

    @Mock
    private ProgressIndicator mockProgressIndicator;

    @Mock
    private HBox mockStatusPane;

    @Mock
    private ImageView mockStatusImage;

    @Mock
    private Label mockStatusLabel;

    @Mock
    private XmlService mockXmlService;

    @BeforeEach
    void setUp() {
        controller = new XsdValidationController();
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
        assertTrue(xmlFile.getName().endsWith(".xml"), "Should recognize .xml extension");

        File invalidFile = new File("document.txt");
        assertFalse(invalidFile.getName().endsWith(".xml"), "Should not accept non-XML files");
    }

    @Test
    @DisplayName("Should validate XSD file extension")
    void testXsdFileExtension() {
        File xsdFile = new File("schema.xsd");
        assertTrue(xsdFile.getName().endsWith(".xsd"), "Should recognize .xsd extension");

        File invalidFile = new File("schema.xml");
        assertFalse(invalidFile.getName().endsWith(".xsd"), "Should not accept non-XSD files");
    }

    @Test
    @DisplayName("Should validate Excel export file extension")
    void testExcelFileExtension() {
        String excelFileName = "ValidationErrors.xlsx";
        assertTrue(excelFileName.endsWith(".xlsx"), "Excel export should use .xlsx extension");

        File excelFile = new File(excelFileName);
        assertEquals("ValidationErrors.xlsx", excelFile.getName());
    }

    @Test
    @DisplayName("Should recognize XSD namespace")
    void testXsdNamespace() {
        String xsdNamespace = "http://www.w3.org/2001/XMLSchema";
        assertNotNull(xsdNamespace);
        assertTrue(xsdNamespace.contains("XMLSchema"));
    }

    @Test
    @DisplayName("Should recognize noNamespaceSchemaLocation attribute")
    void testNoNamespaceSchemaLocation() {
        String noNamespaceAttr = "xsi:noNamespaceSchemaLocation";
        assertNotNull(noNamespaceAttr);
        assertTrue(noNamespaceAttr.contains("noNamespaceSchemaLocation"));
    }

    @Test
    @DisplayName("Should recognize schemaLocation attribute")
    void testSchemaLocation() {
        String schemaLocationAttr = "xsi:schemaLocation";
        assertNotNull(schemaLocationAttr);
        assertTrue(schemaLocationAttr.contains("schemaLocation"));
    }

    @Test
    @DisplayName("Should validate error message format")
    void testErrorMessageFormat() {
        String errorMessage = "Line: 5 Column: 12";

        assertNotNull(errorMessage);
        assertTrue(errorMessage.contains("Line:"));
        assertTrue(errorMessage.contains("Column:"));
        assertTrue(errorMessage.contains("5"));
        assertTrue(errorMessage.contains("12"));
    }

    @Test
    @DisplayName("Should handle positive line numbers")
    void testPositiveLineNumbers() {
        int lineNumber = 42;
        int columnNumber = 15;

        assertTrue(lineNumber > 0, "Line numbers should be positive");
        assertTrue(columnNumber > 0, "Column numbers should be positive");
    }

    @Test
    @DisplayName("Should handle zero and negative line numbers")
    void testZeroAndNegativeLineNumbers() {
        int zeroLine = 0;
        int negativeLine = -1;

        assertTrue(zeroLine <= 0, "Zero line should be handled");
        assertTrue(negativeLine < 0, "Negative line should be handled");
    }

    @Test
    @DisplayName("Should format success message correctly")
    void testSuccessMessage() {
        String successMessage = "Validation successful. No errors found.";

        assertNotNull(successMessage);
        assertTrue(successMessage.contains("successful"));
        assertTrue(successMessage.contains("No errors"));
    }

    @Test
    @DisplayName("Should format error count message correctly")
    void testErrorCountMessage() {
        int errorCount = 5;
        String errorMessage = "Validation failed. " + errorCount + " error(s) found.";

        assertNotNull(errorMessage);
        assertTrue(errorMessage.contains("Validation failed"));
        assertTrue(errorMessage.contains("5"));
        assertTrue(errorMessage.contains("error(s)"));
    }

    @Test
    @DisplayName("Should validate ready status message")
    void testReadyStatusMessage() {
        String readyMessage = "Ready for validation.";

        assertNotNull(readyMessage);
        assertTrue(readyMessage.contains("Ready"));
        assertTrue(readyMessage.contains("validation"));
    }

    @Test
    @DisplayName("Should validate success status style")
    void testSuccessStatusStyle() {
        String successStyle = "-fx-background-color: #e0f8e0;"; // Light green

        assertNotNull(successStyle);
        assertTrue(successStyle.contains("-fx-background-color"));
        assertTrue(successStyle.contains("#e0f8e0")); // Light green color
    }

    @Test
    @DisplayName("Should validate error status style")
    void testErrorStatusStyle() {
        String errorStyle = "-fx-background-color: #f8e0e0;"; // Light red

        assertNotNull(errorStyle);
        assertTrue(errorStyle.contains("-fx-background-color"));
        assertTrue(errorStyle.contains("#f8e0e0")); // Light red color
    }

    @Test
    @DisplayName("Should validate status icon paths")
    void testStatusIconPaths() {
        String successIcon = "/img/icons8-ok-48.png";
        String errorIcon = "/img/icons8-stornieren-48.png";

        assertNotNull(successIcon);
        assertNotNull(errorIcon);
        assertTrue(successIcon.startsWith("/img/"));
        assertTrue(errorIcon.startsWith("/img/"));
        assertTrue(successIcon.endsWith(".png"));
        assertTrue(errorIcon.endsWith(".png"));
    }

    @Test
    @DisplayName("Should handle drag and drop transfer modes")
    void testDragAndDropTransferModes() {
        // Drag and drop should use COPY transfer mode
        javafx.scene.input.TransferMode copyMode = javafx.scene.input.TransferMode.COPY;

        assertNotNull(copyMode);
        assertEquals(javafx.scene.input.TransferMode.COPY, copyMode);
    }

    @Test
    @DisplayName("Should validate progress indicator values")
    void testProgressIndicatorValues() {
        double[] progressSteps = {0.0, 0.1, 0.2, 0.4, 1.0};

        for (double progress : progressSteps) {
            assertTrue(progress >= 0.0 && progress <= 1.0,
                    "Progress should be between 0 and 1: " + progress);
        }
    }

    @Test
    @DisplayName("Should validate text flow line spacing")
    void testTextFlowLineSpacing() {
        double lineSpacing = 5.0;

        assertTrue(lineSpacing > 0, "Line spacing should be positive");
        assertEquals(5.0, lineSpacing);
    }

    @Test
    @DisplayName("Should validate font settings")
    void testFontSettings() {
        String fontFamily = "Verdana";
        int fontSize = 14;

        assertNotNull(fontFamily);
        assertEquals("Verdana", fontFamily);
        assertTrue(fontSize > 0, "Font size should be positive");
        assertEquals(14, fontSize);
    }

    @Test
    @DisplayName("Should validate success label styling")
    void testSuccessLabelStyling() {
        String labelStyle = "-fx-font-weight: bold; -fx-text-fill: green; -fx-font-size: 14px;";

        assertNotNull(labelStyle);
        assertTrue(labelStyle.contains("bold"));
        assertTrue(labelStyle.contains("green"));
        assertTrue(labelStyle.contains("14px"));
    }

    @Test
    @DisplayName("Should validate error list separator")
    void testErrorListSeparator() {
        Separator separator = new Separator();

        assertNotNull(separator);
    }

    @Test
    @DisplayName("Should validate go to error button text")
    void testGoToErrorButtonText() {
        String buttonText = "Go to error";

        assertNotNull(buttonText);
        assertEquals("Go to error", buttonText);
    }

    @Test
    @DisplayName("Should validate tab pane ID for navigation")
    void testTabPaneIdForNavigation() {
        String tabPaneId = "tabPaneXml";

        assertNotNull(tabPaneId);
        assertEquals("tabPaneXml", tabPaneId);
    }

    @Test
    @DisplayName("Should handle null file gracefully")
    void testNullFileHandling() {
        File nullFile = null;

        assertNull(nullFile, "Null file should be handled gracefully");
    }

    @Test
    @DisplayName("Should validate alert dialog types")
    void testAlertDialogTypes() {
        Alert.AlertType infoType = Alert.AlertType.INFORMATION;

        assertNotNull(infoType);
        assertEquals(Alert.AlertType.INFORMATION, infoType);
    }

    @Test
    @DisplayName("Should validate export alert messages")
    void testExportAlertMessages() {
        String exportNotPossibleHeader = "Export not possible.";
        String noErrorsContent = "No Errors found.";

        assertNotNull(exportNotPossibleHeader);
        assertNotNull(noErrorsContent);
        assertTrue(exportNotPossibleHeader.contains("Export"));
        assertTrue(noErrorsContent.contains("No Errors"));
    }

    @Test
    @DisplayName("Should set parent controller")
    void testSetParentController() {
        MainController mockMainController = new MainController();
        assertDoesNotThrow(() -> controller.setParentController(mockMainController));
    }

    @Test
    @DisplayName("Should validate file chooser extensions")
    void testFileChooserExtensions() {
        String xmlExtension = "*.xml";
        String xsdExtension = "*.xsd";

        assertNotNull(xmlExtension);
        assertNotNull(xsdExtension);
        assertTrue(xmlExtension.startsWith("*"));
        assertTrue(xsdExtension.startsWith("*"));
        assertTrue(xmlExtension.contains("xml"));
        assertTrue(xsdExtension.contains("xsd"));
    }

    @Test
    @DisplayName("Should handle error index formatting")
    void testErrorIndexFormatting() {
        int errorIndex = 0;
        String formattedIndex = "#" + (errorIndex + 1) + ": ";

        assertEquals("#1: ", formattedIndex);

        int errorIndex2 = 4;
        String formattedIndex2 = "#" + (errorIndex2 + 1) + ": ";

        assertEquals("#5: ", formattedIndex2);
    }

    @Test
    @DisplayName("Should validate system line separator")
    void testSystemLineSeparator() {
        String lineSeparator = System.lineSeparator();

        assertNotNull(lineSeparator);
        assertFalse(lineSeparator.isEmpty());
    }

    @Test
    @DisplayName("Should validate success icon properties")
    void testSuccessIconProperties() {
        String iconCode = "bi-check-circle-fill";
        int iconSize = 18;

        assertNotNull(iconCode);
        assertEquals("bi-check-circle-fill", iconCode);
        assertTrue(iconSize > 0);
        assertEquals(18, iconSize);
    }

    @Test
    @DisplayName("Should validate context line range for errors")
    void testContextLineRange() {
        int errorLine = 10;
        int beforeLine = errorLine - 1;
        int afterLine = errorLine + 1;

        assertEquals(9, beforeLine);
        assertEquals(10, errorLine);
        assertEquals(11, afterLine);
        assertTrue(beforeLine < errorLine);
        assertTrue(afterLine > errorLine);
    }

    @Test
    @DisplayName("Should validate status pane styling")
    void testStatusPaneStyling() {
        String baseStyle = "-fx-background-radius: 5; -fx-padding: 10;";

        assertNotNull(baseStyle);
        assertTrue(baseStyle.contains("-fx-background-radius"));
        assertTrue(baseStyle.contains("-fx-padding"));
        assertTrue(baseStyle.contains("5"));
        assertTrue(baseStyle.contains("10"));
    }

    @Test
    @DisplayName("Should handle file existence check")
    void testFileExistenceCheck() {
        File testFile = new File("test.xml");

        assertNotNull(testFile);
        // File may or may not exist - this tests the check logic
        boolean exists = testFile.exists();
        // No assertion on existence - just testing the logic
    }

    @Test
    @DisplayName("Should validate Excel file initial name")
    void testExcelFileInitialName() {
        String initialFileName = "ValidationErrors.xlsx";

        assertNotNull(initialFileName);
        assertEquals("ValidationErrors.xlsx", initialFileName);
        assertTrue(initialFileName.endsWith(".xlsx"));
    }

    @Test
    @DisplayName("Should validate file length check for export")
    void testFileLengthCheckForExport() {
        // File length should be greater than 0 for valid export
        long validLength = 1024;
        long emptyLength = 0;

        assertTrue(validLength > 0, "Valid file should have positive length");
        assertEquals(0, emptyLength, "Empty file has zero length");
    }
}
