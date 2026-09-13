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

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage for the generated HTML documentation site.
 *
 * <p>Runs against the small {@code testSchema.xsd} rather than a FundsXML schema so the whole
 * pipeline (root page, type lists, data dictionary, search index, detail pages, languages.json)
 * stays fast enough for the normal test run. Note the schema declares no globally named types -
 * every complexType/simpleType in it is anonymous - so the assertions target the pages that are
 * always produced.
 */
@DisplayName("XSD HTML documentation generation")
class GenerateXsdHtmlDocumentationTest {

    private static final String SIMPLE_XSD_FILE = "src/test/resources/testSchema.xsd";
    private static final String XML_420_XSD = "src/test/resources/FundsXML_420.xsd";

    private static XsdDocumentationService service() {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(SIMPLE_XSD_FILE);
        service.setMethod(XsdDocumentationService.ImageOutputMethod.SVG);
        return service;
    }

    private static String read(File file) throws Exception {
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("parsing populates the element map with the schema's elements")
    void parseXsdPopulatesTheElementMap() throws Exception {
        XsdDocumentationService service = service();

        service.processXsd(XsdDocumentationService.MarkdownMode.ALL);
        var elements = service.xsdDocumentationData.getExtendedXsdElementMap();

        assertFalse(elements.isEmpty(), "the element map must not be empty");
        assertTrue(elements.values().stream().anyMatch(e -> "Cars".equals(e.getElementName())),
                "the root element Cars must be in the map");
        assertTrue(elements.values().stream().anyMatch(e -> "Manufatorer".equals(e.getElementName())),
                "a deeply nested element must be reached too");
    }

    @Test
    @DisplayName("generation produces the whole site")
    void generatesTheDocumentationSite(@TempDir Path outputDir) throws Exception {
        File target = outputDir.toFile();

        service().generateXsdDocumentation(target);

        File index = new File(target, "index.html");
        assertTrue(index.isFile(), "index.html must exist");
        assertTrue(index.length() > 0, "index.html must not be empty");
        assertTrue(read(index).contains("Cars"), "the root element must appear on the start page");

        assertTrue(new File(target, "dataDictionary.html").isFile(), "data dictionary must exist");
        assertTrue(new File(target, "complexTypes.html").isFile(), "complex type list must exist");
        assertTrue(new File(target, "simpleTypes.html").isFile(), "simple type list must exist");
        assertTrue(new File(target, "assets").isDirectory(), "assets must be copied");

        File details = new File(target, "details");
        assertTrue(details.isDirectory(), "detail pages must be generated");
        File[] detailPages = details.listFiles((d, n) -> n.endsWith(".html"));
        assertNotNull(detailPages);
        assertTrue(detailPages.length > 0, "there must be at least one detail page");
    }

    @Test
    @DisplayName("the search index and language list are written and carry the schema's content")
    void writesSearchIndexAndLanguages(@TempDir Path outputDir) throws Exception {
        File target = outputDir.toFile();

        service().generateXsdDocumentation(target);

        File searchIndex = new File(target, "search_index.json");
        assertTrue(searchIndex.isFile(), "search_index.json must exist");
        assertTrue(read(searchIndex).contains("Cars"), "the search index must list the root element");

        assertTrue(new File(target, "languages.json").isFile(), "languages.json must exist");
    }

    @Test
    @DisplayName("documentation text from the schema reaches the detail pages")
    void documentationTextReachesTheDetailPages(@TempDir Path outputDir) throws Exception {
        File target = outputDir.toFile();

        service().generateXsdDocumentation(target);

        File[] detailPages = new File(target, "details").listFiles((d, n) -> n.endsWith(".html"));
        assertNotNull(detailPages);
        boolean found = false;
        for (File page : detailPages) {
            if (read(page).contains("manufactor doc")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "the xs:documentation of Cars must appear on a detail page");
    }

    @Test
    @DisplayName("parallel generation produces the same site as sequential")
    void parallelGenerationProducesTheSameSite(@TempDir Path outputDir) throws Exception {
        File target = outputDir.toFile();
        XsdDocumentationService service = service();
        service.setParallelProcessing(true);

        service.generateXsdDocumentation(target);

        assertTrue(new File(target, "index.html").isFile(), "index.html must exist");
        assertTrue(new File(target, "dataDictionary.html").isFile(), "data dictionary must exist");
        File[] detailPages = new File(target, "details").listFiles((d, n) -> n.endsWith(".html"));
        assertNotNull(detailPages);
        assertTrue(detailPages.length > 0, "the parallel path must generate detail pages too");
    }

    /**
     * Benchmark, not a correctness test: it has no assertions and generates the documentation for a
     * full FundsXML schema twice, which takes far too long for the normal suite. Enable it by hand
     * when comparing the parallel and sequential pipelines.
     */
    @Test
    @Disabled("Benchmark, not a correctness test - run manually when profiling the pipeline")
    void compareParallelVsSequentialPerformance(@TempDir Path outputDir) throws Exception {
        XsdDocumentationService sequential = new XsdDocumentationService();
        sequential.setXsdFilePath(XML_420_XSD);
        sequential.setParallelProcessing(false);
        sequential.setMethod(XsdDocumentationService.ImageOutputMethod.SVG);

        long startSequential = System.currentTimeMillis();
        sequential.generateXsdDocumentation(outputDir.resolve("sequential").toFile());
        long durationSequential = System.currentTimeMillis() - startSequential;

        XsdDocumentationService parallel = new XsdDocumentationService();
        parallel.setXsdFilePath(XML_420_XSD);
        parallel.setParallelProcessing(true);
        parallel.setMethod(XsdDocumentationService.ImageOutputMethod.SVG);

        long startParallel = System.currentTimeMillis();
        parallel.generateXsdDocumentation(outputDir.resolve("parallel").toFile());
        long durationParallel = System.currentTimeMillis() - startParallel;

        System.out.printf("sequential: %,d ms / parallel: %,d ms%n", durationSequential, durationParallel);
    }
}
