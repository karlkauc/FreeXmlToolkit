package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the smart filtering logic in XsdCompletionProvider.
 * Tests the sibling counting and XPath extraction methods.
 */
class XsdCompletionProviderFilteringTest {

    private XsdCompletionProvider provider;

    @BeforeEach
    void setUp() {
        // Create provider with null schema provider for testing helper methods
        provider = new XsdCompletionProvider(new DummySchemaProvider());
    }

    @Test
    void testExtractLastElementFromXPath() throws Exception {
        Method method = XsdCompletionProvider.class.getDeclaredMethod("extractLastElementFromXPath", String.class);
        method.setAccessible(true);

        // Test normal XPath
        assertEquals("Future", method.invoke(provider, "/FundsXML4/AssetDetails/Future"));

        // Test single element
        assertEquals("Root", method.invoke(provider, "/Root"));

        // Test without leading slash
        assertEquals("Element", method.invoke(provider, "Parent/Element"));

        // Test single element without slash
        assertEquals("Element", method.invoke(provider, "Element"));

        // Test empty string
        assertNull(method.invoke(provider, ""));

        // Test null
        assertNull(method.invoke(provider, (String) null));

        // Test trailing slash
        assertNull(method.invoke(provider, "/Root/"));
    }

    @Test
    void testCountExistingSiblings() throws Exception {
        Method method = XsdCompletionProvider.class.getDeclaredMethod("countExistingSiblings", String.class, String.class);
        method.setAccessible(true);

        // Test with Type element already present
        String xml1 = """
            <AssetDetails>
                <Future>
                    <Type>BF</Type>
                    <""";

        @SuppressWarnings("unchecked")
        Map<String, Integer> counts1 = (Map<String, Integer>) method.invoke(provider, xml1, "Future");
        assertEquals(1, counts1.getOrDefault("Type", 0), "Type should be counted once");

        // Test with multiple elements
        String xml2 = """
            <Parent>
                <Child1>A</Child1>
                <Child2>B</Child2>
                <Child1>C</Child1>
                <""";

        @SuppressWarnings("unchecked")
        Map<String, Integer> counts2 = (Map<String, Integer>) method.invoke(provider, xml2, "Parent");
        assertEquals(2, counts2.getOrDefault("Child1", 0), "Child1 should be counted twice");
        assertEquals(1, counts2.getOrDefault("Child2", 0), "Child2 should be counted once");

        // Test with nested elements - should only count direct children
        String xml3 = """
            <Root>
                <Parent>
                    <Child>
                        <Nested>X</Nested>
                    </Child>
                    <""";

        @SuppressWarnings("unchecked")
        Map<String, Integer> counts3 = (Map<String, Integer>) method.invoke(provider, xml3, "Parent");
        assertEquals(1, counts3.getOrDefault("Child", 0), "Child should be counted once");
        assertEquals(0, counts3.getOrDefault("Nested", 0), "Nested should not be counted (not direct child)");

        // Test with self-closing tags
        String xml4 = """
            <Root>
                <Parent>
                    <Empty/>
                    <Another/>
                    <""";

        @SuppressWarnings("unchecked")
        Map<String, Integer> counts4 = (Map<String, Integer>) method.invoke(provider, xml4, "Parent");
        assertEquals(1, counts4.getOrDefault("Empty", 0), "Empty should be counted once");
        assertEquals(1, counts4.getOrDefault("Another", 0), "Another should be counted once");

        // Test with comments - should ignore elements in comments
        String xml5 = """
            <Root>
                <Parent>
                    <!-- <Commented>X</Commented> -->
                    <Real>Y</Real>
                    <""";

        @SuppressWarnings("unchecked")
        Map<String, Integer> counts5 = (Map<String, Integer>) method.invoke(provider, xml5, "Parent");
        assertEquals(0, counts5.getOrDefault("Commented", 0), "Commented should not be counted");
        assertEquals(1, counts5.getOrDefault("Real", 0), "Real should be counted once");
    }

    @Test
    void testCountExistingSiblingsWithRealWorldExample() throws Exception {
        Method method = XsdCompletionProvider.class.getDeclaredMethod("countExistingSiblings", String.class, String.class);
        method.setAccessible(true);

        // Real-world FundsXML example
        String fundsXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <FundsXML4>
                <AssetDetails>
                    <Future>
                        <Type>BF</Type>
                        <Listing>
                            <ISIN>DE000123456</ISIN>
                        </Listing>
                        <BasePrice>
                            <Currency>EUR</Currency>
                        </BasePrice>
                        <""";

        @SuppressWarnings("unchecked")
        Map<String, Integer> counts = (Map<String, Integer>) method.invoke(provider, fundsXml, "Future");

        assertEquals(1, counts.getOrDefault("Type", 0), "Type should be counted once");
        assertEquals(1, counts.getOrDefault("Listing", 0), "Listing should be counted once");
        assertEquals(1, counts.getOrDefault("BasePrice", 0), "BasePrice should be counted once");

        // These should not be counted because they are nested inside Listing/BasePrice
        assertEquals(0, counts.getOrDefault("ISIN", 0), "ISIN should not be counted (nested in Listing)");
        assertEquals(0, counts.getOrDefault("Currency", 0), "Currency should not be counted (nested in BasePrice)");
    }

    @Test
    void testCountExistingSiblingsEmptyParent() throws Exception {
        Method method = XsdCompletionProvider.class.getDeclaredMethod("countExistingSiblings", String.class, String.class);
        method.setAccessible(true);

        // Test with empty parent - no children yet
        String xml = """
            <Root>
                <Parent>
                    <""";

        @SuppressWarnings("unchecked")
        Map<String, Integer> counts = (Map<String, Integer>) method.invoke(provider, xml, "Parent");
        assertTrue(counts.isEmpty(), "Counts should be empty for parent with no children");
    }

    @Test
    void testCountExistingSiblingsParentNotFound() throws Exception {
        Method method = XsdCompletionProvider.class.getDeclaredMethod("countExistingSiblings", String.class, String.class);
        method.setAccessible(true);

        String xml = """
            <Root>
                <Other>
                    <Child>A</Child>
                </Other>
                <""";

        @SuppressWarnings("unchecked")
        Map<String, Integer> counts = (Map<String, Integer>) method.invoke(provider, xml, "NonExistent");
        assertTrue(counts.isEmpty(), "Counts should be empty when parent not found");
    }

    /**
     * Dummy schema provider for testing helper methods.
     */
    private static class DummySchemaProvider implements org.fxt.freexmltoolkit.controls.v2.editor.services.XmlSchemaProvider {
        @Override
        public boolean hasSchema() {
            return false;
        }

        @Override
        public org.fxt.freexmltoolkit.domain.XsdDocumentationData getXsdDocumentationData() {
            return null;
        }

        @Override
        public String getXsdFilePath() {
            return null;
        }

        @Override
        public org.fxt.freexmltoolkit.domain.XsdExtendedElement findBestMatchingElement(String xpath) {
            return null;
        }
    }
}
