package org.fxt.freexmltoolkit.controls.intellisense;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CompletionContext.
 * Tests context creation, property management, and state tracking for IntelliSense.
 */
class CompletionContextTest {

    private CompletionContext context;

    @BeforeEach
    void setUp() {
        context = new CompletionContext(
                "<root><element></element></root>",
                "",
                15,
                "element"
        );
    }

    @Test
    @DisplayName("Should create context with required fields")
    void testCreateContext() {
        assertNotNull(context, "Context should be created");
        assertEquals("<root><element></element></root>", context.getFullText(),
                "Full text should be set");
        assertEquals("", context.getSelectedText(), "Selected text should be empty string");
        assertEquals(15, context.getCaretPosition(), "Caret position should be set");
        assertEquals("element", context.getCurrentElement(), "Current element should be set");
    }

    @Test
    @DisplayName("Should default to ELEMENT completion type")
    void testDefaultCompletionType() {
        assertEquals(CompletionContext.CompletionType.ELEMENT, context.getCompletionType(),
                "Should default to ELEMENT completion type");
    }

    @Test
    @DisplayName("Should handle null selected text")
    void testNullSelectedText() {
        CompletionContext ctx = new CompletionContext(
                "<root></root>",
                null,
                5,
                "root"
        );

        assertEquals("", ctx.getSelectedText(),
                "Null selected text should be converted to empty string");
    }

    @Test
    @DisplayName("Should set and get inElement state")
    void testInElementState() {
        assertFalse(context.isInElement(), "Should default to false");

        context.setInElement(true);
        assertTrue(context.isInElement(), "Should be in element");

        context.setInElement(false);
        assertFalse(context.isInElement(), "Should not be in element");
    }

    @Test
    @DisplayName("Should set and get inAttribute state")
    void testInAttributeState() {
        assertFalse(context.isInAttribute(), "Should default to false");

        context.setInAttribute(true);
        assertTrue(context.isInAttribute(), "Should be in attribute");

        context.setInAttribute(false);
        assertFalse(context.isInAttribute(), "Should not be in attribute");
    }

    @Test
    @DisplayName("Should set and get inAttributeValue state")
    void testInAttributeValueState() {
        assertFalse(context.isInAttributeValue(), "Should default to false");

        context.setInAttributeValue(true);
        assertTrue(context.isInAttributeValue(), "Should be in attribute value");

        context.setInAttributeValue(false);
        assertFalse(context.isInAttributeValue(), "Should not be in attribute value");
    }

    @Test
    @DisplayName("Should set and get parent element")
    void testParentElement() {
        assertNull(context.getParentElement(), "Should default to null");

        context.setParentElement("root");
        assertEquals("root", context.getParentElement(), "Parent element should be set");

        context.setParentElement(null);
        assertNull(context.getParentElement(), "Parent element should be nullable");
    }

    @Test
    @DisplayName("Should set and get current namespace")
    void testCurrentNamespace() {
        assertNull(context.getCurrentNamespace(), "Should default to null");

        context.setCurrentNamespace("http://www.w3.org/2001/XMLSchema");
        assertEquals("http://www.w3.org/2001/XMLSchema", context.getCurrentNamespace(),
                "Namespace should be set");

        context.setCurrentNamespace(null);
        assertNull(context.getCurrentNamespace(), "Namespace should be nullable");
    }

    @Test
    @DisplayName("Should set and get hasXsdSchema flag")
    void testHasXsdSchema() {
        assertFalse(context.hasXsdSchema(), "Should default to false");

        context.setHasXsdSchema(true);
        assertTrue(context.hasXsdSchema(), "Should have XSD schema");

        context.setHasXsdSchema(false);
        assertFalse(context.hasXsdSchema(), "Should not have XSD schema");
    }

    @Test
    @DisplayName("Should set and get document type")
    void testDocumentType() {
        assertNull(context.getDocumentType(), "Should default to null");

        context.setDocumentType("XML");
        assertEquals("XML", context.getDocumentType(), "Document type should be set");

        context.setDocumentType("XSD");
        assertEquals("XSD", context.getDocumentType(), "Document type should be changeable");
    }

