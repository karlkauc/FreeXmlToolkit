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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UsageStatistics")
class UsageStatisticsTest {

    private UsageStatistics stats;

    @BeforeEach
    void setUp() {
        stats = new UsageStatistics();
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Initializes with current timestamps")
        void initializesWithCurrentTimestamps() {
            assertNotNull(stats.getFirstLaunch());
            assertNotNull(stats.getLastLaunch());
            assertTrue(stats.getFirstLaunch().isBefore(LocalDateTime.now().plusSeconds(1)));
            assertTrue(stats.getLastLaunch().isBefore(LocalDateTime.now().plusSeconds(1)));
        }

        @Test
        @DisplayName("Initializes with zero launches")
        void initializesWithZeroLaunches() {
            assertEquals(0, stats.getTotalLaunches());
        }

        @Test
        @DisplayName("Initializes empty maps")
        void initializesEmptyMaps() {
            assertNotNull(stats.getDailyStats());
            assertNotNull(stats.getFeatureUsage());
            assertTrue(stats.getDailyStats().isEmpty());
            assertTrue(stats.getFeatureUsage().isEmpty());
        }
    }

    @Nested
    @DisplayName("Launch Tracking")
    class LaunchTrackingTests {

        @Test
        @DisplayName("Increment launches")
        void incrementLaunches() {
            stats.incrementLaunches();
            stats.incrementLaunches();
            stats.incrementLaunches();

            assertEquals(3, stats.getTotalLaunches());
        }

        @Test
        @DisplayName("Set and get launches")
        void setAndGetLaunches() {
            stats.setTotalLaunches(10);
            assertEquals(10, stats.getTotalLaunches());
        }

        @Test
        @DisplayName("Set and get launch timestamps")
        void setAndGetLaunchTimestamps() {
            LocalDateTime first = LocalDateTime.of(2024, 1, 1, 10, 0);
            LocalDateTime last = LocalDateTime.of(2024, 12, 31, 18, 0);

            stats.setFirstLaunch(first);
            stats.setLastLaunch(last);

            assertEquals(first, stats.getFirstLaunch());
            assertEquals(last, stats.getLastLaunch());
        }
    }

    @Nested
    @DisplayName("Usage Time Tracking")
    class UsageTimeTests {

        @Test
        @DisplayName("Add usage seconds")
        void addUsageSeconds() {
            stats.addUsageSeconds(100);
            stats.addUsageSeconds(200);

            assertEquals(300, stats.getTotalUsageSeconds());
        }

        @Test
        @DisplayName("Set and get usage seconds")
        void setAndGetUsageSeconds() {
            stats.setTotalUsageSeconds(3600);
            assertEquals(3600, stats.getTotalUsageSeconds());
        }
    }

    @Nested
    @DisplayName("File Validation Metrics")
    class FileValidationTests {

        @Test
        @DisplayName("Increment files validated")
        void incrementFilesValidated() {
            stats.incrementFilesValidated();
            stats.incrementFilesValidated();

            assertEquals(2, stats.getFilesValidated());
        }

        @Test
        @DisplayName("Add validation errors")
        void addValidationErrors() {
            stats.addValidationErrors(5);
            stats.addValidationErrors(3);

            assertEquals(8, stats.getValidationErrors());
        }

        @Test
        @DisplayName("Increment errors corrected")
        void incrementErrorsCorrected() {
            stats.incrementErrorsCorrected(2);
            stats.incrementErrorsCorrected(3);

            assertEquals(5, stats.getErrorsCorrected());
        }

        @Test
        @DisplayName("Increment files opened")
        void incrementFilesOpened() {
            stats.incrementFilesOpened();
            stats.incrementFilesOpened();
            stats.incrementFilesOpened();

            assertEquals(3, stats.getFilesOpened());
        }
    }

    @Nested
    @DisplayName("Transformation Metrics")
    class TransformationTests {

        @Test
        @DisplayName("Increment transformations")
        void incrementTransformations() {
            stats.incrementTransformations();
            stats.incrementTransformations();

            assertEquals(2, stats.getTransformationsPerformed());
        }

        @Test
        @DisplayName("Increment documents formatted")
        void incrementDocumentsFormatted() {
            stats.incrementDocumentsFormatted();

            assertEquals(1, stats.getDocumentsFormatted());
        }
    }

    @Nested
    @DisplayName("Query Metrics")
    class QueryMetricsTests {

