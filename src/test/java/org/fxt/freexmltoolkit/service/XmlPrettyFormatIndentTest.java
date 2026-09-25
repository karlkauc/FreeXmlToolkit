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

package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link XmlService#prettyFormat(String, int)} backs Format Document (Shift+Alt+F) and the
 * generated samples: the requested indent width must be honoured (the Saxon serializer
 * ignores the Xalan {@code indent-amount} property) and start tags must never be wrapped
 * onto several lines, however many attributes they carry.
 */
class XmlPrettyFormatIndentTest {

    private static final String LONG_START_TAG = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<FundsXML4 xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
            + " xsi:noNamespaceSchemaLocation=\"FundsXML4.xsd\""
            + " xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\" a=\"1\" b=\"2\" c=\"3\">"
            + "<ControlData><UniqueDocumentID>x</UniqueDocumentID></ControlData></FundsXML4>";

    @Test
    void honoursTheRequestedIndentWidth() {
        String two = XmlService.prettyFormat(LONG_START_TAG, 2);
        String four = XmlService.prettyFormat(LONG_START_TAG, 4);

        assertTrue(two.contains("\n  <ControlData>\n    <UniqueDocumentID>x</UniqueDocumentID>\n  </ControlData>\n"), two);
        assertTrue(four.contains("\n    <ControlData>\n        <UniqueDocumentID>x</UniqueDocumentID>\n    </ControlData>\n"), four);
    }

    @Test
    void neverWrapsAttributesOntoSeveralLines() {
        String formatted = XmlService.prettyFormat(LONG_START_TAG, 2);

        String rootLine = formatted.lines().filter(l -> l.startsWith("<FundsXML4")).findFirst().orElseThrow();
        assertTrue(rootLine.endsWith("\">") && rootLine.contains("a=\"1\"") && rootLine.contains("c=\"3\"")
                        && rootLine.contains("xsi:noNamespaceSchemaLocation=\"FundsXML4.xsd\""),
                "the whole start tag stays on one line: " + formatted);
        assertEquals(1, formatted.lines().filter(l -> l.contains("xmlns:")).count(),
                "all namespace declarations sit on the root line: " + formatted);
        assertTrue(formatted.lines().noneMatch(l -> l.startsWith(" ") && l.contains("=\"") && !l.contains("<")),
                "no continuation lines holding only attributes: " + formatted);
    }
}
