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
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageServer;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxmisc.richtext.model.TwoDimensional;
import org.fxt.freexmltoolkit.controller.MainController;
import org.fxt.freexmltoolkit.controller.controls.SearchReplaceController;
import org.fxt.freexmltoolkit.controller.controls.XmlEditorSidebarController;
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
import java.util.stream.Collectors;

public class XmlEditor extends Tab {

    public static final int MAX_SIZE_FOR_FORMATTING = 1024 * 1024 * 20;
    public static final String DEFAULT_FILE_NAME = "Untitled.xml *";

    private final Tab xml = new Tab("XML");
    private final Tab graphic = new Tab("Graphic");

    private final XmlCodeEditor xmlCodeEditor = new XmlCodeEditor();
    public final CodeArea codeArea = xmlCodeEditor.getCodeArea();

    private List<Diagnostic> currentDiagnostics = new ArrayList<>();

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

    private MainController mainController;
    private LanguageServer serverProxy;

    private PopOver hoverPopOver;
    private final Label popOverLabel = new Label();
    private final PauseTransition hoverDelay = new PauseTransition(Duration.millis(500));

    private SearchReplaceController searchController;
    private PopOver searchPopOver;

    // --- Sidebar Components ---
    private XmlEditorSidebarController sidebarController;
    private SplitPane splitPane;

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
    }

    public XmlEditor(File file) {
        init();
        this.setXmlFile(file);
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    public void setLanguageServer(LanguageServer serverProxy) {
        this.serverProxy = serverProxy;
        // Also pass the LanguageServer to the XmlCodeEditor for IntelliSense
        if (xmlCodeEditor != null) {
            xmlCodeEditor.setLanguageServer(serverProxy);
        }
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
            // Try to find the current element using XMLStreamReader
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(text));

            String currentElementName = null;

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    currentElementName = reader.getLocalName();

                    // Check if we're at or near the current position
                    if (reader.getLocation() != null) {
                        int elementStart = reader.getLocation().getCharacterOffset();
                        if (elementStart <= position) {
                            // We found the element at the cursor position
                            break;
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    currentElementName = null;
                }
            }

            reader.close();

            if (currentElementName != null) {
                // Get XSD type information for this element
                String elementType = determineXsdElementType(currentElementName);
                return new ElementInfo(currentElementName, elementType);
            }

        } catch (Exception e) {
            logger.debug("Error parsing XML for element info: {}", e.getMessage());
        }

        // Fallback: try to extract element name from manual parsing
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
                        return "xs:" + typeName;
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
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<([a-zA-Z][a-zA-Z0-9_:]*)\\b");
            java.util.regex.Matcher matcher = pattern.matcher(beforeCursor);

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
        hoverDelay.setOnFinished(e -> triggerLspHover());
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
     * Uses LSP server to get comprehensive information about the current element.
     */
    private void updateCursorInformation() {
        if (sidebarController == null) return;
        
        String text = codeArea.getText();
        int caretPosition = codeArea.getCaretPosition();
        String xpath = getCurrentXPath(text, caretPosition);
        sidebarController.setXPath(xpath);

        // Extract element name and type from current position
        ElementInfo elementInfo = getElementInfoAtPosition(text, caretPosition);
        sidebarController.setElementName(elementInfo.name);
        sidebarController.setElementType(elementInfo.type);

        // Update child elements based on current position
        updateChildElements(xpath);

        // Get documentation from LSP server and example values from XSD
        if (serverProxy != null && xmlFile != null) {
            updateElementDocumentation(caretPosition);
        }

        // Update example values based on current XPath
        updateExampleValuesFromXsd(xpath);
    }

    /**
     * Determines the XPath of the element at the current cursor position.
     * Uses a more robust approach that handles malformed XML gracefully.
     *
     * @param text The XML text content
     * @param position The current cursor position
     * @return The XPath string or a descriptive message if parsing fails
     */
    public String getCurrentXPath(String text, int position) {
        if (text == null || text.isEmpty() || position <= 0) {
            return "No XML content";
        }

        Deque<String> elementStack = new ArrayDeque<>();
        try {
            // First try with XMLStreamReader for well-formed XML
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(text.substring(0, position)));

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamReader.START_ELEMENT) {
                    String localName = reader.getLocalName();
                    String prefix = reader.getPrefix();
                    String elementName = (prefix != null && !prefix.isEmpty()) ? prefix + ":" + localName : localName;
                    elementStack.push(elementName);
                } else if (event == XMLStreamReader.END_ELEMENT) {
                    if (!elementStack.isEmpty()) {
                        elementStack.pop();
                    }
                }
            }
            
            // Reverse the stack for correct order
            Deque<String> reversedStack = new ArrayDeque<>();
            elementStack.forEach(reversedStack::push);
            String xpath = "/" + String.join("/", reversedStack);

            // Return root element if we're at the beginning
            if (xpath.equals("/")) {
                return "Root element";
            }

            return xpath;

        } catch (Exception e) {
            logger.debug("XMLStreamReader failed, trying manual parsing: {}", e.getMessage());

            // Fallback: manual parsing for malformed XML
            return getCurrentXPathManual(text, position);
        }
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
            java.util.regex.Pattern openPattern = java.util.regex.Pattern.compile(openTagPattern);
            java.util.regex.Matcher openMatcher = openPattern.matcher(textBeforeCursor);

            while (openMatcher.find()) {
                String elementName = openMatcher.group(1);
                // Skip self-closing tags and processing instructions
                if (!elementName.startsWith("?") && !elementName.startsWith("!")) {
                    elementStack.push(elementName);
                }
            }

            // Find all closing tags before cursor and remove corresponding opening tags
            java.util.regex.Pattern closePattern = java.util.regex.Pattern.compile(closeTagPattern);
            java.util.regex.Matcher closeMatcher = closePattern.matcher(textBeforeCursor);

            while (closeMatcher.find()) {
                String elementName = closeMatcher.group(1);
                if (!elementStack.isEmpty() && elementStack.peek().equals(elementName)) {
                    elementStack.pop();
                }
            }

            // Build XPath
            Deque<String> reversedStack = new ArrayDeque<>();
            elementStack.forEach(reversedStack::push);
            String xpath = "/" + String.join("/", reversedStack);

            if (xpath.equals("/")) {
                return "Root element";
            }

            return xpath;
            
        } catch (Exception e) {
            logger.debug("Manual parsing also failed: {}", e.getMessage());
            return "Unable to determine XPath";
        }
    }

    /**
     * Updates the child elements list based on the current XPath.
     *
     * @param xpath The current XPath to find child elements for
     */
    private void updateChildElements(String xpath) {
        if (sidebarController == null) return;
        
        if (xsdFile == null || xpath == null || xpath.equals("Invalid XML structure") ||
                xpath.equals("No XML content") || xpath.equals("Unable to determine XPath")) {
            sidebarController.setPossibleChildElements(Collections.singletonList("No XSD schema loaded or invalid XPath"));
            return;
        }

        try {
            // Get the current element name from XPath
            String[] pathParts = xpath.split("/");
            if (pathParts.length == 0) {
                sidebarController.setPossibleChildElements(Collections.singletonList("No element selected"));
                return;
            }

            String currentElementName = pathParts[pathParts.length - 1];
            if (currentElementName.isEmpty()) {
                sidebarController.setPossibleChildElements(Collections.singletonList("No element selected"));
                return;
            }

            // Find the element in XSD and get its children
            List<String> childElements = getChildElementsFromXsd(currentElementName);
            if (childElements.isEmpty()) {
                sidebarController.setPossibleChildElements(Collections.singletonList("No child elements found for: " + currentElementName));
            } else {
                sidebarController.setPossibleChildElements(childElements);
            }
        } catch (Exception e) {
            logger.error("Error getting child elements", e);
            sidebarController.setPossibleChildElements(Collections.singletonList("Error: " + e.getMessage()));
        }
    }

    public List<String> getChildElementsFromXsd(String elementName) {
        List<String> childElements = new ArrayList<>();

        try {
            // Parse the XSD file
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document xsdDoc = builder.parse(xsdFile);

            // Find the element definition - try with and without namespace
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

            if (elementNode == null) {
                return childElements;
            }

            // Get the type of the element
            String typeName = elementNode.getAttributes().getNamedItem("type") != null ?
                    elementNode.getAttributes().getNamedItem("type").getNodeValue() : null;

            Node typeDefinition = null;
            if (typeName != null) {
                // Remove namespace prefix if present
                if (typeName.contains(":")) {
                    typeName = typeName.split(":")[1];
                }

                // Find the complex type definition
                String typeQuery = "//xs:complexType[@name='" + typeName + "']";
                typeDefinition = (Node) xpath.evaluate(typeQuery, xsdDoc, XPathConstants.NODE);

                if (typeDefinition == null) {
                    // Try without namespace prefix
                    typeQuery = "//complexType[@name='" + typeName + "']";
                    typeDefinition = (Node) xpath.evaluate(typeQuery, xsdDoc, XPathConstants.NODE);
                }
            } else {
                // Check for inline complex type
                NodeList complexTypes = elementNode.getChildNodes();
                for (int i = 0; i < complexTypes.getLength(); i++) {
                    Node child = complexTypes.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE &&
                            ("complexType".equals(child.getLocalName()) || "xs:complexType".equals(child.getNodeName()))) {
                        typeDefinition = child;
                        break;
                    }
                }
            }

            if (typeDefinition != null) {
                // Find sequence, choice, or all elements
                NodeList children = typeDefinition.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        String localName = child.getLocalName();
                        if (localName == null) {
                            // Try to get from nodeName
                            String nodeName = child.getNodeName();
                            if (nodeName.contains(":")) {
                                localName = nodeName.split(":")[1];
                            } else {
                                localName = nodeName;
                            }
                        }

                        if ("sequence".equals(localName) || "choice".equals(localName) || "all".equals(localName)) {
                            // Get all element children
                            NodeList elementChildren = child.getChildNodes();
                            for (int j = 0; j < elementChildren.getLength(); j++) {
                                Node elementChild = elementChildren.item(j);
                                if (elementChild.getNodeType() == Node.ELEMENT_NODE) {
                                    String elementLocalName = elementChild.getLocalName();
                                    if (elementLocalName == null) {
                                        String elementNodeName = elementChild.getNodeName();
                                        if (elementNodeName.contains(":")) {
                                            elementLocalName = elementNodeName.split(":")[1];
                                        } else {
                                            elementLocalName = elementNodeName;
                                        }
                                    }

                                    if ("element".equals(elementLocalName)) {
                                        String childName = elementChild.getAttributes().getNamedItem("name") != null ?
                                                elementChild.getAttributes().getNamedItem("name").getNodeValue() : null;
                                        if (childName != null) {
                                            String minOccurs = elementChild.getAttributes().getNamedItem("minOccurs") != null ?
                                                    elementChild.getAttributes().getNamedItem("minOccurs").getNodeValue() : "1";
                                            String maxOccurs = elementChild.getAttributes().getNamedItem("maxOccurs") != null ?
                                                    elementChild.getAttributes().getNamedItem("maxOccurs").getNodeValue() : "1";

                                            String occurrence = "";
                                            if ("0".equals(minOccurs) && "1".equals(maxOccurs)) {
                                                occurrence = " (optional)";
                                            } else if ("unbounded".equals(maxOccurs)) {
                                                occurrence = " (0..*)";
                                            } else if (!"1".equals(minOccurs) || !"1".equals(maxOccurs)) {
                                                occurrence = " (" + minOccurs + ".." + maxOccurs + ")";
                                            }

                                            childElements.add(childName + occurrence);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing XSD for child elements", e);
        }

        return childElements;
    }

    public File getXsdFile() {
        return xsdFile;
    }

    public void setXsdFile(File xsdFile) {
        this.xsdFile = xsdFile;
        if (sidebarController != null) {
            sidebarController.setXsdPathField(xsdFile != null ? xsdFile.getAbsolutePath() : "No XSD schema selected");
        }

        // Extract element names from XSD and update IntelliSense
        if (xsdFile != null) {
            List<String> elementNames = extractElementNamesFromXsd(xsdFile);
            Map<String, List<String>> contextElementNames = extractContextElementNamesFromXsd(xsdFile);
            xmlCodeEditor.setAvailableElementNames(elementNames);
            xmlCodeEditor.setContextElementNames(contextElementNames);
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
            sidebarController.updateValidationStatus("No XSD selected", "orange");
            return;
        }

        try {
            String xmlContent = codeArea.getText();
            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                sidebarController.updateValidationStatus("No XML content", "orange");
                return;
            }

            // Use the XmlService for validation
            List<org.xml.sax.SAXParseException> errors = xmlService.validateText(xmlContent, xsdFile);

            if (errors == null || errors.isEmpty()) {
                sidebarController.updateValidationStatus("✓ Valid", "green");
            } else {
                String errorMessage = "✗ Invalid (" + errors.size() + " error(s))";
                if (errors.size() == 1) {
                    errorMessage += "\n" + errors.get(0).getMessage();
                } else {
                    errorMessage += "\nFirst error: " + errors.get(0).getMessage();
                }
                sidebarController.setValidationStatus(errorMessage);
                sidebarController.updateValidationStatus(errorMessage, "red");
            }
        } catch (Exception e) {
            sidebarController.updateValidationStatus("Error during validation", "red");
            logger.error("Error during XML validation", e);
        }
    }

    public void validateSchematron() {
        if (sidebarController == null) return;
        
        if (schematronFile == null) {
            sidebarController.updateSchematronValidationStatus("No Schematron rules selected", "orange");
            return;
        }

        try {
            String xmlContent = codeArea.getText();
            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                sidebarController.updateSchematronValidationStatus("No XML content", "orange");
                return;
            }

            // Use the SchematronService for validation
            List<SchematronService.SchematronValidationError> errors = schematronService.validateXml(xmlContent, schematronFile);

            if (errors == null || errors.isEmpty()) {
                sidebarController.updateSchematronValidationStatus("✓ Valid", "green");
            } else {
                String errorMessage = "✗ Invalid (" + errors.size() + " error(s))";
                if (errors.size() == 1) {
                    errorMessage += "\n" + errors.get(0).message();
                } else {
                    errorMessage += "\nFirst error: " + errors.get(0).message();
                }
                sidebarController.updateSchematronValidationStatus(errorMessage, "red");
                logger.debug("Schematron validation: ✗ Invalid (" + errors.size() + " error(s))");
                for (SchematronService.SchematronValidationError error : errors) {
                    logger.debug("Schematron error: {} at line {}, column {}",
                            error.message(), error.lineNumber(), error.columnNumber());
                }
            }
        } catch (Exception e) {
            sidebarController.updateSchematronValidationStatus("Error during validation", "red");
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
                if (name != null && !name.isEmpty()) {
                    elementNames.add(name);
                }
            }

            // Also find elements in other namespaces
            NodeList allElements = document.getElementsByTagName("*");
            for (int i = 0; i < allElements.getLength(); i++) {
                Element element = (Element) allElements.item(i);
                if (element.getTagName().endsWith(":element")) {
                    String name = element.getAttribute("name");
                    if (name != null && !name.isEmpty() && !elementNames.contains(name)) {
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

                if (typeName != null && !typeName.isEmpty()) {
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
                            if (elementName != null && !elementName.isEmpty()) {
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
                            if (elementName != null && !elementName.isEmpty()) {
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
                            if (elementName != null && !elementName.isEmpty()) {
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

                if (elementName != null && !elementName.isEmpty() && elementType != null && !elementType.isEmpty()) {
                    // If this element has a type, check if we have child elements for that type
                    String typeName = elementType;
                    if (typeName.startsWith("xs:")) {
                        // Skip built-in types
                        continue;
                    }

                    List<String> childElements = contextElementNames.get(typeName);
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
                if (elementName != null && !elementName.isEmpty()) {
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

    public void updateDiagnostics(List<Diagnostic> diagnostics) {
        this.currentDiagnostics = new ArrayList<>(diagnostics);
        applyStyles();
    }

    private void applyStyles() {
        if (codeArea.getText().length() >= MAX_SIZE_FOR_FORMATTING) return;
        StyleSpans<Collection<String>> syntaxHighlighting = XmlCodeEditor.computeHighlighting(codeArea.getText());
        codeArea.setStyleSpans(0, syntaxHighlighting.overlay(computeDiagnosticStyles(), (syntax, diagnostic) -> diagnostic.isEmpty() ? syntax : diagnostic));
    }

    private StyleSpans<Collection<String>> computeDiagnosticStyles() {
        if (currentDiagnostics.isEmpty()) {
            return StyleSpans.singleton(Collections.emptyList(), codeArea.getLength());
        }
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        int lastKwEnd = 0;
        for (Diagnostic diagnostic : currentDiagnostics) {
            Range range = diagnostic.getRange();
            int start = codeArea.position(range.getStart().getLine(), range.getStart().getCharacter()).toOffset();
            int end = codeArea.position(range.getEnd().getLine(), range.getEnd().getCharacter()).toOffset();
            if (start < end) {
                spansBuilder.add(Collections.emptyList(), start - lastKwEnd);
                String styleClass = getStyleClassFor(diagnostic.getSeverity());
                spansBuilder.add(Collections.singleton(styleClass), end - start);
                lastKwEnd = end;
            }
        }
        spansBuilder.add(Collections.emptyList(), codeArea.getLength() - lastKwEnd);
        return spansBuilder.create();
    }

    private String getStyleClassFor(DiagnosticSeverity severity) {
        if (severity == null) return "diagnostic-warning";
        return switch (severity) {
            case Error -> "diagnostic-error";
            case Warning -> "diagnostic-warning";
            default -> "diagnostic-warning";
        };
    }

    private void triggerLspHover() {
        if (this.serverProxy == null || xmlFile == null) return;

        int caretPosition = codeArea.getCaretPosition();
        var lineColumn = codeArea.offsetToPosition(caretPosition, TwoDimensional.Bias.Forward);
        TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(xmlFile.toURI().toString());
        Position position = new Position(lineColumn.getMajor(), lineColumn.getMinor());
        HoverParams hoverParams = new HoverParams(textDocumentIdentifier, position);

        CompletableFuture<Hover> hoverFuture = this.serverProxy.getTextDocumentService().hover(hoverParams);
        hoverFuture.thenAcceptAsync(hover -> {
            if (hover != null && hover.getContents() != null) {
                if (hover.getContents().isRight()) {
                    String hoverText = hover.getContents().getRight().getValue();
                    if (!hoverText.isBlank()) {
                        Platform.runLater(() -> {
                            popOverLabel.setText(hoverText);
                            showPopOver();
                        });
                    }
                }
            }
        }).exceptionally(ex -> {
            logger.error("LSP hover request failed.", ex);
            return null;
        });
    }

    /**
     * Updates element documentation using LSP server hover functionality.
     *
     * @param caretPosition The current cursor position
     */
    private void updateElementDocumentation(int caretPosition) {
        if (serverProxy == null || xmlFile == null) return;

        try {
            var lineColumn = codeArea.offsetToPosition(caretPosition, TwoDimensional.Bias.Forward);
            TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(xmlFile.toURI().toString());
            Position position = new Position(lineColumn.getMajor(), lineColumn.getMinor());

            // Get hover information (documentation)
            HoverParams hoverParams = new HoverParams(textDocumentIdentifier, position);
            CompletableFuture<Hover> hoverFuture = serverProxy.getTextDocumentService().hover(hoverParams);

            hoverFuture.thenAcceptAsync(hover -> {
                if (hover != null && hover.getContents() != null) {
                    final String[] documentation = {""};

                    // Handle different types of hover content
                    if (hover.getContents().isRight()) {
                        // Simple string content
                        documentation[0] = hover.getContents().getRight().getValue();
                    } else if (hover.getContents().isLeft()) {
                        // List of marked strings
                        try {
                            documentation[0] = hover.getContents().getLeft().stream()
                                    .map(Object::toString)
                                    .collect(Collectors.joining("\n"));
                        } catch (Exception e) {
                            logger.debug("Error processing hover content: {}", e.getMessage());
                        }
                    }

                    if (!documentation[0].isBlank()) {
                        Platform.runLater(() -> updateDocumentationDisplay(documentation[0]));
                    }
                }
            }).exceptionally(ex -> {
                logger.debug("LSP hover request failed: {}", ex.getMessage());
                return null;
            });

        } catch (Exception e) {
            logger.debug("Error updating element documentation: {}", e.getMessage());
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

                        // Look for documentation with example values
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

    public File getXmlFile() {
        return xmlFile;
    }

    public void setXmlFile(File xmlFile) {
        this.xmlFile = xmlFile;
        this.setText(xmlFile.getName());
        refreshTextView();

        xmlService.setCurrentXmlFile(xmlFile);

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
            codeArea.replaceText(content);

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
            codeArea.replaceText("Error: Could not read file.\n" + e.getMessage());
            document = null;
        }
    }

    private void refreshGraphicView() {
        try {
            VBox vBox = new VBox();
            vBox.setPadding(new Insets(3));
            if (document != null) {
                var simpleNodeElement = new XmlGraphicEditor(document, this);
                VBox.setVgrow(simpleNodeElement, Priority.ALWAYS);
                vBox.getChildren().add(simpleNodeElement);
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
                codeArea.replaceText(newXmlContent);

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
}