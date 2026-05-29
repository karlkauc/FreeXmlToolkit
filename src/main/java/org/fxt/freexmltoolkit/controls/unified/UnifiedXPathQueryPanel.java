package org.fxt.freexmltoolkit.controls.unified;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import javax.xml.transform.stream.StreamSource;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.XPathIntelliSenseEngine;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.FavoritesService;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;

/**
 * XPath/XQuery query panel for the Unified Editor.
 * <p>
 * Provides a collapsible panel at the bottom of the Unified Editor with:
 * <ul>
 *   <li>TabPane with XPath and XQuery query input tabs</li>
 *   <li>Read-only result display area</li>
 *   <li>Execute/Clear buttons with status feedback</li>
 *   <li>Works with the active tab's XML content</li>
 * </ul>
 *
 * @since 2.0
 */
public class UnifiedXPathQueryPanel extends VBox {

    private static final Logger logger = LogManager.getLogger(UnifiedXPathQueryPanel.class);

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
    private final Button saveButton;
    private final Button loadButton;
    private final MenuButton savedQueriesMenu;
    private final MenuButton examplesMenu;
    private final SplitPane contentSplitPane;

    // Saved-query persistence (shared with the XML Editor)
    private final FavoritesService favoritesService = ServiceRegistry.get(FavoritesService.class);

    // Saxon processor for execution
    private final Processor saxonProcessor;
    private final XPathCompiler xpathCompiler;
    private final XQueryCompiler xqueryCompiler;

    // Background execution
    private final ExecutorService executorService;

    // Content supplier - gets XML content from active tab
    private Supplier<String> contentSupplier;

    // IntelliSense / autocomplete engines (lazy-initialized on first focus)
    private XPathIntelliSenseEngine xpathIntelliSenseEngine;
    private XPathIntelliSenseEngine xqueryIntelliSenseEngine;
    private boolean intelliSenseInitialized = false;

    // Callbacks
    private Runnable onCloseRequested;

    /**
     * Creates a new Unified XPath/XQuery Query Panel.
     */
    public UnifiedXPathQueryPanel() {
        // Initialize Saxon
        saxonProcessor = new Processor(false);
        xpathCompiler = saxonProcessor.newXPathCompiler();
        xqueryCompiler = saxonProcessor.newXQueryCompiler();
        setupNamespaces();

        // Background executor
        executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("Unified-XPath-Query-Thread");
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

        // Query management: Saved Queries / Save / Load / Examples (context-sensitive to active tab)
        savedQueriesMenu = createTextMenuButton("Saved Queries", "bi-folder2", "#6f42c1",
                "Load a saved query");
        savedQueriesMenu.setOnShowing(e -> refreshSavedQueriesMenu());

        saveButton = createIconButton("bi-floppy", "Save query to file", "#17a2b8");
        saveButton.setOnAction(e -> saveCurrentQuery());

        loadButton = createIconButton("bi-folder2-open", "Load query from file", "#007bff");
        loadButton.setOnAction(e -> loadQueryFromFile());

        examplesMenu = createTextMenuButton("Examples", "bi-collection", "#6c757d",
                "Insert an example query");
        examplesMenu.setOnShowing(e -> refreshExamplesMenu());

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("theme-text-secondary");
        statusLabel.setStyle("-fx-font-size: 12px;");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        Label titleLabel = new Label("XPath/XQuery Console");
        titleLabel.getStyleClass().add("theme-text-primary");
        titleLabel.setStyle("-fx-font-weight: bold;");
        IconifyIcon terminalIcon = new IconifyIcon("bi-terminal");
        terminalIcon.setIconSize(16);
        terminalIcon.setIconColor(Color.web("#6c757d"));

        HBox titleBox = new HBox(5, terminalIcon, titleLabel);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(5, 10, 5, 10));
        toolbar.getStyleClass().add("query-panel-toolbar");
        toolbar.getChildren().addAll(
                closeButton,
                new Separator(Orientation.VERTICAL),
                titleBox,
                new Separator(Orientation.VERTICAL),
                executeButton,
                clearQueryButton,
                clearResultButton,
                new Separator(Orientation.VERTICAL),
                savedQueriesMenu,
                saveButton,
                loadButton,
                examplesMenu,
                new Separator(Orientation.VERTICAL),
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
        IconifyIcon xpathIcon = new IconifyIcon("bi-code-slash");
        xpathIcon.setIconSize(14);
        xPathTab.setGraphic(xpathIcon);
        xPathTab.setContent(xpathScrollPane);

        xQueryTab = new Tab("XQuery");
        xQueryTab.setClosable(false);
        IconifyIcon xqueryIcon = new IconifyIcon("bi-braces");
        xqueryIcon.setIconSize(14);
        xQueryTab.setGraphic(xqueryIcon);
        xQueryTab.setContent(xqueryScrollPane);

        queryTabPane = new TabPane(xPathTab, xQueryTab);
        queryTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(queryTabPane, Priority.ALWAYS);

        // Result CodeArea (read-only)
        resultCodeArea = createResultCodeArea();
        VirtualizedScrollPane<CodeArea> resultScrollPane = new VirtualizedScrollPane<>(resultCodeArea);

        // Result section with header
        Label resultLabel = new Label("Result");
        resultLabel.getStyleClass().add("theme-text-primary");
        resultLabel.setStyle("-fx-font-weight: bold;");
        VBox resultSection = new VBox(5);
        resultSection.setPadding(new Insets(5, 0, 0, 0));
        resultSection.getChildren().addAll(resultLabel, resultScrollPane);
        VBox.setVgrow(resultScrollPane, Priority.ALWAYS);
        VBox.setVgrow(resultSection, Priority.ALWAYS);

        // Main content split pane (query input left, result right)
        contentSplitPane = new SplitPane();
        contentSplitPane.setOrientation(Orientation.HORIZONTAL);
        contentSplitPane.setDividerPositions(0.5);
        contentSplitPane.getItems().addAll(queryTabPane, resultSection);
        VBox.setVgrow(contentSplitPane, Priority.ALWAYS);

        // Add all to this VBox
        this.getChildren().addAll(toolbar, contentSplitPane);
        this.getStyleClass().add("query-panel-root");
        this.setPrefHeight(200);
        this.setMinHeight(100);

        // Setup keyboard shortcuts
        setupKeyboardShortcuts();

        // Setup IntelliSense / autocomplete (lazy on first focus)
        setupIntelliSense();

        logger.info("Unified XPath Query Panel initialized");
    }

