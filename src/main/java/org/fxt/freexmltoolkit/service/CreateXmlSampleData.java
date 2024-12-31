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
import org.apache.xerces.xs.XSModel;

import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.nio.file.Path;

public class CreateXmlSampleData {

    public static void main(String[] args) {
        Path p = Path.of("src/test/resources/FundsXML_306.xsd");
        var s = createData(p, "FundsXML", "http://www.fundsxml.org/XMLSchema/3.0.6");
        System.out.println(s);
    }

    public static String createData(Path schemaFilePath, String rootElementName, String namespaceURI) {
        try {
            XSModel xsModel = new XSParser().parse(schemaFilePath.toUri().toString());

            XSInstance xsInstance = new XSInstance();
            xsInstance.minimumElementsGenerated = 2;
            xsInstance.maximumElementsGenerated = 4;
            xsInstance.generateOptionalElements = Boolean.TRUE; // null means random

            StringWriter stringWriter = new StringWriter();
            XMLDocument sampleXml = new XMLDocument(new StreamResult(stringWriter), false, 4, null);
            QName rootElement = new QName(namespaceURI, rootElementName);
            xsInstance.generate(xsModel, rootElement, sampleXml);

            return stringWriter.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rootElementName;
    }
}
