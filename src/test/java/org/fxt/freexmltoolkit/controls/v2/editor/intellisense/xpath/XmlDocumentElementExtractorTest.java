package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.xpath;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XmlDocumentElementExtractor.
 * Tests XML element and attribute extraction for XPath completions.
 */
@DisplayName("XmlDocumentElementExtractor Tests")
class XmlDocumentElementExtractorTest {

    private XmlDocumentElementExtractor extractor;
    private static final int TEST_SCORE = 100;

    @BeforeEach
    void setUp() {
        extractor = new XmlDocumentElementExtractor();
    }

    @Nested
    @DisplayName("Basic Extraction")
    class BasicExtractionTests {

        @Test
        @DisplayName("Should extract root element")
        void extractRootElement() {
            String xml = "<root></root>";
            extractor.extractFromXml(xml);

            Set<String> elements = extractor.getAllElements();
            assertTrue(elements.contains("root"), "Should extract root element");
        }

        @Test
        @DisplayName("Should extract nested elements")
        void extractNestedElements() {
            String xml = """
                    <root>
                        <child>
                            <grandchild/>
                        </child>
                    </root>
                    """;
            extractor.extractFromXml(xml);

            Set<String> elements = extractor.getAllElements();
            assertTrue(elements.contains("root"), "Should extract root");
            assertTrue(elements.contains("child"), "Should extract child");
            assertTrue(elements.contains("grandchild"), "Should extract grandchild");
        }

        @Test
        @DisplayName("Should extract attributes")
        void extractAttributes() {
            String xml = "<root id=\"1\" name=\"test\"></root>";
            extractor.extractFromXml(xml);

            Set<String> attributes = extractor.getAllAttributes();
            assertTrue(attributes.contains("id"), "Should extract 'id' attribute");
            assertTrue(attributes.contains("name"), "Should extract 'name' attribute");
        }

        @Test
        @DisplayName("Should track element count correctly")
        void trackElementCount() {
            String xml = """
                    <root>
                        <item/><item/><item/>
                    </root>
                    """;
            extractor.extractFromXml(xml);

            // 'item' should only be counted once as unique
            assertEquals(2, extractor.getElementCount(), "Should have 2 unique elements (root, item)");
        }
    }

    @Nested
    @DisplayName("Search and Filter")
    class SearchFilterTests {

        @Test
        @DisplayName("Should filter elements by prefix")
        void filterElements_byPrefix() {
            String xml = """
                    <root>
                        <chapter/><chapter/>
                        <chart/>
                        <section/>
                    </root>
                    """;
            extractor.extractFromXml(xml);

            List<String> results = extractor.searchElements("cha");
            assertEquals(2, results.size(), "Should find 'chapter' and 'chart'");
            assertTrue(results.contains("chapter"));
            assertTrue(results.contains("chart"));
        }

        @Test
        @DisplayName("Should filter attributes by prefix")
        void filterAttributes_byPrefix() {
            String xml = "<root id=\"1\" index=\"0\" name=\"test\"></root>";
            extractor.extractFromXml(xml);

            List<String> results = extractor.searchAttributes("i");
            assertEquals(2, results.size(), "Should find 'id' and 'index'");
        }

        @Test
        @DisplayName("Empty prefix should return all elements")
        void emptyPrefix_returnsAll() {
            String xml = "<root><child/><sibling/></root>";
            extractor.extractFromXml(xml);

            List<String> results = extractor.searchElements("");
            assertEquals(3, results.size(), "Should return all 3 elements");
        }

        @Test
        @DisplayName("Null prefix should return all elements")
        void nullPrefix_returnsAll() {
            String xml = "<root><child/></root>";
            extractor.extractFromXml(xml);

            List<String> results = extractor.searchElements(null);
            assertEquals(2, results.size(), "Should return all elements");
        }

        @Test
        @DisplayName("Case-insensitive search")
        void caseInsensitiveSearch() {
            String xml = "<ROOT><Child><SubChild/></Child></ROOT>";
            extractor.extractFromXml(xml);

            List<String> lowerResults = extractor.searchElements("child");
            List<String> upperResults = extractor.searchElements("CHILD");

            assertEquals(lowerResults.size(), upperResults.size(),
                    "Case should not affect search results");
        }
    }

