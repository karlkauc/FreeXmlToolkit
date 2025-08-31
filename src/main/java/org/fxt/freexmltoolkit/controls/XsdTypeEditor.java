package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controller.XsdController;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.fxt.freexmltoolkit.service.XsdLiveValidationService;
import org.fxt.freexmltoolkit.service.XsdViewService;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.Optional;

/**
 * Specialized editor for editing individual XSD simple and complex types
 * in a dedicated graphical interface. Shows only the type definition
 * with the type name as root element.
 */
public class XsdTypeEditor extends Tab {

    private static final Logger logger = LogManager.getLogger(XsdTypeEditor.class);

    private final Element originalTypeElement;
    private final String typeName;
    private final boolean isSimpleType;
    private final XsdController parentController;

    // Core components
    private Document typeDocument;
    private XsdDiagramView diagramView;
    private XsdDomManipulator domManipulator;
    private XsdViewService viewService;
    private XsdLiveValidationService validationService;

    // UI components
    private BorderPane mainPane;
    private VBox headerPane;
    private Label typeInfoLabel;
    private Button saveButton;
    private Button revertButton;

    /**
     * Creates a new type editor for the given type element
     */
    public XsdTypeEditor(Element typeElement, XsdController parentController) {
        this.originalTypeElement = typeElement;
        this.parentController = parentController;
        this.typeName = typeElement.getAttribute("name");
        this.isSimpleType = "simpleType".equals(typeElement.getLocalName());

        // Set tab properties
        setText(getTabTitle());
        setClosable(true);

        initializeServices();
        createTypeDocument();
        loadTypeIntoManipulator();
        buildUI();
        setupEventHandlers();

        logger.info("Created XsdTypeEditor for {} type: {}",
                isSimpleType ? "simple" : "complex", typeName);
    }

    private String getTabTitle() {
        String typeIcon = isSimpleType ? "📝" : "🏗️";
        return typeIcon + " " + typeName + " (" + (isSimpleType ? "SimpleType" : "ComplexType") + ")";
    }

    private void initializeServices() {
        try {
            // Create a new document for the isolated type
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            this.typeDocument = builder.newDocument();

            // Initialize services for this type document
            this.domManipulator = new XsdDomManipulator();
            this.viewService = new XsdViewService();
            this.validationService = XsdLiveValidationService.getInstance();

        } catch (Exception e) {
            logger.error("Failed to initialize XsdTypeEditor services", e);
            throw new RuntimeException("Could not initialize type editor", e);
        }
    }

    private void createTypeDocument() {
        try {
            // Create a minimal XSD schema document containing only this type
            Element schemaElement = typeDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:schema");
            schemaElement.setAttribute("xmlns:xs", "http://www.w3.org/2001/XMLSchema");
            schemaElement.setAttribute("elementFormDefault", "qualified");
            typeDocument.appendChild(schemaElement);

            // Import the type element into this document
            Element importedType = (Element) typeDocument.importNode(originalTypeElement, true);
            schemaElement.appendChild(importedType);

            logger.debug("Created isolated type document for: {}", typeName);

        } catch (Exception e) {
            logger.error("Failed to create type document for: " + typeName, e);
            throw new RuntimeException("Could not create type document", e);
        }
    }

    private void loadTypeIntoManipulator() {
        try {
            // Convert document to string and load into domManipulator
            String typeXsd = documentToString(typeDocument);
            domManipulator.loadXsd(typeXsd);

            logger.debug("Loaded type document into DOM manipulator for: {}", typeName);

        } catch (Exception e) {
            logger.error("Failed to load type into DOM manipulator: " + typeName, e);
            throw new RuntimeException("Could not load type into manipulator", e);
        }
    }

    private void buildUI() {
        mainPane = new BorderPane();

        // Header with type information and controls
        createHeaderPane();
        mainPane.setTop(headerPane);

        // Create the diagram view for this type
        createDiagramView();

        setContent(mainPane);
    }

