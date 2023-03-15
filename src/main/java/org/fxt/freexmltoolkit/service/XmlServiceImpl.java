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

package org.fxt.freexmltoolkit.service;

import net.sf.saxon.s9api.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
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
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class XmlServiceImpl implements XmlService {
    private final static Logger logger = LogManager.getLogger(XmlService.class);

    PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

    File currentXmlFile = null, currentXsltFile = null, currentXsdFile = null;

    private String currentXML;

    private String remoteXsdLocation;

    private String xsltOutputMethod;

    DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder;
    Document xmlDocument;

    HttpClient client;
    HttpRequest request;

    final String CACHE_DIR = FileUtils.getUserDirectory().getAbsolutePath() + File.separator + ".freeXmlToolkit" + File.separator + "cache";

    private static final XmlServiceImpl instance = new XmlServiceImpl();

    private XmlServiceImpl() {
        logger.debug("BIN IM XML SERVICE IMPL CONSTRUCTOR");
    }

    public static XmlServiceImpl getInstance() {
        return instance;
    }

    @Override
    public Document getXmlDocument() {
        return xmlDocument;
    }

    @Override
    public File getCurrentXmlFile() {
        return currentXmlFile;
    }

    @Override
    public void setCurrentXmlFile(File currentXmlFile) {
        this.currentXmlFile = currentXmlFile;

        try {
            var schemaLocation = getSchemaNameFromCurrentXMLFile();
            if (schemaLocation.isPresent() && schemaLocation.get().length() > 0) {
                remoteXsdLocation = schemaLocation.get();
            }
        } catch (Exception ignore) {
        }
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
    public String saxonTransform() {
        try {
            Processor processor = new Processor(false);
            XsltCompiler compiler = processor.newXsltCompiler();
            XsltExecutable stylesheet = compiler.compile(new StreamSource(getCurrentXsltFile()));
            StringWriter sw = new StringWriter();
            Serializer out = processor.newSerializer();
            out.setOutputProperty(Serializer.Property.METHOD, "html");
            out.setOutputProperty(Serializer.Property.INDENT, "yes");
            out.setOutputWriter(sw);

            Xslt30Transformer transformer = stylesheet.load30();
            transformer.transform(new StreamSource(getCurrentXmlFile()), out);

            return sw.toString();
        } catch (SaxonApiException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public List<SAXParseException> validateText(String xmlString, File schemaFile) {

        if (schemaFile == null) {
            logger.warn("Schema File is empty.");
            return null;
        }

        final List<SAXParseException> exceptions = new LinkedList<>();
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(new StreamSource(schemaFile));
            Validator validator = schema.newValidator();

            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                    exceptions.add(exception);
                }

                @Override
                public void fatalError(SAXParseException exception) {
                    exceptions.add(exception);
                }

                @Override
                public void error(SAXParseException exception) {
                    exceptions.add(exception);
                }
            });

            StreamSource xmlStreamSource = new StreamSource(new StringReader(xmlString));
            validator.validate(xmlStreamSource);

            return exceptions;

        } catch (SAXException | IOException e) {
            logger.error(e.getMessage());
            return exceptions;
        }
    }

    @Override
    public List<SAXParseException> validateFile(File xml) {
        if (xml == null || !xml.exists()) {
            return null;
        }
        return validateFile(xml, currentXsdFile);
    }

    @Override
    public List<SAXParseException> validateText(String xmlString) {
        return validateText(xmlString, currentXsdFile);
    }

    @Override
    public List<SAXParseException> validateFile(File xml, File schemaFile) {
        if (xml == null || schemaFile == null
                || !xml.exists() || !schemaFile.exists()) {
            logger.warn("Files do not exits.");
            return null;
        }

        try {
            String s = Files.readString(xml.toPath());
            return validateText(s, schemaFile);
        } catch (IOException ioException) {
            return null;
        }
    }

    public List<SAXParseException> validate() {
        logger.debug("Validate File [{}] with schema [{}].", currentXmlFile.toPath().toString(), currentXsdFile.toPath().toString());
        return validateFile(currentXmlFile, currentXsdFile);
    }

    @Override
    public void setCurrentXml(String currentXml) {
        logger.debug("set XML Content {}", currentXml.length());
        this.currentXML = currentXml;
    }

    @Override
    public String getXmlFromXpath(String xPath) {
        try {
            FileInputStream fileIS = new FileInputStream(this.getCurrentXmlFile());
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document xmlDocument = builder.parse(fileIS);
            XPath xPathPath = XPathFactory.newInstance().newXPath();
            var nodeList = (NodeList) xPathPath.compile(xPath).evaluate(xmlDocument, XPathConstants.NODESET);

            Node elem = nodeList.item(0);//Your Node
            StringWriter buf = new StringWriter();
            Transformer xform = TransformerFactory.newInstance().newTransformer();
            xform.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes"); // optional
            xform.setOutputProperty(OutputKeys.INDENT, "yes"); // optional
            xform.transform(new DOMSource(elem), new StreamResult(buf));

            logger.debug(buf.toString());

            return buf.toString();
        } catch (XPathExpressionException | ParserConfigurationException |
                 TransformerException | IOException | SAXException e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    @Override
    public boolean loadSchemaFromXMLFile() {
        var prop = propertiesService.loadProperties();
        logger.debug("Properties: {}", prop);

        try {
            if (!new File(CACHE_DIR).exists()) {
                logger.debug("Create cache path: {}", CACHE_DIR);
                Files.createDirectories(Paths.get(CACHE_DIR));
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }

        var possibleSchemaLocation = getSchemaNameFromCurrentXMLFile();

        if (possibleSchemaLocation.isPresent()) {
            var temp = possibleSchemaLocation.get();

            if (possibleSchemaLocation.get().trim().toLowerCase().startsWith("http://") ||
                    possibleSchemaLocation.get().trim().toLowerCase().startsWith("https://")) {

                String md5Hex = DigestUtils.md5Hex(possibleSchemaLocation.get().trim().toLowerCase()).toUpperCase();
                logger.debug("Cache path: {}", md5Hex);

                final Path CURRENT_XSD_CACHE_PATH = Path.of(CACHE_DIR + File.separator + md5Hex);
                logger.debug("Absolute cache Path: {}", CURRENT_XSD_CACHE_PATH);

                if (!Files.exists(CURRENT_XSD_CACHE_PATH)) {
                    try {
                        Files.createDirectories(CURRENT_XSD_CACHE_PATH);
                    } catch (IOException e) {
                        logger.error(e.getMessage());
                    }
                }

                String fileNameNew = FilenameUtils.getName(possibleSchemaLocation.get());
                String possibleFileName = CURRENT_XSD_CACHE_PATH + File.separator + fileNameNew;
                logger.debug("Cache File: {}", possibleFileName);

                File newFile = new File(possibleFileName);
                if (newFile.exists() && newFile.length() > 1) {
                    logger.debug("Load file from cache: {}", newFile.getAbsolutePath());
                    this.setCurrentXsdFile(newFile);
                    this.remoteXsdLocation = possibleSchemaLocation.get();

                    return true;
                } else {
                    logger.debug("Did not find cached Schema file.");

                    var proxySelector = ProxySelector.getDefault();
                    if (prop.get("http.proxy.host") != null && prop.get("http.proxy.port") != null) {
                        logger.debug("PROXY HOST: {}", prop.get("http.proxy.host"));
                        logger.debug("PROXY PORT: {}", prop.get("http.proxy.port"));
                        proxySelector = ProxySelector.of(
                                new InetSocketAddress(
                                        prop.get("http.proxy.host").toString(),
                                        Integer.parseInt(prop.get("http.proxy.port").toString())));
                    }

                    client = HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_2)
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .connectTimeout(Duration.ofSeconds(20))
                            .proxy(proxySelector)
                            .build();

                    request = HttpRequest.newBuilder()
                            .uri(URI.create(possibleSchemaLocation.get()))
                            .build();

                    var pathNew = Path.of(newFile.getAbsolutePath());

                    try {
                        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(pathNew));
                        logger.debug("HTTP Status Code: {}", response.statusCode());
                        logger.debug("Loaded file to: {}", pathNew.toFile().getAbsolutePath());

                        if (pathNew.toFile().exists() && pathNew.toFile().length() > 1) {
                            this.setCurrentXsdFile(pathNew.toFile());
                            this.remoteXsdLocation = possibleSchemaLocation.get();
                            return true;
                        } else {
                            logger.error("File nicht gefunden oder kein Fileinhalt: {}", pathNew.getFileName());
                        }
                    } catch (IOException | InterruptedException exception) {
                        logger.error(exception.getMessage());
                    }
                    return false;
                }
            } else {
                logger.debug("Schema do not start with http!");
            }
        }
        return false;
    }

    @Override
    public Optional<String> getSchemaNameFromCurrentXMLFile() {
        if (this.currentXmlFile != null && this.currentXmlFile.exists()) {
            try {
                FileInputStream fileIS = new FileInputStream(this.currentXmlFile);
                builder = builderFactory.newDocumentBuilder();
                xmlDocument = builder.parse(fileIS);

                Element root = xmlDocument.getDocumentElement();
                logger.debug("ROOT: {}", root);

                String possibleSchemaLocation;
                possibleSchemaLocation = root.getAttribute("xsi:schemaLocation");
                if (possibleSchemaLocation.contains(" ")) {
                    // e.g. xsi:schemaLocation="http://www.fundsxml.org/XMLSchema/3.0.6 FundsXML3.0.6.xsd"
                    String[] splitStr = possibleSchemaLocation.split("\\s+");
                    String possibleFileName = splitStr[1];
                    String possibleFilePath = this.currentXmlFile.getParent() + "/" + possibleFileName;
                    if (new File(possibleFilePath).exists()) {
                        logger.debug("Found Schema at: {}", possibleFilePath);

                        return Optional.of("file://" + possibleFilePath);
                    } else {
                        logger.debug("Do not found schema at: {}", possibleFilePath);
                    }
                }

                logger.debug("Typing xsi:noNamespaceSchemaLocation...");
                possibleSchemaLocation = root.getAttribute("xsi:noNamespaceSchemaLocation");
                if (!possibleSchemaLocation.isEmpty()) {
                    logger.debug("Possible Schema Location: {}", possibleSchemaLocation);
                    return Optional.of(possibleSchemaLocation);
                } else {
                    logger.debug("No possible Schema Location found!");
                }

                logger.debug("Trying xmlns...");
                possibleSchemaLocation = root.getAttribute("xmlns");
                logger.debug("Schema Location: {}", possibleSchemaLocation);
                if (!possibleSchemaLocation.isEmpty()) {
                    logger.debug("Possible Schema Location: {}", possibleSchemaLocation);
                    return Optional.of(possibleSchemaLocation);
                }

            } catch (IOException | ParserConfigurationException | SAXException exception) {
                // logger.error(exception.getMessage());
                logger.error("Error");
            }
        } else {
            logger.debug("Kein XML File ausgewählt!");
        }

        return Optional.empty();
    }

    public String prettyFormat(String input, int indent) {
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
            // System.out.println(e.getMessage());
            return input;
        }
    }
}
