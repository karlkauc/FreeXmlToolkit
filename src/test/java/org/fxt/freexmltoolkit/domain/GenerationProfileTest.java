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

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

@DisplayName("GenerationProfile")
class GenerationProfileTest {

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .setPrettyPrinting()
            .create();

    @Nested
    @DisplayName("Defaults")
    class DefaultsTests {

        @Test
        @DisplayName("Default constructor sets sensible defaults")
        void defaultConstructor() {
            var profile = new GenerationProfile();
            assertEquals(1, profile.getBatchCount());
            assertEquals("example_{seq:3}.xml", profile.getFileNamePattern());
            assertFalse(profile.isMandatoryOnly());
            assertEquals(3, profile.getMaxOccurrences());
            assertNotNull(profile.getRules());
            assertTrue(profile.getRules().isEmpty());
            assertNotNull(profile.getCreatedAt());
            assertNotNull(profile.getUpdatedAt());
        }

        @Test
        @DisplayName("Name constructor sets name and defaults")
        void nameConstructor() {
            var profile = new GenerationProfile("Test Profile");
            assertEquals("Test Profile", profile.getName());
            assertEquals(1, profile.getBatchCount());
        }
    }

    @Nested
    @DisplayName("Setters with validation")
    class SetterTests {

        @Test
        @DisplayName("setBatchCount enforces minimum of 1")
        void batchCountMinimum() {
            var profile = new GenerationProfile();
            profile.setBatchCount(0);
            assertEquals(1, profile.getBatchCount());
            profile.setBatchCount(-5);
            assertEquals(1, profile.getBatchCount());
            profile.setBatchCount(10);
            assertEquals(10, profile.getBatchCount());
        }

        @Test
        @DisplayName("setMaxOccurrences enforces minimum of 1")
        void maxOccurrencesMinimum() {
            var profile = new GenerationProfile();
            profile.setMaxOccurrences(0);
            assertEquals(1, profile.getMaxOccurrences());
            profile.setMaxOccurrences(-3);
            assertEquals(1, profile.getMaxOccurrences());
            profile.setMaxOccurrences(5);
            assertEquals(5, profile.getMaxOccurrences());
        }

        @Test
        @DisplayName("setRules with null creates empty list")
        void setRulesNull() {
            var profile = new GenerationProfile();
            profile.setRules(null);
            assertNotNull(profile.getRules());
            assertTrue(profile.getRules().isEmpty());
        }
    }

    @Nested
    @DisplayName("Rule management")
    class RuleManagementTests {

        @Test
        @DisplayName("addRule and removeRule work correctly")
        void addAndRemoveRule() {
            var profile = new GenerationProfile();
            var rule = new XPathRule("/order/@id", GenerationStrategy.SEQUENCE);
            profile.addRule(rule);
            assertEquals(1, profile.getRules().size());
            assertSame(rule, profile.getRules().get(0));

            profile.removeRule(rule);
            assertTrue(profile.getRules().isEmpty());
        }

        @Test
        @DisplayName("getEnabledRules filters disabled rules")
        void enabledRulesFilter() {
            var profile = new GenerationProfile();
            var enabledRule = new XPathRule("/order/@id", GenerationStrategy.SEQUENCE);
            var disabledRule = new XPathRule("/order/notes", GenerationStrategy.OMIT);
            disabledRule.setEnabled(false);

            profile.addRule(enabledRule);
            profile.addRule(disabledRule);

            assertEquals(2, profile.getRules().size());
            assertEquals(1, profile.getEnabledRules().size());
            assertEquals(enabledRule, profile.getEnabledRules().get(0));
        }
    }

    @Nested
    @DisplayName("Deep copy")
    class DeepCopyTests {

