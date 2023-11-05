/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2023.
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

package org.fxt.freexmltoolkit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.extendedXsd.ExtendedXsdElement;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmlet.xsdparser.core.XsdParser;
import org.xmlet.xsdparser.xsdelements.*;
import org.xmlet.xsdparser.xsdelements.elementswrapper.ReferenceBase;

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
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenerateXsdHtmlDocumentationTest {
    final static String fileName = "src/test/resources/FundsXML_420.xsd";
    final static String fundsXml306Xsd = "src/test/resources/FundsXML_306.xsd";

    final XsdDocumentationService xsdDocumentationService = new XsdDocumentationService();

    private final static Logger logger = LogManager.getLogger(GenerateXsdHtmlDocumentationTest.class);

    String xsdFilePath;
    static final int MAX_ALLOWED_DEPTH = 99;

    private List<XsdComplexType> xsdComplexTypes;
    private List<XsdSimpleType> xsdSimpleTypes;
    private List<XsdElement> elements;

    XsdParser parser;
    List<XsdSchema> xmlSchema;
    Map<String, ExtendedXsdElement> extendedXsdElements;

    XmlService xmlService = XmlServiceImpl.getInstance();

    @Test
    void parseXsdTest() {
        xsdDocumentationService.setXsdFilePath(fileName);
        xsdDocumentationService.processXsd();
        var elements = xsdDocumentationService.getExtendedXsdElements();

        System.out.println("------------");
        for (String s : elements.keySet()) {
            logger.debug("xPath: {}", s);
        }
    }

    @Test
    void generateSeperatedFiles() throws IOException {
        logger.debug("vor filename");
        xsdDocumentationService.setXsdFilePath("src/test/resources/simpleFile.xsd");
        logger.debug("vor process xsd");
        xsdDocumentationService.processXsd();

        logger.debug("vor generate root element");
        xsdDocumentationService.generateXsdDocumentation(new File("output/test123"));

    }




    @Test
    void generateNewTest() {
        xmlService = XmlServiceImpl.getInstance();
        parser = new XsdParser(fileName);
        xmlService.setCurrentXmlFile(new File(fileName));

        elements = parser.getResultXsdElements().toList();
        xmlSchema = parser.getResultXsdSchemas().toList();

        xsdComplexTypes = xmlSchema.get(0).getChildrenComplexTypes().toList();
        xsdSimpleTypes = xmlSchema.get(0).getChildrenSimpleTypes().toList();

        extendedXsdElements = new HashMap<>();

        for (XsdElement xsdElement : elements) {
            var elementName = xsdElement.getRawName();
            System.out.println("elementName = " + elementName);

            Node startNode = xmlService.getNodeFromXpath("//xs:element[@name='" + elementName + "']");
            getXsdAbstractElementInfo(0, xsdElement, List.of(), List.of(), startNode);
        }
    }

    void getXsdAbstractElementInfo(int level,
                                   XsdAbstractElement xsdAbstractElement,
                                   List<String> prevElementTypes,
                                   List<String> prevElementPath,
                                   Node parentNode) {
        logger.debug("prevElementTypes = {}", prevElementTypes);
        if (level > MAX_ALLOWED_DEPTH) {
            logger.error("Too many elements");
            System.err.println("Too many elements");
            return;
        }

        ExtendedXsdElement extendedXsdElement = new ExtendedXsdElement();

        switch (xsdAbstractElement) {
            case XsdElement xsdElement -> {
                logger.debug("ELEMENT: {}", xsdElement.getRawName());
                final String currentXpath = "/" + String.join("/", prevElementPath) + "/" + xsdElement.getName();
                logger.debug("Current XPath = {}", currentXpath);

                var currentType = xsdElement.getType();

                if (currentType == null) {
                    /* reines element - kein complex/simple - aber children. in doku aufnehmen und mit kinder weiter machen */
                    Node n = xmlService.getNodeFromXpath("//xs:element[@name='" + xsdElement.getRawName() + "']", parentNode);
                    String elementString = xmlService.getNodeAsString(n);

                    extendedXsdElement.setCurrentNode(n);
                    extendedXsdElement.setSourceCode(elementString);

                    if (xsdElement.getXsdComplexType() != null) {
                        ArrayList<String> prevTemp = new ArrayList<>(prevElementTypes);
                        prevTemp.add(xsdElement.getName());

                        ArrayList<String> prevPathTemp = new ArrayList<>(prevElementPath);
                        prevPathTemp.add(xsdElement.getName());

                        if (xsdElement.getXsdComplexType().getElements() != null) {
                            for (ReferenceBase referenceBase : xsdElement.getXsdComplexType().getElements()) {
                                getXsdAbstractElementInfo(level + 1, referenceBase.getElement(), prevTemp, prevPathTemp, n);
                            }
                            return;
                        }
                    }
                }

                if (prevElementTypes.stream().anyMatch(str -> str.trim().equals(currentType))) {
                    System.out.println("ELEMENT SCHON BEARBEITET: " + currentType);
                    logger.warn("Element {} schon bearbeitet.", currentType);
                    return;
                } else {
                    logger.debug("noch nicht bearbeitet: {}", currentType);
                }

                // current type beginnt mit xs: oder nicht...
                System.out.println("currentType = " + currentType);
                ArrayList<String> prevTemp = new ArrayList<>(prevElementTypes);
                prevTemp.add(currentType);

                ArrayList<String> prevPathTemp = new ArrayList<>(prevElementPath);
                prevPathTemp.add(xsdElement.getName());

                // complex oder simple type
                if (xsdElement.getXsdComplexType() != null) {
                    XsdComplexType xsdComplexType = xsdElement.getXsdComplexType();
                    System.out.println("xsdComplexType.getName() = " + xsdComplexType.getName());

                    var currentNode = xmlService.getNodeFromXpath("//xs:complexType[@name='" + xsdComplexType.getRawName() + "']", parentNode);
                    extendedXsdElement.setCurrentNode(currentNode);

                    var s = xmlService.getXmlFromXpath("//xs:complexType[@name='" + xsdComplexType.getRawName() + "']");
                    extendedXsdElement.setSourceCode(s);

                    if (xsdElement.getXsdComplexType().getElements() != null) {
                        for (ReferenceBase referenceBase : xsdElement.getXsdComplexType().getElements()) {
                            getXsdAbstractElementInfo(level + 1, referenceBase.getElement(), prevTemp, prevPathTemp, currentNode);
                        }
                        return;
                    }
                }
                if (xsdElement.getXsdSimpleType() != null) {
                    XsdSimpleType xsdSimpleType = xsdElement.getXsdSimpleType();
                    System.out.println("xsdSimpleType = " + xsdSimpleType.getName());
                }

            }
            case XsdChoice xsdChoice -> System.out.println("xsdChoice = " + xsdChoice);
            case XsdSequence xsdSequence -> System.out.println("xsdSequence = " + xsdSequence);
            case XsdAll xsdAll -> System.out.println("xsdAll = " + xsdAll);
            case XsdGroup xsdGroup -> System.out.println("xsdGroup = " + xsdGroup);
            case XsdAttributeGroup xsdAttributeGroup -> System.out.println("xsdAttributeGroup = " + xsdAttributeGroup);

            default -> throw new IllegalStateException("Unexpected value: " + xsdAbstractElement);
        }
    }

    @Test
    void createHtmlTable420() {
        xsdDocumentationService.setXsdFilePath(fileName);
        xsdDocumentationService.generateDocumentation("test-doc.html");
    }

    @Test
    void createHtmlTable() {
        xsdDocumentationService.setXsdFilePath(fundsXml306Xsd);
        xsdDocumentationService.generateDocumentation("test-doc_306.html");
    }

    @Test
    void generateXsdSourceFromNode() {
        Map<String, String> complexTypes = new HashMap<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new FileReader(fileName)));

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
            exe.printStackTrace();
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
                "                    <xs:element name=\"item\" type=\"xs:integer\" maxOccurs=\"1\" minOccurs=\"0\"/>                        \r\n" +
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
}
