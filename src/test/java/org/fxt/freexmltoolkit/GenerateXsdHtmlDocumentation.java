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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.FileInputStream;
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
            // /xs:schema/xs:complexType[@name="ControlDataType"]

            final String xPath = "/xs:schema/xs:complexType[@name=\"ControlDataType\"]";
            // //xs:element[@name="ControlDataType"]
            FileInputStream fileIS = new FileInputStream(fileName);
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document xmlDocument = builder.parse(fileIS);
            XPath xPathPath = XPathFactory.newInstance().newXPath();
            var nodeList = (NodeList) xPathPath.compile(xPath).evaluate(xmlDocument, XPathConstants.NODESET);


            Node elem = nodeList.item(0);
            StringWriter buf = new StringWriter();
            Transformer xform = TransformerFactory.newInstance().newTransformer();
            xform.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes"); // optional
            xform.setOutputProperty(OutputKeys.INDENT, "yes"); // optional
            xform.transform(new DOMSource(elem), new StreamResult(buf));

            logger.debug("OUTPUT: " + buf);


        } catch (Exception exe) {
            exe.printStackTrace();
        }
    }
}
