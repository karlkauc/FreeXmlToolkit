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

package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdDocInfo;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.transform.*;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface XmlService {

    // XML Var
    File getCurrentXmlFile();
    void setCurrentXmlFile(File currentXmlFile);

    String getFormatedXmlFile();

    void prettyFormatCurrentFile();

    // XSLT Var
    File getCurrentXsltFile();
    void setCurrentXsltFile(File currentXsltFile);

    String getXsltOutputMethod();

    // XSD Var
    File getCurrentXsdFile();
    String getCurrentXsdString() throws IOException;
    String getRemoteXsdLocation();
    void setCurrentXsdFile(File xsdFile);

    String getLastXsdError();

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

    // XML Path Operations
    String getXmlFromXpath(String xml, String xPath);

    String getXmlFromXpath(String xPath, Node node);

    String getXmlFromXpath(String xPath);

    List<String> getXQueryResult(String xQuery);

    static String prettyFormat(File input, int indent) {
        List<String> allLines;
        try {
            allLines = Files.readAllLines(input.toPath());
            final var temp = String.join(System.lineSeparator(), allLines);
            return prettyFormat(temp, indent);
        } catch (Exception e) {
            return null;
        }
    }

    static String prettyFormat(String input, int indent) {
        Logger logger = LogManager.getLogger(XmlService.class);
        try {
            Transformer transformer = SAXTransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(indent));

            Source xmlSource = new SAXSource(new InputSource(new ByteArrayInputStream(input.getBytes())));
            StreamResult res = new StreamResult(new ByteArrayOutputStream());
            transformer.transform(xmlSource, res);
            return res.getOutputStream().toString();
        } catch (Exception e) {
            logger.error("Error formatting XML: {}", e.getMessage());
            return input;
        }
    }

    static String convertXmlToOneLine(String xml) throws TransformerException {
        if (xml == null || xml.isEmpty()) {
            return xml;
        }
        // This XSLT stylesheet copies the entire XML document while removing
        // ignorable whitespace and outputting the result without any indentation.
        // It robustly converts XML to a single line by stripping space between
        // elements and normalizing text content within elements to remove extra whitespace.
        final String xslt =
                "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">" +
                        "    <xsl:output indent=\"no\"/>" +
                        "    <xsl:strip-space elements=\"*\"/>" +
                        "    <!-- Identity template for attributes and all node types except text nodes -->" +
                        "    <xsl:template match=\"@*|*|comment()|processing-instruction()\">" +
                        "        <xsl:copy>" +
                        "            <xsl:apply-templates select=\"@*|node()\"/>" +
                        "        </xsl:copy>" +
                        "    </xsl:template>" +
                        "    <!-- For text nodes, normalize them to trim whitespace -->" +
                        "    <xsl:template match=\"text()\">" +
                        "        <xsl:value-of select=\"normalize-space(.)\"/>" +
                        "    </xsl:template>" +
                        "</xsl:stylesheet>";

        Source xsltSource = new StreamSource(new StringReader(xslt));
        Transformer transformer = TransformerFactory.newInstance().newTransformer(xsltSource);
        StringWriter writer = new StringWriter();
        transformer.transform(new StreamSource(new StringReader(xml)), new StreamResult(writer));
        return writer.toString();
    }



    String removeBom(String s);

    void removeBom(Path path);

    void updateRootDocumentation(File xsdFile, String documentationContent) throws Exception;

    void updateElementDocumentation(File xsdFile, String elementXpath, String documentation, String javadoc) throws Exception;

    void updateExampleValues(File xsdFile, String elementXpath, List<String> exampleValues) throws Exception;

    XsdDocInfo getElementDocInfo(File xsdFile, String elementXpath) throws Exception;
}
