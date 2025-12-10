/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2023.
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

package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XPath query execution in XmlServiceImpl.
 * Covers element selection, text nodes, attributes, and scalar functions.
 */
public class XmlServiceXPathTest {

    private XmlService xmlService;

    private static final String TEST_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <library>
                <book id="1" category="fiction">
                    <title>The Great Gatsby</title>
                    <author>F. Scott Fitzgerald</author>
                    <price>10.99</price>
                </book>
                <book id="2" category="non-fiction">
                    <title>A Brief History of Time</title>
                    <author>Stephen Hawking</author>
                    <price>15.50</price>
                </book>
                <book id="3" category="fiction">
                    <title>1984</title>
                    <author>George Orwell</author>
                    <price>9.99</price>
                </book>
                <!-- This is a comment -->
            </library>
            """;

    @BeforeEach
    void setUp() {
        xmlService = new XmlServiceImpl();
    }

    @Nested
    @DisplayName("Element Selection Tests")
    class ElementSelectionTests {

        @Test
        @DisplayName("Should return XML elements for standard XPath")
        void testElementSelection() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "//book[1]");

            assertNotNull(result, "Result should not be null");
            assertTrue(result.contains("<book"), "Result should contain book element");
            assertTrue(result.contains("id=\"1\""), "Result should contain book with id=1");
            assertTrue(result.contains("<title>The Great Gatsby</title>"), "Result should contain title");
        }

        @Test
        @DisplayName("Should return multiple elements")
        void testMultipleElementSelection() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "//title");

            assertNotNull(result, "Result should not be null");
            assertTrue(result.contains("The Great Gatsby"), "Result should contain first title");
            assertTrue(result.contains("A Brief History of Time"), "Result should contain second title");
            assertTrue(result.contains("1984"), "Result should contain third title");
        }
    }

    @Nested
    @DisplayName("Text Node Tests")
    class TextNodeTests {

        @Test
        @DisplayName("Should return text content for text() selection")
        void testTextNodeSelection() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "//book[1]/title/text()");

            assertNotNull(result, "Result should not be null");
            assertTrue(result.contains("The Great Gatsby"), "Result should contain the text content");
            assertFalse(result.contains("<title>"), "Result should NOT contain XML tags");
        }

        @Test
        @DisplayName("Should return multiple text nodes")
        void testMultipleTextNodes() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "//title/text()");

            assertNotNull(result, "Result should not be null");
            assertTrue(result.contains("The Great Gatsby"), "Result should contain first text");
            assertTrue(result.contains("A Brief History of Time"), "Result should contain second text");
            assertTrue(result.contains("1984"), "Result should contain third text");
        }

        @Test
        @DisplayName("Should return author text content")
        void testAuthorTextNode() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "//book[@id='2']/author/text()");

            assertNotNull(result, "Result should not be null");
            assertTrue(result.trim().contains("Stephen Hawking"), "Result should contain author name");
        }
    }

    @Nested
    @DisplayName("Attribute Selection Tests")
    class AttributeSelectionTests {

        @Test
        @DisplayName("Should return attribute value only")
        void testAttributeSelection() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "//book[1]/@id");

            assertNotNull(result, "Result should not be null");
            assertTrue(result.trim().contains("1"), "Result should contain attribute value '1'");
            assertFalse(result.contains("id="), "Result should NOT contain attribute name");
        }

        @Test
        @DisplayName("Should return multiple attribute values")
        void testMultipleAttributeSelection() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "//book/@id");

            assertNotNull(result, "Result should not be null");
            assertTrue(result.contains("1"), "Result should contain first id");
            assertTrue(result.contains("2"), "Result should contain second id");
            assertTrue(result.contains("3"), "Result should contain third id");
        }

        @Test
        @DisplayName("Should return category attribute")
        void testCategoryAttribute() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "//book[1]/@category");

            assertNotNull(result, "Result should not be null");
            assertTrue(result.trim().contains("fiction"), "Result should contain 'fiction'");
        }
    }

    @Nested
    @DisplayName("Scalar Function Tests")
    class ScalarFunctionTests {

        @Test
        @DisplayName("Should return count of elements")
        void testCountFunction() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "count(//book)");

            assertNotNull(result, "Result should not be null");
            assertEquals("3", result.trim(), "Result should be '3' for count of books");
        }

        @Test
        @DisplayName("Should return sum of prices")
        void testSumFunction() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "sum(//price)");

            assertNotNull(result, "Result should not be null");
            // 10.99 + 15.50 + 9.99 = 36.48
            assertTrue(result.trim().startsWith("36.4"), "Result should be approximately 36.48");
        }

        @Test
        @DisplayName("Should return string function result")
        void testStringFunction() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "string(//book[1]/title)");

            assertNotNull(result, "Result should not be null");
            assertEquals("The Great Gatsby", result.trim(), "Result should be the title text");
        }

        @Test
        @DisplayName("Should return boolean function result")
        void testBooleanFunction() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "boolean(//book[@id='1'])");

            assertNotNull(result, "Result should not be null");
            assertEquals("true", result.trim(), "Result should be 'true'");
        }

        @Test
        @DisplayName("Should return false for non-existent element")
        void testBooleanFunctionFalse() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "boolean(//nonexistent)");

            assertNotNull(result, "Result should not be null");
            assertEquals("false", result.trim(), "Result should be 'false'");
        }
    }

    @Nested
    @DisplayName("Comment Node Tests")
    class CommentNodeTests {

        @Test
        @DisplayName("Should return comment nodes")
        void testCommentSelection() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "//comment()");

            assertNotNull(result, "Result should not be null");
            assertTrue(result.contains("This is a comment"), "Result should contain comment text");
            assertTrue(result.contains("<!--"), "Result should contain comment markers");
        }
    }

    @Nested
    @DisplayName("Namespace Tests")
    class NamespaceTests {

        private static final String FUNDS_XML = """
                <?xml version="1.0" encoding="UTF-8"?>
                <FundsXML4 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                   <ControlData>
                      <Language>EN</Language>
                   </ControlData>
                </FundsXML4>
                """;

        @Test
        @DisplayName("Should return text from FundsXML Language element")
        void testFundsXmlLanguageText() {
            String result = xmlService.getXmlFromXpath(FUNDS_XML, "/FundsXML4/ControlData/Language/text()");

            assertNotNull(result, "Result should not be null");
            assertTrue(result.trim().contains("EN"), "Result should contain 'EN', but was: [" + result + "]");
        }

        @Test
        @DisplayName("Should return Language element as XML")
        void testFundsXmlLanguageElement() {
            String result = xmlService.getXmlFromXpath(FUNDS_XML, "/FundsXML4/ControlData/Language");

            assertNotNull(result, "Result should not be null");
            // Element may contain xmlns attributes from parent
            assertTrue(result.contains("<Language"), "Result should contain Language element");
            assertTrue(result.contains("EN"), "Result should contain 'EN'");
            assertTrue(result.contains("</Language>"), "Result should contain closing tag");
        }

        @Test
        @DisplayName("Should return text using string() function")
        void testFundsXmlStringFunction() {
            String result = xmlService.getXmlFromXpath(FUNDS_XML, "string(/FundsXML4/ControlData/Language)");

            assertNotNull(result, "Result should not be null");
            assertEquals("EN", result.trim(), "Result should be 'EN'");
        }

        @Test
        @DisplayName("Should return text using descendant axis")
        void testFundsXmlDescendantAxis() {
            String result = xmlService.getXmlFromXpath(FUNDS_XML, "//Language/text()");

            assertNotNull(result, "Result should not be null");
            assertTrue(result.trim().contains("EN"), "Result should contain 'EN'");
        }
    }

    @Nested
    @DisplayName("XPath 2.0 Function Tests")
    class XPath20FunctionTests {

        private static final String FUNDS_XML = """
                <?xml version="1.0" encoding="UTF-8"?>
                <FundsXML4>
                    <Funds>
                        <Fund>
                            <FundDynamicData>
                                <Portfolios>
                                    <Portfolio>
                                        <Positions>
                                            <Position>
                                                <TotalValue>
                                                    <Amount ccy="EUR">1234567.89</Amount>
                                                </TotalValue>
                                            </Position>
                                            <Position>
                                                <TotalValue>
                                                    <Amount ccy="EUR">987654.32</Amount>
                                                </TotalValue>
                                            </Position>
                                            <Position>
                                                <TotalValue>
                                                    <Amount ccy="USD">5000.00</Amount>
                                                </TotalValue>
                                            </Position>
                                        </Positions>
                                    </Portfolio>
                                </Portfolios>
                            </FundDynamicData>
                        </Fund>
                    </Funds>
                </FundsXML4>
                """;

        @Test
        @DisplayName("Should support XPath 2.0 format-number function with two parameters")
        void testFormatNumberTwoParams() {
            String result = xmlService.getXmlFromXpath(FUNDS_XML,
                "format-number(sum(/FundsXML4/Funds/Fund/FundDynamicData/Portfolios/Portfolio/Positions/Position/TotalValue/Amount[@ccy='EUR']/text()), '###,###,000.0###')");

            assertNotNull(result, "Result should not be null");
            assertTrue(result.contains("2,222,222.2"), "Result should contain formatted number with comma separator");
        }

        @Test
        @DisplayName("Should support XPath 2.0 format-number function with simple pattern")
        void testFormatNumberSimplePattern() {
            String result = xmlService.getXmlFromXpath(FUNDS_XML,
                "format-number(1234.56, '0.00')");

            assertNotNull(result, "Result should not be null");
            assertTrue(result.contains("1234.56"), "Result should contain formatted number");
        }

        @Test
        @DisplayName("Should support XPath 2.0 current-date function")
        void testCurrentDateFunction() {
            String result = xmlService.getXmlFromXpath(FUNDS_XML, "current-date()");

            assertNotNull(result, "Result should not be null");
            assertFalse(result.trim().isEmpty(), "Result should not be empty");
            // Should return ISO date format like 2025-12-10
        }

        @Test
        @DisplayName("Should support XPath 2.0 upper-case function")
        void testUpperCaseFunction() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "upper-case('hello world')");

            assertNotNull(result, "Result should not be null");
            assertEquals("HELLO WORLD", result.trim(), "Result should be uppercase");
        }

        @Test
        @DisplayName("Should support XPath 2.0 lower-case function")
        void testLowerCaseFunction() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "lower-case('HELLO WORLD')");

            assertNotNull(result, "Result should not be null");
            assertEquals("hello world", result.trim(), "Result should be lowercase");
        }

        @Test
        @DisplayName("Should support XPath 2.0 tokenize function")
        void testTokenizeFunction() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "tokenize('a,b,c', ',')");

            assertNotNull(result, "Result should not be null");
            // Saxon returns sequence items separated by newlines
            assertTrue(result.contains("a"), "Result should contain 'a'");
            assertTrue(result.contains("b"), "Result should contain 'b'");
            assertTrue(result.contains("c"), "Result should contain 'c'");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty result set")
        void testEmptyResultSet() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "//nonexistent");

            assertNotNull(result, "Result should not be null");
            assertTrue(result.trim().isEmpty(), "Result should be empty for non-matching XPath");
        }

        @Test
        @DisplayName("Should handle XPath with predicates")
        void testXPathWithPredicates() {
            String result = xmlService.getXmlFromXpath(TEST_XML, "//book[@category='fiction']/title/text()");

            assertNotNull(result, "Result should not be null");
            assertTrue(result.contains("The Great Gatsby"), "Result should contain fiction book");
            assertTrue(result.contains("1984"), "Result should contain fiction book");
            assertFalse(result.contains("A Brief History of Time"), "Result should NOT contain non-fiction book");
        }

        @Test
        @DisplayName("Should throw exception for invalid XPath")
        void testInvalidXPath() {
            assertThrows(RuntimeException.class, () -> {
                xmlService.getXmlFromXpath(TEST_XML, "///invalid[xpath");
            }, "Invalid XPath should throw RuntimeException");
        }
    }
}
