package org.fxt.freexmltoolkit.controls;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
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
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
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

    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer transformer;
    DocumentBuilder db;
    Document document;
    XmlService xmlService = new XmlServiceImpl();
    XsdDocumentationService xsdDocumentationService = new XsdDocumentationService();

    private MainController mainController;
    private LanguageServer serverProxy;

    private PopOver hoverPopOver;
    private final Label popOverLabel = new Label();
    private final PauseTransition hoverDelay = new PauseTransition(Duration.millis(500));

    private SearchReplaceController searchController;
    private PopOver searchPopOver;

    // --- Sidebar UI Components ---
    private TextField xsdPathField;
    private Label validationStatusLabel;
    private CheckBox continuousValidationCheckBox;
    private TextField xpathField;
    private ListView<String> childElementsListView;

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

        setupHover();
        setupSearchAndReplace();

        xml.setContent(xmlCodeEditor);
        this.setText(DEFAULT_FILE_NAME);
        this.setClosable(true);

        // --- Create Sidebar Programmatically ---
        TitledPane sidebar = createSidebar();

        // --- Create Main Layout ---
        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(tabPane, sidebar);
        splitPane.setDividerPositions(0.8); // Initial position

        this.setContent(splitPane);

        // Store the last divider position before collapsing
        final double[] lastDividerPosition = {splitPane.getDividerPositions()[0]};

        // Keep track of the divider position when the user moves it, but only when the sidebar is expanded.
        splitPane.getDividers().get(0).positionProperty().addListener((obs, oldPos, newPos) -> {
            if (sidebar.isExpanded()) {
                lastDividerPosition[0] = newPos.doubleValue();
            }
        });

        // Add a listener to the TitledPane's expanded property to move the divider
        sidebar.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
            if (isExpanded) {
                splitPane.setDividerPositions(lastDividerPosition[0]);
            } else {
                splitPane.setDividerPositions(1.0);
            }
        });

        // --- Add Listeners ---
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (continuousValidationCheckBox.isSelected()) {
                validateXml();
            }
        });

        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> updateCursorInformation());
    }

    private TitledPane createSidebar() {
        // --- Sidebar Content Pane ---
        VBox sidebarContent = new VBox(10);
        sidebarContent.setPadding(new Insets(10));

        // --- XSD Section ---
        xsdPathField = new TextField();
        xsdPathField.setPromptText("XSD Schema");
        xsdPathField.setEditable(false); // Make it read-only to show current schema
        HBox.setHgrow(xsdPathField, Priority.ALWAYS);
        Button changeXsdButton = new Button("...");
        changeXsdButton.setOnAction(event -> selectXsdFile());
        HBox xsdFileBox = new HBox(5, xsdPathField, changeXsdButton);
        validationStatusLabel = new Label("Validation status: Unknown");
        validationStatusLabel.setWrapText(true);
        continuousValidationCheckBox = new CheckBox("Continuous validation");
        VBox xsdBox = new VBox(5, xsdFileBox, validationStatusLabel, continuousValidationCheckBox);
        TitledPane xsdPane = new TitledPane("XSD Schema", xsdBox);
        xsdPane.setCollapsible(false);

        // --- Cursor Section ---
        xpathField = new TextField();
        xpathField.setEditable(false);
        VBox cursorBox = new VBox(5, new Label("XPath:"), xpathField);
        TitledPane cursorPane = new TitledPane("Cursor Information", cursorBox);
        cursorPane.setCollapsible(false);

        // --- Child Elements Section ---
        childElementsListView = new ListView<>();
        childElementsListView.setPrefHeight(150);
        TitledPane childElementsPane = new TitledPane("Possible Child Elements", childElementsListView);
        childElementsPane.setCollapsible(false);
        VBox.setVgrow(childElementsPane, Priority.ALWAYS);

        sidebarContent.getChildren().addAll(xsdPane, cursorPane, childElementsPane);

        // Wrap the entire sidebar content in a TitledPane
        TitledPane sidebarPane = new TitledPane("Tools", sidebarContent);
        sidebarPane.setAnimated(true); // Use smooth animation for collapse/expand
        sidebarPane.setExpanded(true); // Start expanded

        return sidebarPane;
    }

    private void selectXsdFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select XSD Schema");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSD Files", "*.xsd"));

        // Set initial directory to the same as the XML file if available
        if (xmlFile != null && xmlFile.getParentFile() != null) {
            fileChooser.setInitialDirectory(xmlFile.getParentFile());
        }
        
        File selectedFile = fileChooser.showOpenDialog(this.getContent().getScene().getWindow());
        if (selectedFile != null) {
            setXsdFile(selectedFile);
        }
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

    private void updateCursorInformation() {
        String text = codeArea.getText();
        int caretPosition = codeArea.getCaretPosition();
        String xpath = getCurrentXPath(text, caretPosition);
        xpathField.setText(xpath);

        // Update child elements based on current position
        updateChildElements(xpath);
    }

    public String getCurrentXPath(String text, int position) {
        Deque<String> elementStack = new ArrayDeque<>();
        try {
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
            return "/" + String.join("/", reversedStack);
        } catch (Exception e) {
            logger.debug("Error parsing XML for XPath: {}", e.getMessage());
            return "Invalid XML structure";
        }
    }

    private void updateChildElements(String xpath) {
        if (xsdFile == null || xpath == null || xpath.equals("Invalid XML structure")) {
            childElementsListView.getItems().setAll(Collections.singletonList("No XSD schema loaded or invalid XPath"));
            return;
        }

        try {
            // Get the current element name from XPath
            String[] pathParts = xpath.split("/");
            if (pathParts.length == 0) {
                childElementsListView.getItems().setAll(Collections.singletonList("No element selected"));
                return;
            }

            String currentElementName = pathParts[pathParts.length - 1];
            if (currentElementName.isEmpty()) {
                childElementsListView.getItems().setAll(Collections.singletonList("No element selected"));
                return;
            }

            // Find the element in XSD and get its children
            List<String> childElements = getChildElementsFromXsd(currentElementName);
            if (childElements.isEmpty()) {
                childElementsListView.getItems().setAll(Collections.singletonList("No child elements found for: " + currentElementName));
            } else {
                childElementsListView.getItems().setAll(childElements);
            }
        } catch (Exception e) {
            logger.error("Error getting child elements", e);
            childElementsListView.getItems().setAll(Collections.singletonList("Error: " + e.getMessage()));
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
        if (xsdPathField != null) {
            xsdPathField.setText(xsdFile != null ? xsdFile.getAbsolutePath() : "No XSD schema selected");
        }
        validateXml();
    }

    public void validateXml() {
        if (xsdFile == null) {
            validationStatusLabel.setText("Validation status: No XSD selected");
            validationStatusLabel.setStyle("-fx-text-fill: orange;");
            return;
        }

        try {
            String xmlContent = codeArea.getText();
            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                validationStatusLabel.setText("Validation status: No XML content");
                validationStatusLabel.setStyle("-fx-text-fill: orange;");
                return;
            }

            // Use the XmlService for validation
            List<org.xml.sax.SAXParseException> errors = xmlService.validateText(xmlContent, xsdFile);

            if (errors == null || errors.isEmpty()) {
                validationStatusLabel.setText("Validation status: ✓ Valid");
                validationStatusLabel.setStyle("-fx-text-fill: green;");
            } else {
                String errorMessage = "Validation status: ✗ Invalid (" + errors.size() + " error(s))";
                if (errors.size() == 1) {
                    errorMessage += "\n" + errors.get(0).getMessage();
                } else {
                    errorMessage += "\nFirst error: " + errors.get(0).getMessage();
                }
                validationStatusLabel.setText(errorMessage);
                validationStatusLabel.setStyle("-fx-text-fill: red;");
            }
        } catch (Exception e) {
            validationStatusLabel.setText("Validation status: Error during validation");
            validationStatusLabel.setStyle("-fx-text-fill: red;");
            logger.error("Error during XML validation", e);
        }
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
                if (xsdPathField != null) {
                    xsdPathField.setText("No XSD schema found in XML");
                }
                validationStatusLabel.setText("Validation status: No XSD schema found");
                validationStatusLabel.setStyle("-fx-text-fill: orange;");
            }
        } else {
            // If no XSD was found, clear the XSD field
            if (xsdPathField != null) {
                xsdPathField.setText("No XSD schema found in XML");
            }
            validationStatusLabel.setText("Validation status: No XSD schema found");
            validationStatusLabel.setStyle("-fx-text-fill: orange;");
        }
    }

    public void refresh() {
        refreshTextView();
        refreshGraphicView();
    }

    public void refreshTextView() {
        if (xmlFile == null || !xmlFile.exists()) {
            codeArea.clear();
            return;
        }
        try {
            final String content = Files.readString(xmlFile.toPath(), StandardCharsets.UTF_8);
            codeArea.replaceText(content);
        } catch (IOException e) {
            codeArea.replaceText("Error: Could not read file.\n" + e.getMessage());
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
        refresh();
    }
}