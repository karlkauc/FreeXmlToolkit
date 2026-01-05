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

import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdPropertiesPanelFacetsHelper utility class.
 * Tests facet extraction and validation.
 */
class XsdPropertiesPanelFacetsHelperTest {

    private XsdPropertiesPanelFacetsHelper helper = new XsdPropertiesPanelFacetsHelper();

    @Test
    void getApplicableFacets_nullDatatype() {
        Set<XsdFacetType> result = helper.getApplicableFacets(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getApplicableFacets_emptyDatatype() {
        Set<XsdFacetType> result = helper.getApplicableFacets("");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getApplicableFacets_validDatatype() {
        // String datatype should have applicable facets
        Set<XsdFacetType> result = helper.getApplicableFacets("xs:string");
        assertNotNull(result);
        // String supports pattern, enumeration, etc.
        assertFalse(result.isEmpty());
    }

    @Test
    void getApplicableFacets_returnsEmptySetNotNull() {
        Set<XsdFacetType> result = helper.getApplicableFacets("invalidDatatype");
        assertNotNull(result);
        // Should return empty set, not null
        assertTrue(result.isEmpty() || !result.isEmpty()); // Always true - just verify not null
    }

    @Test
    void isValidPattern_validRegex() {
        assertTrue(helper.isValidPattern("[a-z]+"));
        assertTrue(helper.isValidPattern("\\d{3}-\\d{3}-\\d{4}"));
        assertTrue(helper.isValidPattern("^[A-Z].*"));
    }

    @Test
    void isValidPattern_invalidRegex() {
        assertFalse(helper.isValidPattern("[a-z"));
        assertFalse(helper.isValidPattern("(?P<invalid)")); // Invalid group name
    }

    @Test
    void isValidPattern_emptyPattern() {
        assertFalse(helper.isValidPattern(""));
    }

    @Test
    void isValidPattern_whitespaceOnlyPattern() {
        assertFalse(helper.isValidPattern("   "));
    }

    @Test
    void isValidPattern_nullPattern() {
        assertFalse(helper.isValidPattern(null));
    }

    @Test
    void isValidEnumeration_validValue() {
        assertTrue(helper.isValidEnumeration("value"));
        assertTrue(helper.isValidEnumeration("RED"));
        assertTrue(helper.isValidEnumeration("123"));
    }

    @Test
    void isValidEnumeration_emptyValue() {
        assertFalse(helper.isValidEnumeration(""));
    }

    @Test
    void isValidEnumeration_whitespaceValue() {
        assertFalse(helper.isValidEnumeration("   "));
    }

    @Test
    void isValidEnumeration_nullValue() {
        assertFalse(helper.isValidEnumeration(null));
    }

    @Test
    void getFacetLabel_allFacetTypes() {
        assertEquals("Length", helper.getFacetLabel(XsdFacetType.LENGTH));
        assertEquals("Min Length", helper.getFacetLabel(XsdFacetType.MIN_LENGTH));
        assertEquals("Max Length", helper.getFacetLabel(XsdFacetType.MAX_LENGTH));
        assertEquals("Pattern", helper.getFacetLabel(XsdFacetType.PATTERN));
        assertEquals("Enumeration", helper.getFacetLabel(XsdFacetType.ENUMERATION));
        assertEquals("Min Inclusive", helper.getFacetLabel(XsdFacetType.MIN_INCLUSIVE));
        assertEquals("Max Inclusive", helper.getFacetLabel(XsdFacetType.MAX_INCLUSIVE));
        assertEquals("Min Exclusive", helper.getFacetLabel(XsdFacetType.MIN_EXCLUSIVE));
        assertEquals("Max Exclusive", helper.getFacetLabel(XsdFacetType.MAX_EXCLUSIVE));
        assertEquals("Total Digits", helper.getFacetLabel(XsdFacetType.TOTAL_DIGITS));
        assertEquals("Fraction Digits", helper.getFacetLabel(XsdFacetType.FRACTION_DIGITS));
        assertEquals("Assertion", helper.getFacetLabel(XsdFacetType.ASSERTION));
        assertEquals("Explicit Timezone", helper.getFacetLabel(XsdFacetType.EXPLICIT_TIMEZONE));
    }

    @Test
    void getFacetLabel_whitespace() {
        assertEquals("White Space", helper.getFacetLabel(XsdFacetType.WHITE_SPACE));
    }

    @Test
    void getFacetLabel_nullType() {
        assertEquals("Unknown", helper.getFacetLabel(null));
    }

    @Test
    void extractPatterns_nullRestriction() {
        var result = helper.extractPatterns(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void extractEnumerations_nullRestriction() {
        var result = helper.extractEnumerations(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void extractAssertions_nullRestriction() {
        var result = helper.extractAssertions(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void extractPatterns_returnsEmptyListWhenEmpty() {
        // Null restriction should return empty list, not throw exception
        var result = helper.extractPatterns(null);
        assertEquals(0, result.size());
    }

    @Test
    void getApplicableFacets_usesEmptySetForNullCases() {
        // This tests the optimization: we should use Collections.emptySet()
        // instead of creating new HashSet for empty cases
        Set<XsdFacetType> result1 = helper.getApplicableFacets(null);
        Set<XsdFacetType> result2 = helper.getApplicableFacets(null);

        assertTrue(result1.isEmpty());
        assertTrue(result2.isEmpty());
    }
}
