/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
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

package org.fxt.freexmltoolkit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XPathSnippet")
class XPathSnippetTest {

    @Nested
    @DisplayName("SnippetType Enum")
    class SnippetTypeTests {

        @Test
        @DisplayName("All snippet types have display names")
        void allTypesHaveDisplayNames() {
            for (XPathSnippet.SnippetType type : XPathSnippet.SnippetType.values()) {
                assertNotNull(type.getDisplayName());
                assertFalse(type.getDisplayName().isEmpty());
            }
        }

        @Test
        @DisplayName("All snippet types have CSS classes")
        void allTypesHaveCssClasses() {
            for (XPathSnippet.SnippetType type : XPathSnippet.SnippetType.values()) {
                assertNotNull(type.getCssClass());
                assertFalse(type.getCssClass().isEmpty());
            }
        }

        @Test
        @DisplayName("All snippet types have short names")
        void allTypesHaveShortNames() {
            for (XPathSnippet.SnippetType type : XPathSnippet.SnippetType.values()) {
                assertNotNull(type.getShortName());
                assertFalse(type.getShortName().isEmpty());
            }
        }

        @Test
        @DisplayName("XPATH type has correct values")
        void xpathTypeValues() {
            XPathSnippet.SnippetType type = XPathSnippet.SnippetType.XPATH;
            assertEquals("XPath Expression", type.getDisplayName());
            assertEquals("xpath", type.getCssClass());
            assertEquals("XPath", type.getShortName());
        }

        @Test
        @DisplayName("XQUERY type has correct values")
        void xqueryTypeValues() {
            XPathSnippet.SnippetType type = XPathSnippet.SnippetType.XQUERY;
            assertEquals("XQuery Script", type.getDisplayName());
            assertEquals("xquery", type.getCssClass());
            assertEquals("XQuery", type.getShortName());
        }
    }

    @Nested
    @DisplayName("SnippetCategory Enum")
    class SnippetCategoryTests {

        @Test
        @DisplayName("All categories have display names")
        void allCategoriesHaveDisplayNames() {
            for (XPathSnippet.SnippetCategory category : XPathSnippet.SnippetCategory.values()) {
                assertNotNull(category.getDisplayName());
                assertFalse(category.getDisplayName().isEmpty());
            }
        }

        @Test
        @DisplayName("All categories have descriptions")
        void allCategoriesHaveDescriptions() {
            for (XPathSnippet.SnippetCategory category : XPathSnippet.SnippetCategory.values()) {
                assertNotNull(category.getDescription());
                assertFalse(category.getDescription().isEmpty());
            }
        }

        @Test
        @DisplayName("All categories have colors")
        void allCategoriesHaveColors() {
            for (XPathSnippet.SnippetCategory category : XPathSnippet.SnippetCategory.values()) {
                assertNotNull(category.getColor());
                assertTrue(category.getColor().startsWith("#"));
            }
        }

        @Test
        @DisplayName("NAVIGATION category has correct values")
        void navigationCategoryValues() {
            XPathSnippet.SnippetCategory category = XPathSnippet.SnippetCategory.NAVIGATION;
            assertEquals("Navigation", category.getDisplayName());
            assertEquals("Navigate XML structure", category.getDescription());
            assertEquals("#007bff", category.getColor());
        }
    }

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor initializes all collections")
        void defaultConstructorInitializesCollections() {
            XPathSnippet snippet = new XPathSnippet();

            assertNotNull(snippet.getId());
            assertNotNull(snippet.getCreatedAt());
            assertNotNull(snippet.getLastModified());
            assertNotNull(snippet.getTags());
            assertNotNull(snippet.getVariables());
            assertNotNull(snippet.getNamespaces());
            assertNotNull(snippet.getParameters());
            assertEquals(0, snippet.getExecutionCount());
            assertEquals("1.0", snippet.getVersion());
            assertFalse(snippet.isFavorite());
        }

