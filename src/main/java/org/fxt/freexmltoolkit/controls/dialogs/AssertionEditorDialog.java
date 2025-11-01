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
 * Dialog for creating and editing XSD 1.1 assertions (xs:assert).
 * Provides XPath 2.0 expression validation and preview.
 */
public class AssertionEditorDialog extends Dialog<AssertionEditorDialog.AssertionResult> {

    private final TextArea testExpressionArea;
    private final TextField xpathDefaultNamespaceField;
    private final TextArea documentationArea;
    private final Label validationLabel;
    private final TextArea previewArea;
    private final XPath2Service xpathService;
    private final String elementContext;
    private final boolean isSimpleType;

    public AssertionEditorDialog(String elementContext) {
        this(elementContext, false, null, null, null);
    }

    public AssertionEditorDialog(String elementContext, boolean isSimpleType) {
        this(elementContext, isSimpleType, null, null, null);
    }

    public AssertionEditorDialog(String elementContext, String existingTest,
                                 String existingNamespace, String existingDocumentation) {
        this(elementContext, false, existingTest, existingNamespace, existingDocumentation);
    }

    public AssertionEditorDialog(String elementContext, boolean isSimpleType, String existingTest,
                                 String existingNamespace, String existingDocumentation) {
        this.elementContext = elementContext;
        this.isSimpleType = isSimpleType;
        this.xpathService = new XPath2Service();

        setTitle("XSD 1.1 Assertion Editor");
        setHeaderText(isSimpleType
                ? "Create or edit xs:assert for simpleType restriction"
                : "Create or edit xs:assert for complexType");

        // Set dialog icon
        setGraphic(new FontIcon("bi-check-circle"));

        // Create UI components
        testExpressionArea = new TextArea();
        xpathDefaultNamespaceField = new TextField();
        documentationArea = new TextArea();
        validationLabel = new Label();
        previewArea = new TextArea();

        // Set existing values if editing
        if (existingTest != null) {
            testExpressionArea.setText(existingTest);
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
                return new AssertionResult(
                        testExpressionArea.getText().trim(),
                        xpathDefaultNamespaceField.getText().trim(),
                        documentationArea.getText().trim()
                );
            }
            return null;
        });

        // Focus on expression field when dialog opens
        Platform.runLater(() -> testExpressionArea.requestFocus());
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
        Label contextLabel = new Label("Context Element:");
        contextLabel.setStyle("-fx-font-weight: bold;");
        Label contextValue = new Label(elementContext != null ? elementContext : "complexType");
        contextValue.setStyle("-fx-text-fill: #4a90e2; -fx-font-weight: bold;");

        // Test expression (required)
        Label testLabel = new Label("XPath 2.0 Test Expression: *");
        testLabel.setStyle("-fx-font-weight: bold;");
        String promptText = isSimpleType
                ? "Enter XPath 2.0 expression using $value (e.g., $value > 0 and $value < 100)"
                : "Enter XPath 2.0 expression (e.g., price > discount)";
        testExpressionArea.setPromptText(promptText);
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
        documentationArea.setPromptText("Describe what this assertion validates...");
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
        getDialogPane().setPrefSize(750, 650);
    }

    private void setupValidation() {
        // Add listeners for real-time validation
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
        String testExpression = testExpressionArea.getText().trim();
        boolean isValid = true;
        StringBuilder errorMessage = new StringBuilder();

        if (testExpression.isEmpty()) {
            isValid = false;
            errorMessage.append("Test expression is required\n");
        } else {
            // Validate XPath 2.0 syntax using XPath2Service
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
            testExpressionArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; " +
                    "-fx-font-size: 12px;");
        } else {
            validationLabel.setText("⚠ " + errorMessage);
            validationLabel.setVisible(true);
            testExpressionArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; " +
                    "-fx-font-size: 12px; -fx-border-color: #d32f2f; " +
                    "-fx-border-width: 2px;");
        }

        // Enable/disable OK button
        getDialogPane().lookupButton(ButtonType.OK).setDisable(!isValid);
    }

    private void updatePreview() {
        String testExpr = testExpressionArea.getText().trim();
        String namespace = xpathDefaultNamespaceField.getText().trim();
        String documentation = documentationArea.getText().trim();

        if (testExpr.isEmpty()) {
            previewArea.setText("Enter a test expression to see preview...");
            return;
        }

        StringBuilder preview = new StringBuilder();
        preview.append("<!-- XSD 1.1 Assertion -->\n");

        if (isSimpleType) {
            // SimpleType context
            preview.append("<xs:simpleType name=\"").append(elementContext != null ? elementContext : "YourType").append("\">\n");
            preview.append("  <xs:restriction base=\"xs:integer\">\n");
            preview.append("    <!-- Facets here (e.g., minInclusive, maxInclusive) -->\n");
            preview.append("    \n");
            preview.append("    <!-- Assertion -->\n");
        } else {
            // ComplexType context
            preview.append("<xs:complexType name=\"").append(elementContext != null ? elementContext : "YourType").append("\">\n");
            preview.append("  <xs:sequence>\n");
            preview.append("    <!-- Your elements here -->\n");
            preview.append("  </xs:sequence>\n");
            preview.append("  \n");
            preview.append("  <!-- Assertion -->\n");
        }

        String indent = isSimpleType ? "    " : "  ";
        if (!namespace.isEmpty()) {
            preview.append(indent).append("<xs:assert test=\"").append(escapeXml(testExpr)).append("\"\n");
            preview.append(indent).append("           xpath-default-namespace=\"").append(escapeXml(namespace)).append("\"");
        } else {
            preview.append(indent).append("<xs:assert test=\"").append(escapeXml(testExpr)).append("\"");
        }

        if (!documentation.isEmpty()) {
            preview.append(">\n");
            preview.append(indent).append("  <xs:annotation>\n");
            preview.append(indent).append("    <xs:documentation>\n");
            preview.append(indent).append("      ").append(escapeXml(documentation)).append("\n");
            preview.append(indent).append("    </xs:documentation>\n");
            preview.append(indent).append("  </xs:annotation>\n");
            preview.append(indent).append("</xs:assert>\n");
        } else {
            preview.append("/>\n");
        }

        if (isSimpleType) {
            preview.append("  </xs:restriction>\n");
            preview.append("</xs:simpleType>\n\n");
        } else {
            preview.append("</xs:complexType>\n\n");
        }

        // Add helpful notes
        preview.append("Notes:\n");
        preview.append("✓ XPath 2.0 syntax validated\n");
        if (isSimpleType) {
            preview.append("✓ Test expression evaluated with $value variable (the value being validated)\n");
            preview.append("✓ Example: $value > 0 and $value < 100 (for a range check)\n");
        } else {
            preview.append("✓ Test expression evaluated in the context of the complexType\n");
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
    public record AssertionResult(
            String testExpression,
            String xpathDefaultNamespace,
            String documentation
    ) {
        public boolean hasNamespace() {
            return xpathDefaultNamespace != null && !xpathDefaultNamespace.isEmpty();
        }

        public boolean hasDocumentation() {
            return documentation != null && !documentation.isEmpty();
        }
    }
}
