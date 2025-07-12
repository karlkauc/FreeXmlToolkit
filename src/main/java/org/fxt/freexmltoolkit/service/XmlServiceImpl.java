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

import net.sf.saxon.s9api.*;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.validator.routines.UrlValidator;
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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class XmlServiceImpl implements XmlService {

    private final static Logger logger = LogManager.getLogger(XmlService.class);

    private static final XmlServiceImpl instance = new XmlServiceImpl();
    private static final ConnectionService connectionService = ConnectionServiceImpl.getInstance();

    final String CACHE_DIR = FileUtils.getUserDirectory().getAbsolutePath() + File.separator + ".freeXmlToolkit" + File.separator + "cache";
    XPathFactory xPathFactory = new net.sf.saxon.xpath.XPathFactoryImpl();
    XPath xPathPath = xPathFactory.newXPath();
    Processor processor = new Processor(false);
    XsltCompiler compiler = processor.newXsltCompiler();
    StringWriter sw;
    Xslt30Transformer transformer;
    UrlValidator urlValidator = new UrlValidator();
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
    Transformer transform;
    XsltExecutable stylesheet;
    File currentXmlFile = null, currentXsltFile = null, currentXsdFile = null;
    DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder;
    Document xmlDocument;

    Schema schema;
    Validator validator;
    Element rootElement;
    String targetNamespace;

    private String remoteXsdLocation;
    private String xsltOutputMethod;

    private String xmlContent;

    public XmlServiceImpl() {
        try {
            transform = TransformerFactory.newInstance().newTransformer();
            transform.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transform.setOutputProperty(OutputKeys.INDENT, "yes");
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    public static XmlServiceImpl getInstance() {
        return instance;
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
        if (currentXmlFile != null && currentXmlFile.exists() && isContainBOM(currentXmlFile.toPath())) {
            removeBom(currentXmlFile.toPath());
        }

        this.currentXmlFile = currentXmlFile;

        try {
            if (currentXmlFile != null && currentXmlFile.exists() && currentXmlFile.getName().toLowerCase().endsWith("xml")) {
                var schemaLocation = getSchemaNameFromCurrentXMLFile();
                if (schemaLocation.isPresent() && !schemaLocation.get().isEmpty()) {
                    remoteXsdLocation = schemaLocation.get();
                }
            }
        } catch (Exception ignore) {
        }

        try {
            FileInputStream fileIS = new FileInputStream(this.currentXmlFile);
            this.builder = builderFactory.newDocumentBuilder();
            this.xmlDocument = builder.parse(fileIS);
            this.xmlContent = String.join(System.lineSeparator(), Files.readAllLines(this.currentXmlFile.toPath()));

        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.error(e.getLocalizedMessage());
        }
    }

    @Override
    public String getFormatedXmlFile() {
        String t = "";
        try {
            t = String.join(System.lineSeparator(), Files.readAllLines(this.currentXmlFile.toPath()));
        } catch (IOException ioException) {
            logger.error(ioException.getMessage());
        }
        return prettyFormat(t, 20);
    }

    @Override
    public void prettyFormatCurrentFile() {
        logger.debug("pretty format file");
        try {
            var temp = String.join(System.lineSeparator(), Files.readAllLines(this.currentXmlFile.toPath()));
            temp = prettyFormat(temp, 20);
            Files.write(this.currentXmlFile.toPath(), temp.getBytes());
            logger.debug("done: {}", temp.getBytes().length);
        } catch (IOException ioException) {
            logger.error(ioException.getMessage());
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
            final var builder = builderFactory.newDocumentBuilder();
            final var xmlDocument = builder.parse(fileIS);

            final String expression = "/stylesheet/output/@method";
            final XPath xPath = XPathFactory.newInstance().newXPath();
            final var nodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);

            if (nodeList != null && nodeList.getLength() != 0) {
                logger.debug("Output Method: {}", nodeList.item(0).getNodeValue());
                this.xsltOutputMethod = nodeList.item(0).getNodeValue();
            }
        } catch (XPathExpressionException e) {
            logger.error("Could not detect output Method.");
            logger.error(e.getMessage());
        } catch (ParserConfigurationException | IOException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public File getCurrentXsdFile() {
        return this.currentXsdFile;
    }

    @Override
    public void setCurrentXsdFile(File xsdFile) {
        this.currentXsdFile = xsdFile;

        try {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            this.schema = schemaFactory.newSchema(xsdFile);

            builderFactory = DocumentBuilderFactory.newInstance();
            builderFactory.setNamespaceAware(true);
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document document = builder.parse(xsdFile);

            this.rootElement = document.getDocumentElement();
            this.targetNamespace = rootElement.getAttribute("targetNamespace");
        } catch (SAXException | IOException | ParserConfigurationException e) {
            logger.error("Could not set current XSD File: {}", xsdFile.getAbsolutePath());
            logger.error(e.getMessage());
        }

    }

    @Override
    public String getCurrentXsdString() {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            StringWriter stringWriter = new StringWriter();
            transformer.transform(new DOMSource(this.xmlDocument), new StreamResult(stringWriter));
            return stringWriter.toString();
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
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
    public File createExcelValidationReport(File file, List<SAXParseException> errorList) {
        logger.debug("Writing Excel File: {} - {} errors.", file.getName(), errorList.size());

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var fileContent = Files.readAllLines(this.getCurrentXmlFile().toPath());
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
            FileOutputStream outputStream = new FileOutputStream(file);
            workbook.write(outputStream);
            outputStream.close();

            return file;
        } catch (Exception ignored) {
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
    public Node getNodeFromXpath(String xPath) {
        try {
            return (Node) xPathPath.compile(xPath).evaluate(xmlDocument, XPathConstants.NODE);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    @Override
    public Node getNodeFromXpath(String xPath, Node currentNode) {
        if (xPath == null || xPath.trim().isEmpty()) {
            logger.warn("XPath expression is null or empty. Cannot proceed.");
            return null;
        }
        if (currentNode == null) {
            logger.warn("Current node is null. Cannot evaluate XPath on a null node.");
            return null;
        }

        try {
            final XPath localXPath = xPathFactory.newXPath();
            return (Node) localXPath.compile(xPath).evaluate(currentNode, XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            logger.error("Error evaluating XPath expression '{}' on the current node. Msg: {}", xPath, e.getMessage(), e);
        }
        return null;
    }

    @Override
    public String getNodeAsString(Node node) {
        try {
            sw = new StringWriter();
            transform.transform(new DOMSource(node), new StreamResult(sw));
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

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(resultNode), new StreamResult(sw));
            return sw.toString();

        } catch (XPathExpressionException | TransformerException e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    @Override
    public String getXmlFromXpath(String xml, String xPath) {
        try {
            var nodeList = (NodeList) xPathPath.compile(xPath).evaluate(xmlDocument, XPathConstants.NODESET);
            sw = new StringWriter();

            for (int i = 0; i < nodeList.getLength(); i++) {
                Node n = nodeList.item(i);

                transform.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                transform.setOutputProperty(OutputKeys.INDENT, "yes");
                var swTemp = new StringWriter();
                transform.transform(new DOMSource(n), new StreamResult(swTemp));
                sw.append(swTemp.toString()).append(System.lineSeparator());
            }

            return sw.toString();

        } catch (XPathExpressionException | TransformerException e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    @Override
    public String getXmlFromXpath(String xPathQueryString) {
        try {
            return getXmlFromXpath(xmlContent, xPathQueryString);
        } catch (Exception e) {
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

            var validUrl = urlValidator.isValid(temp);
            logger.debug("Valid URL: {}", validUrl);

            if (validUrl) {
                try {
                    URL url = new URI(temp).toURL();
                    var protocol = url.getProtocol();
                    logger.debug("Protocol: {}", protocol);

                    if (protocol.equals("http") || protocol.equals("https")) {
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
                            var pathNew = Path.of(newFile.getAbsolutePath());

                            try {
                                String textContent = connectionService.getTextContentFromURL(new URI(possibleSchemaLocation.get()));
                                Files.write(pathNew, textContent.getBytes());
                                logger.debug("Write new file '{}' with {} Bytes.", pathNew.toFile().getAbsoluteFile(), pathNew.toFile().length());
                                this.currentXsdFile = new File(pathNew.toUri());

                                return true;
                            } catch (Exception e) {
                                logger.error(e.getMessage());
                                return false;
                            }
                        }
                    } else {
                        logger.debug("Schema do not start with http!");
                    }
                } catch (URISyntaxException | MalformedURLException e) {
                    logger.error(e.getMessage());
                }
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
            // System.out.println(e.getMessage());
            return input;
        }
    }
}
