package org.fxt.freexmltoolkit.controller.controls;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XmlEditor;
import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.domain.ValidationError;
import org.fxt.freexmltoolkit.domain.XmlParserType;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.SchematronService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class XmlEditorSidebarController {

    private static final Logger logger = LogManager.getLogger(XmlEditorSidebarController.class);

    @FXML
    private TextField xsdPathField;

    @FXML
    private Button changeXsdButton;

    @FXML
    private Label validationStatusLabel;

    @FXML
    private CheckBox continuousValidationCheckBox;

    @FXML
    private Button manualValidateButton;

    @FXML
    private ComboBox<String> xsdFavoritesComboBox;

    @FXML
    private Button resetXsdButton;

    @FXML
    private ComboBox<XmlParserType> validatorTypeComboBox;

    @FXML
    private TextField xpathField;

    @FXML
    private TextField elementNameField;

    @FXML
    private TextField elementTypeField;

    @FXML
    private TextArea documentationTextArea;

    @FXML
    private ListView<String> exampleValuesListView;

    @FXML
    private ListView<String> childElementsListView;

    @FXML
    private TitledPane validationErrorsPane;

    @FXML
    private Label validationErrorsCountLabel;

    @FXML
    private ListView<ValidationError> validationErrorsListView;

    @FXML
    private TextField schematronPathField;

    @FXML
    private Button changeSchematronButton;

    @FXML
    private Label schematronValidationStatusLabel;

    @FXML
    private CheckBox continuousSchematronValidationCheckBox;

    @FXML
    private Button schematronDetailsButton;

    @FXML
    private TitledPane schematronErrorsPane;

    @FXML
    private Label schematronErrorsCountLabel;

    @FXML
    private ListView<SchematronService.SchematronValidationError> schematronErrorsListView;

    @FXML
    private Button toggleSidebarButton;

    @FXML
    private ToggleButton xsdDocumentationToggleButton;

    @FXML
    private Label xsdCommentsStatusLabel;

    @FXML
    private VBox sidebarContent;

    private XmlEditor xmlEditor;

    // Services
    private final FavoritesService favoritesService = FavoritesService.getInstance();
    private final PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

    // XSD management
    private File originalXsdFile; // Store the original linked XSD

    private String xsdPath;

    private boolean sidebarVisible = true;

    private org.fxt.freexmltoolkit.controller.MainController mainController;

    private VBox sidebarContainer; // Reference to the main container
    private double expandedWidth = 300; // Store the expanded width
    private final List<SchematronService.SchematronValidationError> currentSchematronErrors = new ArrayList<>();
    private final List<ValidationError> currentValidationErrors = new ArrayList<>();
    
    // XSD Documentation Comments tracking
    private boolean xsdCommentsActive = false;
    private static final String XSD_COMMENT_MARKER = " [XSD-DOC] ";

    public void setXmlEditor(XmlEditor xmlEditor) {
        this.xmlEditor = xmlEditor;
    }

    public void setMainController(org.fxt.freexmltoolkit.controller.MainController mainController) {
        this.mainController = mainController;
    }

    @FXML
    private void initialize() {
        // Add click handler for element name field to show node information
        if (elementNameField != null) {
            elementNameField.setOnMouseClicked(event -> {
                if (event.getClickCount() == 1) { // Single click
                    showNodeInformation();
                }
            });

            // Add visual feedback to indicate the field is clickable
            elementNameField.setStyle("-fx-cursor: hand;");
            elementNameField.setTooltip(new Tooltip("Click to show detailed node information"));
        }

        // Initialize Schematron errors list view
        initializeSchematronErrorsList();

        // Initialize example values list view with double-click handler
        initializeExampleValuesList();

        // Initialize child elements list view with double-click handler
        initializeChildElementsList();

        // Setup manual validate button
        if (manualValidateButton != null) {
            manualValidateButton.setOnAction(event -> {
                if (xmlEditor != null) {
                    xmlEditor.validateXml();
                }
            });
        }

        // Setup XSD favorites dropdown
        setupXsdFavoritesDropdown();

        // Setup validator type combobox
        setupValidatorTypeComboBox();

        // Setup reset XSD button
        if (resetXsdButton != null) {
            resetXsdButton.setOnAction(event -> resetToOriginalXsd());
        }

        changeXsdButton.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select XSD Schema");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSD Files", "*.xsd"));

            // Set initial directory to the same as the XML file if available
            if (xmlEditor != null && xmlEditor.getXmlFile() != null && xmlEditor.getXmlFile().getParentFile() != null) {
                fileChooser.setInitialDirectory(xmlEditor.getXmlFile().getParentFile());
            }

            File selectedFile = fileChooser.showOpenDialog(changeXsdButton.getScene().getWindow());
            if (selectedFile != null) {
                xsdPathField.setText(selectedFile.getAbsolutePath());
                if (xmlEditor != null) {
                    xmlEditor.setXsdFile(selectedFile);
                }
            }
        });

        changeSchematronButton.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Schematron Rules");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Schematron Files", "*.sch", "*.xslt", "*.xsl"));

            // Set initial directory to the same as the XML file if available
            if (xmlEditor != null && xmlEditor.getXmlFile() != null && xmlEditor.getXmlFile().getParentFile() != null) {
                fileChooser.setInitialDirectory(xmlEditor.getXmlFile().getParentFile());
            }

            File selectedFile = fileChooser.showOpenDialog(changeSchematronButton.getScene().getWindow());
            if (selectedFile != null) {
                schematronPathField.setText(selectedFile.getAbsolutePath());
                if (xmlEditor != null) {
                    xmlEditor.setSchematronFile(selectedFile);
                }
            }
        });

        continuousValidationCheckBox.setOnAction(event -> {
            if (continuousValidationCheckBox.isSelected() && xmlEditor != null) {
                xmlEditor.validateXml();
            }
        });

        continuousSchematronValidationCheckBox.setOnAction(event -> {
            if (continuousSchematronValidationCheckBox.isSelected() && xmlEditor != null) {
                xmlEditor.validateSchematron();
            }
        });

        // Setup validation errors ListView with custom cell factory
        if (validationErrorsListView != null) {
            validationErrorsListView.setCellFactory(listView -> new ListCell<ValidationError>() {
                @Override
                protected void updateItem(ValidationError error, boolean empty) {
                    super.updateItem(error, empty);
                    if (empty || error == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(error.getDisplayText());
                        // Style based on severity
                        String style = "-fx-text-fill: ";
                        switch (error.severity().toUpperCase()) {
                            case "ERROR" -> style += "red";
                            case "WARNING" -> style += "orange";
                            case "INFO" -> style += "blue";
                            default -> style += "black";
                        }
                        setStyle(style + "; -fx-padding: 2 5 2 5;");
                    }
                }
            });

            // Add click handler for navigation to error position
            validationErrorsListView.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) { // Double-click
                    ValidationError selectedError = validationErrorsListView.getSelectionModel().getSelectedItem();
                    if (selectedError != null && xmlEditor != null) {
                        navigateToError(selectedError);
                    }
                }
            });
        }
    }

    @FXML
    private void toggleSidebar() {
        // Use the global sidebar toggle functionality instead of local minimize/maximize
        if (mainController != null) {
            // Get the current state from the main controller's menu
            boolean currentlyVisible = mainController.isXmlEditorSidebarVisible();

            // Toggle the state through the MainController - this will:
            // 1. Update the menu CheckMenuItem
            // 2. Save the preference 
            // 3. Apply to all XML Editor tabs
            // 4. Completely hide/show the sidebar (not just minimize)
            mainController.toggleXmlEditorSidebarFromSidebar(!currentlyVisible);
        } else {
            // Alternative approach: Try to find the MainController through the Stage
            logger.warn("MainController not available directly - trying alternative approach");

            try {
                // Get the current stage and find MainController through the window
                javafx.stage.Window window = toggleSidebarButton.getScene().getWindow();
                if (window instanceof javafx.stage.Stage stage) {
                    Object userData = stage.getUserData();
                    // This is a fallback - in practice, we should ensure MainController is available
                    // For now, just use local toggle as fallback
                    toggleSidebarLocal();
                }
            } catch (Exception e) {
                logger.warn("Fallback failed - using local sidebar toggle: {}", e.getMessage());
                toggleSidebarLocal();
            }
        }
    }

    /**
     * Fallback method for local sidebar toggle (original behavior)
     */
    private void toggleSidebarLocal() {
        sidebarVisible = !sidebarVisible;

        if (sidebarVisible) {
            // Expand sidebar
            sidebarContent.setVisible(true);
            sidebarContent.setManaged(true);

            if (sidebarContainer != null) {
                sidebarContainer.setPrefWidth(expandedWidth);
                sidebarContainer.setMinWidth(250);
                sidebarContainer.setMaxWidth(400);
            }

            // Reset toggle button width when expanded
            toggleSidebarButton.setPrefWidth(Region.USE_COMPUTED_SIZE);
            toggleSidebarButton.setMinWidth(Region.USE_COMPUTED_SIZE);

            toggleSidebarButton.setText("◀");
        } else {
            // Collapse sidebar - minimize to just the toggle button width
            sidebarContent.setVisible(false);
            sidebarContent.setManaged(false);

            if (sidebarContainer != null) {
                sidebarContainer.setPrefWidth(32); // Minimal width for just the toggle button
                sidebarContainer.setMinWidth(32);
                sidebarContainer.setMaxWidth(32);
            }

            // Make toggle button take minimal space when collapsed
            toggleSidebarButton.setPrefWidth(30);
            toggleSidebarButton.setMinWidth(30);

            toggleSidebarButton.setText("▶");
        }
    }

    public void setValidationStatus(String status) {
        validationStatusLabel.setText("Validation status: " + status);
    }

    public void setXPath(String xpath) {
        xpathField.setText(xpath);
    }

    public void setPossibleChildElements(java.util.List<String> childElements) {
        childElementsListView.getItems().setAll(childElements);
    }

    public boolean isContinuousValidationSelected() {
        return continuousValidationCheckBox.isSelected();
    }

    public void setXsdPath(String absolutePath) {
        this.xsdPath = absolutePath;
        if (xsdPathField != null) {
            xsdPathField.setText(absolutePath != null ? absolutePath : "No XSD schema selected");
        }
    }

    // Additional methods for better integration
    public void updateValidationStatus(String status, String color) {
        validationStatusLabel.setText("Validation status: " + status);
        validationStatusLabel.setStyle("-fx-text-fill: " + color + ";");
    }

    public void updateSchematronValidationStatus(String status, String color) {
        if (schematronValidationStatusLabel != null) {
            schematronValidationStatusLabel.setText("Schematron validation status: " + status);
            schematronValidationStatusLabel.setStyle("-fx-text-fill: " + color + ";");
        }
    }

    /**
     * Updates the Schematron validation status and stores the error details for the details button.
     *
     * @param status The status message to display
     * @param color  The color for the status text
     * @param errors List of detailed Schematron errors (can be null or empty)
     */
    public void updateSchematronValidationStatus(String status, String color, List<SchematronService.SchematronValidationError> errors) {
        updateSchematronValidationStatus(status, color);

        // Store the errors for the details view
        this.currentSchematronErrors.clear();
        if (errors != null) {
            this.currentSchematronErrors.addAll(errors);
        }

        // Show/hide the details button based on whether there are errors
        if (schematronDetailsButton != null) {
            boolean hasErrors = errors != null && !errors.isEmpty();
            schematronDetailsButton.setVisible(hasErrors);
        }

        // Update the new Schematron errors list
        updateSchematronErrorsList(errors);
    }

    public void setXsdPathField(String path) {
        if (xsdPathField != null) {
            xsdPathField.setText(path != null ? path : "No XSD schema selected");
        }
    }

    public void setSchematronPathField(String path) {
        if (schematronPathField != null) {
            schematronPathField.setText(path != null ? path : "No Schematron rules selected");
        }
    }

    public void setContinuousValidation(boolean selected) {
        if (continuousValidationCheckBox != null) {
            continuousValidationCheckBox.setSelected(selected);
        }
    }

    public void setContinuousSchematronValidation(boolean selected) {
        if (continuousSchematronValidationCheckBox != null) {
            continuousSchematronValidationCheckBox.setSelected(selected);
        }
    }

    // Method to set the sidebar container reference from XmlEditor
    public void setSidebarContainer(VBox container) {
        this.sidebarContainer = container;
        if (container != null) {
            this.expandedWidth = container.getPrefWidth();
        }
    }

    public void setElementName(String elementName) {
        if (elementNameField != null) {
            elementNameField.setText(elementName != null ? elementName : "");
        }
    }

    public void setElementType(String elementType) {
        if (elementTypeField != null) {
            elementTypeField.setText(elementType != null ? elementType : "");
        }
    }

    public void setDocumentation(String documentation) {
        if (documentationTextArea != null) {
            documentationTextArea.setText(documentation != null ? documentation : "");
        }
    }

    public void setExampleValues(java.util.List<String> exampleValues) {
        if (exampleValuesListView != null) {
            if (exampleValues != null && !exampleValues.isEmpty()) {
                exampleValuesListView.getItems().setAll(exampleValues);
            } else {
                exampleValuesListView.getItems().setAll("No example values available");
            }
        }
    }

    public boolean isContinuousSchematronValidationSelected() {
        return continuousSchematronValidationCheckBox != null && continuousSchematronValidationCheckBox.isSelected();
    }

    @FXML
    private void showSchematronDetails() {
        if (currentSchematronErrors.isEmpty()) {
            return;
        }

        // Create a new stage for the detailed error view
        Stage detailStage = new Stage();
        detailStage.setTitle("Schematron Validation Errors");
        detailStage.setWidth(600);
        detailStage.setHeight(400);

        // Create the content
        VBox content = new VBox();
        content.setSpacing(10);
        content.setPadding(new Insets(10));

        Label titleLabel = new Label("Schematron Validation Errors (" + currentSchematronErrors.size() + " error(s)):");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // Create a list view for the errors
        ListView<String> errorList = new ListView<>();
        for (int i = 0; i < currentSchematronErrors.size(); i++) {
            SchematronService.SchematronValidationError error = currentSchematronErrors.get(i);
            StringBuilder errorText = new StringBuilder();

            errorText.append((i + 1)).append(". ");

            if (error.lineNumber() > 0) {
                errorText.append("Line ").append(error.lineNumber());
                if (error.columnNumber() > 0) {
                    errorText.append(", Col ").append(error.columnNumber());
                }
                errorText.append(": ");
            }

            errorText.append(error.message());

            if (error.ruleId() != null && !error.ruleId().isEmpty()) {
                errorText.append("\n   Rule: ").append(error.ruleId());
            }

            if (error.context() != null && !error.context().isEmpty()) {
                errorText.append("\n   Context: ").append(error.context());
            }

            errorList.getItems().add(errorText.toString());
        }

        // Close button
        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> detailStage.close());

        content.getChildren().addAll(titleLabel, errorList, closeButton);
        VBox.setVgrow(errorList, javafx.scene.layout.Priority.ALWAYS);

        Scene scene = new Scene(content);
        detailStage.setScene(scene);

        // Make it modal to the main window
        if (schematronDetailsButton.getScene() != null && schematronDetailsButton.getScene().getWindow() != null) {
            detailStage.initOwner(schematronDetailsButton.getScene().getWindow());
        }

        detailStage.show();
    }

    /**
     * Navigates to the position of a validation error in the XmlCodeEditor
     */
    private void navigateToError(ValidationError error) {
        if (xmlEditor == null || error.lineNumber() <= 0) {
            return;
        }

        // Get the CodeArea from the XmlEditor (works with both V1 and V2)
        org.fxmisc.richtext.CodeArea codeArea = xmlEditor.codeArea;
        if (codeArea == null) {
            return;
        }

        try {
            // Convert line/column to position (lines are 1-based in ValidationError, but 0-based in CodeArea)
            int targetLine = Math.max(0, error.lineNumber() - 1);
            int targetColumn = Math.max(0, error.columnNumber() - 1);

            // Get the paragraph (line) from the CodeArea
            if (targetLine < codeArea.getParagraphs().size()) {
                int lineStartPosition = codeArea.getAbsolutePosition(targetLine, 0);
                int lineLength = codeArea.getParagraph(targetLine).length();
                int targetPosition = lineStartPosition + Math.min(targetColumn, lineLength);

                // Move cursor to the error position
                codeArea.moveTo(targetPosition);

                // Select the position or a small range for visibility
                if (targetColumn < lineLength) {
                    // Try to select a word or character at the error position
                    int selectionEnd = Math.min(targetPosition + 10, lineStartPosition + lineLength);
                    codeArea.selectRange(targetPosition, selectionEnd);
                } else {
                    codeArea.selectRange(targetPosition, targetPosition);
                }

                // Request focus and scroll to make the position visible
                codeArea.requestFocus();
                codeArea.requestFollowCaret();
            }
        } catch (Exception e) {
            logger.error("Error navigating to validation error: {}", e.getMessage(), e);
        }
    }

    /**
     * Updates the validation errors display in the sidebar
     */
    public void updateValidationErrors(List<ValidationError> errors) {
        currentValidationErrors.clear();
        if (errors != null) {
            currentValidationErrors.addAll(errors);
        }

        // Update the ListView
        if (validationErrorsListView != null) {
            validationErrorsListView.getItems().setAll(currentValidationErrors);
        }

        // Update the count label
        if (validationErrorsCountLabel != null) {
            int errorCount = currentValidationErrors.size();
            validationErrorsCountLabel.setText("Validation Errors (" + errorCount + ")");
        }

        // Show/hide the validation errors pane based on whether there are errors
        if (validationErrorsPane != null) {
            boolean hasErrors = !currentValidationErrors.isEmpty();
            validationErrorsPane.setVisible(hasErrors);
            validationErrorsPane.setManaged(hasErrors);
        }
    }

    /**
     * Enhanced validation status update that also accepts validation errors
     */
    public void updateValidationStatus(String status, String color, List<ValidationError> errors) {
        updateValidationStatus(status, color);
        updateValidationErrors(errors);
    }

    /**
     * Shows detailed information about the node when clicking on the element name field
     */
    private void showNodeInformation() {
        if (xmlEditor == null) {
            return;
        }

        String currentXPath = xpathField.getText();
        if (currentXPath == null || currentXPath.trim().isEmpty()) {
            return;
        }

        try {
            // Find the node in the XML document using the current XPath
            org.w3c.dom.Node node = xmlEditor.findNodeByXPath(currentXPath);
            if (node != null) {
                // Update the sidebar with information about this node
                updateSidebarWithNodeInfo(node);
            }
        } catch (Exception e) {
            logger.error("Error showing node information: {}", e.getMessage(), e);
        }
    }

    /**
     * Updates the sidebar with information about the specified node
     */
    private void updateSidebarWithNodeInfo(org.w3c.dom.Node node) {
        if (xmlEditor == null || node == null) {
            return;
        }

        try {
            // Build XPath for the node
            String xpath = xmlEditor.buildXPathForNode(node);
            setXPath(xpath);

            // Set element name and type
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                setElementName(node.getNodeName());

                // Try to get element type from XSD if available
                org.fxt.freexmltoolkit.domain.XsdExtendedElement xsdElement = xmlEditor.findBestMatchingElement(xpath);
                if (xsdElement != null) {
                    setElementType(xsdElement.getElementType());
                    setDocumentation(xmlEditor.getDocumentationFromExtendedElement(xsdElement));

                    // Set example values
                    if (xsdElement.getExampleValues() != null && !xsdElement.getExampleValues().isEmpty()) {
                        setExampleValues(xsdElement.getExampleValues());
                    } else {
                        setExampleValues(java.util.List.of("No example values available"));
                    }

                    // Set child elements
                    if (xsdElement.getChildren() != null && !xsdElement.getChildren().isEmpty()) {
                        setPossibleChildElements(xsdElement.getChildren());
                    } else {
                        setPossibleChildElements(java.util.List.of("No child elements available"));
                    }
                } else {
                    // Fallback to DOM-based information
                    setElementType("Unknown");
                    setDocumentation("No XSD documentation available");
                    setExampleValues(java.util.List.of("No example values available"));
                    setPossibleChildElements(java.util.List.of("No child elements available"));
                }
            } else {
                setElementName(node.getNodeName());
                setElementType(getNodeTypeName(node.getNodeType()));
                setDocumentation("Node type: " + getNodeTypeName(node.getNodeType()));
                setExampleValues(java.util.List.of("No example values available"));
                setPossibleChildElements(java.util.List.of("No child elements available"));
            }
        } catch (Exception e) {
            logger.error("Error updating sidebar with node info: {}", e.getMessage(), e);
        }
    }

    /**
     * Helper method to get a readable name for node types
     */
    private String getNodeTypeName(short nodeType) {
        return switch (nodeType) {
            case org.w3c.dom.Node.ELEMENT_NODE -> "Element";
            case org.w3c.dom.Node.ATTRIBUTE_NODE -> "Attribute";
            case org.w3c.dom.Node.TEXT_NODE -> "Text";
            case org.w3c.dom.Node.CDATA_SECTION_NODE -> "CDATA Section";
            case org.w3c.dom.Node.ENTITY_REFERENCE_NODE -> "Entity Reference";
            case org.w3c.dom.Node.ENTITY_NODE -> "Entity";
            case org.w3c.dom.Node.PROCESSING_INSTRUCTION_NODE -> "Processing Instruction";
            case org.w3c.dom.Node.COMMENT_NODE -> "Comment";
            case org.w3c.dom.Node.DOCUMENT_NODE -> "Document";
            case org.w3c.dom.Node.DOCUMENT_TYPE_NODE -> "Document Type";
            case org.w3c.dom.Node.DOCUMENT_FRAGMENT_NODE -> "Document Fragment";
            case org.w3c.dom.Node.NOTATION_NODE -> "Notation";
            default -> "Unknown";
        };
    }

    /**
     * Setup XSD favorites dropdown with available XSD files
     */
    private void setupXsdFavoritesDropdown() {
        if (xsdFavoritesComboBox == null) {
            return;
        }

        // Load XSD favorites
        refreshXsdFavorites();

        // Handle selection
        xsdFavoritesComboBox.setOnAction(event -> {
            String selectedItem = xsdFavoritesComboBox.getSelectionModel().getSelectedItem();
            if (selectedItem != null && !selectedItem.isEmpty()) {
                applyFavoriteXsd(selectedItem);
            }
        });
    }

    /**
     * Refresh the XSD favorites dropdown
     */
    private void refreshXsdFavorites() {
        if (xsdFavoritesComboBox == null) {
            return;
        }

        xsdFavoritesComboBox.getItems().clear();

        // Get XSD favorites from service
        List<FileFavorite> xsdFavorites = favoritesService.getFavoritesByType(FileFavorite.FileType.XSD);

        for (FileFavorite favorite : xsdFavorites) {
            // Display format: "name (folder)" or just "name" if no folder
            String displayName = favorite.getName();
            if (favorite.getFolderName() != null && !favorite.getFolderName().isEmpty()) {
                displayName += " (" + favorite.getFolderName() + ")";
            }
            xsdFavoritesComboBox.getItems().add(displayName);
        }

        logger.debug("Loaded {} XSD favorites into dropdown", xsdFavorites.size());
    }

    /**
     * Apply selected favorite XSD schema
     */
    private void applyFavoriteXsd(String selectedDisplayName) {
        // Find the favorite by display name
        List<FileFavorite> xsdFavorites = favoritesService.getFavoritesByType(FileFavorite.FileType.XSD);

        FileFavorite selectedFavorite = null;
        for (FileFavorite favorite : xsdFavorites) {
            String displayName = favorite.getName();
            if (favorite.getFolderName() != null && !favorite.getFolderName().isEmpty()) {
                displayName += " (" + favorite.getFolderName() + ")";
            }

            if (displayName.equals(selectedDisplayName)) {
                selectedFavorite = favorite;
                break;
            }
        }

        if (selectedFavorite != null) {
            File newXsdFile = new File(selectedFavorite.getFilePath());
            if (newXsdFile.exists()) {
                // Store original XSD if not already stored
                if (originalXsdFile == null && xmlEditor != null && xmlEditor.getXsdFile() != null) {
                    originalXsdFile = xmlEditor.getXsdFile();
                }

                // Apply new XSD
                if (xmlEditor != null) {
                    xmlEditor.setXsdFile(newXsdFile);
                    xsdPathField.setText(newXsdFile.getAbsolutePath());

                    // Trigger validation with new schema
                    xmlEditor.validateXml();

                    logger.info("Applied XSD favorite: {}", selectedFavorite.getName());
                }
            } else {
                logger.warn("Selected XSD favorite file does not exist: {}", selectedFavorite.getFilePath());
                showAlert("XSD File Not Found", "The selected XSD file no longer exists:\n" + selectedFavorite.getFilePath());
            }
        }
    }

    /**
     * Reset to original XSD schema
     */
    private void resetToOriginalXsd() {
        if (originalXsdFile != null && xmlEditor != null) {
            xmlEditor.setXsdFile(originalXsdFile);
            xsdPathField.setText(originalXsdFile.getAbsolutePath());

            // Clear dropdown selection
            xsdFavoritesComboBox.getSelectionModel().clearSelection();

            // Trigger validation with original schema
            xmlEditor.validateXml();

            logger.info("Reset to original XSD: {}", originalXsdFile.getName());
        } else {
            logger.debug("No original XSD to reset to");
        }
    }

    /**
     * Setup validator type combobox with available validators
     */
    private void setupValidatorTypeComboBox() {
        if (validatorTypeComboBox == null) {
            return;
        }

        // Add all validator types to the combobox
        validatorTypeComboBox.getItems().addAll(XmlParserType.values());

        // Set current validator from properties
        XmlParserType currentValidator = propertiesService.getXmlParserType();
        validatorTypeComboBox.setValue(currentValidator);

        // Handle selection changes
        validatorTypeComboBox.setOnAction(event -> {
            XmlParserType selectedValidator = validatorTypeComboBox.getValue();
            if (selectedValidator != null) {
                // Save the selection to properties (setXmlParserType already persists the change)
                propertiesService.setXmlParserType(selectedValidator);

                logger.info("Changed validator to: {}", selectedValidator.getDisplayName());

                // Trigger re-validation with the new validator
                if (xmlEditor != null) {
                    xmlEditor.validateXml();
                }
            }
        });

        logger.debug("Validator type combobox initialized with current validator: {}", currentValidator);
    }


    /**
     * Initializes the Schematron errors list view with custom cell factory
     */
    private void initializeSchematronErrorsList() {
        if (schematronErrorsListView != null) {
            // Custom cell factory to display errors with click functionality
            schematronErrorsListView.setCellFactory(listView -> new ListCell<SchematronService.SchematronValidationError>() {
                @Override
                protected void updateItem(SchematronService.SchematronValidationError error, boolean empty) {
                    super.updateItem(error, empty);

                    if (empty || error == null) {
                        setText(null);
                        setGraphic(null);
                        setStyle("");
                        setOnMouseClicked(null);
                    } else {
                        // Format the error message for display
                        String displayText = String.format("[Line %d] %s",
                                error.lineNumber() > 0 ? error.lineNumber() : 0,
                                error.message());
                        setText(displayText);

                        // Style based on severity
                        String severity = error.severity() != null ? error.severity().toLowerCase() : "error";
                        switch (severity) {
                            case "error" -> setStyle("-fx-text-fill: #d32f2f; -fx-cursor: hand;");
                            case "warning" -> setStyle("-fx-text-fill: #f57c00; -fx-cursor: hand;");
                            case "info" -> setStyle("-fx-text-fill: #1976d2; -fx-cursor: hand;");
                            default -> setStyle("-fx-text-fill: #d32f2f; -fx-cursor: hand;");
                        }

                        // Add click handler to navigate to error location
                        setOnMouseClicked(event -> {
                            if (event.getClickCount() == 1) {
                                navigateToSchematronError(error);
                            }
                        });

                        // Add tooltip with detailed information
                        String tooltipText = String.format(
                                "Severity: %s\nRule ID: %s\nContext: %s\nLine: %d, Column: %d\n\nClick to navigate to error location",
                                error.severity() != null ? error.severity() : "Error",
                                error.ruleId() != null ? error.ruleId() : "Unknown",
                                error.context() != null ? error.context() : "Unknown",
                                error.lineNumber(),
                                error.columnNumber()
                        );
                        setTooltip(new Tooltip(tooltipText));
                    }
                }
            });
        }
    }

    /**
     * Updates the Schematron errors list and visibility
     */
    private void updateSchematronErrorsList(List<SchematronService.SchematronValidationError> errors) {
        if (schematronErrorsListView == null || schematronErrorsPane == null || schematronErrorsCountLabel == null) {
            return;
        }

        // Clear existing items
        schematronErrorsListView.getItems().clear();

        boolean hasErrors = errors != null && !errors.isEmpty();

        if (hasErrors) {
            // Add errors to the list
            schematronErrorsListView.getItems().addAll(errors);

            // Update count label
            schematronErrorsCountLabel.setText(String.format("Schematron Errors (%d)", errors.size()));

            // Show the errors pane
            schematronErrorsPane.setVisible(true);
            schematronErrorsPane.setManaged(true);
        } else {
            // Hide the errors pane when no errors
            schematronErrorsPane.setVisible(false);
            schematronErrorsPane.setManaged(false);
        }
    }

    /**
     * Navigates to the specified Schematron error location in the code editor
     */
    private void navigateToSchematronError(SchematronService.SchematronValidationError error) {
        if (xmlEditor == null) {
            logger.warn("Cannot navigate to error - xmlEditor is null");
            return;
        }

        try {
            logger.info("Navigating to Schematron error at line {} column {}: {}",
                    error.lineNumber(), error.columnNumber(), error.message());

            // Navigate to the error location using line number
            if (error.lineNumber() > 0) {
                xmlEditor.navigateToLine(error.lineNumber());
            }

            // Try to also navigate in the graphic view if possible
            if (error.context() != null && !error.context().trim().isEmpty()) {
                xmlEditor.navigateToXPath(error.context());
            }

        } catch (Exception e) {
            logger.error("Error navigating to Schematron error location", e);
        }
    }

    /**
     * Initializes the example values list view with a double-click handler
     * to insert selected values into the code editor at the cursor position.
     */
    private void initializeExampleValuesList() {
        if (exampleValuesListView != null) {
            exampleValuesListView.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) { // Double click
                    String selectedValue = exampleValuesListView.getSelectionModel().getSelectedItem();
                    if (selectedValue != null && !selectedValue.isEmpty() &&
                        !"No example values available".equals(selectedValue)) {
                        insertExampleValueIntoEditor(selectedValue);
                    }
                }
            });

            // Add visual feedback to indicate the list is double-clickable
            exampleValuesListView.setStyle("-fx-cursor: hand;");
            exampleValuesListView.setTooltip(new Tooltip("Double-click to insert example value at cursor position"));
        }
    }

    /**
     * Inserts an example value into the XML editor at the current cursor position.
     *
     * @param value The example value to insert
     */
    private void insertExampleValueIntoEditor(String value) {
        if (xmlEditor == null) {
            logger.warn("Cannot insert example value - xmlEditor is null");
            return;
        }

        try {
            // Insert text at cursor using wrapper method (works with both V1 and V2)
            xmlEditor.insertTextAtCursor(value);
            logger.info("Inserted example value '{}' into editor at cursor position", value);
        } catch (Exception e) {
            logger.error("Error inserting example value into editor", e);
        }
    }

    /**
     * Initializes the child elements list view with a double-click handler
     * to insert selected elements as complete XML nodes into the code editor.
     */
    private void initializeChildElementsList() {
        if (childElementsListView != null) {
            childElementsListView.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) { // Double click
                    String selectedElement = childElementsListView.getSelectionModel().getSelectedItem();
                    if (selectedElement != null && !selectedElement.isEmpty() &&
                        !selectedElement.startsWith("No ") && !selectedElement.startsWith("Error:")) {
                        insertChildElementIntoEditor(selectedElement);
                    }
                }
            });

            // Add visual feedback to indicate the list is double-clickable
            childElementsListView.setStyle("-fx-cursor: hand;");
            childElementsListView.setTooltip(new Tooltip("Double-click to insert element with opening and closing tags"));
        }
    }

    /**
     * Inserts a child element into the XML editor at the current cursor position.
     * The element is inserted as a complete XML node with opening and closing tags.
     *
     * @param elementDisplayText The element text from the list (may include type and mandatory info)
     */
    private void insertChildElementIntoEditor(String elementDisplayText) {
        if (xmlEditor == null) {
            logger.warn("Cannot insert child element - xmlEditor is null");
            return;
        }

        try {
            // Extract the element name from the display text
            // Format can be: "ElementName", "ElementName (type)", "ElementName [mandatory]", or "ElementName (type) [mandatory]"
            String elementName = extractElementName(elementDisplayText);

            if (elementName != null && !elementName.isEmpty()) {
                // Build the complete XML node with opening and closing tags
                String xmlNode = "<" + elementName + "></" + elementName + ">";

                // Insert text at cursor using wrapper method (works with both V1 and V2)
                xmlEditor.insertTextAtCursor(xmlNode);

                // Move cursor between the tags for easy content insertion
                // The cursor should be positioned after the opening tag
                org.fxmisc.richtext.CodeArea codeArea = xmlEditor.codeArea;
                if (codeArea != null) {
                    codeArea.moveTo(codeArea.getCaretPosition() - elementName.length() - 3);
                }

                logger.info("Inserted child element '{}' as complete XML node at cursor position", elementName);
            }
        } catch (Exception e) {
            logger.error("Error inserting child element into editor", e);
        }
    }

    /**
     * Extracts the element name from the display text in the child elements list.
     * Handles formats like "ElementName", "ElementName (type)", "ElementName [mandatory]", etc.
     *
     * @param displayText The display text from the list
     * @return The extracted element name, or null if extraction fails
     */
    private String extractElementName(String displayText) {
        if (displayText == null || displayText.trim().isEmpty()) {
            return null;
        }

        String elementName = displayText.trim();

        // Find the first occurrence of space, opening parenthesis, or opening bracket
        // These indicate the start of type info or mandatory/optional indicator
        int endIndex = elementName.length();

        int spaceIndex = elementName.indexOf(' ');
        int parenIndex = elementName.indexOf('(');
        int bracketIndex = elementName.indexOf('[');

        // Find the minimum valid index (excluding -1)
        if (spaceIndex != -1 && spaceIndex < endIndex) {
            endIndex = spaceIndex;
        }
        if (parenIndex != -1 && parenIndex < endIndex) {
            endIndex = parenIndex;
        }
        if (bracketIndex != -1 && bracketIndex < endIndex) {
            endIndex = bracketIndex;
        }

        // Extract only the element name part
        elementName = elementName.substring(0, endIndex).trim();

        return elementName.isEmpty() ? null : elementName;
    }

    /**
     * Toggles XSD documentation comments in the XML editor.
     */
    @FXML
    private void toggleXsdDocumentationComments() {
        if (xmlEditor == null) {
            logger.warn("Cannot toggle XSD comments - xmlEditor is null");
            return;
        }

        if (xmlEditor.getXsdFile() == null) {
            xsdDocumentationToggleButton.setSelected(false);
            xsdCommentsStatusLabel.setText("No XSD linked");
            showAlert("No XSD Schema", "Please link an XSD schema first to use this feature.");
            return;
        }

        boolean isSelected = xsdDocumentationToggleButton.isSelected();
        
        if (isSelected && !xsdCommentsActive) {
            // Add XSD documentation comments
            addXsdDocumentationComments();
        } else if (!isSelected && xsdCommentsActive) {
            // Remove XSD documentation comments
            removeXsdDocumentationComments();
        }
    }

    /**
     * Adds XSD documentation as comments before each XML element.
     */
    private void addXsdDocumentationComments() {
        try {
            String xmlContent = xmlEditor.getEditorText();
            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                xsdCommentsStatusLabel.setText("No XML content");
                return;
            }

            String modifiedXml = insertXsdCommentsIntoXml(xmlContent);
            
            if (!modifiedXml.equals(xmlContent)) {
                xmlEditor.setEditorText(modifiedXml);
                xsdCommentsActive = true;
                xsdCommentsStatusLabel.setText("XSD comments active");
                logger.info("XSD documentation comments added to XML");
            } else {
                xsdCommentsStatusLabel.setText("No changes made");
            }
            
        } catch (Exception e) {
            logger.error("Error adding XSD documentation comments", e);
            xsdDocumentationToggleButton.setSelected(false);
            xsdCommentsStatusLabel.setText("Error adding comments");
            showAlert("Error", "Failed to add XSD documentation comments: " + e.getMessage());
        }
    }

    /**
     * Removes XSD documentation comments from the XML.
     */
    private void removeXsdDocumentationComments() {
        try {
            String xmlContent = xmlEditor.getEditorText();
            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                xsdCommentsStatusLabel.setText("No XML content");
                return;
            }

            String modifiedXml = removeXsdCommentsFromXml(xmlContent);
            
            if (!modifiedXml.equals(xmlContent)) {
                xmlEditor.setEditorText(modifiedXml);
                xsdCommentsActive = false;
                xsdCommentsStatusLabel.setText("XSD comments removed");
                logger.info("XSD documentation comments removed from XML");
            } else {
                xsdCommentsStatusLabel.setText("No XSD comments found");
            }
            
        } catch (Exception e) {
            logger.error("Error removing XSD documentation comments", e);
            xsdCommentsStatusLabel.setText("Error removing comments");
            showAlert("Error", "Failed to remove XSD documentation comments: " + e.getMessage());
        }
    }

    /**
     * Inserts XSD documentation comments before each XML element.
     */
    private String insertXsdCommentsIntoXml(String xmlContent) {
        if (xmlEditor.getXsdDocumentationData() == null) {
            return xmlContent;
        }

        StringBuilder result = new StringBuilder();
        String[] lines = xmlContent.split("\n");
        
        for (String line : lines) {
            // Check if this line contains an opening XML element
            if (line.trim().matches(".*<[a-zA-Z][a-zA-Z0-9_:]*[^/>]*>.*") && 
                !line.trim().startsWith("<?") && 
                !line.trim().startsWith("<!--")) {
                
                String elementName = extractElementNameFromLine(line);
                if (elementName != null) {
                    String documentation = getDocumentationForElement(elementName, line);
                    if (documentation != null && !documentation.trim().isEmpty()) {
                        // Add indentation matching the element line
                        String indent = getIndentation(line);
                        String comment = indent + "<!--" + XSD_COMMENT_MARKER + documentation.trim() + " -->";
                        result.append(comment).append("\n");
                    }
                }
            }
            result.append(line).append("\n");
        }
        
        return result.toString().trim();
    }

    /**
     * Removes XSD documentation comments from XML content.
     * Handles both single-line and multi-line comments.
     */
    private String removeXsdCommentsFromXml(String xmlContent) {
        // Use regex to find and remove complete XML comments that contain the XSD marker
        // This pattern matches: <!-- ... [XSD-DOC] ... --> including multi-line comments
        String pattern = "<!--[^>]*?" + Pattern.quote(XSD_COMMENT_MARKER) + ".*?-->";
        
        // Use DOTALL flag to make . match newlines as well
        Pattern commentPattern = Pattern.compile(pattern, Pattern.DOTALL);
        
        // Replace all XSD documentation comments with empty string
        String result = commentPattern.matcher(xmlContent).replaceAll("");
        
        // Clean up any extra blank lines that might be left behind
        return cleanupExtraBlankLines(result);
    }
    
    /**
     * Removes excessive blank lines while preserving intentional spacing.
     */
    private String cleanupExtraBlankLines(String content) {
        // Replace multiple consecutive newlines with at most two newlines
        return content.replaceAll("\n\\s*\n\\s*\n+", "\n\n").trim();
    }

    /**
     * Extracts element name from an XML line.
     */
    private String extractElementNameFromLine(String line) {
        try {
            String trimmed = line.trim();
            if (trimmed.startsWith("<")) {
                int start = trimmed.indexOf('<') + 1;
                int end = trimmed.indexOf(' ', start);
                if (end == -1) {
                    end = trimmed.indexOf('>', start);
                }
                if (end > start) {
                    return trimmed.substring(start, end);
                }
            }
        } catch (Exception e) {
            logger.debug("Error extracting element name from line: {}", line);
        }
        return null;
    }

    /**
     * Gets documentation for a specific element from XSD data.
     */
    private String getDocumentationForElement(String elementName, String line) {
        try {
            // Build XPath context for this element
            String xpath = buildXPathForElement(elementName, line);
            
            var xsdData = xmlEditor.getXsdDocumentationData();
            if (xsdData != null) {
                var elementInfo = xsdData.getExtendedXsdElementMap().get(xpath);
                if (elementInfo == null) {
                    elementInfo = xmlEditor.findBestMatchingElement(xpath);
                }
                
                if (elementInfo != null) {
                    return xmlEditor.getDocumentationFromExtendedElement(elementInfo);
                }
            }
        } catch (Exception e) {
            logger.debug("Error getting documentation for element: {}", elementName);
        }
        return null;
    }

    /**
     * Builds XPath for an element (simplified version).
     */
    private String buildXPathForElement(String elementName, String line) {
        // Simplified XPath building - in a real implementation, 
        // you would need to track the full element hierarchy
        return "/" + elementName;
    }

    /**
     * Gets the indentation (leading whitespace) from a line.
     */
    private String getIndentation(String line) {
        StringBuilder indent = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == ' ' || c == '\t') {
                indent.append(c);
            } else {
                break;
            }
        }
        return indent.toString();
    }

    /**
     * Shows an alert dialog.
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
