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

package org.fxt.freexmltoolkit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Xsd11Feature")
class Xsd11FeatureTest {

    @Nested
    @DisplayName("Enum Values")
    class EnumValuesTests {

        @Test
        @DisplayName("All 12 XSD 1.1 features are defined")
        void allFeaturesAreDefined() {
            assertEquals(12, Xsd11Feature.values().length);
        }

        @Test
        @DisplayName("ASSERTIONS feature exists")
        void assertionsFeatureExists() {
            Xsd11Feature feature = Xsd11Feature.ASSERTIONS;
            assertEquals("xs:assert", feature.getXmlTag());
            assertEquals("Assertions", feature.getDisplayName());
            assertTrue(feature.getDescription().contains("XPath 2.0"));
        }

        @Test
        @DisplayName("ALTERNATIVES feature exists")
        void alternativesFeatureExists() {
            Xsd11Feature feature = Xsd11Feature.ALTERNATIVES;
            assertEquals("xs:alternative", feature.getXmlTag());
            assertEquals("Type Alternatives", feature.getDisplayName());
        }

        @Test
        @DisplayName("ALL_EXTENSIONS feature exists")
        void allExtensionsFeatureExists() {
            Xsd11Feature feature = Xsd11Feature.ALL_EXTENSIONS;
            assertEquals("xs:all", feature.getXmlTag());
            assertEquals("Enhanced xs:all", feature.getDisplayName());
        }

        @Test
        @DisplayName("OVERRIDE feature exists")
        void overrideFeatureExists() {
            Xsd11Feature feature = Xsd11Feature.OVERRIDE;
            assertEquals("xs:override", feature.getXmlTag());
            assertEquals("Override", feature.getDisplayName());
        }

        @Test
        @DisplayName("OPEN_CONTENT feature exists")
        void openContentFeatureExists() {
            Xsd11Feature feature = Xsd11Feature.OPEN_CONTENT;
            assertEquals("xs:openContent", feature.getXmlTag());
            assertEquals("Open Content", feature.getDisplayName());
        }

        @Test
        @DisplayName("ENHANCED_WILDCARDS feature exists")
        void enhancedWildcardsFeatureExists() {
            Xsd11Feature feature = Xsd11Feature.ENHANCED_WILDCARDS;
            assertEquals("xs:any", feature.getXmlTag());
            assertEquals("Enhanced Wildcards", feature.getDisplayName());
        }

        @Test
        @DisplayName("NEW_BUILTIN_TYPES feature exists")
        void newBuiltinTypesFeatureExists() {
            Xsd11Feature feature = Xsd11Feature.NEW_BUILTIN_TYPES;
            assertEquals("xs:dateTimeStamp", feature.getXmlTag());
            assertEquals("New Built-in Types", feature.getDisplayName());
        }

        @Test
        @DisplayName("VERSION_INDICATOR feature exists")
        void versionIndicatorFeatureExists() {
            Xsd11Feature feature = Xsd11Feature.VERSION_INDICATOR;
            assertEquals("vc:minVersion", feature.getXmlTag());
            assertEquals("Version Indicator", feature.getDisplayName());
        }
    }

    @Nested
    @DisplayName("Properties")
    class PropertiesTests {

        @ParameterizedTest
        @EnumSource(Xsd11Feature.class)
        @DisplayName("All features have XML tag")
        void allFeaturesHaveXmlTag(Xsd11Feature feature) {
            assertNotNull(feature.getXmlTag());
            assertFalse(feature.getXmlTag().isEmpty());
        }

        @ParameterizedTest
        @EnumSource(Xsd11Feature.class)
        @DisplayName("All features have display name")
        void allFeaturesHaveDisplayName(Xsd11Feature feature) {
            assertNotNull(feature.getDisplayName());
            assertFalse(feature.getDisplayName().isEmpty());
        }

        @ParameterizedTest
        @EnumSource(Xsd11Feature.class)
        @DisplayName("All features have description")
        void allFeaturesHaveDescription(Xsd11Feature feature) {
            assertNotNull(feature.getDescription());
            assertFalse(feature.getDescription().isEmpty());
        }
    }

    @Nested
    @DisplayName("Critical Features")
    class CriticalFeaturesTests {

