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

package org.fxt.freexmltoolkit.controller.controls;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure context-range logic of {@link ValidationErrorRenderer}.
 */
class ValidationErrorRendererTest {

    @Test
    @DisplayName("Error in the middle returns the full +/- radius window")
    void middleOfFile() {
        assertArrayEquals(new int[]{4, 6}, ValidationErrorRenderer.contextRange(5, 1, 10));
        assertArrayEquals(new int[]{3, 7}, ValidationErrorRenderer.contextRange(5, 2, 10));
    }

    @Test
    @DisplayName("Error on the first line clamps the start to 1")
    void firstLine() {
        assertArrayEquals(new int[]{1, 2}, ValidationErrorRenderer.contextRange(1, 1, 10));
    }

    @Test
    @DisplayName("Error on the last line clamps the end to the line count")
    void lastLine() {
        assertArrayEquals(new int[]{9, 10}, ValidationErrorRenderer.contextRange(10, 1, 10));
    }

    @Test
    @DisplayName("Error line beyond the file is clamped to the last line")
    void errorLineBeyondFile() {
        assertArrayEquals(new int[]{9, 10}, ValidationErrorRenderer.contextRange(99, 1, 10));
    }

    @Test
    @DisplayName("Single-line file returns just that line")
    void singleLineFile() {
        assertArrayEquals(new int[]{1, 1}, ValidationErrorRenderer.contextRange(1, 1, 1));
    }

    @Test
    @DisplayName("No usable line number or empty file yields an empty range")
    void emptyOrUnknown() {
        assertArrayEquals(new int[]{0, 0}, ValidationErrorRenderer.contextRange(0, 1, 10));
        assertArrayEquals(new int[]{0, 0}, ValidationErrorRenderer.contextRange(-1, 1, 10));
        assertArrayEquals(new int[]{0, 0}, ValidationErrorRenderer.contextRange(5, 1, 0));
        assertArrayEquals(new int[]{0, 0}, ValidationErrorRenderer.contextRange(5, -1, 10));
    }
}