    @Nested
    @DisplayName("Completion Items Generation")
    class CompletionItemsTests {

        @Test
        @DisplayName("Element completions should have correct type")
        void elementCompletions_haveCorrectType() {
            String xml = "<root><item/></root>";
            extractor.extractFromXml(xml);

            List<CompletionItem> items = extractor.getElementCompletions("", TEST_SCORE);

            items.forEach(item ->
                    assertEquals(CompletionItemType.ELEMENT, item.getType(),
                            "Element should have ELEMENT type"));
        }

        @Test
        @DisplayName("Attribute completions should have correct type")
        void attributeCompletions_haveCorrectType() {
            String xml = "<root id=\"1\" name=\"test\"></root>";
            extractor.extractFromXml(xml);

            List<CompletionItem> items = extractor.getAttributeCompletions("", TEST_SCORE);

            items.forEach(item ->
                    assertEquals(CompletionItemType.ATTRIBUTE, item.getType(),
                            "Attribute should have ATTRIBUTE type"));
        }

        @Test
        @DisplayName("Completions should include description")
        void completions_includeDescription() {
            String xml = "<root><item/></root>";
            extractor.extractFromXml(xml);

            List<CompletionItem> items = extractor.getElementCompletions("", TEST_SCORE);

            items.forEach(item -> {
                assertNotNull(item.getDescription(), "Should have description");
                assertFalse(item.getDescription().isEmpty(), "Description should not be empty");
            });
        }

        @Test
        @DisplayName("Prefix matches should have boosted score")
        void prefixMatches_haveBoostedScore() {
            String xml = "<root><item/><items/></root>";
            extractor.extractFromXml(xml);

            List<CompletionItem> items = extractor.getElementCompletions("item", TEST_SCORE);

            // All items should have at least the base score + boost
            items.forEach(item ->
                    assertTrue(item.getRelevanceScore() >= TEST_SCORE,
                            "Score should be at least base score"));
        }
    }

    @Nested
    @DisplayName("Element-Attribute Mapping")
    class ElementAttributeMappingTests {

        @Test
        @DisplayName("Should map attributes to their elements")
        void mapAttributesToElements() {
            String xml = """
                    <root>
                        <item id="1" name="first"/>
                        <item id="2" name="second"/>
                        <other type="test"/>
                    </root>
                    """;
            extractor.extractFromXml(xml);

            Set<String> itemAttrs = extractor.getAttributesForElement("item");
            assertTrue(itemAttrs.contains("id"), "item should have 'id' attribute");
            assertTrue(itemAttrs.contains("name"), "item should have 'name' attribute");
            assertFalse(itemAttrs.contains("type"), "item should not have 'type' attribute");

            Set<String> otherAttrs = extractor.getAttributesForElement("other");
            assertTrue(otherAttrs.contains("type"), "other should have 'type' attribute");
        }

        @Test
        @DisplayName("Should return empty set for unknown element")
        void unknownElement_returnsEmptySet() {
            String xml = "<root><item id=\"1\"/></root>";
            extractor.extractFromXml(xml);

            Set<String> attrs = extractor.getAttributesForElement("unknown");
            assertTrue(attrs.isEmpty(), "Unknown element should return empty set");
        }

        @Test
        @DisplayName("Null element should return empty set")
        void nullElement_returnsEmptySet() {
            String xml = "<root id=\"1\"/>";
            extractor.extractFromXml(xml);

            Set<String> attrs = extractor.getAttributesForElement(null);
            assertTrue(attrs.isEmpty(), "Null element should return empty set");
        }

        @Test
        @DisplayName("Element-specific attribute completions should be available")
        void elementSpecificAttributeCompletions() {
            String xml = """
                    <root>
                        <item id="1" name="test"/>
                        <other type="other"/>
                    </root>
                    """;
            extractor.extractFromXml(xml);

            List<CompletionItem> items = extractor.getAttributeCompletionsForElement("item", "", TEST_SCORE);

            assertTrue(items.size() >= 2, "Should have at least 2 attribute completions");
        }
    }

    @Nested
    @DisplayName("Namespace Handling")
    class NamespaceHandlingTests {

