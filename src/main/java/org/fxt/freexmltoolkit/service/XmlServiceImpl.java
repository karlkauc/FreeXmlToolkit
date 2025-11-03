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
import org.fxt.freexmltoolkit.domain.XmlParserType;
import org.fxt.freexmltoolkit.domain.XsdDocInfo;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
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
import java.util.*;

public class XmlServiceImpl implements XmlService {

    private final static Logger logger = LogManager.getLogger(XmlService.class);

    private static final XmlServiceImpl instance = new XmlServiceImpl();
    private static final ConnectionService connectionService = ConnectionServiceImpl.getInstance();
    private static final PropertiesService propertiesService = PropertiesServiceImpl.getInstance();

    // Validation services for different parsers
    private final XmlValidationService saxonValidationService = new SaxonXmlValidationService();
    private final XmlValidationService xercesValidationService = new XercesXmlValidationService();

    final String CACHE_DIR = FileUtils.getUserDirectory().getAbsolutePath() + File.separator + ".freeXmlToolkit" + File.separator + "cache";
    XPathFactory xPathFactory = new net.sf.saxon.xpath.XPathFactoryImpl();
    XPath xPathPath = xPathFactory.newXPath();
    Processor processor = new Processor(false);
    XsltCompiler compiler = processor.newXsltCompiler();
    StringWriter sw;
    Xslt30Transformer transformer;
    UrlValidator urlValidator = new UrlValidator();
    SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    Transformer transform;
    XsltExecutable stylesheet;
    private File cachedXsltFile = null; // Added for caching compiled stylesheet
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
    private String lastXsdError; // Store the last XSD loading error

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