    /**
     * Sets up default XML namespaces for Saxon compilers.
     */
    private void setupNamespaces() {
        try {
            // Common namespaces
            xpathCompiler.declareNamespace("xs", "http://www.w3.org/2001/XMLSchema");
            xpathCompiler.declareNamespace("xsd", "http://www.w3.org/2001/XMLSchema");
            xpathCompiler.declareNamespace("xsl", "http://www.w3.org/1999/XSL/Transform");
            xpathCompiler.declareNamespace("sch", "http://purl.oclc.org/dml/schematron");
            xpathCompiler.declareNamespace("fn", "http://www.w3.org/2005/xpath-functions");

            xqueryCompiler.declareNamespace("xs", "http://www.w3.org/2001/XMLSchema");
            xqueryCompiler.declareNamespace("xsd", "http://www.w3.org/2001/XMLSchema");
            xqueryCompiler.declareNamespace("xsl", "http://www.w3.org/1999/XSL/Transform");
            xqueryCompiler.declareNamespace("sch", "http://purl.oclc.org/dml/schematron");
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
        attachInputContextMenu(codeArea);
        return codeArea;
    }

    /**
     * Attaches a standard right-click context menu (Cut, Copy, Paste, Select All,
     * Clear) to an editable query input CodeArea. Items are enabled based on
     * selection, content, and clipboard state.
     */
    private void attachInputContextMenu(CodeArea codeArea) {
        MenuItem cutItem = new MenuItem("Cut");
        cutItem.setGraphic(menuIcon("bi-scissors"));
        cutItem.setOnAction(e -> codeArea.cut());

        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setGraphic(menuIcon("bi-clipboard"));
        copyItem.setOnAction(e -> codeArea.copy());

        MenuItem pasteItem = new MenuItem("Paste");
        pasteItem.setGraphic(menuIcon("bi-clipboard-plus"));
        pasteItem.setOnAction(e -> codeArea.paste());

        MenuItem selectAllItem = new MenuItem("Select All");
        selectAllItem.setGraphic(menuIcon("bi-check-all"));
        selectAllItem.setOnAction(e -> codeArea.selectAll());

        MenuItem clearItem = new MenuItem("Clear");
        clearItem.setGraphic(menuIcon("bi-trash"));
        clearItem.setOnAction(e -> codeArea.clear());

        ContextMenu menu = new ContextMenu(cutItem, copyItem, pasteItem,
                new SeparatorMenuItem(), selectAllItem, new SeparatorMenuItem(), clearItem);
        menu.setOnShowing(e -> {
            boolean hasSelection = !codeArea.getSelectedText().isEmpty();
            boolean hasText = codeArea.getLength() > 0;
            boolean clipboardHasText = Clipboard.getSystemClipboard().hasString();
            cutItem.setDisable(!hasSelection);
            copyItem.setDisable(!hasSelection);
            pasteItem.setDisable(!clipboardHasText);
            selectAllItem.setDisable(!hasText);
            clearItem.setDisable(!hasText);
        });
        codeArea.setContextMenu(menu);
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
        attachResultContextMenu(codeArea);
        return codeArea;
    }

    /**
     * Attaches a standard right-click context menu (Copy, Select All, Clear)
     * to the read-only result CodeArea. Cut/Paste are omitted since the area
     * is not editable.
     */
    private void attachResultContextMenu(CodeArea codeArea) {
        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setGraphic(menuIcon("bi-clipboard"));
        copyItem.setOnAction(e -> codeArea.copy());

        MenuItem selectAllItem = new MenuItem("Select All");
        selectAllItem.setGraphic(menuIcon("bi-check-all"));
        selectAllItem.setOnAction(e -> codeArea.selectAll());

        MenuItem clearItem = new MenuItem("Clear");
        clearItem.setGraphic(menuIcon("bi-trash"));
        clearItem.setOnAction(e -> clearResult());

        ContextMenu menu = new ContextMenu(copyItem, selectAllItem, new SeparatorMenuItem(), clearItem);
        // Enable Copy only when there is a selection; disable everything when empty.
        menu.setOnShowing(e -> {
            boolean hasSelection = !codeArea.getSelectedText().isEmpty();
            boolean hasText = codeArea.getLength() > 0;
            copyItem.setDisable(!hasSelection);
            selectAllItem.setDisable(!hasText);
            clearItem.setDisable(!hasText);
        });
        codeArea.setContextMenu(menu);
    }

    /**
     * Creates a 16px icon graphic for a context menu item.
     */
    private IconifyIcon menuIcon(String literal) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(16);
        icon.setIconColor(Color.web("#6c757d"));
        return icon;
    }