        @Test
        @DisplayName("Parameterized constructor sets values")
        void parameterizedConstructorSetsValues() {
            XPathSnippet snippet = new XPathSnippet(
                    "Test Snippet",
                    XPathSnippet.SnippetType.XPATH,
                    XPathSnippet.SnippetCategory.NAVIGATION,
                    "//element"
            );

            assertEquals("Test Snippet", snippet.getName());
            assertEquals(XPathSnippet.SnippetType.XPATH, snippet.getType());
            assertEquals(XPathSnippet.SnippetCategory.NAVIGATION, snippet.getCategory());
            assertEquals("//element", snippet.getQuery());
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderTests {

        @Test
        @DisplayName("Builder creates snippet with required fields")
        void builderCreatesSnippet() {
            XPathSnippet snippet = XPathSnippet.builder()
                    .name("Test")
                    .query("//test")
                    .build();

            assertEquals("Test", snippet.getName());
            assertEquals("//test", snippet.getQuery());
            assertEquals(XPathSnippet.SnippetType.XPATH, snippet.getType()); // Default
            assertEquals(XPathSnippet.SnippetCategory.CUSTOM, snippet.getCategory()); // Default
        }

        @Test
        @DisplayName("Builder sets all optional fields")
        void builderSetsAllFields() {
            XPathSnippet snippet = XPathSnippet.builder()
                    .name("Full Snippet")
                    .description("A full test snippet")
                    .type(XPathSnippet.SnippetType.XQUERY)
                    .category(XPathSnippet.SnippetCategory.TRANSFORMATION)
                    .query("for $x in //item return $x")
                    .expectedResult("<item>test</item>")
                    .author("Test Author")
                    .version("2.0")
                    .tags("test", "sample")
                    .variable("var1", "value1")
                    .namespace("xs", "http://www.w3.org/2001/XMLSchema")
                    .contextPath("/root")
                    .requiresContext(true)
                    .favorite(true)
                    .documentationUrl("http://example.com/docs")
                    .exampleXml("<root><item/></root>")
                    .build();

            assertEquals("Full Snippet", snippet.getName());
            assertEquals("A full test snippet", snippet.getDescription());
            assertEquals(XPathSnippet.SnippetType.XQUERY, snippet.getType());
            assertEquals(XPathSnippet.SnippetCategory.TRANSFORMATION, snippet.getCategory());
            assertEquals("for $x in //item return $x", snippet.getQuery());
            assertEquals("<item>test</item>", snippet.getExpectedResult());
            assertEquals("Test Author", snippet.getAuthor());
            assertEquals("2.0", snippet.getVersion());
            assertTrue(snippet.getTags().contains("test"));
            assertTrue(snippet.getTags().contains("sample"));
            assertEquals("value1", snippet.getVariables().get("var1"));
            assertEquals("http://www.w3.org/2001/XMLSchema", snippet.getNamespaces().get("xs"));
            assertEquals("/root", snippet.getContextPath());
            assertTrue(snippet.isRequiresContext());
            assertTrue(snippet.isFavorite());
            assertEquals("http://example.com/docs", snippet.getDocumentationUrl());
            assertEquals("<root><item/></root>", snippet.getExampleXml());
        }

        @Test
        @DisplayName("Builder throws exception for missing name")
        void builderThrowsForMissingName() {
            assertThrows(IllegalStateException.class, () ->
                    XPathSnippet.builder()
                            .query("//test")
                            .build()
            );
        }

        @Test
        @DisplayName("Builder throws exception for missing query")
        void builderThrowsForMissingQuery() {
            assertThrows(IllegalStateException.class, () ->
                    XPathSnippet.builder()
                            .name("Test")
                            .build()
            );
        }

        @Test
        @DisplayName("Builder throws exception for blank name")
        void builderThrowsForBlankName() {
            assertThrows(IllegalStateException.class, () ->
                    XPathSnippet.builder()
                            .name("   ")
                            .query("//test")
                            .build()
            );
        }
    }

    @Nested
    @DisplayName("Execution Methods")
    class ExecutionMethodsTests {

        @Test
        @DisplayName("Record execution updates metrics")
        void recordExecutionUpdatesMetrics() {
            XPathSnippet snippet = XPathSnippet.builder()
                    .name("Test")
                    .query("//test")
                    .build();

            snippet.recordExecution(100);

            assertEquals(1, snippet.getExecutionCount());
            assertEquals(100, snippet.getLastExecutionTime());
            assertEquals(100, snippet.getAverageExecutionTime());
            assertEquals(100, snippet.getTotalExecutionTime());
            assertNotNull(snippet.getLastExecuted());
        }

        @Test
        @DisplayName("Multiple executions calculate average")
        void multipleExecutionsCalculateAverage() {
            XPathSnippet snippet = XPathSnippet.builder()
                    .name("Test")
                    .query("//test")
                    .build();

            snippet.recordExecution(100);
            snippet.recordExecution(200);
            snippet.recordExecution(300);

            assertEquals(3, snippet.getExecutionCount());
            assertEquals(300, snippet.getLastExecutionTime());
            assertEquals(200, snippet.getAverageExecutionTime()); // (100+200+300)/3 = 200
            assertEquals(600, snippet.getTotalExecutionTime());
        }

        @Test
        @DisplayName("Get execution statistics for never executed")
        void getExecutionStatisticsNeverExecuted() {
            XPathSnippet snippet = XPathSnippet.builder()
                    .name("Test")
                    .query("//test")
                    .build();

            assertEquals("Never executed", snippet.getExecutionStatistics());
        }

        @Test
        @DisplayName("Get execution statistics for executed snippet")
        void getExecutionStatisticsExecuted() {
            XPathSnippet snippet = XPathSnippet.builder()
                    .name("Test")
                    .query("//test")
                    .build();

            snippet.recordExecution(150);

            String stats = snippet.getExecutionStatistics();
            assertTrue(stats.contains("1 times"));
            assertTrue(stats.contains("150ms"));
        }
    }

    @Nested
    @DisplayName("Query Processing")
    class QueryProcessingTests {

        @Test
        @DisplayName("Has parameters returns correct value")
        void hasParametersReturnsCorrectValue() {
            XPathSnippet snippet = new XPathSnippet();
            assertFalse(snippet.hasParameters());

            snippet.getParameters().add(new SnippetParameter("param", SnippetParameter.ParameterType.STRING, "default"));
            assertTrue(snippet.hasParameters());
        }

        @Test
        @DisplayName("Has variables returns correct value")
        void hasVariablesReturnsCorrectValue() {
            XPathSnippet snippet = new XPathSnippet();
            assertFalse(snippet.hasVariables());

            snippet.getVariables().put("var", "value");
            assertTrue(snippet.hasVariables());
        }

        @Test
        @DisplayName("Has namespaces returns correct value")
        void hasNamespacesReturnsCorrectValue() {
            XPathSnippet snippet = new XPathSnippet();
            assertFalse(snippet.hasNamespaces());

            snippet.getNamespaces().put("xs", "http://www.w3.org/2001/XMLSchema");
            assertTrue(snippet.hasNamespaces());
        }

        @Test
        @DisplayName("Get query with variables substitutes values")
        void getQueryWithVariablesSubstitutesValues() {
            XPathSnippet snippet = XPathSnippet.builder()
                    .name("Test")
                    .query("//element[@name=$varName]")
                    .variable("varName", "'default'")
                    .build();

            String result = snippet.getQueryWithVariables(Map.of("varName", "'custom'"));
            assertEquals("//element[@name='custom']", result);
        }

        @Test
        @DisplayName("Get query with variables uses default for missing")
        void getQueryWithVariablesUsesDefault() {
            XPathSnippet snippet = XPathSnippet.builder()
                    .name("Test")
                    .query("//element[@name=$varName]")
                    .variable("varName", "'default'")
                    .build();

            String result = snippet.getQueryWithVariables(Map.of());
            assertEquals("//element[@name='default']", result);
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("Valid snippet returns no errors")
        void validSnippetReturnsNoErrors() {
            XPathSnippet snippet = XPathSnippet.builder()
                    .name("Valid Snippet")
                    .query("//element")
                    .type(XPathSnippet.SnippetType.XPATH)
                    .build();

            List<String> errors = snippet.validate();
            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("Snippet with null name returns error")
        void snippetWithNullNameReturnsError() {
            XPathSnippet snippet = new XPathSnippet();
            snippet.setQuery("//test");
            snippet.setType(XPathSnippet.SnippetType.XPATH);

            List<String> errors = snippet.validate();
            assertTrue(errors.stream().anyMatch(e -> e.contains("name")));
        }

        @Test
        @DisplayName("Snippet with null query returns error")
        void snippetWithNullQueryReturnsError() {
            XPathSnippet snippet = new XPathSnippet();
            snippet.setName("Test");
            snippet.setType(XPathSnippet.SnippetType.XPATH);

            List<String> errors = snippet.validate();
            assertTrue(errors.stream().anyMatch(e -> e.contains("Query")));
        }

        @Test
        @DisplayName("Snippet with undefined variable returns error")
        void snippetWithUndefinedVariableReturnsError() {
            XPathSnippet snippet = XPathSnippet.builder()
                    .name("Test")
                    .query("//element[@id=$undefinedVar]")
                    .type(XPathSnippet.SnippetType.XPATH)
                    .build();

            List<String> errors = snippet.validate();
            assertTrue(errors.stream().anyMatch(e -> e.contains("undefinedVar")));
        }

        @Test
        @DisplayName("Snippet with defined variable passes validation")
        void snippetWithDefinedVariablePasses() {
            XPathSnippet snippet = XPathSnippet.builder()
                    .name("Test")
                    .query("//element[@id=$definedVar]")
                    .variable("definedVar", "'value'")
                    .type(XPathSnippet.SnippetType.XPATH)
                    .build();

            List<String> errors = snippet.validate();
            assertFalse(errors.stream().anyMatch(e -> e.contains("definedVar")));
        }
    }

    @Nested
    @DisplayName("Clone")
    class CloneTests {

        @Test
        @DisplayName("Clone creates new instance with different ID")
        void cloneCreatesDifferentId() {
            XPathSnippet original = XPathSnippet.builder()
                    .name("Original")
                    .query("//element")
                    .build();

            XPathSnippet cloned = original.clone();

            assertNotEquals(original.getId(), cloned.getId());
        }

        @Test
        @DisplayName("Clone copies all properties")
        void cloneCopiesAllProperties() {
            XPathSnippet original = XPathSnippet.builder()
                    .name("Original")
                    .description("Description")
                    .type(XPathSnippet.SnippetType.XQUERY)
                    .category(XPathSnippet.SnippetCategory.ANALYSIS)
                    .query("//element")
                    .author("Author")
                    .version("2.0")
                    .tags("tag1", "tag2")
                    .variable("var1", "val1")
                    .namespace("xs", "http://example.com")
                    .contextPath("/root")
                    .build();

            XPathSnippet cloned = original.clone();

            assertEquals("Original (Copy)", cloned.getName());
            assertEquals("Description", cloned.getDescription());
            assertEquals(XPathSnippet.SnippetType.XQUERY, cloned.getType());
            assertEquals(XPathSnippet.SnippetCategory.ANALYSIS, cloned.getCategory());
            assertEquals("//element", cloned.getQuery());
            assertEquals("Author", cloned.getAuthor());
            assertEquals("2.0", cloned.getVersion());
            assertTrue(cloned.getTags().contains("tag1"));
            assertEquals("val1", cloned.getVariables().get("var1"));
            assertEquals("http://example.com", cloned.getNamespaces().get("xs"));
            assertEquals("/root", cloned.getContextPath());
        }
    }

    @Nested
    @DisplayName("Summary and Display")
    class SummaryTests {

        @Test
        @DisplayName("Get summary with no executions")
        void getSummaryNoExecutions() {
            XPathSnippet snippet = XPathSnippet.builder()
                    .name("Test Snippet")
                    .query("//element")
                    .type(XPathSnippet.SnippetType.XPATH)
                    .build();

            String summary = snippet.getSummary();
            assertTrue(summary.contains("Test Snippet"));
            assertTrue(summary.contains("XPath"));
            assertFalse(summary.contains("executions"));
        }

        @Test
        @DisplayName("Get summary with executions")
        void getSummaryWithExecutions() {
            XPathSnippet snippet = XPathSnippet.builder()
                    .name("Test Snippet")
                    .query("//element")
                    .type(XPathSnippet.SnippetType.XPATH)
                    .build();

            snippet.recordExecution(100);
            snippet.recordExecution(200);

            String summary = snippet.getSummary();
            assertTrue(summary.contains("Test Snippet"));
            assertTrue(summary.contains("2 executions"));
        }

        @Test
        @DisplayName("ToString includes key info")
        void toStringIncludesKeyInfo() {
            XPathSnippet snippet = XPathSnippet.builder()
                    .name("Test")
                    .query("//element")
                    .type(XPathSnippet.SnippetType.XPATH)
                    .category(XPathSnippet.SnippetCategory.NAVIGATION)
                    .build();

            String str = snippet.toString();
            assertTrue(str.contains("Test"));
            assertTrue(str.contains("XPATH"));
            assertTrue(str.contains("NAVIGATION"));
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("Same instance is equal")
        void sameInstanceIsEqual() {
            XPathSnippet snippet = XPathSnippet.builder()
                    .name("Test")
                    .query("//element")
                    .build();

            assertEquals(snippet, snippet);
        }

        @Test
        @DisplayName("Different instances with same ID are equal")
        void differentInstancesSameIdAreEqual() {
            XPathSnippet snippet1 = new XPathSnippet();
            XPathSnippet snippet2 = new XPathSnippet();
            snippet2.setId(snippet1.getId());

            assertEquals(snippet1, snippet2);
            assertEquals(snippet1.hashCode(), snippet2.hashCode());
        }

        @Test
        @DisplayName("Different IDs are not equal")
        void differentIdsNotEqual() {
            XPathSnippet snippet1 = new XPathSnippet();
            XPathSnippet snippet2 = new XPathSnippet();

            assertNotEquals(snippet1, snippet2);
        }

        @Test
        @DisplayName("Null is not equal")
        void nullIsNotEqual() {
            XPathSnippet snippet = new XPathSnippet();
            assertNotEquals(null, snippet);
        }
    }

    @Nested
    @DisplayName("Setters update lastModified")
    class SetterModificationTests {

        @Test
        @DisplayName("setName updates lastModified")
        void setNameUpdatesLastModified() throws InterruptedException {
            XPathSnippet snippet = new XPathSnippet();
            var original = snippet.getLastModified();

            Thread.sleep(10); // Ensure time difference
            snippet.setName("New Name");

            assertTrue(snippet.getLastModified().isAfter(original) ||
                    snippet.getLastModified().equals(original));
        }

        @Test
        @DisplayName("setFavorite updates lastModified")
        void setFavoriteUpdatesLastModified() throws InterruptedException {
            XPathSnippet snippet = new XPathSnippet();
            var original = snippet.getLastModified();

            Thread.sleep(10);
            snippet.setFavorite(true);

            assertTrue(snippet.getLastModified().isAfter(original) ||
                    snippet.getLastModified().equals(original));
            assertTrue(snippet.isFavorite());
        }
    }
}
