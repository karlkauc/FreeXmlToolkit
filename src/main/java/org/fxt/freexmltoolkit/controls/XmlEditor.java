package org.fxt.freexmltoolkit.controls;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Side;
import javafx.scene.CacheHint;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import net.sf.saxon.s9api.XdmNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.controlsfx.control.PopOver;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxt.freexmltoolkit.controller.MainController;
import org.fxt.freexmltoolkit.controller.controls.XmlEditorSidebarController;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.XmlCanvasView;
import org.fxt.freexmltoolkit.controls.v2.common.utilities.XmlValidationHelper;
import org.fxt.freexmltoolkit.controls.v2.common.utilities.XmlEditorUIHelper;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.ValidationError;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * XML Editor tab component.
 * Uses XmlCodeEditorV2 (the new V2 architecture).
 */
public class XmlEditor extends Tab {

    public static final int MAX_SIZE_FOR_FORMATTING = 1024 * 1024 * 20;
    public static final String DEFAULT_FILE_NAME = "Untitled.xml *";

    private final Tab xml = new Tab("XML");
    private final Tab graphic = new Tab("Graphic");

    // V2 editor (V1 is deprecated and removed)
    private final org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2 xmlCodeEditorV2;
    public final CodeArea codeArea;


    private final static Logger logger = LogManager.getLogger(XmlEditor.class);

    File xmlFile;
    File xsdFile;
    File schematronFile;

    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer transformer;
    DocumentBuilder db;
    Document document;
    XmlService xmlService = new XmlServiceImpl();
    XsdDocumentationService xsdDocumentationService = new XsdDocumentationService();
    SchematronService schematronService = new SchematronServiceImpl();

    // Cache for XSD documentation data to avoid reparsing
    private XsdDocumentationData xsdDocumentationData;

    // Reference to the current XmlEditorContext used by the graphic view (V2)
    // This is updated when xsdDocumentationData is loaded asynchronously
    private XmlEditorContext currentGraphicViewContext;

    // Store original XML content for XPath queries (not modified by query results)
    private String originalXmlForXPath;

    private MainController mainController;

    private PopOver hoverPopOver;
    private final Label popOverLabel = new Label();
    private final PauseTransition hoverDelay = new PauseTransition(Duration.millis(500));


    // --- Sidebar Components ---
    private XmlEditorSidebarController sidebarController;
    private SplitPane splitPane;

    // --- Folding Control ---
    // Can be disabled for single-line XML files to prevent performance issues
    private boolean foldingEnabled = true;

    /**
         * Data class to hold element information.
         */
        private record ElementInfo(String name, String type) {
            private ElementInfo(String name, String type) {
                this.name = name != null ? name : "Unknown";
                this.type = type != null ? type : "Unknown";
            }
        }

    public XmlEditor() {
        // Create V2 editor (V1 has been removed)
        logger.info("Creating XmlCodeEditorV2");
        this.xmlCodeEditorV2 = org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2Factory.createForXmlEditor(this);
        this.codeArea = xmlCodeEditorV2.getCodeArea();
        xmlCodeEditorV2.setDocumentUri("untitled:" + System.nanoTime() + ".xml");

        init();
    }

    public XmlEditor(File file) {
        this(); // Call default constructor which sets up editor
        this.setXmlFile(file);
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;

        // Also set the MainController reference in the sidebar controller if it's already initialized
        if (sidebarController != null) {
            sidebarController.setMainController(mainController);
            logger.debug("✅ Updated MainController reference in XmlEditorSidebarController");
        } else {
            logger.debug("⚠️ SidebarController not yet initialized, MainController will be set later");
        }
    }

    public MainController getMainController() {
        return mainController;
    }


