/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
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
package org.fxt.freexmltoolkit.service.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.GenerationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("XsdExampleValueStrategy")
class XsdExampleValueStrategyTest {

    private final XsdExampleValueStrategy strategy = new XsdExampleValueStrategy();
    private final GenerationContext context = new GenerationContext();

    @Test
    @DisplayName("Null element returns empty string")
    void nullElement() {
        assertEquals("", strategy.resolve(null, Map.of(), context));
    }

    @Test
    @DisplayName("Element without example values falls back to empty string")
    void noExamplesFallsBackToEmpty() {
        var element = new XsdExtendedElement();
        element.setElementName("name");
        element.setElementType("xs:string");

        assertEquals("", strategy.resolve(element, Map.of(), context));
    }

    @Test
    @DisplayName("Single example value is returned verbatim")
    void singleExampleReturnedVerbatim() {
        var element = new XsdExtendedElement();
        element.setElementName("country");
        element.setElementType("xs:string");
        element.setExampleValues(List.of("AT"));

        assertEquals("AT", strategy.resolve(element, Map.of(), context));
    }

    @Test
    @DisplayName("Multiple example values: strategy picks one of them")
    void picksFromMultipleExamples() {
        var element = new XsdExtendedElement();
        element.setElementName("country");
        element.setElementType("xs:string");
        element.setExampleValues(List.of("AT", "DE", "CH"));

        Set<String> allowed = Set.of("AT", "DE", "CH");
        for (int i = 0; i < 40; i++) {
            String result = strategy.resolve(element, Map.of(), context);
            assertTrue(allowed.contains(result),
                    "Result '" + result + "' must be one of " + allowed);
        }
    }

    @Test
    @DisplayName("Across many calls, all configured values are eventually picked")
    void allValuesEventuallyPicked() {
        var element = new XsdExtendedElement();
        element.setElementName("country");
        element.setExampleValues(List.of("AT", "DE", "CH"));

        Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 300 && seen.size() < 3; i++) {
            seen.add(strategy.resolve(element, Map.of(), context));
        }

        assertEquals(Set.of("AT", "DE", "CH"), seen,
                "Random picker should eventually hit every example value; saw " + seen);
    }

    @Test
    @DisplayName("Empty example list falls back to empty string")
    void emptyExampleList() {
        var element = new XsdExtendedElement();
        element.setElementName("x");
        element.setExampleValues(List.of());

        assertEquals("", strategy.resolve(element, Map.of(), context));
    }

    @Test
    @DisplayName("Config map is ignored")
    void configIsIgnored() {
        var element = new XsdExtendedElement();
        element.setElementName("x");
        element.setExampleValues(List.of("only"));

        assertEquals("only", strategy.resolve(element, Map.of("value", "ignored"), context));
    }
}
