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

package org.fxt.freexmltoolkit.domain.statistics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FeatureUsage")
class FeatureUsageTest {

    private FeatureUsage usage;

    @BeforeEach
    void setUp() {
        usage = new FeatureUsage();
    }

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor initializes defaults")
        void defaultConstructorInitializesDefaults() {
            assertEquals(0, usage.getUseCount());
            assertFalse(usage.isDiscovered());
            assertNull(usage.getFeatureId());
            assertNull(usage.getFeatureName());
            assertNull(usage.getCategory());
        }

        @Test
        @DisplayName("Parameterized constructor sets values")
        void parameterizedConstructorSetsValues() {
            FeatureUsage feature = new FeatureUsage("xml-validation", "XML Validation", "Validation");

            assertEquals("xml-validation", feature.getFeatureId());
            assertEquals("XML Validation", feature.getFeatureName());
            assertEquals("Validation", feature.getCategory());
            assertEquals(0, feature.getUseCount());
            assertFalse(feature.isDiscovered());
        }
    }

    @Nested
    @DisplayName("Use Count")
    class UseCountTests {

        @Test
        @DisplayName("Increment use count")
        void incrementUseCount() {
            usage.incrementUseCount();
            usage.incrementUseCount();
            usage.incrementUseCount();

            assertEquals(3, usage.getUseCount());
        }

        @Test
        @DisplayName("Set use count")
        void setUseCount() {
            usage.setUseCount(10);
            assertEquals(10, usage.getUseCount());
        }
    }

    @Nested
    @DisplayName("Discovery Tracking")
    class DiscoveryTests {

        @Test
        @DisplayName("Set discovered")
        void setDiscovered() {
            usage.setDiscovered(true);
            assertTrue(usage.isDiscovered());
        }

        @Test
        @DisplayName("Set first used")
        void setFirstUsed() {
            LocalDateTime now = LocalDateTime.now();
            usage.setFirstUsed(now);

            assertEquals(now, usage.getFirstUsed());
        }

        @Test
        @DisplayName("Set last used")
        void setLastUsed() {
            LocalDateTime now = LocalDateTime.now();
            usage.setLastUsed(now);

            assertEquals(now, usage.getLastUsed());
        }
    }

    @Nested
    @DisplayName("UsageLevel Enum")
    class UsageLevelTests {

        @Test
        @DisplayName("All usage levels have descriptions")
        void allLevelsHaveDescriptions() {
            for (FeatureUsage.UsageLevel level : FeatureUsage.UsageLevel.values()) {
                assertNotNull(level.getDescription());
                assertFalse(level.getDescription().isEmpty());
            }
        }

        @Test
        @DisplayName("NEVER level has correct description")
        void neverLevelDescription() {
            assertEquals("Never used", FeatureUsage.UsageLevel.NEVER.getDescription());
        }

        @Test
        @DisplayName("EXPERT level has correct description")
        void expertLevelDescription() {
            assertEquals("Expert level", FeatureUsage.UsageLevel.EXPERT.getDescription());
        }
    }

    @Nested
    @DisplayName("Get Usage Level")
    class GetUsageLevelTests {

        @Test
        @DisplayName("NEVER when use count is 0")
        void neverWhenZero() {
            usage.setUseCount(0);
            assertEquals(FeatureUsage.UsageLevel.NEVER, usage.getUsageLevel());
        }

        @Test
        @DisplayName("RARELY when use count is 1-4")
        void rarelyWhenLowCount() {
            usage.setUseCount(1);
            assertEquals(FeatureUsage.UsageLevel.RARELY, usage.getUsageLevel());

            usage.setUseCount(4);
            assertEquals(FeatureUsage.UsageLevel.RARELY, usage.getUsageLevel());
        }

        @Test
        @DisplayName("OCCASIONALLY when use count is 5-19")
        void occasionallyWhenMediumCount() {
            usage.setUseCount(5);
            assertEquals(FeatureUsage.UsageLevel.OCCASIONALLY, usage.getUsageLevel());

            usage.setUseCount(19);
            assertEquals(FeatureUsage.UsageLevel.OCCASIONALLY, usage.getUsageLevel());
        }

        @Test
        @DisplayName("FREQUENTLY when use count is 20-49")
        void frequentlyWhenHighCount() {
            usage.setUseCount(20);
            assertEquals(FeatureUsage.UsageLevel.FREQUENTLY, usage.getUsageLevel());

            usage.setUseCount(49);
            assertEquals(FeatureUsage.UsageLevel.FREQUENTLY, usage.getUsageLevel());
        }

        @Test
        @DisplayName("EXPERT when use count is 50+")
        void expertWhenVeryHighCount() {
            usage.setUseCount(50);
            assertEquals(FeatureUsage.UsageLevel.EXPERT, usage.getUsageLevel());

            usage.setUseCount(100);
            assertEquals(FeatureUsage.UsageLevel.EXPERT, usage.getUsageLevel());

            usage.setUseCount(1000);
            assertEquals(FeatureUsage.UsageLevel.EXPERT, usage.getUsageLevel());
        }
    }

    @Nested
    @DisplayName("Setters")
    class SetterTests {

        @Test
        @DisplayName("Set feature ID")
        void setFeatureId() {
            usage.setFeatureId("test-feature");
            assertEquals("test-feature", usage.getFeatureId());
        }

        @Test
        @DisplayName("Set feature name")
        void setFeatureName() {
            usage.setFeatureName("Test Feature");
            assertEquals("Test Feature", usage.getFeatureName());
        }

        @Test
        @DisplayName("Set category")
        void setCategory() {
            usage.setCategory("Testing");
            assertEquals("Testing", usage.getCategory());
        }
    }

    @Nested
    @DisplayName("Real World Scenarios")
    class RealWorldTests {

        @Test
        @DisplayName("Track feature usage over time")
        void trackUsageOverTime() {
            FeatureUsage feature = new FeatureUsage("xslt-transform", "XSLT Transform", "Transformation");

            // First use
            feature.setDiscovered(true);
            feature.setFirstUsed(LocalDateTime.now().minusDays(10));
            feature.incrementUseCount();
            feature.setLastUsed(LocalDateTime.now().minusDays(10));

            // Multiple uses over time
            for (int i = 0; i < 24; i++) {
                feature.incrementUseCount();
            }
            feature.setLastUsed(LocalDateTime.now());

            assertEquals(25, feature.getUseCount());
            assertTrue(feature.isDiscovered());
            assertEquals(FeatureUsage.UsageLevel.FREQUENTLY, feature.getUsageLevel());
            assertTrue(feature.getFirstUsed().isBefore(feature.getLastUsed()));
        }
    }
}
