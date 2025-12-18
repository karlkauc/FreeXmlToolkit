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
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationSeverity;

import java.util.function.Consumer;

/**
 * TextField widget with length validation (minLength, maxLength, length facets).
 *
 * <p>Shows a character counter and provides real-time length validation feedback.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class LengthValidatedField implements TypeAwareWidgetFactory.EditWidget {

    private final VBox container;
    private final TextField textField;
    private final Label counterLabel;
    private final Consumer<String> onValueChange;
    private final Integer minLength;
    private final Integer maxLength;
    private final Integer exactLength;
    private ValidationResult validationResult = ValidationResult.valid();

    /**
     * Creates a new LengthValidatedField.
     *
     * @param currentValue  the current value
     * @param onValueChange callback when value changes
     * @param minLength     minimum length (null if not constrained)
     * @param maxLength     maximum length (null if not constrained)
     * @param exactLength   exact length (null if not constrained)
     */
    public LengthValidatedField(String currentValue, Consumer<String> onValueChange,
                                Integer minLength, Integer maxLength, Integer exactLength) {
        this.onValueChange = onValueChange;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.exactLength = exactLength;

        container = new VBox(2);
        container.setPadding(new Insets(0));

        // Text field
        textField = new TextField(currentValue != null ? currentValue : "");

        // Counter label
        counterLabel = new Label();
        counterLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #6c757d;");

        // Layout with counter on the right
        HBox fieldRow = new HBox(5);
        fieldRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textField, Priority.ALWAYS);
        fieldRow.getChildren().addAll(textField, counterLabel);

        container.getChildren().add(fieldRow);

        // Set tooltip with constraints
        updateTooltip();

        // Validate initial value
        updateCounter(currentValue != null ? currentValue : "");
        validate(currentValue != null ? currentValue : "");

        // Listen for changes
        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateCounter(newVal);
            validate(newVal);
            if (onValueChange != null) {
                onValueChange.accept(newVal);
            }
        });

        // Apply initial style
        applyValidationStyle();
    }

    private void updateTooltip() {
        StringBuilder tip = new StringBuilder("Length constraints: ");
        if (exactLength != null) {
            tip.append("exactly ").append(exactLength).append(" characters");
        } else {
            if (minLength != null) {
                tip.append("min ").append(minLength);
            }
            if (maxLength != null) {
                if (minLength != null) tip.append(", ");
                tip.append("max ").append(maxLength);
            }
        }
        textField.setTooltip(new Tooltip(tip.toString()));
    }

    private void updateCounter(String value) {
        int currentLength = value != null ? value.length() : 0;

        StringBuilder counter = new StringBuilder();
        counter.append(currentLength);

        if (exactLength != null) {
            counter.append("/").append(exactLength);
        } else if (maxLength != null) {
            counter.append("/").append(maxLength);
        }

        if (minLength != null && minLength > 0) {
            counter.append(" (min: ").append(minLength).append(")");
        }

        counterLabel.setText(counter.toString());

        // Color the counter based on state
        if (exactLength != null) {
            if (currentLength == exactLength) {
                counterLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #28a745; -fx-font-weight: bold;");
            } else {
                counterLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #dc3545;");
            }
        } else if (maxLength != null && currentLength > maxLength) {
            counterLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #dc3545; -fx-font-weight: bold;");
        } else if (minLength != null && currentLength < minLength) {
            counterLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #ffc107;");
        } else {
            counterLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #28a745;");
        }
    }

    private void validate(String value) {
        int length = value != null ? value.length() : 0;

        if (exactLength != null && length != exactLength) {
            validationResult = ValidationResult.warning(
                    "Value must be exactly " + exactLength + " characters (currently " + length + ")");
        } else if (minLength != null && length < minLength) {
            validationResult = ValidationResult.warning(
                    "Value must be at least " + minLength + " characters (currently " + length + ")");
        } else if (maxLength != null && length > maxLength) {
            validationResult = ValidationResult.warning(
                    "Value must be at most " + maxLength + " characters (currently " + length + ")");
        } else {
            validationResult = ValidationResult.valid();
        }
        applyValidationStyle();
    }

    private void applyValidationStyle() {
        String baseStyle = "-fx-background-radius: 3; -fx-border-radius: 3;";

        if (!validationResult.isValid() ||
                validationResult.severity() == ValidationSeverity.WARNING) {
            textField.setStyle(baseStyle +
                    "-fx-border-color: #ffc107; -fx-border-width: 2px;");
        } else {
            textField.setStyle(baseStyle +
                    "-fx-border-color: #28a745; -fx-border-width: 1px;");
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
        updateCounter(value);
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
