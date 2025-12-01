/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
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

package org.fxt.freexmltoolkit.domain;

/**
 * Represents the validation status of a file during batch validation.
 */
public enum ValidationStatus {
    PENDING("Pending", "#6c757d", "bi-hourglass"),
    RUNNING("Running", "#007bff", "bi-arrow-repeat"),
    PASSED("Passed", "#28a745", "bi-check-circle-fill"),
    FAILED("Failed", "#dc3545", "bi-x-circle-fill"),
    ERROR("Error", "#ffc107", "bi-exclamation-triangle-fill");

    private final String displayText;
    private final String color;
    private final String iconLiteral;

    ValidationStatus(String displayText, String color, String iconLiteral) {
        this.displayText = displayText;
        this.color = color;
        this.iconLiteral = iconLiteral;
    }

    /**
     * Gets the human-readable display text for this status.
     *
     * @return the display text
     */
    public String getDisplayText() {
        return displayText;
    }

    /**
     * Gets the CSS color code associated with this status.
     *
     * @return the color code (e.g., "#28a745")
     */
    public String getColor() {
        return color;
    }

    /**
     * Gets the Ikonli icon literal for this status.
     *
     * @return the icon literal (e.g., "bi-check-circle-fill")
     */
    public String getIconLiteral() {
        return iconLiteral;
    }

    @Override
    public String toString() {
        return displayText;
    }
}
