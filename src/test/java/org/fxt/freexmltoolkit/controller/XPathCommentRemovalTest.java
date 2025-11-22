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

package org.fxt.freexmltoolkit.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for XPath/XQuery comment removal functionality.
 * This tests the removeCommentsFromQuery method that handles both XPath 2.0 comments (: ... :)
 * and XML-style comments <!-- ... -->.
 */
public class XPathCommentRemovalTest {

    /**
     * Helper method that uses reflection to call the private removeCommentsFromQuery method.
     */
    private String removeCommentsFromQuery(String query) throws Exception {
        XmlUltimateController controller = new XmlUltimateController();
        java.lang.reflect.Method method = XmlUltimateController.class.getDeclaredMethod(
                "removeCommentsFromQuery", String.class);
        method.setAccessible(true);
        return (String) method.invoke(controller, query);
    }

    @Test
    @DisplayName("Should remove XPath 2.0 style comments (: ... :)")
    void testRemoveXPathComments() throws Exception {
        String input = "(: Enter your XPath expression here :)\n(: Example: //book[@category='fiction']/title :)";
        String result = removeCommentsFromQuery(input);

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isEmpty() || result.isBlank(), "All comments should be removed");
    }

    @Test
    @DisplayName("Should remove XML style comments <!-- ... -->")
    void testRemoveXmlComments() throws Exception {
        String input = "<!-- Enter your XPath expression here -->";
        String result = removeCommentsFromQuery(input);

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isEmpty() || result.isBlank(), "All comments should be removed");
    }

    @Test
    @DisplayName("Should preserve actual XPath expression while removing comments")
    void testPreserveXPathWithComments() throws Exception {
        String input = "(: Get all books :)\n//book[@category='fiction']/title";
        String result = removeCommentsFromQuery(input);

        assertNotNull(result, "Result should not be null");
        assertEquals("//book[@category='fiction']/title", result,
                "XPath expression should be preserved, comments removed");
    }

    @Test
    @DisplayName("Should handle multiple XPath comments")
    void testMultipleXPathComments() throws Exception {
        String input = "(: Comment 1 :) //book (: Comment 2 :)";
        String result = removeCommentsFromQuery(input);

        assertNotNull(result, "Result should not be null");
        assertTrue(result.contains("//book"), "XPath should be preserved");
        assertFalse(result.contains("(:"), "No comment markers should remain");
        assertFalse(result.contains(":)"), "No comment markers should remain");
    }

    @Test
    @DisplayName("Should handle mixed comment styles")
    void testMixedComments() throws Exception {
        String input = "(: XPath comment :)\n<!-- XML comment -->\n//book/title";
        String result = removeCommentsFromQuery(input);

        assertNotNull(result, "Result should not be null");
        assertEquals("//book/title", result, "Only XPath expression should remain");
    }

    @Test
    @DisplayName("Should return null when input is null")
    void testNullInput() throws Exception {
        String result = removeCommentsFromQuery(null);
        assertNull(result, "Should return null for null input");
    }

    @Test
    @DisplayName("Should handle empty string")
    void testEmptyString() throws Exception {
        String result = removeCommentsFromQuery("");
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isEmpty(), "Empty string should remain empty");
    }

    @Test
    @DisplayName("Should handle query without comments")
    void testNoComments() throws Exception {
        String input = "//book[@category='fiction']/title";
        String result = removeCommentsFromQuery(input);

        assertEquals(input, result, "Query without comments should remain unchanged");
    }
}