    /**
     * Creates a styled icon button.
     */
    private Button createIconButton(String iconLiteral, String tooltip, String iconColor) {
        Button button = new Button();
        IconifyIcon icon = new IconifyIcon(iconLiteral);
        icon.setIconSize(16);
        icon.setIconColor(Color.web(iconColor));
        button.setGraphic(icon);
        button.setTooltip(new Tooltip(tooltip));
        button.setStyle("-fx-background-color: transparent; -fx-padding: 5;");
        button.setOnMouseEntered(e -> button.setStyle("-fx-padding: 5;"));
        button.setOnMouseExited(e -> button.setStyle("-fx-background-color: transparent; -fx-padding: 5;"));
        return button;
    }

    /**
     * Creates a small text MenuButton with a leading icon, used for the
     * "Saved Queries" and "Examples" dropdowns in the toolbar.
     */
    private MenuButton createTextMenuButton(String text, String iconLiteral, String iconColor, String tooltip) {
        MenuButton menuButton = new MenuButton(text);
        IconifyIcon icon = new IconifyIcon(iconLiteral);
        icon.setIconSize(14);
        icon.setIconColor(Color.web(iconColor));
        menuButton.setGraphic(icon);
        menuButton.setTooltip(new Tooltip(tooltip));
        menuButton.setStyle("-fx-font-size: 12px;");
        return menuButton;
    }

    // ========== Query Save / Load / Examples (context-sensitive to active tab) ==========

    /**
     * Returns {@code true} when the XPath tab is the active query tab.
     */
    private boolean isXPathActive() {
        return queryTabPane.getSelectionModel().getSelectedItem() == xPathTab;
    }

    /**
     * Returns the CodeArea of the currently active query tab.
     */
    private CodeArea activeQueryCodeArea() {
        return isXPathActive() ? xpathCodeArea : xqueryCodeArea;
    }

