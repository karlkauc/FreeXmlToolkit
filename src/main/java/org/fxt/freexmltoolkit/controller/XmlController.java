/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controls.XmlCodeEditor;
import org.fxt.freexmltoolkit.controls.XmlEditor;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.XmlService;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class XmlController {
    private final static Logger logger = LogManager.getLogger(XmlController.class);


    CodeArea codeAreaXpath = new CodeArea();
    CodeArea codeAreaXQuery = new CodeArea();

    VirtualizedScrollPane<CodeArea> virtualizedScrollPaneXpath;
    VirtualizedScrollPane<CodeArea> virtualizedScrollPaneXQuery;

    private MainController mainController;

    private final PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

    @FXML
    Button openFile, saveFile, prettyPrint, newFile, runXpathQuery, minifyButton;

    @FXML
    StackPane stackPaneXPath, stackPaneXQuery;



    FileChooser fileChooser = new FileChooser();





    @FXML
    Tab xPathTab, xQueryTab;

    @FXML
    TabPane xPathQueryPane, xmlFilesPane;

    @FXML
    TitledPane xPathQueryTitledPane;

    // Reference to the parent SplitPane for dynamic layout management
    private SplitPane parentVerticalSplitPane;

    @FXML
    TextArea textAreaTemp;

    @FXML
    XmlEditor emptyXmlEditor;


    // Executor for asynchronous UI tasks like formatting
    private final ExecutorService formattingExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread t = new Thread(runnable);
        t.setDaemon(true); // So the application can be terminated even when tasks are running
        return t;
    });


    @FXML
    private void initialize() {
        logger.debug("Initializing XML Controller");

        // Initialize XPath code area with syntax highlighting
        codeAreaXpath.setParagraphGraphicFactory(LineNumberFactory.get(codeAreaXpath));
        virtualizedScrollPaneXpath = new VirtualizedScrollPane<>(codeAreaXpath);
        stackPaneXPath.getChildren().add(virtualizedScrollPaneXpath);

        // Performance optimization: Debounce syntax highlighting updates
        codeAreaXpath.textProperty().addListener((obs, oldText, newText) -> {
            if (newText != null && !newText.equals(oldText)) {
                Platform.runLater(() -> codeAreaXpath.setStyleSpans(0, XmlCodeEditor.computeHighlighting(newText)));
            }
        });

        // Initialize XQuery code area with syntax highlighting
        codeAreaXQuery.setParagraphGraphicFactory(LineNumberFactory.get(codeAreaXQuery));
        virtualizedScrollPaneXQuery = new VirtualizedScrollPane<>(codeAreaXQuery);
        stackPaneXQuery.getChildren().add(virtualizedScrollPaneXQuery);

        // Performance optimization: Debounce syntax highlighting updates
        codeAreaXQuery.textProperty().addListener((obs, oldText, newText) -> {
            if (newText != null && !newText.equals(oldText)) {
                Platform.runLater(() -> codeAreaXQuery.setStyleSpans(0, XmlCodeEditor.computeHighlighting(newText)));
            }
        });

        var t = System.getenv("debug");
        if (t != null) {
            logger.debug("debug mode enabled");
            // Debug mode - test functionality can be added here if needed

            codeAreaXpath.replaceText(0, 0, "/FundsXML4/ControlData");
            codeAreaXQuery.replaceText(0, 0, """
                    for $i in /FundsXML4/Funds/Fund
                                      	where number($i/FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy=$i/Currency]/text()) -\s
                                      			sum($i/FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount[@ccy=$i/Currency]) > 10
                                          return
                                              string-join(
                                                  (
                                                      $i/Names/OfficialName,
                                                      $i/Currency,
                                                      format-number(number($i/FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy=$i/Currency]/text()), "###,##0.00"),
                                                      format-number(sum($i/FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount[@ccy=$i/Currency]), "###,##0.00"),
                                                      format-number(
                                      			number($i/FundDynamicData/TotalAssetValues/TotalAssetValue/TotalNetAssetValue/Amount[@ccy=$i/Currency]/text()) -\s
                                      			sum($i/FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount[@ccy=$i/Currency])
                                      		, "###,##0.00")
                                                  ), ' | '
                                              )
                    """ );
        }

        reloadXmlText();

        // Initialize parent SplitPane reference for dynamic layout management
        Platform.runLater(() -> {
            if (xPathQueryTitledPane != null) {
                logger.debug("TitledPane found: {}", xPathQueryTitledPane);

                // Performance optimization: Limit parent traversal to avoid infinite loops
                Node parent = xPathQueryTitledPane.getParent();
                int traversalCount = 0;
                final int MAX_TRAVERSAL = 10; // Prevent infinite loops

                while (parent != null && !(parent instanceof SplitPane) && traversalCount < MAX_TRAVERSAL) {
                    logger.debug("Current parent: {} (class: {})", parent, parent.getClass());
                    parent = parent.getParent();
                    traversalCount++;
                }

                if (parent instanceof SplitPane) {
                    parentVerticalSplitPane = (SplitPane) parent;
                    logger.debug("Found parent SplitPane: {} - items count: {}", parentVerticalSplitPane, parentVerticalSplitPane.getItems().size());
                } else {
                    logger.warn("Could not find SplitPane parent for TitledPane after {} traversals", traversalCount);
                }
            } else {
                logger.warn("xPathQueryTitledPane is null during initialization");
            }
        });

        xmlFilesPane.setOnDragOver(this::handleFileOverEvent);
        xmlFilesPane.setOnDragExited(this::handleDragExitedEvent);
        xmlFilesPane.setOnDragDropped(this::handleFileDroppedEvent);

        // Add listener for continuous validation
    }

    /**
     * Creates and adds a new XML editor tab to the tab pane.
     *
     * @param file The file to load, or null for a new empty editor
     */
    private void createAndAddXmlTab(File file) {
        // Close empty tabs when opening a file (not when creating new tabs)
        if (file != null) {
            closeEmptyXmlTabs();
        }
        
        // The XmlEditor constructors must be adapted to accept the MainController
        XmlEditor xmlEditor = new XmlEditor(file); // Keep your constructor
        xmlEditor.setMainController(this.mainController); // Pass the controller

        // Using XSD-based implementation for validation and completion
        logger.debug("✅ Using XSD-based implementation");
        
        xmlEditor.refresh();

        // Apply sidebar visibility setting from preferences
        if (mainController != null) {
            boolean sidebarVisible = mainController.isXmlEditorSidebarVisible();
            xmlEditor.setXmlEditorSidebarVisible(sidebarVisible);
            logger.debug("Applied sidebar visibility setting to new tab: {}", sidebarVisible);
        }

        // Add a listener for text changes (XSD-based validation)

        xmlFilesPane.getTabs().add(xmlEditor);
        xmlFilesPane.getSelectionModel().select(xmlEditor);

        if (file != null) {
            mainController.addFileToRecentFiles(file);
        }
    }

    /**
     * Closes all XML editor tabs that have no content.
     */
    private void closeEmptyXmlTabs() {
        var tabsToRemove = xmlFilesPane.getTabs().stream()
                .filter(tab -> tab instanceof XmlEditor)
                .map(tab -> (XmlEditor) tab)
                .filter(this::isEmptyXmlEditor)
                .collect(java.util.stream.Collectors.toList());

        if (!tabsToRemove.isEmpty()) {
            logger.debug("Closing {} empty XML editor tabs", tabsToRemove.size());
            xmlFilesPane.getTabs().removeAll(tabsToRemove);
        }
    }

    /**
     * Checks if an XML editor tab is empty (no content and no associated file).
     */
    private boolean isEmptyXmlEditor(XmlEditor xmlEditor) {
        // Check if editor has a file associated
        if (xmlEditor.getXmlFile() != null) {
            return false;
        }

        // Check if editor has any content
        String content = xmlEditor.codeArea.getText();
        return content == null || content.trim().isEmpty();
    }

    /**
     * Handles drag over events for file dropping.
     *
     * @param event The drag event
     */
    @FXML
    void handleFileOverEvent(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
            if (!xmlFilesPane.getStyleClass().contains("xmlPaneFileDragDrop-active")) {
                xmlFilesPane.getStyleClass().add("xmlPaneFileDragDrop-active");
            }
        } else {
            event.consume();
        }
    }

    /**
     * Handles drag exit events for file dropping.
     *
     * @param event The drag event
     */
    @FXML
    void handleDragExitedEvent(DragEvent event) {
        xmlFilesPane.getStyleClass().clear();
        xmlFilesPane.getStyleClass().add("tab-pane");
    }

    /**
     * Handles file dropped events for file dropping.
     *
     * @param event The drag event
     */
    @FXML
    void handleFileDroppedEvent(DragEvent event) {
        Dragboard db = event.getDragboard();

        for (File f : db.getFiles()) {
            logger.debug("add File: '{}': {}", f.getName(), f.getAbsolutePath());
            createAndAddXmlTab(f);
        }
    }

    /**
     * The method is now public and has been refactored to use the
     * central `createAndAddXmlTab` method. This avoids code duplication.
     *
     * @param f The file to load.
     */
    public void loadFile(File f) {
        logger.debug("Loading file {} via createAndAddXmlTab", f.getAbsolutePath());
        createAndAddXmlTab(f);
    }

    /**
     * Handles the new file button press event.
     */
    @FXML
    private void newFilePressed() {
        logger.debug("New File Pressed");
        // Use the central method for creating tabs
        createAndAddXmlTab(null);
    }

    /**
     * Creates a new XML file tab. Called from MainController.
     */
    public void createNewFile() {
        logger.debug("Creating new XML file from main menu");
        createAndAddXmlTab(null);
    }

    /**
     * Gets the currently active XML editor tab.
     *
     * @return The current XmlEditor or null if no tab is selected
     */
    private XmlEditor getCurrentXmlEditor() {
        Tab active = xmlFilesPane.getSelectionModel().getSelectedItem();
        return (XmlEditor) active;
    }

    // The font size methods can be removed since the control
    // now happens directly in XmlCodeEditor via keyboard shortcuts and mouse wheel.
    // If you want to keep them for toolbar buttons, you can implement them like this:

    /**
     * Increases the font size of the current editor.
     */
    @FXML
    private void increaseFontSize() {
        XmlEditor editor = getCurrentXmlEditor();
        if (editor != null) {
            editor.getXmlCodeEditor().increaseFontSize();
        }
    }

    /**
     * Decreases the font size of the current editor.
     */
    @FXML
    private void decreaseFontSize() {
        XmlEditor editor = getCurrentXmlEditor();
        if (editor != null) {
            editor.getXmlCodeEditor().decreaseFontSize();
        }
    }

    /**
     * Gets the current code area from the active XML editor.
     * The `getCurrentCodeArea()` method must be adapted to find the new structure.
     *
     * @return The current CodeArea or null if no editor is active
     */
    private CodeArea getCurrentCodeArea() {
        XmlEditor editor = getCurrentXmlEditor();
        if (editor != null && editor.getXmlCodeEditor() != null) {
            // Access the CodeArea through the XmlCodeEditor.
            return editor.getXmlCodeEditor().getCodeArea();
        }
        return null;
    }

    /**
     * Executes the current XPath or XQuery and displays the result.
     */
    @FXML
    private void runXpathQueryPressed() {
        var currentCodeArea = getCurrentCodeArea();

        if (currentCodeArea != null && currentCodeArea.getText() != null) {
            String xml = currentCodeArea.getText();
            Tab selectedItem = xPathQueryPane.getSelectionModel().getSelectedItem();

            // Performance optimization: Cache the query text to avoid repeated UI access
            final String query;
            try {
                query = ((CodeArea) ((VirtualizedScrollPane<?>) ((StackPane) selectedItem.getContent()).getChildren().getFirst()).getContent()).getText();
            } catch (Exception e) {
                logger.error("Error accessing query text: {}", e.getMessage());
                return;
            }

            if (query == null || query.trim().isEmpty()) {
                logger.warn("Query is empty, nothing to execute");
                return;
            }

            logger.debug("QUERY: {}", query);

            // Performance optimization: Execute query in background thread for large XML files
            Task<String> queryTask = new Task<>() {
                @Override
                protected String call() throws Exception {
                    switch (selectedItem.getId()) {
                        case "xQueryTab":
                            return String.join(System.lineSeparator(), getCurrentXmlEditor().getXmlService().getXQueryResult(query));
                        case "xPathTab":
                            return getCurrentXmlEditor().getXmlService().getXmlFromXpath(xml, query);
                        default:
                            return "";
                    }
                }
            };

            queryTask.setOnSucceeded(event -> {
                String queryResult = queryTask.getValue();
                if (queryResult != null && !queryResult.isEmpty()) {
                    logger.debug("Query result length: {}", queryResult.length());
                    currentCodeArea.clear();
                    currentCodeArea.replaceText(0, 0, queryResult);
                } else {
                    logger.debug("Query returned empty result");
                }
            });

            queryTask.setOnFailed(event -> {
                logger.error("Query execution failed: {}", queryTask.getException().getMessage());
            });

            // Execute in background thread for better UI responsiveness
            new Thread(queryTask).start();
        }
    }

    /**
     * Sets the parent controller for this XML controller.
     *
     * @param parentController The main controller
     */
    public void setParentController(MainController parentController) {
        logger.debug("XML Controller - set parent controller");
        this.mainController = parentController;
        // Using XSD-based implementation for validation and completion
        logger.debug("Using XSD-based implementation");

        // Initialize XPath Query Pane visibility based on saved preference
        if (mainController != null) {
            boolean xpathPaneVisible = mainController.isXPathQueryPaneVisible();
            // Use Platform.runLater to ensure UI is fully loaded before applying visibility
            Platform.runLater(() -> {
                setXPathQueryPaneVisible(xpathPaneVisible);
            });
        }
    }


    /**
     * Displays the content of a file in the current code area.
     *
     * @param file The file to display
     */
    public void displayFileContent(File file) {
        if (file != null && file.exists()) {
            try {
                // Read the file content
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);

                if (mainController != null) {
                    mainController.addFileToRecentFiles(file);
                }

                // Set the content in the CodeArea
                var area = getCurrentCodeArea();
                if (area != null) {
                    area.replaceText(content);
                    logger.debug("File {} displayed.", file.getName());
                }

            } catch (IOException e) {
                logger.error("Could not read file {}", file.getAbsolutePath(), e);
            }
        }
    }

    /**
     * Reloads the XML text from the current file.
     */
    @FXML
    public void reloadXmlText() {
        try {
            XmlEditor xmlEditor = getCurrentXmlEditor();
            if (xmlEditor != null && xmlEditor.getXmlFile() != null && xmlEditor.getXmlFile().exists()) {
                xmlEditor.refresh();

                if (xmlEditor.getXmlFile() != null) {
                    textAreaTemp.setText(xmlEditor.getXmlFile().getName());
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * Called by the "Save File" button in the FXML file.
     * This method orchestrates the entire save process: validation, user confirmation, and actual saving.
     */
    @FXML
    private void saveFile() {
        XmlEditor currentEditor = getCurrentXmlEditor();
        CodeArea currentCodeArea = getCurrentCodeArea();

        // 1. Ensure that an editor is active
        if (currentEditor == null || currentCodeArea == null) {
            logger.warn("Save action triggered, but no active editor found.");
            return;
        }

        // 2. Validate content. The XmlService checks for well-formedness
        //    and, if a schema is present, for schema validity.
        String contentToValidate = currentCodeArea.getText();
        XmlService service = currentEditor.getXmlService();

        // Determine schema file for validation.
        // Use the schema recognized by the service.
        File schemaToUse = null;
        if (schemaToUse == null) {
            schemaToUse = service.getCurrentXsdFile();
        }

        // 2. Validate content.
        // The `validateText(String, File)` method checks for well-formedness when the schema is null,
        // and additionally for schema validity when a schema is specified.
        List<SAXParseException> errors = service.validateText(contentToValidate, schemaToUse);

        // 3. If errors were found, ask the user for confirmation.
        if (errors != null && !errors.isEmpty()) {
            Alert confirmationDialog = new Alert(Alert.AlertType.CONFIRMATION);
            confirmationDialog.setTitle("Validation Errors");
            confirmationDialog.setHeaderText(errors.size() + " validation errors found.");
            confirmationDialog.setContentText("The XML is not well-formed or not schema-compliant.\n\nReally save?");

            Optional<ButtonType> result = confirmationDialog.showAndWait();
            // Cancel action if the user doesn't click "OK".
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
        }

        // 4. Proceed with saving.
        saveTextToFile(currentEditor);
    }

    /**
     * A helper method that encapsulates the actual file I/O logic.
     * It distinguishes between saving a new and an existing file.
     *
     * @param editor The XmlEditor whose content should be saved.
     */
    private void saveTextToFile(XmlEditor editor) {
        File targetFile = editor.getXmlFile();
        String content = editor.getXmlCodeEditor().getCodeArea().getText();

        // Case 1: It's a new, unsaved document. Show "Save As" dialog.
        if (targetFile == null) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save XML File");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files (*.xml)", "*.xml"));

            // Set the last used directory for better user experience.
            String lastDirString = propertiesService.getLastOpenDirectory();
            if (lastDirString != null) {
                File lastDir = new File(lastDirString);
                if (lastDir.exists() && lastDir.isDirectory()) {
                    fileChooser.setInitialDirectory(lastDir);
                }
            }

            File selectedFile = fileChooser.showSaveDialog(xmlFilesPane.getScene().getWindow());

            if (selectedFile == null) {
                return; // User cancelled the dialog.
            }

            // Update the editor with the information of the new file.
            targetFile = selectedFile;
            editor.setXmlFile(targetFile);
            editor.getXmlService().setCurrentXmlFile(targetFile);
            mainController.addFileToRecentFiles(targetFile);

            // Save the new directory for next time.
            if (targetFile.getParentFile() != null) {
                propertiesService.setLastOpenDirectory(targetFile.getParentFile().getAbsolutePath());
            }
        }

        // Case 2: Write file (either the existing or newly selected one).
        try {
            Files.writeString(targetFile.toPath(), content, StandardCharsets.UTF_8);
            logger.info("File successfully saved: {}", targetFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Error writing file: {}", targetFile.getAbsolutePath(), e);
            new Alert(Alert.AlertType.ERROR, "Could not save file:\n" + e.getMessage()).showAndWait();
        }
    }

    /**
     * Minifies the current XML text by removing unnecessary whitespace.
     */
    @FXML
    private void minifyXmlText() {
        final CodeArea currentCodeArea = getCurrentCodeArea();
        if (currentCodeArea == null) return;
        final String xml = currentCodeArea.getText();
        if (xml == null || xml.isBlank()) return;

        // The operation is offloaded to a background thread to avoid blocking the UI.
        minifyButton.setDisable(true);

        Task<String> minifyTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                // Calls the new, significantly faster StAX-based method.
                return XmlService.convertXmlToOneLineFast(xml);
            }
        };

        minifyTask.setOnSucceeded(event -> {
            String minifiedString = minifyTask.getValue();
            if (minifiedString != null && !minifiedString.isEmpty()) {
                currentCodeArea.clear();
                currentCodeArea.replaceText(0, 0, minifiedString);
            }
            minifyButton.setDisable(false);
        });

        minifyTask.setOnFailed(event -> {
            logger.error("Failed to minify XML", minifyTask.getException());
            new Alert(Alert.AlertType.ERROR, "Could not minify XML: " + minifyTask.getException().getMessage()).showAndWait();
            minifyButton.setDisable(false);
        });

        formattingExecutor.submit(minifyTask);
    }

    /**
     * Formats the current XML text with proper indentation.
     */
    @FXML
    private void prettifyingXmlText() {
        final CodeArea currentCodeArea = getCurrentCodeArea();
        if (currentCodeArea == null) return;
        final String text = currentCodeArea.getText();
        if (text == null || text.isBlank()) return;

        prettyPrint.setDisable(true);

        Task<String> formatTask = new Task<>() {
            @Override
            protected String call() {
                return XmlService.prettyFormat(text, propertiesService.getXmlIndentSpaces());
            }
        };

        formatTask.setOnSucceeded(event -> {
            String prettyString = formatTask.getValue();
            if (prettyString != null && !prettyString.isEmpty()) {
                // Store current caret position
                int caretPosition = currentCodeArea.getCaretPosition();
                int textLength = currentCodeArea.getLength();

                // Replace entire text content safely
                currentCodeArea.replaceText(0, textLength, prettyString);

                // Restore caret position synchronously, ensuring it's within valid bounds
                int newCaretPosition = Math.min(caretPosition, prettyString.length());
                currentCodeArea.moveTo(newCaretPosition);
            }
            prettyPrint.setDisable(false);
        });

        formatTask.setOnFailed(event -> {
            logger.error("Failed to pretty-print XML", formatTask.getException());
            new Alert(Alert.AlertType.ERROR, "Could not format XML: " + formatTask.getException().getMessage()).showAndWait();
            prettyPrint.setDisable(false);
        });

        formattingExecutor.submit(formatTask);
    }


    /**
     * Moves the cursor to the beginning of the document.
     */
    @FXML
    private void moveUp() {
        logger.debug("Moving caret and scrollbar to the beginning.");
        XmlEditor editor = getCurrentXmlEditor();
        if (editor != null) {
            editor.getXmlCodeEditor().moveUp();
        }
    }

    /**
     * Moves the cursor to the end of the document.
     */
    @FXML
    private void moveDown() {
        logger.debug("Moving caret and scrollbar to the end.");
        XmlEditor editor = getCurrentXmlEditor();
        if (editor != null) {
            editor.getXmlCodeEditor().moveDown();
        }
    }

    /**
     * Handles the open file button press event.
     */
    @FXML
    private void openFile() {
        // Read the last opened directory from the PropertiesService
        String lastDirString = propertiesService.getLastOpenDirectory();
        if (lastDirString != null) {
            File lastDir = new File(lastDirString);
            if (lastDir.exists() && lastDir.isDirectory()) {
                fileChooser.setInitialDirectory(lastDir);
            }
        }

        // Ensure only the XML filter is active
        fileChooser.getExtensionFilters().clear();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files (*.xml)", "*.xml"));
        File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null && selectedFile.exists()) {
            logger.debug("Selected File: {}", selectedFile.getAbsolutePath());

            // Save the new directory in the PropertiesService
            if (selectedFile.getParentFile() != null) {
                propertiesService.setLastOpenDirectory(selectedFile.getParentFile().getAbsolutePath());
            }

            createAndAddXmlTab(selectedFile); // Use the central method

            // Set focus on the new editor after UI changes have been processed.
            Platform.runLater(() -> {
                XmlEditor xmlEditor = getCurrentXmlEditor();
                // 1. First set focus on the tab container.
                xmlFilesPane.requestFocus();

                // 2. Then pass focus to the CodeArea inside.
                if (xmlEditor.getXmlCodeEditor().getCodeArea() != null) {
                    xmlEditor.getXmlCodeEditor().getCodeArea().requestFocus();
                    xmlEditor.getXmlCodeEditor().moveUp(); // Sets the cursor to the beginning.
                }
            });
        } else {
            logger.debug("No file selected");
        }
    }

    /**
     * Shuts down executor services.
     */
    public void shutdown() {
        if (!formattingExecutor.isShutdown()) {
            formattingExecutor.shutdownNow();
            logger.debug("Formatting-Executor-Service shut down.");
        }
        logger.info("XmlController shutdown completed.");
    }



    /**
     * Sets the visibility of the XML Editor Sidebar for all tabs.
     *
     * @param visible true to show the sidebar, false to hide it completely
     */
    public void setXmlEditorSidebarVisible(boolean visible) {
        logger.debug("Setting XML Editor Sidebar visibility to: {}", visible);

        // Apply to all existing tabs
        if (xmlFilesPane != null) {
            for (Tab tab : xmlFilesPane.getTabs()) {
                if (tab instanceof XmlEditor xmlEditor) {
                    xmlEditor.setXmlEditorSidebarVisible(visible);
                }
            }
        }
    }

    /**
     * Sets the visibility of the XPath/XQuery TitledPane.
     * Dynamically adds/removes the TitledPane from the SplitPane for proper layout management.
     *
     * @param visible true to show the pane, false to hide it completely
     */
    public void setXPathQueryPaneVisible(boolean visible) {
        logger.debug("Setting XPath Query Pane visibility to: {}", visible);
        logger.debug("xPathQueryTitledPane is null: {}", xPathQueryTitledPane == null);
        logger.debug("parentVerticalSplitPane is null: {}", parentVerticalSplitPane == null);

        if (xPathQueryTitledPane != null && parentVerticalSplitPane != null) {
            logger.debug("Current SplitPane items count: {}", parentVerticalSplitPane.getItems().size());
            logger.debug("TitledPane currently in SplitPane: {}", parentVerticalSplitPane.getItems().contains(xPathQueryTitledPane));

            if (visible) {
                // Add the TitledPane back if it's not already present
                if (!parentVerticalSplitPane.getItems().contains(xPathQueryTitledPane)) {
                    parentVerticalSplitPane.getItems().add(xPathQueryTitledPane);
                    // Restore the divider position (0.8 means XmlEditor takes 80% of space)
                    parentVerticalSplitPane.setDividerPositions(0.8);
                    logger.debug("Added XPath Query TitledPane back to SplitPane");
                } else {
                    logger.debug("TitledPane already present in SplitPane");
                }
            } else {
                // Remove the TitledPane completely from the SplitPane
                boolean removed = parentVerticalSplitPane.getItems().remove(xPathQueryTitledPane);
                logger.debug("Removed XPath Query TitledPane from SplitPane: {} - XmlEditor should now take full space", removed);
            }
            logger.debug("Final SplitPane items count: {}", parentVerticalSplitPane.getItems().size());
        } else {
            logger.warn("Cannot set XPath Query Pane visibility - missing references: xPathQueryTitledPane={}, parentVerticalSplitPane={}",
                    xPathQueryTitledPane != null, parentVerticalSplitPane != null);
        }
    }
}
