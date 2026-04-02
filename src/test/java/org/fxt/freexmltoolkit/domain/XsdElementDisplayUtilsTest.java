package org.fxt.freexmltoolkit.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.junit.jupiter.api.Test;

class XsdElementDisplayUtilsTest {

    @Test
    void testIsCompositorElement() {
        assertTrue(XsdElementDisplayUtils.isCompositorElement("SEQUENCE_1"));
        assertTrue(XsdElementDisplayUtils.isCompositorElement("CHOICE_2"));
        assertTrue(XsdElementDisplayUtils.isCompositorElement("ALL_1"));
        assertTrue(XsdElementDisplayUtils.isCompositorElement("GROUP_1"));
        assertTrue(XsdElementDisplayUtils.isCompositorElement("SEQUENCE"));
        assertFalse(XsdElementDisplayUtils.isCompositorElement("Name"));
        assertFalse(XsdElementDisplayUtils.isCompositorElement("Element"));
        assertFalse(XsdElementDisplayUtils.isCompositorElement(null));
    }

    @Test
    void testBuildFacetHints_withRestrictions() {
        XsdExtendedElement element = new XsdExtendedElement();
        Map<String, List<String>> facets = new HashMap<>();
        facets.put("minLength", List.of("1"));
        facets.put("maxLength", List.of("100"));
        facets.put("pattern", List.of("[A-Z]+"));
        facets.put("enumeration", List.of("A", "B", "C")); // Should be skipped
        element.setRestrictionInfo(new XsdExtendedElement.RestrictionInfo("xs:string", facets));

        List<String> hints = XsdElementDisplayUtils.buildFacetHints(element);

        assertTrue(hints.contains("minLength:1"));
        assertTrue(hints.contains("maxLength:100"));
        assertTrue(hints.contains("pattern"));
        // enumeration should be skipped
        assertFalse(hints.stream().anyMatch(h -> h.contains("enumeration")));
    }

    @Test
    void testBuildFacetHints_noRestriction() {
        XsdExtendedElement element = new XsdExtendedElement();
        List<String> hints = XsdElementDisplayUtils.buildFacetHints(element);
        assertTrue(hints.isEmpty());
    }

    @Test
    void testExtractExamples_enumerations() {
        XsdExtendedElement element = new XsdExtendedElement();
        Map<String, List<String>> facets = new HashMap<>();
        facets.put("enumeration", List.of("EUR", "USD", "GBP", "CHF", "JPY", "AUD", "CAD"));
        element.setRestrictionInfo(new XsdExtendedElement.RestrictionInfo("xs:string", facets));

        List<String> examples = XsdElementDisplayUtils.extractExamples(element);

        assertEquals(6, examples.size()); // 5 values + "... (2 more)"
        assertEquals("EUR", examples.get(0));
        assertEquals("JPY", examples.get(4));
        assertTrue(examples.get(5).contains("2 more"));
    }

    @Test
    void testExtractExamples_exampleValues() {
        XsdExtendedElement element = new XsdExtendedElement();
        element.setExampleValues(List.of("example1", "example2", "example3", "example4"));

        List<String> examples = XsdElementDisplayUtils.extractExamples(element);

        assertEquals(3, examples.size()); // Limited to 3
        assertEquals("example1", examples.get(0));
    }

    @Test
    void testExtractExamples_noData() {
        XsdExtendedElement element = new XsdExtendedElement();
        List<String> examples = XsdElementDisplayUtils.extractExamples(element);
        assertTrue(examples.isEmpty());
    }

    @Test
    void testExtractDefaultValue_noNode() {
        XsdExtendedElement element = new XsdExtendedElement();
        assertNull(XsdElementDisplayUtils.extractDefaultValue(element));
    }

    @Test
    void testBuildCardinalityString_noNode() {
        XsdExtendedElement element = new XsdExtendedElement();
        assertEquals("", XsdElementDisplayUtils.buildCardinalityString(element));
    }