    /**
     * Rebuilds the "Saved Queries" dropdown for the active tab's query type.
     * Lists previously saved queries plus management actions.
     */
    private void refreshSavedQueriesMenu() {
        boolean isXPath = isXPathActive();
        savedQueriesMenu.getItems().clear();

        List<File> queries = isXPath
                ? favoritesService.getSavedXPathQueries()
                : favoritesService.getSavedXQueryQueries();

        if (queries == null || queries.isEmpty()) {
            MenuItem noQueriesItem = new MenuItem("No saved queries");
            noQueriesItem.setDisable(true);
            savedQueriesMenu.getItems().add(noQueriesItem);
        } else {
            String itemIcon = isXPath ? "bi-code-slash" : "bi-braces";
            for (File queryFile : queries) {
                MenuItem item = new MenuItem(FavoritesService.getQueryName(queryFile));
                item.setGraphic(menuIcon(itemIcon));
                item.setOnAction(e -> loadSavedQuery(queryFile, isXPath));
                savedQueriesMenu.getItems().add(item);
            }
        }

        savedQueriesMenu.getItems().add(new SeparatorMenuItem());

        MenuItem addToFavoritesItem = new MenuItem("Add Current Query to Favorites...");
        addToFavoritesItem.setGraphic(menuIcon("bi-star"));
        addToFavoritesItem.setOnAction(e -> addCurrentQueryToFavorites(isXPath));
        savedQueriesMenu.getItems().add(addToFavoritesItem);

        MenuItem openFolderItem = new MenuItem("Open Queries Folder...");
        openFolderItem.setGraphic(menuIcon("bi-folder2-open"));
        openFolderItem.setOnAction(e -> openQueriesFolder(isXPath));
        savedQueriesMenu.getItems().add(openFolderItem);
    }

    /**
     * Rebuilds the "Examples" dropdown with sample expressions for the active
     * tab's query type.
     */
    private void refreshExamplesMenu() {
        boolean isXPath = isXPathActive();
        examplesMenu.getItems().clear();

        if (isXPath) {
            examplesMenu.getItems().addAll(
                    exampleItem("//node - Select all nodes", "//node", "bi-diagram-3", "#007bff"),
                    exampleItem("/root/child[@attr='value']", "/root/child[@attr='value']", "bi-funnel", "#ffc107"),
                    exampleItem("//text() - All text nodes", "//text()", "bi-text-left", "#28a745"),
                    exampleItem("count(//element)", "count(//element)", "bi-hash", "#6f42c1")
            );
        } else {
            examplesMenu.getItems().addAll(
                    exampleItem("for $x in //item return $x",
                            "for $x in //item return $x", "bi-arrow-repeat", "#007bff"),
                    exampleItem("for $x in //item where $x/@id='1' return $x/name",
                            "for $x in //item where $x/@id='1' return $x/name", "bi-funnel-fill", "#ffc107")
            );
        }
    }

    /**
     * Builds a single "Examples" menu item that inserts the given expression
     * into the active query CodeArea.
     */
    private MenuItem exampleItem(String label, String expression, String iconLiteral, String iconColor) {
        MenuItem item = new MenuItem(label);
        IconifyIcon icon = new IconifyIcon(iconLiteral);
        icon.setIconSize(14);
        icon.setIconColor(Color.web(iconColor));
        item.setGraphic(icon);
        item.setOnAction(e -> activeQueryCodeArea().replaceText(expression));
        return item;
    }

    /**
     * Loads a previously saved query file into the matching query CodeArea.
     */
    private void loadSavedQuery(File queryFile, boolean isXPath) {
        String content = favoritesService.loadQuery(queryFile);
        if (content != null) {
            CodeArea target = isXPath ? xpathCodeArea : xqueryCodeArea;
            target.replaceText(content);
            queryTabPane.getSelectionModel().select(isXPath ? xPathTab : xQueryTab);
            setStatus("Loaded query: " + queryFile.getName(), false);
        } else {
            setStatus("Error: failed to load " + queryFile.getName(), true);
            showAlert(Alert.AlertType.ERROR, "Load Error",
                    "Failed to load query from file: " + queryFile.getName());
        }
    }

