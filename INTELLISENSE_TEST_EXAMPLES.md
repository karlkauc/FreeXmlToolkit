# IntelliSense System Test Examples

The IntelliSense system has **4% test coverage (1/27 files)** and is a critical feature. This document provides test examples for key components.

---

## 1. FuzzySearchTest.java

**Purpose:** Test fuzzy matching algorithm for completion suggestions

```java
package org.fxt.freexmltoolkit.controls.intellisense;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FuzzySearchTest {

    private FuzzySearch fuzzySearch;

    @BeforeEach
    void setUp() {
        fuzzySearch = new FuzzySearch();
    }

    @Test
    @DisplayName("Should match exact string")
    void testExactMatch() {
        assertTrue(fuzzySearch.matches("element", "element"));
        assertEquals(100, fuzzySearch.score("element", "element"));
    }

    @Test
    @DisplayName("Should match prefix")
    void testPrefixMatch() {
        assertTrue(fuzzySearch.matches("ele", "element"));
        assertTrue(fuzzySearch.score("ele", "element") > 80);
    }

    @Test
    @DisplayName("Should match abbreviation")
    void testAbbreviationMatch() {
        // Type "ele" should match "element"
        assertTrue(fuzzySearch.matches("ele", "element"));

        // Type "xselem" should match "xs:element"
        assertTrue(fuzzySearch.matches("xselem", "xs:element"));
    }

    @Test
    @DisplayName("Should match camelCase abbreviations")
    void testCamelCaseMatch() {
        // "MC" should match "myComplexType"
        assertTrue(fuzzySearch.matches("MC", "myComplexType"));

        // "mCT" should match "myComplexType"
        assertTrue(fuzzySearch.matches("mCT", "myComplexType"));
    }

    @Test
    @DisplayName("Should be case insensitive")
    void testCaseInsensitive() {
        assertTrue(fuzzySearch.matches("ELEM", "element"));
        assertTrue(fuzzySearch.matches("elem", "ELEMENT"));
        assertTrue(fuzzySearch.matches("ELem", "ElEmEnT"));
    }

    @Test
    @DisplayName("Should not match unrelated strings")
    void testNoMatch() {
        assertFalse(fuzzySearch.matches("xyz", "element"));
        assertFalse(fuzzySearch.matches("abc", "element"));
    }

    @Test
    @DisplayName("Should score matches by quality")
    void testScoring() {
        int exactScore = fuzzySearch.score("element", "element");
        int prefixScore = fuzzySearch.score("elem", "element");
        int abbrevScore = fuzzySearch.score("elt", "element");
        int partialScore = fuzzySearch.score("emen", "element");

        // Exact match should score highest
        assertTrue(exactScore > prefixScore);

        // Prefix should score higher than abbreviation
        assertTrue(prefixScore > abbrevScore);

        // Continuous matches score higher than scattered
        assertTrue(prefixScore > partialScore);
    }

    @Test
    @DisplayName("Should rank results by relevance")
    void testRanking() {
        List<String> candidates = Arrays.asList(
            "element",
            "xs:element",
            "myElement",
            "complexElement",
            "elementRef"
        );

        List<String> results = fuzzySearch.rankMatches("elem", candidates);

        // "element" should rank first (exact prefix)
        assertEquals("element", results.get(0));

        // "xs:element" should rank high (contains exact)
        assertTrue(results.indexOf("xs:element") < 3);
    }

    @Test
    @DisplayName("Should handle special characters in namespace")
    void testNamespaceHandling() {
        assertTrue(fuzzySearch.matches("xse", "xs:element"));
        assertTrue(fuzzySearch.matches("xs:e", "xs:element"));
    }

    @Test
    @DisplayName("Should handle empty and null inputs")
    void testEdgeCases() {
        assertFalse(fuzzySearch.matches("", "element"));
        assertFalse(fuzzySearch.matches("elem", ""));
        assertFalse(fuzzySearch.matches(null, "element"));
        assertFalse(fuzzySearch.matches("elem", null));
    }
}
```

---