    @Test
    @DisplayName("Should set and get completion type")
    void testCompletionType() {
        assertEquals(CompletionContext.CompletionType.ELEMENT, context.getCompletionType(),
                "Should default to ELEMENT");

        context.setCompletionType(CompletionContext.CompletionType.ATTRIBUTE);
        assertEquals(CompletionContext.CompletionType.ATTRIBUTE, context.getCompletionType(),
                "Should change to ATTRIBUTE");

        context.setCompletionType(CompletionContext.CompletionType.ATTRIBUTE_VALUE);
        assertEquals(CompletionContext.CompletionType.ATTRIBUTE_VALUE, context.getCompletionType(),
                "Should change to ATTRIBUTE_VALUE");
    }

    @Test
    @DisplayName("Should have all CompletionType enum values")
    void testCompletionTypeEnumValues() {
        CompletionContext.CompletionType[] types = CompletionContext.CompletionType.values();

        assertTrue(types.length >= 6, "Should have at least 6 completion types");

        // Verify specific types exist
        assertNotNull(CompletionContext.CompletionType.valueOf("ELEMENT"));
        assertNotNull(CompletionContext.CompletionType.valueOf("ATTRIBUTE"));
        assertNotNull(CompletionContext.CompletionType.valueOf("ATTRIBUTE_VALUE"));
        assertNotNull(CompletionContext.CompletionType.valueOf("TEXT_CONTENT"));
        assertNotNull(CompletionContext.CompletionType.valueOf("NAMESPACE"));
        assertNotNull(CompletionContext.CompletionType.valueOf("TEMPLATE"));
    }

    @Test
    @DisplayName("Should provide meaningful toString output")
    void testToString() {
        context.setInElement(true);
        context.setInAttribute(false);
        context.setCompletionType(CompletionContext.CompletionType.ELEMENT);

        String result = context.toString();

        assertNotNull(result, "toString should not return null");
        assertTrue(result.contains("element"), "Should contain current element");
        assertTrue(result.contains("ELEMENT"), "Should contain completion type");
        assertTrue(result.contains("inElement=true"), "Should contain inElement state");
        assertTrue(result.contains("inAttribute=false"), "Should contain inAttribute state");
    }

    @Test
    @DisplayName("Should handle element completion context")
    void testElementCompletionContext() {
        context.setInElement(true);
        context.setParentElement("root");
        context.setCompletionType(CompletionContext.CompletionType.ELEMENT);

        assertTrue(context.isInElement(), "Should be in element");
        assertEquals("root", context.getParentElement(), "Parent should be root");
        assertEquals(CompletionContext.CompletionType.ELEMENT, context.getCompletionType(),
                "Type should be ELEMENT");
    }

    @Test
    @DisplayName("Should handle attribute completion context")
    void testAttributeCompletionContext() {
        context.setInAttribute(true);
        context.setCompletionType(CompletionContext.CompletionType.ATTRIBUTE);

        assertTrue(context.isInAttribute(), "Should be in attribute");
        assertEquals(CompletionContext.CompletionType.ATTRIBUTE, context.getCompletionType(),
                "Type should be ATTRIBUTE");
    }

    @Test
    @DisplayName("Should handle attribute value completion context")
    void testAttributeValueCompletionContext() {
        context.setInAttributeValue(true);
        context.setCompletionType(CompletionContext.CompletionType.ATTRIBUTE_VALUE);

        assertTrue(context.isInAttributeValue(), "Should be in attribute value");
        assertEquals(CompletionContext.CompletionType.ATTRIBUTE_VALUE, context.getCompletionType(),
                "Type should be ATTRIBUTE_VALUE");
    }

    @Test
    @DisplayName("Should handle namespace completion context")
    void testNamespaceCompletionContext() {
        context.setCurrentNamespace("xs");
        context.setCompletionType(CompletionContext.CompletionType.NAMESPACE);

        assertEquals("xs", context.getCurrentNamespace(), "Namespace should be xs");
        assertEquals(CompletionContext.CompletionType.NAMESPACE, context.getCompletionType(),
                "Type should be NAMESPACE");
    }

    @Test
    @DisplayName("Should handle schema-aware context")
    void testSchemaAwareContext() {
        context.setHasXsdSchema(true);
        context.setDocumentType("XSD");

        assertTrue(context.hasXsdSchema(), "Should have XSD schema");
        assertEquals("XSD", context.getDocumentType(), "Document type should be XSD");
    }

    @Test
    @DisplayName("Should create context with empty full text")
    void testEmptyFullText() {
        CompletionContext ctx = new CompletionContext("", "", 0, "");

        assertEquals("", ctx.getFullText(), "Full text should be empty");
        assertEquals(0, ctx.getCaretPosition(), "Caret should be at 0");
    }

