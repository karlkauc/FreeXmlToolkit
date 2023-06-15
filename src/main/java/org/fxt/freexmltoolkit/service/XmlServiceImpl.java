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

import net.sf.saxon.s9api.*;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class XmlServiceImpl implements XmlService {
    private final static Logger logger = LogManager.getLogger(XmlService.class);

    XPathFactory xPathFactory = new net.sf.saxon.xpath.XPathFactoryImpl();
    XPath xPathPath = xPathFactory.newXPath();

    Processor processor = new Processor(false);
    XsltCompiler compiler = processor.newXsltCompiler();

    StringWriter sw;

    Xslt30Transformer transformer;

    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

    XsltExecutable stylesheet;

    File currentXmlFile = null, currentXsltFile = null, currentXsdFile = null;

    private String currentXML;

    private String remoteXsdLocation;

    private String xsltOutputMethod;

    DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder;
    Document xmlDocument;

    Schema schema;
    Validator validator;

    HttpClient client;
    HttpRequest request;

    final String CACHE_DIR = FileUtils.getUserDirectory().getAbsolutePath() + File.separator + ".freeXmlToolkit" + File.separator + "cache";

    private static final XmlServiceImpl instance = new XmlServiceImpl();

    private XmlServiceImpl() {
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
        if (isContainBOM(currentXmlFile.toPath())) {
            removeBom(currentXmlFile.toPath());
        }

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
            logger.error("Could not detect output Method.");
            logger.error(e.getMessage());
        }
    }

    @Override
    public File getCurrentXsdFile() {
        return this.currentXsdFile;
    }

    @Override
    public String getCurrentXsdString() throws IOException {
        return Files.readString(Path.of(this.currentXsdFile.toURI()));
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
    public String performXsltTransformation() {
        try {
            stylesheet = compiler.compile(new StreamSource(getCurrentXsltFile()));
            sw = new StringWriter();
            Serializer out = processor.newSerializer();
            out.setOutputProperty(Serializer.Property.METHOD, "html");
            out.setOutputProperty(Serializer.Property.INDENT, "yes");
            out.setOutputWriter(sw);

            transformer = stylesheet.load30();
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
            schema = factory.newSchema(new StreamSource(schemaFile));
            validator = schema.newValidator();

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
    public File createExcelValidationReport() {
        return createExcelValidationReport(new File("ValidationErrors.xlsx"), validate());
    }

    @Override
    public File createExcelValidationReport(File fileName) {
        return createExcelValidationReport(fileName, validate());
    }

    @Override
    public File createExcelValidationReport(File fileName, List<SAXParseException> errorList) {
        logger.debug("Writing Excel File: {} - {} errors.", fileName.getName(), errorList.size());

        try {
            var fileContent = Files.readAllLines(this.getCurrentXmlFile().toPath());
            XSSFWorkbook workbook = new XSSFWorkbook();
            XSSFSheet sheet = workbook.createSheet("Validation Error Report");

            CellStyle style = workbook.createCellStyle();
            style.setWrapText(true);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("#");
            header.createCell(1).setCellValue("Error Message");
            header.createCell(2).setCellValue("Line#");
            header.createCell(3).setCellValue("Col#");
            header.createCell(4).setCellValue("XML Content");

            header.setRowStyle(headerStyle);

            // Fix header Row
            sheet.createFreezePane(0, 1);

            for (int i = 0; i < errorList.size(); i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(errorList.get(i).getLocalizedMessage());
                row.createCell(2).setCellValue(errorList.get(i).getLineNumber());
                row.createCell(3).setCellValue(errorList.get(i).getColumnNumber());

                var content = row.createCell(4);
                content.setCellValue(
                        fileContent.get(errorList.get(i).getLineNumber() - 2).trim() +
                                System.lineSeparator() +
                                fileContent.get(errorList.get(i).getLineNumber() - 1).trim() +
                                System.lineSeparator() +
                                fileContent.get(errorList.get(i).getLineNumber()).trim()
                );
                content.setCellStyle(style);

                row.setHeight((short) -1);
            }
            FileOutputStream outputStream = new FileOutputStream(fileName);
            workbook.write(outputStream);

            return fileName;
        } catch (Exception e) {
        }

        return null;
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
    public Node getNodeFromXpath(String xPath) {
        try {
            FileInputStream fileInputStream = new FileInputStream(this.getCurrentXmlFile());
            xmlDocument = builder.parse(fileInputStream);

            var node = (Node) xPathPath.compile(xPath).evaluate(xmlDocument, XPathConstants.NODE);
            return node;
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    @Override
    public Node getNodeFromXpath(String xPath, Node currentNode) {
        try {
            xPathPath = xPathFactory.newXPath();
            var node = (Node) xPathPath.compile(xPath).evaluate(currentNode, XPathConstants.NODE);
            return node;
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    @Override
    public String getNodeAsString(Node node) {
        try {
            sw = new StringWriter();
            Transformer xform = TransformerFactory.newInstance().newTransformer();
            xform.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            xform.setOutputProperty(OutputKeys.INDENT, "yes");
            xform.transform(new DOMSource(node), new StreamResult(sw));
            return sw.toString();

        } catch (TransformerException e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    @Override
    public String getXmlFromXpath(String xPath, Node node) {
        try {
            var resultNode = (Node) xPathPath.compile(xPath).evaluate(node, XPathConstants.NODE);
            sw = new StringWriter();

            Transformer xform = TransformerFactory.newInstance().newTransformer();
            xform.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            xform.setOutputProperty(OutputKeys.INDENT, "yes");
            xform.transform(new DOMSource(resultNode), new StreamResult(sw));
            return sw.toString();

        } catch (XPathExpressionException | TransformerException e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    @Override
    public String getXmlFromXpath(String xPathQueryString) {
        try {
            FileInputStream fileInputStream = new FileInputStream(this.getCurrentXmlFile());
            xmlDocument = builder.parse(fileInputStream);

            var nodeList = (NodeList) xPathPath.compile(xPathQueryString).evaluate(xmlDocument, XPathConstants.NODESET);
            sw = new StringWriter();

            if (nodeList.getLength() > 0) {
                Node node = nodeList.item(0);

                Transformer xform = TransformerFactory.newInstance().newTransformer();
                xform.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                xform.setOutputProperty(OutputKeys.INDENT, "yes");
                xform.transform(new DOMSource(node), new StreamResult(sw));
                // logger.debug(buf.toString());
            }
            return sw.toString();

        } catch (XPathExpressionException | TransformerException | IOException | SAXException e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    @Override
    public List<String> getXQueryResult(String xQuery) {
        List<String> resultList = new LinkedList<>();

        try {
            Processor saxon = new Processor(false);

            XQueryCompiler compiler = saxon.newXQueryCompiler();
            XQueryExecutable xpathExecutable = compiler.compile(xQuery);

            net.sf.saxon.s9api.DocumentBuilder builder = saxon.newDocumentBuilder();

            Source src = new StreamSource(new StringReader(Files.readString(Path.of(this.currentXmlFile.toURI()))));
            XdmNode doc = builder.build(src);

            XQueryEvaluator query = xpathExecutable.load();
            query.setContextItem(doc);
            XdmValue result = query.evaluate();

            logger.debug("Result Size: {}", result.size());
            for (var r : result) {
                logger.debug("Result Line: {}", r.toString());
                resultList.add(r.toString());
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            return null;
        }

        return resultList;
    }


    @Override
    public boolean loadSchemaFromXMLFile() {
        var prop = propertiesService.loadProperties();
        logger.debug("Properties: {}", prop);
        this.currentXsdFile = null;

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
            var temp = possibleSchemaLocation.get().trim();

            if (temp.toLowerCase().startsWith("http://") || temp.toLowerCase().startsWith("https://")) {
                String md5Hex = DigestUtils.md5Hex(temp.toLowerCase()).toUpperCase();
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
                if (!possibleSchemaLocation.isEmpty() && possibleSchemaLocation.toLowerCase().endsWith(".xsd")) {
                    logger.debug("Possible Schema Location: {}", possibleSchemaLocation);
                    return Optional.of(possibleSchemaLocation);
                } else {
                    logger.debug("Possible Schema Location empty or doesn't end with .xsd");
                }
                return Optional.empty();

            } catch (IOException | ParserConfigurationException | SAXException exception) {
                logger.error("Error: {}", exception.getMessage());
                return Optional.empty();
            }
        } else {
            logger.debug("Kein XML File ausgewählt!");
        }

        return Optional.empty();
    }

    @Override
    public String removeBom(String s) {
        byte[] bom = new byte[3];
        try (InputStream is = new ByteArrayInputStream(s.getBytes(Charset.defaultCharset()))) {
            String content = new String(Hex.encodeHex(bom));
            if ("efbbbf".equalsIgnoreCase(content)) {
                ByteBuffer bb = ByteBuffer.wrap(is.readAllBytes());

                bom = new byte[3];
                bb.get(bom, 0, bom.length);

                byte[] contentAfterFirst3Bytes = new byte[is.readAllBytes().length - 3];
                bb.get(contentAfterFirst3Bytes, 0, contentAfterFirst3Bytes.length);

                // override the same path
                return Arrays.toString(contentAfterFirst3Bytes);
            }
        } catch (IOException e) {
            // throw new RuntimeException(e);
            logger.error("ERROR: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public void removeBom(Path path) {
        try {
            if (isContainBOM(path)) {
                byte[] bytes = Files.readAllBytes(path);
                ByteBuffer bb = ByteBuffer.wrap(bytes);
                logger.debug("Found BOM in File: {}", path.toString());

                // get the first 3 bytes
                byte[] bom = new byte[3];
                bb.get(bom, 0, bom.length);

                // remaining
                byte[] contentAfterFirst3Bytes = new byte[bytes.length - 3];
                bb.get(contentAfterFirst3Bytes, 0, contentAfterFirst3Bytes.length);

                // override the same path
                Files.write(path, contentAfterFirst3Bytes);
            } else {
                logger.debug("This file doesn't contains UTF-8 BOM: {}", path.toString());
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    static boolean isContainBOM(Path path) {
        if (Files.notExists(path)) {
            throw new IllegalArgumentException("Path: " + path + " does not exists!");
        }

        boolean result = false;

        byte[] bom = new byte[3];
        try (InputStream is = new FileInputStream(path.toFile())) {

            // read 3 bytes of a file.
            is.read(bom);

            // BOM encoded as ef bb bf
            String content = new String(Hex.encodeHex(bom));
            if ("efbbbf".equalsIgnoreCase(content)) {
                result = true;
            }

        } catch (IOException ignore) {
        }

        return result;
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
