package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.xpath;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link XsdSchemaElementExtractor}.
 */
class XsdSchemaElementExtractorTest {

    private XsdSchemaElementExtractor extractor;

    private static final String SIMPLE_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="person" type="PersonType"/>
                <xs:element name="address" type="AddressType"/>

                <xs:complexType name="PersonType">
                    <xs:sequence>
                        <xs:element name="firstName" type="xs:string"/>
                        <xs:element name="lastName" type="xs:string"/>
                    </xs:sequence>
                    <xs:attribute name="id" type="xs:integer"/>
                </xs:complexType>

                <xs:complexType name="AddressType">
                    <xs:sequence>
                        <xs:element name="street" type="xs:string"/>
                        <xs:element name="city" type="xs:string"/>
                    </xs:sequence>
                </xs:complexType>

                <xs:simpleType name="EmailType">
                    <xs:restriction base="xs:string">
                        <xs:pattern value="[^@]+@[^@]+"/>
                    </xs:restriction>
                </xs:simpleType>

                <xs:group name="ContactInfo">
                    <xs:sequence>
                        <xs:element name="phone" type="xs:string"/>
                        <xs:element name="email" type="EmailType"/>
                    </xs:sequence>
                </xs:group>

                <xs:attributeGroup name="CommonAttributes">
                    <xs:attribute name="lang" type="xs:language"/>
                </xs:attributeGroup>
            </xs:schema>
            """;

    @BeforeEach
    void setUp() {
        extractor = new XsdSchemaElementExtractor();
    }

    @Nested
    @DisplayName("Extraction Tests")
    class ExtractionTests {

        @Test
        @DisplayName("Should extract global element names")
        void shouldExtractGlobalElementNames() {
            extractor.extractFromXsd(SIMPLE_XSD);

            Set<String> elements = extractor.getAllElementNames();

            assertTrue(elements.contains("person"));
            assertTrue(elements.contains("address"));
            // Note: nested elements (firstName, lastName, street, city) should also be extracted
            assertTrue(elements.contains("firstName"));
            assertTrue(elements.contains("lastName"));
        }

        @Test
        @DisplayName("Should extract complex type names")
        void shouldExtractComplexTypeNames() {
            extractor.extractFromXsd(SIMPLE_XSD);

            Set<String> complexTypes = extractor.getAllComplexTypeNames();

            assertTrue(complexTypes.contains("PersonType"));
            assertTrue(complexTypes.contains("AddressType"));
            assertEquals(2, complexTypes.size());
        }

        @Test
        @DisplayName("Should extract simple type names")
        void shouldExtractSimpleTypeNames() {
            extractor.extractFromXsd(SIMPLE_XSD);

            Set<String> simpleTypes = extractor.getAllSimpleTypeNames();

            assertTrue(simpleTypes.contains("EmailType"));
            assertEquals(1, simpleTypes.size());
        }

        @Test
        @DisplayName("Should extract attribute names")
        void shouldExtractAttributeNames() {
            extractor.extractFromXsd(SIMPLE_XSD);

            Set<String> attributes = extractor.getAllAttributeNames();

            assertTrue(attributes.contains("id"));
            assertTrue(attributes.contains("lang"));
        }

        @Test
        @DisplayName("Should handle empty XSD content")
        void shouldHandleEmptyContent() {
            extractor.extractFromXsd("");

            assertEquals(0, extractor.getTotalCount());
            assertFalse(extractor.isCacheValid());
        }

        @Test
        @DisplayName("Should handle null XSD content")
        void shouldHandleNullContent() {
            extractor.extractFromXsd(null);

            assertEquals(0, extractor.getTotalCount());
        }

        @Test
        @DisplayName("Should cache results")
        void shouldCacheResults() {
            extractor.extractFromXsd(SIMPLE_XSD);
            assertTrue(extractor.isCacheValid());

            int count1 = extractor.getTotalCount();

            // Extract again with same content - should use cache
            extractor.extractFromXsd(SIMPLE_XSD);
            assertTrue(extractor.isCacheValid());

            int count2 = extractor.getTotalCount();

            assertEquals(count1, count2);
        }

        @Test
        @DisplayName("Should invalidate cache on different content")
        void shouldInvalidateCacheOnDifferentContent() {
            extractor.extractFromXsd(SIMPLE_XSD);
            int count1 = extractor.getTotalCount();

            String differentXsd = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                        <xs:element name="different" type="xs:string"/>
                    </xs:schema>
                    """;