    /**
     * Prompts for a name and saves the active query to the query store.
     */
    private void saveCurrentQuery() {
        boolean isXPath = isXPathActive();
        String content = activeQueryCodeArea().getText();
        String type = isXPath ? "XPath" : "XQuery";

        if (content == null || content.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Empty Query",
                    "Please enter an " + type + " expression first.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Save " + type + " Query");
        dialog.setHeaderText("Save " + type + " Query to File");
        dialog.setContentText("Query name:");
        dialog.getDialogPane().setGraphic(new IconifyIcon("bi-floppy"));

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (name.isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Invalid Name", "Please enter a valid query name.");
                return;
            }
            File savedFile = isXPath
                    ? favoritesService.saveXPathQuery(name, content)
                    : favoritesService.saveXQueryQuery(name, content);
            if (savedFile != null) {
                setStatus(type + " query saved as: " + name, false);
                showAlert(Alert.AlertType.INFORMATION, "Query Saved", type + " query saved as: " + name);
            } else {
                showAlert(Alert.AlertType.ERROR, "Save Error", "Failed to save " + type + " query.");
            }
        });
    }

    /**
     * Opens a file chooser to load a query from disk into the active CodeArea.
     */
    private void loadQueryFromFile() {
        boolean isXPath = isXPathActive();
        FileChooser fileChooser = new FileChooser();
        File initialDir = isXPath
                ? favoritesService.getXPathQueriesDir().toFile()
                : favoritesService.getXQueryQueriesDir().toFile();
        if (initialDir.exists()) {
            fileChooser.setInitialDirectory(initialDir);
        }
        if (isXPath) {
            fileChooser.setTitle("Open XPath Query File");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("XPath Files", "*.xpath"),
                    new FileChooser.ExtensionFilter("All Files", "*.*"));
        } else {
            fileChooser.setTitle("Open XQuery File");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("XQuery Files", "*.xquery", "*.xq"),
                    new FileChooser.ExtensionFilter("All Files", "*.*"));
        }

        Stage stage = getScene() != null ? (Stage) getScene().getWindow() : null;
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            String content = favoritesService.loadQuery(file);
            if (content != null) {
                activeQueryCodeArea().replaceText(content);
                setStatus("Loaded query from: " + file.getName(), false);
            } else {
                showAlert(Alert.AlertType.ERROR, "Load Error", "Failed to load query from file.");
            }
        }
    }

    /**
     * Saves the active query and registers it as a favorite.
     */
    private void addCurrentQueryToFavorites(boolean isXPath) {
        String content = (isXPath ? xpathCodeArea : xqueryCodeArea).getText();
        String type = isXPath ? "XPath" : "XQuery";

        if (content == null || content.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Empty Query",
                    "Please enter an " + type + " expression first.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add " + type + " Query to Favorites");
        dialog.setHeaderText("Save and add " + type + " query to favorites");
        dialog.setContentText("Query name:");
        dialog.getDialogPane().setGraphic(new IconifyIcon("bi-star"));

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (name.isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Invalid Name", "Please enter a valid query name.");
                return;
            }
            File savedFile = isXPath
                    ? favoritesService.saveXPathQuery(name, content)
                    : favoritesService.saveXQueryQuery(name, content);
            if (savedFile != null) {
                favoritesService.addFavorite(savedFile.getAbsolutePath(), name,
                        isXPath ? "XPath Queries" : "XQuery Queries");
                setStatus(type + " query added to favorites: " + name, false);
                showAlert(Alert.AlertType.INFORMATION, "Added to Favorites",
                        type + " query '" + name + "' added to favorites.");
            } else {
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to save " + type + " query.");
            }
        });
    }

    /**
     * Opens the on-disk folder holding saved queries of the given type.
     */
    private void openQueriesFolder(boolean isXPath) {
        try {
            File folder = isXPath
                    ? favoritesService.getXPathQueriesDir().toFile()
                    : favoritesService.getXQueryQueriesDir().toFile();
            if (folder.exists()) {
                java.awt.Desktop.getDesktop().open(folder);
            }
        } catch (Exception e) {
            logger.error("Failed to open queries folder", e);
        }
    }

    /**
     * Shows a simple alert dialog.
     */
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initOwner(getScene() != null ? getScene().getWindow() : null);
        alert.showAndWait();
    }

    /**
     * Sets up keyboard shortcuts.
     */
    private void setupKeyboardShortcuts() {
        // Ctrl+Enter to execute
        xpathCodeArea.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode() == KeyCode.ENTER) {
                executeQuery();
                e.consume();
            }
        });

        xqueryCodeArea.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode() == KeyCode.ENTER) {
                executeQuery();
                e.consume();
            }
        });
    }

    /**
     * Attaches focus listeners that lazily initialize the IntelliSense engines
     * the first time either query CodeArea gains focus.
     */
    private void setupIntelliSense() {
        xpathCodeArea.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                initializeIntelliSense();
            }
        });
        xqueryCodeArea.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                initializeIntelliSense();
            }
        });
    }

    /**
     * Initializes the XPath/XQuery IntelliSense engines (called lazily on first focus).
     * The engines pull the current XML content from the active tab via the content supplier.
     */
    private void initializeIntelliSense() {
        if (intelliSenseInitialized) {
            return;
        }
        try {
            xpathIntelliSenseEngine = new XPathIntelliSenseEngine(xpathCodeArea, false);
            xqueryIntelliSenseEngine = new XPathIntelliSenseEngine(xqueryCodeArea, true);

            xpathIntelliSenseEngine.setXmlContentSupplier(this::getContent);
            xqueryIntelliSenseEngine.setXmlContentSupplier(this::getContent);

            intelliSenseInitialized = true;
            logger.debug("IntelliSense engines initialized for Unified XPath panel");
        } catch (Exception e) {
            logger.error("Failed to initialize IntelliSense: {}", e.getMessage(), e);
        }
    }

    /**
     * Executes the current query (XPath or XQuery based on active tab).
     */
    public void executeQuery() {
        // Determine which tab is active
        boolean isXPath = queryTabPane.getSelectionModel().getSelectedItem() == xPathTab;
        CodeArea activeCodeArea = isXPath ? xpathCodeArea : xqueryCodeArea;
        String query = activeCodeArea.getText().trim();

        if (query.isEmpty()) {
            setStatus("Error: Query is empty", true);
            return;
        }

        String xmlContent = getContent();
        if (xmlContent == null || xmlContent.isBlank()) {
            setStatus("Error: No XML content available from active tab", true);
            return;
        }

        setStatus("Executing...", false);
        executeButton.setDisable(true);

        Task<String> queryTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                if (isXPath) {
                    return executeXPath(xmlContent, query);
                } else {
                    return executeXQuery(xmlContent, query);
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
     * Gets the XML content from the active tab via the supplier.
     */
    private String getContent() {
        if (contentSupplier != null) {
            return contentSupplier.get();
        }
        return null;
    }

    /**
     * Executes an XPath expression against the XML content.
     */
    private String executeXPath(String xmlContent, String xpathExpression) throws SaxonApiException {
        // Parse XML as document
        DocumentBuilder docBuilder = saxonProcessor.newDocumentBuilder();
        XdmNode doc = docBuilder.build(new StreamSource(new StringReader(xmlContent)));

        // Compile and execute XPath
        XPathExecutable executable = xpathCompiler.compile(xpathExpression);
        XPathSelector selector = executable.load();
        selector.setContextItem(doc);

        XdmValue result = selector.evaluate();

        return formatResult(result);
    }

    /**
     * Executes an XQuery expression against the XML content.
     */
    private String executeXQuery(String xmlContent, String xqueryExpression) throws SaxonApiException {
        // Compile XQuery
        XQueryExecutable executable = xqueryCompiler.compile(xqueryExpression);
        XQueryEvaluator evaluator = executable.load();

        // Parse XML as document
        DocumentBuilder docBuilder = saxonProcessor.newDocumentBuilder();
        XdmNode doc = docBuilder.build(new StreamSource(new StringReader(xmlContent)));
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
                : "-fx-text-fill: -text-secondary; -fx-font-size: 12px;");
    }

    /**
     * Truncates a message to a maximum length.
     */
    private String truncateMessage(String message, int maxLength) {
        if (message == null) {
            return "";
        }
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength - 3) + "...";
    }

    /**
     * Sets the content supplier for the active tab.
     * This supplier is called when executing queries to get the XML content.
     *
     * @param supplier a supplier that returns the current XML content from the active tab
     */
    public void setActiveContentProvider(Supplier<String> supplier) {
        this.contentSupplier = supplier;
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
     *
     * @return The CodeArea used for XPath input
     */
    public CodeArea getXpathCodeArea() {
        return xpathCodeArea;
    }

    /**
     * Gets the XQuery CodeArea for external access.
     *
     * @return The CodeArea used for XQuery input
     */
    public CodeArea getXqueryCodeArea() {
        return xqueryCodeArea;
    }

    /**
     * Gets the result CodeArea for external access.
     *
     * @return The CodeArea used for result display
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
            logger.debug("Unified XPath Query Panel disposed");
        } catch (Exception e) {
            logger.warn("Error disposing XPath panel: {}", e.getMessage());
        }
    }
}