        @Test
        @DisplayName("ASSERTIONS is critical")
        void assertionsIsCritical() {
            assertTrue(Xsd11Feature.ASSERTIONS.isCritical());
        }

        @Test
        @DisplayName("ALTERNATIVES is critical")
        void alternativesIsCritical() {
            assertTrue(Xsd11Feature.ALTERNATIVES.isCritical());
        }

        @Test
        @DisplayName("OVERRIDE is critical")
        void overrideIsCritical() {
            assertTrue(Xsd11Feature.OVERRIDE.isCritical());
        }

        @Test
        @DisplayName("OPEN_CONTENT is critical")
        void openContentIsCritical() {
            assertTrue(Xsd11Feature.OPEN_CONTENT.isCritical());
        }

        @Test
        @DisplayName("VERSION_INDICATOR is critical")
        void versionIndicatorIsCritical() {
            assertTrue(Xsd11Feature.VERSION_INDICATOR.isCritical());
        }

        @Test
        @DisplayName("ALL_EXTENSIONS is not critical")
        void allExtensionsIsNotCritical() {
            assertFalse(Xsd11Feature.ALL_EXTENSIONS.isCritical());
        }

        @Test
        @DisplayName("ENHANCED_WILDCARDS is not critical")
        void enhancedWildcardsIsNotCritical() {
            assertFalse(Xsd11Feature.ENHANCED_WILDCARDS.isCritical());
        }

        @Test
        @DisplayName("NEW_BUILTIN_TYPES is not critical")
        void newBuiltinTypesIsNotCritical() {
            assertFalse(Xsd11Feature.NEW_BUILTIN_TYPES.isCritical());
        }

        @Test
        @DisplayName("EXPLICIT_TIMEZONE is not critical")
        void explicitTimezoneIsNotCritical() {
            assertFalse(Xsd11Feature.EXPLICIT_TIMEZONE.isCritical());
        }

        @Test
        @DisplayName("DEFAULT_ATTRIBUTES is not critical")
        void defaultAttributesIsNotCritical() {
            assertFalse(Xsd11Feature.DEFAULT_ATTRIBUTES.isCritical());
        }

        @Test
        @DisplayName("LOCAL_TARGET_NAMESPACE is not critical")
        void localTargetNamespaceIsNotCritical() {
            assertFalse(Xsd11Feature.LOCAL_TARGET_NAMESPACE.isCritical());
        }

        @Test
        @DisplayName("INHERITABLE_ATTRIBUTES is not critical")
        void inheritableAttributesIsNotCritical() {
            assertFalse(Xsd11Feature.INHERITABLE_ATTRIBUTES.isCritical());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString includes display name and XML tag")
        void toStringIncludesDisplayNameAndXmlTag() {
            Xsd11Feature feature = Xsd11Feature.ASSERTIONS;
            String str = feature.toString();

            assertTrue(str.contains("Assertions"));
            assertTrue(str.contains("xs:assert"));
        }

        @ParameterizedTest
        @EnumSource(Xsd11Feature.class)
        @DisplayName("All features have valid toString")
        void allFeaturesHaveValidToString(Xsd11Feature feature) {
            String str = feature.toString();

            assertNotNull(str);
            assertTrue(str.contains(feature.getDisplayName()));
            assertTrue(str.contains(feature.getXmlTag()));
        }
    }

    @Nested
    @DisplayName("valueOf and name")
    class ValueOfTests {

        @Test
        @DisplayName("valueOf works for ASSERTIONS")
        void valueOfAssertions() {
            assertEquals(Xsd11Feature.ASSERTIONS, Xsd11Feature.valueOf("ASSERTIONS"));
        }

        @Test
        @DisplayName("valueOf throws for invalid name")
        void valueOfThrowsForInvalid() {
            assertThrows(IllegalArgumentException.class,
                    () -> Xsd11Feature.valueOf("INVALID_FEATURE"));
        }

        @ParameterizedTest
        @EnumSource(Xsd11Feature.class)
        @DisplayName("name returns constant name")
        void nameReturnsConstantName(Xsd11Feature feature) {
            assertEquals(feature, Xsd11Feature.valueOf(feature.name()));
        }
    }
}
