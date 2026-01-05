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

package org.fxt.freexmltoolkit.controls.v2.editor.panels;

import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Helper class for constraint-related functionality in XsdPropertiesPanel.
 *
 * <p>Manages constraints like nillable, abstract, and fixed values.</p>
 *
 * @since 2.0
 */
public class XsdPropertiesPanelConstraintHelper {
    private static final Logger logger = LogManager.getLogger(XsdPropertiesPanelConstraintHelper.class);

    /**
     * Loads constraint properties from a model object.
     *
     * @param modelObject the model object
     * @param nillableCheckBox the nillable checkbox
     * @param abstractCheckBox the abstract checkbox
     * @param fixedCheckBox the fixed checkbox
     * @param fixedValueField the fixed value field
     */
    public void loadConstraints(Object modelObject, CheckBox nillableCheckBox, CheckBox abstractCheckBox,
                                CheckBox fixedCheckBox, TextField fixedValueField) {
        if (modelObject instanceof XsdElement element) {
            nillableCheckBox.setSelected(element.isNillable());
            abstractCheckBox.setSelected(element.isAbstract());

            // Load fixed value
            String fixedValue = element.getFixed();
            boolean hasFixed = fixedValue != null && !fixedValue.isEmpty();
            fixedCheckBox.setSelected(hasFixed);
            fixedValueField.setText(hasFixed ? fixedValue : "");
            fixedValueField.setDisable(!hasFixed);

            logger.debug("Loaded constraints: nillable={}, abstract={}, fixed={}",
                    element.isNillable(), element.isAbstract(), fixedValue);
        } else {
            // Clear constraints for non-elements
            nillableCheckBox.setSelected(false);
            abstractCheckBox.setSelected(false);
            fixedCheckBox.setSelected(false);
            fixedValueField.setText("");
            fixedValueField.setDisable(true);
        }
    }

    /**
     * Updates edit state of constraint controls based on edit mode and node type.
     *
     * @param modelObject the model object
     * @param isEditMode whether in edit mode
     * @param nillableCheckBox the nillable checkbox
     * @param abstractCheckBox the abstract checkbox
     * @param fixedCheckBox the fixed checkbox
     * @param fixedValueField the fixed value field
     */
    public void updateConstraintEditState(Object modelObject, boolean isEditMode,
                                         CheckBox nillableCheckBox, CheckBox abstractCheckBox,
                                         CheckBox fixedCheckBox, TextField fixedValueField) {
        boolean isElement = modelObject instanceof XsdElement;
        boolean constraintsEnabled = isEditMode && isElement;

        nillableCheckBox.setDisable(!constraintsEnabled);
        abstractCheckBox.setDisable(!constraintsEnabled);
        fixedCheckBox.setDisable(!constraintsEnabled);
        fixedValueField.setEditable(isEditMode);

        logger.debug("Updated constraint edit state: constraintsEnabled={}", constraintsEnabled);
    }

    /**
     * Saves constraints from UI to model object.
     *
     * @param modelObject the model object to save to
     * @param nillableCheckBox the nillable checkbox value
     * @param abstractCheckBox the abstract checkbox value
     * @param fixedCheckBox the fixed checkbox value
     * @param fixedValueField the fixed value field value
     */
    public void saveConstraints(Object modelObject, boolean nillableCheckBox, boolean abstractCheckBox,
                               boolean fixedCheckBox, String fixedValueField) {
        if (!(modelObject instanceof XsdElement element)) {
            logger.warn("Cannot save constraints: model object is not an XsdElement");
            return;
        }

        element.setNillable(nillableCheckBox);
        element.setAbstract(abstractCheckBox);

        if (fixedCheckBox && fixedValueField != null && !fixedValueField.isEmpty()) {
            element.setFixed(fixedValueField);
        } else {
            element.setFixed(null);
        }

        logger.debug("Saved constraints: nillable={}, abstract={}, fixed={}",
                nillableCheckBox, abstractCheckBox, fixedValueField);
    }

    /**
     * Validates constraint values.
     *
     * @param fixedValue the fixed value to validate
     * @return true if valid
     */
    public boolean isValidFixedValue(String fixedValue) {
        if (fixedValue == null || fixedValue.trim().isEmpty()) {
            logger.warn("Fixed value is empty");
            return false;
        }
        return true;
    }

    /**
     * Gets constraint description for display.
     *
     * @param element the element
     * @return description of constraints
     */
    public String getConstraintDescription(XsdElement element) {
        StringBuilder sb = new StringBuilder();

        if (element.isNillable()) {
            sb.append("Nillable ");
        }
        if (element.isAbstract()) {
            sb.append("Abstract ");
        }
        if (element.getFixed() != null && !element.getFixed().isEmpty()) {
            sb.append("Fixed(").append(element.getFixed()).append(") ");
        }

        String description = sb.toString().trim();
        return description.isEmpty() ? "No constraints" : description;
    }
}
