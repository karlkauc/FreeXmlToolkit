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

package org.fxt.freexmltoolkit.controls.v2.common.utilities;

import org.fxt.freexmltoolkit.domain.ValidationError;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XmlValidationHelper utility class.
 * Tests validation error conversion and message cleaning.
 */
class XmlValidationHelperTest {

    @Test
    void convertToValidationError_simpleError() {
        SAXParseException exception = new SAXParseException("Test error", null);
        ValidationError error = XmlValidationHelper.convertToValidationError(exception);

        assertNotNull(error);
        assertEquals("Test error", error.message());
        assertEquals("ERROR", error.severity());
    }

    @Test
    void convertToValidationError_cvcErrorPrefix() {
        SAXParseException exception = new SAXParseException("cvc-complex-type.2.4.a: Invalid content", null);
        ValidationError error = XmlValidationHelper.convertToValidationError(exception);

        assertNotNull(error);
        assertEquals("Invalid content", error.message());
    }

    @Test
    void convertToValidationError_incompleteElementError() {
        SAXParseException exception = new SAXParseException("The content of element 'root' is not complete.", null);
        ValidationError error = XmlValidationHelper.convertToValidationError(exception);

        assertNotNull(error);
        assertEquals("Content is incomplete.", error.message());
    }

    @Test
    void convertToValidationError_multiplePatterns() {
        SAXParseException exception = new SAXParseException("cvc-pattern: The content of element 'value' is not complete.", null);
        ValidationError error = XmlValidationHelper.convertToValidationError(exception);

        assertNotNull(error);
        // First pattern match should be applied
        assertTrue(error.message().contains("incomplete") || error.message().contains("Content"));
    }

    @Test
    void convertToValidationError_withLineAndColumn() {
        SAXParseException exception = new SAXParseException("Test error", null, null, 10, 5);
        ValidationError error = XmlValidationHelper.convertToValidationError(exception);

        assertNotNull(error);
        assertEquals(10, error.lineNumber());
        assertEquals(5, error.columnNumber());
    }

    @Test
    void convertToValidationError_preservesUnmatchedMessage() {
        SAXParseException exception = new SAXParseException("Some random validation message", null);
        ValidationError error = XmlValidationHelper.convertToValidationError(exception);

        assertNotNull(error);
        assertEquals("Some random validation message", error.message());
    }

    @Test
    void isMandatoryFromMinOccurs_nullMinOccurs() {
        assertTrue(XmlValidationHelper.isMandatoryFromMinOccurs(null));
    }

    @Test
    void isMandatoryFromMinOccurs_emptyMinOccurs() {
        assertTrue(XmlValidationHelper.isMandatoryFromMinOccurs(""));
    }

    @Test
    void isMandatoryFromMinOccurs_zeroMinOccurs() {
        assertFalse(XmlValidationHelper.isMandatoryFromMinOccurs("0"));
    }

    @Test
    void isMandatoryFromMinOccurs_oneMinOccurs() {
        assertTrue(XmlValidationHelper.isMandatoryFromMinOccurs("1"));
    }

    @Test
    void isMandatoryFromMinOccurs_largeMinOccurs() {
        assertTrue(XmlValidationHelper.isMandatoryFromMinOccurs("100"));
    }

    @Test
    void isMandatoryFromMinOccurs_invalidMinOccurs() {
        // Invalid values should default to mandatory
        assertTrue(XmlValidationHelper.isMandatoryFromMinOccurs("invalid"));
        assertTrue(XmlValidationHelper.isMandatoryFromMinOccurs("abc"));
    }
}