## 2. CompletionContextTest.java

**Purpose:** Test context-sensitive completion logic

```java
package org.fxt.freexmltoolkit.controls.intellisense;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class CompletionContextTest {

    private CompletionContext context;

    @BeforeEach
    void setUp() {
        context = new CompletionContext();
    }

    @Test
    @DisplayName("Should detect root element context")
    void testRootElementContext() {
        String xml = "<|";
        int cursorPos = 1;

        CompletionContext.Type type = context.determineContext(xml, cursorPos);

        assertEquals(CompletionContext.Type.ROOT_ELEMENT, type);
    }

    @Test
    @DisplayName("Should detect child element context")
    void testChildElementContext() {
        String xml = """
            <root>
              <|
            </root>
            """;
        int cursorPos = xml.indexOf("<|") + 1;

        CompletionContext.Type type = context.determineContext(xml, cursorPos);

        assertEquals(CompletionContext.Type.CHILD_ELEMENT, type);
    }

    @Test
    @DisplayName("Should detect attribute context")
    void testAttributeContext() {
        String xml = "<element |";
        int cursorPos = 9;

        CompletionContext.Type type = context.determineContext(xml, cursorPos);

        assertEquals(CompletionContext.Type.ATTRIBUTE, type);
    }

    @Test
    @DisplayName("Should detect attribute value context")
    void testAttributeValueContext() {
        String xml = "<element type=\"|\"";
        int cursorPos = xml.indexOf("|");

        CompletionContext.Type type = context.determineContext(xml, cursorPos);

        assertEquals(CompletionContext.Type.ATTRIBUTE_VALUE, type);
    }

    @Test
    @DisplayName("Should detect closing tag context")
    void testClosingTagContext() {
        String xml = """
            <element>
              content
            </|
            """;
        int cursorPos = xml.indexOf("</|") + 2;

        CompletionContext.Type type = context.determineContext(xml, cursorPos);

        assertEquals(CompletionContext.Type.CLOSING_TAG, type);
    }

    @Test
    @DisplayName("Should extract parent element name")
    void testParentElementExtraction() {
        String xml = """
            <root>
              <child>
                <|
              </child>
            </root>
            """;
        int cursorPos = xml.indexOf("<|") + 1;

        String parent = context.getParentElementName(xml, cursorPos);

        assertEquals("child", parent);
    }

    @Test
    @DisplayName("Should extract current element name")
    void testCurrentElementExtraction() {
        String xml = "<element type=\"|\"";
        int cursorPos = xml.indexOf("|");

        String element = context.getCurrentElementName(xml, cursorPos);

        assertEquals("element", element);
    }

    @Test
    @DisplayName("Should extract current attribute name")
    void testCurrentAttributeExtraction() {
        String xml = "<element type=\"|\"";
        int cursorPos = xml.indexOf("|");

        String attribute = context.getCurrentAttributeName(xml, cursorPos);

        assertEquals("type", attribute);
    }

    @Test
    @DisplayName("Should get element path from root")
    void testElementPath() {
        String xml = """
            <root>
              <level1>
                <level2>
                  <|
                </level2>
              </level1>
            </root>
            """;
        int cursorPos = xml.indexOf("<|") + 1;

        List<String> path = context.getElementPath(xml, cursorPos);

        assertEquals(Arrays.asList("root", "level1", "level2"), path);
    }

    @Test
    @DisplayName("Should detect namespace prefix")
    void testNamespacePrefix() {
        String xml = "<xs:element |";
        int cursorPos = 12;

        String prefix = context.getNamespacePrefix(xml, cursorPos);

        assertEquals("xs", prefix);
    }

    @Test
    @DisplayName("Should handle incomplete tags")
    void testIncompleteTags() {
        String xml = "<element\n  type=\"st|";
        int cursorPos = xml.indexOf("|");

        CompletionContext.Type type = context.determineContext(xml, cursorPos);

        assertEquals(CompletionContext.Type.ATTRIBUTE_VALUE, type);
    }

    @Test
    @DisplayName("Should handle CDATA sections")
    void testCDATAHandling() {
        String xml = """
            <element>
              <![CDATA[
                <not-a-tag>
              ]]>
              <|
            </element>
            """;
        int cursorPos = xml.indexOf("<|") + 1;

        CompletionContext.Type type = context.determineContext(xml, cursorPos);

        assertEquals(CompletionContext.Type.CHILD_ELEMENT, type);
        assertEquals("element", context.getParentElementName(xml, cursorPos));
    }

    @Test
    @DisplayName("Should handle comments")
    void testCommentHandling() {
        String xml = """
            <element>
              <!-- This is a comment with <fake> tags -->
              <|
            </element>
            """;
        int cursorPos = xml.indexOf("<|") + 1;

        assertEquals("element", context.getParentElementName(xml, cursorPos));
    }
}
```