        @Test
        @DisplayName("Should extract namespace prefixes")
        void extractNamespacePrefixes() {
            String xml = """
                    <ns:root xmlns:ns="http://example.com/ns">
                        <ns:child/>
                    </ns:root>
                    """;
            extractor.extractFromXml(xml);

            Map<String, String> namespaces = extractor.getNamespaces();
            assertTrue(namespaces.containsKey("ns"), "Should extract 'ns' namespace prefix");
            assertEquals("http://example.com/ns", namespaces.get("ns"));
        }

        @Test
        @DisplayName("Should extract prefixed element names")
        void extractPrefixedElementNames() {
            String xml = """
                    <ns:root xmlns:ns="http://example.com/ns">
                        <ns:child/>
                    </ns:root>
                    """;
            extractor.extractFromXml(xml);

            Set<String> elements = extractor.getAllElements();
            assertTrue(elements.contains("ns:root"), "Should extract 'ns:root'");
            assertTrue(elements.contains("ns:child"), "Should extract 'ns:child'");
        }

        @Test
        @DisplayName("Should not include xmlns declarations as attributes")
        void excludeXmlnsDeclarations() {
            String xml = "<root xmlns=\"http://default.ns\" xmlns:ns=\"http://example.com\"/>";
            extractor.extractFromXml(xml);

            Set<String> attributes = extractor.getAllAttributes();
            assertFalse(attributes.stream().anyMatch(a -> a.startsWith("xmlns")),
                    "Should not include xmlns declarations");
        }
    }

    @Nested
    @DisplayName("Caching Behavior")
    class CachingBehaviorTests {

        @Test
        @DisplayName("Cache should be valid after extraction")
        void cache_validAfterExtraction() {
            String xml = "<root><item/></root>";
            extractor.extractFromXml(xml);

            assertTrue(extractor.isCacheValid(), "Cache should be valid after extraction");
        }

        @Test
        @DisplayName("Same content should use cache")
        void sameContent_usesCache() {
            String xml = "<root><item/></root>";
            extractor.extractFromXml(xml);

            // Extract again with same content
            extractor.extractFromXml(xml);

            assertTrue(extractor.isCacheValid(), "Cache should still be valid");
        }

        @Test
        @DisplayName("Different content should invalidate cache")
        void differentContent_invalidatesCache() {
            String xml1 = "<root><item/></root>";
            String xml2 = "<root><different/></root>";

            extractor.extractFromXml(xml1);
            // Make a defensive copy since getAllElements() returns an unmodifiable view
            Set<String> elements1 = new HashSet<>(extractor.getAllElements());

            extractor.extractFromXml(xml2);
            Set<String> elements2 = extractor.getAllElements();

            assertTrue(elements1.contains("item"), "First extraction should have 'item'");
            assertTrue(elements2.contains("different"), "Second extraction should have 'different'");
            assertFalse(elements2.contains("item"), "Second extraction should not have 'item'");
        }

        @Test
        @DisplayName("invalidateCache should mark cache as invalid")
        void invalidateCache_marksInvalid() {
            String xml = "<root/>";
            extractor.extractFromXml(xml);
            assertTrue(extractor.isCacheValid());

            extractor.invalidateCache();
            assertFalse(extractor.isCacheValid(), "Cache should be invalid after invalidation");
        }