        // Reset remote XSD location for new file
        this.remoteXsdLocation = null;

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
            this.xmlContent = Files.readString(this.currentXmlFile.toPath());

        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.error(e.getLocalizedMessage());
        }
    }

    @Override
    public String getFormatedXmlFile() {
        String t = "";
        try {
            t = Files.readString(this.currentXmlFile.toPath());
        } catch (IOException ioException) {
            logger.error(ioException.getMessage());
        }
        return XmlService.prettyFormat(t, propertiesService.getXmlIndentSpaces());
    }

    @Override
    public void prettyFormatCurrentFile() {
        logger.debug("pretty format file");
        try {
            var temp = Files.readString(this.currentXmlFile.toPath());
            temp = XmlService.prettyFormat(temp, propertiesService.getXmlIndentSpaces());
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

        // Add caching logic here
        if (currentXsltFile != null && !currentXsltFile.equals(this.cachedXsltFile)) {
            try {
                this.stylesheet = compiler.compile(new StreamSource(currentXsltFile));
                this.cachedXsltFile = currentXsltFile;
                logger.debug("XSLT stylesheet compiled and cached: {}", currentXsltFile.getAbsolutePath());
            } catch (SaxonApiException e) {
                logger.error("Failed to compile XSLT stylesheet: {}", e.getMessage());
                this.stylesheet = null; // Invalidate cached stylesheet on error
                this.cachedXsltFile = null;
                throw new RuntimeException("Failed to compile XSLT stylesheet", e);
            }
        } else if (currentXsltFile == null) {
            this.stylesheet = null;
            this.cachedXsltFile = null;
        }

        // output methode ermitteln!!
        try {
            if (this.currentXsltFile != null) { // Ensure file is not null before trying to read
                FileInputStream fileIS = new FileInputStream(this.currentXsltFile);
                final var builder = builderFactory.newDocumentBuilder();
                final var xmlDocument = builder.parse(fileIS);

                final String expression = "/stylesheet/output/@method";
                final XPath xPath = XPathFactory.newInstance().newXPath();
                final var nodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);

                if (nodeList != null && nodeList.getLength() != 0) {
                    logger.debug("Output Method: {}", nodeList.item(0).getNodeValue());
                    this.xsltOutputMethod = nodeList.item(0).getNodeValue();
                } else {
                    this.xsltOutputMethod = null; // Reset if no method found
                }
            } else {
                this.xsltOutputMethod = null; // Reset if no XSLT file
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
        // If the provided file is null, reset the schema-related-state.
        if (xsdFile == null) {
            this.currentXsdFile = null;
            this.schema = null;
            this.rootElement = null;
            this.targetNamespace = null;
            logger.debug("Current XSD file has been reset.");
            return; // Exit the method
        }

        // If the file is not null, proceed with loading it.
        this.currentXsdFile = xsdFile;

        try {
            // STEP 1: Parse the XSD file as an XML document (this is required)
            builderFactory = DocumentBuilderFactory.newInstance();
            builderFactory.setNamespaceAware(true);
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document document = builder.parse(xsdFile);

            this.rootElement = document.getDocumentElement();
            this.targetNamespace = rootElement.getAttribute("targetNamespace");
            this.lastXsdError = null; // Clear any previous error on success
            logger.debug("Successfully parsed XSD file as XML document: {}", xsdFile.getAbsolutePath());

            // STEP 2: Try to create a Schema object (optional, for XSD 1.0 validation)
            // This may fail for XSD 1.1 features (assert, alternative, etc.), but that's okay
            try {
                SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                this.schema = schemaFactory.newSchema(xsdFile);
                logger.debug("Successfully created Schema object from XSD file");
            } catch (SAXException e) {
                // Schema creation failed, likely due to XSD 1.1 features
                logger.warn("Could not create Schema object from XSD file (possibly XSD 1.1 features): {}", e.getMessage());
                logger.debug("XSD file will be used without schema validation support");
                this.schema = null; // Set to null, but continue with the parsed document
            }

        } catch (SAXException | IOException | ParserConfigurationException e) {
            logger.error("Could not parse XSD File as XML document: {}", xsdFile.getAbsolutePath(), e);

            // Store detailed error message
            String errorMessage = e.getMessage();
            if (e instanceof SAXException && e.getCause() != null) {
                errorMessage = e.getCause().getMessage();
            }
            this.lastXsdError = errorMessage;

            // Also reset state on failure to ensure consistency
            this.currentXsdFile = null;
            this.schema = null;
            this.rootElement = null;
            this.targetNamespace = null;
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
        if (this.stylesheet == null) {
            throw new IllegalStateException("XSLT stylesheet not set or failed to compile. Call setCurrentXsltFile first.");
        }
        if (getCurrentXmlFile() == null || !getCurrentXmlFile().exists()) {
            throw new IllegalStateException("XML file not set or does not exist.");
        }

        try {
            sw = new StringWriter();
            Serializer out = processor.newSerializer();

            // Use the detected output method, default to "html" if not found
            if (this.xsltOutputMethod != null && !this.xsltOutputMethod.isEmpty()) {
                out.setOutputProperty(Serializer.Property.METHOD, this.xsltOutputMethod);
            } else {
                out.setOutputProperty(Serializer.Property.METHOD, "html"); // Default to html
            }
            out.setOutputProperty(Serializer.Property.INDENT, "yes");
            out.setOutputWriter(sw);

            transformer = stylesheet.load30(); // Reuse compiled stylesheet
            transformer.transform(new StreamSource(getCurrentXmlFile()), out);

            return sw.toString();
        } catch (SaxonApiException e) {
            logger.error("Error during XSLT transformation: {}", e.getMessage());
            throw new RuntimeException("Error during XSLT transformation", e);
        }
    }

    /**
     * Eine private Hilfsmethode, die ausschließlich die Wohlgeformtheit eines XML-Strings prüft.
     * Sie verwendet einen Standard-Parser mit deaktivierter Validierung, um zuverlässige Ergebnisse zu gewährleisten.
     *
     * @param xmlString Der zu prüfende XML-String.
     * @return Eine Liste von SAXParseExceptions. Die Liste ist leer, wenn der String wohlgeformt ist.
     */
    private List<SAXParseException> checkWellFormednessOnly(String xmlString) {
        final List<SAXParseException> exceptions = new LinkedList<>();
        try {
            // Wir verwenden einen Standard DocumentBuilder, da dies der direkteste Weg ist,
            // die Wohlgeformtheit ohne Schema-Validierung zu prüfen.
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(false); // Explizit DTD-Validierung deaktivieren
            dbf.setNamespaceAware(true); // Gute Praxis für XML-Verarbeitung
            DocumentBuilder db = dbf.newDocumentBuilder();

            // Ein ErrorHandler fängt alle Parsing-Fehler ab.
            db.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) { /* Für Wohlgeformtheit ignorieren wir Warnungen */ }

                @Override
                public void error(SAXParseException e) {
                    exceptions.add(e);
                }

                @Override
                public void fatalError(SAXParseException e) {
                    exceptions.add(e);
                }
            });

            // Parsen des Strings. Fehler werden vom ErrorHandler abgefangen.
            db.parse(new org.xml.sax.InputSource(new StringReader(xmlString)));

        } catch (SAXException | IOException | ParserConfigurationException e) {
            // Wenn der Parser früh abbricht (z.B. bei einem leeren String), wird der ErrorHandler
            // möglicherweise nicht aufgerufen. Wir fangen die Exception hier ab, um sicherzustellen,
            // dass immer ein Fehler gemeldet wird.
            if (exceptions.isEmpty()) {
                if (e instanceof SAXParseException) {
                    exceptions.add((SAXParseException) e);
                } else {
                    // Erstellen einer synthetischen Exception für andere Fehler (z.B. IO-Probleme).
                    exceptions.add(new SAXParseException(e.getMessage(), null, e));
                }
            }
        }
        return exceptions;
    }

    /**
     * Detects if a schema uses XSD 1.1 features.
     * @param schemaContent The XSD content to check.
     * @return true if XSD 1.1 features are detected, false otherwise.
     */
    private boolean isXsd11Schema(String schemaContent) {
        if (schemaContent == null || schemaContent.isBlank()) {
            return false;
        }

        // XSD 1.1 specific elements and attributes
        String[] xsd11Features = {
            "<xs:assert", "<xsd:assert", // assertions
            "<xs:alternative", "<xsd:alternative", // type alternatives
            "<xs:openContent", "<xsd:openContent", // open content
            "vc:minVersion=\"1.1\"", // version declaration
            "explicitTimezone=" // XSD 1.1 facet
        };

        for (String feature : xsd11Features) {
            if (schemaContent.contains(feature)) {
                logger.debug("Detected XSD 1.1 feature: {}", feature);
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if the given file is a valid W3C XML Schema.
     * Handles both XSD 1.0 and XSD 1.1 schemas appropriately.
     * @param schemaFile The XSD file to check.
     * @return true if the schema is valid, false otherwise.
     */
    private boolean isSchemaValid(File schemaFile) {
        if (schemaFile == null || !schemaFile.exists()) {
            return false;
        }

        try {
            // First, check if this is an XSD 1.1 schema
            String schemaContent = Files.readString(schemaFile.toPath());

            if (isXsd11Schema(schemaContent)) {
                // For XSD 1.1, we can't use the standard SchemaFactory (it only supports 1.0)
                // Instead, just verify it's well-formed XML
                logger.debug("Detected XSD 1.1 schema: {}", schemaFile.getAbsolutePath());

                try {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setNamespaceAware(true);
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    db.parse(schemaFile);
                    logger.debug("XSD 1.1 schema is well-formed XML");
                    return true;
                } catch (Exception e) {
                    logger.warn("XSD 1.1 schema is not well-formed XML: {}", e.getMessage());
                    return false;
                }
            }

            // For XSD 1.0, use the standard validation
            factory.newSchema(new StreamSource(schemaFile));
            return true;
        } catch (IOException e) {
            logger.error("Could not read schema file: {}", schemaFile.getAbsolutePath(), e);
            return false;
        } catch (SAXException e) {
            // This exception indicates that the schema itself is invalid.
            logger.warn("The provided schema file '{}' is not a valid W3C XML Schema. Reason: {}", schemaFile.getAbsolutePath(), e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the given string content is a valid W3C XML Schema.
     * Handles both XSD 1.0 and XSD 1.1 schemas appropriately.
     * @param schemaContent The XSD content as a string.
     * @return true if the schema is valid, false otherwise.
     */
    private boolean isSchemaValid(String schemaContent) {
        if (schemaContent == null || schemaContent.isBlank()) {
            return false;
        }

        try {
            if (isXsd11Schema(schemaContent)) {
                // For XSD 1.1, we can't use the standard SchemaFactory (it only supports 1.0)
                // Instead, just verify it's well-formed XML
                logger.debug("Detected XSD 1.1 schema content");

                try {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setNamespaceAware(true);
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    db.parse(new org.xml.sax.InputSource(new StringReader(schemaContent)));
                    logger.debug("XSD 1.1 schema content is well-formed XML");
                    return true;
                } catch (Exception e) {
                    logger.warn("XSD 1.1 schema content is not well-formed XML: {}", e.getMessage());
                    return false;
                }
            }

            // For XSD 1.0, use the standard validation
            factory.newSchema(new StreamSource(new StringReader(schemaContent)));
            return true;
        } catch (SAXException e) {
            // This exception indicates that the schema itself is invalid.
            logger.warn("The provided schema content is not a valid W3C XML Schema. Reason: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<SAXParseException> validateText(String xmlString, File schemaFile) {
        // Get the configured validation service based on user settings
        XmlValidationService validationService = getValidationService();
        logger.debug("Using {} for XML validation", validationService.getValidatorName());

        // Delegate validation to the selected service
        return validationService.validateText(xmlString, schemaFile);
    }

    /**
     * Gets the appropriate validation service based on user settings.
     *
     * @return the configured validation service
     */
    private XmlValidationService getValidationService() {
        XmlParserType parserType = propertiesService.getXmlParserType();

        return switch (parserType) {
            case XERCES -> xercesValidationService;
            case SAXON -> saxonValidationService;
        };
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

            // Spaltenbreite für bessere Lesbarkeit anpassen
            // Die Einheit ist 1/256 eines Zeichens
            sheet.setColumnWidth(1, 20_000); // Spalte "Error Message" (ca. 78 Zeichen)
            sheet.setColumnWidth(4, 25_000); // Spalte "XML Content" (ca. 97 Zeichen)

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
                                // NEU: Schema-Inhalt vor dem Speichern validieren
                                if (isSchemaValid(textContent)) {
                                    Files.write(pathNew, textContent.getBytes());
                                    logger.debug("Write new file '{}' with {} Bytes.", pathNew.toFile().getAbsoluteFile(), pathNew.toFile().length());
                                    this.setCurrentXsdFile(new File(pathNew.toUri()));
                                    this.remoteXsdLocation = possibleSchemaLocation.get();

                                    // Download imported XSD files recursively
                                    downloadImportedSchemas(textContent, possibleSchemaLocation.get(), CURRENT_XSD_CACHE_PATH);
                                    
                                    return true;
                                } else {
                                    logger.error("Downloaded schema from {} is not valid and will not be saved.", possibleSchemaLocation.get());
                                    return false;
                                }
                            } catch (Exception e) {
                                logger.error("Failed to download or process schema from {}: {}", possibleSchemaLocation.get(), e.getMessage());
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
                    String[] splitStr = possibleSchemaLocation.split(" +");
                    String possibleFileName = splitStr[1];

                    // First check if it's already a complete URL
                    if (possibleFileName.startsWith("http://") || possibleFileName.startsWith("https://")) {
                        logger.debug("Found remote Schema URL: {}", possibleFileName);
                        return Optional.of(possibleFileName);
                    }

                    // Check for local file
                    String possibleFilePath = this.currentXmlFile.getParent() + "/" + possibleFileName;
                    if (new File(possibleFilePath).exists()) {
                        logger.debug("Found local Schema at: {}", possibleFilePath);
                        return Optional.of("file://" + possibleFilePath);
                    } else {
                        logger.debug("Local schema not found at: {}, checking if second part is URL", possibleFilePath);
                        // Only return if it looks like a URL, not just a filename
                        if (possibleFileName.startsWith("http://") || possibleFileName.startsWith("https://") || possibleFileName.contains(".")) {
                            logger.debug("Second part looks like a URL or path: {}", possibleFileName);
                            return Optional.of(possibleFileName);
                        } else {
                            logger.debug("Second part is just a filename without URL indicators: {}", possibleFileName);
                        }
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

    @Override
    public void updateRootDocumentation(File xsdFile, String documentationContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xsdFile);

        Element root = doc.getDocumentElement();
        final String xsdNs = "http://www.w3.org/2001/XMLSchema";

        // Finde oder erstelle xsd:annotation
        NodeList annotationList = root.getElementsByTagNameNS(xsdNs, "annotation");
        Element annotation;
        if (annotationList.getLength() > 0) {
            annotation = (Element) annotationList.item(0);
        } else {
            annotation = doc.createElementNS(xsdNs, "xsd:annotation");
            // Füge es als erstes Kind des Wurzelelements ein
            root.insertBefore(annotation, root.getFirstChild());
        }

        // Finde oder erstelle xsd:documentation in xsd:annotation
        NodeList docList = annotation.getElementsByTagNameNS(xsdNs, "documentation");
        Element documentationElement;
        if (docList.getLength() > 0) {
            documentationElement = (Element) docList.item(0);
        } else {
            documentationElement = doc.createElementNS(xsdNs, "xsd:documentation");
            annotation.appendChild(documentationElement);
        }

        // Setze den neuen Inhalt
        documentationElement.setTextContent(documentationContent);

        // Schreibe den Inhalt zurück in die Datei mit anständiger Formatierung
        writeDocumentToFile(doc, xsdFile);
    }

    @Override
    public void updateElementDocumentation(File xsdFile, String elementXpath, String documentation, String javadoc) throws Exception {
        // 1. Parse the document
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xsdFile);
        Element root = doc.getDocumentElement();

        // 2. Define namespaces
        final String xsdNs = "http://www.w3.org/2001/XMLSchema";

        // 3. Find the target element using XPath resolution logic
        if (elementXpath == null || !elementXpath.startsWith("/")) {
            throw new IllegalArgumentException("Invalid XPath provided (must start with '/'): " + elementXpath);
        }

        String[] parts = elementXpath.substring(1).split("/");
        if (parts.length == 0 || parts[0].isEmpty()) {
            throw new IllegalArgumentException("Invalid XPath provided (empty): " + elementXpath);
        }

        // Use the same robust XPath resolution logic as updateExampleValues
        XPath xpath = XPathFactory.newInstance().newXPath();
        Node contextNode = doc; // Start from the document root

        for (String part : parts) {
            if (part.isEmpty()) continue;

            String query = getQuery(part);
            Node foundNode = (Node) xpath.compile(query).evaluate(contextNode, XPathConstants.NODE);

            // If not found, it might be in a referenced type.
            if (foundNode == null && contextNode.getNodeType() == Node.ELEMENT_NODE) {
                Element contextElement = (Element) contextNode;
                String typeAttr = contextElement.getAttribute("type");
                if (!typeAttr.isEmpty()) {
                    // We have a type reference, e.g., "tns:PurchaseOrderType"
                    String typeName = typeAttr.contains(":") ? typeAttr.split(":")[1] : typeAttr;

                    // Find the type definition anywhere in the document.
                    String typeQuery = "//*[local-name()='complexType' or local-name()='simpleType'][@name='" + typeName + "']";
                    Node typeDefinitionNode = (Node) xpath.compile(typeQuery).evaluate(doc, XPathConstants.NODE);

                    if (typeDefinitionNode != null) {
                        // Search for our part inside this type definition.
                        foundNode = (Node) xpath.compile(query).evaluate(typeDefinitionNode, XPathConstants.NODE);
                    }
                }
            }

            if (foundNode == null) {
                throw new IllegalArgumentException("Could not resolve path segment: '" + part + "' in XPath: " + elementXpath);
            }
            contextNode = foundNode;
        }

        Node targetNode = contextNode;
        if (targetNode.getNodeType() != Node.ELEMENT_NODE) {
            throw new IllegalArgumentException("Could not find element for XPath: " + elementXpath);
        }
        Element targetElement = (Element) targetNode;

        // 4. Find or create xs:annotation
        NodeList annotationList = targetElement.getElementsByTagNameNS(xsdNs, "annotation");
        Element annotation = (annotationList.getLength() > 0)
                ? (Element) annotationList.item(0)
                : (Element) targetElement.insertBefore(doc.createElementNS(xsdNs, "xs:annotation"), targetElement.getFirstChild());

        // 5. Update documentation
        if (documentation != null && !documentation.trim().isEmpty()) {
            // Find or create xs:documentation element
            NodeList docList = annotation.getElementsByTagNameNS(xsdNs, "documentation");
            Element docElement = (docList.getLength() > 0)
                    ? (Element) docList.item(0)
                    : (Element) annotation.appendChild(doc.createElementNS(xsdNs, "xs:documentation"));
            docElement.setTextContent(documentation.trim());
        }

        // 6. Update javadoc in appinfo elements
        if (javadoc != null && !javadoc.trim().isEmpty()) {
            // Remove existing javadoc appinfo elements (those with source attribute containing @)
            NodeList appinfoList = annotation.getElementsByTagNameNS(xsdNs, "appinfo");
            for (int i = appinfoList.getLength() - 1; i >= 0; i--) {
                Element appinfoEl = (Element) appinfoList.item(i);
                String source = appinfoEl.getAttribute("source");
                if (source.startsWith("@")) {
                    annotation.removeChild(appinfoEl);
                }
            }

            // Add new javadoc appinfo elements
            String[] javadocLines = javadoc.trim().split("\n");
            for (String line : javadocLines) {
                if (!line.trim().isEmpty()) {
                    Element appinfo = doc.createElementNS(xsdNs, "xs:appinfo");
                    appinfo.setAttribute("source", line.trim());
                    annotation.appendChild(appinfo);
                }
            }
        }

        // 7. Save the document
        writeDocumentToFile(doc, xsdFile);
    }

    @Override
    public void updateExampleValues(File xsdFile, String elementXpath, List<String> exampleValues) throws Exception {
        // 1. Parse the document
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xsdFile);
        Element root = doc.getDocumentElement();

        // 2. Define namespaces
        final String xsdNs = "http://www.w3.org/2001/XMLSchema";
        final String altovaNs = "http://www.altova.com";
        final String altovaPrefix = "altova";

        // 3. Find the target element using a robust XPath resolution logic
        if (elementXpath == null || !elementXpath.startsWith("/")) {
            throw new IllegalArgumentException("Invalid XPath provided (must start with '/'): " + elementXpath);
        }

        String[] parts = elementXpath.substring(1).split("/");
        if (parts.length == 0 || parts[0].isEmpty()) {
            throw new IllegalArgumentException("Invalid XPath provided (empty): " + elementXpath);
        }

        // New, robust XPath resolution logic that can follow type references.
        XPath xpath = XPathFactory.newInstance().newXPath();
        Node contextNode = doc; // Start from the document root

        for (String part : parts) {
            if (part.isEmpty()) continue;

            String query = getQuery(part);
            Node foundNode = (Node) xpath.compile(query).evaluate(contextNode, XPathConstants.NODE);

            // If not found, it might be in a referenced type.
            if (foundNode == null && contextNode.getNodeType() == Node.ELEMENT_NODE) {
                Element contextElement = (Element) contextNode;
                String typeAttr = contextElement.getAttribute("type");
                if (!typeAttr.isEmpty()) {
                    // We have a type reference, e.g., "tns:PurchaseOrderType"
                    String typeName = typeAttr.contains(":") ? typeAttr.split(":")[1] : typeAttr;

                    // Find the type definition anywhere in the document.
                    String typeQuery = "//*[local-name()='complexType' or local-name()='simpleType'][@name='" + typeName + "']";
                    Node typeDefinitionNode = (Node) xpath.compile(typeQuery).evaluate(doc, XPathConstants.NODE);

                    if (typeDefinitionNode != null) {
                        // Search for our part inside this type definition.
                        foundNode = (Node) xpath.compile(query).evaluate(typeDefinitionNode, XPathConstants.NODE);
                    }
                }
            }

            if (foundNode == null) {
                throw new IllegalArgumentException("Could not resolve path segment: '" + part + "' in XPath: " + elementXpath);
            }
            contextNode = foundNode;
        }

        Node targetNode = contextNode;
        if (targetNode.getNodeType() != Node.ELEMENT_NODE) {
            throw new IllegalArgumentException("Could not find element for XPath: " + elementXpath);
        }
        Element targetElement = (Element) targetNode;

        // 4. Find or create xs:annotation
        NodeList annotationList = targetElement.getElementsByTagNameNS(xsdNs, "annotation");
        Element annotation = (annotationList.getLength() > 0)
                ? (Element) annotationList.item(0)
                : (Element) targetElement.insertBefore(doc.createElementNS(xsdNs, "xsd:annotation"), targetElement.getFirstChild());

        // 5. Find or create xs:appinfo
        NodeList appInfoList = annotation.getElementsByTagNameNS(xsdNs, "appinfo");
        Element appinfo = (appInfoList.getLength() > 0)
                ? (Element) appInfoList.item(0)
                : (Element) annotation.appendChild(doc.createElementNS(xsdNs, "xsd:appinfo"));

        // 6. Remove existing exampleValues
        NodeList existingExamplesList = appinfo.getElementsByTagNameNS(altovaNs, "exampleValues");
        for (int i = existingExamplesList.getLength() - 1; i >= 0; i--) {
            appinfo.removeChild(existingExamplesList.item(i));
        }

        // 7. Add new exampleValues if the list is not empty
        if (exampleValues != null && !exampleValues.isEmpty()) {
            if (!root.hasAttribute("xmlns:" + altovaPrefix)) {
                root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:" + altovaPrefix, altovaNs);
            }
            Element exampleValuesContainer = doc.createElementNS(altovaNs, altovaPrefix + ":exampleValues");
            for (String value : exampleValues) {
                Element exampleElement = doc.createElementNS(altovaNs, altovaPrefix + ":example");
                exampleElement.setAttribute("value", value);
                exampleValuesContainer.appendChild(exampleElement);
            }
            appinfo.appendChild(exampleValuesContainer);
        }

        // 8. Write back to file
        writeDocumentToFile(doc, xsdFile);
    }

    @Override
    public XsdDocInfo getElementDocInfo(File xsdFile, String elementXpath) throws Exception {
        // 1. Parse the document
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xsdFile);

        // 2. Define namespaces
        final String xsdNs = "http://www.w3.org/2001/XMLSchema";

        // 3. Find the target element using the same XPath resolution logic
        if (elementXpath == null || !elementXpath.startsWith("/")) {
            throw new IllegalArgumentException("Invalid XPath provided (must start with '/'): " + elementXpath);
        }

        String[] parts = elementXpath.substring(1).split("/");
        if (parts.length == 0 || parts[0].isEmpty()) {
            throw new IllegalArgumentException("Invalid XPath provided (empty): " + elementXpath);
        }

        XPath xpath = XPathFactory.newInstance().newXPath();
        Node contextNode = doc;

        for (String part : parts) {
            if (part.isEmpty()) continue;

            String query = getQuery(part);
            Node foundNode = (Node) xpath.compile(query).evaluate(contextNode, XPathConstants.NODE);

            if (foundNode == null && contextNode.getNodeType() == Node.ELEMENT_NODE) {
                Element contextElement = (Element) contextNode;
                String typeAttr = contextElement.getAttribute("type");
                if (!typeAttr.isEmpty()) {
                    String typeName = typeAttr.contains(":") ? typeAttr.split(":")[1] : typeAttr;
                    String typeQuery = "//*[local-name()='complexType' or local-name()='simpleType'][@name='" + typeName + "']";
                    Node typeDefinitionNode = (Node) xpath.compile(typeQuery).evaluate(doc, XPathConstants.NODE);

                    if (typeDefinitionNode != null) {
                        foundNode = (Node) xpath.compile(query).evaluate(typeDefinitionNode, XPathConstants.NODE);
                    }
                }
            }

            if (foundNode == null) {
                // Element not found - return null instead of throwing exception
                return null;
            }
            contextNode = foundNode;
        }

        Node targetNode = contextNode;
        if (targetNode.getNodeType() != Node.ELEMENT_NODE) {
            return null;
        }
        Element targetElement = (Element) targetNode;

        // 4. Extract XSD Doc annotations from xs:annotation/xs:appinfo
        XsdDocInfo docInfo = new XsdDocInfo();
        NodeList annotationList = targetElement.getElementsByTagNameNS(xsdNs, "annotation");

        if (annotationList.getLength() > 0) {
            Element annotation = (Element) annotationList.item(0);
            NodeList appInfoList = annotation.getElementsByTagNameNS(xsdNs, "appinfo");

            for (int i = 0; i < appInfoList.getLength(); i++) {
                Element appInfo = (Element) appInfoList.item(i);
                String source = appInfo.getAttribute("source");

                if (source != null && !source.isBlank()) {
                    if (source.startsWith("@since")) {
                        docInfo.setSince(source.substring("@since".length()).trim());
                    } else if (source.startsWith("@see")) {
                        docInfo.getSee().add(source.substring("@see".length()).trim());
                    } else if (source.startsWith("@deprecated")) {
                        docInfo.setDeprecated(source.substring("@deprecated".length()).trim());
                    }
                }
            }
        }

        return docInfo.hasData() ? docInfo : null;
    }

    private static @NotNull String getQuery(String part) {
        String elementName;
        String nodeKind;

        if (part.startsWith("@")) {
            elementName = part.substring(1);
            nodeKind = "attribute";
        } else {
            elementName = part;
            nodeKind = "element";
        }

        // First, try to find the node as a descendant of the current context. This handles inline definitions.
        return "(.//*[local-name()='" + nodeKind + "' and @name='" + elementName + "'])";
    }

    private void writeDocumentToFile(Document doc, File file) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(propertiesService.getXmlIndentSpaces()));
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(file);
        transformer.transform(source, result);
    }

    @Override
    public String getLastXsdError() {
        return lastXsdError;
    }

    /**
     * Download imported XSD schemas recursively
     *
     * @param xsdContent Content of the main XSD file
     * @param baseUrl    Base URL of the main XSD file
     * @param cacheDir   Cache directory for storing imported schemas
     */
    private void downloadImportedSchemas(String xsdContent, String baseUrl, Path cacheDir) {
        logger.debug("Analyzing XSD content for imports from base URL: {}", baseUrl);

        try {
            // Parse XSD content to find xs:import statements
            List<String> importLocations = parseXsdImports(xsdContent);

            if (importLocations.isEmpty()) {
                logger.debug("No xs:import statements found in XSD");
                return;
            }

            logger.info("Found {} import(s) in XSD, downloading...", importLocations.size());

            for (String importLocation : importLocations) {
                downloadImportedSchema(importLocation, baseUrl, cacheDir);
            }

        } catch (Exception e) {
            logger.warn("Error analyzing XSD imports: {}", e.getMessage());
        }
    }

    /**
     * Parse XSD content to extract xs:import schemaLocation attributes
     *
     * @param xsdContent Content of the XSD file
     * @return List of schema locations from xs:import statements
     */
    private List<String> parseXsdImports(String xsdContent) {
        List<String> importLocations = new ArrayList<>();

        try {
            // Parse as XML DOM
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            Document doc = builder.parse(new ByteArrayInputStream(xsdContent.getBytes()));

            // Find all xs:import elements
            NodeList importNodes = doc.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");

            for (int i = 0; i < importNodes.getLength(); i++) {
                Element importElement = (Element) importNodes.item(i);
                String schemaLocation = importElement.getAttribute("schemaLocation");

                if (schemaLocation != null && !schemaLocation.trim().isEmpty()) {
                    importLocations.add(schemaLocation.trim());
                    logger.debug("Found xs:import with schemaLocation: {}", schemaLocation);
                }
            }

        } catch (Exception e) {
            logger.warn("Error parsing XSD for imports: {}", e.getMessage());
        }

        return importLocations;
    }

    /**
     * Download a single imported schema file
     *
     * @param importLocation Schema location from xs:import statement
     * @param baseUrl        Base URL of the main XSD file
     * @param cacheDir       Cache directory for storing schemas
     */
    private void downloadImportedSchema(String importLocation, String baseUrl, Path cacheDir) {
        try {
            String resolvedUrl = resolveImportUrl(importLocation, baseUrl);

            if (resolvedUrl == null) {
                logger.debug("Skipping import (not a remote URL): {}", importLocation);
                return;
            }

            logger.debug("Resolved import URL: {} -> {}", importLocation, resolvedUrl);

            // Generate cache filename for imported schema
            String filename = extractFilenameFromUrl(resolvedUrl);
            if (filename == null || filename.isEmpty()) {
                filename = "imported_" + Math.abs(resolvedUrl.hashCode()) + ".xsd";
            }

            Path importedSchemaPath = cacheDir.resolve(filename);

            // Check if already cached
            if (Files.exists(importedSchemaPath)) {
                logger.debug("Imported schema already cached: {}", importedSchemaPath);
                return;
            }

            // Download imported schema
            logger.info("Downloading imported schema: {}", resolvedUrl);
            String importedContent = connectionService.getTextContentFromURL(new URI(resolvedUrl));

            // Validate imported schema
            if (isSchemaValid(importedContent)) {
                Files.write(importedSchemaPath, importedContent.getBytes());
                logger.info("Downloaded imported schema: {} -> {}", resolvedUrl, importedSchemaPath);

                // Recursively download imports of this imported schema
                downloadImportedSchemas(importedContent, resolvedUrl, cacheDir);

            } else {
                logger.warn("Downloaded imported schema is not valid: {}", resolvedUrl);
            }

        } catch (Exception e) {
            logger.error("Failed to download imported schema '{}': {}", importLocation, e.getMessage());
        }
    }

    /**
     * Resolve import URL relative to base URL
     *
     * @param importLocation Schema location from xs:import
     * @param baseUrl        Base URL of the main XSD
     * @return Resolved URL or null if not a remote import
     */
    private String resolveImportUrl(String importLocation, String baseUrl) {
        // If import location is already a complete URL, use it as-is
        if (importLocation.startsWith("http://") || importLocation.startsWith("https://") ||
                importLocation.startsWith("ftp://") || importLocation.startsWith("ftps://")) {
            return importLocation;
        }

        // If it's a local file reference, skip it
        if (importLocation.startsWith("file://") || importLocation.startsWith("/") ||
                importLocation.matches("[A-Za-z]:\\\\.+")) {
            return null;
        }

        // Resolve relative URL against base URL
        try {
            URI baseUri = new URI(baseUrl);
            String basePath = baseUri.getPath();

            // Remove filename from base path to get directory
            int lastSlash = basePath.lastIndexOf('/');
            if (lastSlash >= 0) {
                basePath = basePath.substring(0, lastSlash + 1);
            }

            // Construct resolved URL
            URI resolvedUri = new URI(baseUri.getScheme(), baseUri.getAuthority(),
                    basePath + importLocation, null, null);

            return resolvedUri.toString();

        } catch (URISyntaxException e) {
            logger.warn("Failed to resolve import URL '{}' against base '{}': {}",
                    importLocation, baseUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Extract filename from URL
     *
     * @param url URL string
     * @return Filename or null if not extractable
     */
    private String extractFilenameFromUrl(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (path != null && !path.isEmpty()) {
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                    return path.substring(lastSlash + 1);
                }
            }
        } catch (URISyntaxException e) {
            logger.debug("Could not extract filename from URL: {}", url);
        }
        return null;
    }
}
