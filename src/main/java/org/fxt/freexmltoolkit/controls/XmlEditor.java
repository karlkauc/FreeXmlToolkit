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
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
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
        HBox.setHgrow(xsdPathField, Priority.ALWAYS);
        Button changeXsdButton = new Button("...");
        changeXsdButton.setOnAction(event -> selectXsdFile());
        HBox xsdFileBox = new HBox(5, xsdPathField, changeXsdButton);
        validationStatusLabel = new Label("Validation status: Unknown");
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
        childElementsListView.getItems().setAll(Collections.singletonList("Child element detection not yet implemented."));
    }

    private String getCurrentXPath(String text, int position) {
        Deque<String> elementStack = new ArrayDeque<>();
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(text.substring(0, position)));

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamReader.START_ELEMENT) {
                    elementStack.push(reader.getLocalName());
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
            return "Invalid XML structure";
        }
    }

    public void setXsdFile(File xsdFile) {
        this.xsdFile = xsdFile;
        if (xsdPathField != null) {
            xsdPathField.setText(xsdFile.getAbsolutePath());
        }
        validateXml();
    }

    public void validateXml() {
        if (xsdFile == null) {
            validationStatusLabel.setText("Validation status: No XSD selected");
            return;
        }
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(new StreamSource(xsdFile));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new ByteArrayInputStream(codeArea.getText().getBytes(StandardCharsets.UTF_8))));
            validationStatusLabel.setText("Validation status: Valid");
        } catch (SAXException | IOException e) {
            validationStatusLabel.setText("Validation status: Invalid");
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
        if (xmlService.loadSchemaFromXMLFile()) {
            File loadedXsdFile = xmlService.getCurrentXsdFile();
            if (loadedXsdFile != null) {
                setXsdFile(loadedXsdFile);
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