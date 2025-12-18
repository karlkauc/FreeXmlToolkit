/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
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
package org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationSeverity;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;

/**
 * TextField widget for XSD anyURI type.
 *
 * <p>Provides URI validation and quick-insert buttons for common URI prefixes.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class UriTextField implements TypeAwareWidgetFactory.EditWidget {

    private final HBox container;
    private final TextField textField;
    private final Consumer<String> onValueChange;
    private ValidationResult validationResult = ValidationResult.valid();

    // Common URI prefixes
    private static final String[] URI_PREFIXES = {"https://", "http://", "file://", "urn:", "mailto:"};

    /**
     * Creates a new UriTextField.
     *
     * @param currentValue  the current value
     * @param onValueChange callback when value changes
     */
    public UriTextField(String currentValue, Consumer<String> onValueChange) {
        this.onValueChange = onValueChange;

        container = new HBox(3);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(0));

        // Text field
        textField = new TextField(currentValue != null ? currentValue : "");
        textField.setPromptText("Enter URI...");
        HBox.setHgrow(textField, Priority.ALWAYS);

        // Validate initial value
        if (currentValue != null && !currentValue.isEmpty()) {
            validate(currentValue);
        }

        // Listen for changes
        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            validate(newVal);
            if (onValueChange != null) {
                onValueChange.accept(newVal);
            }
        });

        // Create prefix buttons
        HBox prefixBox = new HBox(1);
        prefixBox.setAlignment(Pos.CENTER_LEFT);

        for (String prefix : URI_PREFIXES) {
            Button btn = createPrefixButton(prefix);
            prefixBox.getChildren().add(btn);
        }

        container.getChildren().addAll(textField, prefixBox);

        // Apply initial style
        applyValidationStyle();
    }

    private Button createPrefixButton(String prefix) {
        // Use short label
        String label = switch (prefix) {
            case "https://" -> "S";
            case "http://" -> "H";
            case "file://" -> "F";
            case "urn:" -> "U";
            case "mailto:" -> "M";
            default -> prefix.substring(0, 1);
        };

        Button btn = new Button(label);
        btn.setStyle("-fx-font-size: 9; -fx-padding: 2 4; -fx-background-radius: 2; " +
                "-fx-background-color: #e9ecef; -fx-text-fill: #495057;");
        btn.setTooltip(new Tooltip("Insert " + prefix));

        btn.setOnAction(e -> {
            String current = textField.getText();
            // Remove existing scheme if present
            if (current.contains("://")) {
                current = current.substring(current.indexOf("://") + 3);
            } else if (current.startsWith("urn:") || current.startsWith("mailto:")) {
                current = current.substring(current.indexOf(":") + 1);
            }
            textField.setText(prefix + current);
            textField.requestFocus();
            textField.positionCaret(textField.getText().length());
        });

        // Hover effect
        btn.setOnMouseEntered(e ->
                btn.setStyle("-fx-font-size: 9; -fx-padding: 2 4; -fx-background-radius: 2; " +
                        "-fx-background-color: #007bff; -fx-text-fill: white;"));
        btn.setOnMouseExited(e ->
                btn.setStyle("-fx-font-size: 9; -fx-padding: 2 4; -fx-background-radius: 2; " +
                        "-fx-background-color: #e9ecef; -fx-text-fill: #495057;"));

        return btn;
    }

    private void validate(String value) {
        if (value == null || value.isEmpty()) {
            validationResult = ValidationResult.valid();
        } else {
            try {
                new URI(value);
                validationResult = ValidationResult.valid();
            } catch (URISyntaxException e) {
                validationResult = ValidationResult.warning("Invalid URI syntax: " + e.getMessage());
            }
        }
        applyValidationStyle();
    }

    private void applyValidationStyle() {
        String baseStyle = "-fx-background-radius: 3; -fx-border-radius: 3;";

        if (!validationResult.isValid() ||
                validationResult.severity() == ValidationSeverity.WARNING) {
            textField.setStyle(baseStyle +
                    "-fx-border-color: #ffc107; -fx-border-width: 2px;");
            textField.setTooltip(new Tooltip(validationResult.errorMessage()));
        } else {
            textField.setStyle(baseStyle +
                    "-fx-border-color: #28a745; -fx-border-width: 1px;");
            textField.setTooltip(new Tooltip("Valid URI"));
        }
    }

    @Override
    public Node getNode() {
        return container;
    }

    @Override
    public String getValue() {
        return textField.getText();
    }

    @Override
    public void setValue(String value) {
        textField.setText(value);
        validate(value);
    }

    @Override
    public void focus() {
        textField.requestFocus();
        textField.selectAll();
    }

    @Override
    public ValidationResult getValidationResult() {
        return validationResult;
    }
}
