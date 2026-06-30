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
 */

package org.fxt.freexmltoolkit.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RenderingStatus}. These run without a started JavaFX toolkit, so the
 * active pipeline is typically unavailable — the contract is simply that the methods are
 * robust (never throw) and return a sane value/{@code null}.
 */
class RenderingStatusTest {

    @Test
    void activePipelineDescriptionNeverThrows() {
        assertDoesNotThrow(RenderingStatus::activePipelineDescription);
        // Result may be null (no toolkit) or a description string; both are acceptable.
        String desc = RenderingStatus.activePipelineDescription();
        if (desc != null) {
            org.junit.jupiter.api.Assertions.assertFalse(desc.isBlank());
        }
    }

    @Test
    void isHardwareActiveNeverThrows() {
        assertDoesNotThrow(RenderingStatus::isHardwareActive);
    }

    @Test
    void detectAdapterNamesNeverThrows() {
        assertDoesNotThrow(RenderingPipelineDetector::detectAdapterNames);
        // Never null, even when the OS query fails.
        org.junit.jupiter.api.Assertions.assertNotNull(RenderingPipelineDetector.detectAdapterNames());
    }
}