        @Test
        @DisplayName("Increment XPath queries")
        void incrementXpathQueries() {
            stats.incrementXpathQueries();
            stats.incrementXpathQueries();

            assertEquals(2, stats.getXpathQueriesExecuted());
        }

        @Test
        @DisplayName("Increment XQuery executions")
        void incrementXqueryExecutions() {
            stats.incrementXqueryExecutions();

            assertEquals(1, stats.getXqueryExecutions());
        }

        @Test
        @DisplayName("Increment Schematron validations")
        void incrementSchematronValidations() {
            stats.incrementSchematronValidations();
            stats.incrementSchematronValidations();

            assertEquals(2, stats.getSchematronValidations());
        }
    }

    @Nested
    @DisplayName("Schema and Signature Metrics")
    class SchemaSignatureTests {

        @Test
        @DisplayName("Increment schemas generated")
        void incrementSchemasGenerated() {
            stats.incrementSchemasGenerated();

            assertEquals(1, stats.getSchemasGenerated());
        }

        @Test
        @DisplayName("Increment signatures created")
        void incrementSignaturesCreated() {
            stats.incrementSignaturesCreated();
            stats.incrementSignaturesCreated();

            assertEquals(2, stats.getSignaturesCreated());
        }

        @Test
        @DisplayName("Increment signatures verified")
        void incrementSignaturesVerified() {
            stats.incrementSignaturesVerified();

            assertEquals(1, stats.getSignaturesVerified());
        }

        @Test
        @DisplayName("Increment PDFs generated")
        void incrementPdfsGenerated() {
            stats.incrementPdfsGenerated();
            stats.incrementPdfsGenerated();
            stats.incrementPdfsGenerated();

            assertEquals(3, stats.getPdfsGenerated());
        }
    }

    @Nested
    @DisplayName("Daily Statistics")
    class DailyStatsTests {

        @Test
        @DisplayName("Get today stats creates new if not exists")
        void getTodayStatsCreatesNew() {
            DailyStatistics todayStats = stats.getTodayStats();

            assertNotNull(todayStats);
            assertEquals(LocalDate.now(), todayStats.getDate());
        }

        @Test
        @DisplayName("Get today stats returns same instance")
        void getTodayStatsReturnsSameInstance() {
            DailyStatistics first = stats.getTodayStats();
            DailyStatistics second = stats.getTodayStats();

            assertSame(first, second);
        }

        @Test
        @DisplayName("Set daily stats with null initializes empty map")
        void setDailyStatsWithNull() {
            stats.setDailyStats(null);

            assertNotNull(stats.getDailyStats());
            assertTrue(stats.getDailyStats().isEmpty());
        }

        @Test
        @DisplayName("Set daily stats with map")
        void setDailyStatsWithMap() {
            Map<LocalDate, DailyStatistics> dailyStats = new HashMap<>();
            dailyStats.put(LocalDate.now(), new DailyStatistics());

            stats.setDailyStats(dailyStats);

            assertEquals(1, stats.getDailyStats().size());
        }
    }

    @Nested
    @DisplayName("Feature Usage")
    class FeatureUsageTests {

        @Test
        @DisplayName("Track feature creates new usage")
        void trackFeatureCreatesNewUsage() {
            stats.trackFeature("xml-validation", "XML Validation", "Validation");

            FeatureUsage usage = stats.getFeatureUsage("xml-validation");
            assertNotNull(usage);
            assertEquals("xml-validation", usage.getFeatureId());
            assertEquals("XML Validation", usage.getFeatureName());
            assertEquals("Validation", usage.getCategory());
            assertEquals(1, usage.getUseCount());
            assertTrue(usage.isDiscovered());
            assertNotNull(usage.getFirstUsed());
            assertNotNull(usage.getLastUsed());
        }

        @Test
        @DisplayName("Track feature increments existing usage")
        void trackFeatureIncrementsExisting() {
            stats.trackFeature("xml-validation", "XML Validation", "Validation");
            stats.trackFeature("xml-validation", "XML Validation", "Validation");
            stats.trackFeature("xml-validation", "XML Validation", "Validation");

            FeatureUsage usage = stats.getFeatureUsage("xml-validation");
            assertEquals(3, usage.getUseCount());
        }

        @Test
        @DisplayName("Get feature usage returns null for unknown")
        void getFeatureUsageReturnsNullForUnknown() {
            assertNull(stats.getFeatureUsage("unknown-feature"));
        }

        @Test
        @DisplayName("Set feature usage with null initializes empty map")
        void setFeatureUsageWithNull() {
            stats.setFeatureUsage(null);

            assertNotNull(stats.getFeatureUsage());
        }

