package org.fxt.freexmltoolkit.service;

import com.google.inject.Inject;
import net.sf.saxon.TransformerFactoryImpl;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedList;
import java.util.List;

public class XmlServiceImpl implements XmlService {
    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    @Inject
    PropertiesService propertiesService;

    File currentXmlFile = null, currentXsltFile = null, currentXsdFile = null;

    private String currentXML;

    @Override
    public File getCurrentXmlFile() {
        return currentXmlFile;
    }

    @Override
    public void setCurrentXmlFile(File currentXmlFile) {
        this.currentXmlFile = currentXmlFile;
    }

    @Override
    public File getCurrentXsltFile() {
        return currentXsltFile;
    }

    @Override
    public void setCurrentXsltFile(File currentXsltFile) {
        this.currentXsltFile = currentXsltFile;
    }

    @Override
    public File getCurrentXsdFile() {
        return this.currentXsdFile;
    }

    @Override
    public void setCurrentXsdFile(File xsdFile) {
        this.currentXsdFile = xsdFile;

    }

    @Override
    public String getCurrentXml() {
        if (currentXML != null) {
            logger.debug("get Current XML Content {}", currentXML.length());
        } else {
            logger.debug("get current XML - NULL");
        }

        return currentXML;
    }


    @Override
    public String saxonTransform() throws TransformerException, FileNotFoundException {
        TransformerFactoryImpl f = new TransformerFactoryImpl();
        f.setAttribute("http://saxon.sf.net/feature/version-warning", Boolean.FALSE);

        StreamSource xsrc = new StreamSource(new FileInputStream(currentXsltFile));
        Transformer t = f.newTransformer(xsrc);
        StreamSource src = new StreamSource(new FileInputStream(currentXmlFile));
        StreamResult res = new StreamResult(new ByteArrayOutputStream());
        t.transform(src, res);
        return res.getOutputStream().toString();
    }

    public List<SAXParseException> validate() {
        final List<SAXParseException> exceptions = new LinkedList<>();
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(new StreamSource(this.currentXsdFile));
            Validator validator = schema.newValidator();

            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) throws SAXException {
                    exceptions.add(exception);
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    exceptions.add(exception);
                }

                @Override
                public void error(SAXParseException exception) throws SAXException {
                    exceptions.add(exception);
                }
            });

            StreamSource xmlFile = new StreamSource(this.currentXmlFile);
            validator.validate(xmlFile);

            return exceptions;

        } catch (SAXException | IOException e) {
            e.printStackTrace();
            return exceptions;
        }
    }

    @Override
    public void setCurrentXml(String currentXml) {
        logger.debug("set XML Content {}", currentXml.length());
        this.currentXML = currentXml;
    }

    @Override
    public String getSchemaFromXMLFile() {
        var prop = propertiesService.loadProperties();
        logger.debug("Properties: {}", prop);

        if (this.currentXmlFile != null && this.currentXmlFile.exists()) {
            try {
                FileInputStream fileIS = new FileInputStream(this.currentXmlFile);
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = builderFactory.newDocumentBuilder();
                Document xmlDocument = builder.parse(fileIS);

                Element root = xmlDocument.getDocumentElement();
                logger.debug("ROOT: {}", root);

                var possibleSchemaLocation = root.getAttribute("xsi:noNamespaceSchemaLocation");
                if (!possibleSchemaLocation.isEmpty()) {
                    logger.debug("Possible Schema Location: {}", possibleSchemaLocation);
                    if (possibleSchemaLocation.trim().toLowerCase().startsWith("http://") ||
                            possibleSchemaLocation.trim().toLowerCase().startsWith("https://")) {

                        URL url = new URL(possibleSchemaLocation);
                        URLConnection connection;

                        if (!prop.get("http.proxy.host").toString().isEmpty() && !prop.get("http.proxy.port").toString().isEmpty()) {
                            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(prop.get("http.proxy.host").toString(), Integer.parseInt(prop.get("http.proxy.port").toString())));
                            connection = url.openConnection(proxy);
                        } else {
                            connection = url.openConnection();
                        }
                        InputStream is = connection.getInputStream();
                        String newFileName = FilenameUtils.getName(url.getPath());

                        File newFile = new File(newFileName);
                        Files.copy(is, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        this.setCurrentXsdFile(newFile);
                        logger.debug("Loaded file to: {}", newFile.getAbsolutePath());

                        return newFile.getName();
                    }
                } else {
                    logger.debug("No possible Schema Location found!");
                }

            } catch (IOException | ParserConfigurationException | SAXException exception) {
                logger.error(exception.getMessage());
            }
        } else {
            logger.debug("Kein XML File ausgewählt!");
        }

        return null;
    }
}
