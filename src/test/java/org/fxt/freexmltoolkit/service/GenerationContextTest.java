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

package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("GenerationContext")
class GenerationContextTest {

    @Nested
    @DisplayName("Sequence counters")
    class SequenceTests {

        @Test
        @DisplayName("Sequence starts at configured start value")
        void startsAtConfiguredValue() {
            var ctx = new GenerationContext();
            assertEquals(1, ctx.nextSequenceValue("id", 1, 1));
        }

        @Test
        @DisplayName("Sequence increments by step")
        void incrementsByStep() {
            var ctx = new GenerationContext();
            assertEquals(10, ctx.nextSequenceValue("id", 10, 5));
            assertEquals(15, ctx.nextSequenceValue("id", 10, 5));
            assertEquals(20, ctx.nextSequenceValue("id", 10, 5));
        }

        @Test
        @DisplayName("Different rule IDs have independent counters")
        void independentCounters() {
            var ctx = new GenerationContext();
            assertEquals(1, ctx.nextSequenceValue("a", 1, 1));
            assertEquals(100, ctx.nextSequenceValue("b", 100, 10));
            assertEquals(2, ctx.nextSequenceValue("a", 1, 1));
            assertEquals(110, ctx.nextSequenceValue("b", 100, 10));
        }

        @Test
        @DisplayName("Sequence counters persist after resetForNewFile")
        void persistsAfterReset() {
            var ctx = new GenerationContext();
            ctx.nextSequenceValue("id", 1, 1); // 1
            ctx.nextSequenceValue("id", 1, 1); // 2
            ctx.resetForNewFile();
            assertEquals(3, ctx.nextSequenceValue("id", 1, 1));
        }
    }

    @Nested
    @DisplayName("Generated values")
    class GeneratedValuesTests {

        @Test
        @DisplayName("Record and retrieve values")
        void recordAndRetrieve() {
            var ctx = new GenerationContext();
            ctx.recordGeneratedValue("/order/@id", "ORD-001");
            assertEquals("ORD-001", ctx.getGeneratedValue("/order/@id"));
            assertTrue(ctx.hasGeneratedValue("/order/@id"));
        }

        @Test
        @DisplayName("Missing value returns null")
        void missingReturnsNull() {
            var ctx = new GenerationContext();
            assertNull(ctx.getGeneratedValue("/nonexistent"));
            assertFalse(ctx.hasGeneratedValue("/nonexistent"));
        }

        @Test
        @DisplayName("Generated values cleared on resetForNewFile")
        void clearedOnReset() {
            var ctx = new GenerationContext();
            ctx.recordGeneratedValue("/order/@id", "ORD-001");
            ctx.resetForNewFile();
            assertNull(ctx.getGeneratedValue("/order/@id"));
        }
    }

    @Nested
    @DisplayName("Enum cycle positions")
    class EnumCycleTests {

        @Test
        @DisplayName("Cycles through positions")
        void cycleThroughPositions() {
            var ctx = new GenerationContext();
            assertEquals(0, ctx.nextEnumPosition("status", 3));
            assertEquals(1, ctx.nextEnumPosition("status", 3));
            assertEquals(2, ctx.nextEnumPosition("status", 3));
            assertEquals(0, ctx.nextEnumPosition("status", 3)); // wrap around
        }

        @Test
        @DisplayName("Enum positions persist after resetForNewFile")
        void persistsAfterReset() {
            var ctx = new GenerationContext();
            ctx.nextEnumPosition("status", 3); // 0
            ctx.nextEnumPosition("status", 3); // 1
            ctx.resetForNewFile();
            assertEquals(2, ctx.nextEnumPosition("status", 3)); // continues
        }

        @Test
        @DisplayName("Zero enum size returns 0")
        void zeroSizeReturnsZero() {
            var ctx = new GenerationContext();
            assertEquals(0, ctx.nextEnumPosition("empty", 0));
        }
    }

    @Nested
    @DisplayName("File index")
    class FileIndexTests {

        @Test
        @DisplayName("File index starts at 0")
        void startsAtZero() {
            var ctx = new GenerationContext();
            assertEquals(0, ctx.getFileIndex());
        }

        @Test
        @DisplayName("resetForNewFile increments file index")
        void incrementsOnReset() {
            var ctx = new GenerationContext();
            ctx.resetForNewFile();
            assertEquals(1, ctx.getFileIndex());
            ctx.resetForNewFile();
            assertEquals(2, ctx.getFileIndex());
        }
    }

    @Test
    @DisplayName("resetAll clears everything")
    void resetAllClearsEverything() {
        var ctx = new GenerationContext();
        ctx.nextSequenceValue("id", 1, 1);
        ctx.recordGeneratedValue("/a", "val");
        ctx.nextEnumPosition("e", 3);
        ctx.setFileIndex(5);
        ctx.setCurrentXPath("/test");

        ctx.resetAll();

        assertEquals(1, ctx.nextSequenceValue("id", 1, 1)); // restarted
        assertNull(ctx.getGeneratedValue("/a"));
        assertEquals(0, ctx.nextEnumPosition("e", 3)); // restarted
        assertEquals(0, ctx.getFileIndex());
        assertNull(ctx.getCurrentXPath());
    }
}
