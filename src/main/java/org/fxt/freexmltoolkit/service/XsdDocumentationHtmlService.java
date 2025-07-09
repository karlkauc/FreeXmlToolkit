/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.ExtendedXsdElement;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.w3c.dom.Node;
import org.xmlet.xsdparser.xsdelements.XsdAnnotation;
import org.xmlet.xsdparser.xsdelements.XsdAnnotationChildren;
import org.xmlet.xsdparser.xsdelements.XsdComplexType;
import org.xmlet.xsdparser.xsdelements.XsdSimpleType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class XsdDocumentationHtmlService {

    public static final String ASSETS_PATH = "assets";
    private static final Logger logger = LogManager.getLogger(XsdDocumentationHtmlService.class);

    ClassLoaderTemplateResolver resolver;
    TemplateEngine templateEngine;

    private File outputDirectory;

    XsdDocumentationData xsdDocumentationData;
    XsdDocumentationImageService xsdDocumentationImageService;

    private XsdDocumentationService xsdDocService;

    public XsdDocumentationHtmlService() {
        resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setPrefix("xsdDocumentation/");
        resolver.setSuffix(".html");

        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public void setXsdDocumentationService(XsdDocumentationService xsdDocService) {
        this.xsdDocService = xsdDocService;
    }

    public void setDocumentationData(XsdDocumentationData xsdDocumentationData) {
        this.xsdDocumentationData = xsdDocumentationData;
    }

    void generateRootPage() {
        final var rootElementName = xsdDocumentationData.getElements().getFirst().getName();

        var context = new Context();
        context.setVariable("date", LocalDate.now());
        context.setVariable("filename", Paths.get(xsdDocumentationData.getXsdFilePath()).getFileName().toString());
        context.setVariable("rootElementName", rootElementName);
        context.setVariable("version", xsdDocumentationData.getVersion());
        context.setVariable("rootElement", xsdDocumentationData.getXmlSchema().getFirst());
        context.setVariable("xsdElements", xsdDocumentationData.getElements().getFirst());
        context.setVariable("xsdComplexTypes", xsdDocumentationData.getXsdComplexTypes());
        context.setVariable("xsdSimpleTypes", xsdDocumentationData.getXsdSimpleTypes());
        context.setVariable("namespace", xsdDocumentationData.getNameSpacesAsString());
        context.setVariable("targetNamespace", xsdDocumentationData.getXmlSchema().getFirst().getTargetNamespace());
        context.setVariable("rootElementLink", "details/" + xsdDocumentationData.getExtendedXsdElementMap().get("/" + rootElementName).getPageName());

        final var result = templateEngine.process("templateRootElement", context);
        final var outputFileName = Paths.get(outputDirectory.getPath(), "index.html").toFile().getAbsolutePath();
        logger.debug("Root File: {}", outputFileName);

        try {
            Files.write(Paths.get(outputFileName), result.getBytes());
            logger.info("Written {} bytes in File '{}'", new File(outputFileName).length(), outputFileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void generateComplexTypePages() {
        logger.debug("Complex Types");

        for (var complexType : xsdDocumentationData.getXsdComplexTypes()) {
            try {
                var context = new Context();
                context.setVariable("complexType", complexType);

                if (complexType.getAnnotation() != null && complexType.getAnnotation().getDocumentations() != null) {
                    context.setVariable("documentations", complexType.getAnnotation().getDocumentations());
                }

                // NEU: Finde alle Elemente, deren Name dem des Complex Type entspricht.
                final String typeName = complexType.getName();
                if (typeName != null && !typeName.isEmpty()) {
                    // Filtere die Liste aller ExtendedXsdElement-Objekte.
                    var currentExtendedElement = xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                            .filter(element -> typeName.equals(element.getElementName()))
                            .collect(Collectors.toList());

                    // Füge die Liste der gefundenen Elemente zum Kontext hinzu.
                    // Diese kann im Template z.B. unter "Verwendet in:" angezeigt werden.
                    context.setVariable("typeUsages", currentExtendedElement);
                    logger.debug("Found {} usage(s) for complex type '{}'", currentExtendedElement.size(), typeName);

                    var usedInElements = xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                            .filter(element -> typeName.equals(element.getElementType()))
                            .sorted(Comparator.comparing(ExtendedXsdElement::getCurrentXpath))
                            .collect(Collectors.toList());

                    context.setVariable("usedInElements", usedInElements);
                    logger.debug("Found {} usage(s) for complex type '{}'", usedInElements.size(), typeName);
                }


                final var result = templateEngine.process("complexTypes/templateComplexType", context);
                var fileName = complexType.getRawName();
                if (fileName.isEmpty()) {
                    fileName = complexType.getName();
                }

                final var outputFilePath = Paths.get(outputDirectory.getPath(), "complexTypes", fileName + ".html");
                logger.debug("Complex Type File: {}", outputFilePath.toFile().getAbsolutePath());

                try {
                    Files.write(outputFilePath, result.getBytes());
                    logger.info("Written {} bytes in File '{}'", new File(outputFilePath.toFile().getAbsolutePath()).length(), outputFilePath.toFile().getAbsolutePath());
                } catch (IOException e) {
                    logger.error("ERROR in writing complex Type File: {}", e.getMessage());
                }
            } catch (Exception e) {
                logger.error("ERROR in creating complex Type File: {}", e.getMessage());
            }
        }
    }

    void generateComplexTypePagesInParallel() {
        logger.debug("Generating Complex Type Pages in parallel...");

        // Verwende für jede Seite einen eigenen virtuellen Thread
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (final var complexType : xsdDocumentationData.getXsdComplexTypes()) {
                executor.submit(() -> {
                    try {
                        var context = new Context();
                        context.setVariable("complexType", complexType);

                        if (complexType.getAnnotation() != null && complexType.getAnnotation().getDocumentations() != null) {
                            context.setVariable("documentations", complexType.getAnnotation().getDocumentations());
                        }

                        final String typeName = complexType.getName();
                        if (typeName != null && !typeName.isEmpty()) {
                            var usedInElements = xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                                    .filter(element -> typeName.equals(element.getElementType()))
                                    .sorted(Comparator.comparing(ExtendedXsdElement::getCurrentXpath))
                                    .collect(Collectors.toList());
                            context.setVariable("usedInElements", usedInElements);
                        }

                        final var result = templateEngine.process("complexTypes/templateComplexType", context);
                        var fileName = complexType.getRawName();
                        if (fileName == null || fileName.isEmpty()) {
                            fileName = complexType.getName();
                        }

                        final var outputFilePath = Paths.get(outputDirectory.getPath(), "complexTypes", fileName + ".html");
                        Files.write(outputFilePath, result.getBytes());
                        logger.info("Written Complex Type Page: {}", outputFilePath.getFileName());

                    } catch (Exception e) {
                        logger.error("ERROR in creating complex Type File for '{}': {}", complexType.getName(), e.getMessage(), e);
                    }
                });
            }
        } // Der Executor wird hier automatisch geschlossen und wartet auf die Fertigstellung der Tasks.
    }

    void generateDetailPages() {
        xsdDocumentationImageService = new XsdDocumentationImageService(xsdDocumentationData.getExtendedXsdElementMap());

        List<String> sortedKeys = new ArrayList<>(xsdDocumentationData.getExtendedXsdElementMap().keySet());
        Collections.sort(sortedKeys);

        for (String key : sortedKeys) {
            final var currentElement = xsdDocumentationData.getExtendedXsdElementMap().get(key);
            logger.debug("Generating single details page for {}", key);

            try {
                writeDetailsPageContent(currentElement, key, xsdDocumentationImageService);
            } catch (Exception e) {
                logger.warn("Error in thread while writing details page for element '{}': {}", key, e.getMessage());
            }
        }
    }

    void generateSimpleTypePages() {
        logger.debug("Generating Simple Type Pages");

        // Iteriere durch alle gefundenen SimpleTypes aus den XSD-Daten
        for (var simpleType : xsdDocumentationData.getXsdSimpleTypes()) {
            try {
                // Erstelle einen neuen Kontext für das Thymeleaf-Template
                var context = new Context();

                // Füge die relevanten Daten zum Kontext hinzu
                context.setVariable("simpleType", simpleType);
                context.setVariable("restriction", simpleType.getRestriction());

                // Füge die Dokumentation hinzu, falls vorhanden
                if (simpleType.getAnnotation() != null && simpleType.getAnnotation().getDocumentations() != null) {
                    context.setVariable("documentations", simpleType.getAnnotation().getDocumentations());
                }

                // NEU: Finde alle Elemente, die diesen Simple Type verwenden.
                final String typeName = simpleType.getName();
                if (typeName != null && !typeName.isEmpty()) {
                    var usedInElements = xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                            .filter(element -> typeName.equals(element.getElementType()))
                            .sorted(Comparator.comparing(ExtendedXsdElement::getCurrentXpath))
                            .collect(Collectors.toList());

                    // Füge die Liste der Verwendungen zum Kontext hinzu.
                    context.setVariable("usedInElements", usedInElements);
                    logger.debug("Found {} usage(s) for simple type '{}'", usedInElements.size(), typeName);
                }

                // Verarbeite das Template mit den gefüllten Daten
                final var result = templateEngine.process("simpleTypes/templateSimpleType", context);

                // Definiere den Ausgabepfad und den Dateinamen
                final var outputFilePath = Paths.get(outputDirectory.getPath(), "simpleTypes", simpleType.getName() + ".html");
                logger.debug("File: {}", outputFilePath.toFile().getAbsolutePath());

                // Schreibe die generierte HTML-Datei in das Ausgabeverzeichnis
                Files.write(outputFilePath, result.getBytes());
                logger.info("Written {} bytes in File '{}'", new File(outputFilePath.toFile().getAbsolutePath()).length(), outputFilePath.toFile().getAbsolutePath());

            } catch (Exception e) {
                // Logge den Fehler, aber fahre mit dem nächsten Typ fort, um den gesamten Prozess nicht zu blockieren.
                logger.error("ERROR in creating simple Type File for '{}': {}", simpleType.getName(), e.getMessage(), e);
            }
        }
    }

    void generateSimpleTypePagesInParallel() {
        logger.debug("Generating Simple Type Pages in parallel...");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (final var simpleType : xsdDocumentationData.getXsdSimpleTypes()) {
                executor.submit(() -> {
                    try {
                        var context = new Context();
                        context.setVariable("simpleType", simpleType);
                        context.setVariable("restriction", simpleType.getRestriction());

                        if (simpleType.getAnnotation() != null && simpleType.getAnnotation().getDocumentations() != null) {
                            context.setVariable("documentations", simpleType.getAnnotation().getDocumentations());
                        }

                        final String typeName = simpleType.getName();
                        if (typeName != null && !typeName.isEmpty()) {
                            var usedInElements = xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                                    .filter(element -> typeName.equals(element.getElementType()))
                                    .sorted(Comparator.comparing(ExtendedXsdElement::getCurrentXpath))
                                    .collect(Collectors.toList());
                            context.setVariable("usedInElements", usedInElements);
                        }

                        final var result = templateEngine.process("simpleTypes/templateSimpleType", context);
                        final var outputFilePath = Paths.get(outputDirectory.getPath(), "simpleTypes", simpleType.getName() + ".html");

                        Files.write(outputFilePath, result.getBytes());
                        logger.info("Written Simple Type Page: {}", outputFilePath.getFileName());

                    } catch (Exception e) {
                        logger.error("ERROR in creating simple Type File for '{}': {}", simpleType.getName(), e.getMessage(), e);
                    }
                });
            }
        }
    }

    /**
     * Generiert eine einzelne HTML-Seite, die alle gefundenen Complex Types auflistet.
     * Jeder Eintrag verlinkt auf die jeweilige Detailseite des Typs.
     */
    void generateComplexTypesListPage() {
        logger.debug("Generating Complex Types list page");

        // 1. Thymeleaf-Kontext erstellen
        final var context = new Context();

        // 2. Die Liste aller Complex Types aus den gesammelten Daten holen
        //    und dem Kontext hinzufügen, damit sie im Template verfügbar ist.
        context.setVariable("xsdComplexTypes", xsdDocumentationData.getXsdComplexTypes());

        // 3. Das Thymeleaf-Template "complexTypes.html" verarbeiten
        final var result = templateEngine.process("complexTypes", context);

        // 4. Den Ausgabepfad für die Datei definieren (z.B. output/complexTypes.html)
        final var outputFilePath = Paths.get(outputDirectory.getPath(), "complexTypes.html");
        logger.debug("Creating list page for complex types: {}", outputFilePath.toFile().getAbsolutePath());

        // 5. Die generierte HTML-Datei schreiben
        try {
            Files.write(outputFilePath, result.getBytes());
            logger.info("Written {} bytes to File '{}'", outputFilePath.toFile().length(), outputFilePath.toFile().getAbsolutePath());
        } catch (IOException e) {
            // Bei einem Fehler den Prozess abbrechen
            throw new RuntimeException("Could not write complex types list page", e);
        }
    }

    /**
     * Generiert eine einzelne HTML-Seite, die alle gefundenen Simple Types auflistet.
     */
    void generateSimpleTypesListPage() {
        logger.debug("Generating Simple Types list page");

        final var context = new Context();

        // Die Liste aller Simple Types aus den Daten holen und dem Kontext hinzufügen
        context.setVariable("xsdSimpleTypes", xsdDocumentationData.getXsdSimpleTypes());

        // Das Thymeleaf-Template "simpleTypes.html" verarbeiten
        final var result = templateEngine.process("simpleTypes", context);

        // Den Ausgabepfad für die Datei definieren (z.B. output/simpleTypes.html)
        final var outputFilePath = Paths.get(outputDirectory.getPath(), "simpleTypes.html");
        logger.debug("Creating list page for simple types: {}", outputFilePath.toFile().getAbsolutePath());

        // Die generierte HTML-Datei schreiben
        try {
            Files.write(outputFilePath, result.getBytes());
            logger.info("Written {} bytes to File '{}'", outputFilePath.toFile().length(), outputFilePath.toFile().getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Could not write simple types list page", e);
        }
    }

    void writeDetailsPageContent(ExtendedXsdElement currentElement,
                                 String currentXpath,
                                 XsdDocumentationImageService xsdDocumentationImageService) {
        var context = new Context();
        context.setVariable("element", currentElement);
        String diagramContent;
        String diagramType;

        if (xsdDocService.imageOutputMethod == XsdDocumentationService.ImageOutputMethod.SVG) {
            // Fall 1: SVG direkt als String generieren
            diagramContent = xsdDocumentationImageService.generateSvgString(currentXpath);
            diagramType = "SVG";
            logger.debug("Generating SVG diagram for {}", currentElement.getElementName());

        } else {
            // Fall 2: PNG-Bild generieren und den Pfad speichern
            // Sicherstellen, dass das "images"-Verzeichnis existiert
            File imagesDir = new File(outputDirectory, "images");
            if (!imagesDir.exists()) {
                imagesDir.mkdirs();
            }

            // Zieldatei für das Bild definieren
            File imageFile = new File(imagesDir, currentElement.getPageName().replace(".html", ".png"));

            // Bild mit der Methode aus XsdDocumentationService generieren
            xsdDocumentationImageService.generateImage(currentXpath, imageFile);

            // Im Template benötigen wir den relativen Pfad zum Bild
            diagramContent = "images/" + imageFile.getName();
            diagramType = "PNG";
            logger.debug("Generating PNG diagram for {}: {}", currentElement.getElementName(), diagramContent);
        }

        // Die Ergebnisse an das Template übergeben
        context.setVariable("diagramContent", diagramContent);
        context.setVariable("diagramType", diagramType);

        var breadCrumbs = getBreadCrumbs(currentElement);
        if (breadCrumbs != null && !breadCrumbs.isEmpty()) {
            context.setVariable("xpath", getBreadCrumbs(currentElement));
        }
        context.setVariable("code", currentElement.getSourceCode());
        context.setVariable("element", currentElement);
        if (currentElement.getSampleData() != null) {
            context.setVariable("sampleData", currentElement.getSampleData());
        }
        context.setVariable("namespace", xsdDocumentationData.getNameSpacesAsString());
        context.setVariable("this", this);

        if (currentElement.getXsdElement() != null && currentElement.getXsdElement().getType() != null) {
            context.setVariable("type", currentElement.getElementType());
        } else {
            context.setVariable("type", "NULL");
        }

        // Wir verwenden jetzt die vorverarbeiteten generischen App-Infos.
        // Das JavadocInfo-Objekt ist bereits über das "element"-Objekt verfügbar.
        if (currentElement.getGenericAppInfos() != null && !currentElement.getGenericAppInfos().isEmpty()) {
            context.setVariable("appInfos", currentElement.getGenericAppInfos());
        }

        Map<String, String> docTemp = new LinkedHashMap<>();
        if (currentElement.getLanguageDocumentation() != null && !currentElement.getLanguageDocumentation().isEmpty()) {
            docTemp = currentElement.getLanguageDocumentation();
        }
        context.setVariable("documentation", docTemp);

        currentElement.getChildren().forEach(child -> {
            try {
                if (xsdDocumentationData.getExtendedXsdElementMap().get(child) != null) {
                    logger.debug("Child: {}, Page Name: {}", child, xsdDocumentationData.getExtendedXsdElementMap().get(child).getPageName());
                } else {
                    logger.debug("Child {}, no enty found.", child);
                }
            } catch (Exception e) {
                logger.warn("Error in Children for child: {}. Message: {}", child, e.getMessage());
            }
        });

        final var result = templateEngine.process("details/templateDetail", context);
        final var outputFileName = Paths.get(outputDirectory.getPath(), "details", currentElement.getPageName()).toFile().getAbsolutePath();
        logger.debug("File: {}", outputFileName);

        try {
            Files.write(Paths.get(outputFileName), result.getBytes());
            logger.info("Written {} bytes in File '{}'", new File(outputFileName).length(), outputFileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void generateDetailsPagesInParallel() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final XsdDocumentationImageService xsdDocumentationImageService = new XsdDocumentationImageService(xsdDocumentationData.getExtendedXsdElementMap());

            for (final String key : xsdDocumentationData.getExtendedXsdElementMap().keySet()) {
                final var currentElement = xsdDocumentationData.getExtendedXsdElementMap().get(key);
                logger.debug("Submitting details page generation for {}", key);

                executor.submit(() -> {
                    try {
                        writeDetailsPageContent(currentElement, key, xsdDocumentationImageService);
                    } catch (Exception e) {
                        // Es ist eine gute Praxis, auch innerhalb des Threads zu loggen.
                        logger.warn("Error in thread while writing details page for element '{}': {}", key, e.getMessage());
                    }
                });
            }
        }
    }


    void generateDataDictionaryPage() {
        logger.debug("Generating Data Dictionary page");

        final var context = new Context();

        // Hole alle Elemente und sortiere sie nach ihrem XPath für eine konsistente Ausgabe.
        List<ExtendedXsdElement> allElements = new ArrayList<>(xsdDocumentationData.getExtendedXsdElementMap().values());
        allElements.sort(Comparator.comparing(ExtendedXsdElement::getCounter));

        context.setVariable("allElements", allElements);
        context.setVariable("this", this); // Damit wir getChildDocumentation im Template aufrufen können

        final var result = templateEngine.process("dataDictionary", context);

        final var outputFilePath = Paths.get(outputDirectory.getPath(), "dataDictionary.html");
        logger.debug("Creating data dictionary page: {}", outputFilePath.toFile().getAbsolutePath());

        try {
            Files.write(outputFilePath, result.getBytes());
            logger.info("Written {} bytes to File '{}'", outputFilePath.toFile().length(), outputFilePath.toFile().getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Could not write data dictionary page", e);
        }
    }

    /**
     * Holt die Dokumentation für einen bestimmten Typ (Simple oder Complex).
     *
     * @param xpath Der XPath des Elements, dessen Typ-Dokumentation gesucht wird.
     * @return Die gefundene Dokumentation als String, oder ein leerer String, wenn nichts gefunden wurde.
     */
    public String getTypeDocumentation(String xpath) {
        ExtendedXsdElement element = xsdDocumentationData.getExtendedXsdElementMap().get(xpath);

        // Schritt 1: Prüfen, ob das Element und sein Typ existieren und ob es kein eingebauter Typ ist.
        if (element == null || element.getElementType() == null || element.getElementType().startsWith("xs:")) {
            return "";
        }
        final String typeName = element.getElementType();

        // Schritt 2: Suche in den SimpleTypes.
        var simpleTypeDoc = xsdDocumentationData.getXsdSimpleTypes().stream()
                .filter(st -> typeName.equals(st.getName()))
                .findFirst()
                .map(XsdSimpleType::getAnnotation)
                .map(XsdAnnotation::getDocumentations)
                .map(docs -> docs.stream().map(XsdAnnotationChildren::getContent).collect(Collectors.joining(" ")))
                .orElse(null);

        if (simpleTypeDoc != null) {
            return simpleTypeDoc;
        }

        // Schritt 3: Wenn nicht in SimpleTypes gefunden, suche in den ComplexTypes.
        // Leeren String als Fallback

        return xsdDocumentationData.getXsdComplexTypes().stream()
                .filter(ct -> typeName.equals(ct.getName()))
                .findFirst()
                .map(XsdComplexType::getAnnotation)
                .map(XsdAnnotation::getDocumentations)
                .map(docs -> docs.stream().map(XsdAnnotationChildren::getContent).collect(Collectors.joining(" ")))
                .orElse("");
    }


    Map<String, String> getBreadCrumbs(ExtendedXsdElement currentElement) {
        var xpath = new StringBuilder();
        Map<String, String> breadCrumbs = new LinkedHashMap<>();

        final var t = currentElement.getCurrentXpath().split("/");
        for (String element : t) {
            if (!element.isEmpty()) {
                xpath.append("/").append(element);
                String link = "#";
                if (xsdDocumentationData.getExtendedXsdElementMap() != null && xsdDocumentationData.getExtendedXsdElementMap().get(xpath.toString()) != null) {
                    link = xsdDocumentationData.getExtendedXsdElementMap().get(xpath.toString()).getPageName();
                }
                breadCrumbs.put(element, link);
            }
        }

        return breadCrumbs;
    }

    private void copyAssets(String resourcePath, File assetsDirectory) throws Exception {
        copyResource(resourcePath, assetsDirectory, ASSETS_PATH);
    }


    void copyResources() {
        try {
            deleteDirectory(outputDirectory);

            Files.createDirectories(outputDirectory.toPath());
            Files.createDirectories(Paths.get(outputDirectory.getPath(), ASSETS_PATH));
            Files.createDirectories(Paths.get(outputDirectory.getPath(), "details"));
            Files.createDirectories(Paths.get(outputDirectory.getPath(), "complexTypes"));
            Files.createDirectories(Paths.get(outputDirectory.getPath(), "simpleTypes"));

            copyAssets("/xsdDocumentation/assets/bootstrap.bundle.min.js", outputDirectory);
            copyAssets("/xsdDocumentation/assets/prism.js", outputDirectory);
            copyAssets("/xsdDocumentation/assets/prism.css", outputDirectory);
            copyAssets("/xsdDocumentation/assets/freeXmlToolkit.css", outputDirectory);
            copyAssets("/xsdDocumentation/assets/plus.png", outputDirectory);
            copyAssets("/xsdDocumentation/assets/logo.png", outputDirectory);
            copyAssets("/xsdDocumentation/assets/Roboto-Regular.ttf", outputDirectory);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }


    public String getNodeTypeNameFromNodeType(short nodeType) {
        return switch (nodeType) {
            case Node.ELEMENT_NODE -> "Element";
            case Node.ATTRIBUTE_NODE -> "Attribute";
            case Node.TEXT_NODE -> "Text";
            case Node.CDATA_SECTION_NODE -> "CDATA Section";
            case Node.ENTITY_REFERENCE_NODE -> "Entity Reference";
            case Node.ENTITY_NODE -> "Entity";
            case Node.PROCESSING_INSTRUCTION_NODE -> "Processing Instruction";
            case Node.COMMENT_NODE -> "Comment";
            case Node.DOCUMENT_NODE -> "Document";
            case Node.DOCUMENT_TYPE_NODE -> "Document Type";
            case Node.DOCUMENT_FRAGMENT_NODE -> "Document Fragment";
            case Node.NOTATION_NODE -> "Notation";
            default -> "Unknown";
        };
    }

    private void copyResource(String resourcePath, File outputDirectory, String targetPath) throws IOException {
        Files.copy(Objects.requireNonNull(getClass().getResourceAsStream(resourcePath)), Paths.get(outputDirectory.getPath(), targetPath, new File(resourcePath).getName()), StandardCopyOption.REPLACE_EXISTING);
    }

    void deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }

    public Node getChildNodeFromXpath(String xpath) {
        try {
            return xsdDocumentationData.getExtendedXsdElementMap().get(xpath).getCurrentNode();
        } catch (Exception e) {
            logger.debug("ERROR in getting Node: {}", e.getMessage());
        }
        return null;
    }

    public String getChildType(String xpath) {
        ExtendedXsdElement element = xsdDocumentationData.getExtendedXsdElementMap().get(xpath);
        if (element != null && element.getElementType() != null) {
            return element.getElementType();
        }
        return null;
    }

    public String getChildSampleData(String xpath) {
        ExtendedXsdElement element = xsdDocumentationData.getExtendedXsdElementMap().get(xpath);
        if (element != null && element.getSampleData() != null && !element.getSampleData().isEmpty()) {
            return element.getSampleData();
        }
        return null;
    }

    public String getPageName(String xpath) {
        ExtendedXsdElement element = xsdDocumentationData.getExtendedXsdElementMap().get(xpath);
        if (element != null && element.getPageName() != null) {
            return element.getPageName();
        }
        return "#";
    }

    public String getChildDocumentation(String xpath) {
        ExtendedXsdElement element = xsdDocumentationData.getExtendedXsdElementMap().get(xpath);
        if (element != null) {
            return element.getDocumentationAsHtml();
        }
        return "";
    }

    /**
     * Prüft, ob der Typ eines Kind-Elements ein verlinkbarer, benutzerdefinierter Typ ist.
     *
     * @param childXPath Der XPath des Kind-Elements.
     * @return true, wenn der Typ kein eingebauter xs-Typ ist, sonst false.
     */
    public boolean isChildTypeLinkable(String childXPath) {
        ExtendedXsdElement childElement = xsdDocumentationData.getExtendedXsdElementMap().get(childXPath);
        if (childElement != null && childElement.getElementType() != null) {
            // Ein Typ ist verlinkbar, wenn er nicht mit dem Standard-Namespace-Präfix "xs:" beginnt.
            return !childElement.getElementType().startsWith("xs:");
        }
        return false;
    }

    /**
     * Gibt den relativen Pfad zur Detailseite des Typs eines Kind-Elements zurück.
     *
     * @param childXPath Der XPath des Kind-Elements.
     * @return Der Dateiname für die Detailseite (z.B. "../simpletypes/MySimpleType.html").
     */
    public String getChildTypePageName(String childXPath) {
        ExtendedXsdElement childElement = xsdDocumentationData.getExtendedXsdElementMap().get(childXPath);
        if (childElement == null || childElement.getElementType() == null) {
            return "#";
        }

        String typeName = childElement.getElementType();
        // Entferne ein mögliches Namespace-Präfix für den Dateinamen
        String cleanTypeName = typeName.substring(typeName.lastIndexOf(":") + 1);

        // KORREKTUR: Prüfe, ob der Typ in der Liste der globalen Simple- oder Complex-Types existiert.
        // Wir verwenden einen Stream und anyMatch, da es sich um eine Liste handelt.
        boolean isSimpleType = xsdDocumentationData.getXsdSimpleTypes().stream()
                .anyMatch(st -> typeName.equals(st.getName()));

        if (isSimpleType) {
            return "../simpletypes/" + cleanTypeName + ".html";
        }

        boolean isComplexType = xsdDocumentationData.getXsdComplexTypes().stream()
                .anyMatch(ct -> typeName.equals(ct.getName()));

        if (isComplexType) {
            return "../complexTypes/" + cleanTypeName + ".html";
        }

        // Fallback, sollte eigentlich nicht erreicht werden, wenn isChildTypeLinkable() korrekt funktioniert.
        return "#";
    }

}
