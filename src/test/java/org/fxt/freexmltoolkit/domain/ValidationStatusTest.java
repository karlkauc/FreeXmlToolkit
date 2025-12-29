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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValidationStatus")
class ValidationStatusTest {

    @Test
    @DisplayName("All enum values exist")
    void allValuesExist() {
        assertEquals(5, ValidationStatus.values().length);
        assertNotNull(ValidationStatus.PENDING);
        assertNotNull(ValidationStatus.RUNNING);
        assertNotNull(ValidationStatus.PASSED);
        assertNotNull(ValidationStatus.FAILED);
        assertNotNull(ValidationStatus.ERROR);
    }

    @ParameterizedTest
    @EnumSource(ValidationStatus.class)
    @DisplayName("All statuses have display text")
    void allHaveDisplayText(ValidationStatus status) {
        assertNotNull(status.getDisplayText());
        assertFalse(status.getDisplayText().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(ValidationStatus.class)
    @DisplayName("All statuses have color")
    void allHaveColor(ValidationStatus status) {
        assertNotNull(status.getColor());
        assertTrue(status.getColor().startsWith("#"));
        assertEquals(7, status.getColor().length()); // #RRGGBB format
    }

    @ParameterizedTest
    @EnumSource(ValidationStatus.class)
    @DisplayName("All statuses have icon literal")
    void allHaveIconLiteral(ValidationStatus status) {
        assertNotNull(status.getIconLiteral());
        assertTrue(status.getIconLiteral().startsWith("bi-"));
    }

    @Test
    @DisplayName("PENDING has correct values")
    void pendingValues() {
        ValidationStatus status = ValidationStatus.PENDING;
        assertEquals("Pending", status.getDisplayText());
        assertEquals("#6c757d", status.getColor());
        assertEquals("bi-hourglass", status.getIconLiteral());
    }

    @Test
    @DisplayName("RUNNING has correct values")
    void runningValues() {
        ValidationStatus status = ValidationStatus.RUNNING;
        assertEquals("Running", status.getDisplayText());
        assertEquals("#007bff", status.getColor());
        assertEquals("bi-arrow-repeat", status.getIconLiteral());
    }

    @Test
    @DisplayName("PASSED has correct values")
    void passedValues() {
        ValidationStatus status = ValidationStatus.PASSED;
        assertEquals("Passed", status.getDisplayText());
        assertEquals("#28a745", status.getColor());
        assertEquals("bi-check-circle-fill", status.getIconLiteral());
    }

    @Test
    @DisplayName("FAILED has correct values")
    void failedValues() {
        ValidationStatus status = ValidationStatus.FAILED;
        assertEquals("Failed", status.getDisplayText());
        assertEquals("#dc3545", status.getColor());
        assertEquals("bi-x-circle-fill", status.getIconLiteral());
    }

    @Test
    @DisplayName("ERROR has correct values")
    void errorValues() {
        ValidationStatus status = ValidationStatus.ERROR;
        assertEquals("Error", status.getDisplayText());
        assertEquals("#ffc107", status.getColor());
        assertEquals("bi-exclamation-triangle-fill", status.getIconLiteral());
    }

    @Test
    @DisplayName("toString returns display text")
    void toStringReturnsDisplayText() {
        assertEquals("Pending", ValidationStatus.PENDING.toString());
        assertEquals("Passed", ValidationStatus.PASSED.toString());
        assertEquals("Failed", ValidationStatus.FAILED.toString());
    }
}
