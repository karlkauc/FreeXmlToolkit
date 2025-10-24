package org.fxt.freexmltoolkit.controls.dialogs;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.service.XPath2Service;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Dialog for creating and editing XSD 1.1 type alternatives (xs:alternative).
 * Provides XPath 2.0 expression validation for conditional type assignment and preview.
 */
public class TypeAlternativeEditorDialog extends Dialog<TypeAlternativeEditorDialog.TypeAlternativeResult> {

    private final TextArea testExpressionArea;
    private final TextField typeNameField;
    private final TextField xpathDefaultNamespaceField;
    private final TextArea documentationArea;
    private final Label validationLabel;
    private final TextArea previewArea;
    private final XPath2Service xpathService;
    private final String elementContext;
    private final CheckBox defaultAlternativeCheckBox;

    public TypeAlternativeEditorDialog(String elementContext) {
        this(elementContext, null, null, null, null);
    }

    public TypeAlternativeEditorDialog(String elementContext, String existingTest,
                                       String existingType, String existingNamespace,
                                       String existingDocumentation) {
        this.elementContext = elementContext;
        this.xpathService = new XPath2Service();

        setTitle("XSD 1.1 Type Alternative Editor");
        setHeaderText("Create or edit xs:alternative with conditional type assignment");

        // Set dialog icon
        setGraphic(new FontIcon("bi-diagram-3"));

        // Create UI components
        testExpressionArea = new TextArea();
        typeNameField = new TextField();
        xpathDefaultNamespaceField = new TextField();
        documentationArea = new TextArea();
        validationLabel = new Label();
        previewArea = new TextArea();
        defaultAlternativeCheckBox = new CheckBox("Default Alternative (no test condition)");

        // Set existing values if editing
        if (existingTest != null && !existingTest.isEmpty()) {
            testExpressionArea.setText(existingTest);
            defaultAlternativeCheckBox.setSelected(false);
        } else {
            defaultAlternativeCheckBox.setSelected(true);
            testExpressionArea.setDisable(true);
        }
        if (existingType != null) {
            typeNameField.setText(existingType);
        }
        if (existingNamespace != null) {
            xpathDefaultNamespaceField.setText(existingNamespace);
        }
        if (existingDocumentation != null) {
            documentationArea.setText(existingDocumentation);
        }

        initializeUI();
        setupValidation();

        // Set result converter
        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                String test = defaultAlternativeCheckBox.isSelected()
                        ? null
                        : testExpressionArea.getText().trim();
                return new TypeAlternativeResult(
                        test,
                        typeNameField.getText().trim(),
                        xpathDefaultNamespaceField.getText().trim(),
                        documentationArea.getText().trim()
                );
            }
            return null;
        });

        // Focus on type name field when dialog opens
        Platform.runLater(() -> typeNameField.requestFocus());
    }

    private void initializeUI() {
        // Create main layout
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setPrefWidth(700);

        // Create form grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new Insets(10));

        // Element context info
        Label contextLabel = new Label("Element:");
        contextLabel.setStyle("-fx-font-weight: bold;");
        Label contextValue = new Label(elementContext != null ? elementContext : "element");
        contextValue.setStyle("-fx-text-fill: #8e24aa; -fx-font-weight: bold;");

        // Type name (required)
        Label typeLabel = new Label("Type Name: *");
        typeLabel.setStyle("-fx-font-weight: bold;");
        typeNameField.setPromptText("e.g., xs:string, customType");
        typeNameField.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");

        // Default alternative checkbox
        defaultAlternativeCheckBox.setStyle("-fx-font-weight: bold;");
        defaultAlternativeCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            testExpressionArea.setDisable(newVal);
            if (newVal) {
                testExpressionArea.setText("");
            }
            validateInput();
            updatePreview();
        });

        // Test expression (optional if default)
        Label testLabel = new Label("XPath 2.0 Test Expression:");
        testLabel.setStyle("-fx-font-weight: bold;");
        testExpressionArea.setPromptText("Enter XPath 2.0 condition (e.g., @type='premium')");
        testExpressionArea.setPrefRowCount(3);
        testExpressionArea.setWrapText(true);
        testExpressionArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");

        // XPath default namespace (optional)
        Label namespaceLabel = new Label("XPath Default Namespace:");
        namespaceLabel.setStyle("-fx-font-weight: bold;");
        xpathDefaultNamespaceField.setPromptText("http://example.com/namespace (optional)");

        // Documentation (optional)
        Label docLabel = new Label("Documentation:");
        docLabel.setStyle("-fx-font-weight: bold;");
        documentationArea.setPromptText("Describe when this type alternative is used...");
        documentationArea.setPrefRowCount(3);
        documentationArea.setWrapText(true);

        // Validation feedback
        validationLabel.setWrapText(true);
        validationLabel.setMaxWidth(Double.MAX_VALUE);
        validationLabel.setStyle("-fx-text-fill: #d32f2f; -fx-background-color: #ffebee; " +
                "-fx-padding: 8px; -fx-background-radius: 4px; -fx-border-color: #ef5350; " +
                "-fx-border-width: 1px; -fx-border-radius: 4px;");
        validationLabel.setVisible(false);

        // Preview area
        Label previewLabel = new Label("XSD Preview:");
        previewLabel.setStyle("-fx-font-weight: bold;");
        previewArea.setEditable(false);
        previewArea.setPrefRowCount(8);
        previewArea.setWrapText(true);
        previewArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 11px; " +
                "-fx-background-color: #f5f5f5;");
        updatePreview();

        // Add components to grid
        int row = 0;
        grid.add(contextLabel, 0, row);
        grid.add(contextValue, 1, row);
        row++;

        grid.add(typeLabel, 0, row);
        grid.add(typeNameField, 0, row + 1, 2, 1);
        GridPane.setHgrow(typeNameField, Priority.ALWAYS);
        row += 2;

        grid.add(defaultAlternativeCheckBox, 0, row, 2, 1);
        row++;

        grid.add(testLabel, 0, row);
        grid.add(testExpressionArea, 0, row + 1, 2, 1);
        GridPane.setHgrow(testExpressionArea, Priority.ALWAYS);
        row += 2;

        grid.add(namespaceLabel, 0, row);
        grid.add(xpathDefaultNamespaceField, 0, row + 1, 2, 1);
        GridPane.setHgrow(xpathDefaultNamespaceField, Priority.ALWAYS);
        row += 2;

        grid.add(docLabel, 0, row);
        grid.add(documentationArea, 0, row + 1, 2, 1);
        GridPane.setHgrow(documentationArea, Priority.ALWAYS);
        row += 2;

        grid.add(validationLabel, 0, row, 2, 1);
        row++;

        grid.add(previewLabel, 0, row);
        grid.add(previewArea, 0, row + 1, 2, 1);
        GridPane.setHgrow(previewArea, Priority.ALWAYS);

        content.getChildren().add(grid);

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Initially disable OK button until valid input
        getDialogPane().lookupButton(ButtonType.OK).setDisable(true);

        // Set dialog size
        getDialogPane().setPrefSize(750, 700);
    }

    private void setupValidation() {
        // Add listeners for real-time validation
        typeNameField.textProperty().addListener((observable, oldValue, newValue) -> {
            validateInput();
            updatePreview();
        });

        testExpressionArea.textProperty().addListener((observable, oldValue, newValue) -> {
            validateInput();
            updatePreview();
        });

        xpathDefaultNamespaceField.textProperty().addListener((observable, oldValue, newValue) -> {
            validateInput();
            updatePreview();
        });

        documentationArea.textProperty().addListener((observable, oldValue, newValue) -> {
            updatePreview();
        });

        // Initial validation
        validateInput();
    }

    private void validateInput() {
        String typeName = typeNameField.getText().trim();
        String testExpression = testExpressionArea.getText().trim();
        boolean isDefault = defaultAlternativeCheckBox.isSelected();
        boolean isValid = true;
        StringBuilder errorMessage = new StringBuilder();

        // Type name is required
        if (typeName.isEmpty()) {
            isValid = false;
            errorMessage.append("Type name is required\n");
        }

        // Test expression validation (only if not default)
        if (!isDefault && !testExpression.isEmpty()) {
            XPath2Service.ValidationResult validationResult = xpathService.validateExpression(testExpression);
            if (!validationResult.valid()) {
                isValid = false;
                errorMessage.append("Invalid XPath 2.0 expression:\n");
                errorMessage.append(validationResult.errorMessage());
            }
        }

        // Update validation UI
        if (isValid) {
            validationLabel.setVisible(false);
            typeNameField.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");
            testExpressionArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; " +
                    "-fx-font-size: 12px;");
        } else {
            validationLabel.setText("⚠ " + errorMessage);
            validationLabel.setVisible(true);
            if (typeName.isEmpty()) {
                typeNameField.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; " +
                        "-fx-font-size: 12px; -fx-border-color: #d32f2f; " +
                        "-fx-border-width: 2px;");
            }
            if (!isDefault && !testExpression.isEmpty() &&
                    !xpathService.validateExpression(testExpression).valid()) {
                testExpressionArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; " +
                        "-fx-font-size: 12px; -fx-border-color: #d32f2f; " +
                        "-fx-border-width: 2px;");
            }
        }

        // Enable/disable OK button
        getDialogPane().lookupButton(ButtonType.OK).setDisable(!isValid);
    }

    private void updatePreview() {
        String typeName = typeNameField.getText().trim();
        String testExpr = testExpressionArea.getText().trim();
        String namespace = xpathDefaultNamespaceField.getText().trim();
        String documentation = documentationArea.getText().trim();
        boolean isDefault = defaultAlternativeCheckBox.isSelected();

        if (typeName.isEmpty()) {
            previewArea.setText("Enter a type name to see preview...");
            return;
        }

        StringBuilder preview = new StringBuilder();
        preview.append("<!-- XSD 1.1 Type Alternative -->\n");
        preview.append("<xs:element name=\"").append(elementContext != null ? elementContext : "myElement").append("\">\n");
        preview.append("  \n");

        if (isDefault) {
            // Default alternative (no test attribute)
            preview.append("  <!-- Default alternative (used when no other alternatives match) -->\n");
            preview.append("  <xs:alternative type=\"").append(escapeXml(typeName)).append("\"");
        } else if (!testExpr.isEmpty()) {
            // Conditional alternative
            preview.append("  <!-- Conditional alternative -->\n");
            if (!namespace.isEmpty()) {
                preview.append("  <xs:alternative test=\"").append(escapeXml(testExpr)).append("\"\n");
                preview.append("                 type=\"").append(escapeXml(typeName)).append("\"\n");
                preview.append("                 xpath-default-namespace=\"").append(escapeXml(namespace)).append("\"");
            } else {
                preview.append("  <xs:alternative test=\"").append(escapeXml(testExpr)).append("\"\n");
                preview.append("                 type=\"").append(escapeXml(typeName)).append("\"");
            }
        } else {
            preview.append("  <xs:alternative type=\"").append(escapeXml(typeName)).append("\"");
        }

        if (!documentation.isEmpty()) {
            preview.append(">\n");
            preview.append("    <xs:annotation>\n");
            preview.append("      <xs:documentation>\n");
            preview.append("        ").append(escapeXml(documentation)).append("\n");
            preview.append("      </xs:documentation>\n");
            preview.append("    </xs:annotation>\n");
            preview.append("  </xs:alternative>\n");
        } else {
            preview.append("/>\n");
        }

        preview.append("</xs:element>\n\n");

        // Add helpful notes
        preview.append("Notes:\n");
        if (isDefault) {
            preview.append("✓ Default alternative (no test attribute)\n");
            preview.append("✓ Used when no other alternatives' conditions match\n");
        } else {
            preview.append("✓ XPath 2.0 test expression for conditional type assignment\n");
            preview.append("✓ Test evaluated in the context of the element\n");
        }
        preview.append("✓ Schema must declare vc:minVersion=\"1.1\" for XSD 1.1 support\n");

        previewArea.setText(preview.toString());
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Result class for the dialog
     */
    public record TypeAlternativeResult(
            String testExpression,    // null for default alternative
            String typeName,
            String xpathDefaultNamespace,
            String documentation
    ) {
        public boolean isDefaultAlternative() {
            return testExpression == null || testExpression.isEmpty();
        }

        public boolean hasNamespace() {
            return xpathDefaultNamespace != null && !xpathDefaultNamespace.isEmpty();
        }

        public boolean hasDocumentation() {
            return documentation != null && !documentation.isEmpty();
        }
    }
}
