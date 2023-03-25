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

package org.fxt.freexmltoolkit.service;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;


public interface XmlService {

    File getCurrentXmlFile();

    void setCurrentXmlFile(File currentXmlFile);

    File getCurrentXsltFile();

    void setCurrentXsltFile(File currentXsltFile);

    File getCurrentXsdFile();

    String getRemoteXsdLocation();

    String getXsltOutputMethod();

    void setCurrentXsdFile(File xsdFile);

    String getCurrentXml();

    void setCurrentXml(String currentXml);

    String performXsltTransformation();

    Document getXmlDocument();


    // Schema Validation Methods
    List<SAXParseException> validate();

    List<SAXParseException> validateText(String xmlString);

    List<SAXParseException> validateText(String xmlString, File schemaFile);

    List<SAXParseException> validateFile(File xml);

    List<SAXParseException> validateFile(File xml, File schemaFile);

    File createExcelValidationReport();

    File createExcelValidationReport(File fileName);

    File createExcelValidationReport(File fileName, List<SAXParseException> errorList);

    Optional<String> getSchemaNameFromCurrentXMLFile();

    boolean loadSchemaFromXMLFile();

    Node getNodeFromXpath(String xPath);

    Node getNodeFromXpath(String xPath, Node currentNode);

    String getNodeAsString(Node node);

    String getXmlFromXpath(String xPath, Node node);

    String getXmlFromXpath(String xPath);

    static String prettyFormat(String input, int indent) {
        try {
            Transformer transformer = SAXTransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            //transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(indent));

            Source xmlSource = new SAXSource(new InputSource(new ByteArrayInputStream(input.getBytes())));
            StreamResult res = new StreamResult(new ByteArrayOutputStream());
            transformer.transform(xmlSource, res);
            return res.getOutputStream().toString();
        } catch (Exception e) {
            System.out.println("FEHLER");
            System.out.println(e.getMessage());
            return input;
        }
    }


    void removeBom(Path path);
}