        @Test
        @DisplayName("Deep copy creates independent copy")
        void deepCopyIsIndependent() {
            var original = new GenerationProfile("Original");
            original.setDescription("Test");
            original.setSchemaFile("test.xsd");
            original.setBatchCount(5);
            original.setMandatoryOnly(true);
            original.addRule(new XPathRule("/a", GenerationStrategy.FIXED, Map.of("value", "X")));

            var copy = original.deepCopy();

            assertEquals(original.getName(), copy.getName());
            assertEquals(original.getDescription(), copy.getDescription());
            assertEquals(original.getSchemaFile(), copy.getSchemaFile());
            assertEquals(original.getBatchCount(), copy.getBatchCount());
            assertEquals(original.isMandatoryOnly(), copy.isMandatoryOnly());
            assertEquals(1, copy.getRules().size());

            // Modify copy, original should be unchanged
            copy.setName("Modified");
            copy.getRules().get(0).setXpath("/b");
            assertEquals("Original", original.getName());
            assertEquals("/a", original.getRules().get(0).getXpath());
        }
    }

    @Nested
    @DisplayName("JSON serialization")
    class SerializationTests {

        @Test
        @DisplayName("Round-trip serialization preserves all fields")
        void roundTrip() {
            var original = new GenerationProfile("Test Profile");
            original.setDescription("A test profile");
            original.setSchemaFile("order.xsd");
            original.setBatchCount(5);
            original.setFileNamePattern("order_{seq:3}.xml");
            original.setMandatoryOnly(true);
            original.setMaxOccurrences(2);
            original.addRule(new XPathRule("/order/@id", GenerationStrategy.SEQUENCE,
                    Map.of("pattern", "ORD-{seq:6}", "start", "1")));
            original.addRule(new XPathRule("/order/notes", GenerationStrategy.OMIT));

            String json = gson.toJson(original);
            var restored = gson.fromJson(json, GenerationProfile.class);

            assertEquals(original.getName(), restored.getName());
            assertEquals(original.getDescription(), restored.getDescription());
            assertEquals(original.getSchemaFile(), restored.getSchemaFile());
            assertEquals(original.getBatchCount(), restored.getBatchCount());
            assertEquals(original.getFileNamePattern(), restored.getFileNamePattern());
            assertEquals(original.isMandatoryOnly(), restored.isMandatoryOnly());
            assertEquals(original.getMaxOccurrences(), restored.getMaxOccurrences());
            assertEquals(2, restored.getRules().size());
            assertEquals(GenerationStrategy.SEQUENCE, restored.getRules().get(0).getStrategy());
            assertEquals("ORD-{seq:6}", restored.getRules().get(0).getConfigValue("pattern"));
            assertEquals(GenerationStrategy.OMIT, restored.getRules().get(1).getStrategy());
        }

        @Test
        @DisplayName("JSON contains expected structure")
        void jsonStructure() {
            var profile = new GenerationProfile("Simple");
            profile.addRule(new XPathRule("/root", GenerationStrategy.FIXED, Map.of("value", "test")));

            String json = gson.toJson(profile);
            assertTrue(json.contains("\"name\": \"Simple\""));
            assertTrue(json.contains("\"strategy\": \"FIXED\""));
            assertTrue(json.contains("\"value\": \"test\""));
        }
    }

    @Nested
    @DisplayName("Equality")
    class EqualityTests {

        @Test
        @DisplayName("Equality is based on name")
        void equalityByName() {
            var a = new GenerationProfile("Same");
            var b = new GenerationProfile("Same");
            b.setBatchCount(10); // different config but same name
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("Different names are not equal")
        void differentNames() {
            var a = new GenerationProfile("A");
            var b = new GenerationProfile("B");
            assertNotEquals(a, b);
        }
    }

    @Test
    @DisplayName("touch() updates updatedAt timestamp")
    void touchUpdatesTimestamp() throws InterruptedException {
        var profile = new GenerationProfile();
        var before = profile.getUpdatedAt();
        Thread.sleep(10);
        profile.touch();
        assertTrue(profile.getUpdatedAt().isAfter(before));
    }

    private static class LocalDateTimeAdapter implements JsonSerializer<LocalDateTime>,
            JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(formatter.format(src));
        }

        @Override
        public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            return LocalDateTime.parse(json.getAsString(), formatter);
        }
    }
}
