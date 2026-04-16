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

package org.fxt.freexmltoolkit.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("XPathRule")
class XPathRuleTest {

    @Nested
    @DisplayName("Defaults")
    class DefaultsTests {

        @Test
        @DisplayName("Default constructor sets AUTO strategy")
        void defaultStrategy() {
            var rule = new XPathRule();
            assertEquals(GenerationStrategy.AUTO, rule.getStrategy());
            assertEquals(0, rule.getPriority());
            assertTrue(rule.isEnabled());
            assertNotNull(rule.getConfig());
            assertTrue(rule.getConfig().isEmpty());
        }

        @Test
        @DisplayName("Two-arg constructor sets xpath and strategy")
        void twoArgConstructor() {
            var rule = new XPathRule("/order/@id", GenerationStrategy.SEQUENCE);
            assertEquals("/order/@id", rule.getXpath());
            assertEquals(GenerationStrategy.SEQUENCE, rule.getStrategy());
            assertTrue(rule.isEnabled());
        }

        @Test
        @DisplayName("Three-arg constructor sets config map")
        void threeArgConstructor() {
            var config = Map.of("value", "test");
            var rule = new XPathRule("/a", GenerationStrategy.FIXED, config);
            assertEquals("test", rule.getConfigValue("value"));
        }

        @Test
        @DisplayName("Null config in constructor creates empty map")
        void nullConfigInConstructor() {
            var rule = new XPathRule("/a", GenerationStrategy.FIXED, null);
            assertNotNull(rule.getConfig());
            assertTrue(rule.getConfig().isEmpty());
        }
    }

    @Nested
    @DisplayName("Config management")
    class ConfigTests {

        @Test
        @DisplayName("setConfigValue adds to config map")
        void setConfigValue() {
            var rule = new XPathRule();
            rule.setConfigValue("pattern", "ID-{seq:4}");
            rule.setConfigValue("start", "1");
            assertEquals("ID-{seq:4}", rule.getConfigValue("pattern"));
            assertEquals("1", rule.getConfigValue("start"));
        }

        @Test
        @DisplayName("getConfigValue returns null for missing key")
        void missingConfigValue() {
            var rule = new XPathRule();
            assertNull(rule.getConfigValue("nonexistent"));
        }

        @Test
        @DisplayName("setConfig with null creates empty map")
        void setConfigNull() {
            var rule = new XPathRule();
            rule.setConfigValue("key", "value");
            rule.setConfig(null);
            assertNotNull(rule.getConfig());
            assertTrue(rule.getConfig().isEmpty());
        }

        @Test
        @DisplayName("setConfig creates defensive copy")
        void setConfigDefensiveCopy() {
            var original = new HashMap<String, String>();
            original.put("key", "value");
            var rule = new XPathRule();
            rule.setConfig(original);
            original.put("extra", "data");
            assertNull(rule.getConfigValue("extra"));
        }
    }

    @Nested
    @DisplayName("Deep copy")
    class DeepCopyTests {

        @Test
        @DisplayName("Deep copy creates independent copy")
        void deepCopyIsIndependent() {
            var original = new XPathRule("/order/@id", GenerationStrategy.SEQUENCE,
                    Map.of("pattern", "ID-{seq:4}", "start", "1"));
            original.setPriority(5);
            original.setEnabled(false);

            var copy = original.deepCopy();
            assertEquals(original.getXpath(), copy.getXpath());
            assertEquals(original.getStrategy(), copy.getStrategy());
            assertEquals(original.getPriority(), copy.getPriority());
            assertEquals(original.isEnabled(), copy.isEnabled());
            assertEquals(original.getConfig(), copy.getConfig());

            // Modify copy, original unchanged
            copy.setXpath("/other");
            copy.getConfig().put("pattern", "CHANGED");
            assertEquals("/order/@id", original.getXpath());
            assertEquals("ID-{seq:4}", original.getConfigValue("pattern"));
        }
    }

    @Nested
    @DisplayName("Equality")
    class EqualityTests {

        @Test
        @DisplayName("Equal rules are equal")
        void equalRules() {
            var a = new XPathRule("/a", GenerationStrategy.FIXED, Map.of("value", "X"));
            var b = new XPathRule("/a", GenerationStrategy.FIXED, Map.of("value", "X"));
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("Different xpath means not equal")
        void differentXpath() {
            var a = new XPathRule("/a", GenerationStrategy.FIXED);
            var b = new XPathRule("/b", GenerationStrategy.FIXED);
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("Different strategy means not equal")
        void differentStrategy() {
            var a = new XPathRule("/a", GenerationStrategy.FIXED);
            var b = new XPathRule("/a", GenerationStrategy.OMIT);
            assertNotEquals(a, b);
        }
    }

    @Test
    @DisplayName("toString contains xpath and strategy")
    void toStringContainsInfo() {
        var rule = new XPathRule("/order/@id", GenerationStrategy.SEQUENCE);
        String str = rule.toString();
        assertTrue(str.contains("/order/@id"));
        assertTrue(str.contains("Sequence") || str.contains("SEQUENCE"));
    }
}
