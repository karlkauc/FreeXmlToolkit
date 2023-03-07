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

import net.sf.saxon.s9api.*;
import org.fxt.freexmltoolkit.service.XmlService;
import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.junit.jupiter.api.Test;

import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.StringWriter;

public class XsltTest {


    @Test
    void testTransform() {
        File inputXslt = new File("C:\\Data\\TEMP\\2023-03-07_XSLT_TEST\\test.xslt");
        File inputXml = new File("C:\\Data\\TEMP\\2023-03-07_XSLT_TEST\\data.xml");

        XmlService xmlService = XmlServiceImpl.getInstance();
        xmlService.setCurrentXmlFile(inputXml);
        xmlService.setCurrentXsltFile(inputXslt);

        try {
            Processor processor = new Processor(false);
            XsltCompiler compiler = processor.newXsltCompiler();
            XsltExecutable stylesheet = compiler.compile(new StreamSource(xmlService.getCurrentXsltFile()));
            StringWriter sw = new StringWriter();
            Serializer out = processor.newSerializer();
            // out.setOutputProperty(Serializer.Property.METHOD, "html");
            out.setOutputProperty(Serializer.Property.INDENT, "yes");
            out.setOutputWriter(sw);

            Xslt30Transformer transformer = stylesheet.load30();
            transformer.transform(new StreamSource(xmlService.getCurrentXmlFile()), out);

            System.out.println("out.toString() = " + sw);

        } catch (SaxonApiException e) {
            throw new RuntimeException(e);
        }

        System.out.println("OK");

    }
}
