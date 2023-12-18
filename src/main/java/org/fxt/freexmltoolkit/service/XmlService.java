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

    static String convertXmlToOneLine(String xml) throws TransformerException {
        final String xslt =
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                        "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" +
                        "    <xsl:output indent=\"no\"/>\n" +
                        "    <xsl:strip-space elements=\"*\"/>\n" +
                        "    <xsl:template match=\"@*|node()\">\n" +
                        "        <xsl:copy>\n" +
                        "            <xsl:apply-templates select=\"@*|node()\"/>\n" +
                        "        </xsl:copy>\n" +
                        "    </xsl:template>\n" +
                        "</xsl:stylesheet>";

        /* prepare XSLT transformer from String */
        Source xsltSource = new StreamSource(new StringReader(xslt));
        TransformerFactory factory = TransformerFactory.newInstance();
        Transformer transformer = factory.newTransformer(xsltSource);

        /* where to read the XML? */
        Source source = new StreamSource(new StringReader(xml));

        /* where to write the XML? */
        StringWriter stringWriter = new StringWriter();
        Result result = new StreamResult(stringWriter);

        /* transform XML to one line */
        transformer.transform(source, result);

        return stringWriter.toString();
    }


    String removeBom(String s);

    void removeBom(Path path);
}