    private void init() {
        try {
            db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            transformer = transformerFactory.newTransformer();
        } catch (ParserConfigurationException | TransformerConfigurationException e) {
            throw new RuntimeException(e);
        }

        TabPane tabPane = new TabPane();
        tabPane.setSide(Side.LEFT);
        tabPane.getTabs().addAll(xml, graphic);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Enable caching for better rendering performance
        tabPane.setCache(true);
        tabPane.setCacheHint(CacheHint.SPEED);

        xml.setGraphic(new FontIcon("bi-code-slash:20"));
        graphic.setGraphic(new FontIcon("bi-columns-gap:20"));

        xml.setOnSelectionChanged(e -> {
            if (!xml.isSelected()) {
                try {
                    if (!codeArea.getText().isEmpty()) {
                        document = db.parse(new ByteArrayInputStream(codeArea.getText().getBytes(StandardCharsets.UTF_8)));
                        refreshGraphicView();
                    }
                } catch (SAXException | IOException ex) {
                    graphic.setContent(new Label("Invalid XML. Cannot display graphic view."));
                }
            }
        });

        // Also update DOM when graphic tab is selected to ensure it's current
        graphic.setOnSelectionChanged(e -> {
            if (graphic.isSelected()) {
                try {
                    if (!codeArea.getText().isEmpty()) {
                        document = db.parse(new ByteArrayInputStream(codeArea.getText().getBytes(StandardCharsets.UTF_8)));
                        refreshGraphicView();
                    }
                } catch (SAXException | IOException ex) {
                    graphic.setContent(new Label("Invalid XML. Cannot display graphic view."));
                }
            }
        });

        setupHover();
        setupSearchAndReplace();

        // Set XML tab content to V2 editor
        xml.setContent(xmlCodeEditorV2);
        this.setText(DEFAULT_FILE_NAME);
        this.setClosable(true);

        // --- Create Main Layout ---
        splitPane = new SplitPane();
        splitPane.setDividerPositions(0.8); // Initial position

        // --- Load Sidebar from FXML ---
        VBox sidebar = loadSidebar();

        splitPane.getItems().addAll(tabPane, sidebar);
        this.setContent(splitPane);

        // --- Add Listeners ---
        // Note: Syntax highlighting is handled by XmlCodeEditor's text listener
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (sidebarController != null && sidebarController.isContinuousValidationSelected()) {
                validateXml();
            }
            if (sidebarController != null && sidebarController.isContinuousSchematronValidationSelected()) {
                validateSchematron();
            }
        });

        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> updateCursorInformation());
    }

    /**
     * Loads the sidebar from FXML and initializes the controller.
     *
     * @return VBox containing the sidebar loaded from FXML
     */
    private VBox loadSidebar() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/pages/controls/XmlEditorSidebar.fxml"));
            VBox sidebar = loader.load();
            sidebarController = loader.getController();
            sidebarController.setXmlEditor(this);
            sidebarController.setSidebarContainer(sidebar); // Pass the container reference

            // Pass MainController reference if available
            if (mainController != null) {
                sidebarController.setMainController(mainController);
                logger.debug("✅ Set MainController reference during sidebar initialization");
            } else {
                logger.debug("⚠️ MainController not available during sidebar initialization");
            }

            // Initialize XSD-dependent panes state (disabled by default until XSD is linked)
            sidebarController.updateXsdDependentPanes(xsdFile != null);

            return sidebar;
        } catch (IOException e) {
            logger.error("Failed to load sidebar FXML", e);
            // Fallback: create a simple error message
            VBox errorBox = new VBox();
            errorBox.getChildren().add(new Label("Error loading sidebar: " + e.getMessage()));
            return errorBox;
        }
    }

    /**
     * Extracts element name and type information at the given cursor position.
     *
     * @param text     The XML text content
     * @param position The cursor position
     * @return ElementInfo containing name and type
     */
    private ElementInfo getElementInfoAtPosition(String text, int position) {
        if (text == null || text.isEmpty() || position <= 0) {
            return new ElementInfo("No XML content", "Unknown");
        }

        try {
            // Use the improved element name extraction
            String elementName = extractElementNameFromPosition(position);
            if (!"Unknown".equals(elementName)) {
                String elementType = determineXsdElementType(elementName);
                return new ElementInfo(elementName, elementType);
            }

            // Fallback: try XMLStreamReader approach but find the innermost element
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(text));

            Deque<String> elementStack = new ArrayDeque<>();
            String resultElementName = null;

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String localName = reader.getLocalName();
                    elementStack.push(localName);

                    // Check if we've passed the cursor position
                    if (reader.getLocation() != null) {
                        int elementStart = reader.getLocation().getCharacterOffset();
                        if (elementStart > position) {
                            // We've gone past the cursor, so the current element is what we want
                            if (!elementStack.isEmpty()) {
                                elementStack.pop(); // Remove the element we just added
                                if (!elementStack.isEmpty()) {
                                    resultElementName = elementStack.peek();
                                }
                            }
                            break;
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if (!elementStack.isEmpty()) {
                        String endElementName = reader.getLocalName();
                        if (elementStack.peek().equals(endElementName)) {
                            elementStack.pop();
                        }
                    }
                }
            }

            reader.close();

            if (resultElementName != null) {
                String elementType = determineXsdElementType(resultElementName);
                return new ElementInfo(resultElementName, elementType);
            }

            // If we still don't have a result but have elements in stack, use the top one
            if (!elementStack.isEmpty()) {
                String topElement = elementStack.peek();
                String elementType = determineXsdElementType(topElement);
                return new ElementInfo(topElement, elementType);
            }

        } catch (Exception e) {
            logger.debug("Error parsing XML for element info: {}", e.getMessage());
        }

        // Final fallback: try to extract element name from manual parsing
        return getElementInfoManual(text, position);
    }

    /**
     * Determines the XSD type of an XML element based on the loaded XSD schema.
     *
     * @param elementName The name of the element to find the type for
     * @return String describing the XSD type (simpleType, complexType, or built-in type)
     */
    private String determineXsdElementType(String elementName) {
        if (xsdFile == null || elementName == null || elementName.isEmpty()) {
            return "Unknown (no XSD loaded)";
        }

        try {
            // Parse the XSD file
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document xsdDoc = builder.parse(xsdFile);

            // Remove namespace prefix if present
            String cleanElementName = elementName;
            if (elementName.contains(":")) {
                cleanElementName = elementName.split(":")[1];
            }

            // Try to find the element definition using Saxon XPath 3.1
            String elementQuery = "//xs:element[@name='" + cleanElementName + "']";
            XdmNode elementNode = SaxonXPathHelper.evaluateSingleNode(xsdDoc, elementQuery, SaxonXPathHelper.XSD_NAMESPACES);

            if (elementNode == null) {
                // Try without namespace prefix
                elementQuery = "//element[@name='" + cleanElementName + "']";
                elementNode = SaxonXPathHelper.evaluateSingleNode(xsdDoc, elementQuery, null);
            }

            if (elementNode != null) {
                // Get the type attribute
                String typeName = SaxonXPathHelper.getAttributeValue(elementNode, "type");
                if (typeName != null) {
                    // Check if it's a built-in type
                    if (isBuiltInType(typeName)) {
                        // Remove namespace prefix if present to avoid duplication
                        String cleanTypeName = typeName;
                        if (typeName.contains(":")) {
                            cleanTypeName = typeName.split(":")[1];
                        }
                        return "xs:" + cleanTypeName;
                    }

                    // Remove namespace prefix from type name for lookup
                    String cleanTypeLookup = typeName;
                    if (typeName.contains(":")) {
                        cleanTypeLookup = typeName.split(":")[1];
                    }

                    // Check if it's a simpleType
                    String simpleTypeQuery = "//xs:simpleType[@name='" + cleanTypeLookup + "']";
                    XdmNode simpleTypeNode = SaxonXPathHelper.evaluateSingleNode(xsdDoc, simpleTypeQuery, SaxonXPathHelper.XSD_NAMESPACES);
                    if (simpleTypeNode != null) {
                        return "simpleType: " + typeName;
                    }

                    // Check if it's a complexType
                    String complexTypeQuery = "//xs:complexType[@name='" + cleanTypeLookup + "']";
                    XdmNode complexTypeNode = SaxonXPathHelper.evaluateSingleNode(xsdDoc, complexTypeQuery, SaxonXPathHelper.XSD_NAMESPACES);
                    if (complexTypeNode != null) {
                        return "complexType: " + typeName;
                    }

                    return "type: " + typeName;
                }

                // Check if element has inline type definition
                String inlineSimpleTypeQuery = ".//xs:simpleType";
                if (SaxonXPathHelper.evaluateBoolean(xsdDoc,
                        "//xs:element[@name='" + cleanElementName + "']/xs:simpleType",
                        SaxonXPathHelper.XSD_NAMESPACES)) {
                    return "inline simpleType";
                }

                String inlineComplexTypeQuery = "//xs:element[@name='" + cleanElementName + "']/xs:complexType";
                if (SaxonXPathHelper.evaluateBoolean(xsdDoc, inlineComplexTypeQuery, SaxonXPathHelper.XSD_NAMESPACES)) {
                    return "inline complexType";
                }

                // If no type is specified, check if it's a complex element (has child elements)
                String hasChildrenQuery = "//xs:element[@name='" + cleanElementName + "']//xs:element";
                int childCount = SaxonXPathHelper.evaluateCount(xsdDoc, hasChildrenQuery, SaxonXPathHelper.XSD_NAMESPACES);
                if (childCount > 0) {
                    return "complexType (implicit)";
                }

                return "string (default)";
            }

        } catch (Exception e) {
            logger.debug("Error determining XSD element type: {}", e.getMessage());
        }

        return "Unknown";
    }

    /**
     * Checks if a type name is a built-in XSD type.
     *
     * @param typeName The type name to check
     * @return true if it's a built-in type
     */
    private boolean isBuiltInType(String typeName) {
        if (typeName == null) return false;

        // Remove namespace prefix if present
        String cleanTypeName = typeName;
        if (typeName.contains(":")) {
            cleanTypeName = typeName.split(":")[1];
        }

        // List of built-in XSD types
        String[] builtInTypes = {
                "string", "boolean", "decimal", "float", "double", "duration", "dateTime", "time", "date",
                "gYearMonth", "gYear", "gMonthDay", "gDay", "gMonth", "hexBinary", "base64Binary",
                "anyURI", "QName", "NOTATION", "normalizedString", "token", "language", "Name", "NCName",
                "ID", "IDREF", "IDREFS", "ENTITY", "ENTITIES", "NMTOKEN", "NMTOKENS", "integer",
                "nonPositiveInteger", "negativeInteger", "long", "int", "short", "byte", "nonNegativeInteger",
                "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte", "positiveInteger"
        };

        for (String builtInType : builtInTypes) {
            if (builtInType.equals(cleanTypeName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Manual fallback method to extract element information using regex.
     *
     * @param text     The XML text content
     * @param position The cursor position
     * @return ElementInfo containing name and type
     */
    private ElementInfo getElementInfoManual(String text, int position) {
        try {
            // Find the most recent opening tag before the cursor position
            String beforeCursor = text.substring(0, Math.min(position, text.length()));

            // Look for the last opening tag
            Pattern pattern = Pattern.compile("<([a-zA-Z][a-zA-Z0-9_:]*)\\b");
            Matcher matcher = pattern.matcher(beforeCursor);

            String lastElementName = null;
            while (matcher.find()) {
                lastElementName = matcher.group(1);
            }

            if (lastElementName != null) {
                // Get XSD type information for this element
                String elementType = determineXsdElementType(lastElementName);
                return new ElementInfo(lastElementName, elementType);
            }

        } catch (Exception e) {
            logger.debug("Error in manual element info extraction: {}", e.getMessage());
        }

        return new ElementInfo("Unable to determine", "Unknown");
    }



    private void setupHover() {
        popOverLabel.setWrapText(true);
        popOverLabel.setStyle("-fx-padding: 8px; -fx-font-family: 'monospaced';");
        hoverPopOver = new PopOver(popOverLabel);
        hoverPopOver.setDetachable(false);
        hoverPopOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);
    }

    private void setupSearchAndReplace() {
        codeArea.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case F -> showSearchPopup(true);
                    case R -> showSearchPopup(false);
                    default -> {
                    } // Handle all other key codes
                }
                event.consume();
            }
        });
    }

    private void showSearchPopup(boolean isSearch) {
        try {
            org.fxt.freexmltoolkit.controls.shared.utilities.FindReplaceDialog dialog =
                new org.fxt.freexmltoolkit.controls.shared.utilities.FindReplaceDialog(codeArea);
            dialog.showAndWait();
        } catch (Exception e) {
            logger.error("Error opening find/replace dialog: {}", e.getMessage(), e);
        }
    }

    /**
     * Updates the cursor information including XPath, element name, element type, documentation, and example values.
     * Uses findBestMatchingElement with path-cleaning to properly handle XSD SEQUENCE/CHOICE/ALL markers.
     */
    private void updateCursorInformation() {
        if (sidebarController == null) return;

        String text = codeArea.getText();
        int caretPosition = codeArea.getCaretPosition();
        String xpath = getCurrentXPath(text, caretPosition);
        logger.debug("XPath at position {}: {}", caretPosition, xpath);
        sidebarController.setXPath(xpath);

        // Extract element name from the XPath
        String elementName = null;
        if (xpath != null && !xpath.isEmpty() && !xpath.equals("Invalid XML structure") &&
                !xpath.equals("No XML content") && !xpath.equals("Unable to determine XPath")) {
            String[] parts = xpath.split("/");
            if (parts.length > 0) {
                elementName = parts[parts.length - 1];
            }
        }

        if (elementName == null || elementName.isEmpty()) {
            sidebarController.setElementName("");
            sidebarController.setElementType("");
            sidebarController.setDocumentation("");
            sidebarController.setExampleValues(List.of("No element selected"));
            sidebarController.setPossibleChildElements(List.of("No element selected"));
            return;
        }

        sidebarController.setElementName(elementName);

        // Use findBestMatchingElement with path-cleaning (same as Unified Editor)
        if (xsdDocumentationData != null) {
            XsdExtendedElement xsdElement = findBestMatchingElement(xpath);
            logger.debug("updateCursorInformation: xpath='{}', xsdElement={}",
                    xpath, xsdElement != null ? "found '" + xsdElement.getElementName() + "'" : "null");

            if (xsdElement != null) {
                // Set element type
                sidebarController.setElementType(xsdElement.getElementType() != null ? xsdElement.getElementType() : "");

                // Set documentation
                String doc = xsdElement.getDocumentationAsHtml();
                sidebarController.setDocumentation(doc != null ? stripHtmlTags(doc) : "");

                // Set example values
                List<String> examples = xsdElement.getExampleValues();
                if (examples != null && !examples.isEmpty()) {
                    sidebarController.setExampleValues(examples);
                } else {
                    sidebarController.setExampleValues(List.of("No example values available"));
                }

                // Set child elements - format to resolve SEQUENCE_, CHOICE_, ALL_ containers
                List<String> children = xsdElement.getChildren();
                if (children != null && !children.isEmpty()) {
                    List<String> formattedChildren = formatChildElementsForDisplay(children, true);
                    if (formattedChildren.isEmpty()) {
                        sidebarController.setPossibleChildElements(List.of("No child elements defined"));
                    } else {
                        sidebarController.setPossibleChildElements(formattedChildren);
                    }
                } else {
                    sidebarController.setPossibleChildElements(List.of("No child elements defined"));
                }
            } else {
                // Element not found in XSD
                sidebarController.setElementType("");
                sidebarController.setDocumentation("Element '" + elementName + "' not found in XSD schema");
                sidebarController.setExampleValues(List.of("No example values available"));
                sidebarController.setPossibleChildElements(List.of("No child elements"));
            }
        } else {
            // No XSD linked
            sidebarController.setElementType("");
            sidebarController.setDocumentation("Link an XSD schema to see documentation");
            sidebarController.setExampleValues(List.of("Link XSD for example values"));
            sidebarController.setPossibleChildElements(List.of("Link XSD for child elements"));
        }
    }

    /**
     * Strips HTML tags from text for plain text display.
     */
    private String stripHtmlTags(String html) {
        return XmlEditorUIHelper.stripHtmlTags(html);
    }

    /**
     * Determines the XPath of the element at the current cursor position.
     * Uses the same robust stack-based approach as element name extraction.
     *
     * @param text The XML text content
     * @param position The current cursor position
     * @return The XPath string or a descriptive message if parsing fails
     */
    public String getCurrentXPath(String text, int position) {
        if (text == null || text.isEmpty() || position <= 0) {
            return "No XML content";
        }

        try {
            // Use the same stack-based approach as extractElementNameFromPosition
            Deque<String> elementStack = buildElementStackToPosition(text, position);

            // Always try to get the root element name
            String rootElementName = extractRootElementName(text);

            if (elementStack.isEmpty()) {
                // If no nested elements, show root element or indicate we're at root level
                return rootElementName != null ? "/" + rootElementName : "/";
            }

            // Build XPath from stack (stack is in reverse order)
            List<String> pathElements = new ArrayList<>();
            elementStack.descendingIterator().forEachRemaining(pathElements::add);

            // Always include root element at the beginning if we found one
            if (rootElementName != null) {
                // Check if root element is already first in path, if not add it
                if (pathElements.isEmpty() || !pathElements.getFirst().equals(rootElementName)) {
                    pathElements.addFirst(rootElementName);
                }
            }

            return pathElements.isEmpty() ? "/" : "/" + String.join("/", pathElements);

        } catch (Exception e) {
            logger.debug("Stack-based XPath failed, trying fallback: {}", e.getMessage());
            // Fallback to manual parsing
            return getCurrentXPathManual(text, position);
        }
    }

    /**
     * Builds a stack of elements from the beginning of the text to the cursor position.
     * This is the same logic used in extractElementNameFromPosition but returns the full stack.
     *
     * @param text          The XML text content
     * @param caretPosition The cursor position
     * @return A stack of element names (top of stack = innermost element)
     */
    private Deque<String> buildElementStackToPosition(String text, int caretPosition) {
        Deque<String> elementStack = new ArrayDeque<>();

        if (text == null || text.isEmpty() || caretPosition < 0 || caretPosition > text.length()) {
            return elementStack;
        }

        try {
            String textToCursor = text.substring(0, caretPosition);

            // Pattern for opening tags - handles attributes with slashes (e.g., URLs in namespace declarations)
            // The previous pattern [^/>]* would stop at '/' in attribute values like xmlns="http://..."
            Pattern openTagPattern = Pattern.compile("<([a-zA-Z][a-zA-Z0-9_:]*)(?:\\s[^>]*)?>(?<!/>)");
            // Pattern for closing tags
            Pattern closeTagPattern = Pattern.compile("</([a-zA-Z][a-zA-Z0-9_:]*)\\s*>");
            // Pattern for self-closing tags - handles attributes with slashes
            Pattern selfClosingPattern = Pattern.compile("<([a-zA-Z][a-zA-Z0-9_:]*)(?:\\s[^>]*)?/>");

            // Find all tags in order
            List<TagMatch> tags = new ArrayList<>();

            // Find opening tags
            Matcher openMatcher = openTagPattern.matcher(textToCursor);
            while (openMatcher.find()) {
                tags.add(new TagMatch(openMatcher.start(), openMatcher.group(1), TagType.OPEN));
            }

            // Find closing tags
            Matcher closeMatcher = closeTagPattern.matcher(textToCursor);
            while (closeMatcher.find()) {
                tags.add(new TagMatch(closeMatcher.start(), closeMatcher.group(1), TagType.CLOSE));
            }

            // Find self-closing tags
            Matcher selfClosingMatcher = selfClosingPattern.matcher(textToCursor);
            while (selfClosingMatcher.find()) {
                tags.add(new TagMatch(selfClosingMatcher.start(), selfClosingMatcher.group(1), TagType.SELF_CLOSING));
            }

            // Sort tags by position
            tags.sort(java.util.Comparator.comparingInt(t -> t.position));

            // Process tags to build element stack
            for (TagMatch tag : tags) {
                switch (tag.type) {
                    case OPEN -> elementStack.push(tag.name);
                    case CLOSE -> {
                        // Remove matching opening tag from stack
                        if (!elementStack.isEmpty() && elementStack.peek().equals(tag.name)) {
                            elementStack.pop();
                        }
                    }
                    case SELF_CLOSING -> {
                        // Self-closing tags don't affect the stack for XPath
                        // but we might want to include them in some cases
                    }
                }
            }

        } catch (Exception e) {
            logger.debug("Error building element stack to position: {}", e.getMessage());
        }

        return elementStack;
    }

    /**
     * Extracts the root element name from the XML text.
     *
     * @param text The XML text content
     * @return The name of the root element, or null if not found
     */
    private String extractRootElementName(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        try {
            // Step by step approach to find the root element

            // First, remove XML declaration if present
            String workingText = text;
            Pattern xmlDeclPattern = Pattern.compile(
                    "^\\s*<\\?xml[^>]*>\\s*", Pattern.DOTALL
            );
            workingText = xmlDeclPattern.matcher(workingText).replaceFirst("");

            // Remove any comments at the beginning
            Pattern commentPattern = Pattern.compile(
                    "^\\s*<!--.*?-->\\s*", Pattern.DOTALL
            );
            while (commentPattern.matcher(workingText).find()) {
                workingText = commentPattern.matcher(workingText).replaceFirst("");
            }

            // Remove any processing instructions
            Pattern piPattern = Pattern.compile(
                    "^\\s*<\\?.*?\\?>\\s*", Pattern.DOTALL
            );
            while (piPattern.matcher(workingText).find()) {
                workingText = piPattern.matcher(workingText).replaceFirst("");
            }

            // Now find the first element tag
            Pattern rootElementPattern = Pattern.compile(
                    "^\\s*<([a-zA-Z][a-zA-Z0-9_:]*)[\\s>]"
            );
            Matcher matcher = rootElementPattern.matcher(workingText);

            if (matcher.find()) {
                return matcher.group(1);
            }

            // Fallback: find any element tag at the beginning
            Pattern fallbackPattern = Pattern.compile(
                    "<([a-zA-Z][a-zA-Z0-9_:]*)[\\s>/]"
            );
            matcher = fallbackPattern.matcher(text);

            if (matcher.find()) {
                return matcher.group(1);
            }
            
        } catch (Exception e) {
            logger.debug("Error extracting root element name: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Manual XPath parsing for malformed XML.
     * This method uses simple string operations to extract element names.
     *
     * @param text     The XML text content
     * @param position The current cursor position
     * @return The XPath string or a descriptive message
     */
    private String getCurrentXPathManual(String text, int position) {
        Deque<String> elementStack = new ArrayDeque<>();
        String textBeforeCursor = text.substring(0, position);

        // Simple regex to find opening and closing tags
        String openTagPattern = "<([a-zA-Z][a-zA-Z0-9_:]*)\\b[^>]*>";
        String closeTagPattern = "</([a-zA-Z][a-zA-Z0-9_:]*)\\s*>";

        try {
            // Find all opening tags before cursor
            Pattern openPattern = Pattern.compile(openTagPattern);
            Matcher openMatcher = openPattern.matcher(textBeforeCursor);

            while (openMatcher.find()) {
                String elementName = openMatcher.group(1);
                // Skip self-closing tags and processing instructions
                if (!elementName.startsWith("?") && !elementName.startsWith("!")) {
                    elementStack.push(elementName);
                }
            }

            // Find all closing tags before cursor and remove corresponding opening tags
            Pattern closePattern = Pattern.compile(closeTagPattern);
            Matcher closeMatcher = closePattern.matcher(textBeforeCursor);

            while (closeMatcher.find()) {
                String elementName = closeMatcher.group(1);
                if (!elementStack.isEmpty() && elementStack.peek().equals(elementName)) {
                    elementStack.pop();
                }
            }

            // Build XPath with root element
            String rootElementName = extractRootElementName(text);
            Deque<String> reversedStack = new ArrayDeque<>();
            elementStack.forEach(reversedStack::push);

            List<String> pathElements = new ArrayList<>(reversedStack);

            // Always include root element at the beginning if we found one
            if (rootElementName != null) {
                // Check if root element is already first in path, if not add it
                if (pathElements.isEmpty() || !pathElements.getFirst().equals(rootElementName)) {
                    pathElements.addFirst(rootElementName);
                }
            }

            return pathElements.isEmpty() ? "/" : "/" + String.join("/", pathElements);
            
        } catch (Exception e) {
            logger.debug("Manual parsing also failed: {}", e.getMessage());
            return "Unable to determine XPath";
        }
    }

    /**
     * Updates the child elements list based on the current XPath using XsdDocumentationData.
     *
     * @param xpath The current XPath to find child elements for
     */
    private void updateChildElements(String xpath) {
        if (sidebarController == null) return;

        if (xsdDocumentationData == null || xpath == null || xpath.equals("Invalid XML structure") ||
                xpath.equals("No XML content") || xpath.equals("Unable to determine XPath")) {
            sidebarController.setPossibleChildElements(Collections.singletonList("No XSD documentation data loaded or invalid XPath"));
            return;
        }

        try {
            // Look up the element information in the extendedXsdElementMap
            XsdExtendedElement elementInfo = xsdDocumentationData.getExtendedXsdElementMap().get(xpath);

            if (elementInfo == null) {
                // Try to find a partial match or parent element
                elementInfo = findBestMatchingElement(xpath);
            }

            if (elementInfo != null) {
                List<String> childElements = elementInfo.getChildren();
                if (childElements != null && !childElements.isEmpty()) {
                    // Format child elements to show names and types for sidebar, not full XPaths
                    List<String> formattedChildren = formatChildElementsForDisplay(childElements, true);
                    sidebarController.setPossibleChildElements(formattedChildren);
                } else {
                    sidebarController.setPossibleChildElements(Collections.singletonList("No child elements defined for this element"));
                }
            } else {
                // Fallback: try to find child elements using element name from xpath
                String elementName = getElementNameFromXPath(xpath);
                if (elementName != null) {
                    List<String> childElements = getChildElementsFromXsdByName(elementName);
                    if (!childElements.isEmpty()) {
                        // Format child elements to show names and types for sidebar, not full XPaths
                        List<String> formattedChildren = formatChildElementsForDisplay(childElements, true);
                        sidebarController.setPossibleChildElements(formattedChildren);
                    } else {
                        sidebarController.setPossibleChildElements(Collections.singletonList("No child elements found for: " + elementName));
                    }
                } else {
                    sidebarController.setPossibleChildElements(Collections.singletonList("Element not found in XSD"));
                }
            }
        } catch (Exception e) {
            logger.error("Error getting child elements from XSD documentation data", e);
            sidebarController.setPossibleChildElements(Collections.singletonList("Error: " + e.getMessage()));
        }
    }

    /**
     * Formats child element XPaths for display in the sidebar.
     * Extracts element name and type information instead of showing full XPath.
     *
     * @param childElements List of child element XPaths from XsdExtendedElement
     * @return List of formatted strings with element names and types
     */
    private List<String> formatChildElementsForDisplay(List<String> childElements) {
        return formatChildElementsForDisplay(childElements, false);
    }

    /**
     * Formats child elements with option to include type information for display.
     * Filters out container elements (SEQUENCE_, CHOICE_, ALL_) and recursively
     * resolves them to show actual child element names.
     */
    private List<String> formatChildElementsForDisplay(List<String> childElements, boolean includeTypes) {
        List<String> formattedElements = new ArrayList<>();
        var elementMap = xsdDocumentationData != null ? xsdDocumentationData.getExtendedXsdElementMap() : null;

        for (String childXPath : childElements) {
            try {
                // Skip attributes
                if (childXPath.contains("/@")) {
                    continue;
                }

                // Extract element name from XPath (get the last part after the last '/')
                String elementName = getElementNameFromXPath(childXPath);
                if (elementName == null) {
                    continue;
                }

                // Handle container elements (SEQUENCE_, CHOICE_, ALL_) - resolve them recursively
                if (elementName.startsWith("SEQUENCE_") || elementName.startsWith("CHOICE_") || elementName.startsWith("ALL_")) {
                    // For containers, look at their children instead
                    if (elementMap != null && elementMap.containsKey(childXPath)) {
                        var containerElement = elementMap.get(childXPath);
                        if (containerElement.getChildren() != null && !containerElement.getChildren().isEmpty()) {
                            formattedElements.addAll(formatChildElementsForDisplay(containerElement.getChildren(), includeTypes));
                        }
                    }
                    continue;
                }

                String displayText;
                if (includeTypes) {
                    // Get the XsdExtendedElement for this child
                    XsdExtendedElement childElement = elementMap != null ? elementMap.get(childXPath) : null;

                    // Check if element is mandatory or optional
                    String mandatoryIndicator = "";
                    if (childElement != null) {
                        mandatoryIndicator = childElement.isMandatory() ? " *" : "";
                    }

                    // Try to get type information from the XSD documentation data
                    String elementType = getElementTypeFromXsdData(childXPath);
                    if (elementType != null && !elementType.isEmpty() && !elementType.equals("xs:string") && !elementType.equals("(container)")) {
                        // Show element name with type and mandatory indicator
                        displayText = elementName + " : " + elementType + mandatoryIndicator;
                    } else {
                        // Show element name with mandatory indicator (no type info)
                        displayText = elementName + mandatoryIndicator;
                    }
                } else {
                    // IntelliSense mode: only show element name
                    displayText = elementName;
                }

                // Avoid duplicates
                if (!formattedElements.contains(displayText)) {
                    formattedElements.add(displayText);
                }
            } catch (Exception e) {
                logger.debug("Could not format child element: " + childXPath, e);
            }
        }

        return formattedElements;
    }

    /**
     * Gets the element type from XSD documentation data based on XPath.
     *
     * @param xpath The XPath of the element
     * @return The type of the element, or null if not found
     */
    private String getElementTypeFromXsdData(String xpath) {
        try {
            if (xsdDocumentationData != null && xsdDocumentationData.getExtendedXsdElementMap() != null) {
                XsdExtendedElement elementInfo = xsdDocumentationData.getExtendedXsdElementMap().get(xpath);
                if (elementInfo != null) {
                    String type = elementInfo.getElementType();
                    // Clean up common prefixes to make types more readable
                    if (type != null && type.startsWith("xs:")) {
                        return type; // Keep xs: prefix as it's standard
                    } else if (type != null && type.contains(":")) {
                        // Remove other namespace prefixes for cleaner display
                        return type.substring(type.lastIndexOf(":") + 1);
                    }
                    return type;
                }
            }
        } catch (Exception e) {
            logger.debug("Could not get element type for xpath: " + xpath, e);
        }
        return null;
    }

    /**
     * Gets child elements by searching through XSD documentation data by element name.
     */
    private List<String> getChildElementsFromXsdByName(String elementName) {
        if (xsdDocumentationData == null || elementName == null) {
            return Collections.emptyList();
        }

        // Search through all elements to find ones with matching name
        for (XsdExtendedElement element : xsdDocumentationData.getExtendedXsdElementMap().values()) {
            if (elementName.equals(element.getElementName()) && element.getChildren() != null) {
                return element.getChildren();
            }
        }

        return Collections.emptyList();
    }

    public File getXsdFile() {
        return xsdFile;
    }

    /**
     * Public method to get child elements for IntelliSense use.
     * This allows XmlCodeEditor to get the same child elements as shown in sidebar.
     */
    public List<String> getChildElementsForIntelliSense(String xpath) {
        try {
            if (xpath == null || xpath.trim().isEmpty()) {
                return Collections.emptyList();
            }

            logger.debug("Getting child elements for IntelliSense, XPath: {}", xpath);

            // Use the same logic as updateChildElements() but return the list instead of setting sidebar
            if (xsdDocumentationData == null) {
                return Collections.emptyList();
            }

            // Look up the element information in the extendedXsdElementMap
            XsdExtendedElement elementInfo = xsdDocumentationData.getExtendedXsdElementMap().get(xpath);

            if (elementInfo == null) {
                // Try to find a partial match or parent element
                elementInfo = findBestMatchingElement(xpath);
            }

            if (elementInfo != null) {
                List<String> childElements = elementInfo.getChildren();
                if (childElements != null && !childElements.isEmpty()) {
                    // Format child elements to show only names, not full XPaths
                    List<String> formattedChildren = formatChildElementsForDisplay(childElements);
                    logger.debug("Found {} formatted child elements for IntelliSense: {}", formattedChildren.size(), formattedChildren);
                    return formattedChildren;
                }
            } else {
                // Fallback: try to find child elements using element name from xpath
                String elementName = getElementNameFromXPath(xpath);
                if (elementName != null) {
                    List<String> childElements = getChildElementsFromXsdByName(elementName);
                    if (!childElements.isEmpty()) {
                        // Format child elements to show only names, not full XPaths
                        List<String> formattedChildren = formatChildElementsForDisplay(childElements);
                        logger.debug("Found {} formatted child elements by name for IntelliSense: {}", formattedChildren.size(), formattedChildren);
                        return formattedChildren;
                    }
                }
            }

            return Collections.emptyList();
        } catch (Exception e) {
            logger.error("Error getting child elements for IntelliSense", e);
            return Collections.emptyList();
        }
    }

    public void setXsdFile(File xsdFile) {
        this.xsdFile = xsdFile;
        if (sidebarController != null) {
            sidebarController.setXsdPathField(xsdFile != null ? xsdFile.getAbsolutePath() : "No XSD schema selected");
            // Update XSD-dependent panes in sidebar based on whether XSD is linked
            sidebarController.updateXsdDependentPanes(xsdFile != null);
        }

        // Clear cached XSD documentation data when XSD changes
        this.xsdDocumentationData = null;

        // Extract element names from XSD and update IntelliSense
        if (xsdFile != null) {
            List<String> elementNames = extractElementNamesFromXsd(xsdFile);
            Map<String, List<String>> contextElementNames = extractContextElementNamesFromXsd(xsdFile);

            // Update IntelliSense
            // Schema provider is already connected, invalidate cache to trigger reload
            if (xmlCodeEditorV2 != null && xmlCodeEditorV2.getIntelliSenseEngine() != null) {
                xmlCodeEditorV2.getIntelliSenseEngine().invalidateCacheForSchema();
                logger.debug("IntelliSense cache invalidated for new XSD schema");
            }

            // Refresh status line to show XSD status
            javafx.application.Platform.runLater(() -> {
                if (xmlCodeEditorV2 != null) {
                    xmlCodeEditorV2.refreshStatusLine();
                    logger.debug("Status line refreshed after XSD change");
                }
            });

            // Load XSD documentation data in background
            loadXsdDocumentationDataAsync();
        }

        validateXml();
    }

    public void setSchematronFile(File schematronFile) {
        this.schematronFile = schematronFile;
        if (sidebarController != null) {
            sidebarController.setSchematronPathField(schematronFile != null ? schematronFile.getAbsolutePath() : "No Schematron rules selected");
        }
        validateSchematron();
    }

    public void validateXml() {
        if (sidebarController == null) return;

        if (xsdFile == null) {
            sidebarController.updateValidationStatus("No XSD selected", "orange", null);
            return;
        }

        try {
            String xmlContent = codeArea.getText();
            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                sidebarController.updateValidationStatus("No XML content", "orange", null);
                return;
            }

            // Use the XmlService for validation
            List<org.xml.sax.SAXParseException> saxErrors = xmlService.validateText(xmlContent, xsdFile);

            if (saxErrors == null || saxErrors.isEmpty()) {
                sidebarController.updateValidationStatus("✓ Valid", "green", null);
            } else {
                // Convert SAXParseException to ValidationError objects
                List<ValidationError> validationErrors = saxErrors.stream()
                        .map(XmlValidationHelper::convertToValidationError)
                        .collect(Collectors.toList());

                String errorMessage = "✗ Invalid (" + saxErrors.size() + " error" + (saxErrors.size() == 1 ? "" : "s") + ")";
                sidebarController.updateValidationStatus(errorMessage, "red", validationErrors);
            }
        } catch (Exception e) {
            sidebarController.updateValidationStatus("Error during validation", "red", null);
            logger.error("Error during XML validation", e);
        }
    }

    /**
     * Validates XML and shows an alert with the result.
     * If no XSD is linked, only checks well-formedness.
     * If XSD is linked, checks both well-formedness and schema validity.
     */
    public void validateWithAlert() {
        String xmlContent = codeArea.getText();
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            XmlValidationHelper.showValidationAlert(Alert.AlertType.WARNING, "Validation", "No XML content to validate.");
            return;
        }

        try {
            if (xsdFile == null) {
                // Only check well-formedness
                List<org.xml.sax.SAXParseException> errors = xmlService.validateText(xmlContent);

                if (errors == null || errors.isEmpty()) {
                    XmlValidationHelper.showValidationAlert(Alert.AlertType.INFORMATION, "Well-formedness Check",
                        "The XML document is well-formed.");
                } else {
                    StringBuilder errorDetails = new StringBuilder();
                    errorDetails.append("The XML document is not well-formed.\n\n");
                    errorDetails.append("Errors found: ").append(errors.size()).append("\n\n");
                    for (int i = 0; i < Math.min(errors.size(), 5); i++) {
                        org.xml.sax.SAXParseException e = errors.get(i);
                        errorDetails.append("Line ").append(e.getLineNumber())
                            .append(", Column ").append(e.getColumnNumber())
                            .append(": ").append(e.getMessage()).append("\n");
                    }
                    if (errors.size() > 5) {
                        errorDetails.append("\n... and ").append(errors.size() - 5).append(" more errors.");
                    }
                    XmlValidationHelper.showValidationAlert(Alert.AlertType.ERROR, "Well-formedness Check", errorDetails.toString());
                }
            } else {
                // Check both well-formedness and schema validity
                List<org.xml.sax.SAXParseException> errors = xmlService.validateText(xmlContent, xsdFile);

                if (errors == null || errors.isEmpty()) {
                    XmlValidationHelper.showValidationAlert(Alert.AlertType.INFORMATION, "Schema Validation",
                        "The XML document is valid.\n\nXSD: " + xsdFile.getName());
                } else {
                    StringBuilder errorDetails = new StringBuilder();
                    errorDetails.append("The XML document is not valid.\n\n");
                    errorDetails.append("XSD: ").append(xsdFile.getName()).append("\n");
                    errorDetails.append("Errors found: ").append(errors.size()).append("\n\n");
                    for (int i = 0; i < Math.min(errors.size(), 5); i++) {
                        org.xml.sax.SAXParseException e = errors.get(i);
                        String msg = e.getMessage();
                        // Clean up error message
                        if (msg != null) {
                            msg = msg.replaceAll("^cvc-[^:]*:\\s*", "");
                        }
                        errorDetails.append("Line ").append(e.getLineNumber())
                            .append(", Column ").append(e.getColumnNumber())
                            .append(": ").append(msg).append("\n");
                    }
                    if (errors.size() > 5) {
                        errorDetails.append("\n... and ").append(errors.size() - 5).append(" more errors.");
                    }
                    XmlValidationHelper.showValidationAlert(Alert.AlertType.ERROR, "Schema Validation", errorDetails.toString());
                }
            }
        } catch (Exception e) {
            logger.error("Error during validation", e);
            XmlValidationHelper.showValidationAlert(Alert.AlertType.ERROR, "Validation Error",
                "An error occurred during validation:\n" + e.getMessage());
        }

        // Also update the sidebar status
        validateXml();
    }


    public void validateSchematron() {
        if (sidebarController == null) return;

        if (schematronFile == null) {
            sidebarController.updateSchematronValidationStatus("No Schematron rules selected", "orange", null);
            return;
        }

        try {
            String xmlContent = codeArea.getText();
            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                sidebarController.updateSchematronValidationStatus("No XML content", "orange", null);
                return;
            }

            // Use the SchematronService for validation
            List<SchematronService.SchematronValidationError> errors = schematronService.validateXml(xmlContent, schematronFile);

            if (errors == null || errors.isEmpty()) {
                sidebarController.updateSchematronValidationStatus("✓ Valid", "green", null);
            } else {
                // Create a short summary for the label
                String shortMessage = String.format("✗ Invalid (%d error%s)",
                        errors.size(), errors.size() == 1 ? "" : "s");

                // Use the new method that stores the error details and shows the details button
                sidebarController.updateSchematronValidationStatus(shortMessage, "red", errors);

                logger.info("Schematron validation: ✗ Invalid ({} error(s))", errors.size());
                for (SchematronService.SchematronValidationError error : errors) {
                    logger.info("Schematron error: {} at line {}, column {} (Rule: {})",
                            error.message(), error.lineNumber(), error.columnNumber(), error.ruleId());
                }
            }
        } catch (SchematronLoadException e) {
            // Handle Schematron loading errors specifically
            sidebarController.updateSchematronValidationStatus("Failed to load Schematron", "red", null);
            logger.error("Failed to load Schematron file: {}", schematronFile.getName(), e);

            // Show error dialog to the user
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Schematron Loading Error");
                alert.setHeaderText("Failed to load or compile Schematron file");
                alert.setContentText(e.getMessage() + "\n\nPlease check that the Schematron file is valid and try again.");
                alert.showAndWait();
            });
        } catch (Exception e) {
            sidebarController.updateSchematronValidationStatus("Error during validation", "red", null);
            logger.error("Error during Schematron validation", e);
        }
    }

    /**
     * Extracts element names from an XSD file for IntelliSense completion.
     *
     * @param xsdFile The XSD file to extract element names from
     * @return List of element names found in the XSD
     */
    private List<String> extractElementNamesFromXsd(File xsdFile) {
        return XmlValidationHelper.extractElementNamesFromXsd(xsdFile);
    }

    /**
     * Extracts context-sensitive element names (parent-child relationships) from XSD file.
     * Uses simple DOM parsing to get mandatory children only.
     *
     * @param xsdFile The XSD file to extract context information from
     * @return Map of parent element names to their mandatory child element names only
     */
    private Map<String, List<String>> extractContextElementNamesFromXsd(File xsdFile) {
        return XmlValidationHelper.extractContextElementNamesFromXsd(xsdFile);
    }

    private void applyStyles() {
        if (codeArea.getText().length() >= MAX_SIZE_FOR_FORMATTING) return;
        StyleSpans<Collection<String>> syntaxHighlighting = org.fxt.freexmltoolkit.controls.shared.XmlSyntaxHighlighter.computeHighlighting(codeArea.getText());
        codeArea.setStyleSpans(0, syntaxHighlighting);
    }

    /**
     * Updates element information using XSD-based implementation for better accuracy.
     * This method tries to get the current element name and type from the XSD schema.
     *
     * @param caretPosition The current cursor position
     */
    private void updateElementInfoFromXsd(int caretPosition) {
        try {
            // Get the current XPath position in the XML
            String currentXPath = getCurrentXPath(codeArea.getText(), caretPosition);

            if (xsdDocumentationData != null && currentXPath != null && !currentXPath.isEmpty()) {
                // Look up the element information in the extendedXsdElementMap
                XsdExtendedElement elementInfo = xsdDocumentationData.getExtendedXsdElementMap().get(currentXPath);

                if (elementInfo == null) {
                    // Try to find a partial match or parent element
                    elementInfo = findBestMatchingElement(currentXPath);
                }

                if (elementInfo != null) {
                    // Update sidebar with XSD-based information
                    sidebarController.setElementName(elementInfo.getElementName());
                    sidebarController.setElementType(elementInfo.getElementType() != null ? elementInfo.getElementType() : "");
                    return;
                }
            }

            // Fallback to manual parsing if XSD data is not available
            ElementInfo fallbackInfo = getElementInfoAtPosition(codeArea.getText(), caretPosition);
            sidebarController.setElementName(fallbackInfo.name);
            sidebarController.setElementType(fallbackInfo.type);

        } catch (Exception e) {
            logger.debug("Error getting element info from XSD: {}", e.getMessage());
            // Fallback to manual parsing
            ElementInfo elementInfo = getElementInfoAtPosition(codeArea.getText(), caretPosition);
            sidebarController.setElementName(elementInfo.name);
            sidebarController.setElementType(elementInfo.type);
        }
    }


    /**
     * Extracts element name by analyzing the text around the cursor position.
     * This method finds the innermost/current XML node, not just the root element.
     */
    private String extractElementNameFromPosition(int caretPosition) {
        String text = codeArea.getText();
        if (text == null || text.isEmpty() || caretPosition < 0 || caretPosition > text.length()) {
            return "Unknown";
        }

        try {
            // Use the shared element stack building logic
            Deque<String> elementStack = buildElementStackToPosition(text, caretPosition);
            
            // The current element is the top of the stack
            if (!elementStack.isEmpty()) {
                return elementStack.peek();
            }

            // If stack is empty, check if we're directly within a tag
            return findElementAtCursorPosition(text, caretPosition);

        } catch (Exception e) {
            logger.debug("Error extracting element name from position: {}", e.getMessage());
        }

        return "Unknown";
    }

    /**
     * Helper method to find if cursor is directly within an XML tag.
     */
    private String findElementAtCursorPosition(String text, int caretPosition) {
        try {
            // Look in a small window around the cursor
            int windowStart = Math.max(0, caretPosition - 100);
            int windowEnd = Math.min(text.length(), caretPosition + 100);
            String window = text.substring(windowStart, windowEnd);
            int relativePos = caretPosition - windowStart;

            // Find tags that contain the cursor position
            Pattern tagPattern = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9_:]*)[^>]*>");
            Matcher matcher = tagPattern.matcher(window);

            while (matcher.find()) {
                int tagStart = matcher.start();
                int tagEnd = matcher.end();

                // Check if cursor is within this tag
                if (relativePos >= tagStart && relativePos <= tagEnd) {
                    return matcher.group(2); // Return element name
                }
            }

        } catch (Exception e) {
            logger.debug("Error finding element at cursor position: {}", e.getMessage());
        }

        return "Unknown";
    }

    /**
     * Helper class for tracking XML tags during parsing.
     */
    private record TagMatch(int position, String name, TagType type) {
    }

    private enum TagType {
        OPEN, CLOSE, SELF_CLOSING
    }

    /**
     * Updates element documentation using XSD documentation data.
     *
     * @param caretPosition The current cursor position
     */
    private void updateElementDocumentation(int caretPosition) {
        if (xmlFile == null) return;

        // Use XSD documentation data directly
        tryXsdDocumentationFallback(caretPosition);
    }

    /**
     * Fallback method to extract documentation using XsdDocumentationData and XPath lookup.
     *
     * @param caretPosition The current cursor position
     */
    private void tryXsdDocumentationFallback(int caretPosition) {
        if (xsdDocumentationData == null || xsdFile == null || !xsdFile.exists()) {
            Platform.runLater(() -> updateDocumentationDisplay(""));
            return;
        }

        try {
            // Get the current XPath position in the XML
            String currentXPath = getCurrentXPath(codeArea.getText(), caretPosition);

            logger.debug("XSD fallback: current XPath: '{}'", currentXPath);

            if (currentXPath == null || currentXPath.isEmpty()) {
                Platform.runLater(() -> updateDocumentationDisplay(""));
                return;
            }

            // Look up the element information in the extendedXsdElementMap
            XsdExtendedElement elementInfo = xsdDocumentationData.getExtendedXsdElementMap().get(currentXPath);

            if (elementInfo == null) {
                // Try to find a partial match or parent element
                elementInfo = findBestMatchingElement(currentXPath);
            }

            if (elementInfo != null) {
                // Get documentation from the extended element
                String documentation = getDocumentationFromExtendedElement(elementInfo);
                String elementType = elementInfo.getElementType();

                // Make variables effectively final for lambda
                final String finalDocumentation = documentation;
                final String finalElementType = elementType;
                final String finalElementName = elementInfo.getElementName();
                final List<String> finalExampleValues = elementInfo.getExampleValues();

                logger.debug("Found element info for XPath '{}': type='{}', doc='{}'",
                        currentXPath, finalElementType, finalDocumentation);

                // Update sidebar with comprehensive information
                Platform.runLater(() -> {
                    updateDocumentationDisplay(finalDocumentation != null ? finalDocumentation : "");
                    if (sidebarController != null) {
                        sidebarController.setElementName(finalElementName);
                        sidebarController.setElementType(finalElementType != null ? finalElementType : "");
                        sidebarController.setExampleValues(finalExampleValues);
                    }
                });
            } else {
                logger.debug("No element info found for XPath '{}'", currentXPath);
                Platform.runLater(() -> updateDocumentationDisplay(""));
            }

        } catch (Exception e) {
            logger.debug("Error extracting documentation from XsdDocumentationData: {}", e.getMessage());
            Platform.runLater(() -> updateDocumentationDisplay(""));
        }
    }

    /**
     * Tries to find the best matching element in the extendedXsdElementMap for a given XPath.
     * This method handles XSD structure markers (SEQUENCE_, CHOICE_, ALL_) by cleaning paths
     * and using a scoring algorithm to find the best context match.
     *
     * @param targetXPath The XPath to find a match for
     * @return The best matching XsdExtendedElement or null
     */
    public XsdExtendedElement findBestMatchingElement(String targetXPath) {
        if (xsdDocumentationData == null || targetXPath == null) {
            return null;
        }

        Map<String, XsdExtendedElement> elementMap = xsdDocumentationData.getExtendedXsdElementMap();
        if (elementMap == null || elementMap.isEmpty()) {
            return null;
        }

        // Try exact match first
        XsdExtendedElement exactMatch = elementMap.get(targetXPath);
        if (exactMatch != null) {
            return exactMatch;
        }

        // Clean the XML XPath (remove empty parts)
        List<String> cleanXmlPath = new ArrayList<>();
        for (String part : targetXPath.split("/")) {
            if (!part.isEmpty()) {
                cleanXmlPath.add(part);
            }
        }

        if (cleanXmlPath.isEmpty()) {
            return null;
        }

        // Get the element name we're looking for (last part of path)
        String elementName = cleanXmlPath.get(cleanXmlPath.size() - 1);

        // Find best matching element using path-cleaning and scoring
        XsdExtendedElement bestMatch = null;
        int bestMatchScore = -1;

        for (Map.Entry<String, XsdExtendedElement> entry : elementMap.entrySet()) {
            String mapXPath = entry.getKey();
            XsdExtendedElement element = entry.getValue();

            // Skip container elements
            String elName = element.getElementName();
            if (elName == null || elName.startsWith("SEQUENCE_") || elName.startsWith("CHOICE_") || elName.startsWith("ALL_")) {
                continue;
            }

            // Check if element name matches
            if (!elName.equals(elementName)) {
                continue;
            }

            // Clean the map XPath (remove container elements like SEQUENCE_, CHOICE_, ALL_)
            List<String> cleanMapPath = new ArrayList<>();
            for (String part : mapXPath.split("/")) {
                if (!part.isEmpty() && !part.startsWith("SEQUENCE_") && !part.startsWith("CHOICE_") && !part.startsWith("ALL_")) {
                    cleanMapPath.add(part);
                }
            }

            // Calculate score: compare full path from the start
            // This ensures we match the correct context (e.g., /FundsXML4/AssetMasterData/Asset vs /FundsXML4/Fund/Asset)
            int score = 0;
            boolean fullMatch = cleanXmlPath.size() == cleanMapPath.size();

            // Score based on how many path elements match from the start
            int minLen = Math.min(cleanXmlPath.size(), cleanMapPath.size());
            boolean pathMatches = true;
            for (int i = 0; i < minLen; i++) {
                if (cleanXmlPath.get(i).equals(cleanMapPath.get(i))) {
                    score++;
                } else {
                    pathMatches = false;
                    break;
                }
            }

            // If the full path matches exactly, give it a much higher score
            if (fullMatch && pathMatches && score == cleanXmlPath.size()) {
                score += 1000; // Bonus for exact match
            }

            // Prefer matches with higher score
            if (score > bestMatchScore) {
                bestMatchScore = score;
                bestMatch = element;
            }
        }

        if (bestMatch != null) {
            logger.debug("Found element by path matching (score={}): elementName='{}', xpath='{}'",
                    bestMatchScore, bestMatch.getElementName(), bestMatch.getCurrentXpath());
            return bestMatch;
        }

        logger.debug("No match found for xpath='{}', elementName='{}'", targetXPath, elementName);
        return null;
    }

    /**
     * Extracts the element name from an XPath string.
     *
     * @param xpath The XPath string
     * @return The element name or null
     */
    private String getElementNameFromXPath(String xpath) {
        if (xpath == null || xpath.isEmpty()) {
            return null;
        }

        // Get the last element in the path
        String[] parts = xpath.split("/");
        if (parts.length > 0) {
            String lastPart = parts[parts.length - 1];
            // Remove any array indices like [1], [2], etc.
            return lastPart.replaceAll("\\[\\d+\\]", "");
        }

        return null;
    }

    /**
     * Extracts documentation text from an XsdExtendedElement.
     *
     * @param element The XsdExtendedElement
     * @return The documentation string or null
     */
    public String getDocumentationFromExtendedElement(XsdExtendedElement element) {
        if (element == null) {
            return null;
        }

        // Get documentation from the element
        List<XsdExtendedElement.DocumentationInfo> documentations = element.getDocumentations();
        if (documentations != null && !documentations.isEmpty()) {
            StringBuilder docBuilder = new StringBuilder();

            for (XsdExtendedElement.DocumentationInfo docInfo : documentations) {
                if (docInfo.content() != null && !docInfo.content().trim().isEmpty()) {
                    if (docBuilder.length() > 0) {
                        docBuilder.append("\n\n");
                    }

                    // Add language prefix if available
                    if (docInfo.lang() != null && !docInfo.lang().isEmpty()) {
                        docBuilder.append("[").append(docInfo.lang()).append("] ");
                    }

                    docBuilder.append(docInfo.content().trim());
                }
            }

            return docBuilder.length() > 0 ? docBuilder.toString() : null;
        }

        return null;
    }

    /**
     * Extracts documentation for a specific element from the XSD schema.
     *
     * @param elementName The name of the element to find documentation for
     * @return The documentation string or null if not found
     */
    private String extractDocumentationFromXsd(String elementName) {
        if (xsdFile == null || !xsdFile.exists() || elementName == null || elementName.isEmpty()) {
            return null;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document xsdDoc = builder.parse(xsdFile);

            // Look for element documentation in various locations using Saxon XPath 3.1
            String[] xpathQueries = {
                    "//xs:element[@name='" + elementName + "']/xs:annotation/xs:documentation",
                    "//xsd:element[@name='" + elementName + "']/xsd:annotation/xsd:documentation",
                    "//element[@name='" + elementName + "']/annotation/documentation",
                    // Also check complexTypes and simpleTypes that might be referenced
                    "//xs:complexType[@name='" + elementName + "']/xs:annotation/xs:documentation",
                    "//xsd:complexType[@name='" + elementName + "']/xsd:annotation/xsd:documentation",
                    "//xs:simpleType[@name='" + elementName + "']/xs:annotation/xs:documentation",
                    "//xsd:simpleType[@name='" + elementName + "']/xsd:annotation/xsd:documentation"
            };

            for (String query : xpathQueries) {
                List<XdmNode> nodes = SaxonXPathHelper.evaluateNodes(xsdDoc, query, SaxonXPathHelper.XSD_NAMESPACES);
                if (!nodes.isEmpty()) {
                    StringBuilder documentation = new StringBuilder();
                    for (XdmNode node : nodes) {
                        String content = SaxonXPathHelper.getTextContent(node);
                        if (content != null && !content.trim().isEmpty()) {
                            if (documentation.length() > 0) {
                                documentation.append("\n\n");
                            }
                            documentation.append(content.trim());
                        }
                    }
                    if (documentation.length() > 0) {
                        return documentation.toString();
                    }
                }
            }

        } catch (Exception e) {
            logger.debug("Error parsing XSD for documentation: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Loads XSD documentation data asynchronously to avoid blocking the UI.
     */
    private void loadXsdDocumentationDataAsync() {
        if (xsdFile == null || !xsdFile.exists()) {
            return;
        }

        // Run in background thread
        CompletableFuture.runAsync(() -> {
            try {
                logger.debug("Loading XSD documentation data from: {}", xsdFile.getAbsolutePath());

                // Use the class instance XsdDocumentationService to parse the XSD
                xsdDocumentationService.setXsdFilePath(xsdFile.getAbsolutePath());
                xsdDocumentationService.processXsd(true);

                // Get the populated documentation data
                this.xsdDocumentationData = xsdDocumentationService.xsdDocumentationData;

                logger.debug("Successfully loaded XSD documentation data with {} elements",
                        xsdDocumentationData.getExtendedXsdElementMap().size());

                // Notify editor to refresh its XSD integration data
                // Invalidate cache to trigger reload with new schema data
                if (xmlCodeEditorV2 != null && xmlCodeEditorV2.getIntelliSenseEngine() != null) {
                    xmlCodeEditorV2.getIntelliSenseEngine().invalidateCacheForSchema();
                    logger.debug("IntelliSense cache invalidated after XSD documentation loaded");
                }

                // Update the graphic view's schema if it's currently showing
                if (currentGraphicViewContext != null && xsdDocumentationData != null) {
                    Platform.runLater(() -> {
                        currentGraphicViewContext.setSchema(xsdDocumentationData);
                        logger.info("XSD schema updated on graphic view context with {} elements",
                                xsdDocumentationData.getExtendedXsdElementMap() != null
                                        ? xsdDocumentationData.getExtendedXsdElementMap().size() : 0);
                    });
                }

            } catch (Exception e) {
                logger.debug("Error loading XSD documentation data: {}", e.getMessage());
                this.xsdDocumentationData = null;
            }
        });
    }

    /**
     * Updates the documentation display in the sidebar.
     *
     * @param documentation The documentation text to display
     */
    private void updateDocumentationDisplay(String documentation) {
        if (sidebarController != null) {
            sidebarController.setDocumentation(documentation);
        }
    }

    /**
     * Updates the example values display in the sidebar.
     *
     * @param exampleValues The list of example values to display
     */
    private void updateExampleValuesDisplay(List<String> exampleValues) {
        if (sidebarController != null) {
            sidebarController.setExampleValues(exampleValues);
        }
    }

    /**
     * Updates example values based on the current XPath and XSD schema.
     *
     * @param xpath The current XPath to find example values for
     */
    private void updateExampleValuesFromXsd(String xpath) {
        if (sidebarController == null) return;
        
        if (xsdFile == null || xpath == null || xpath.equals("Invalid XML structure") ||
                xpath.equals("No XML content") || xpath.equals("Unable to determine XPath")) {
            sidebarController.setExampleValues(Collections.singletonList("No XSD schema loaded"));
            return;
        }

        try {
            // Get the current element name from XPath
            String[] pathParts = xpath.split("/");
            if (pathParts.length == 0) {
                sidebarController.setExampleValues(Collections.singletonList("No element selected"));
                return;
            }

            String currentElementName = pathParts[pathParts.length - 1];
            if (currentElementName.isEmpty()) {
                sidebarController.setExampleValues(Collections.singletonList("No element selected"));
                return;
            }

            // Extract example values from XSD
            List<String> exampleValues = getExampleValuesFromXsd(currentElementName);
            if (exampleValues.isEmpty()) {
                sidebarController.setExampleValues(Collections.singletonList("No example values found for: " + currentElementName));
            } else {
                sidebarController.setExampleValues(exampleValues);
            }
        } catch (Exception e) {
            logger.error("Error getting example values", e);
            sidebarController.setExampleValues(Collections.singletonList("Error: " + e.getMessage()));
        }
    }

    /**
     * Extracts example values from XSD schema for a given element.
     *
     * @param elementName The name of the element to find example values for
     * @return List of example values
     */
    private List<String> getExampleValuesFromXsd(String elementName) {
        List<String> exampleValues = new ArrayList<>();

        try {
            // Parse the XSD file
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document xsdDoc = builder.parse(xsdFile);

            // Remove namespace prefix if present
            String cleanElementName = elementName;
            if (elementName.contains(":")) {
                cleanElementName = elementName.split(":")[1];
            }

            // Combined namespace map including Altova extensions
            Map<String, String> namespaces = new HashMap<>();
            namespaces.putAll(SaxonXPathHelper.XSD_NAMESPACES);
            namespaces.put("altova", "http://www.altova.com/xml-schema-extensions");

            // Try to find Altova example values using Saxon XPath 3.1
            String altovaExamplesQuery = "//xs:element[@name='" + cleanElementName + "']/xs:annotation/xs:appinfo/altova:exampleValues/altova:example/@value";
            List<String> altovaValues = SaxonXPathHelper.evaluateStringList(xsdDoc, altovaExamplesQuery, namespaces);
            for (String value : altovaValues) {
                if (value != null && !value.trim().isEmpty()) {
                    exampleValues.add(value.trim());
                }
            }

            // Also check documentation for example values (legacy support)
            String documentationQuery = "//xs:element[@name='" + cleanElementName + "']/xs:annotation/xs:documentation";
            List<XdmNode> docNodes = SaxonXPathHelper.evaluateNodes(xsdDoc, documentationQuery, SaxonXPathHelper.XSD_NAMESPACES);
            for (XdmNode docNode : docNodes) {
                String content = SaxonXPathHelper.getTextContent(docNode);
                if (content != null && !content.trim().isEmpty()) {
                    // Extract example values from documentation
                    String[] lines = content.split("\n");
                    for (String line : lines) {
                        line = line.trim();
                        if (line.startsWith("Example:") || line.startsWith("example:")) {
                            String example = line.substring(line.indexOf(":") + 1).trim();
                            if (!example.isEmpty()) {
                                exampleValues.add(example);
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.debug("Error extracting example values from XSD: {}", e.getMessage());
        }

        return exampleValues;
    }

    private void showPopOver() {
        Point2D screenPos = codeArea.localToScreen(codeArea.getCaretBounds().get().getMaxX(), codeArea.getCaretBounds().get().getMaxY());
        hoverPopOver.show(codeArea.getScene().getWindow(), screenPos.getX(), screenPos.getY() + 5);
    }

    /**
     * Auto-formats the XML content if the setting is enabled.
     */
    private void autoFormatIfEnabled() {
        PropertiesService propertiesService = ServiceRegistry.get(PropertiesService.class);
        if (propertiesService.isXmlAutoFormatAfterLoading()) {
            logger.debug("Auto-formatting XML content after loading file");
            Platform.runLater(() -> {
                String currentText = codeArea.getText();
                if (currentText != null && !currentText.trim().isEmpty()) {
                    try {
                        String formattedText = XmlService.prettyFormat(currentText, propertiesService.getXmlIndentSpaces());
                        if (formattedText != null && !formattedText.isEmpty()) {
                            xmlCodeEditorV2.setText(formattedText);
                            logger.debug("XML content auto-formatted successfully");
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to auto-format XML content: {}", e.getMessage());
                    }
                }
            });
        }
    }

    public File getXmlFile() {
        return xmlFile;
    }

    /**
     * Returns the original XML content for XPath queries.
     * This is the content as loaded from the file, not modified by XPath query results.
     * Falls back to current editor content if no original is stored.
     *
     * @return the original XML content, or current editor content as fallback
     */
    public String getOriginalXmlForXPath() {
        if (originalXmlForXPath != null && !originalXmlForXPath.isEmpty()) {
            return originalXmlForXPath;
        }
        // Fallback to current editor content (for new unsaved files)
        return codeArea.getText();
    }

    /**
     * Stores the current editor content as the original XML for XPath queries.
     * Call this after user edits to update what XPath queries will run against.
     */
    public void updateOriginalXmlForXPath() {
        originalXmlForXPath = codeArea.getText();
    }

    public void setXmlFile(File xmlFile) {
        this.xmlFile = xmlFile;
        this.setText(xmlFile.getName());
        refreshTextView();

        xmlService.setCurrentXmlFile(xmlFile);

        // Set the document URI for completion requests
        if (xmlCodeEditorV2 != null) {
            xmlCodeEditorV2.setDocumentUri(xmlFile.toURI().toString());
        }

        // Auto-format XML if setting is enabled
        autoFormatIfEnabled();

        // Try to automatically load XSD schema from XML file
        if (xmlService.loadSchemaFromXMLFile()) {
            File loadedXsdFile = xmlService.getCurrentXsdFile();
            if (loadedXsdFile != null) {
                setXsdFile(loadedXsdFile);
            } else {
                // If no XSD was found, clear the XSD field
                if (sidebarController != null) {
                    sidebarController.setXsdPathField("No XSD schema found in XML");
                    sidebarController.updateValidationStatus("No XSD schema found", "orange");
                }
            }
        } else {
            // If no XSD was found, clear the XSD field
            if (sidebarController != null) {
                sidebarController.setXsdPathField("No XSD schema found in XML");
                sidebarController.updateValidationStatus("No XSD schema found", "orange");
            }
        }
    }

    public void refresh() {
        refreshTextView();
        refreshGraphicView();
    }

    public void refreshTextView() {
        if (xmlFile == null || !xmlFile.exists()) {
            codeArea.clear();
            document = null;
            originalXmlForXPath = null;
            return;
        }
        try {
            final String content = Files.readString(xmlFile.toPath(), StandardCharsets.UTF_8);
            setEditorText(content);
            // Store original XML for XPath queries
            originalXmlForXPath = content;

            // Also initialize the DOM when loading a file
            if (!content.isEmpty()) {
                try {
                    document = db.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
                } catch (SAXException e) {
                    logger.warn("Could not parse XML file for DOM: {}", e.getMessage());
                    document = null;
                }
            }
        } catch (IOException e) {
            setEditorText("Error: Could not read file.\n" + e.getMessage());
            document = null;
        }
    }

    private void refreshGraphicView() {
        try {
            refreshGraphicViewV2();
        } catch (Exception e) {
            logger.error("Error refreshing graphic view: {}", e.getMessage(), e);
        }
    }

    /**
     * Refreshes the graphic view using V2 XmlCanvasView (XMLSpy Grid-style).
     */
    private void refreshGraphicViewV2() {
        try {
            String xmlContent = codeArea.getText();
            if (xmlContent == null || xmlContent.isBlank()) {
                showGraphicViewPlaceholder("No XML content to display.");
                return;
            }

            // Create XmlEditorContext and parse the XML content
            XmlEditorContext xmlEditorContext = new XmlEditorContext();
            try {
                xmlEditorContext.loadDocumentFromString(xmlContent);
            } catch (Exception parseException) {
                logger.warn("Failed to parse XML for graphic view: {}", parseException.getMessage());
                showGraphicViewPlaceholder("Cannot display graphic view:\n\n" + parseException.getMessage() +
                        "\n\nFix the XML errors first.");
                return;
            }

            // Store reference to context for later schema update (if XSD loads async)
            this.currentGraphicViewContext = xmlEditorContext;

            // Set schema for schema-aware editing if XSD is available
            if (xsdDocumentationData != null) {
                xmlEditorContext.setSchema(xsdDocumentationData);
                logger.info("XSD schema set for graphical XML editor V2 with {} elements",
                        xsdDocumentationData.getExtendedXsdElementMap() != null
                                ? xsdDocumentationData.getExtendedXsdElementMap().size() : 0);
            } else {
                logger.info("No XSD documentation data available yet for graphical XML editor V2 - will be set when loaded");
            }

            // Create XmlCanvasView with the context (XMLSpy Grid-style view)
            XmlCanvasView canvasView = new XmlCanvasView(xmlEditorContext);
            canvasView.expandAll();

            // Sync changes back to text view
            canvasView.setOnDocumentModified(xml -> {
                if (xml != null && !xml.equals(codeArea.getText())) {
                    int caretPos = codeArea.getCaretPosition();
                    codeArea.replaceText(xml);
                    // Try to restore caret position
                    if (caretPos < xml.length()) {
                        codeArea.moveTo(caretPos);
                    }
                }
            });

            // Ensure canvas view fills available space
            canvasView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            VBox.setVgrow(canvasView, Priority.ALWAYS);

            // Create container with StackPane for toast support
            javafx.scene.layout.StackPane toastContainer = new javafx.scene.layout.StackPane();
            canvasView.setToastContainer(toastContainer);

            // Main content area (canvas view)
            VBox contentBox = new VBox(canvasView);
            contentBox.setFillWidth(true);
            VBox.setVgrow(canvasView, Priority.ALWAYS);
            VBox.setVgrow(contentBox, Priority.ALWAYS);

            // Wrap content in StackPane for toast overlay
            toastContainer.getChildren().add(contentBox);
            toastContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

            // No ScrollPane needed - XmlCanvasView has its own scrollbar
            this.graphic.setContent(toastContainer);

            // Connect V2 selection to existing sidebar
            connectV2SelectionToSidebar(xmlEditorContext);

            logger.info("V2 Graphic view (XmlCanvasView) loaded successfully");
        } catch (Exception e) {
            logger.error("Error creating V2 graphic view: {}", e.getMessage(), e);
            showGraphicViewPlaceholder("Error loading graphic view:\n\n" + e.getMessage());
        }
    }

    /**
     * Shows a placeholder message in the graphic view.
     */
    private void showGraphicViewPlaceholder(String message) {
        // Clear the reference to the graphic view context since we're showing a placeholder
        this.currentGraphicViewContext = null;

        VBox vBox = new VBox();
        vBox.setPadding(new Insets(20));

        Label infoLabel = new Label(message);
        infoLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");
        infoLabel.setWrapText(true);
        vBox.getChildren().add(infoLabel);

        ScrollPane pane = new ScrollPane(vBox);
        this.graphic.setContent(pane);
    }

    /**
     * Connects the V2 XmlEditorContext selection model to the existing sidebar.
     * When a node is selected in the graphic view, the sidebar properties are updated.
     *
     * @param xmlEditorContext the V2 editor context to connect
     */
    private void connectV2SelectionToSidebar(XmlEditorContext xmlEditorContext) {
        if (sidebarController == null) {
            logger.warn("Cannot connect V2 selection to sidebar - sidebarController is null");
            return;
        }

        // Listen to selection changes in the V2 SelectionModel
        xmlEditorContext.getSelectionModel().addPropertyChangeListener("selectedNode", evt -> {
            Object newValue = evt.getNewValue();
            Platform.runLater(() -> updateSidebarFromV2Selection(newValue));
        });

        logger.debug("Connected V2 SelectionModel to sidebar");
    }

    /**
     * Updates the sidebar with information from the V2 selected node.
     *
     * @param selectedNode the selected XmlNode (can be null)
     */
    private void updateSidebarFromV2Selection(Object selectedNode) {
        if (sidebarController == null) {
            return;
        }

        if (selectedNode == null) {
            // Clear sidebar fields when nothing is selected
            sidebarController.setElementName("");
            sidebarController.setElementType("");
            sidebarController.setDocumentation("");
            sidebarController.setXPath("");
            sidebarController.setExampleValues(List.of("No element selected"));
            sidebarController.setPossibleChildElements(List.of("No element selected"));
            return;
        }

        if (selectedNode instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement element) {
            // Update element name
            String displayName = element.getNamespacePrefix() != null && !element.getNamespacePrefix().isEmpty()
                    ? element.getNamespacePrefix() + ":" + element.getName()
                    : element.getName();
            sidebarController.setElementName(displayName);

            // Build XPath for the selected element
            String xpath = buildXPathForV2Element(element);
            sidebarController.setXPath(xpath);

            logger.debug("updateSidebarFromV2Selection: element='{}', xpath='{}', xsdDocumentationData={}",
                    displayName, xpath, xsdDocumentationData != null ? "present (" +
                    (xsdDocumentationData.getExtendedXsdElementMap() != null ?
                     xsdDocumentationData.getExtendedXsdElementMap().size() : 0) + " elements)" : "null");

            // Try to get XSD information if available
            if (xsdDocumentationData != null) {
                XsdExtendedElement xsdElement = findBestMatchingElement(xpath);
                logger.debug("findBestMatchingElement result: {}", xsdElement != null ?
                        "found '" + xsdElement.getElementName() + "'" : "null");
                if (xsdElement != null) {
                    sidebarController.setElementType(xsdElement.getElementType() != null ? xsdElement.getElementType() : "");
                    sidebarController.setDocumentation(getDocumentationFromExtendedElement(xsdElement));

                    // Set example values
                    if (xsdElement.getExampleValues() != null && !xsdElement.getExampleValues().isEmpty()) {
                        sidebarController.setExampleValues(xsdElement.getExampleValues());
                    } else {
                        sidebarController.setExampleValues(List.of("No example values available"));
                    }

                    // Set child elements - format to resolve SEQUENCE_, CHOICE_, ALL_ containers
                    if (xsdElement.getChildren() != null && !xsdElement.getChildren().isEmpty()) {
                        List<String> formattedChildren = formatChildElementsForDisplay(xsdElement.getChildren(), true);
                        if (formattedChildren.isEmpty()) {
                            sidebarController.setPossibleChildElements(List.of("No child elements defined"));
                        } else {
                            sidebarController.setPossibleChildElements(formattedChildren);
                        }
                    } else {
                        sidebarController.setPossibleChildElements(List.of("No child elements defined"));
                    }
                } else {
                    // No XSD info available
                    sidebarController.setElementType("Unknown (no XSD)");
                    sidebarController.setDocumentation("No XSD documentation available");
                    sidebarController.setExampleValues(List.of("Link XSD for example values"));
                    sidebarController.setPossibleChildElements(getV2ChildElementNames(element));
                }
            } else {
                // No XSD linked
                sidebarController.setElementType("Unknown (no XSD linked)");
                sidebarController.setDocumentation("Link an XSD schema to see documentation");
                sidebarController.setExampleValues(List.of("Link XSD for example values"));
                sidebarController.setPossibleChildElements(getV2ChildElementNames(element));
            }

        } else if (selectedNode instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText textNode) {
            sidebarController.setElementName("Text Node");
            sidebarController.setElementType("Text");
            sidebarController.setDocumentation("Text content: " + truncateText(textNode.getText(), 200));
            sidebarController.setXPath("");
            sidebarController.setExampleValues(List.of("N/A for text nodes"));
            sidebarController.setPossibleChildElements(List.of("Text nodes have no children"));

        } else if (selectedNode instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment commentNode) {
            sidebarController.setElementName("Comment");
            sidebarController.setElementType("Comment");
            sidebarController.setDocumentation("Comment: " + truncateText(commentNode.getText(), 200));
            sidebarController.setXPath("");
            sidebarController.setExampleValues(List.of("N/A for comments"));
            sidebarController.setPossibleChildElements(List.of("Comments have no children"));

        } else {
            // Generic XmlNode
            sidebarController.setElementName("Node");
            sidebarController.setElementType(selectedNode.getClass().getSimpleName());
            sidebarController.setDocumentation("");
            sidebarController.setXPath("");
            sidebarController.setExampleValues(List.of());
            sidebarController.setPossibleChildElements(List.of());
        }
    }

    /**
     * Builds an XPath expression for a V2 XmlElement.
     *
     * @param element the element to build XPath for
     * @return the XPath expression
     */
    private String buildXPathForV2Element(org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement element) {
        StringBuilder xpath = new StringBuilder();
        List<String> pathParts = new ArrayList<>();

        // Traverse up to root
        org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode current = element;
        while (current != null) {
            if (current instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement elem) {
                String name = elem.getNamespacePrefix() != null && !elem.getNamespacePrefix().isEmpty()
                        ? elem.getNamespacePrefix() + ":" + elem.getName()
                        : elem.getName();

                // Calculate position among siblings with same name
                int position = 1;
                org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode parent = elem.getParent();
                if (parent instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement parentElem) {
                    int sameNameCount = 0;
                    for (org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode sibling : parentElem.getChildren()) {
                        if (sibling instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement siblingElem) {
                            if (siblingElem.getName().equals(elem.getName())) {
                                sameNameCount++;
                                if (sibling == elem) {
                                    position = sameNameCount;
                                    break;
                                }
                            }
                        }
                    }
                    // Only add position if there are multiple siblings with same name
                    if (sameNameCount > 1) {
                        name += "[" + position + "]";
                    }
                }

                pathParts.add(0, name);
            }
            current = current.getParent();
        }

        xpath.append("/");
        xpath.append(String.join("/", pathParts));
        return xpath.toString();
    }

    /**
     * Gets child element names from a V2 XmlElement.
     *
     * @param element the element
     * @return list of child element names
     */
    private List<String> getV2ChildElementNames(org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement element) {
        List<String> childNames = new ArrayList<>();
        for (org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode child : element.getChildren()) {
            if (child instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement childElem) {
                String name = childElem.getNamespacePrefix() != null && !childElem.getNamespacePrefix().isEmpty()
                        ? childElem.getNamespacePrefix() + ":" + childElem.getName()
                        : childElem.getName();
                if (!childNames.contains(name)) {
                    childNames.add(name);
                }
            }
        }
        return childNames.isEmpty() ? List.of("No child elements") : childNames;
    }

    /**
     * Truncates text to a maximum length.
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }


    /**
     * Gets the V2 editor if active.
     * @return V2 editor or null if V1 is active
     */
    public org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2 getXmlCodeEditorV2() {
        return xmlCodeEditorV2;
    }

    /**
     * Checks if V2 editor is active.
     * @return true (V2 is always used, V1 is deprecated)
     */
    public boolean isUsingV2Editor() {
        return true;
    }

    /**
     * Sets whether code folding is enabled.
     * Disabling folding is useful for single-line XML files to prevent performance issues.
     *
     * @param enabled true to enable folding, false to disable
     */
    public void setFoldingEnabled(boolean enabled) {
        this.foldingEnabled = enabled;
        // Pass through to V2 editor
        if (xmlCodeEditorV2 != null) {
            xmlCodeEditorV2.setFoldingEnabled(enabled);
        }
        logger.info("Folding enabled: {}", enabled);
    }

    /**
     * Checks if code folding is enabled.
     *
     * @return true if folding is enabled
     */
    public boolean isFoldingEnabled() {
        return foldingEnabled;
    }

    // ==================== Wrapper methods that work with both V1 and V2 ====================

    /**
     * Sets the text content in the editor (works with both V1 and V2).
     *
     * @param text the text to set
     */
    public void setEditorText(String text) {
        // Use setText() to trigger folding updates and paragraph graphics refresh
        xmlCodeEditorV2.setText(text);

        // Update the document tree in the sidebar
        if (sidebarController != null) {
            sidebarController.updateDocumentTree(text);
        }
    }

    /**
     * Gets the text content from the editor (works with both V1 and V2).
     *
     * @return the current text
     */
    public String getEditorText() {
        return codeArea.getText();
    }

    /**
     * Refreshes syntax highlighting.
     * V2 handles highlighting automatically via SyntaxHighlightManagerV2.
     */
    public void refreshEditorHighlighting() {
        // V2 syntax highlighting is automatic - no action needed
        logger.debug("Syntax highlighting refresh requested (automatic)");
    }

    /**
     * Notifies the editor that the file has been saved.
     */
    public void notifyEditorFileSaved() {
        xmlCodeEditorV2.setDirty(false);
    }

    /**
     * Checks if the editor has unsaved changes.
     *
     * @return true if there are unsaved changes
     */
    public boolean isEditorDirty() {
        return xmlCodeEditorV2.isDirty();
    }

    /**
     * Inserts text at the current cursor position.
     *
     * @param text the text to insert
     */
    public void insertTextAtCursor(String text) {
        int caretPosition = codeArea.getCaretPosition();
        codeArea.insertText(caretPosition, text);
    }

    // ==================== End of wrapper methods ====================

    public XmlService getXmlService() {
        return xmlService;
    }

    public void refreshTextViewFromDom() {
        if (document != null) {
            try {
                // Transform the DOM back to XML string
                javax.xml.transform.dom.DOMSource source = new javax.xml.transform.dom.DOMSource(document);
                java.io.StringWriter writer = new java.io.StringWriter();
                javax.xml.transform.stream.StreamResult result = new javax.xml.transform.stream.StreamResult(writer);
                transformer.transform(source, result);

                // Update the text editor with the new content
                String newXmlContent = writer.toString();
                setEditorText(newXmlContent);

                // Update the file if it exists
                if (xmlFile != null && xmlFile.exists()) {
                    try {
                        Files.writeString(xmlFile.toPath(), newXmlContent, StandardCharsets.UTF_8);
                        notifyEditorFileSaved();
                    } catch (IOException e) {
                        logger.error("Failed to write updated XML to file", e);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to refresh text view from DOM", e);
            }
        }
    }

    // Store the original divider position to restore later
    private double savedDividerPosition = 0.8;

    /**
     * Sets the visibility of the XML Editor Sidebar.
     *
     * @param visible true to show the sidebar, false to hide it completely
     */
    public void setXmlEditorSidebarVisible(boolean visible) {
        if (splitPane != null && splitPane.getItems().size() > 1) {
            javafx.scene.Node sidebarNode = splitPane.getItems().get(1); // Sidebar is the second item

            if (visible) {
                // Show sidebar - restore visibility and divider position
                sidebarNode.setVisible(true);
                sidebarNode.setManaged(true);

                // Restore the saved divider position
                Platform.runLater(() -> {
                    splitPane.setDividerPositions(savedDividerPosition);
                    logger.debug("Restored SplitPane divider position to: {}", savedDividerPosition);
                });

                logger.debug("XML Editor Sidebar shown");
            } else {
                // Hide sidebar - save current position and maximize main content
                if (splitPane.getDividers().size() > 0) {
                    savedDividerPosition = splitPane.getDividerPositions()[0];
                    logger.debug("Saved current divider position: {}", savedDividerPosition);
                }

                sidebarNode.setVisible(false);
                sidebarNode.setManaged(false);

                // Set divider to give all space to main content
                Platform.runLater(() -> {
                    splitPane.setDividerPositions(1.0);
                    logger.debug("Set SplitPane divider to 1.0 (full width for main content)");
                });

                logger.debug("XML Editor Sidebar hidden");
            }
        } else {
            logger.warn("SplitPane or sidebar not available - cannot set visibility");
        }
    }

    /**
     * Test method to verify syntax highlighting is working in the XML editor.
     */
    public void testSyntaxHighlighting() {
        logger.info("Syntax highlighting test - V2 Editor active");
    }

    /**
     * Debug method to check CSS loading in the XML editor.
     */
    public void debugCssLoading() {
        logger.debug("=== Debugging CSS Loading in XmlEditor (V2) ===");
        logger.debug("V2 Editor active with integrated CSS");
    }

    /**
     * Gets the XSD documentation data.
     *
     * @return The XSD documentation data
     */
    public XsdDocumentationData getXsdDocumentationData() {
        return xsdDocumentationData;
    }

    /**
     * Gets the XSD documentation service.
     *
     * @return The XSD documentation service
     */
    public XsdDocumentationService getXsdDocumentationService() {
        return xsdDocumentationService;
    }

    /**
     * Finds a node in the XML document using the given XPath
     *
     * @param xpath The XPath expression to find the node
     * @return The found node or null if not found
     */
    public Node findNodeByXPath(String xpath) {
        if (document == null || xpath == null || xpath.trim().isEmpty()) {
            return null;
        }

        try {
            // Use Saxon XPath 3.1 via SaxonXPathHelper
            XdmNode result = SaxonXPathHelper.evaluateSingleNode(document, xpath, null);
            if (result != null) {
                // Convert XdmNode back to DOM Node if needed
                // Note: This is a simplified version - for complex use cases,
                // consider working directly with XdmNode
                return (Node) result.getUnderlyingNode();
            }
            return null;
        } catch (Exception e) {
            logger.error("Error finding node by XPath: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Builds an XPath for the given DOM node
     *
     * @param node The DOM node to build XPath for
     * @return The XPath string for the node
     */
    public String buildXPathForNode(Node node) {
        if (node == null) {
            return "";
        }

        try {
            StringBuilder xpath = new StringBuilder();
            Node current = node;

            // Build path from current node to root
            while (current != null && current.getNodeType() != Node.DOCUMENT_NODE) {
                if (current.getNodeType() == Node.ELEMENT_NODE) {
                    String nodeName = current.getNodeName();

                    // Add position if there are siblings with same name
                    int position = 1;
                    Node sibling = current.getPreviousSibling();
                    while (sibling != null) {
                        if (sibling.getNodeType() == Node.ELEMENT_NODE &&
                                sibling.getNodeName().equals(nodeName)) {
                            position++;
                        }
                        sibling = sibling.getPreviousSibling();
                    }

                    if (position > 1) {
                        xpath.insert(0, "/" + nodeName + "[" + position + "]");
                    } else {
                        xpath.insert(0, "/" + nodeName);
                    }
                }
                current = current.getParentNode();
            }

            return xpath.toString();
        } catch (Exception e) {
            logger.error("Error building XPath for node: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * Navigates to a specific line in the code editor
     *
     * @param lineNumber The line number to navigate to (1-based)
     */
    public void navigateToLine(int lineNumber) {
        if (codeArea != null) {
            Platform.runLater(() -> {
                try {
                    // Convert to 0-based line number
                    int zeroBasedLine = Math.max(0, lineNumber - 1);

                    // Ensure the line number is within bounds
                    int totalLines = codeArea.getParagraphs().size();
                    if (zeroBasedLine >= totalLines) {
                        zeroBasedLine = totalLines - 1;
                    }

                    // Move cursor to the beginning of the line
                    codeArea.moveTo(zeroBasedLine, 0);

                    // Select the entire line to highlight it
                    codeArea.selectLine();

                    // Scroll to make the line visible
                    codeArea.requestFollowCaret();

                    // Request focus
                    codeArea.requestFocus();

                    logger.info("Navigated to line {} in code editor", lineNumber);
                } catch (Exception e) {
                    logger.error("Error navigating to line {}: {}", lineNumber, e.getMessage(), e);
                }
            });
        }
    }

    /**
     * Navigates to a specific XPath location in both code and graphic view
     *
     * @param xpath The XPath to navigate to
     */
    public void navigateToXPath(String xpath) {
        if (xpath == null || xpath.trim().isEmpty()) {
            logger.warn("Cannot navigate to empty XPath");
            return;
        }

        try {
            logger.info("Navigating to XPath: {}", xpath);

            // Try to find the element in the DOM and estimate line number
            if (document != null) {
                Node targetNode = findNodeByXPath(xpath);
                if (targetNode != null) {
                    // Try to estimate line number based on element position
                    int estimatedLine = estimateLineNumberForNode(targetNode);
                    if (estimatedLine > 0) {
                        navigateToLine(estimatedLine);
                        logger.info("Navigated to estimated line {} for XPath: {}", estimatedLine, xpath);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error navigating to XPath {}: {}", xpath, e.getMessage(), e);
        }
    }


    /**
     * Estimates the line number for a DOM node based on its position in the document
     * This is a heuristic approach since DOM doesn't contain line information
     */
    private int estimateLineNumberForNode(Node targetNode) {
        try {
            // Count all preceding nodes to estimate line position
            int nodeCount = 0;
            nodeCount += countPrecedingNodes(document.getDocumentElement(), targetNode);

            // Rough estimation: each element might span 1-3 lines on average
            // This is very approximate but better than nothing
            return Math.max(1, nodeCount * 2);

        } catch (Exception e) {
            logger.debug("Could not estimate line number for node: {}", e.getMessage());
            return 1;
        }
    }

    /**
     * Recursively counts nodes preceding the target node
     */
    private int countPrecedingNodes(Node current, Node target) {
        if (current == target) {
            return 0;
        }

        int count = 1; // Count current node

        // Count child nodes
        for (int i = 0; i < current.getChildNodes().getLength(); i++) {
            Node child = current.getChildNodes().item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                int childCount = countPrecedingNodes(child, target);
                if (childCount >= 0) {
                    return count + childCount;
                }
                count += countAllNodes(child);
            }
        }

        return -1; // Target not found in this branch
    }

    /**
     * Counts all nodes in a subtree
     */
    private int countAllNodes(Node node) {
        int count = 1; // Count current node
        for (int i = 0; i < node.getChildNodes().getLength(); i++) {
            Node child = node.getChildNodes().item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                count += countAllNodes(child);
            }
        }
        return count;
    }

    /**
     * Saves the current XML content to the associated file.
     * If no file is associated, this method does nothing.
     *
     * @return true if the file was saved successfully, false otherwise
     */
    public boolean saveFile() {
        if (xmlFile == null) {
            return false;
        }

        try {
            String content = getEditorText();
            Files.writeString(xmlFile.toPath(), content, StandardCharsets.UTF_8);
            notifyEditorFileSaved();
            logger.info("File saved: {}", xmlFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            logger.error("Error saving file: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Shows a Save As dialog and saves the current XML content to the selected file.
     *
     * @return true if the file was saved successfully, false otherwise
     */
    public boolean saveAsFile() {
        try {
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle("Save XML File As");
            fileChooser.getExtensionFilters().addAll(
                    new javafx.stage.FileChooser.ExtensionFilter("XML Files", "*.xml"),
                    new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*")
            );

            // Set initial directory and filename from current file if available
            if (xmlFile != null) {
                if (xmlFile.getParent() != null) {
                    File parentDir = new File(xmlFile.getParent());
                    if (parentDir.exists() && parentDir.isDirectory()) {
                        fileChooser.setInitialDirectory(parentDir);
                    }
                }
                fileChooser.setInitialFileName(xmlFile.getName());
            }

            File selectedFile = fileChooser.showSaveDialog(codeArea.getScene().getWindow());
            if (selectedFile != null) {
                String content = getEditorText();
                Files.writeString(selectedFile.toPath(), content, StandardCharsets.UTF_8);

                // Update this editor with the new file
                setXmlFile(selectedFile);

                logger.info("File saved as: {}", selectedFile.getAbsolutePath());
                return true;
            }
        } catch (Exception e) {
            logger.error("Error in Save As: {}", e.getMessage(), e);
        }

        return false;
    }
}