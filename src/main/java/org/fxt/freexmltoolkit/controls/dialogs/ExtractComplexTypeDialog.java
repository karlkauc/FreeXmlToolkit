package org.fxt.freexmltoolkit.controls.dialogs;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.regex.Pattern;

/**
 * Dialog for extracting inline complexType definitions to global complexTypes.
 * Provides user interface for specifying the new type name and validating input.
 */
public class ExtractComplexTypeDialog extends Dialog<ExtractComplexTypeDialog.ExtractComplexTypeResult> {

    private final TextField typeNameField;
    private final TextArea previewArea;
    private final Label validationLabel;
    private final String elementName;

    // XSD Name pattern (NCName)
    private static final Pattern XSD_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.-]*$");

    public ExtractComplexTypeDialog(String elementName) {
        this.elementName = elementName;

        setTitle("Extract ComplexType");
        setHeaderText("Extract inline complexType to global type definition");

        // Set dialog icon
        setGraphic(new FontIcon("bi-box-arrow-up"));

        // Create UI components
        typeNameField = new TextField();
        previewArea = new TextArea();
        validationLabel = new Label();

        initializeUI();
        setupValidation();

        // Set result converter
        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return new ExtractComplexTypeResult(typeNameField.getText().trim());
            }
            return null;
        });

        // Focus on name field when dialog opens
        Platform.runLater(() -> typeNameField.requestFocus());
    }

    private void initializeUI() {
        // Create main layout
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        // Create form grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new Insets(10));

        // Element info
        Label currentElementLabel = new Label("Current Element:");
        currentElementLabel.getStyleClass().add("form-label");
        Label currentElementValue = new Label(elementName);
        currentElementValue.getStyleClass().addAll("form-value", "element-name");

        // Type name input
        Label typeNameLabel = new Label("New Type Name:");
        typeNameLabel.getStyleClass().add("form-label");
        typeNameField.setPromptText("Enter global complexType name");
        typeNameField.getStyleClass().add("form-field");

        // Generate suggested name
        String suggestedName = generateSuggestedTypeName(elementName);
        typeNameField.setText(suggestedName);
        typeNameField.selectAll();

        // Validation label
        validationLabel.getStyleClass().addAll("validation-message", "error");
        validationLabel.setVisible(false);

        // Preview area
        Label previewLabel = new Label("Preview:");
        previewLabel.getStyleClass().add("form-label");
        previewArea.getStyleClass().add("preview-area");
        previewArea.setEditable(false);
        previewArea.setPrefRowCount(8);
        previewArea.setWrapText(true);
        updatePreview();

        // Add components to grid
        grid.add(currentElementLabel, 0, 0);
        grid.add(currentElementValue, 1, 0);
        grid.add(typeNameLabel, 0, 1);
        grid.add(typeNameField, 1, 1);
        grid.add(validationLabel, 1, 2);
        grid.add(previewLabel, 0, 3);
        grid.add(previewArea, 0, 4, 2, 1);

        content.getChildren().add(grid);

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Initially disable OK button until valid input
        getDialogPane().lookupButton(ButtonType.OK).setDisable(true);

        // Apply CSS styling
        getDialogPane().getStylesheets().add(
                getClass().getResource("/css/extract-complextype-dialog.css").toExternalForm());
        getDialogPane().getStyleClass().add("extract-complextype-dialog");
    }

    private void setupValidation() {
        // Add listener for real-time validation
        typeNameField.textProperty().addListener((observable, oldValue, newValue) -> {
            validateInput();
            updatePreview();
        });

        // Initial validation
        validateInput();
    }

    private void validateInput() {
        String typeName = typeNameField.getText().trim();
        boolean isValid = true;
        StringBuilder errorMessage = new StringBuilder();

        if (typeName.isEmpty()) {
            isValid = false;
            errorMessage.append("Type name is required");
        } else if (!XSD_NAME_PATTERN.matcher(typeName).matches()) {
            isValid = false;
            errorMessage.append("Invalid type name. Must be a valid XSD NCName");
        } else if (typeName.length() > 100) {
            isValid = false;
            errorMessage.append("Type name too long (max 100 characters)");
        }

        // Update validation UI
        if (isValid) {
            validationLabel.setVisible(false);
            typeNameField.getStyleClass().remove("error");
        } else {
            validationLabel.setText(errorMessage.toString());
            validationLabel.setVisible(true);
            typeNameField.getStyleClass().add("error");
        }

        // Enable/disable OK button
        getDialogPane().lookupButton(ButtonType.OK).setDisable(!isValid);
    }

    private void updatePreview() {
        String typeName = typeNameField.getText().trim();
        if (typeName.isEmpty()) {
            previewArea.setText("Enter a type name to see preview...");
            return;
        }

        String preview = "Refactoring Preview:\n\n" +
                "BEFORE (Inline ComplexType):\n" +
                "<xs:element name=\"" + elementName + "\">\n" +
                "  <xs:complexType>\n" +
                "    <!-- inline type definition -->\n" +
                "  </xs:complexType>\n" +
                "</xs:element>\n\n" +
                "AFTER (Global ComplexType Reference):\n" +
                "<!-- Global type definition -->\n" +
                "<xs:complexType name=\"" + typeName + "\">\n" +
                "  <!-- extracted type definition -->\n" +
                "</xs:complexType>\n\n" +
                "<!-- Element with type reference -->\n" +
                "<xs:element name=\"" + elementName +
                "\" type=\"" + typeName + "\"/>\n\n" +
                "Benefits:\n" +
                "✓ Type can be reused by other elements\n" +
                "✓ Better schema organization\n" +
                "✓ Improved maintainability\n";

        previewArea.setText(preview);
    }

    private String generateSuggestedTypeName(String elementName) {
        if (elementName == null || elementName.isEmpty()) {
            return "NewComplexType";
        }

        // Capitalize first letter and add "Type" suffix
        String suggested = elementName.substring(0, 1).toUpperCase() +
                elementName.substring(1) + "Type";

        // Ensure it's a valid XSD name
        if (XSD_NAME_PATTERN.matcher(suggested).matches()) {
            return suggested;
        } else {
            return "NewComplexType";
        }
    }

    /**
         * Result class for the dialog
         */
        public record ExtractComplexTypeResult(String typeName) {
    }
}