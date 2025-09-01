package org.fxt.freexmltoolkit.controls;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.controlsfx.control.PopOver;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxt.freexmltoolkit.controller.MainController;
import org.fxt.freexmltoolkit.controller.controls.SearchReplaceController;
import org.fxt.freexmltoolkit.controller.controls.XmlEditorSidebarController;
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

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
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

public class XmlEditor extends Tab {

    public static final int MAX_SIZE_FOR_FORMATTING = 1024 * 1024 * 20;
    public static final String DEFAULT_FILE_NAME = "Untitled.xml *";

    private final Tab xml = new Tab("XML");
    private final Tab graphic = new Tab("Graphic");

    private final XmlCodeEditor xmlCodeEditor = new XmlCodeEditor();
    public final CodeArea codeArea = xmlCodeEditor.getCodeArea();


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

    private MainController mainController;

    private PopOver hoverPopOver;
    private final Label popOverLabel = new Label();
    private final PauseTransition hoverDelay = new PauseTransition(Duration.millis(500));

    private SearchReplaceController searchController;
    private PopOver searchPopOver;

    // --- Sidebar Components ---
    private XmlEditorSidebarController sidebarController;
    private SplitPane splitPane;

    // --- Graphic View Component ---
    private XmlGraphicEditor currentGraphicEditor;

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
        init();
        // Set a temporary URI for new documents and parent reference
        if (xmlCodeEditor != null) {
            xmlCodeEditor.setDocumentUri("untitled:" + System.nanoTime() + ".xml");
            xmlCodeEditor.setParentXmlEditor(this);
        }
    }

    public XmlEditor(File file) {
        init();
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

        xml.setContent(xmlCodeEditor);
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

            // Find the element definition
            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(new javax.xml.namespace.NamespaceContext() {
                @Override
                public String getNamespaceURI(String prefix) {
                    if ("xs".equals(prefix)) {
                        return "http://www.w3.org/2001/XMLSchema";
                    }
                    return null;
                }

                @Override
                public String getPrefix(String uri) {
                    if ("http://www.w3.org/2001/XMLSchema".equals(uri)) {
                        return "xs";
                    }
                    return null;
                }

                @Override
                public java.util.Iterator<String> getPrefixes(String uri) {
                    return java.util.Collections.singletonList("xs").iterator();
                }
            });

            // Remove namespace prefix if present
            String cleanElementName = elementName;
            if (elementName.contains(":")) {
                cleanElementName = elementName.split(":")[1];
            }

            // Try to find the element definition
            String elementQuery = "//xs:element[@name='" + cleanElementName + "']";
            Node elementNode = (Node) xpath.evaluate(elementQuery, xsdDoc, XPathConstants.NODE);

            if (elementNode == null) {
                // Try without namespace prefix
                elementQuery = "//element[@name='" + cleanElementName + "']";
                elementNode = (Node) xpath.evaluate(elementQuery, xsdDoc, XPathConstants.NODE);
            }

            if (elementNode != null) {
                // Get the type attribute
                Node typeAttr = elementNode.getAttributes().getNamedItem("type");
                if (typeAttr != null) {
                    String typeName = typeAttr.getNodeValue();

                    // Check if it's a built-in type
                    if (isBuiltInType(typeName)) {
                        // Remove namespace prefix if present to avoid duplication
                        String cleanTypeName = typeName;
                        if (typeName.contains(":")) {
                            cleanTypeName = typeName.split(":")[1];
                        }
                        return "xs:" + cleanTypeName;
                    }

                    // Check if it's a simpleType
                    String simpleTypeQuery = "//xs:simpleType[@name='" + typeName + "']";
                    Node simpleTypeNode = (Node) xpath.evaluate(simpleTypeQuery, xsdDoc, XPathConstants.NODE);
                    if (simpleTypeNode != null) {
                        return "simpleType: " + typeName;
                    }

                    // Check if it's a complexType
                    String complexTypeQuery = "//xs:complexType[@name='" + typeName + "']";
                    Node complexTypeNode = (Node) xpath.evaluate(complexTypeQuery, xsdDoc, XPathConstants.NODE);
                    if (complexTypeNode != null) {
                        return "complexType: " + typeName;
                    }

                    return "type: " + typeName;
                }

                // Check if element has inline type definition
                NodeList children = elementNode.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        String nodeName = child.getLocalName();
                        if ("simpleType".equals(nodeName) || "xs:simpleType".equals(child.getNodeName())) {
                            return "inline simpleType";
                        } else if ("complexType".equals(nodeName) || "xs:complexType".equals(child.getNodeName())) {
                            return "inline complexType";
                        }
                    }
                }

                // If no type is specified, check if it's a complex element (has child elements)
                String hasChildrenQuery = "//xs:element[@name='" + cleanElementName + "']//xs:element";
                NodeList childElements = (NodeList) xpath.evaluate(hasChildrenQuery, xsdDoc, XPathConstants.NODESET);
                if (childElements.getLength() > 0) {
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

        try {
            // This could also be created programmatically if we want to remove all FXML dependencies
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/pages/controls/SearchReplaceControl.fxml"));
            Pane searchPane = loader.load();
            searchController = loader.getController();
            searchController.setXmlCodeEditor(this.xmlCodeEditor);

            searchPopOver = new PopOver(searchPane);
            searchPopOver.setDetachable(false);
            searchPopOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);
            searchPopOver.setTitle("Find/Replace");
        } catch (IOException e) {
            logger.error("Failed to initialize search popup.", e);
        }
    }

    private void showSearchPopup(boolean isSearch) {
        if (searchPopOver == null) return;
        searchController.selectTab(isSearch ? searchController.getSearchTab() : searchController.getReplaceTab());
        searchPopOver.show(codeArea, -5);
        searchController.focusFindField();
    }

    /**
     * Updates the cursor information including XPath, element name, element type, documentation, and example values.
     * Uses XSD-based implementation to get comprehensive information about the current element.
     */
    private void updateCursorInformation() {
        if (sidebarController == null) return;

        String text = codeArea.getText();
        int caretPosition = codeArea.getCaretPosition();
        String xpath = getCurrentXPath(text, caretPosition);
        sidebarController.setXPath(xpath);

        // Get element information from XSD documentation data
        updateElementInfoFromXsd(caretPosition);

        // Update child elements based on current position
        updateChildElements(xpath);

        // Update documentation and example values from XSD
        updateElementDocumentation(caretPosition);

        // Update example values based on current XPath
        updateExampleValuesFromXsd(xpath);
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

            // Pattern for opening tags
            Pattern openTagPattern = Pattern.compile("<([a-zA-Z][a-zA-Z0-9_:]*)[^/>]*(?<!/)>");
            // Pattern for closing tags  
            Pattern closeTagPattern = Pattern.compile("</([a-zA-Z][a-zA-Z0-9_:]*)\\s*>");
            // Pattern for self-closing tags
            Pattern selfClosingPattern = Pattern.compile("<([a-zA-Z][a-zA-Z0-9_:]*)[^>]*/>");

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
     * Formats child elements with option to include type information for display
     */
    private List<String> formatChildElementsForDisplay(List<String> childElements, boolean includeTypes) {
        List<String> formattedElements = new ArrayList<>();

        for (String childXPath : childElements) {
            try {
                // Extract element name from XPath (get the last part after the last '/')
                String elementName = getElementNameFromXPath(childXPath);
                if (elementName != null) {
                    String displayText;
                    if (includeTypes) {
                        // Try to get type information from the XSD documentation data
                        String elementType = getElementTypeFromXsdData(childXPath);
                        if (elementType != null && !elementType.isEmpty() && !elementType.equals("xs:string")) {
                            // Show element name with type if available and not default string type
                            displayText = elementName + " (" + elementType + ")";
                        } else {
                            // Just show element name if no specific type info
                            displayText = elementName;
                        }
                    } else {
                        // IntelliSense mode: only show element name
                        displayText = elementName;
                    }

                    formattedElements.add(displayText);
                } else {
                    // Fallback: show the original XPath if we can't parse it
                    formattedElements.add(childXPath);
                }
            } catch (Exception e) {
                logger.debug("Could not format child element: " + childXPath, e);
                // Fallback: show the original XPath
                formattedElements.add(childXPath);
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
        }

        // Clear cached XSD documentation data when XSD changes
        this.xsdDocumentationData = null;

        // Extract element names from XSD and update IntelliSense
        if (xsdFile != null) {
            List<String> elementNames = extractElementNamesFromXsd(xsdFile);
            Map<String, List<String>> contextElementNames = extractContextElementNamesFromXsd(xsdFile);
            xmlCodeEditor.setAvailableElementNames(elementNames);
            xmlCodeEditor.setContextElementNames(contextElementNames);

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
                        .map(this::convertToValidationError)
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
     * Converts a SAXParseException to a ValidationError
     */
    private ValidationError convertToValidationError(org.xml.sax.SAXParseException saxException) {
        int lineNumber = saxException.getLineNumber();
        int columnNumber = saxException.getColumnNumber();
        String message = saxException.getMessage();

        // Clean up common error message patterns for better readability
        if (message != null) {
            // Remove common prefixes that are not useful for end users
            message = message.replaceAll("^cvc-[^:]*:\\s*", "");
            message = message.replaceAll("^The content of element '[^']*' is not complete\\.", "Content is incomplete.");
        }

        return new ValidationError(lineNumber, columnNumber, message, "ERROR");
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
        List<String> elementNames = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(xsdFile);

            // Find all element definitions
            NodeList elementNodes = document.getElementsByTagName("xs:element");
            for (int i = 0; i < elementNodes.getLength(); i++) {
                Element element = (Element) elementNodes.item(i);
                String name = element.getAttribute("name");
                if (!name.isEmpty()) {
                    elementNames.add(name);
                }
            }

            // Also find elements in other namespaces
            NodeList allElements = document.getElementsByTagName("*");
            for (int i = 0; i < allElements.getLength(); i++) {
                Element element = (Element) allElements.item(i);
                if (element.getTagName().endsWith(":element")) {
                    String name = element.getAttribute("name");
                    if (!name.isEmpty() && !elementNames.contains(name)) {
                        elementNames.add(name);
                    }
                }
            }

            logger.debug("Extracted {} element names from XSD: {}", elementNames.size(), elementNames);

        } catch (Exception e) {
            logger.error("Error extracting element names from XSD: {}", xsdFile.getAbsolutePath(), e);
            // Add some default element names as fallback
            elementNames.addAll(Arrays.asList("root", "element", "item", "data", "content"));
        }

        return elementNames;
    }

    /**
     * Extracts context-sensitive element names (parent-child relationships) from an XSD file.
     *
     * @param xsdFile The XSD file to extract context information from
     * @return Map of parent element names to their child element names
     */
    private Map<String, List<String>> extractContextElementNamesFromXsd(File xsdFile) {
        Map<String, List<String>> contextElementNames = new HashMap<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(xsdFile);

            // Find all complex types that define element structures
            NodeList complexTypes = document.getElementsByTagName("xs:complexType");
            for (int i = 0; i < complexTypes.getLength(); i++) {
                Element complexType = (Element) complexTypes.item(i);
                String typeName = complexType.getAttribute("name");

                if (!typeName.isEmpty()) {
                    // Find child elements within this complex type
                    List<String> childElements = new ArrayList<>();

                    // Look for sequence, choice, or all elements
                    NodeList sequences = complexType.getElementsByTagName("xs:sequence");
                    NodeList choices = complexType.getElementsByTagName("xs:choice");
                    NodeList alls = complexType.getElementsByTagName("xs:all");

                    // Process sequences
                    for (int j = 0; j < sequences.getLength(); j++) {
                        Element sequence = (Element) sequences.item(j);
                        NodeList elements = sequence.getElementsByTagName("xs:element");
                        for (int k = 0; k < elements.getLength(); k++) {
                            Element element = (Element) elements.item(k);
                            String elementName = element.getAttribute("name");
                            if (!elementName.isEmpty()) {
                                childElements.add(elementName);
                            }
                        }
                    }

                    // Process choices
                    for (int j = 0; j < choices.getLength(); j++) {
                        Element choice = (Element) choices.item(j);
                        NodeList elements = choice.getElementsByTagName("xs:element");
                        for (int k = 0; k < elements.getLength(); k++) {
                            Element element = (Element) elements.item(k);
                            String elementName = element.getAttribute("name");
                            if (!elementName.isEmpty()) {
                                childElements.add(elementName);
                            }
                        }
                    }

                    // Process alls
                    for (int j = 0; j < alls.getLength(); j++) {
                        Element all = (Element) alls.item(j);
                        NodeList elements = all.getElementsByTagName("xs:element");
                        for (int k = 0; k < elements.getLength(); k++) {
                            Element element = (Element) elements.item(k);
                            String elementName = element.getAttribute("name");
                            if (!elementName.isEmpty()) {
                                childElements.add(elementName);
                            }
                        }
                    }

                    if (!childElements.isEmpty()) {
                        contextElementNames.put(typeName, childElements);
                    }
                }
            }

            // Also find direct element definitions and their relationships
            NodeList allElements = document.getElementsByTagName("xs:element");
            for (int i = 0; i < allElements.getLength(); i++) {
                Element element = (Element) allElements.item(i);
                String elementName = element.getAttribute("name");
                String elementType = element.getAttribute("type");

                if (!elementName.isEmpty() && !elementType.isEmpty()) {
                    // If this element has a type, check if we have child elements for that type
                    if (elementType.startsWith("xs:")) {
                        // Skip built-in types
                        continue;
                    }

                    List<String> childElements = contextElementNames.get(elementType);
                    if (childElements != null && !childElements.isEmpty()) {
                        contextElementNames.put(elementName, new ArrayList<>(childElements));
                    }
                }
            }

            // Add root-level elements
            List<String> rootElements = new ArrayList<>();
            for (int i = 0; i < allElements.getLength(); i++) {
                Element element = (Element) allElements.item(i);
                String elementName = element.getAttribute("name");
                if (!elementName.isEmpty()) {
                    rootElements.add(elementName);
                }
            }
            if (!rootElements.isEmpty()) {
                contextElementNames.put("root", rootElements);
            }

            logger.debug("Extracted context element names: {}", contextElementNames);

        } catch (Exception e) {
            logger.error("Error extracting context element names from XSD: {}", xsdFile.getAbsolutePath(), e);
            // Add some default context as fallback
            contextElementNames.put("root", Arrays.asList("root", "element", "item", "data"));
            contextElementNames.put("root", Arrays.asList("child", "subelement", "content"));
        }

        return contextElementNames;
    }


    private void applyStyles() {
        if (codeArea.getText().length() >= MAX_SIZE_FOR_FORMATTING) return;
        StyleSpans<Collection<String>> syntaxHighlighting = XmlCodeEditor.computeHighlighting(codeArea.getText());
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
     * This method handles cases where the exact XPath isn't found by looking for parent elements.
     *
     * @param targetXPath The XPath to find a match for
     * @return The best matching XsdExtendedElement or null
     */
    public XsdExtendedElement findBestMatchingElement(String targetXPath) {
        if (xsdDocumentationData == null || targetXPath == null) {
            return null;
        }

        Map<String, XsdExtendedElement> elementMap = xsdDocumentationData.getExtendedXsdElementMap();

        // Try exact match first
        XsdExtendedElement exactMatch = elementMap.get(targetXPath);
        if (exactMatch != null) {
            return exactMatch;
        }

        // Try to find the longest matching parent path
        String bestMatch = null;
        int bestMatchLength = 0;

        for (String xpath : elementMap.keySet()) {
            if (targetXPath.startsWith(xpath + "/") || targetXPath.equals(xpath)) {
                if (xpath.length() > bestMatchLength) {
                    bestMatch = xpath;
                    bestMatchLength = xpath.length();
                }
            }
        }

        if (bestMatch != null) {
            logger.debug("Found partial match for '{}': '{}'", targetXPath, bestMatch);
            return elementMap.get(bestMatch);
        }

        // Fallback: try to find by element name
        String elementName = getElementNameFromXPath(targetXPath);
        if (elementName != null) {
            for (XsdExtendedElement element : elementMap.values()) {
                if (elementName.equals(element.getElementName())) {
                    logger.debug("Found match by element name for '{}': '{}'", targetXPath, element.getCurrentXpath());
                    return element;
                }
            }
        }

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

            // Create XPath to find the element and its documentation
            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(new SimpleNamespaceContext(xsdDoc));

            // Look for element documentation in various locations
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
                NodeList nodes = (NodeList) xpath.evaluate(query, xsdDoc, XPathConstants.NODESET);
                if (nodes.getLength() > 0) {
                    StringBuilder documentation = new StringBuilder();
                    for (int i = 0; i < nodes.getLength(); i++) {
                        Node node = nodes.item(i);
                        String content = node.getTextContent();
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

                // Use the XsdDocumentationService to parse the XSD
                XsdDocumentationService service = new XsdDocumentationService();
                service.setXsdFilePath(xsdFile.getAbsolutePath());
                service.processXsd(true);

                // Get the populated documentation data
                this.xsdDocumentationData = service.xsdDocumentationData;

                logger.debug("Successfully loaded XSD documentation data with {} elements",
                        xsdDocumentationData.getExtendedXsdElementMap().size());

            } catch (Exception e) {
                logger.debug("Error loading XSD documentation data: {}", e.getMessage());
                this.xsdDocumentationData = null;
            }
        });
    }

    /**
     * Simple namespace context for XSD parsing.
     */
    private static class SimpleNamespaceContext implements NamespaceContext {
        private final Map<String, String> prefixMap = new HashMap<>();

        public SimpleNamespaceContext(Document doc) {
            // Add common XSD namespace prefixes
            prefixMap.put("xs", "http://www.w3.org/2001/XMLSchema");
            prefixMap.put("xsd", "http://www.w3.org/2001/XMLSchema");

            // Try to extract namespace information from the document
            Element root = doc.getDocumentElement();
            if (root != null) {
                String defaultNamespace = root.getNamespaceURI();
                if (defaultNamespace != null) {
                    prefixMap.put("", defaultNamespace);
                }
            }
        }

        @Override
        public String getNamespaceURI(String prefix) {
            return prefixMap.getOrDefault(prefix, "");
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return prefixMap.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(namespaceURI))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            return prefixMap.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(namespaceURI))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList())
                    .iterator();
        }
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

            // Find the element definition
            XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(new javax.xml.namespace.NamespaceContext() {
                @Override
                public String getNamespaceURI(String prefix) {
                    if ("xs".equals(prefix)) {
                        return "http://www.w3.org/2001/XMLSchema";
                    }
                    return null;
                }

                @Override
                public String getPrefix(String uri) {
                    if ("http://www.w3.org/2001/XMLSchema".equals(uri)) {
                        return "xs";
                    }
                    return null;
                }

                @Override
                public java.util.Iterator<String> getPrefixes(String uri) {
                    return java.util.Collections.singletonList("xs").iterator();
                }
            });

            // Remove namespace prefix if present
            String cleanElementName = elementName;
            if (elementName.contains(":")) {
                cleanElementName = elementName.split(":")[1];
            }

            // Try to find the element definition
            String elementQuery = "//xs:element[@name='" + cleanElementName + "']";
            Node elementNode = (Node) xpath.evaluate(elementQuery, xsdDoc, XPathConstants.NODE);

            if (elementNode == null) {
                // Try without namespace prefix
                elementQuery = "//element[@name='" + cleanElementName + "']";
                elementNode = (Node) xpath.evaluate(elementQuery, xsdDoc, XPathConstants.NODE);
            }

            if (elementNode != null) {
                // Look for example values in annotations
                NodeList annotations = elementNode.getChildNodes();
                for (int i = 0; i < annotations.getLength(); i++) {
                    Node annotation = annotations.item(i);
                    if (annotation.getNodeType() == Node.ELEMENT_NODE &&
                            ("annotation".equals(annotation.getLocalName()) || "xs:annotation".equals(annotation.getNodeName()))) {

                        // Look for appinfo elements with Altova example values
                        NodeList appInfos = annotation.getChildNodes();
                        for (int j = 0; j < appInfos.getLength(); j++) {
                            Node appInfo = appInfos.item(j);
                            if (appInfo.getNodeType() == Node.ELEMENT_NODE &&
                                    ("appinfo".equals(appInfo.getLocalName()) || "xs:appinfo".equals(appInfo.getNodeName()))) {

                                // Look for Altova exampleValues
                                NodeList appInfoChildren = appInfo.getChildNodes();
                                for (int k = 0; k < appInfoChildren.getLength(); k++) {
                                    Node appInfoChild = appInfoChildren.item(k);
                                    if (appInfoChild.getNodeType() == Node.ELEMENT_NODE &&
                                            "http://www.altova.com/xml-schema-extensions".equals(appInfoChild.getNamespaceURI()) &&
                                            "exampleValues".equals(appInfoChild.getLocalName())) {

                                        // Extract individual example values
                                        NodeList examples = appInfoChild.getChildNodes();
                                        for (int l = 0; l < examples.getLength(); l++) {
                                            Node exampleNode = examples.item(l);
                                            if (exampleNode.getNodeType() == Node.ELEMENT_NODE &&
                                                    "http://www.altova.com/xml-schema-extensions".equals(exampleNode.getNamespaceURI()) &&
                                                    "example".equals(exampleNode.getLocalName())) {

                                                // Get the value attribute
                                                if (exampleNode.hasAttributes()) {
                                                    Node valueAttr = exampleNode.getAttributes().getNamedItem("value");
                                                    if (valueAttr != null) {
                                                        String value = valueAttr.getNodeValue();
                                                        if (value != null && !value.trim().isEmpty()) {
                                                            exampleValues.add(value);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Also look for documentation with example values (legacy support)
                        NodeList docs = annotation.getChildNodes();
                        for (int j = 0; j < docs.getLength(); j++) {
                            Node doc = docs.item(j);
                            if (doc.getNodeType() == Node.ELEMENT_NODE &&
                                    ("documentation".equals(doc.getLocalName()) || "xs:documentation".equals(doc.getNodeName()))) {

                                String content = doc.getTextContent();
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
        PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
        if (propertiesService.isXmlAutoFormatAfterLoading()) {
            logger.debug("Auto-formatting XML content after loading file");
            Platform.runLater(() -> {
                String currentText = codeArea.getText();
                if (currentText != null && !currentText.trim().isEmpty()) {
                    try {
                        String formattedText = XmlService.prettyFormat(currentText, propertiesService.getXmlIndentSpaces());
                        if (formattedText != null && !formattedText.isEmpty()) {
                            xmlCodeEditor.setText(formattedText);
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

    public void setXmlFile(File xmlFile) {
        this.xmlFile = xmlFile;
        this.setText(xmlFile.getName());
        refreshTextView();

        xmlService.setCurrentXmlFile(xmlFile);

        // Set the document URI for completion requests and parent reference
        if (xmlCodeEditor != null) {
            xmlCodeEditor.setDocumentUri(xmlFile.toURI().toString());
            xmlCodeEditor.setParentXmlEditor(this);
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
            return;
        }
        try {
            final String content = Files.readString(xmlFile.toPath(), StandardCharsets.UTF_8);
            xmlCodeEditor.setText(content);

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
            xmlCodeEditor.setText("Error: Could not read file.\n" + e.getMessage());
            document = null;
        }
    }

    private void refreshGraphicView() {
        try {
            VBox vBox = new VBox();
            vBox.setPadding(new Insets(3));
            if (document != null) {
                currentGraphicEditor = new XmlGraphicEditor(document, this);
                // Set sidebar controller for integration with XmlEditorSidebar functionality
                if (sidebarController != null) {
                    currentGraphicEditor.setSidebarController(sidebarController);
                }
                VBox.setVgrow(currentGraphicEditor, Priority.ALWAYS);
                vBox.getChildren().add(currentGraphicEditor);
            }
            ScrollPane pane = new ScrollPane(vBox);
            pane.setBackground(new Background(new BackgroundFill(Color.rgb(200, 200, 50, 0.5), new CornerRadii(5), new Insets(5))));
            this.graphic.setContent(pane);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    public XmlCodeEditor getXmlCodeEditor() {
        return xmlCodeEditor;
    }

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
                xmlCodeEditor.setText(newXmlContent);

                // Update the file if it exists
                if (xmlFile != null && xmlFile.exists()) {
                    try {
                        Files.writeString(xmlFile.toPath(), newXmlContent, StandardCharsets.UTF_8);
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
        if (xmlCodeEditor != null) {
            xmlCodeEditor.testSyntaxHighlighting();
        }
    }

    /**
     * Debug method to check CSS loading in the XML editor.
     */
    public void debugCssLoading() {
        if (xmlCodeEditor != null) {
            logger.debug("=== Debugging CSS Loading in XmlEditor ===");
            xmlCodeEditor.debugCssStatus();
        } else {
            logger.error("ERROR: XmlCodeEditor is null");
        }
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
            XPath xpathEvaluator = XPathFactory.newInstance().newXPath();
            return (Node) xpathEvaluator.evaluate(xpath, document, XPathConstants.NODE);
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

            // Try to navigate in the graphic view first
            if (currentGraphicEditor != null) {
                boolean graphicNavigationSuccess = currentGraphicEditor.navigateToNode(xpath, 0);
                if (graphicNavigationSuccess) {
                    logger.info("Successfully navigated to XPath in graphic view: {}", xpath);
                    return;
                }
            }

            // Fallback: try to find the element in the DOM and estimate line number
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
}