        @Test
        @DisplayName("clear should reset all data")
        void clear_resetsAllData() {
            String xml = "<root id=\"1\"><item/></root>";
            extractor.extractFromXml(xml);

            extractor.clear();

            assertTrue(extractor.getAllElements().isEmpty(), "Elements should be empty");
            assertTrue(extractor.getAllAttributes().isEmpty(), "Attributes should be empty");
            assertFalse(extractor.isCacheValid(), "Cache should be invalid");
            assertEquals(0, extractor.getElementCount());
            assertEquals(0, extractor.getAttributeCount());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Invalid XML should not throw exception")
        void invalidXml_noException() {
            String invalidXml = "<root><unclosed>";

            assertDoesNotThrow(() -> extractor.extractFromXml(invalidXml),
                    "Should handle invalid XML gracefully");
        }

        @Test
        @DisplayName("Null XML should clear data")
        void nullXml_clearsData() {
            extractor.extractFromXml("<root/>");
            extractor.extractFromXml(null);

            assertTrue(extractor.getAllElements().isEmpty(), "Elements should be empty");
        }

        @Test
        @DisplayName("Empty XML should clear data")
        void emptyXml_clearsData() {
            extractor.extractFromXml("<root/>");
            extractor.extractFromXml("");

            assertTrue(extractor.getAllElements().isEmpty(), "Elements should be empty");
        }

        @Test
        @DisplayName("Blank XML should clear data")
        void blankXml_clearsData() {
            extractor.extractFromXml("<root/>");
            extractor.extractFromXml("   ");

            assertTrue(extractor.getAllElements().isEmpty(), "Elements should be empty");
        }

        @Test
        @DisplayName("Partial extraction on error should be available")
        void partialExtraction_onError() {
            // XML that is partially parseable (well-formed up to a point)
            String partialXml = "<root><valid/><another>";

            extractor.extractFromXml(partialXml);

            // Should have extracted elements before the error
            Set<String> elements = extractor.getAllElements();
            assertTrue(elements.contains("root"), "Should extract 'root' before error");
            assertTrue(elements.contains("valid"), "Should extract 'valid' before error");
        }
    }

    @Nested
    @DisplayName("Complex Documents")
    class ComplexDocumentTests {

        @Test
        @DisplayName("Should handle deeply nested structure")
        void handleDeeplyNested() {
            String xml = """
                    <level1>
                        <level2>
                            <level3>
                                <level4>
                                    <level5>content</level5>
                                </level4>
                            </level3>
                        </level2>
                    </level1>
                    """;
            extractor.extractFromXml(xml);

            assertEquals(5, extractor.getElementCount(), "Should extract all 5 levels");
        }

        @Test
        @DisplayName("Should handle many siblings")
        void handleManySiblings() {
            StringBuilder xml = new StringBuilder("<root>");
            for (int i = 0; i < 100; i++) {
                xml.append("<item").append(i).append("/>");
            }
            xml.append("</root>");

            extractor.extractFromXml(xml.toString());

            assertEquals(101, extractor.getElementCount(), "Should extract root + 100 items");
        }

        @Test
        @DisplayName("Should handle mixed content")
        void handleMixedContent() {
            String xml = """
                    <root>
                        Text content
                        <child>More text</child>
                        <![CDATA[CDATA content]]>
                        <!-- Comment -->
                    </root>
                    """;
            extractor.extractFromXml(xml);

            Set<String> elements = extractor.getAllElements();
            assertEquals(2, elements.size(), "Should extract root and child");
        }

