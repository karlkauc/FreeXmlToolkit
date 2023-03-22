/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) 2023.
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
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.Test;
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
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

public class GenerateXsdHtmlDocumentation {
    final static String fileName = "src/test/resources/FundsXML_420.xsd";
    final XsdDocumentationService xsdDocumentationService = new XsdDocumentationService();

    private final static Logger logger = LogManager.getLogger(GenerateXsdHtmlDocumentation.class);

    @Test
    void createHtmlTable() {
        xsdDocumentationService.setXsdFilePath(fileName);
        xsdDocumentationService.generateDocumentation("test-doc.html");
    }

    @Test
    void generateXsdSourceFromNode() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new FileReader(fileName)));

            NodeList elementList = doc.getElementsByTagName("xs:element");
            for (int i = 0; i < elementList.getLength(); i++) {
                Element element = (Element) elementList.item(i);
                if (element.hasAttributes() && element.getAttribute("type") != "") {
                    System.out.println("element.getNodeValue() = " + element.getTextContent());

                    Node elem = element;
                    StringWriter buf = new StringWriter();
                    Transformer xform = TransformerFactory.newInstance().newTransformer();
                    xform.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes"); // optional
                    xform.setOutputProperty(OutputKeys.INDENT, "yes"); // optional
                    xform.transform(new DOMSource(elem), new StreamResult(buf));

                    logger.debug(buf.toString());
                }
            }

            XPathFactory xPathFactory = XPathFactory.newInstance();
            XPath xPath = xPathFactory.newXPath();

            String expression = "//xs:element[@name='FundsXML4']//xs:element[@name='ControlDataType']";
            NodeList nodeList = (NodeList) xPath.evaluate(expression, doc, XPathConstants.NODESET);

            System.out.println("Node count: " + nodeList.getLength());

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
