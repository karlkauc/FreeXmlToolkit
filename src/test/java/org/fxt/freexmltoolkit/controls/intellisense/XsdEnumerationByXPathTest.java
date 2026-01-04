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
package org.fxt.freexmltoolkit.controls.intellisense;

import org.fxt.freexmltoolkit.controls.shared.utilities.XsdIntegrationAdapter;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XPath-based enumeration lookup in XsdIntegrationAdapter.
 * <p>
 * This test ensures that when multiple elements share the same name but exist
 * at different XPath locations (e.g., multiple "Version" elements in FundsXML),
 * the correct enumeration values are returned based on the full XPath context.
 * </p>
 */
class XsdEnumerationByXPathTest {

    private XsdIntegrationAdapter adapter;
    private XsdDocumentationData xsdData;

    @BeforeEach
    void setUp() {
        adapter = new XsdIntegrationAdapter();
        xsdData = new XsdDocumentationData();

        // Set up mock XSD elements with different enumeration values at different paths
        Map<String, XsdExtendedElement> elementMap = new HashMap<>();

        // First "Version" element at /FundsXML4/ControlData/Version
        XsdExtendedElement controlDataVersion = new XsdExtendedElement();
        controlDataVersion.setElementName("Version");
        controlDataVersion.setCurrentXpath("/FundsXML4/ControlData/Version");
        controlDataVersion.setRestrictionInfo(new XsdExtendedElement.RestrictionInfo(
                "xs:string",
                Map.of("enumeration", List.of("4.0.0", "4.0.1", "4.1.0", "4.2.0"))
        ));
        elementMap.put("/FundsXML4/ControlData/Version", controlDataVersion);

        // Second "Version" element at /FundsXML4/RegulatoryReportings/EMT/DataSetInformation/Version
        XsdExtendedElement emtVersion = new XsdExtendedElement();
        emtVersion.setElementName("Version");
        emtVersion.setCurrentXpath("/FundsXML4/RegulatoryReportings/EMT/DataSetInformation/Version");
        emtVersion.setRestrictionInfo(new XsdExtendedElement.RestrictionInfo(
                "xs:string",
                Map.of("enumeration", List.of("V3", "V3S1", "V3S2", "V4"))
        ));
        elementMap.put("/FundsXML4/RegulatoryReportings/EMT/DataSetInformation/Version", emtVersion);

        // Third "Version" element at /FundsXML4/Documents/Document/Version (no enumeration, just text)
        XsdExtendedElement documentVersion = new XsdExtendedElement();
        documentVersion.setElementName("Version");
        documentVersion.setCurrentXpath("/FundsXML4/Documents/Document/Version");
        documentVersion.setElementType("Text64Type");
        // No restriction info - this is a simple text field
        elementMap.put("/FundsXML4/Documents/Document/Version", documentVersion);

        xsdData.setExtendedXsdElementMap(elementMap);
        adapter.setXsdDocumentationData(xsdData);
    }

    @Test
    @DisplayName("getElementEnumerationValuesByXPath returns correct values for ControlData/Version")
    void testControlDataVersionEnumerations() {
        List<String> values = adapter.getElementEnumerationValuesByXPath("/FundsXML4/ControlData/Version");

        assertNotNull(values, "Enumeration values should not be null");
        assertEquals(4, values.size(), "Should have 4 enumeration values");
        assertTrue(values.contains("4.0.0"), "Should contain '4.0.0'");
        assertTrue(values.contains("4.0.1"), "Should contain '4.0.1'");
        assertTrue(values.contains("4.1.0"), "Should contain '4.1.0'");
        assertTrue(values.contains("4.2.0"), "Should contain '4.2.0'");

        // Should NOT contain values from other Version elements
        assertFalse(values.contains("V3S1"), "Should NOT contain 'V3S1' from EMT Version");
        assertFalse(values.contains("V3"), "Should NOT contain 'V3' from EMT Version");
    }

    @Test
    @DisplayName("getElementEnumerationValuesByXPath returns correct values for EMT/Version")
    void testEmtVersionEnumerations() {
        List<String> values = adapter.getElementEnumerationValuesByXPath(
                "/FundsXML4/RegulatoryReportings/EMT/DataSetInformation/Version");

        assertNotNull(values, "Enumeration values should not be null");
        assertEquals(4, values.size(), "Should have 4 enumeration values");
        assertTrue(values.contains("V3"), "Should contain 'V3'");
        assertTrue(values.contains("V3S1"), "Should contain 'V3S1'");
        assertTrue(values.contains("V3S2"), "Should contain 'V3S2'");
        assertTrue(values.contains("V4"), "Should contain 'V4'");

        // Should NOT contain values from ControlData Version
        assertFalse(values.contains("4.0.0"), "Should NOT contain '4.0.0' from ControlData Version");
        assertFalse(values.contains("4.1.0"), "Should NOT contain '4.1.0' from ControlData Version");
    }

    @Test
    @DisplayName("getElementEnumerationValuesByXPath returns empty list for element without enumeration")
    void testDocumentVersionNoEnumeration() {
        List<String> values = adapter.getElementEnumerationValuesByXPath(
                "/FundsXML4/Documents/Document/Version");

        assertNotNull(values, "Result should not be null");
        assertTrue(values.isEmpty(), "Should return empty list for element without enumeration");
    }

    @Test
    @DisplayName("getElementEnumerationValuesByXPath returns empty list for non-existent path")
    void testNonExistentPath() {
        List<String> values = adapter.getElementEnumerationValuesByXPath(
                "/NonExistent/Path/Version");

        assertNotNull(values, "Result should not be null");
        assertTrue(values.isEmpty(), "Should return empty list for non-existent path");
    }

    @Test
    @DisplayName("Deprecated getElementEnumerationValues method does not mix enumerations from different paths")
    @SuppressWarnings("deprecation")
    void testDeprecatedMethodDoesNotMixEnumerations() {
        // This test verifies that after the fix, the deprecated method
        // no longer returns values from ALL elements with the same name.
        // Instead, it should only return values for exact path matches.
        List<String> values = adapter.getElementEnumerationValues("Version");

        // The deprecated method tries "/" + elementName = "/Version" which doesn't exist
        // So it should return empty (the fallback that collected all was removed)
        assertNotNull(values, "Result should not be null");
        assertTrue(values.isEmpty(),
                "Deprecated method should return empty when exact path doesn't match");
    }
}
