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
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.stream.StreamResult;

public class CreateXmlSampleData {

    public static void main(String[] args) throws TransformerConfigurationException {
        createData();
    }

    public static void createData() {
        try {
            XSModel xsModel = new XSParser().parse("FundsXML4.xsd");

            XSInstance xsInstance = new XSInstance();
            xsInstance.minimumElementsGenerated = 2;
            xsInstance.maximumElementsGenerated = 4;
            xsInstance.generateOptionalElements = Boolean.TRUE; // null means random

            XMLDocument sampleXml = new XMLDocument(new StreamResult(System.out), true, 4, null);
            xsInstance.generate(xsModel, new QName("http://www.fundsxml.org", "FundsXML4"), sampleXml);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
