package org.fxt.freexmltoolkit.service;

import com.google.inject.Inject;
import net.sf.saxon.TransformerFactoryImpl;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;

public class XmlServiceImpl implements XmlService {
    private final static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    @Inject
    PropertiesService propertiesService;

    File currentXmlFile = null, currentXsltFile = null, currentXsdFile = null;

    private String currentXML;

    private String remoteXsdLocation;

    private String xsltOutputMethod;

    DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder;
    Document xmlDocument;

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

        // output methode ermitteln!!
        try {
            FileInputStream fileIS = new FileInputStream(this.currentXsltFile);
            builder = builderFactory.newDocumentBuilder();
            xmlDocument = builder.parse(fileIS);

            String expression = "/stylesheet/output/@method";
            XPath xPath = XPathFactory.newInstance().newXPath();
            var nodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
            logger.debug("Output Method: {}", nodeList.item(0).getNodeValue());

            this.xsltOutputMethod = nodeList.item(0).getNodeValue();
        } catch (ParserConfigurationException | IOException | SAXException | XPathExpressionException e) {
            logger.error("Could not detect output Method: ");
            logger.error(e.getMessage());
        }
    }

    @Override
    public File getCurrentXsdFile() {
        return this.currentXsdFile;
    }

    @Override
    public String getRemoteXsdLocation() {
        return this.remoteXsdLocation;
    }

    @Override
    public String getXsltOutputMethod() {
        return this.xsltOutputMethod;
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
            logger.error(e.getMessage());
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
                builder = builderFactory.newDocumentBuilder();
                xmlDocument = builder.parse(fileIS);

                Element root = xmlDocument.getDocumentElement();
                logger.debug("ROOT: {}", root);

                var possibleSchemaLocation = root.getAttribute("xsi:noNamespaceSchemaLocation");

                if (possibleSchemaLocation.isEmpty()) {
                    logger.debug("noNamespaceSchemaLocation not set. Trying schemaLocation");
                    // xsi:schemaLocation="http://example/note example.xsd"
                    possibleSchemaLocation = root.getAttribute("xsi:schemaLocation");
                    logger.debug("Schema Location: {}", possibleSchemaLocation);
                    if (possibleSchemaLocation.contains(" ")) {
                        var temp = possibleSchemaLocation.split(" ");
                        if (temp.length == 2) {
                            possibleSchemaLocation = temp[1];
                        }
                    }
                }

                if (!possibleSchemaLocation.isEmpty()) {
                    logger.debug("Possible Schema Location: {}", possibleSchemaLocation);
                    if (possibleSchemaLocation.trim().toLowerCase().startsWith("http://") ||
                            possibleSchemaLocation.trim().toLowerCase().startsWith("https://")) {

                        var proxySelector = ProxySelector.getDefault();

                        if (prop.get("http.proxy.host") != null && prop.get("http.proxy.port") != null) {
                            proxySelector = ProxySelector.of(
                                    new InetSocketAddress(
                                            prop.get("http.proxy.host").toString(),
                                            Integer.parseInt(prop.get("http.proxy.port").toString())));
                        }

                        HttpClient client = HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_2)
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .connectTimeout(Duration.ofSeconds(20))
                                .proxy(proxySelector)
                                .build();

                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(possibleSchemaLocation))
                                .build();

                        String fileNameNew = FilenameUtils.getName(possibleSchemaLocation);
                        var pathNew = Path.of(fileNameNew);
                        var response = client.send(request, HttpResponse.BodyHandlers.ofFile(pathNew));
                        logger.debug("HTTP Status Code: {}", response.statusCode());
                        logger.debug("Loaded file to: {}", pathNew.toFile().getAbsolutePath());

                        if (pathNew.toFile().exists() && pathNew.toFile().length() > 1) {
                            this.setCurrentXsdFile(pathNew.toFile());
                            this.remoteXsdLocation = possibleSchemaLocation;
                            return pathNew.toFile().getName();
                        } else {
                            logger.error("File nicht gefunden oder kein Fileinhalt: {}", pathNew.getFileName());
                        }
                        return null;
                    }
                } else {
                    logger.debug("No possible Schema Location found!");
                }

            } catch (IOException | ParserConfigurationException | SAXException | InterruptedException exception) {
                logger.error(exception.getMessage());
            }
        } else {
            logger.debug("Kein XML File ausgewählt!");
        }

        return null;
    }
}
