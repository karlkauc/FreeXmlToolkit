package org.fxt.freexmltoolkit.service;

import org.xml.sax.InputSource;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;


public interface XmlService {

    String getCurrentXml();

    void setCurrentXml(String currentXml);

    static String prettyFormat(String input, int indent) {
        try {
            Transformer serializer = SAXTransformerFactory.newInstance().newTransformer();
            serializer.setOutputProperty(OutputKeys.INDENT, "yes");
            //serializer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            try {
                serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(indent));
            } catch (Exception e) {
                System.out.println("INDENT HAT NICHT FUNKTIONIERT");
            }

            //serializer.setOutputProperty("{http://xml.customer.org/xslt}indent-amount", "2");
            Source xmlSource = new SAXSource(new InputSource(new ByteArrayInputStream(input.getBytes())));
            StreamResult res = new StreamResult(new ByteArrayOutputStream());
            serializer.transform(xmlSource, res);
            return res.getOutputStream().toString();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return input;
        }
    }
}
