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

package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;

import java.io.File;

/**
 * Test class to generate XSD documentation for FundsXML4.xsd
 */
public class XsdDocumentationGeneratorTest {

    @Test
    public void generateFundsXmlDocumentation() throws Exception {
        // Input XSD file path
        String xsdFilePath = "/Users/karlkauc/src/FundsXMLSchema 2/include_files/FundsXML4.xsd";

        // Output directory for HTML documentation
        File outputDirectory = new File("/Users/karlkauc/src/testDoc");

        // Check if input file exists
        File xsdFile = new File(xsdFilePath);
        if (!xsdFile.exists()) {
            System.err.println("XSD file not found: " + xsdFilePath);
            return;
        }

        // Create output directory if it doesn't exist
        if (!outputDirectory.exists()) {
            boolean created = outputDirectory.mkdirs();
            System.out.println("Created output directory: " + created);
        }

        System.out.println("Starting XSD Documentation Generation...");
        System.out.println("Input XSD: " + xsdFilePath);
        System.out.println("Output Directory: " + outputDirectory.getAbsolutePath());

        // Create and configure the documentation service
        XsdDocumentationService service = new XsdDocumentationService();

        // Set the XSD file path
        service.setXsdFilePath(xsdFilePath);

        // Optional: Configure documentation generation options
        service.setUseMarkdownRenderer(true);           // Enable markdown rendering
        service.setParallelProcessing(true);            // Use parallel processing for faster generation
        service.setMethod(XsdDocumentationService.ImageOutputMethod.SVG);  // Use SVG for diagrams

        service.setGenerateSvgOverviewPage(false);
        service.setIncludeTypeDefinitionsInSourceCode(true);
        service.setShowDocumentationInSvg(false);
        service.setIncludeTypeDefinitionsInSourceCode(true);

        // Optional: Set a progress listener to track progress
        service.setProgressListener(update -> {
            String status = switch (update.status()) {
                case STARTED -> "[STARTED]";
                case RUNNING -> "[RUNNING]";
                case FINISHED -> "[DONE in " + update.durationMillis() + "ms]";
                case FAILED -> "[FAILED]";
            };
            System.out.println(status + " " + update.taskName());
        });

        long startTime = System.currentTimeMillis();

        // Generate the documentation
        service.generateXsdDocumentation(outputDirectory);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("Documentation generation completed!");
        System.out.println("Duration: " + (duration / 1000.0) + " seconds");
        System.out.println("Output: " + outputDirectory.getAbsolutePath());
        System.out.println("Open: " + new File(outputDirectory, "index.html").getAbsolutePath());
        System.out.println("=".repeat(60));
    }
}
