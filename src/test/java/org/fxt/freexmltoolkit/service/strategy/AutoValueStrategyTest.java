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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.GenerationContext;
import org.fxt.freexmltoolkit.service.XsdSampleDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AutoValueStrategy")
class AutoValueStrategyTest {

    private final GenerationContext context = new GenerationContext();

    @Test
    @DisplayName("Null element returns empty string without calling generator")
    void nullElementReturnsEmpty() {
        var recorder = new RecordingGenerator();
        var strategy = new AutoValueStrategy(recorder);

        assertEquals("", strategy.resolve(null, Map.of(), context));
        assertEquals(0, recorder.callCount, "Generator must not be called for null element");
    }

    @Test
    @DisplayName("Non-null element delegates to XsdSampleDataGenerator")
    void delegatesToGenerator() {
        var recorder = new RecordingGenerator();
        recorder.nextReturn = "SAMPLE";
        var strategy = new AutoValueStrategy(recorder);

        var element = new XsdExtendedElement();
        element.setElementName("name");
        element.setElementType("xs:string");

        String result = strategy.resolve(element, Map.of(), context);

        assertEquals("SAMPLE", result);
        assertEquals(1, recorder.callCount, "Generator should be called exactly once");
        assertSame(element, recorder.lastElement, "Generator must receive the same element instance");
    }

    @Test
    @DisplayName("Strategy ignores config map")
    void ignoresConfig() {
        var recorder = new RecordingGenerator();
        recorder.nextReturn = "X";
        var strategy = new AutoValueStrategy(recorder);

        var element = new XsdExtendedElement();
        element.setElementName("e");
        element.setElementType("xs:string");

        assertEquals("X", strategy.resolve(element, Map.of("value", "ignored"), context));
    }

    @Test
    @DisplayName("Each call produces a fresh value (no caching in the strategy)")
    void freshValuePerCall() {
        var recorder = new RecordingGenerator();
        var strategy = new AutoValueStrategy(recorder);

        var element = new XsdExtendedElement();
        element.setElementName("e");
        element.setElementType("xs:int");

        recorder.nextReturn = "1";
        assertEquals("1", strategy.resolve(element, Map.of(), context));
        recorder.nextReturn = "2";
        assertEquals("2", strategy.resolve(element, Map.of(), context));

        assertEquals(2, recorder.callCount);
    }

    @Test
    @DisplayName("Works with real XsdSampleDataGenerator for simple xs:string element")
    void worksWithRealGenerator() {
        var strategy = new AutoValueStrategy(new XsdSampleDataGenerator());

        var element = new XsdExtendedElement();
        element.setElementName("title");
        element.setElementType("xs:string");

        String value = strategy.resolve(element, Map.of(), context);

        assertNotNull(value, "Real generator must produce a non-null value for xs:string");
    }

    /** Lightweight subclass that records calls without hitting the real generator logic. */
    private static class RecordingGenerator extends XsdSampleDataGenerator {
        int callCount = 0;
        XsdExtendedElement lastElement;
        String nextReturn = "";

        @Override
        public String generate(XsdExtendedElement element) {
            callCount++;
            lastElement = element;
            return nextReturn;
        }
    }
}