        @Test
        @DisplayName("Get discovered features count")
        void getDiscoveredFeaturesCount() {
            stats.trackFeature("feature1", "Feature 1", "Cat1");
            stats.trackFeature("feature2", "Feature 2", "Cat1");
            stats.trackFeature("feature3", "Feature 3", "Cat2");

            assertEquals(3, stats.getDiscoveredFeaturesCount());
        }
    }

    @Nested
    @DisplayName("Activity Metrics")
    class ActivityMetricsTests {

        @Test
        @DisplayName("Get total activity count")
        void getTotalActivityCount() {
            stats.setFilesValidated(10);
            stats.setTransformationsPerformed(5);
            stats.setDocumentsFormatted(3);
            stats.setXpathQueriesExecuted(20);
            stats.setSchematronValidations(2);

            assertEquals(40, stats.getTotalActivityCount());
        }

        @Test
        @DisplayName("Get active days last 7 with no activity")
        void getActiveDaysLast7NoActivity() {
            assertEquals(0, stats.getActiveDaysLast7());
        }

        @Test
        @DisplayName("Get active days last 7 with activity")
        void getActiveDaysLast7WithActivity() {
            LocalDate today = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);
            LocalDate twoDaysAgo = today.minusDays(2);

            DailyStatistics todayStats = new DailyStatistics(today);
            todayStats.incrementFilesValidated();

            DailyStatistics yesterdayStats = new DailyStatistics(yesterday);
            yesterdayStats.incrementTransformations();

            DailyStatistics twoDaysAgoStats = new DailyStatistics(twoDaysAgo);
            // No activity

            Map<LocalDate, DailyStatistics> dailyStats = new HashMap<>();
            dailyStats.put(today, todayStats);
            dailyStats.put(yesterday, yesterdayStats);
            dailyStats.put(twoDaysAgo, twoDaysAgoStats);

            stats.setDailyStats(dailyStats);

            assertEquals(2, stats.getActiveDaysLast7());
        }
    }

    @Nested
    @DisplayName("Cleanup")
    class CleanupTests {

        @Test
        @DisplayName("Cleanup removes old daily stats")
        void cleanupRemovesOldDailyStats() {
            LocalDate today = LocalDate.now();
            LocalDate oldDate = today.minusDays(35);
            LocalDate recentDate = today.minusDays(5);

            Map<LocalDate, DailyStatistics> dailyStats = new HashMap<>();
            dailyStats.put(today, new DailyStatistics(today));
            dailyStats.put(oldDate, new DailyStatistics(oldDate));
            dailyStats.put(recentDate, new DailyStatistics(recentDate));

            stats.setDailyStats(dailyStats);
            stats.cleanupOldDailyStats();

            assertEquals(2, stats.getDailyStats().size());
            assertNull(stats.getDailyStats().get(oldDate));
            assertNotNull(stats.getDailyStats().get(today));
            assertNotNull(stats.getDailyStats().get(recentDate));
        }
    }

    @Nested
    @DisplayName("Setters")
    class SetterTests {

        @Test
        @DisplayName("All setters work correctly")
        void allSettersWork() {
            stats.setFilesValidated(10);
            stats.setValidationErrors(5);
            stats.setErrorsCorrected(3);
            stats.setTransformationsPerformed(7);
            stats.setDocumentsFormatted(2);
            stats.setXpathQueriesExecuted(15);
            stats.setXqueryExecutions(4);
            stats.setSchematronValidations(6);
            stats.setSchemasGenerated(1);
            stats.setSignaturesCreated(2);
            stats.setSignaturesVerified(3);
            stats.setPdfsGenerated(4);
            stats.setFilesOpened(20);

            assertEquals(10, stats.getFilesValidated());
            assertEquals(5, stats.getValidationErrors());
            assertEquals(3, stats.getErrorsCorrected());
            assertEquals(7, stats.getTransformationsPerformed());
            assertEquals(2, stats.getDocumentsFormatted());
            assertEquals(15, stats.getXpathQueriesExecuted());
            assertEquals(4, stats.getXqueryExecutions());
            assertEquals(6, stats.getSchematronValidations());
            assertEquals(1, stats.getSchemasGenerated());
            assertEquals(2, stats.getSignaturesCreated());
            assertEquals(3, stats.getSignaturesVerified());
            assertEquals(4, stats.getPdfsGenerated());
            assertEquals(20, stats.getFilesOpened());
        }
    }
}
