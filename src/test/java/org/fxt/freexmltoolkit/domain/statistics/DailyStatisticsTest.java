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

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DailyStatistics")
class DailyStatisticsTest {

    private DailyStatistics stats;

    @BeforeEach
    void setUp() {
        stats = new DailyStatistics();
    }

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor uses today's date")
        void defaultConstructorUsesTodaysDate() {
            assertEquals(LocalDate.now(), stats.getDate());
        }

        @Test
        @DisplayName("Parameterized constructor sets date")
        void parameterizedConstructorSetsDate() {
            LocalDate specificDate = LocalDate.of(2024, 6, 15);
            DailyStatistics dateStats = new DailyStatistics(specificDate);

            assertEquals(specificDate, dateStats.getDate());
        }

        @Test
        @DisplayName("Constructor initializes all metrics to zero")
        void constructorInitializesMetricsToZero() {
            assertEquals(0, stats.getFilesValidated());
            assertEquals(0, stats.getErrorsFound());
            assertEquals(0, stats.getErrorsFixed());
            assertEquals(0, stats.getTransformations());
            assertEquals(0, stats.getFormattings());
            assertEquals(0, stats.getXpathQueries());
            assertEquals(0, stats.getSchematronValidations());
            assertEquals(0, stats.getUsageMinutes());
        }
    }

    @Nested
    @DisplayName("Files Validated")
    class FilesValidatedTests {

        @Test
        @DisplayName("Increment files validated")
        void incrementFilesValidated() {
            stats.incrementFilesValidated();
            stats.incrementFilesValidated();
            stats.incrementFilesValidated();

            assertEquals(3, stats.getFilesValidated());
        }

        @Test
        @DisplayName("Set files validated")
        void setFilesValidated() {
            stats.setFilesValidated(10);
            assertEquals(10, stats.getFilesValidated());
        }
    }

    @Nested
    @DisplayName("Errors")
    class ErrorsTests {

        @Test
        @DisplayName("Add errors found")
        void addErrorsFound() {
            stats.addErrorsFound(5);
            stats.addErrorsFound(3);

            assertEquals(8, stats.getErrorsFound());
        }

        @Test
        @DisplayName("Set errors found")
        void setErrorsFound() {
            stats.setErrorsFound(15);
            assertEquals(15, stats.getErrorsFound());
        }

        @Test
        @DisplayName("Add errors fixed")
        void addErrorsFixed() {
            stats.addErrorsFixed(2);
            stats.addErrorsFixed(4);

            assertEquals(6, stats.getErrorsFixed());
        }

        @Test
        @DisplayName("Set errors fixed")
        void setErrorsFixed() {
            stats.setErrorsFixed(7);
            assertEquals(7, stats.getErrorsFixed());
        }
    }

    @Nested
    @DisplayName("Transformations and Formattings")
    class TransformationsTests {

        @Test
        @DisplayName("Increment transformations")
        void incrementTransformations() {
            stats.incrementTransformations();
            stats.incrementTransformations();

            assertEquals(2, stats.getTransformations());
        }

        @Test
        @DisplayName("Set transformations")
        void setTransformations() {
            stats.setTransformations(5);
            assertEquals(5, stats.getTransformations());
        }

        @Test
        @DisplayName("Increment formattings")
        void incrementFormattings() {
            stats.incrementFormattings();
            stats.incrementFormattings();
            stats.incrementFormattings();

            assertEquals(3, stats.getFormattings());
        }

        @Test
        @DisplayName("Set formattings")
        void setFormattings() {
            stats.setFormattings(8);
            assertEquals(8, stats.getFormattings());
        }
    }

    @Nested
    @DisplayName("Queries and Validations")
    class QueriesTests {

        @Test
        @DisplayName("Increment XPath queries")
        void incrementXpathQueries() {
            stats.incrementXpathQueries();
            stats.incrementXpathQueries();

            assertEquals(2, stats.getXpathQueries());
        }

        @Test
        @DisplayName("Set XPath queries")
        void setXpathQueries() {
            stats.setXpathQueries(12);
            assertEquals(12, stats.getXpathQueries());
        }

        @Test
        @DisplayName("Increment Schematron validations")
        void incrementSchematronValidations() {
            stats.incrementSchematronValidations();

            assertEquals(1, stats.getSchematronValidations());
        }

        @Test
        @DisplayName("Set Schematron validations")
        void setSchematronValidations() {
            stats.setSchematronValidations(4);
            assertEquals(4, stats.getSchematronValidations());
        }
    }

    @Nested
    @DisplayName("Usage Minutes")
    class UsageMinutesTests {

        @Test
        @DisplayName("Add usage minutes")
        void addUsageMinutes() {
            stats.addUsageMinutes(30);
            stats.addUsageMinutes(45);

            assertEquals(75, stats.getUsageMinutes());
        }

        @Test
        @DisplayName("Set usage minutes")
        void setUsageMinutes() {
            stats.setUsageMinutes(120);
            assertEquals(120, stats.getUsageMinutes());
        }
    }

    @Nested
    @DisplayName("Activity Detection")
    class ActivityTests {

        @Test
        @DisplayName("No activity when all zeros")
        void noActivityWhenAllZeros() {
            assertFalse(stats.hasActivity());
        }

        @Test
        @DisplayName("Has activity when files validated")
        void hasActivityWhenFilesValidated() {
            stats.incrementFilesValidated();
            assertTrue(stats.hasActivity());
        }

        @Test
        @DisplayName("Has activity when transformations performed")
        void hasActivityWhenTransformations() {
            stats.incrementTransformations();
            assertTrue(stats.hasActivity());
        }

        @Test
        @DisplayName("Has activity when formattings performed")
        void hasActivityWhenFormattings() {
            stats.incrementFormattings();
            assertTrue(stats.hasActivity());
        }

        @Test
        @DisplayName("Has activity when XPath queries executed")
        void hasActivityWhenXpathQueries() {
            stats.incrementXpathQueries();
            assertTrue(stats.hasActivity());
        }

        @Test
        @DisplayName("Has activity when Schematron validations")
        void hasActivityWhenSchematronValidations() {
            stats.incrementSchematronValidations();
            assertTrue(stats.hasActivity());
        }

        @Test
        @DisplayName("Has activity when usage minutes recorded")
        void hasActivityWhenUsageMinutes() {
            stats.addUsageMinutes(1);
            assertTrue(stats.hasActivity());
        }
    }

    @Nested
    @DisplayName("Total Activity")
    class TotalActivityTests {

        @Test
        @DisplayName("Total activity with no activity")
        void totalActivityWithNoActivity() {
            assertEquals(0, stats.getTotalActivity());
        }

        @Test
        @DisplayName("Total activity sums all metrics")
        void totalActivitySumsAllMetrics() {
            stats.setFilesValidated(5);
            stats.setTransformations(3);
            stats.setFormattings(2);
            stats.setXpathQueries(10);
            stats.setSchematronValidations(4);

            assertEquals(24, stats.getTotalActivity());
        }

        @Test
        @DisplayName("Total activity excludes errors and usage minutes")
        void totalActivityExcludesErrorsAndMinutes() {
            stats.setFilesValidated(5);
            stats.setErrorsFound(10);
            stats.setErrorsFixed(8);
            stats.setUsageMinutes(120);

            assertEquals(5, stats.getTotalActivity());
        }
    }

    @Nested
    @DisplayName("Date Management")
    class DateTests {

        @Test
        @DisplayName("Set and get date")
        void setAndGetDate() {
            LocalDate newDate = LocalDate.of(2024, 12, 25);
            stats.setDate(newDate);

            assertEquals(newDate, stats.getDate());
        }
    }
}
