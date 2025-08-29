package org.fxt.freexmltoolkit.controls;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * Safe Rename Dialog for XSD elements with preview of affected elements
 * <p>
 * Features:
 * - Real-time validation of new name
 * - Preview of all affected references
 * - Conflict detection
 * - Undo/Redo support via Command Pattern
 */
public class XsdSafeRenameDialog extends Dialog<String> {

    private static final Logger logger = LogManager.getLogger(XsdSafeRenameDialog.class);

    private final XsdNodeInfo targetNode;
    private final XsdDomManipulator domManipulator;

    // UI Components
    private TextField newNameField;
    private Label validationLabel;
    private TextArea previewArea;
    private CheckBox updateReferencesCheckBox;
    private Label affectedCountLabel;

    // Data
    private List<ReferenceInfo> affectedReferences;

    public XsdSafeRenameDialog(XsdNodeInfo targetNode, XsdDomManipulator domManipulator) {
        this.targetNode = targetNode;
        this.domManipulator = domManipulator;

        initializeDialog();
        createContent();
        findAffectedReferences();
        setupValidation();

        logger.info("XsdSafeRenameDialog initialized for node: {}", targetNode.name());
    }

    private void initializeDialog() {
        setTitle("Safe Rename - " + targetNode.name());
        setHeaderText("Rename '" + targetNode.name() + "' and update all references");

        // Set dialog properties
        setResizable(true);
        initModality(Modality.APPLICATION_MODAL);
        getDialogPane().setPrefSize(800, 600);

        // Add custom CSS
        getDialogPane().getStylesheets().add(
                getClass().getResource("/css/xsd-refactoring-tools.css").toExternalForm()
        );

        // Configure result converter
        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return newNameField.getText().trim();
            }
            return null;
        });

        // Add buttons
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Initially disable OK button
        getDialogPane().lookupButton(ButtonType.OK).setDisable(true);
    }

    private void createContent() {
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(20));

        // Current name info
        VBox currentSection = createCurrentNameSection();

        // New name input
        VBox renameSection = createRenameSection();

        // Preview section
        VBox previewSection = createPreviewSection();

        // Options section
        VBox optionsSection = createOptionsSection();

        mainContent.getChildren().addAll(
                currentSection,
                new Separator(),
                renameSection,
                new Separator(),
                previewSection,
                optionsSection
        );

        getDialogPane().setContent(new ScrollPane(mainContent));
    }

    private VBox createCurrentNameSection() {
        VBox section = new VBox(10);

        Label titleLabel = new Label("Current Element");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        titleLabel.setGraphic(new FontIcon("bi-info-circle"));

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(15);
        infoGrid.setVgap(8);

        infoGrid.add(new Label("Name:"), 0, 0);
        infoGrid.add(new Label(targetNode.name()), 1, 0);

        infoGrid.add(new Label("Type:"), 0, 1);
        infoGrid.add(new Label(targetNode.nodeType().toString()), 1, 1);

        if (targetNode.type() != null) {
            infoGrid.add(new Label("Data Type:"), 0, 2);
            infoGrid.add(new Label(targetNode.type()), 1, 2);
        }

        infoGrid.add(new Label("XPath:"), 0, 3);
        Label xpathLabel = new Label(targetNode.xpath());
        xpathLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        infoGrid.add(xpathLabel, 1, 3);

        section.getChildren().addAll(titleLabel, infoGrid);
        return section;
    }

    private VBox createRenameSection() {
        VBox section = new VBox(10);

        Label titleLabel = new Label("New Name");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        titleLabel.setGraphic(new FontIcon("bi-pencil"));

        GridPane inputGrid = new GridPane();
        inputGrid.setHgap(15);
        inputGrid.setVgap(10);

        Label nameLabel = new Label("New Name:");
        newNameField = new TextField(targetNode.name());
        newNameField.setPrefWidth(300);
        newNameField.setPromptText("Enter new name...");

        validationLabel = new Label();
        validationLabel.setStyle("-fx-font-weight: bold;");

        inputGrid.add(nameLabel, 0, 0);
        inputGrid.add(newNameField, 1, 0);
        inputGrid.add(validationLabel, 1, 1);

        section.getChildren().addAll(titleLabel, inputGrid);
        return section;
    }

    private VBox createPreviewSection() {
        VBox section = new VBox(10);

        Label titleLabel = new Label("Affected References");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        titleLabel.setGraphic(new FontIcon("bi-search"));

        affectedCountLabel = new Label();
        affectedCountLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #6c757d;");

        previewArea = new TextArea();
        previewArea.setEditable(false);
        previewArea.setPrefRowCount(12);
        previewArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        previewArea.setPromptText("No references found or analysis not yet performed...");

        section.getChildren().addAll(titleLabel, affectedCountLabel, previewArea);
        return section;
    }

    private VBox createOptionsSection() {
        VBox section = new VBox(10);

        Label titleLabel = new Label("Options");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        titleLabel.setGraphic(new FontIcon("bi-gear"));

        updateReferencesCheckBox = new CheckBox("Update all references automatically");
        updateReferencesCheckBox.setSelected(true);
        updateReferencesCheckBox.setStyle("-fx-font-size: 13px;");

        Label warningLabel = new Label("Warning: This operation cannot be undone easily. Make sure to backup your XSD file first.");
        warningLabel.setStyle("-fx-text-fill: #dc3545; -fx-font-style: italic; -fx-font-size: 12px;");
        warningLabel.setGraphic(new FontIcon("bi-exclamation-triangle"));

        section.getChildren().addAll(titleLabel, updateReferencesCheckBox, warningLabel);
        return section;
    }

    private void findAffectedReferences() {
        affectedReferences = new ArrayList<>();

        try {
            // Find all references to this element
            String elementName = targetNode.name();

            // Find type references
            findTypeReferences(elementName);

            // Find ref attributes
            findRefReferences(elementName);

            // Find base type references  
            findBaseTypeReferences(elementName);

            updatePreview();

        } catch (Exception e) {
            logger.error("Error finding affected references for: " + targetNode.name(), e);
            previewArea.setText("Error analyzing references: " + e.getMessage());
        }
    }

    private void findTypeReferences(String elementName) {
        NodeList elements = domManipulator.getDocument().getElementsByTagName("*");

        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);

            // Check type attribute
            String typeAttr = element.getAttribute("type");
            if (elementName.equals(typeAttr) || elementName.equals(removeNamespacePrefix(typeAttr))) {
                affectedReferences.add(new ReferenceInfo(
                        ReferenceType.TYPE_REFERENCE,
                        element.getTagName() + " '" + element.getAttribute("name") + "'",
                        getElementPath(element),
                        "type=\"" + typeAttr + "\""
                ));
            }
        }
    }

    private void findRefReferences(String elementName) {
        NodeList elements = domManipulator.getDocument().getElementsByTagName("*");

        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);

            // Check ref attribute
            String refAttr = element.getAttribute("ref");
            if (elementName.equals(refAttr) || elementName.equals(removeNamespacePrefix(refAttr))) {
                affectedReferences.add(new ReferenceInfo(
                        ReferenceType.REF_REFERENCE,
                        element.getTagName(),
                        getElementPath(element),
                        "ref=\"" + refAttr + "\""
                ));
            }
        }
    }

    private void findBaseTypeReferences(String elementName) {
        NodeList elements = domManipulator.getDocument().getElementsByTagName("*");

        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);

            // Check base attribute in restrictions and extensions
            String baseAttr = element.getAttribute("base");
            if (elementName.equals(baseAttr) || elementName.equals(removeNamespacePrefix(baseAttr))) {
                affectedReferences.add(new ReferenceInfo(
                        ReferenceType.BASE_REFERENCE,
                        element.getTagName(),
                        getElementPath(element),
                        "base=\"" + baseAttr + "\""
                ));
            }
        }
    }

    private void updatePreview() {
        if (affectedReferences.isEmpty()) {
            affectedCountLabel.setText("No references found - this element can be renamed safely.");
            previewArea.setText("This element is not referenced by other parts of the schema.\n" +
                    "Renaming will only affect this element definition.");
        } else {
            affectedCountLabel.setText("Found " + affectedReferences.size() + " reference(s) that will be updated:");

            StringBuilder preview = new StringBuilder();
            for (ReferenceInfo ref : affectedReferences) {
                preview.append(String.format("• %s at %s\n", ref.description, ref.location));
                preview.append(String.format("  %s\n", ref.attributeInfo));
                preview.append(String.format("  Type: %s\n\n", ref.type.getDisplayName()));
            }

            previewArea.setText(preview.toString());
        }
    }

    private void setupValidation() {
        newNameField.textProperty().addListener((obs, oldVal, newVal) -> validateNewName(newVal));

        // Initial validation
        validateNewName(newNameField.getText());
    }

    private void validateNewName(String newName) {
        Button okButton = (Button) getDialogPane().lookupButton(ButtonType.OK);

        if (newName == null || newName.trim().isEmpty()) {
            validationLabel.setText("✗ Name cannot be empty");
            validationLabel.setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
            okButton.setDisable(true);
            return;
        }

        // Check if name is unchanged
        if (newName.equals(targetNode.name())) {
            validationLabel.setText("ⓘ No changes will be made");
            validationLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-weight: normal;");
            okButton.setDisable(true);
            return;
        }

        // Check XML name validity
        if (!isValidXmlName(newName)) {
            validationLabel.setText("✗ Invalid XML name");
            validationLabel.setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
            okButton.setDisable(true);
            return;
        }

        // Check for conflicts
        if (hasNameConflict(newName)) {
            validationLabel.setText("⚠ Name already exists in schema");
            validationLabel.setStyle("-fx-text-fill: #ff9800; -fx-font-weight: bold;");
            okButton.setDisable(false); // Allow but warn
            return;
        }

        // Valid name
        validationLabel.setText("✓ Valid name - " + affectedReferences.size() + " reference(s) will be updated");
        validationLabel.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
        okButton.setDisable(false);
    }

    private boolean isValidXmlName(String name) {
        if (name == null || name.isEmpty()) return false;

        // Basic XML name validation
        if (!Character.isLetter(name.charAt(0)) && name.charAt(0) != '_') {
            return false;
        }

        for (char c : name.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '_' && c != '.') {
                return false;
            }
        }

        return true;
    }

    private boolean hasNameConflict(String newName) {
        // Check if an element with this name already exists
        NodeList elements = domManipulator.getDocument().getElementsByTagName("xs:element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (newName.equals(element.getAttribute("name")) &&
                    !element.equals(domManipulator.findElementByXPath(targetNode.xpath()))) {
                return true;
            }
        }

        // Check complexType and simpleType names
        String[] typeElements = {"xs:complexType", "xs:simpleType"};
        for (String tagName : typeElements) {
            NodeList types = domManipulator.getDocument().getElementsByTagName(tagName);
            for (int i = 0; i < types.getLength(); i++) {
                Element type = (Element) types.item(i);
                if (newName.equals(type.getAttribute("name"))) {
                    return true;
                }
            }
        }

        return false;
    }

    private String removeNamespacePrefix(String name) {
        if (name != null && name.contains(":")) {
            return name.substring(name.lastIndexOf(':') + 1);
        }
        return name;
    }

    private String getElementPath(Element element) {
        StringBuilder path = new StringBuilder();
        Element current = element;

        while (current != null && !current.getTagName().equals("xs:schema")) {
            String name = current.getAttribute("name");
            if (name.isEmpty()) {
                name = current.getTagName();
            }

            if (path.length() > 0) {
                path.insert(0, "/");
            }
            path.insert(0, name);

            current = (Element) current.getParentNode();
        }

        return "/" + path;
    }

    public boolean shouldUpdateReferences() {
        return updateReferencesCheckBox.isSelected();
    }

    public List<ReferenceInfo> getAffectedReferences() {
        return new ArrayList<>(affectedReferences);
    }

    // Supporting classes
        public record ReferenceInfo(ReferenceType type, String description, String location, String attributeInfo) {
    }

    public enum ReferenceType {
        TYPE_REFERENCE("Type Reference"),
        REF_REFERENCE("Element Reference"),
        BASE_REFERENCE("Base Type Reference");

        private final String displayName;

        ReferenceType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}