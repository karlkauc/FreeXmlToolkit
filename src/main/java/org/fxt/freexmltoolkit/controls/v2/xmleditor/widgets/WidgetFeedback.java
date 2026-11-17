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
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationResult;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ValidationSeverity;

/**
 * Shows a widget's validation result: the theme's error outline ({@code fxt-field-error}) and the reason as tooltip
 * while the value is invalid or questionable; nothing otherwise. Style classes keep the inline font size the grid
 * gives an inline editor.
 */
final class WidgetFeedback {

    static final String ERROR_CLASS = "fxt-field-error";

    private WidgetFeedback() {
    }

    /**
     * @param node   the widget's node
     * @param result the current validation result
     */
    static void show(Node node, ValidationResult result) {
        boolean flagged = result != null && (!result.isValid() || result.severity() != ValidationSeverity.INFO)
                && result.errorMessage() != null;
        node.getStyleClass().remove(ERROR_CLASS);
        if (flagged) {
            node.getStyleClass().add(ERROR_CLASS);
        }
        if (node instanceof Control control) {
            if (flagged) {
                Tooltip tip = new Tooltip(result.errorMessage());
                tip.setWrapText(true);
                tip.setMaxWidth(420);
                control.setTooltip(tip);
            } else {
                control.setTooltip(null);
            }
        } else if (flagged) {
            Tooltip.install(node, new Tooltip(result.errorMessage()));
        }
    }
}
