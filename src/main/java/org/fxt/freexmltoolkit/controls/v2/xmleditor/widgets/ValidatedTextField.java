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

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.scene.Node;
import javafx.scene.control.TextField;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;

/**
 * Text field for every type without a more specific widget (strings and tokens, names, durations, Gregorian
 * parts, binary, QNames, lists, unions). Checks the value live against the declared type when a validator is given
 * and shows the reason; the value is never blocked.
 */
public class ValidatedTextField implements TypeAwareWidgetFactory.EditWidget {

    private final TextField textField;
    private final Function<String, ValidationResult> validator;
    private XmlSchemaProvider schemaProvider;
    private String elementXPath;
    private ValidationResult validationResult = ValidationResult.valid();

    /**
     * Creates a new ValidatedTextField checked through the schema provider once {@link #setElementXPath} is set.
     *
     * @param currentValue   the current value
     * @param onValueChange  callback when the value changes (may be {@code null})
     * @param schemaProvider the schema provider for validation (may be {@code null})
     * @param facets         unused, kept for callers
     */
    public ValidatedTextField(String currentValue, Consumer<String> onValueChange,
                              XmlSchemaProvider schemaProvider, Map<String, String> facets) {
        this(currentValue, onValueChange, (Function<String, ValidationResult>) null, null);
        this.schemaProvider = schemaProvider;
    }

    /**
     * Creates a new ValidatedTextField.
     *
     * @param currentValue  the current value
     * @param onValueChange callback when the value changes (may be {@code null})
     * @param validator     the check of the declared type (may be {@code null})
     * @param promptText    a hint of the expected form shown while empty (may be {@code null})
     */
    public ValidatedTextField(String currentValue, Consumer<String> onValueChange,
                              Function<String, ValidationResult> validator, String promptText) {
        this.validator = validator;
        this.textField = new TextField(currentValue != null ? currentValue : "");
        if (promptText != null) {
            textField.setPromptText(promptText);
        }
        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            validate(newVal);
            if (onValueChange != null) {
                onValueChange.accept(newVal);
            }
        });
        validate(textField.getText());
    }

    /**
     * Sets the element XPath for validation through the schema provider.
     *
     * @param elementXPath the XPath to the element being edited
     */
    public void setElementXPath(String elementXPath) {
        this.elementXPath = elementXPath;
    }

    private void validate(String value) {
        if (validator != null) {
            validationResult = validator.apply(value);
        } else if (schemaProvider != null && elementXPath != null) {
            validationResult = schemaProvider.validateElementValue(elementXPath, value);
        } else {
            validationResult = ValidationResult.valid();
        }
        WidgetFeedback.show(textField, validationResult);
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
