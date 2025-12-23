package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import net.sf.saxon.s9api.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.XPathIntelliSenseEngine;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers.XsdXPathCompletionProvider;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.xpath.XmlDocumentElementExtractor;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.xpath.XsdSchemaElementExtractor;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.kordamp.ikonli.javafx.FontIcon;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * XPath/XQuery query panel for the XSD Editor's text view.
 * <p>
 * Provides a collapsible panel with:
 * <ul>
 *   <li>TabPane with XPath and XQuery query input tabs</li>
 *   <li>IntelliSense autocomplete for XPath functions and XSD elements</li>
 *   <li>Read-only result display area</li>
 *   <li>Execute/Clear buttons with status feedback</li>
 *   <li>Error display for debugging</li>
 * </ul>
 */
public class XsdXPathQueryPanel extends VBox {

    private static final Logger logger = LogManager.getLogger(XsdXPathQueryPanel.class);

    // UI Components
    private final TabPane queryTabPane;
    private final Tab xPathTab;
    private final Tab xQueryTab;
    private final CodeArea xpathCodeArea;
    private final CodeArea xqueryCodeArea;
    private final CodeArea resultCodeArea;
    private final Label statusLabel;
    private final Button executeButton;
    private final Button clearQueryButton;
    private final Button clearResultButton;
    private final Button closeButton;
    private final SplitPane contentSplitPane;

    // IntelliSense engines
    private XPathIntelliSenseEngine xpathIntelliSenseEngine;
    private XPathIntelliSenseEngine xqueryIntelliSenseEngine;

    // Extractors for IntelliSense
    private final XmlDocumentElementExtractor elementExtractor;
    private final XsdSchemaElementExtractor xsdExtractor;

    // Saxon processor for execution
    private final Processor saxonProcessor;
    private final XPathCompiler xpathCompiler;
    private final XQueryCompiler xqueryCompiler;

    // Background execution
    private final ExecutorService executorService;

    // Content supplier
    private Supplier<String> xsdContentSupplier;

    // Callbacks
    private Runnable onCloseRequested;

    // State
    private boolean intelliSenseInitialized = false;