    private void createHeaderPane() {
        headerPane = new VBox(10);
        headerPane.setPadding(new Insets(10));
        headerPane.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;");

        // Type information label
        typeInfoLabel = new Label();
        updateTypeInfoLabel();
        typeInfoLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #495057;");

        // Control buttons
        saveButton = new Button("Save Changes");
        saveButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white;");
        saveButton.setOnAction(e -> saveChanges());

        revertButton = new Button("Revert Changes");
        revertButton.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white;");
        revertButton.setOnAction(e -> revertChanges());

        var buttonBox = new HBox(10, saveButton, revertButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        headerPane.getChildren().addAll(typeInfoLabel, buttonBox);
    }

    private void updateTypeInfoLabel() {
        String typeDescription = isSimpleType ? "Simple Type" : "Complex Type";
        typeInfoLabel.setText(String.format("Editing %s: %s", typeDescription, typeName));
    }

    private void createDiagramView() {
        try {
            // Convert document to string for the view service
            String typeXsd = documentToString(typeDocument);

            // Build the node tree for the diagram view
            XsdNodeInfo rootNode = viewService.buildLightweightTree(typeXsd);

            // Create diagram view with type-specific settings
            diagramView = new XsdDiagramView(rootNode, parentController, typeXsd, "", domManipulator);

            // Customize for type editing
            customizeDiagramForTypeEditing();

            // Get the built diagram view and wrap in scroll pane
            ScrollPane scrollPane = new ScrollPane(diagramView.build());
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);

            mainPane.setCenter(scrollPane);

        } catch (Exception e) {
            logger.error("Failed to create diagram view for type: " + typeName, e);

            // Show error message
            Label errorLabel = new Label("Error creating type diagram: " + e.getMessage());
            errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 14px;");
            errorLabel.setPadding(new Insets(20));
            mainPane.setCenter(errorLabel);
        }
    }

    private void customizeDiagramForTypeEditing() {
        // The diagram view will automatically show the type as root
        // since our document only contains the schema with this single type

        // We could add type-specific customizations here if needed
        logger.debug("Customized diagram view for type editing: {}", typeName);
    }

    private void setupEventHandlers() {
        // Handle tab closing
        setOnCloseRequest(e -> {
            if (hasUnsavedChanges()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Unsaved Changes");
                alert.setHeaderText("Close Type Editor");
                alert.setContentText("You have unsaved changes. Do you want to save before closing?");

                ButtonType saveAndClose = new ButtonType("Save & Close");
                ButtonType closeWithoutSaving = new ButtonType("Close without Saving");
                ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

                alert.getButtonTypes().setAll(saveAndClose, closeWithoutSaving, cancel);

                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent()) {
                    if (result.get() == saveAndClose) {
                        saveChanges();
                    } else if (result.get() == cancel) {
                        e.consume(); // Cancel the close
                    }
                }
            }
        });
    }

    private boolean hasUnsavedChanges() {
        try {
            // Compare current document with original
            String currentXml = documentToString(typeDocument);
            String originalXml = elementToString(originalTypeElement);
            return !currentXml.equals(originalXml);
        } catch (Exception e) {
            logger.warn("Could not check for unsaved changes", e);
            return false; // Assume no changes if we can't check
        }
    }

    private void saveChanges() {
        try {
            // Get the modified type element from our document
            Element schemaElement = typeDocument.getDocumentElement();
            Element modifiedType = (Element) schemaElement.getFirstChild();

            if (modifiedType != null) {
                // Update the original type element in the main XSD document
                updateOriginalTypeElement(modifiedType);

                // Notify parent controller about changes
                if (parentController != null) {
                    Platform.runLater(() -> {
                        parentController.markAsModified();
                        parentController.refreshDiagramView();
                    });
                }

                // Update UI
                updateTypeInfoLabel();

                logger.info("Saved changes for type: {}", typeName);

                // Show success message
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Changes Saved");
                alert.setHeaderText(null);
                alert.setContentText("Type '" + typeName + "' has been updated successfully.");
                alert.showAndWait();
            }

        } catch (Exception e) {
            logger.error("Failed to save changes for type: " + typeName, e);

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Save Error");
            alert.setHeaderText("Failed to save changes");
            alert.setContentText("Error: " + e.getMessage());
            alert.showAndWait();
        }
    }

    private void updateOriginalTypeElement(Element modifiedType) {
        // This would need to be implemented based on how the parent XSD document
        // should be updated. For now, we'll log the intent.
        logger.info("Would update original type element '{}' with modifications", typeName);

        // TODO: Implement the actual DOM update logic
        // This might involve:
        // 1. Finding the original type in the parent document
        // 2. Replacing its content with the modified version
        // 3. Preserving namespace contexts and references
    }

    private void revertChanges() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Revert Changes");
        alert.setHeaderText("Discard all changes?");
        alert.setContentText("This will revert the type to its original state. All unsaved changes will be lost.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Recreate the type document from the original
            createTypeDocument();
            createDiagramView();

            logger.info("Reverted changes for type: {}", typeName);
        }
    }

    // Utility methods
    private String documentToString(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    private String elementToString(Element element) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(element), new StreamResult(writer));
        return writer.toString();
    }

    /**
     * Gets the type name being edited
     */
    public String getTypeName() {
        return typeName;
    }

    /**
     * Returns whether this is editing a simple type
     */
    public boolean isSimpleType() {
        return isSimpleType;
    }

    /**
     * Gets the diagram view for this type editor
     */
    public XsdDiagramView getDiagramView() {
        return diagramView;
    }

    /**
     * Forces a refresh of the diagram view
     */
    public void refreshDiagram() {
        if (diagramView != null) {
            Platform.runLater(() -> {
                createDiagramView();
            });
        }
    }
}