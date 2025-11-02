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

package org.fxt.freexmltoolkit.app;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;

import java.awt.*;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A standalone application to generate HTML documentation from an XSD file.
 * This class can be executed directly to run the documentation generation process
 * without running the main JavaFX application or tests.
 */
public class DocumentationGeneratorApp {

    private static final Logger logger = LogManager.getLogger(DocumentationGeneratorApp.class);

    // --- Configuration ---
    // You can easily change the source XSD and output directory here.
    private static final String XSD_FILE_PATH = "release/examples/xsd/FundsXML4.xsd";
    private static final String OUTPUT_DIRECTORY = "../FundsXML_Documentation";

    /**
     * The main entry point for the documentation generator application.
     *
     * @param args Command-line arguments (not currently used).
     * @throws Exception if an error occurs during documentation generation.
     */
    static void main(String[] args) throws Exception {
        // --- 1. Configuration and Generation ---
        final Path xsdFilePath = Paths.get(XSD_FILE_PATH);
        final Path outputFilePath = Paths.get(OUTPUT_DIRECTORY);

        logger.info("Starting documentation generation...");
        logger.info("Source XSD: {}", xsdFilePath.toAbsolutePath());
        logger.info("Output Directory: {}", outputFilePath.toAbsolutePath());

        XsdDocumentationService xsdDocService = new XsdDocumentationService();
        xsdDocService.setIncludeTypeDefinitionsInSourceCode(true);
        xsdDocService.setUseMarkdownRenderer(true);
        xsdDocService.setParallelProcessing(true);
        xsdDocService.setXsdFilePath(xsdFilePath.toString());
        xsdDocService.imageOutputMethod = XsdDocumentationService.ImageOutputMethod.SVG;

        xsdDocService.generateXsdDocumentation(outputFilePath.toFile());

        logger.info("Documentation generated successfully.");

        // --- 2. Start Embedded HTTP Server (Optional) ---
        // This part is for convenience to view the generated files directly.
        int port = 8080;
        startHttpServer(outputFilePath, port);
        openUrlInBrowser("http://localhost:" + port + "/index.html");

        // Keep the application running to serve the files.
        // Terminate the application (e.g., with Ctrl+C) to stop the server.
        logger.info("Application is running. Press Ctrl+C in the console to stop the server.");
        Thread.sleep(Long.MAX_VALUE);
    }

    /**
     * Starts a simple, embedded HTTP file server.
     *
     * @param docRootPath The root directory from which files should be served.
     * @param port        The port on which the server should listen.
     */
    private static void startHttpServer(Path docRootPath, int port) {
        try {
            Path absoluteDocRoot = docRootPath.toAbsolutePath().normalize();
            File docRootFile = absoluteDocRoot.toFile();

            HttpServer server = SimpleFileServer.createFileServer(
                    new InetSocketAddress(port),
                    absoluteDocRoot,
                    SimpleFileServer.OutputLevel.INFO
            );
            server.start();

            logger.info("===================================================================");
            logger.info("HTTP server started on http://localhost:{}", port);
            logger.info("Serving files from: {}", docRootFile.getAbsolutePath());
            logger.info("===================================================================");
        } catch (Exception e) {
            logger.error("Could not start the HTTP server.", e);
        }
    }

    /**
     * Attempts to open the specified URL in the default desktop browser.
     *
     * @param url The URL to open.
     */
    private static void openUrlInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new java.net.URI(url));
                logger.info("Opened documentation in the default browser: {}", url);
            } else {
                logger.warn("Could not open browser automatically. Please open manually: {}", url);
            }
        } catch (Exception e) {
            logger.error("Error opening the browser.", e);
        }
    }
}