    @Test
    @DisplayName("Should create context with large caret position")
    void testLargeCaretPosition() {
        CompletionContext ctx = new CompletionContext(
                "<root></root>",
                "",
                1000,
                "root"
        );

        assertEquals(1000, ctx.getCaretPosition(),
                "Should accept caret position beyond text length");
    }

    @Test
    @DisplayName("Should handle complex namespace URIs")
    void testComplexNamespaceURI() {
        String namespaceUri = "http://www.w3.org/2001/XMLSchema-instance";
        context.setCurrentNamespace(namespaceUri);

        assertEquals(namespaceUri, context.getCurrentNamespace(),
                "Should handle complex namespace URIs");
    }

    @Test
    @DisplayName("Should handle empty namespace prefix")
    void testEmptyNamespacePrefix() {
        context.setCurrentNamespace("");

        assertEquals("", context.getCurrentNamespace(),
                "Should handle empty namespace prefix");
    }

    @Test
    @DisplayName("Should allow switching between completion types")
    void testSwitchCompletionTypes() {
        // Start with ELEMENT
        context.setCompletionType(CompletionContext.CompletionType.ELEMENT);
        assertEquals(CompletionContext.CompletionType.ELEMENT, context.getCompletionType());

        // Switch to ATTRIBUTE
        context.setCompletionType(CompletionContext.CompletionType.ATTRIBUTE);
        assertEquals(CompletionContext.CompletionType.ATTRIBUTE, context.getCompletionType());

        // Switch to TEXT_CONTENT
        context.setCompletionType(CompletionContext.CompletionType.TEXT_CONTENT);
        assertEquals(CompletionContext.CompletionType.TEXT_CONTENT, context.getCompletionType());

        // Switch to TEMPLATE
        context.setCompletionType(CompletionContext.CompletionType.TEMPLATE);
        assertEquals(CompletionContext.CompletionType.TEMPLATE, context.getCompletionType());
    }

    @Test
    @DisplayName("Should maintain independent state flags")
    void testIndependentStateFlags() {
        // Set all flags to different values
        context.setInElement(true);
        context.setInAttribute(false);
        context.setInAttributeValue(true);
        context.setHasXsdSchema(false);

        // Verify they're independent
        assertTrue(context.isInElement());
        assertFalse(context.isInAttribute());
        assertTrue(context.isInAttributeValue());
        assertFalse(context.hasXsdSchema());

        // Flip all flags
        context.setInElement(false);
        context.setInAttribute(true);
        context.setInAttributeValue(false);
        context.setHasXsdSchema(true);

        // Verify they changed independently
        assertFalse(context.isInElement());
        assertTrue(context.isInAttribute());
        assertFalse(context.isInAttributeValue());
        assertTrue(context.hasXsdSchema());
    }

    @Test
    @DisplayName("Should handle selected text with content")
    void testSelectedTextWithContent() {
        CompletionContext ctx = new CompletionContext(
                "<root>selected text here</root>",
                "selected text here",
                10,
                "root"
        );

        assertEquals("selected text here", ctx.getSelectedText(),
                "Should preserve selected text");
    }

    @Test
    @DisplayName("Should handle special characters in element names")
    void testSpecialCharactersInElementName() {
        CompletionContext ctx = new CompletionContext(
                "<my:element></my:element>",
                "",
                10,
                "my:element"
        );

        assertEquals("my:element", ctx.getCurrentElement(),
                "Should handle namespaced element names");
    }

    @Test
    @DisplayName("Should handle XML declaration in full text")
    void testXmlDeclaration() {
        String xmlWithDeclaration = "<?xml version=\"1.0\"?><root></root>";
        CompletionContext ctx = new CompletionContext(
                xmlWithDeclaration,
                "",
                30,
                "root"
        );

        assertEquals(xmlWithDeclaration, ctx.getFullText(),
                "Should handle XML declaration");
    }

    @Test
    @DisplayName("Should handle nested parent elements")
    void testNestedParentElements() {
        context.setParentElement("grandparent/parent");

        assertEquals("grandparent/parent", context.getParentElement(),
                "Should handle nested parent path");
    }

    @Test
    @DisplayName("Should handle multiple document types")
    void testMultipleDocumentTypes() {
        String[] types = {"XML", "XSD", "XSLT", "Schematron", "HTML"};

        for (String type : types) {
            context.setDocumentType(type);
            assertEquals(type, context.getDocumentType(),
                    "Should handle document type: " + type);
        }
    }
}
