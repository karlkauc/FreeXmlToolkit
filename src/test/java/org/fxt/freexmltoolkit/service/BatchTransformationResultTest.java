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

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BatchTransformationResult")
class BatchTransformationResultTest {

    private BatchTransformationResult result;

    @BeforeEach
    void setUp() {
        result = new BatchTransformationResult();
    }

    @Nested
    @DisplayName("Default State")
    class DefaultStateTests {

        @Test
        @DisplayName("New result is not successful by default")
        void newResultIsNotSuccessfulByDefault() {
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("New result has executedAt timestamp")
        void newResultHasExecutedAtTimestamp() {
            assertNotNull(result.getExecutedAt());
        }

        @Test
        @DisplayName("New result has empty per-file results")
        void newResultHasEmptyPerFileResults() {
            assertTrue(result.getPerFileResults().isEmpty());
        }

        @Test
        @DisplayName("New result has empty per-file errors")
        void newResultHasEmptyPerFileErrors() {
            assertTrue(result.getPerFileErrors().isEmpty());
        }

        @Test
        @DisplayName("New result has zero counts")
        void newResultHasZeroCounts() {
            assertEquals(0, result.getTotalFiles());
            assertEquals(0, result.getSuccessCount());
            assertEquals(0, result.getErrorCount());
            assertEquals(0, result.getSkippedCount());
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("success() creates successful result")
        void successCreatesSuccessfulResult() {
            BatchTransformationResult success = BatchTransformationResult.success(
                    "<combined>output</combined>", 5
            );

            assertTrue(success.isSuccess());
            assertEquals("<combined>output</combined>", success.getCombinedOutput());
            assertEquals(5, success.getTotalFiles());
            assertEquals(5, success.getSuccessCount());
        }

        @Test
        @DisplayName("error() creates error result")
        void errorCreatesErrorResult() {
            BatchTransformationResult error = BatchTransformationResult.error(
                    "Transformation failed"
            );

            assertFalse(error.isSuccess());
            assertEquals("Transformation failed", error.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("Per-File Results")
    class PerFileResultTests {

        private File file1;
        private File file2;
        private File file3;

        @BeforeEach
        void setUp() {
            file1 = new File("test1.xml");
            file2 = new File("test2.xml");
            file3 = new File("test3.xml");
        }

        @Test
        @DisplayName("addFileResult adds successful result")
        void addFileResultAddsSuccessfulResult() {
            result.addFileResult(file1, "<output1/>", 100);

            assertEquals(1, result.getSuccessCount());
            assertEquals("<output1/>", result.getFileResult(file1));
            assertTrue(result.isFileSuccess(file1));
            assertFalse(result.isFileError(file1));
        }

        @Test
        @DisplayName("addFileError adds error result")
        void addFileErrorAddsErrorResult() {
            result.addFileError(file1, "Parse error");

            assertEquals(1, result.getErrorCount());
            assertEquals("Parse error", result.getFileError(file1));
            assertTrue(result.isFileError(file1));
            assertFalse(result.isFileSuccess(file1));
        }

        @Test
        @DisplayName("Multiple file results are tracked independently")
        void multipleFileResultsAreTrackedIndependently() {
            result.addFileResult(file1, "<output1/>", 100);
            result.addFileResult(file2, "<output2/>", 150);
            result.addFileError(file3, "Error");

            assertEquals(2, result.getSuccessCount());
            assertEquals(1, result.getErrorCount());
            assertTrue(result.isFileSuccess(file1));
            assertTrue(result.isFileSuccess(file2));
            assertTrue(result.isFileError(file3));
        }

        @Test
        @DisplayName("getFileResult returns null for non-existent file")
        void getFileResultReturnsNullForNonExistentFile() {
            assertNull(result.getFileResult(file1));
        }

        @Test
        @DisplayName("getFileError returns null for non-existent file")
        void getFileErrorReturnsNullForNonExistentFile() {
            assertNull(result.getFileError(file1));
        }

        @Test
        @DisplayName("Execution times are tracked per file")
        void executionTimesAreTrackedPerFile() {
            result.addFileResult(file1, "<output1/>", 100);
            result.addFileResult(file2, "<output2/>", 200);

            Map<File, Long> times = result.getPerFileExecutionTimes();
            assertEquals(100L, times.get(file1));
            assertEquals(200L, times.get(file2));
        }

        @Test
        @DisplayName("getPerFileResults returns copy")
        void getPerFileResultsReturnsCopy() {
            result.addFileResult(file1, "<output/>", 100);

            Map<File, String> results = result.getPerFileResults();
            results.clear();

            assertFalse(result.getPerFileResults().isEmpty());
        }
    }

    @Nested
    @DisplayName("Getters and Setters")
    class GettersSettersTests {

        @Test
        @DisplayName("Can set and get success")
        void canSetAndGetSuccess() {
            result.setSuccess(true);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Can set and get combined output")
        void canSetAndGetCombinedOutput() {
            result.setCombinedOutput("<combined/>");
            assertEquals("<combined/>", result.getCombinedOutput());
        }

        @Test
        @DisplayName("Can set and get error message")
        void canSetAndGetErrorMessage() {
            result.setErrorMessage("Error occurred");
            assertEquals("Error occurred", result.getErrorMessage());
        }

        @Test
        @DisplayName("Can set and get executed at")
        void canSetAndGetExecutedAt() {
            LocalDateTime now = LocalDateTime.now();
            result.setExecutedAt(now);
            assertEquals(now, result.getExecutedAt());
        }

        @Test
        @DisplayName("Can set and get total execution time")
        void canSetAndGetTotalExecutionTime() {
            result.setTotalExecutionTime(5000);
            assertEquals(5000, result.getTotalExecutionTime());
        }

        @Test
        @DisplayName("Can set and get total files")
        void canSetAndGetTotalFiles() {
            result.setTotalFiles(10);
            assertEquals(10, result.getTotalFiles());
        }

        @Test
        @DisplayName("Can set and get success count")
        void canSetAndGetSuccessCount() {
            result.setSuccessCount(8);
            assertEquals(8, result.getSuccessCount());
        }

        @Test
        @DisplayName("Can set and get error count")
        void canSetAndGetErrorCount() {
            result.setErrorCount(2);
            assertEquals(2, result.getErrorCount());
        }

        @Test
        @DisplayName("Can set and get skipped count")
        void canSetAndGetSkippedCount() {
            result.setSkippedCount(3);
            assertEquals(3, result.getSkippedCount());
        }

        @Test
        @DisplayName("Can set and get output format")
        void canSetAndGetOutputFormat() {
            result.setOutputFormat(XsltTransformationEngine.OutputFormat.HTML);
            assertEquals(XsltTransformationEngine.OutputFormat.HTML, result.getOutputFormat());
        }
    }

    @Nested
    @DisplayName("getSummaryText")
    class GetSummaryTextTests {

        @BeforeEach
        void setUp() {
            result.setTotalFiles(10);
            result.setSuccessCount(8);
            result.setErrorCount(2);
            result.setTotalExecutionTime(500);
        }

        @Test
        @DisplayName("Summary contains title")
        void summaryContainsTitle() {
            String summary = result.getSummaryText();
            assertTrue(summary.contains("Batch XQuery Transformation Results"));
        }

        @Test
        @DisplayName("Summary contains file counts")
        void summaryContainsFileCounts() {
            String summary = result.getSummaryText();
            assertTrue(summary.contains("Total Files: 10"));
            assertTrue(summary.contains("Successful:  8"));
            assertTrue(summary.contains("Errors:      2"));
        }

        @Test
        @DisplayName("Summary contains execution time")
        void summaryContainsExecutionTime() {
            String summary = result.getSummaryText();
            assertTrue(summary.contains("Total Time:  500 ms"));
        }

        @Test
        @DisplayName("Summary lists file errors")
        void summaryListsFileErrors() {
            result.addFileError(new File("bad.xml"), "Parse failed");
            String summary = result.getSummaryText();

            assertTrue(summary.contains("Errors:"));
            assertTrue(summary.contains("bad.xml"));
            assertTrue(summary.contains("Parse failed"));
        }
    }

    @Nested
    @DisplayName("getSummaryHtml")
    class GetSummaryHtmlTests {

        @BeforeEach
        void setUp() {
            result.setTotalFiles(5);
            result.setSuccessCount(4);
            result.setErrorCount(1);
            result.setTotalExecutionTime(300);
        }

        @Test
        @DisplayName("HTML contains proper structure")
        void htmlContainsProperStructure() {
            String html = result.getSummaryHtml();

            assertTrue(html.contains("<html>"));
            assertTrue(html.contains("<head>"));
            assertTrue(html.contains("<body>"));
            assertTrue(html.contains("</html>"));
        }

        @Test
        @DisplayName("HTML contains CSS styles")
        void htmlContainsCssStyles() {
            String html = result.getSummaryHtml();

            assertTrue(html.contains("<style>"));
            assertTrue(html.contains(".stats"));
            assertTrue(html.contains(".success"));
            assertTrue(html.contains(".error"));
        }

        @Test
        @DisplayName("HTML contains stats section")
        void htmlContainsStatsSection() {
            String html = result.getSummaryHtml();

            assertTrue(html.contains("class='stats'"));
            assertTrue(html.contains("5")); // Total files
            assertTrue(html.contains("4")); // Successful
            assertTrue(html.contains("1")); // Errors
        }

        @Test
        @DisplayName("HTML contains file results table")
        void htmlContainsFileResultsTable() {
            result.addFileResult(new File("good.xml"), "<output/>", 100);
            String html = result.getSummaryHtml();

            assertTrue(html.contains("<table>"));
            assertTrue(html.contains("good.xml"));
            assertTrue(html.contains("Success"));
        }

        @Test
        @DisplayName("HTML contains file errors in table")
        void htmlContainsFileErrorsInTable() {
            result.addFileError(new File("bad.xml"), "Parse failed");
            String html = result.getSummaryHtml();

            assertTrue(html.contains("bad.xml"));
            assertTrue(html.contains("Error"));
            assertTrue(html.contains("Parse failed"));
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString contains success status")
        void toStringContainsSuccessStatus() {
            result.setSuccess(true);
            assertTrue(result.toString().contains("success=true"));
        }

        @Test
        @DisplayName("toString contains file counts")
        void toStringContainsFileCounts() {
            result.setTotalFiles(10);
            result.setSuccessCount(8);
            result.setErrorCount(2);

            String str = result.toString();
            assertTrue(str.contains("total=10"));
            assertTrue(str.contains("success=8"));
            assertTrue(str.contains("errors=2"));
        }

        @Test
        @DisplayName("toString contains execution time")
        void toStringContainsExecutionTime() {
            result.setTotalExecutionTime(500);
            assertTrue(result.toString().contains("time=500ms"));
        }
    }

    @Nested
    @DisplayName("Real World Scenarios")
    class RealWorldScenariosTests {

        @Test
        @DisplayName("Batch transformation with mixed results")
        void batchTransformationWithMixedResults() {
            result.setTotalFiles(5);
            result.addFileResult(new File("file1.xml"), "<out1/>", 50);
            result.addFileResult(new File("file2.xml"), "<out2/>", 60);
            result.addFileResult(new File("file3.xml"), "<out3/>", 70);
            result.addFileError(new File("file4.xml"), "Invalid XML");
            result.addFileError(new File("file5.xml"), "Transformation error");
            result.setTotalExecutionTime(250);
            result.setSuccess(true);
            result.setCombinedOutput("<combined><out1/><out2/><out3/></combined>");

            assertEquals(5, result.getTotalFiles());
            assertEquals(3, result.getSuccessCount());
            assertEquals(2, result.getErrorCount());
            assertTrue(result.isSuccess());

            String summary = result.getSummaryText();
            assertTrue(summary.contains("Total Files: 5"));
            assertTrue(summary.contains("Successful:  3"));
            assertTrue(summary.contains("Errors:      2"));
        }

        @Test
        @DisplayName("Complete successful batch")
        void completeSuccessfulBatch() {
            BatchTransformationResult success = BatchTransformationResult.success(
                    "<combined>All results</combined>", 10
            );
            success.setTotalExecutionTime(1000);
            success.setOutputFormat(XsltTransformationEngine.OutputFormat.XML);

            assertTrue(success.isSuccess());
            assertEquals(10, success.getTotalFiles());
            assertEquals(10, success.getSuccessCount());
            assertEquals(0, success.getErrorCount());
            assertEquals(XsltTransformationEngine.OutputFormat.XML, success.getOutputFormat());
        }

        @Test
        @DisplayName("Complete failed batch")
        void completeFailedBatch() {
            BatchTransformationResult error = BatchTransformationResult.error(
                    "XQuery compilation error: Unexpected token"
            );

            assertFalse(error.isSuccess());
            assertEquals("XQuery compilation error: Unexpected token", error.getErrorMessage());
            assertNull(error.getCombinedOutput());
        }
    }
}