            extractor.extractFromXsd(differentXsd);
            int count2 = extractor.getTotalCount();

            assertNotEquals(count1, count2);
        }
    }

    @Nested
    @DisplayName("Completion Tests")
    class CompletionTests {

        @BeforeEach
        void extractXsd() {
            extractor.extractFromXsd(SIMPLE_XSD);
        }

        @Test
        @DisplayName("Should return element completions matching prefix")
        void shouldReturnElementCompletionsMatchingPrefix() {
            List<CompletionItem> completions = extractor.getSchemaElementCompletions("per", 100);

            assertTrue(completions.stream().anyMatch(c -> c.getLabel().equals("person")));
            assertFalse(completions.stream().anyMatch(c -> c.getLabel().equals("address")));
        }

        @Test
        @DisplayName("Should return all element completions with null prefix")
        void shouldReturnAllElementCompletionsWithNullPrefix() {
            List<CompletionItem> completions = extractor.getSchemaElementCompletions(null, 100);

            assertTrue(completions.size() >= 2);
            assertTrue(completions.stream().anyMatch(c -> c.getLabel().equals("person")));
            assertTrue(completions.stream().anyMatch(c -> c.getLabel().equals("address")));
        }

        @Test
        @DisplayName("Should return type completions")
        void shouldReturnTypeCompletions() {
            List<CompletionItem> completions = extractor.getSchemaTypeCompletions("Person", 100);

            assertTrue(completions.stream().anyMatch(c -> c.getLabel().equals("PersonType")));
            assertFalse(completions.stream().anyMatch(c -> c.getLabel().equals("AddressType")));
        }

        @Test
        @DisplayName("Should return XSD attribute completions")
        void shouldReturnXsdAttributeCompletions() {
            List<CompletionItem> completions = extractor.getXsdAttributeCompletions("na", 100);

            assertTrue(completions.stream().anyMatch(c -> c.getInsertText().equals("name")));
        }

        @Test
        @DisplayName("Should return builtin type completions")
        void shouldReturnBuiltinTypeCompletions() {
            List<CompletionItem> completions = extractor.getBuiltinTypeCompletions("string", 100);

            assertTrue(completions.stream().anyMatch(c -> c.getLabel().equals("xs:string")));
        }

        @Test
        @DisplayName("Should return group completions")
        void shouldReturnGroupCompletions() {
            List<CompletionItem> completions = extractor.getGroupCompletions("Contact", 100);

            assertTrue(completions.stream().anyMatch(c -> c.getLabel().equals("ContactInfo")));
        }
    }

    @Nested
    @DisplayName("Clear and Invalidate Tests")
    class ClearAndInvalidateTests {

        @Test
        @DisplayName("Should clear all data")
        void shouldClearAllData() {
            extractor.extractFromXsd(SIMPLE_XSD);
            assertTrue(extractor.getTotalCount() > 0);

            extractor.clear();

            assertEquals(0, extractor.getTotalCount());
            assertFalse(extractor.isCacheValid());
        }

        @Test
        @DisplayName("Should invalidate cache")
        void shouldInvalidateCache() {
            extractor.extractFromXsd(SIMPLE_XSD);
            assertTrue(extractor.isCacheValid());

            extractor.invalidateCache();

            assertFalse(extractor.isCacheValid());
        }
    }
}
