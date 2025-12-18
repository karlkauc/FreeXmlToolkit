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
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;

import java.util.function.Consumer;

/**
 * Toggle button widget for XSD boolean types.
 *
 * <p>Provides true/false toggle buttons for easy boolean value editing.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class BooleanToggle implements TypeAwareWidgetFactory.EditWidget {

    private final HBox container;
    private final ToggleButton trueButton;
    private final ToggleButton falseButton;
    private final Consumer<String> onValueChange;
    private String currentValue;

    /**
     * Creates a new BooleanToggle.
     *
     * @param currentValue  the current value ("true", "false", "1", "0", or null)
     * @param onValueChange callback when value changes
     */
    public BooleanToggle(String currentValue, Consumer<String> onValueChange) {
        this.onValueChange = onValueChange;
        this.currentValue = currentValue;

        container = new HBox(2);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(2));

        ToggleGroup toggleGroup = new ToggleGroup();

        trueButton = new ToggleButton("true");
        trueButton.setToggleGroup(toggleGroup);
        trueButton.setPrefWidth(60);
        styleButton(trueButton, false);

        falseButton = new ToggleButton("false");
        falseButton.setToggleGroup(toggleGroup);
        falseButton.setPrefWidth(60);
        styleButton(falseButton, false);

        // Set initial state
        if (isTrue(currentValue)) {
            trueButton.setSelected(true);
            styleButton(trueButton, true);
        } else if (isFalse(currentValue)) {
            falseButton.setSelected(true);
            styleButton(falseButton, true);
        }

        // Listen for changes
        trueButton.setOnAction(e -> {
            if (trueButton.isSelected()) {
                this.currentValue = "true";
                styleButton(trueButton, true);
                styleButton(falseButton, false);
                if (onValueChange != null) {
                    onValueChange.accept("true");
                }
            }
        });

        falseButton.setOnAction(e -> {
            if (falseButton.isSelected()) {
                this.currentValue = "false";
                styleButton(falseButton, true);
                styleButton(trueButton, false);
                if (onValueChange != null) {
                    onValueChange.accept("false");
                }
            }
        });

        container.getChildren().addAll(trueButton, falseButton);
    }

    private boolean isTrue(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private boolean isFalse(String value) {
        return "false".equalsIgnoreCase(value) || "0".equals(value);
    }

    private void styleButton(ToggleButton button, boolean selected) {
        if (selected) {
            if (button == trueButton) {
                button.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; " +
                        "-fx-font-weight: bold; -fx-background-radius: 3;");
            } else {
                button.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; " +
                        "-fx-font-weight: bold; -fx-background-radius: 3;");
            }
        } else {
            button.setStyle("-fx-background-color: #e9ecef; -fx-text-fill: #495057; " +
                    "-fx-background-radius: 3;");
        }
    }

    @Override
    public Node getNode() {
        return container;
    }

    @Override
    public String getValue() {
        return currentValue;
    }

    @Override
    public void setValue(String value) {
        this.currentValue = value;
        trueButton.setSelected(false);
        falseButton.setSelected(false);
        styleButton(trueButton, false);
        styleButton(falseButton, false);

        if (isTrue(value)) {
            trueButton.setSelected(true);
            styleButton(trueButton, true);
        } else if (isFalse(value)) {
            falseButton.setSelected(true);
            styleButton(falseButton, true);
        }
    }

    @Override
    public void focus() {
        if (trueButton.isSelected()) {
            trueButton.requestFocus();
        } else if (falseButton.isSelected()) {
            falseButton.requestFocus();
        } else {
            trueButton.requestFocus();
        }
    }

    @Override
    public ValidationResult getValidationResult() {
        // Boolean is always valid if a value is selected
        if (currentValue == null || currentValue.isEmpty()) {
            return ValidationResult.warning("Please select true or false");
        }
        return ValidationResult.valid();
    }
}
