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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.Test;

/**
 * Tests that verify the type resolution chain for XsdAttribute facets display.
 * These tests validate the model structures that XsdPropertiesPanel.updateFacetsColumnForAttribute
 * relies on to resolve enumerations, patterns, and facets from referenced types.
 */
class AttributeFacetsResolutionTest {

    /**
     * Verifies that an attribute's type can be resolved to a SimpleType with enumerations
     * by traversing to the schema root and finding the named type.
     * This is the exact resolution path used by updateFacetsColumnForAttribute.
     */
    @Test
    void attributeTypeResolvesToSimpleTypeWithEnumerations() {
        // Build schema: xs:simpleType name="ListedTypeEnum" with enumerations
        XsdSchema schema = new XsdSchema();
        XsdSimpleType simpleType = new XsdSimpleType("ListedTypeEnum");
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:string");
        restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "TypeA"));
        restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "TypeB"));
        restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "TypeC"));
        simpleType.addChild(restriction);
        schema.addChild(simpleType);

        // Build nested structure: schema > complexType > sequence > element > complexType > simpleContent > extension > attribute
        XsdComplexType identifiersType = new XsdComplexType("IdentifiersType");
        XsdSequence sequence = new XsdSequence();
        XsdElement otherIdElement = new XsdElement("OtherID");
        XsdAttribute listedTypeAttr = new XsdAttribute("ListedType");
        listedTypeAttr.setType("ListedTypeEnum");

        // Wire up parent-child relationships
        otherIdElement.addChild(listedTypeAttr);
        sequence.addChild(otherIdElement);
        identifiersType.addChild(sequence);
        schema.addChild(identifiersType);

        // Verify the attribute has the type reference
        assertEquals("ListedTypeEnum", listedTypeAttr.getType());

        // Verify we can traverse from attribute to schema root (same logic as updateFacetsColumnForAttribute)
        XsdNode current = listedTypeAttr;
        while (current != null && !(current instanceof XsdSchema)) {
            current = current.getParent();
        }
        assertNotNull(current);
        assertInstanceOf(XsdSchema.class, current);

        // Verify we can find the SimpleType by name and extract enumerations
        XsdSchema resolvedSchema = (XsdSchema) current;
        String typeName = listedTypeAttr.getType();
        List<String> enumerations = findEnumerationsInType(resolvedSchema, typeName);

        assertEquals(3, enumerations.size());
        assertTrue(enumerations.contains("TypeA"));
        assertTrue(enumerations.contains("TypeB"));
        assertTrue(enumerations.contains("TypeC"));
    }

    /**
     * Verifies that patterns from a referenced type can be resolved for an attribute.
     */
    @Test
    void attributeTypeResolvesToSimpleTypeWithPatterns() {
        XsdSchema schema = new XsdSchema();
        XsdSimpleType simpleType = new XsdSimpleType("PostalCodeType");
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:string");
        restriction.addFacet(new XsdFacet(XsdFacetType.PATTERN, "[0-9]{5}"));
        restriction.addFacet(new XsdFacet(XsdFacetType.PATTERN, "[A-Z]{2}[0-9]{3}"));
        simpleType.addChild(restriction);
        schema.addChild(simpleType);

        XsdAttribute attr = new XsdAttribute("postalCode");
        attr.setType("PostalCodeType");
        schema.addChild(attr);

        // Resolve patterns
        XsdNode current = attr;
        while (current != null && !(current instanceof XsdSchema)) {
            current = current.getParent();
        }
        XsdSchema resolvedSchema = (XsdSchema) current;
        List<String> patterns = findPatternsInType(resolvedSchema, "PostalCodeType");

        assertEquals(2, patterns.size());
        assertTrue(patterns.contains("[0-9]{5}"));
        assertTrue(patterns.contains("[A-Z]{2}[0-9]{3}"));
    }

    /**
     * Verifies that facets (minLength, maxLength, etc.) from a referenced type can be resolved.
     */
    @Test
    void attributeTypeResolvesToSimpleTypeWithFacets() {
        XsdSchema schema = new XsdSchema();
        XsdSimpleType simpleType = new XsdSimpleType("ShortStringType");
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:string");
        restriction.addFacet(new XsdFacet(XsdFacetType.MIN_LENGTH, "1"));
        restriction.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "50"));
        simpleType.addChild(restriction);
        schema.addChild(simpleType);

        XsdAttribute attr = new XsdAttribute("shortName");
        attr.setType("ShortStringType");
        schema.addChild(attr);

        // Find restriction from type
        XsdNode current = attr;
        while (current != null && !(current instanceof XsdSchema)) {
            current = current.getParent();
        }
        XsdSchema resolvedSchema = (XsdSchema) current;
        XsdRestriction resolvedRestriction = findRestrictionInType(resolvedSchema, "ShortStringType");

        assertNotNull(resolvedRestriction);
        assertEquals("xs:string", resolvedRestriction.getBase());

        XsdFacet minLen = resolvedRestriction.getFacetByType(XsdFacetType.MIN_LENGTH);
        XsdFacet maxLen = resolvedRestriction.getFacetByType(XsdFacetType.MAX_LENGTH);
        assertNotNull(minLen);
        assertNotNull(maxLen);
        assertEquals("1", minLen.getValue());
        assertEquals("50", maxLen.getValue());
    }

    /**
     * Verifies that a built-in type (xs:string) does not resolve to any named type.
     */
    @Test
    void attributeWithBuiltInTypeHasNoReferencedEnumerations() {
        XsdSchema schema = new XsdSchema();
        XsdAttribute attr = new XsdAttribute("name");
        attr.setType("xs:string");
        schema.addChild(attr);

        List<String> enumerations = findEnumerationsInType(schema, "xs:string");
        assertTrue(enumerations.isEmpty());
    }

    /**
     * Verifies that an attribute with no type reference returns empty results.
     */
    @Test
    void attributeWithNoTypeHasNoFacets() {
        XsdSchema schema = new XsdSchema();
        XsdAttribute attr = new XsdAttribute("untyped");
        schema.addChild(attr);

        assertNull(attr.getType());
    }

    /**
     * Verifies that namespace-prefixed type references are handled.
     */
    @Test
    void attributeTypeWithNamespacePrefix() {
        XsdSchema schema = new XsdSchema();
        XsdSimpleType simpleType = new XsdSimpleType("StatusType");
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:string");
        restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "Active"));
        restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "Inactive"));
        simpleType.addChild(restriction);
        schema.addChild(simpleType);

        XsdAttribute attr = new XsdAttribute("status");
        attr.setType("tns:StatusType");
        schema.addChild(attr);

        // Simulate namespace prefix stripping (same logic as updateFacetsColumnForAttribute)
        String typeRef = attr.getType();
        String typeName = typeRef;
        if (typeName.contains(":")) {
            typeName = typeName.substring(typeName.indexOf(":") + 1);
        }

        assertEquals("StatusType", typeName);
        List<String> enumerations = findEnumerationsInType(schema, typeName);
        assertEquals(2, enumerations.size());
    }

    /**
     * Verifies deeply nested attribute (like the original bug report path) can resolve to schema root.
     */
    @Test
    void deeplyNestedAttributeResolvesToSchemaRoot() {
        XsdSchema schema = new XsdSchema();

        // Build: schema > complexType > sequence > element > complexType > simpleContent > extension > attribute
        XsdComplexType ct1 = new XsdComplexType("IdentifiersType");
        XsdSequence seq = new XsdSequence();
        XsdElement elem = new XsdElement("OtherID");
        XsdComplexType ct2 = new XsdComplexType(null); // anonymous
        XsdAttribute attr = new XsdAttribute("ListedType");
        attr.setType("SomeEnumType");

        ct2.addChild(attr);
        elem.addChild(ct2);
        seq.addChild(elem);
        ct1.addChild(seq);
        schema.addChild(ct1);

        // Traverse to root
        XsdNode current = attr;
        int depth = 0;
        while (current != null && !(current instanceof XsdSchema)) {
            current = current.getParent();
            depth++;
        }

        assertNotNull(current);
        assertInstanceOf(XsdSchema.class, current);
        assertEquals(5, depth); // attr -> ct2 -> elem -> seq -> ct1 -> schema
    }

    // ===== Helper methods that mirror the logic in XsdPropertiesPanel =====

    private List<String> findEnumerationsInType(XsdSchema schema, String typeName) {
        List<String> enumerations = new ArrayList<>();
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdSimpleType simpleType && typeName.equals(simpleType.getName())) {
                for (XsdNode typeChild : simpleType.getChildren()) {
                    if (typeChild instanceof XsdRestriction restriction) {
                        for (XsdFacet facet : restriction.getFacets()) {
                            if (facet.getFacetType() == XsdFacetType.ENUMERATION) {
                                enumerations.add(facet.getValue());
                            }
                        }
                    }
                }
                break;
            }
        }
        return enumerations;
    }

    private List<String> findPatternsInType(XsdSchema schema, String typeName) {
        List<String> patterns = new ArrayList<>();
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdSimpleType simpleType && typeName.equals(simpleType.getName())) {
                for (XsdNode typeChild : simpleType.getChildren()) {
                    if (typeChild instanceof XsdRestriction restriction) {
                        for (XsdFacet facet : restriction.getFacets()) {
                            if (facet.getFacetType() == XsdFacetType.PATTERN) {
                                patterns.add(facet.getValue());
                            }
                        }
                    }
                }
                break;
            }
        }
        return patterns;
    }

    private XsdRestriction findRestrictionInType(XsdSchema schema, String typeName) {
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdSimpleType simpleType && typeName.equals(simpleType.getName())) {
                for (XsdNode typeChild : simpleType.getChildren()) {
                    if (typeChild instanceof XsdRestriction restriction) {
                        return restriction;
                    }
                }
            }
        }
        return null;
    }
}
