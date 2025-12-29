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

package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XmlValidationResult")
class XmlValidationResultTest {

    private XmlValidationResult result;

    @BeforeEach
    void setUp() {
        result = new XmlValidationResult();
    }

    @Nested
    @DisplayName("Default State")
    class DefaultStateTests {

        @Test
        @DisplayName("New result is valid by default")
        void newResultIsValidByDefault() {
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("New result has no errors")
        void newResultHasNoErrors() {
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("New result has no warnings")
        void newResultHasNoWarnings() {
            assertTrue(result.getWarnings().isEmpty());
        }

        @Test
        @DisplayName("New result has no info")
        void newResultHasNoInfo() {
            assertTrue(result.getInfo().isEmpty());
        }

        @Test
        @DisplayName("Schema path is null by default")
        void schemaPathIsNullByDefault() {
            assertNull(result.getSchemaPath());
        }

        @Test
        @DisplayName("Auto-discovered schema is false by default")
        void autoDiscoveredSchemaIsFalseByDefault() {
            assertFalse(result.isAutoDiscoveredSchema());
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("createEmpty creates result with info message")
        void createEmptyCreatesResultWithInfoMessage() {
            XmlValidationResult empty = XmlValidationResult.createEmpty("No content");

            assertTrue(empty.isValid());
            assertEquals(1, empty.getInfoCount());
            assertTrue(empty.getInfo().get(0).getMessage().contains("No content"));
        }

        @Test
        @DisplayName("createSuccess creates valid result with schema path")
        void createSuccessCreatesValidResultWithSchemaPath() {
            XmlValidationResult success = XmlValidationResult.createSuccess("schema.xsd");

            assertTrue(success.isValid());
            assertEquals("schema.xsd", success.getSchemaPath());
            assertEquals(1, success.getInfoCount());
        }

        @Test
        @DisplayName("createSuccess handles null schema path")
        void createSuccessHandlesNullSchemaPath() {
            XmlValidationResult success = XmlValidationResult.createSuccess(null);

            assertTrue(success.isValid());
            assertNull(success.getSchemaPath());
        }
    }

    @Nested
    @DisplayName("Error Management")
    class ErrorManagementTests {

        @Test
        @DisplayName("Adding ERROR sets valid to false")
        void addingErrorSetsValidToFalse() {
            XmlValidationError error = XmlValidationError.error(1, 1, "Error");
            result.addError(error);

            assertFalse(result.isValid());
            assertEquals(1, result.getErrorCount());
        }

        @Test
        @DisplayName("Adding FATAL sets valid to false")
        void addingFatalSetsValidToFalse() {
            XmlValidationError error = XmlValidationError.fatal(1, 1, "Fatal");
            result.addError(error);

            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("Adding WARNING goes to warnings list")
        void addingWarningGoesToWarningsList() {
            XmlValidationError warning = XmlValidationError.warning(1, 1, "Warning");
            result.addError(warning);

            assertTrue(result.isValid()); // Warnings don't invalidate
            assertEquals(1, result.getWarningCount());
            assertEquals(0, result.getErrorCount());
        }

        @Test
        @DisplayName("Adding INFO goes to info list")
        void addingInfoGoesToInfoList() {
            XmlValidationError info = XmlValidationError.info(1, 1, "Info");
            result.addError(info);

            assertTrue(result.isValid());
            assertEquals(1, result.getInfoCount());
        }

        @Test
        @DisplayName("addErrors adds multiple errors")
        void addErrorsAddsMultipleErrors() {
            List<XmlValidationError> errors = List.of(
                    XmlValidationError.error(1, 1, "Error 1"),
                    XmlValidationError.error(2, 1, "Error 2"),
                    XmlValidationError.warning(3, 1, "Warning 1")
            );
            result.addErrors(errors);

            assertEquals(2, result.getErrorCount());
            assertEquals(1, result.getWarningCount());
        }

        @Test
        @DisplayName("addWarning directly adds to warnings")
        void addWarningDirectlyAddsToWarnings() {
            XmlValidationError warning = XmlValidationError.warning(1, 1, "Warning");
            result.addWarning(warning);

            assertEquals(1, result.getWarningCount());
        }

        @Test
        @DisplayName("addInfo directly adds to info")
        void addInfoDirectlyAddsToInfo() {
            XmlValidationError info = XmlValidationError.info(1, 1, "Info");
            result.addInfo(info);

            assertEquals(1, result.getInfoCount());
        }
    }

    @Nested
    @DisplayName("Error Collections")
    class ErrorCollectionTests {

        @BeforeEach
        void addSampleErrors() {
            result.addError(XmlValidationError.error(10, 5, "Error at line 10"));
            result.addError(XmlValidationError.error(5, 1, "Error at line 5"));
            result.addError(XmlValidationError.warning(15, 1, "Warning at line 15"));
            result.addError(XmlValidationError.info(1, 1, "Info at line 1"));
        }

        @Test
        @DisplayName("getErrors returns copy of errors list")
        void getErrorsReturnsCopyOfErrorsList() {
            List<XmlValidationError> errors = result.getErrors();
            int originalSize = errors.size();
            errors.clear();

            assertEquals(originalSize, result.getErrorCount());
        }

        @Test
        @DisplayName("getAllIssues combines all lists")
        void getAllIssuesCombinesAllLists() {
            List<XmlValidationError> all = result.getAllIssues();

            assertEquals(4, all.size());
        }

        @Test
        @DisplayName("getAllIssues is sorted by line number")
        void getAllIssuesIsSortedByLineNumber() {
            List<XmlValidationError> all = result.getAllIssues();

            assertEquals(1, all.get(0).getLineNumber()); // Info
            assertEquals(5, all.get(1).getLineNumber()); // Error at line 5
            assertEquals(10, all.get(2).getLineNumber()); // Error at line 10
            assertEquals(15, all.get(3).getLineNumber()); // Warning
        }

        @Test
        @DisplayName("getErrorsByType filters correctly")
        void getErrorsByTypeFiltersCorrectly() {
            List<XmlValidationError> errors = result.getErrorsByType(
                    XmlValidationError.ErrorType.ERROR
            );

            assertEquals(2, errors.size());
        }

        @Test
        @DisplayName("getErrorsForLine filters by line number")
        void getErrorsForLineFiltersByLineNumber() {
            List<XmlValidationError> errorsAtLine10 = result.getErrorsForLine(10);

            assertEquals(1, errorsAtLine10.size());
            assertEquals("Error at line 10", errorsAtLine10.get(0).getMessage());
        }
    }

    @Nested
    @DisplayName("Utility Methods")
    class UtilityMethodTests {

        @Test
        @DisplayName("hasIssues returns false for clean result")
        void hasIssuesReturnsFalseForCleanResult() {
            assertFalse(result.hasIssues());
        }

        @Test
        @DisplayName("hasIssues returns true when errors present")
        void hasIssuesReturnsTrueWhenErrorsPresent() {
            result.addError(XmlValidationError.error(1, 1, "Error"));
            assertTrue(result.hasIssues());
        }

        @Test
        @DisplayName("hasIssues returns true when warnings present")
        void hasIssuesReturnsTrueWhenWarningsPresent() {
            result.addWarning(XmlValidationError.warning(1, 1, "Warning"));
            assertTrue(result.hasIssues());
        }

        @Test
        @DisplayName("hasIssues returns false when only info present")
        void hasIssuesReturnsFalseWhenOnlyInfoPresent() {
            result.addInfo(XmlValidationError.info(1, 1, "Info"));
            assertFalse(result.hasIssues());
        }

        @Test
        @DisplayName("hasFatalErrors returns false for no errors")
        void hasFatalErrorsReturnsFalseForNoErrors() {
            assertFalse(result.hasFatalErrors());
        }

        @Test
        @DisplayName("hasFatalErrors returns true when fatal present")
        void hasFatalErrorsReturnsTrueWhenFatalPresent() {
            result.addError(XmlValidationError.fatal(1, 1, "Fatal"));
            assertTrue(result.hasFatalErrors());
        }

        @Test
        @DisplayName("hasFatalErrors returns false for regular errors")
        void hasFatalErrorsReturnsFalseForRegularErrors() {
            result.addError(XmlValidationError.error(1, 1, "Error"));
            assertFalse(result.hasFatalErrors());
        }

        @Test
        @DisplayName("getTotalIssueCount sums all issues")
        void getTotalIssueCountSumsAllIssues() {
            result.addError(XmlValidationError.error(1, 1, "Error"));
            result.addWarning(XmlValidationError.warning(1, 1, "Warning"));
            result.addInfo(XmlValidationError.info(1, 1, "Info"));

            assertEquals(3, result.getTotalIssueCount());
        }
    }

    @Nested
    @DisplayName("Schema Information")
    class SchemaInformationTests {

        @Test
        @DisplayName("setSchemaPath sets schema path")
        void setSchemaPathSetsSchemaPath() {
            result.setSchemaPath("schema.xsd");
            assertEquals("schema.xsd", result.getSchemaPath());
        }

        @Test
        @DisplayName("setAutoDiscoveredSchema sets both flag and path")
        void setAutoDiscoveredSchemaSetsPathAndFlag() {
            result.setAutoDiscoveredSchema("discovered.xsd");

            assertTrue(result.isAutoDiscoveredSchema());
            assertEquals("discovered.xsd", result.getSchemaPath());
        }
    }

    @Nested
    @DisplayName("XML Content")
    class XmlContentTests {

        @Test
        @DisplayName("setXmlContent sets content and calculates metrics")
        void setXmlContentSetsContentAndCalculatesMetrics() {
            String content = "Line 1\nLine 2\nLine 3";
            result.setXmlContent(content);

            assertEquals(content, result.getXmlContent());
            assertEquals(3, result.getTotalLineCount());
            assertEquals(content.length(), result.getTotalCharacterCount());
        }

        @Test
        @DisplayName("Small file is not marked as large")
        void smallFileIsNotMarkedAsLarge() {
            result.setXmlContent("<root/>");
            assertFalse(result.isLargeFile());
        }

        @Test
        @DisplayName("Large file is marked as large")
        void largeFileIsMarkedAsLarge() {
            // Create content > 50KB
            String largeContent = "x".repeat(51000);
            result.setXmlContent(largeContent);

            assertTrue(result.isLargeFile());
        }

        @Test
        @DisplayName("Null content doesn't cause errors")
        void nullContentDoesntCauseErrors() {
            result.setXmlContent(null);

            assertNull(result.getXmlContent());
            assertEquals(0, result.getTotalLineCount());
        }
    }

    @Nested
    @DisplayName("Performance Metrics")
    class PerformanceMetricsTests {

        @Test
        @DisplayName("Can set and get validation start time")
        void canSetAndGetValidationStartTime() {
            LocalDateTime now = LocalDateTime.now();
            result.setValidationStartTime(now);

            assertEquals(now, result.getValidationStartTime());
        }

        @Test
        @DisplayName("Can set and get validation duration")
        void canSetAndGetValidationDuration() {
            result.setValidationDuration(150);
            assertEquals(150, result.getValidationDuration());
        }

        @Test
        @DisplayName("Can set and get parse time")
        void canSetAndGetParseTime() {
            result.setParseTime(50);
            assertEquals(50, result.getParseTime());
        }

        @Test
        @DisplayName("Can set and get validation time")
        void canSetAndGetValidationTime() {
            result.setValidationTime(100);
            assertEquals(100, result.getValidationTime());
        }

        @Test
        @DisplayName("Can set and get processed chunks")
        void canSetAndGetProcessedChunks() {
            result.setProcessedChunks(5);
            assertEquals(5, result.getProcessedChunks());
        }
    }

    @Nested
    @DisplayName("getValidationSummary")
    class GetValidationSummaryTests {

        @Test
        @DisplayName("Summary shows VALID status for valid result")
        void summaryShowsValidStatus() {
            String summary = result.getValidationSummary();
            assertTrue(summary.contains("Status: VALID"));
        }

        @Test
        @DisplayName("Summary shows INVALID status for invalid result")
        void summaryShowsInvalidStatus() {
            result.addError(XmlValidationError.error(1, 1, "Error"));
            String summary = result.getValidationSummary();

            assertTrue(summary.contains("Status: INVALID"));
        }

        @Test
        @DisplayName("Summary includes schema path when set")
        void summaryIncludesSchemaPath() {
            result.setSchemaPath("schema.xsd");
            String summary = result.getValidationSummary();

            assertTrue(summary.contains("Schema: schema.xsd"));
        }

        @Test
        @DisplayName("Summary notes auto-discovered schema")
        void summaryNotesAutoDiscoveredSchema() {
            result.setAutoDiscoveredSchema("auto.xsd");
            String summary = result.getValidationSummary();

            assertTrue(summary.contains("auto-discovered"));
        }

        @Test
        @DisplayName("Summary includes error and warning counts")
        void summaryIncludesErrorAndWarningCounts() {
            result.addError(XmlValidationError.error(1, 1, "Error"));
            result.addWarning(XmlValidationError.warning(1, 1, "Warning"));

            String summary = result.getValidationSummary();
            assertTrue(summary.contains("1 errors"));
            assertTrue(summary.contains("1 warnings"));
        }

        @Test
        @DisplayName("Summary includes duration when set")
        void summaryIncludesDurationWhenSet() {
            result.setValidationDuration(150);
            String summary = result.getValidationSummary();

            assertTrue(summary.contains("Duration: 150ms"));
        }

        @Test
        @DisplayName("Summary includes document metrics when available")
        void summaryIncludesDocumentMetrics() {
            result.setXmlContent("Line 1\nLine 2");
            String summary = result.getValidationSummary();

            assertTrue(summary.contains("2 lines"));
        }

        @Test
        @DisplayName("Summary notes large file")
        void summaryNotesLargeFile() {
            result.setXmlContent("x".repeat(51000));
            String summary = result.getValidationSummary();

            assertTrue(summary.contains("large file"));
        }
    }

    @Nested
    @DisplayName("getDetailedReport")
    class GetDetailedReportTests {

        @Test
        @DisplayName("Report includes summary")
        void reportIncludesSummary() {
            String report = result.getDetailedReport();
            assertTrue(report.contains("Status: VALID"));
        }

        @Test
        @DisplayName("Report lists errors when present")
        void reportListsErrorsWhenPresent() {
            result.addError(XmlValidationError.error(1, 1, "Test error"));
            String report = result.getDetailedReport();

            assertTrue(report.contains("ERRORS:"));
            assertTrue(report.contains("Test error"));
        }

        @Test
        @DisplayName("Report lists warnings when present")
        void reportListsWarningsWhenPresent() {
            result.addWarning(XmlValidationError.warning(1, 1, "Test warning"));
            String report = result.getDetailedReport();

            assertTrue(report.contains("WARNINGS:"));
            assertTrue(report.contains("Test warning"));
        }

        @Test
        @DisplayName("Report lists info when present")
        void reportListsInfoWhenPresent() {
            result.addInfo(XmlValidationError.info(1, 1, "Test info"));
            String report = result.getDetailedReport();

            assertTrue(report.contains("INFO:"));
            assertTrue(report.contains("Test info"));
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString contains validity status")
        void toStringContainsValidityStatus() {
            assertTrue(result.toString().contains("valid=true"));
        }

        @Test
        @DisplayName("toString contains error count")
        void toStringContainsErrorCount() {
            result.addError(XmlValidationError.error(1, 1, "Error"));
            assertTrue(result.toString().contains("errors=1"));
        }

        @Test
        @DisplayName("toString contains warning count")
        void toStringContainsWarningCount() {
            result.addWarning(XmlValidationError.warning(1, 1, "Warning"));
            assertTrue(result.toString().contains("warnings=1"));
        }
    }

    @Nested
    @DisplayName("Valid State Management")
    class ValidStateManagementTests {

        @Test
        @DisplayName("setValid explicitly sets validity")
        void setValidExplicitlySetsValidity() {
            result.setValid(false);
            assertFalse(result.isValid());

            result.setValid(true);
            assertTrue(result.isValid());
        }
    }
}
