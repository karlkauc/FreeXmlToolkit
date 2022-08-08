package org.fxt.freexmltoolkit.service;

import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;


public interface XmlService {

    File getCurrentXmlFile();

    void setCurrentXmlFile(File currentXmlFile);

    File getCurrentXsltFile();

    void setCurrentXsltFile(File currentXsltFile);

    File getCurrentXsdFile();
    void setCurrentXsdFile(File xsdFile);

    String getCurrentXml();

    void setCurrentXml(String currentXml);

    String saxonTransform() throws TransformerException, FileNotFoundException;

    List<SAXParseException> validate();

    String getSchemaFromXMLFile();

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
}
