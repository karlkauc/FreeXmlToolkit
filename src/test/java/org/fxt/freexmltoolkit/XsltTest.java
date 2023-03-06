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
        File inputXslt = new File("/Users/karlkauc/IdeaProjects/FreeXmlToolkit/examples/xslt/Check_FundsXML_File.xslt");
        File inputXml = new File("/Users/karlkauc/IdeaProjects/FreeXmlToolkit/examples/xml/BondFund.xml");

        XmlService xmlService = XmlServiceImpl.getInstance();
        xmlService.setCurrentXmlFile(inputXml);
        xmlService.setCurrentXsltFile(inputXslt);

        try {
            Processor processor = new Processor(false);
            XsltCompiler compiler = processor.newXsltCompiler();
            XsltExecutable stylesheet = compiler.compile(new StreamSource(xmlService.getCurrentXsltFile()));
            StringWriter sw = new StringWriter();
            Serializer out = processor.newSerializer();
            out.setOutputProperty(Serializer.Property.METHOD, "html");
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