    @Test
    void testBuildCompletionItem() {
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementName("TestElement");
        element.setElementType("xs:string");
        Map<String, List<String>> facets = new HashMap<>();
        facets.put("maxLength", List.of("50"));
        facets.put("enumeration", List.of("A", "B"));
        element.setRestrictionInfo(new XsdExtendedElement.RestrictionInfo("xs:string", facets));

        CompletionItem item = XsdElementDisplayUtils.buildCompletionItem(element, 0);

        assertEquals("TestElement", item.getLabel());
        assertEquals("xs:string", item.getDataType());
        assertFalse(item.getFacetHints().isEmpty());
        assertTrue(item.getFacetHints().stream().anyMatch(h -> h.contains("maxLength:50")));
        assertFalse(item.getExamples().isEmpty());
        assertEquals("A", item.getExamples().get(0));
    }

    @Test
    void testResolveChildElements_nullParent() {
        List<CompletionItem> items = XsdElementDisplayUtils.resolveChildElements(null, null);
        assertTrue(items.isEmpty());
    }

    @Test
    void testResolveChildElements_noChildren() {
        XsdExtendedElement parent = new XsdExtendedElement();
        parent.setElementName("Parent");
        List<CompletionItem> items = XsdElementDisplayUtils.resolveChildElements(parent, null);
        assertTrue(items.isEmpty());
    }

    @Test
    void testResolveChildElements_withChildren() {
        // Set up XSD documentation data with element map
        XsdExtendedElement parent = new XsdExtendedElement();
        parent.setElementName("Parent");
        parent.setChildren(List.of("/root/Child1", "/root/Child2"));

        XsdExtendedElement child1 = new XsdExtendedElement();
        child1.setElementName("Child1");
        child1.setElementType("xs:string");

        XsdExtendedElement child2 = new XsdExtendedElement();
        child2.setElementName("Child2");
        child2.setElementType("xs:integer");

        Map<String, XsdExtendedElement> elementMap = new HashMap<>();
        elementMap.put("/root/Child1", child1);
        elementMap.put("/root/Child2", child2);

        XsdDocumentationData xsdData = new XsdDocumentationData();
        xsdData.setExtendedXsdElementMap(elementMap);

        List<CompletionItem> items = XsdElementDisplayUtils.resolveChildElements(parent, xsdData);

        assertEquals(2, items.size());
        assertEquals("Child1", items.get(0).getLabel());
        assertEquals("xs:string", items.get(0).getDataType());
        assertEquals("Child2", items.get(1).getLabel());
        assertEquals("xs:integer", items.get(1).getDataType());
    }

    @Test
    void testResolveChildElements_withCompositor() {
        // Parent has a SEQUENCE container which contains real elements
        XsdExtendedElement parent = new XsdExtendedElement();
        parent.setElementName("Parent");
        parent.setChildren(List.of("/root/SEQUENCE_1"));

        XsdExtendedElement sequence = new XsdExtendedElement();
        sequence.setElementName("SEQUENCE_1");
        sequence.setChildren(List.of("/root/SEQUENCE_1/Name", "/root/SEQUENCE_1/Age"));

        XsdExtendedElement nameElem = new XsdExtendedElement();
        nameElem.setElementName("Name");
        nameElem.setElementType("xs:string");

        XsdExtendedElement ageElem = new XsdExtendedElement();
        ageElem.setElementName("Age");
        ageElem.setElementType("xs:integer");

        Map<String, XsdExtendedElement> elementMap = new HashMap<>();
        elementMap.put("/root/SEQUENCE_1", sequence);
        elementMap.put("/root/SEQUENCE_1/Name", nameElem);
        elementMap.put("/root/SEQUENCE_1/Age", ageElem);

        XsdDocumentationData xsdData = new XsdDocumentationData();
        xsdData.setExtendedXsdElementMap(elementMap);

        List<CompletionItem> items = XsdElementDisplayUtils.resolveChildElements(parent, xsdData);

        assertEquals(2, items.size());
        assertEquals("Name", items.get(0).getLabel());
        assertEquals("Age", items.get(1).getLabel());
    }

    @Test
    void testGetNodeAttribute_nullNode() {
        assertNull(XsdElementDisplayUtils.getNodeAttribute(null, "test"));
    }
}
