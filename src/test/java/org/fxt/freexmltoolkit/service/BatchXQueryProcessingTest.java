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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for multi-file XQuery batch processing functionality.
 */
class BatchXQueryProcessingTest {

    private XsltTransformationEngine engine;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        engine = XsltTransformationEngine.getInstance();
    }

    @Test
    void testXmlFileEntryCreation() throws IOException {
        // Create a test XML file
        Path xmlFile = tempDir.resolve("test.xml");
        Files.writeString(xmlFile, "<?xml version=\"1.0\"?><root><item>Test</item></root>");

        XmlFileEntry entry = new XmlFileEntry(xmlFile.toFile());

        assertEquals("test.xml", entry.getFileName());
        assertTrue(entry.isSelected());
        assertEquals("Ready", entry.getStatus());
        assertTrue(entry.getFileSize() > 0);
    }

    @Test
    void testXmlFileEntryStatusUpdates() throws IOException {
        Path xmlFile = tempDir.resolve("test.xml");
        Files.writeString(xmlFile, "<root/>");

        XmlFileEntry entry = new XmlFileEntry(xmlFile.toFile());

        // Test status transitions
        entry.markProcessing();
        assertEquals("Processing...", entry.getStatus());

        entry.markSuccess("Result content");
        assertEquals("Success", entry.getStatus());
        assertEquals("Result content", entry.getResultOutput());
        assertTrue(entry.isSuccess());

        entry.reset();
        assertEquals("Ready", entry.getStatus());
        assertNull(entry.getResultOutput());

        entry.markError("Some error");
        assertEquals("Error", entry.getStatus());
        assertEquals("Some error", entry.getErrorMessage());
        assertTrue(entry.isError());
    }

    @Test
    void testBatchTransformationResultSuccess() {
        BatchTransformationResult result = BatchTransformationResult.success("<combined/>", 3);

        assertTrue(result.isSuccess());
        assertEquals("<combined/>", result.getCombinedOutput());
        assertEquals(3, result.getTotalFiles());
        assertEquals(3, result.getSuccessCount());
    }

    @Test
    void testBatchTransformationResultError() {
        BatchTransformationResult result = BatchTransformationResult.error("Test error");

        assertFalse(result.isSuccess());
        assertEquals("Test error", result.getErrorMessage());
    }

    @Test
    void testBatchTransformationResultPerFileTracking() throws IOException {
        // Create test files
        Path file1 = tempDir.resolve("file1.xml");
        Path file2 = tempDir.resolve("file2.xml");
        Files.writeString(file1, "<data1/>");
        Files.writeString(file2, "<data2/>");

        BatchTransformationResult result = new BatchTransformationResult();
        result.setTotalFiles(2);

        result.addFileResult(file1.toFile(), "<result1/>", 100);
        result.addFileResult(file2.toFile(), "<result2/>", 150);

        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getErrorCount());
        assertTrue(result.isFileSuccess(file1.toFile()));
        assertTrue(result.isFileSuccess(file2.toFile()));
        assertEquals("<result1/>", result.getFileResult(file1.toFile()));
        assertEquals("<result2/>", result.getFileResult(file2.toFile()));
    }

    @Test
    void testBatchTransformationResultMixedResults() throws IOException {
        Path file1 = tempDir.resolve("success.xml");
        Path file2 = tempDir.resolve("error.xml");
        Files.writeString(file1, "<data/>");
        Files.writeString(file2, "invalid xml");

        BatchTransformationResult result = new BatchTransformationResult();
        result.setTotalFiles(2);

        result.addFileResult(file1.toFile(), "<result/>", 100);
        result.addFileError(file2.toFile(), "Parse error");

        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getErrorCount());
        assertTrue(result.isFileSuccess(file1.toFile()));
        assertTrue(result.isFileError(file2.toFile()));
        assertEquals("Parse error", result.getFileError(file2.toFile()));
    }

    @Test
    void testXmlFileCollectionResolverBasic() throws IOException {
        // Create test XML files
        Path file1 = tempDir.resolve("doc1.xml");
        Path file2 = tempDir.resolve("doc2.xml");
        Files.writeString(file1, "<?xml version=\"1.0\"?><doc><id>1</id></doc>");
        Files.writeString(file2, "<?xml version=\"1.0\"?><doc><id>2</id></doc>");

        XmlFileCollectionResolver resolver = new XmlFileCollectionResolver();
        resolver.setXmlFiles(List.of(file1.toFile(), file2.toFile()));

        assertEquals(2, resolver.size());
        assertTrue(resolver.getFiles().contains(file1.toFile()));
        assertTrue(resolver.getFiles().contains(file2.toFile()));
    }

    @Test
    void testXmlFileCollectionResolverAddDirectory() throws IOException {
        // Create test XML files in temp directory
        Files.writeString(tempDir.resolve("a.xml"), "<a/>");
        Files.writeString(tempDir.resolve("b.xml"), "<b/>");
        Files.writeString(tempDir.resolve("c.txt"), "not xml"); // Should be ignored

        XmlFileCollectionResolver resolver = new XmlFileCollectionResolver();
        resolver.addDirectory(tempDir.toFile(), "*.xml");

        assertEquals(2, resolver.size());
    }

    @Test
    void testXmlFileCollectionResolverClear() throws IOException {
        Path file = tempDir.resolve("test.xml");
        Files.writeString(file, "<test/>");

        XmlFileCollectionResolver resolver = new XmlFileCollectionResolver(List.of(file.toFile()));
        assertEquals(1, resolver.size());

        resolver.clear();
        assertEquals(0, resolver.size());
    }

    @Test
    void testBatchXQueryTransformationWithCollection() throws IOException {
        // Create test XML files
        Path file1 = tempDir.resolve("order1.xml");
        Path file2 = tempDir.resolve("order2.xml");
        Files.writeString(file1, "<?xml version=\"1.0\"?><order><amount>100</amount></order>");
        Files.writeString(file2, "<?xml version=\"1.0\"?><order><amount>200</amount></order>");

        List<File> files = List.of(file1.toFile(), file2.toFile());

        // Simple XQuery that works with collection()
        String xquery = """
            xquery version "3.1";
            let $docs := collection()
            return
            <summary>
                <fileCount>{count($docs)}</fileCount>
                <total>{sum($docs//amount)}</total>
            </summary>
            """;

        BatchTransformationResult result = engine.transformXQueryBatch(
                files, xquery, null, XsltTransformationEngine.OutputFormat.XML);

        // Debug output
        if (!result.isSuccess()) {
            System.err.println("Batch transformation failed: " + result.getErrorMessage());
            System.err.println("Errors: " + result.getPerFileErrors());
        }

        assertTrue(result.isSuccess(),
                "Batch transformation should succeed. Error: " + result.getErrorMessage());
        assertNotNull(result.getCombinedOutput(), "Should have combined output");
        assertTrue(result.getCombinedOutput().contains("<fileCount>2</fileCount>"),
                "Should count 2 files. Output: " + result.getCombinedOutput());
        assertTrue(result.getCombinedOutput().contains("<total>300</total>"),
                "Should sum amounts to 300. Output: " + result.getCombinedOutput());
    }

    @Test
    void testBatchTransformationResultSummary() throws IOException {
        Path file1 = tempDir.resolve("f1.xml");
        Path file2 = tempDir.resolve("f2.xml");
        Files.writeString(file1, "<a/>");
        Files.writeString(file2, "<b/>");

        BatchTransformationResult result = new BatchTransformationResult();
        result.setTotalFiles(2);
        result.setTotalExecutionTime(250);
        result.addFileResult(file1.toFile(), "<r1/>", 100);
        result.addFileResult(file2.toFile(), "<r2/>", 150);

        String summary = result.getSummaryText();
        assertNotNull(summary);
        assertTrue(summary.contains("Total Files: 2"));
        assertTrue(summary.contains("Successful:  2"));
        assertTrue(summary.contains("Total Time:  250 ms"));
    }
}
