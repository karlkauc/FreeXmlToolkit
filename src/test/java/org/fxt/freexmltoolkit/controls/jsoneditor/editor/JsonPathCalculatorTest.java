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

package org.fxt.freexmltoolkit.controls.jsoneditor.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonPathCalculatorTest {

    @Test
    void calculatesPathForNestedKeyAndValue() {
        String json = """
                {
                  "a": {
                    "b": [
                      { "c": 1 }
                    ]
                  }
                }
                """;

        int cKeyPos = json.indexOf("\"c\"") + 1; // inside the key (between quotes)
        assertTrue(cKeyPos > 0, "Expected to find \"c\" in test JSON");

        JsonPathCalculator.JsonHoverInfo keyInfo = JsonPathCalculator.calculatePath(json, cKeyPos);
        assertNotNull(keyInfo);
        assertTrue(keyInfo.isValid());
        assertEquals("$.a.b[0].c", keyInfo.jsonPath());
        assertEquals("property", keyInfo.valueType());
        assertEquals("c", keyInfo.key());

        int onePos = json.indexOf("1");
        assertTrue(onePos > 0, "Expected to find numeric value 1 in test JSON");

        JsonPathCalculator.JsonHoverInfo valueInfo = JsonPathCalculator.calculatePath(json, onePos);
        assertNotNull(valueInfo);
        assertTrue(valueInfo.isValid());
        assertEquals("$.a.b[0].c", valueInfo.jsonPath());
        assertEquals("number", valueInfo.valueType());
    }
}