        @Test
        @DisplayName("Should handle real-world XML structure")
        void handleRealWorldXml() {
            String xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <bookstore xmlns="http://example.com/bookstore">
                        <book isbn="123" category="fiction">
                            <title lang="en">The Great Gatsby</title>
                            <author>F. Scott Fitzgerald</author>
                            <price currency="USD">10.99</price>
                        </book>
                        <book isbn="456" category="non-fiction">
                            <title lang="de">Eine kurze Geschichte</title>
                            <author>Someone Else</author>
                            <price currency="EUR">15.50</price>
                        </book>
                    </bookstore>
                    """;
            extractor.extractFromXml(xml);

            Set<String> elements = extractor.getAllElements();
            assertTrue(elements.contains("bookstore"), "Should extract bookstore");
            assertTrue(elements.contains("book"), "Should extract book");
            assertTrue(elements.contains("title"), "Should extract title");
            assertTrue(elements.contains("author"), "Should extract author");
            assertTrue(elements.contains("price"), "Should extract price");

            Set<String> attributes = extractor.getAllAttributes();
            assertTrue(attributes.contains("isbn"), "Should extract isbn attribute");
            assertTrue(attributes.contains("category"), "Should extract category attribute");
            assertTrue(attributes.contains("lang"), "Should extract lang attribute");
            assertTrue(attributes.contains("currency"), "Should extract currency attribute");
        }
    }

    @Nested
    @DisplayName("Tree Structure Tests")
    class TreeStructureTests {

        @Test
        @DisplayName("Should extract root element")
        void extractRootElement() {
            String xml = """
                    <bookstore>
                        <book/>
                    </bookstore>
                    """;
            extractor.extractFromXml(xml);

            assertEquals("bookstore", extractor.getRootElement(), "Should identify root element");
        }

        @Test
        @DisplayName("Should track parent-child relationships")
        void trackParentChildRelationships() {
            String xml = """
                    <bookstore>
                        <book>
                            <title/>
                            <author/>
                            <price/>
                        </book>
                        <magazine>
                            <title/>
                        </magazine>
                    </bookstore>
                    """;
            extractor.extractFromXml(xml);

            Set<String> bookstoreChildren = extractor.getChildElements("bookstore");
            assertTrue(bookstoreChildren.contains("book"), "bookstore should have book child");
            assertTrue(bookstoreChildren.contains("magazine"), "bookstore should have magazine child");
            assertFalse(bookstoreChildren.contains("title"), "title should not be direct child of bookstore");

            Set<String> bookChildren = extractor.getChildElements("book");
            assertTrue(bookChildren.contains("title"), "book should have title child");
            assertTrue(bookChildren.contains("author"), "book should have author child");
            assertTrue(bookChildren.contains("price"), "book should have price child");
        }

        @Test
        @DisplayName("Should return empty set for leaf elements")
        void leafElementsHaveNoChildren() {
            String xml = """
                    <root>
                        <leaf>text</leaf>
                    </root>
                    """;
            extractor.extractFromXml(xml);

            Set<String> leafChildren = extractor.getChildElements("leaf");
            assertTrue(leafChildren.isEmpty(), "Leaf element should have no children");
        }

        @Test
        @DisplayName("Should return empty set for unknown element")
        void unknownElementReturnsEmptyChildren() {
            String xml = "<root><child/></root>";
            extractor.extractFromXml(xml);

            Set<String> children = extractor.getChildElements("nonexistent");
            assertTrue(children.isEmpty(), "Unknown element should return empty set");
        }

        @Test
        @DisplayName("Should return empty set for null element")
        void nullElementReturnsEmptyChildren() {
            String xml = "<root><child/></root>";
            extractor.extractFromXml(xml);

            Set<String> children = extractor.getChildElements(null);
            assertTrue(children.isEmpty(), "Null element should return empty set");
        }

        @Test
        @DisplayName("Should search child elements with prefix")
        void searchChildElementsWithPrefix() {
            String xml = """
                    <root>
                        <title/>
                        <type/>
                        <author/>
                    </root>
                    """;
            extractor.extractFromXml(xml);

            List<String> tChildren = extractor.searchChildElements("root", "t");
            assertEquals(2, tChildren.size(), "Should find 2 children starting with 't'");
            assertTrue(tChildren.contains("title"));
            assertTrue(tChildren.contains("type"));
        }

        @Test
        @DisplayName("Root element completions should return only root")
        void rootElementCompletions() {
            String xml = """
                    <bookstore>
                        <book/>
                    </bookstore>
                    """;
            extractor.extractFromXml(xml);

            List<CompletionItem> rootCompletions = extractor.getChildElementCompletions(null, "", TEST_SCORE);
            assertEquals(1, rootCompletions.size(), "Should return only root element");
            assertEquals("bookstore", rootCompletions.get(0).getLabel());
        }

        @Test
        @DisplayName("Child element completions should return valid children")
        void childElementCompletions() {
            String xml = """
                    <bookstore>
                        <book>
                            <title/>
                            <author/>
                        </book>
                    </bookstore>
                    """;
            extractor.extractFromXml(xml);

            List<CompletionItem> bookChildren = extractor.getChildElementCompletions("book", "", TEST_SCORE);
            assertEquals(2, bookChildren.size(), "Should return 2 children of book");
            assertTrue(bookChildren.stream().anyMatch(c -> c.getLabel().equals("title")));
            assertTrue(bookChildren.stream().anyMatch(c -> c.getLabel().equals("author")));
        }

        @Test
        @DisplayName("Child element completions with prefix filter")
        void childElementCompletionsWithPrefix() {
            String xml = """
                    <root>
                        <title/>
                        <type/>
                        <author/>
                    </root>
                    """;
            extractor.extractFromXml(xml);

            List<CompletionItem> tChildren = extractor.getChildElementCompletions("root", "t", TEST_SCORE);
            assertEquals(2, tChildren.size(), "Should return 2 children starting with 't'");
        }
    }
}
