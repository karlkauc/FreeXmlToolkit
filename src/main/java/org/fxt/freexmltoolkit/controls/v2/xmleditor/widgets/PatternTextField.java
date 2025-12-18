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

import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationSeverity;

import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * TextField widget with pattern (regex) validation.
 *
 * <p>Provides real-time feedback on whether the value matches the XSD pattern.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class PatternTextField implements TypeAwareWidgetFactory.EditWidget {

    private final TextField textField;
    private final Consumer<String> onValueChange;
    private final String patternString;
    private final Pattern pattern;
    private ValidationResult validationResult = ValidationResult.valid();

    /**
     * Creates a new PatternTextField.
     *
     * @param currentValue  the current value
     * @param onValueChange callback when value changes
     * @param patternString the XSD pattern (regex)
     */
    public PatternTextField(String currentValue, Consumer<String> onValueChange, String patternString) {
        this.onValueChange = onValueChange;
        this.patternString = patternString;

        // Compile pattern
        Pattern compiledPattern = null;
        try {
            compiledPattern = Pattern.compile(patternString);
        } catch (PatternSyntaxException e) {
            // Invalid pattern - will always show as warning
        }
        this.pattern = compiledPattern;

        this.textField = new TextField(currentValue != null ? currentValue : "");
        this.textField.setPromptText("Pattern: " + patternString);

        // Tooltip with pattern info
        Tooltip tooltip = new Tooltip("Pattern: " + patternString);
        textField.setTooltip(tooltip);

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

        // Apply initial style
        applyValidationStyle();
    }

    private void validate(String value) {
        if (value == null || value.isEmpty()) {
            validationResult = ValidationResult.valid();
        } else if (pattern == null) {
            validationResult = ValidationResult.warning("Invalid pattern definition: " + patternString);
        } else if (!pattern.matcher(value).matches()) {
            validationResult = ValidationResult.warning("Value does not match pattern: " + patternString);
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
                    "-fx-border-color: #ffc107; -fx-border-width: 2px; -fx-background-color: #fff8e1;");

            // Update tooltip with error
            if (validationResult.errorMessage() != null) {
                Tooltip.install(textField, new Tooltip(validationResult.errorMessage()));
            }
        } else {
            textField.setStyle(baseStyle +
                    "-fx-border-color: #28a745; -fx-border-width: 1px;");
            Tooltip.install(textField, new Tooltip("Pattern: " + patternString + " (valid)"));
        }
    }

    @Override
    public Node getNode() {
        return textField;
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