---

## 3. NamespaceResolverTest.java

**Purpose:** Test namespace resolution and prefix mapping

```java
package org.fxt.freexmltoolkit.controls.intellisense;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NamespaceResolverTest {

    private NamespaceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new NamespaceResolver();
    }

    @Test
    @DisplayName("Should extract namespace declarations")
    void testExtractNamespaces() {
        String xml = """
            <root xmlns="http://example.com/default"
                  xmlns:xs="http://www.w3.org/2001/XMLSchema"
                  xmlns:custom="http://example.com/custom">
            </root>
            """;

        Map<String, String> namespaces = resolver.extractNamespaces(xml);

        assertEquals(3, namespaces.size());
        assertEquals("http://example.com/default", namespaces.get(""));
        assertEquals("http://www.w3.org/2001/XMLSchema", namespaces.get("xs"));
        assertEquals("http://example.com/custom", namespaces.get("custom"));
    }

    @Test
    @DisplayName("Should resolve prefix to namespace URI")
    void testResolvePrefix() {
        String xml = """
            <root xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="test"/>
            </root>
            """;

        resolver.parseDocument(xml);

        assertEquals("http://www.w3.org/2001/XMLSchema", resolver.getNamespaceURI("xs"));
    }

    @Test
    @DisplayName("Should get prefix for namespace URI")
    void testGetPrefix() {
        String xml = """
            <root xmlns:xs="http://www.w3.org/2001/XMLSchema">
            </root>
            """;

        resolver.parseDocument(xml);

        assertEquals("xs", resolver.getPrefix("http://www.w3.org/2001/XMLSchema"));
    }

    @Test
    @DisplayName("Should handle default namespace")
    void testDefaultNamespace() {
        String xml = """
            <root xmlns="http://example.com/default">
            </root>
            """;

        resolver.parseDocument(xml);

        assertEquals("http://example.com/default", resolver.getDefaultNamespace());
    }

    @Test
    @DisplayName("Should handle nested namespace scopes")
    void testNestedNamespaces() {
        String xml = """
            <root xmlns:a="http://a.com">
              <child xmlns:a="http://b.com">
                <!-- Here 'a' maps to http://b.com -->
              </child>
              <!-- Here 'a' maps to http://a.com -->
            </root>
            """;

        resolver.parseDocument(xml);

        // At root level
        int rootPos = xml.indexOf("<root");
        assertEquals("http://a.com", resolver.getNamespaceURI("a", rootPos));

        // Inside child
        int childPos = xml.indexOf("<!-- Here");
        assertEquals("http://b.com", resolver.getNamespaceURI("a", childPos));
    }

    @Test
    @DisplayName("Should validate namespace prefix")
    void testValidatePrefix() {
        String xml = """
            <root xmlns:xs="http://www.w3.org/2001/XMLSchema">
            </root>
            """;

        resolver.parseDocument(xml);

        assertTrue(resolver.isValidPrefix("xs"));
        assertFalse(resolver.isValidPrefix("unknown"));
    }

    @Test
    @DisplayName("Should suggest namespace prefixes")
    void testSuggestPrefixes() {
        String xml = """
            <root xmlns="http://example.com/default"
                  xmlns:xs="http://www.w3.org/2001/XMLSchema"
                  xmlns:custom="http://example.com/custom">
            </root>
            """;

        resolver.parseDocument(xml);

        List<String> prefixes = resolver.getAllPrefixes();

        assertTrue(prefixes.contains("xs"));
        assertTrue(prefixes.contains("custom"));
    }

    @Test
    @DisplayName("Should handle XSD namespace specifically")
    void testXsdNamespace() {
        String xml = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
            </xs:schema>
            """;

        resolver.parseDocument(xml);

        assertTrue(resolver.hasXsdNamespace());
        assertEquals("xs", resolver.getXsdPrefix());
    }

    @Test
    @DisplayName("Should auto-suggest common namespace prefixes")
    void testCommonNamespaceSuggestions() {
        // When no namespace declared, suggest common ones
        List<NamespaceResolver.NamespaceSuggestion> suggestions =
            resolver.suggestNamespaces("xs");

        assertTrue(suggestions.stream()
            .anyMatch(s -> s.prefix().equals("xs") &&
                          s.uri().equals("http://www.w3.org/2001/XMLSchema")));
    }
}
```

