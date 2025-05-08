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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;

public class CreateXmlSampleData {

    public static void main(String[] args) {

        String xsdPath = "src/test/resources/FundsXML_306.xsd";
        String xmlPath = "output_111.xml";

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document xsdDoc = builder.parse(new File(xsdPath));

            Node rootElement = findRootElement(xsdDoc);
            if (rootElement == null) {
                System.out.println("No root element found in XSD.");
                return;
            }

            Document xmlDoc = builder.newDocument();
            Element xmlRoot = xmlDoc.createElement(rootElement.getAttributes().getNamedItem("name").getNodeValue());
            xmlDoc.appendChild(xmlRoot);

            generateSampleElements(xsdDoc, xmlRoot, rootElement.getAttributes().getNamedItem("type").getNodeValue(), xmlDoc);

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(xmlDoc), new StreamResult(new File(xmlPath)));

            System.out.println("Sample XML generated at: " + xmlPath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Node findRootElement(Document xsdDoc) {
        NodeList elements = xsdDoc.getElementsByTagName("xs:element");
        for (int i = 0; i < elements.getLength(); i++) {
            Node node = elements.item(i);
            if (node.getParentNode().getNodeName().equals("xs:schema")) {
                System.out.println("node.getNodeName() = " + node.getNodeName());
                return node;
            }
        }
        return null;
    }

    private static void generateSampleElements(Document xsdDoc, Element parent, String typeName, Document xmlDoc) {
        NodeList complexTypes = xsdDoc.getElementsByTagName("xs:complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            if (complexType.getAttribute("name").equals(typeName)) {
                NodeList sequences = complexType.getElementsByTagName("xs:sequence");
                for (int j = 0; j < sequences.getLength(); j++) {
                    NodeList elements = ((Element) sequences.item(j)).getElementsByTagName("xs:element");
                    for (int k = 0; k < elements.getLength(); k++) {
                        Element el = (Element) elements.item(k);
                        String name = el.getAttribute("name");
                        Element child = xmlDoc.createElement(name);
                        child.setTextContent("sample_" + name);
                        parent.appendChild(child);
                    }
                }
            }
        }
    }


}
