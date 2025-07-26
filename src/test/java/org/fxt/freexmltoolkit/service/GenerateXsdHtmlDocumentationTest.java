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

import jlibs.xml.sax.XMLDocument;
import jlibs.xml.xsd.XSInstance;
import jlibs.xml.xsd.XSParser;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xerces.xs.XSModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
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
        final var testFilePath = Paths.get(XML_LATEST_XSD);
        // final var testFilePath = Paths.get(SIMPLE_XSD_FILE);
        final var outputFilePath = Paths.get("../FundsXML_Documentation");
        this.xsdDocumentationService.setXsdFilePath(testFilePath.toString());
        this.xsdDocumentationService.setUseMarkdownRenderer(true);
        this.xsdDocumentationService.imageOutputMethod = XsdDocumentationService.ImageOutputMethod.SVG;
        this.xsdDocumentationService.setParallelProcessing(true);
        this.xsdDocumentationService.generateXsdDocumentation(outputFilePath.toFile());
        Desktop.getDesktop().open(new File(outputFilePath.toFile().getAbsolutePath() + "/index.html"));
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
    public void createXmlSampleData() throws TransformerConfigurationException {
        final var testFilePath = Paths.get("examples/xsd/FundsXML_306.xsd");
        XSModel xsModel = new XSParser().parse(testFilePath.toUri().toString());
        XSInstance xsInstance = new XSInstance();
        xsInstance.minimumElementsGenerated = 2;
        xsInstance.maximumElementsGenerated = 4;
        xsInstance.generateOptionalElements = Boolean.TRUE; // null means random

        Writer s = new StringWriter();
        QName rootElement = new QName("http://www.fundsxml.org/XMLSchema/3.0.6", "FundsXML");
        XMLDocument sampleXml = new XMLDocument(new StreamResult(s), false, 4, null);
        logger.debug("start generating xml");
        xsInstance.generate(xsModel, rootElement, sampleXml);
        logger.debug("end generating xml");

        var fileName = Path.of("testdata.xml");

        try {
            Files.write(fileName, s.toString().getBytes());
            logger.debug("File written: {} bytes", FileUtils.byteCountToDisplaySize(fileName.toFile().length()));
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

}