---

## 4. MultiSchemaManagerTest.java

**Purpose:** Test multiple schema loading and management

```java
package org.fxt.freexmltoolkit.controls.intellisense;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class MultiSchemaManagerTest {

    private MultiSchemaManager manager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        manager = new MultiSchemaManager();
    }

    @Test
    @DisplayName("Should load single schema")
    void testLoadSingleSchema() throws Exception {
        String xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root" type="xs:string"/>
            </xs:schema>
            """;

        Path schemaFile = tempDir.resolve("schema.xsd");
        Files.writeString(schemaFile, xsd);

        manager.loadSchema(schemaFile.toFile());

        assertTrue(manager.hasSchemas());
        assertEquals(1, manager.getSchemaCount());
    }

    @Test
    @DisplayName("Should load multiple schemas")
    void testLoadMultipleSchemas() throws Exception {
        String xsd1 = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="http://schema1.com">
              <xs:element name="element1"/>
            </xs:schema>
            """;

        String xsd2 = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="http://schema2.com">
              <xs:element name="element2"/>
            </xs:schema>
            """;

        Path schema1 = tempDir.resolve("schema1.xsd");
        Path schema2 = tempDir.resolve("schema2.xsd");

        Files.writeString(schema1, xsd1);
        Files.writeString(schema2, xsd2);

        manager.loadSchema(schema1.toFile());
        manager.loadSchema(schema2.toFile());

        assertEquals(2, manager.getSchemaCount());
    }

    @Test
    @DisplayName("Should get elements from all schemas")
    void testGetAllElements() throws Exception {
        String xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="element1" type="xs:string"/>
              <xs:element name="element2" type="xs:int"/>
            </xs:schema>
            """;

        Path schemaFile = tempDir.resolve("schema.xsd");
        Files.writeString(schemaFile, xsd);

        manager.loadSchema(schemaFile.toFile());

        List<String> elements = manager.getAllElementNames();

        assertTrue(elements.contains("element1"));
        assertTrue(elements.contains("element2"));
    }

    @Test
    @DisplayName("Should get element by namespace")
    void testGetElementByNamespace() throws Exception {
        String xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="http://example.com">
              <xs:element name="myElement"/>
            </xs:schema>
            """;

        Path schemaFile = tempDir.resolve("schema.xsd");
        Files.writeString(schemaFile, xsd);

        manager.loadSchema(schemaFile.toFile());

        List<String> elements = manager.getElementsByNamespace("http://example.com");

        assertTrue(elements.contains("myElement"));
    }

    @Test
    @DisplayName("Should resolve imports")
    void testResolveImports() throws Exception {
        String mainSchema = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:imp="http://imported.com">
              <xs:import namespace="http://imported.com" schemaLocation="imported.xsd"/>
              <xs:element name="mainElement"/>
            </xs:schema>
            """;

        String importedSchema = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="http://imported.com">
              <xs:element name="importedElement"/>
            </xs:schema>
            """;

        Path mainFile = tempDir.resolve("main.xsd");
        Path importedFile = tempDir.resolve("imported.xsd");

        Files.writeString(mainFile, mainSchema);
        Files.writeString(importedFile, importedSchema);

        manager.loadSchema(mainFile.toFile());

        // Should automatically load imported schema
        List<String> allElements = manager.getAllElementNames();

        assertTrue(allElements.contains("mainElement"));
        assertTrue(allElements.contains("importedElement"));
    }

    @Test
    @DisplayName("Should clear all schemas")
    void testClearSchemas() throws Exception {
        String xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="element1"/>
            </xs:schema>
            """;

        Path schemaFile = tempDir.resolve("schema.xsd");
        Files.writeString(schemaFile, xsd);

        manager.loadSchema(schemaFile.toFile());
        assertTrue(manager.hasSchemas());

        manager.clear();

        assertFalse(manager.hasSchemas());
        assertEquals(0, manager.getSchemaCount());
    }

    @Test
    @DisplayName("Should get valid child elements for context")
    void testGetValidChildElements() throws Exception {
        String xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="child1" type="xs:string"/>
                    <xs:element name="child2" type="xs:int"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

        Path schemaFile = tempDir.resolve("schema.xsd");
        Files.writeString(schemaFile, xsd);

        manager.loadSchema(schemaFile.toFile());

        List<String> validChildren = manager.getValidChildElements("root");

        assertEquals(2, validChildren.size());
        assertTrue(validChildren.contains("child1"));
        assertTrue(validChildren.contains("child2"));
    }

    @Test
    @DisplayName("Should get valid attributes for element")
    void testGetValidAttributes() throws Exception {
        String xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="myElement">
                <xs:complexType>
                  <xs:attribute name="attr1" type="xs:string"/>
                  <xs:attribute name="attr2" type="xs:int" use="required"/>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

        Path schemaFile = tempDir.resolve("schema.xsd");
        Files.writeString(schemaFile, xsd);

        manager.loadSchema(schemaFile.toFile());

        List<String> validAttrs = manager.getValidAttributes("myElement");

        assertEquals(2, validAttrs.size());
        assertTrue(validAttrs.contains("attr1"));
        assertTrue(validAttrs.contains("attr2"));
    }

    @Test
    @DisplayName("Should handle schema loading errors gracefully")
    void testInvalidSchemaHandling() {
        String invalidXsd = "<not-valid-xml";

        assertThrows(SchemaLoadException.class, () -> {
            Path invalidFile = tempDir.resolve("invalid.xsd");
            Files.writeString(invalidFile, invalidXsd);
            manager.loadSchema(invalidFile.toFile());
        });
    }
}
```