    /**
     * Creates a new XSD XPath/XQuery Query Panel.
     */
    public XsdXPathQueryPanel() {
        // Initialize Saxon
        saxonProcessor = new Processor(false);
        xpathCompiler = saxonProcessor.newXPathCompiler();
        xqueryCompiler = saxonProcessor.newXQueryCompiler();
        setupNamespaces();

        // Initialize extractors
        elementExtractor = new XmlDocumentElementExtractor();
        xsdExtractor = new XsdSchemaElementExtractor();

        // Background executor
        executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("XSD-XPath-Query-Thread");
            t.setDaemon(true);
            return t;
        });

        // Create UI components
        // Toolbar
        closeButton = createIconButton("bi-x-circle", "Close panel", "#dc3545");
        closeButton.setOnAction(e -> {
            if (onCloseRequested != null) {
                onCloseRequested.run();
            }
        });

        executeButton = createIconButton("bi-play-fill", "Execute query (Ctrl+Enter)", "#28a745");
        executeButton.setOnAction(e -> executeQuery());

        clearQueryButton = createIconButton("bi-eraser", "Clear query", "#6c757d");
        clearQueryButton.setOnAction(e -> clearQuery());

        clearResultButton = createIconButton("bi-trash", "Clear result", "#6c757d");
        clearResultButton.setOnAction(e -> clearResult());

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 12px;");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(5, 10, 5, 10));
        toolbar.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;");
        toolbar.getChildren().addAll(
                closeButton,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                executeButton,
                clearQueryButton,
                clearResultButton,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                statusLabel
        );

        // Query input CodeAreas
        xpathCodeArea = createQueryCodeArea();
        xqueryCodeArea = createQueryCodeArea();

        // Wrap in scroll panes
        VirtualizedScrollPane<CodeArea> xpathScrollPane = new VirtualizedScrollPane<>(xpathCodeArea);
        VirtualizedScrollPane<CodeArea> xqueryScrollPane = new VirtualizedScrollPane<>(xqueryCodeArea);

        // Create tabs
        xPathTab = new Tab("XPath");
        xPathTab.setClosable(false);
        xPathTab.setGraphic(new FontIcon("bi-code-slash"));
        xPathTab.setContent(xpathScrollPane);

        xQueryTab = new Tab("XQuery");
        xQueryTab.setClosable(false);
        xQueryTab.setGraphic(new FontIcon("bi-braces"));
        xQueryTab.setContent(xqueryScrollPane);

        queryTabPane = new TabPane(xPathTab, xQueryTab);
        queryTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(queryTabPane, Priority.ALWAYS);

        // Result CodeArea (read-only)
        resultCodeArea = createResultCodeArea();
        VirtualizedScrollPane<CodeArea> resultScrollPane = new VirtualizedScrollPane<>(resultCodeArea);

        // Result section with header
        Label resultLabel = new Label("Result");
        resultLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #495057;");
        VBox resultSection = new VBox(5);
        resultSection.setPadding(new Insets(5, 0, 0, 0));
        resultSection.getChildren().addAll(resultLabel, resultScrollPane);
        VBox.setVgrow(resultScrollPane, Priority.ALWAYS);
        VBox.setVgrow(resultSection, Priority.ALWAYS);

        // Main content split pane (query input above, result below)
        contentSplitPane = new SplitPane();
        contentSplitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        contentSplitPane.setDividerPositions(0.5);
        contentSplitPane.getItems().addAll(queryTabPane, resultSection);
        VBox.setVgrow(contentSplitPane, Priority.ALWAYS);

        // Add all to this VBox
        this.getChildren().addAll(toolbar, contentSplitPane);
        this.setStyle("-fx-background-color: #ffffff;");

        // Setup keyboard shortcuts
        setupKeyboardShortcuts();

        logger.info("XSD XPath Query Panel initialized");
    }

    /**
     * Sets up default XML namespaces for Saxon compilers.
     */
    private void setupNamespaces() {
        try {
            // XSD namespace
            xpathCompiler.declareNamespace("xs", "http://www.w3.org/2001/XMLSchema");
            xpathCompiler.declareNamespace("xsd", "http://www.w3.org/2001/XMLSchema");
            xpathCompiler.declareNamespace("fn", "http://www.w3.org/2005/xpath-functions");

            xqueryCompiler.declareNamespace("xs", "http://www.w3.org/2001/XMLSchema");
            xqueryCompiler.declareNamespace("xsd", "http://www.w3.org/2001/XMLSchema");
            xqueryCompiler.declareNamespace("fn", "http://www.w3.org/2005/xpath-functions");
        } catch (Exception e) {
            logger.warn("Failed to setup namespaces: {}", e.getMessage());
        }
    }

    /**
     * Creates a query input CodeArea with line numbers.
     */
    private CodeArea createQueryCodeArea() {
        CodeArea codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', 'Courier New', monospace; -fx-font-size: 13px;");
        codeArea.setWrapText(false);
        return codeArea;
    }

    /**
     * Creates a read-only result CodeArea.
     */
    private CodeArea createResultCodeArea() {
        CodeArea codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', 'Courier New', monospace; -fx-font-size: 13px;");
        codeArea.setEditable(false);
        codeArea.setWrapText(false);
        // Slight gray background to indicate read-only
        codeArea.setStyle(codeArea.getStyle() + "; -fx-background-color: #f8f9fa;");
        return codeArea;
    }

    /**
     * Creates a styled icon button.
     */
    private Button createIconButton(String iconLiteral, String tooltip, String iconColor) {
        Button button = new Button();
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(16);
        icon.setIconColor(Color.web(iconColor));
        button.setGraphic(icon);
        button.setTooltip(new Tooltip(tooltip));
        button.setStyle("-fx-background-color: transparent; -fx-padding: 5;");
        button.setOnMouseEntered(e -> button.setStyle("-fx-background-color: #e9ecef; -fx-padding: 5;"));
        button.setOnMouseExited(e -> button.setStyle("-fx-background-color: transparent; -fx-padding: 5;"));
        return button;
    }

    /**
     * Sets up keyboard shortcuts.
     */
    private void setupKeyboardShortcuts() {
        // Ctrl+Enter to execute
        xpathCodeArea.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                executeQuery();
                e.consume();
            }
        });

        xqueryCodeArea.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                executeQuery();
                e.consume();
            }
        });
    }

    /**
     * Initializes IntelliSense engines (called lazily on first focus).
     */
    private void initializeIntelliSense() {
        if (intelliSenseInitialized) {
            return;
        }

        try {
            // Create custom providers with XSD support
            XsdXPathCompletionProvider xpathProvider = new XsdXPathCompletionProvider(
                    elementExtractor, xsdExtractor, false);
            XsdXPathCompletionProvider xqueryProvider = new XsdXPathCompletionProvider(
                    elementExtractor, xsdExtractor, true);

            // Create IntelliSense engines with standard XPathIntelliSenseEngine
            // The engines will use the standard provider internally, but we refresh extractors manually
            xpathIntelliSenseEngine = new XPathIntelliSenseEngine(xpathCodeArea, false);
            xqueryIntelliSenseEngine = new XPathIntelliSenseEngine(xqueryCodeArea, true);

            // Set content supplier for element extraction
            xpathIntelliSenseEngine.setXmlContentSupplier(this::getXsdContent);
            xqueryIntelliSenseEngine.setXmlContentSupplier(this::getXsdContent);

            intelliSenseInitialized = true;
            logger.debug("IntelliSense engines initialized for XSD XPath panel");
        } catch (Exception e) {
            logger.error("Failed to initialize IntelliSense: {}", e.getMessage(), e);
        }
    }

    /**
     * Gets the current XSD content from the supplier.
     */
    private String getXsdContent() {
        if (xsdContentSupplier != null) {
            return xsdContentSupplier.get();
        }
        return null;
    }

    /**
     * Refreshes the extractors with current XSD content.
     */
    private void refreshExtractors() {
        String xsdContent = getXsdContent();
        if (xsdContent != null && !xsdContent.isBlank()) {
            elementExtractor.extractFromXml(xsdContent);
            xsdExtractor.extractFromXsd(xsdContent);
            logger.debug("Extractors refreshed with XSD content");
        }
    }

    /**
     * Executes the current query (XPath or XQuery based on active tab).
     */
    public void executeQuery() {
        // Initialize IntelliSense on first use
        if (!intelliSenseInitialized) {
            initializeIntelliSense();
        }

        // Refresh extractors
        refreshExtractors();

        // Determine which tab is active
        boolean isXPath = queryTabPane.getSelectionModel().getSelectedItem() == xPathTab;
        CodeArea activeCodeArea = isXPath ? xpathCodeArea : xqueryCodeArea;
        String query = activeCodeArea.getText().trim();

        if (query.isEmpty()) {
            setStatus("Error: Query is empty", true);
            return;
        }

        String xsdContent = getXsdContent();
        if (xsdContent == null || xsdContent.isBlank()) {
            setStatus("Error: No XSD content available", true);
            return;
        }

        setStatus("Executing...", false);
        executeButton.setDisable(true);

        Task<String> queryTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                if (isXPath) {
                    return executeXPath(xsdContent, query);
                } else {
                    return executeXQuery(xsdContent, query);
                }
            }
        };

        queryTask.setOnSucceeded(e -> Platform.runLater(() -> {
            String result = queryTask.getValue();
            if (result != null && !result.isEmpty()) {
                resultCodeArea.replaceText(result);
                setStatus("Query executed successfully (" + result.length() + " chars)", false);
            } else {
                resultCodeArea.replaceText("(No results)");
                setStatus("Query returned no results", false);
            }
            executeButton.setDisable(false);
        }));

        queryTask.setOnFailed(e -> Platform.runLater(() -> {
            Throwable error = queryTask.getException();
            String errorMessage = error != null ? error.getMessage() : "Unknown error";

            // Display error in result area for debugging
            resultCodeArea.replaceText("Error: " + errorMessage);
            setStatus("Error: " + truncateMessage(errorMessage, 80), true);
            executeButton.setDisable(false);

            logger.error("Query execution failed: {}", errorMessage, error);
        }));

        executorService.submit(queryTask);
    }

    /**
     * Executes an XPath expression against the XSD content.
     */
    private String executeXPath(String xsdContent, String xpathExpression) throws SaxonApiException {
        // Parse XSD as XML document
        DocumentBuilder docBuilder = saxonProcessor.newDocumentBuilder();
        XdmNode doc = docBuilder.build(new StreamSource(new StringReader(xsdContent)));

        // Compile and execute XPath
        XPathExecutable executable = xpathCompiler.compile(xpathExpression);
        XPathSelector selector = executable.load();
        selector.setContextItem(doc);

        XdmValue result = selector.evaluate();

        return formatResult(result);
    }

    /**
     * Executes an XQuery expression against the XSD content.
     */
    private String executeXQuery(String xsdContent, String xqueryExpression) throws SaxonApiException {
        // Compile XQuery
        XQueryExecutable executable = xqueryCompiler.compile(xqueryExpression);
        XQueryEvaluator evaluator = executable.load();

        // Parse XSD as XML document
        DocumentBuilder docBuilder = saxonProcessor.newDocumentBuilder();
        XdmNode doc = docBuilder.build(new StreamSource(new StringReader(xsdContent)));
        evaluator.setContextItem(doc);

        XdmValue result = evaluator.evaluate();

        return formatResult(result);
    }

    /**
     * Formats the Saxon result to a displayable string.
     */
    private String formatResult(XdmValue result) throws SaxonApiException {
        if (result.size() == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        Serializer serializer = saxonProcessor.newSerializer(new StringWriter());
        serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
        serializer.setOutputProperty(Serializer.Property.INDENT, "yes");

        for (XdmItem item : result) {
            if (item.isAtomicValue()) {
                sb.append(item.getStringValue());
            } else if (item instanceof XdmNode node) {
                StringWriter writer = new StringWriter();
                Serializer nodeSerializer = saxonProcessor.newSerializer(writer);
                nodeSerializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes");
                nodeSerializer.setOutputProperty(Serializer.Property.INDENT, "yes");
                nodeSerializer.serializeNode(node);
                sb.append(writer.toString().trim());
            }
            sb.append(System.lineSeparator());
        }

        return sb.toString().trim();
    }

    /**
     * Clears the query input.
     */
    public void clearQuery() {
        boolean isXPath = queryTabPane.getSelectionModel().getSelectedItem() == xPathTab;
        CodeArea activeCodeArea = isXPath ? xpathCodeArea : xqueryCodeArea;
        activeCodeArea.clear();
        setStatus("Query cleared", false);
    }

    /**
     * Clears the result area.
     */
    public void clearResult() {
        resultCodeArea.clear();
        setStatus("Result cleared", false);
    }

    /**
     * Sets the status label text.
     */
    private void setStatus(String message, boolean isError) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        statusLabel.setText("[" + timestamp + "] " + message);
        statusLabel.setStyle(isError
                ? "-fx-text-fill: #dc3545; -fx-font-size: 12px;"
                : "-fx-text-fill: #6c757d; -fx-font-size: 12px;");
    }

    /**
     * Truncates a message to a maximum length.
     */
    private String truncateMessage(String message, int maxLength) {
        if (message == null) return "";
        if (message.length() <= maxLength) return message;
        return message.substring(0, maxLength - 3) + "...";
    }

    /**
     * Sets the XSD content supplier.
     *
     * @param supplier a supplier that returns the current XSD content
     */
    public void setXsdContentSupplier(Supplier<String> supplier) {
        this.xsdContentSupplier = supplier;
    }

    /**
     * Sets the callback for when the close button is pressed.
     *
     * @param callback the callback to invoke
     */
    public void setOnCloseRequested(Runnable callback) {
        this.onCloseRequested = callback;
    }

    /**
     * Gets the XPath CodeArea for external access.
     */
    public CodeArea getXpathCodeArea() {
        return xpathCodeArea;
    }

    /**
     * Gets the XQuery CodeArea for external access.
     */
    public CodeArea getXqueryCodeArea() {
        return xqueryCodeArea;
    }

    /**
     * Gets the result CodeArea for external access.
     */
    public CodeArea getResultCodeArea() {
        return resultCodeArea;
    }

    /**
     * Disposes resources when the panel is no longer needed.
     */
    public void dispose() {
        try {
            if (xpathIntelliSenseEngine != null) {
                xpathIntelliSenseEngine.dispose();
            }
            if (xqueryIntelliSenseEngine != null) {
                xqueryIntelliSenseEngine.dispose();
            }
            executorService.shutdownNow();
            logger.debug("XSD XPath Query Panel disposed");
        } catch (Exception e) {
            logger.warn("Error disposing XPath panel: {}", e.getMessage());
        }
    }
}
