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

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.awt.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GenerateXsdHtmlDocumentationTest {

    final static String XML_LATEST_XSD = "src/test/resources/FundsXML4.xsd";
    final static String XML_420_XSD = "src/test/resources/FundsXML_420.xsd";
    final static String XML_429_XSD = "src/test/resources/FundsXML_429.xsd";
    final static String XML_306_XSD = "src/test/resources/FundsXML_306.xsd";
    final static String SIMPLE_XSD_FILE = "src/test/resources/testSchema.xsd";

    final XsdDocumentationService xsdDocumentationService = new XsdDocumentationService();
    private final static Logger logger = LogManager.getLogger(GenerateXsdHtmlDocumentationTest.class);

    @Test
    void parseXsdTest() throws Exception {
        xsdDocumentationService.setXsdFilePath(XML_420_XSD);
        xsdDocumentationService.setMethod(XsdDocumentationService.ImageOutputMethod.SVG);
        xsdDocumentationService.processXsd(true);
        var elements = xsdDocumentationService.xsdDocumentationData.getExtendedXsdElementMap();

        System.out.println("------------");
        for (String s : elements.keySet()) {
            logger.debug("xPath: {}", s);
        }
        logger.debug("Size: {}", elements.size());
    }

    @Test
    void generateSeperatedFiles() throws Exception {
        logger.debug("Creating Documentation");
        xsdDocumentationService.setXsdFilePath(XML_420_XSD);
        xsdDocumentationService.parallelProcessing = true;
        xsdDocumentationService.generateXsdDocumentation(new File("output/testSchema"));
    }

    @Test
    void testWithSeparateCalls() throws Exception {
        final var testFilePath = new File("output/testSchema");
        // xsdDocumentationService.debug = true;
        xsdDocumentationService.setXsdFilePath(SIMPLE_XSD_FILE);
        xsdDocumentationService.setMethod(XsdDocumentationService.ImageOutputMethod.SVG);
        xsdDocumentationService.processXsd(true);


        // xsdDocumentationService.generateRootPage(testFilePath);
        // xsdDocumentationService.generateComplexTypePages(testFilePath);
        // xsdDocumentationService.generateDetailPages(testFilePath);

        //  xsdDocumentationService.generateHtmlDocumentation(new File("output/test123"));
    }

    @Test
    void generateHtmlDoc() throws Exception {
        // --- 1. Konfiguration und Generierung der Dokumentation ---
        final var testFilePath = Paths.get(XML_LATEST_XSD);
        final var outputFilePath = Paths.get("../FundsXML_Documentation");

        this.xsdDocumentationService.setXsdFilePath(testFilePath.toString());
        this.xsdDocumentationService.setUseMarkdownRenderer(true);
        this.xsdDocumentationService.imageOutputMethod = XsdDocumentationService.ImageOutputMethod.SVG;
        this.xsdDocumentationService.setParallelProcessing(true);
        this.xsdDocumentationService.generateXsdDocumentation(outputFilePath.toFile());

        // --- 2. Eingebetteten HTTP-Server starten ---
        int port = 8080;

        // KORREKTUR: Der SimpleFileServer benötigt einen absoluten Pfad.
        // Wir wandeln den relativen Pfad in einen absoluten um und normalisieren ihn.
        Path docRootPath = outputFilePath.toAbsolutePath().normalize();
        File docRoot = docRootPath.toFile();

        // Erstellt einen einfachen Dateiserver, der den Inhalt des docRoot-Verzeichnisses bereitstellt.
        // Diese Funktionalität ist seit JDK 18 standardmäßig verfügbar.
        HttpServer server = SimpleFileServer.createFileServer(
                new InetSocketAddress(port),
                docRootPath, // Verwende den jetzt absoluten Pfad
                SimpleFileServer.OutputLevel.INFO
        );
        server.start();

        logger.info("====================================================================");
        logger.info("HTTP-Server gestartet auf http://localhost:{}", port);
        logger.info("Das Stammverzeichnis ist: {}", docRoot.getAbsolutePath());
        logger.info("Stoppen Sie den Test in Ihrer IDE, um den Server zu beenden.");
        logger.info("====================================================================");


        // --- 3. Browser mit der Server-URL öffnen ---
        String url = "http://localhost:" + port + "/index.html";
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new java.net.URI(url));
            } else {
                logger.warn("Konnte den Browser nicht automatisch öffnen. Bitte öffnen Sie manuell: {}", url);
            }
        } catch (Exception e) {
            logger.error("Fehler beim Öffnen des Browsers.", e);
        }

        // --- 4. Den Test-Thread (und damit den Server) am Leben halten ---
        // Der Server läuft in einem Hintergrund-Thread. Dieser Haupt-Thread muss blockiert werden,
        // damit der Test nicht sofort endet und der Server heruntergefahren wird.
        Thread.sleep(Long.MAX_VALUE);
    }

    @Test
    void createHtmlTable420() {
        xsdDocumentationService.setXsdFilePath(XML_420_XSD);
        // xsdDocumentationService.generateDocumentation("test-doc.html");
    }

    @Test
    void createHtmlTable() {
        xsdDocumentationService.setXsdFilePath(XML_306_XSD);
        // xsdDocumentationService.generateDocumentation("test-doc_306.html");
    }

    @Test
    void generateXsdSourceFromNode() {
        Map<String, String> complexTypes = new HashMap<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new FileReader(XML_420_XSD)));

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            XPathFactory xPathFactory = new net.sf.saxon.xpath.XPathFactoryImpl();
            XPath xPath = xPathFactory.newXPath();
            String expression = "//xs:complexType[@name='AccountType']";
            NodeList nodeList = (NodeList) xPath.evaluate(expression, doc, XPathConstants.NODESET);

            for (int i = 0; i < nodeList.getLength(); i++) {
                Node n = nodeList.item(i);
                String elementName = ((Element) n).getAttribute("name");
                if (!elementName.isEmpty()) {
                    logger.debug("Element: {}", elementName);
                    StringWriter buf = new StringWriter();
                    transformer.transform(new DOMSource(n), new StreamResult(buf));
                    complexTypes.put(elementName, buf.toString());
                }
            }

            logger.debug("OUTPUT FOR AccountType: ");
            logger.debug(complexTypes.get("AccountType"));

        } catch (Exception exe) {
            logger.error(exe.getMessage());
        }
    }


    @Test
    public void t2() throws ParserConfigurationException, XPathExpressionException, IOException, SAXException {
        String schema = "<xs:schema xmlns:xs=\"http://w...content-available-to-author-only...3.org/2001/XMLSchema\" targetNamespace=\"http://x...content-available-to-author-only...e.com/cloud/adapter/nxsd/surrogate/request\"\r\n" +
                "       xmlns=\"http://x...content-available-to-author-only...e.com/cloud/adapter/nxsd/surrogate/request\"\r\n" +
                "       elementFormDefault=\"qualified\">\r\n" +
                "<xs:element name=\"myapp\">\r\n" +
                "    <xs:complexType>\r\n" +
                "    <xs:sequence>\r\n" +
                "        <xs:element name=\"content\">\r\n" +
                "            <xs:complexType>\r\n" +
                "                <xs:sequence>\r\n" +
                "                    <xs:element name=\"EmployeeID\" type=\"xs:string\" maxOccurs=\"1\" minOccurs=\"0\"/>\r\n" +
                "                    <xs:element name=\"EName\" type=\"xs:string\" maxOccurs=\"1\" minOccurs=\"0\"/>\r\n" +
                "                </xs:sequence>\r\n" +
                "            </xs:complexType>\r\n" +
                "        </xs:element>\r\n" +
                "        <xs:element name=\"attribute\">\r\n" +
                "            <xs:complexType>\r\n" +
                "                <xs:sequence>\r\n" +
                "                    <xs:element name=\"item\" type=\"xs:integer\" maxOccurs=\"1\" minOccurs=\"0\"/>\r\n" +
                "                </xs:sequence>\r\n" +
                "            </xs:complexType>\r\n" +
                "        </xs:element>\r\n" +
                "    </xs:sequence>\r\n" +
                "    </xs:complexType>\r\n" +
                "</xs:element>\r\n" +
                "</xs:schema>\r\n";
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(schema)));

        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xPath = xPathFactory.newXPath();

        String expression = "//element[@name='myapp']//element[@name='content']";
        NodeList nodeList = (NodeList) xPath.evaluate(expression, doc, XPathConstants.NODESET);

        System.out.println("Node count: " + nodeList.getLength());
    }

    @Test
    void compareParallelVsSequentialPerformance() throws Exception {
        logger.info("Starting performance comparison: Parallel vs. Sequential Documentation Generation");

        // --- Configuration ---
        final String xsdFile = XML_420_XSD; // A reasonably complex XSD for a good test
        final File outputDirSequential = new File("../output/docs_sequential");
        final File outputDirParallel = new File("../output/docs_parallel");

        // --- 1. Sequential Execution ---
        logger.info("--- Running Sequential Test ---");
        XsdDocumentationService sequentialService = new XsdDocumentationService();
        sequentialService.setXsdFilePath(xsdFile);
        sequentialService.setParallelProcessing(false);
        sequentialService.imageOutputMethod = XsdDocumentationService.ImageOutputMethod.SVG;

        long startTimeSequential = System.currentTimeMillis();
        sequentialService.generateXsdDocumentation(outputDirSequential);
        long endTimeSequential = System.currentTimeMillis();
        long durationSequential = endTimeSequential - startTimeSequential;
        logger.info("--- Sequential execution finished in: {} ---", formatDuration(durationSequential));

        // --- 2. Parallel Execution ---
        logger.info("--- Running Parallel Test ---");
        XsdDocumentationService parallelService = new XsdDocumentationService();
        parallelService.setXsdFilePath(xsdFile);
        parallelService.setParallelProcessing(true);
        parallelService.imageOutputMethod = XsdDocumentationService.ImageOutputMethod.SVG;

        long startTimeParallel = System.currentTimeMillis();
        parallelService.generateXsdDocumentation(outputDirParallel);
        long endTimeParallel = System.currentTimeMillis();
        long durationParallel = endTimeParallel - startTimeParallel;
        logger.info("--- Parallel execution finished in: {} ---", formatDuration(durationParallel));

        // --- 3. Summary ---
        logger.info("================== PERFORMANCE SUMMARY ==================");
        logger.info("Sequential Time: {}", formatDuration(durationSequential));
        logger.info("Parallel Time:   {}", formatDuration(durationParallel));

        if (durationSequential > 0 && durationParallel > 0) {
            double improvement = ((double) (durationSequential - durationParallel) / durationSequential) * 100;
            logger.info("Performance Improvement with Parallel Processing: {}%", String.format("%.2f", improvement));
        }
        logger.info("=======================================================");
    }

    /**
     * Formatiert eine Millisekunden-Dauer in einen lesbareren String, der auch Sekunden enthält.
     *
     * @param millis Die Dauer in Millisekunden.
     * @return Ein formatierter String (z.B. "12345 ms (12.35 s)").
     */
    private String formatDuration(long millis) {
        return String.format("%,d ms (%.2f s)", millis, millis / 1000.0);
    }
}