---

## 5. Integration Test Example

**Purpose:** Test complete IntelliSense workflow

```java
package org.fxt.freexmltoolkit.controls.intellisense;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IntelliSenseIntegrationTest {

    private XmlIntelliSenseEngine engine;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        String xsd = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="library">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="book" maxOccurs="unbounded">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="title" type="xs:string"/>
                          <xs:element name="author" type="xs:string"/>
                          <xs:element name="year" type="xs:int"/>
                        </xs:sequence>
                        <xs:attribute name="isbn" type="xs:string" use="required"/>
                        <xs:attribute name="genre" type="xs:string"/>
                      </xs:complexType>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

        Path schemaFile = tempDir.resolve("library.xsd");
        Files.writeString(schemaFile, xsd);

        engine = new XmlIntelliSenseEngine();
        engine.loadSchema(schemaFile.toFile());
    }

    @Test
    @DisplayName("Should suggest root element")
    void testRootElementSuggestion() {
        String xml = "<|";
        int cursorPos = 1;

        List<Completion> completions = engine.getCompletions(xml, cursorPos);

        assertFalse(completions.isEmpty());
        assertTrue(completions.stream()
            .anyMatch(c -> c.text().equals("library")));
    }

    @Test
    @DisplayName("Should suggest child elements")
    void testChildElementSuggestion() {
        String xml = """
            <library>
              <|
            </library>
            """;
        int cursorPos = xml.indexOf("<|") + 1;

        List<Completion> completions = engine.getCompletions(xml, cursorPos);

        assertTrue(completions.stream()
            .anyMatch(c -> c.text().equals("book")));
    }

    @Test
    @DisplayName("Should suggest grandchild elements")
    void testGrandchildElementSuggestion() {
        String xml = """
            <library>
              <book isbn="123">
                <|
              </book>
            </library>
            """;
        int cursorPos = xml.indexOf("<|") + 1;

        List<Completion> completions = engine.getCompletions(xml, cursorPos);

        assertTrue(completions.stream()
            .anyMatch(c -> c.text().equals("title")));
        assertTrue(completions.stream()
            .anyMatch(c -> c.text().equals("author")));
        assertTrue(completions.stream()
            .anyMatch(c -> c.text().equals("year")));
    }

    @Test
    @DisplayName("Should suggest attributes")
    void testAttributeSuggestion() {
        String xml = """
            <library>
              <book |
            </library>
            """;
        int cursorPos = xml.indexOf("|");

        List<Completion> completions = engine.getCompletions(xml, cursorPos);

        assertTrue(completions.stream()
            .anyMatch(c -> c.text().equals("isbn")));
        assertTrue(completions.stream()
            .anyMatch(c -> c.text().equals("genre")));
    }

    @Test
    @DisplayName("Should mark required attributes")
    void testRequiredAttributeMarking() {
        String xml = """
            <library>
              <book |
            </library>
            """;
        int cursorPos = xml.indexOf("|");

        List<Completion> completions = engine.getCompletions(xml, cursorPos);

        Completion isbnCompletion = completions.stream()
            .filter(c -> c.text().equals("isbn"))
            .findFirst()
            .orElseThrow();

        assertTrue(isbnCompletion.isRequired());
    }

    @Test
    @DisplayName("Should exclude already-used attributes")
    void testExcludeUsedAttributes() {
        String xml = """
            <library>
              <book isbn="123" |
            </library>
            """;
        int cursorPos = xml.indexOf("|");

        List<Completion> completions = engine.getCompletions(xml, cursorPos);

        // isbn already used, should not be suggested again
        assertFalse(completions.stream()
            .anyMatch(c -> c.text().equals("isbn")));

        // genre not used yet, should be suggested
        assertTrue(completions.stream()
            .anyMatch(c -> c.text().equals("genre")));
    }

    @Test
    @DisplayName("Should filter by typed prefix")
    void testPrefixFiltering() {
        String xml = """
            <library>
              <book isbn="123">
                <ti|
              </book>
            </library>
            """;
        int cursorPos = xml.indexOf("|");

        List<Completion> completions = engine.getCompletions(xml, cursorPos);

        // Should only suggest "title" (not "author" or "year")
        assertEquals(1, completions.size());
        assertEquals("title", completions.get(0).text());
    }

    @Test
    @DisplayName("Should provide documentation in completions")
    void testDocumentationInCompletions() {
        String xml = """
            <library>
              <|
            </library>
            """;
        int cursorPos = xml.indexOf("<|") + 1;

        List<Completion> completions = engine.getCompletions(xml, cursorPos);

        Completion bookCompletion = completions.stream()
            .filter(c -> c.text().equals("book"))
            .findFirst()
            .orElseThrow();

        // Should have documentation extracted from schema
        assertNotNull(bookCompletion.documentation());
    }
}
```

---

## Summary

These test examples cover:

1. **FuzzySearch** - Matching algorithm, scoring, ranking
2. **CompletionContext** - Context detection, element/attribute identification
3. **NamespaceResolver** - Namespace parsing, prefix mapping, scoping
4. **MultiSchemaManager** - Schema loading, imports, element/attribute extraction
5. **Integration** - Complete IntelliSense workflow

**Next Steps:**
1. Implement these tests
2. Run tests and verify behavior
3. Add additional edge cases as discovered
4. Integrate with continuous testing

**Estimated Effort:** 10-12 hours for all IntelliSense tests
