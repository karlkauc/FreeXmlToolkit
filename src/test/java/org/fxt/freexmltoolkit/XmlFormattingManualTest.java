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

package org.fxt.freexmltoolkit;

import org.fxt.freexmltoolkit.service.XmlService;

/**
 * Manual test to demonstrate the improved XML formatting functionality.
 * Run this class as a Java application to see the before/after formatting.
 */
public class XmlFormattingManualTest {

    public static void main(String[] args) {
        // Test case 1: User's reported issue - excessive whitespace
        testCase1();

        // Test case 2: Nested elements with whitespace
        testCase2();

        // Test case 3: Preserve meaningful whitespace
        testCase3();
    }

    private static void testCase1() {
        System.out.println("=== Test Case 1: User's Reported Issue ===");
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n   h\n   </root>";

        System.out.println("Input:");
        System.out.println(input);
        System.out.println();

        String formatted = XmlService.prettyFormat(input, 2);

        System.out.println("Formatted:");
        System.out.println(formatted);
        System.out.println();

        // Expected: The 'h' should be trimmed without excessive whitespace
        if (formatted.contains(">h<")) {
            System.out.println("✓ PASS: Text content is correctly trimmed");
        } else {
            System.out.println("✗ FAIL: Text content not properly trimmed");
        }
        System.out.println("==========================================\n");
    }

    private static void testCase2() {
        System.out.println("=== Test Case 2: Nested Elements ===");
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>  \n  <child>  value  </child>  \n</root>";

        System.out.println("Input:");
        System.out.println(input);
        System.out.println();

        String formatted = XmlService.prettyFormat(input, 2);

        System.out.println("Formatted:");
        System.out.println(formatted);
        System.out.println();

        if (formatted.contains(">value<")) {
            System.out.println("✓ PASS: Nested element text is correctly trimmed");
        } else {
            System.out.println("✗ FAIL: Nested element text not properly trimmed");
        }
        System.out.println("====================================\n");
    }

    private static void testCase3() {
        System.out.println("=== Test Case 3: Meaningful Whitespace ===");
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root><text>Hello World</text></root>";

        System.out.println("Input:");
        System.out.println(input);
        System.out.println();

        String formatted = XmlService.prettyFormat(input, 2);

        System.out.println("Formatted:");
        System.out.println(formatted);
        System.out.println();

        if (formatted.contains("Hello World")) {
            System.out.println("✓ PASS: Meaningful whitespace preserved");
        } else {
            System.out.println("✗ FAIL: Meaningful whitespace not preserved");
        }
        System.out.println("==========================================\n");
    }
}
