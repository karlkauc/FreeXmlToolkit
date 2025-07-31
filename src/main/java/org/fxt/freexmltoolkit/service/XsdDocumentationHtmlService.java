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
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        generateSearchIndex();

        if (xsdDocumentationData.getGlobalElements().isEmpty()) {
            logger.error("No global elements found. Cannot generate root page.");
            return;
        }

        Node rootElementNode = xsdDocumentationData.getGlobalElements().getFirst();
        final String rootElementName = getAttributeValue(rootElementNode, "name");
        final XsdExtendedElement rootExtendedElement = xsdDocumentationData.getExtendedXsdElementMap().get("/" + rootElementName);

        var context = new Context();
        context.setVariable("date", LocalDate.now());
        context.setVariable("filename", Paths.get(xsdDocumentationData.getXsdFilePath()).getFileName().toString());
        context.setVariable("rootElementName", rootElementName);
        context.setVariable("version", xsdDocumentationData.getVersion());
        context.setVariable("rootElement", rootExtendedElement); // Pass the XsdExtendedElement
        context.setVariable("xsdComplexTypes", xsdDocumentationData.getGlobalComplexTypes());
        context.setVariable("xsdSimpleTypes", xsdDocumentationData.getGlobalSimpleTypes());
        context.setVariable("namespace", xsdDocumentationData.getNameSpacesAsString());
        context.setVariable("targetNamespace", xsdDocumentationData.getTargetNamespace());
        context.setVariable("rootElementLink", "details/" + rootExtendedElement.getPageName());
        context.setVariable("this", this);

        // NEU: Die Liste der globalen Elemente aus der Map aller Elemente filtern.
        // Globale Elemente sind solche auf Level 0.
        List<XsdExtendedElement> globalElements = xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> e.getLevel() == 0)
                .sorted(Comparator.comparing(XsdExtendedElement::getCounter))
                .collect(Collectors.toList());

        // NEU: Die Variablen für das Template setzen.
        context.setVariable("xsdGlobalElements", globalElements);
        context.setVariable("attributeFormDefault", xsdDocumentationData.getAttributeFormDefault());
        context.setVariable("elementFormDefault", xsdDocumentationData.getElementFormDefault());


        final var result = templateEngine.process("templateRootElement", context);
        final var outputFileName = Paths.get(outputDirectory.getPath(), "index.html").toFile().getAbsolutePath();
        logger.debug("Root File: {}", outputFileName);

        try {
            Files.writeString(Paths.get(outputFileName), result, StandardCharsets.UTF_8);
            logger.info("Written {} bytes in File '{}'", new File(outputFileName).length(), outputFileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void generateComplexTypePages() {
        logger.debug("Generating Complex Type Pages...");
        for (var complexTypeNode : xsdDocumentationData.getGlobalComplexTypes()) {
            generateSingleComplexTypePage(complexTypeNode);
        }
    }

    void generateComplexTypePagesInParallel() {
        logger.debug("Generating Complex Type Pages in parallel...");
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (final var complexTypeNode : xsdDocumentationData.getGlobalComplexTypes()) {
                executor.submit(() -> generateSingleComplexTypePage(complexTypeNode));
            }
        }
    }

    private void generateSingleComplexTypePage(Node complexTypeNode) {
        try {
            String complexTypeName = getAttributeValue(complexTypeNode, "name");
            if (complexTypeName == null || complexTypeName.isEmpty()) {
                logger.warn("Skipping complex type page for a type without a name.");
                return;
            }

            var context = new Context();
            context.setVariable("complexTypeNode", complexTypeNode);
            context.setVariable("this", this);

            // Find elements that use this type
            var usedInElements = xsdDocumentationData.getTypeUsageMap()
                    .getOrDefault(complexTypeName, Collections.emptyList());
            context.setVariable("usedInElements", usedInElements);

            // Find child elements from a representative element
            List<XsdExtendedElement> childElements = new ArrayList<>();
            if (!usedInElements.isEmpty()) {
                XsdExtendedElement representativeElement = usedInElements.getFirst();
                if (representativeElement != null && representativeElement.hasChildren()) {
                    representativeElement.getChildren().stream()
                            .map(xpath -> xsdDocumentationData.getExtendedXsdElementMap().get(xpath))
                            .filter(Objects::nonNull)
                            .forEach(childElements::add);
                }
            }
            context.setVariable("childElements", childElements);

            final var result = templateEngine.process("complexTypes/templateComplexType", context);
            final var outputFilePath = Paths.get(outputDirectory.getPath(), "complexTypes", complexTypeName + ".html");

            Files.writeString(outputFilePath, result, StandardCharsets.UTF_8);
            logger.info("Written Complex Type Page: {}", outputFilePath.getFileName());

        } catch (Exception e) {
            logger.error("ERROR in creating complex Type File for node '{}': {}", getAttributeValue(complexTypeNode, "name"), e.getMessage(), e);
        }
    }

    void generateSimpleTypePages() {
        logger.debug("Generating Simple Type Pages...");
        for (var simpleTypeNode : xsdDocumentationData.getGlobalSimpleTypes()) {
            generateSingleSimpleTypePage(simpleTypeNode);
        }
    }

    void generateSimpleTypePagesInParallel() {
        logger.debug("Generating Simple Type Pages in parallel...");
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (final var simpleTypeNode : xsdDocumentationData.getGlobalSimpleTypes()) {
                executor.submit(() -> generateSingleSimpleTypePage(simpleTypeNode));
            }
        }
    }

    private void generateSingleSimpleTypePage(Node simpleTypeNode) {
        try {
            String typeName = getAttributeValue(simpleTypeNode, "name");
            if (typeName == null || typeName.isEmpty()) {
                logger.warn("Skipping simple type page for a type without a name.");
                return;
            }

            var context = new Context();
            context.setVariable("simpleTypeNode", simpleTypeNode);
            context.setVariable("this", this);

            List<XsdExtendedElement> usedInElements = xsdDocumentationData.getTypeUsageMap()
                    .getOrDefault(typeName, Collections.emptyList());
            context.setVariable("usedInElements", usedInElements);

            final var result = templateEngine.process("simpleTypes/templateSimpleType", context);
            final var outputFilePath = Paths.get(outputDirectory.getPath(), "simpleTypes", typeName + ".html");

            Files.writeString(outputFilePath, result, StandardCharsets.UTF_8);
            logger.info("Written Simple Type Page: {}", outputFilePath.getFileName());

        } catch (Exception e) {
            logger.error("ERROR in creating simple Type File for node '{}': {}", getAttributeValue(simpleTypeNode, "name"), e.getMessage(), e);
        }
    }

    public void generateDetailPages() {
        logger.debug("Generating detail pages for all elements...");
        // xsdDocumentationImageService = new XsdDocumentationImageService(xsdDocumentationData.getExtendedXsdElementMap());
        xsdDocumentationData.getExtendedXsdElementMap().values().forEach(this::generateDetailPage);
        logger.debug("Finished generating detail pages.");
    }

    public void generateDetailsPagesInParallel() {
        logger.debug("Generating detail pages for all elements (in parallel)...");
        // xsdDocumentationImageService = new XsdDocumentationImageService(xsdDocumentationData.getExtendedXsdElementMap());
        xsdDocumentationData.getExtendedXsdElementMap().values().parallelStream().forEach(this::generateDetailPage);
        logger.debug("Finished generating detail pages (in parallel).");
    }

    private void generateDetailPage(XsdExtendedElement element) {
        try {
            final Context context = new Context();
            context.setVariable("this", this); // Wichtig, damit this.* im Template funktioniert
            context.setVariable("element", element);

            // Die fehlenden Variablen aus dem 'element'-Objekt auslesen und an das Template übergeben
            context.setVariable("documentation", element.getLanguageDocumentation());
            context.setVariable("sampleData", element.getDisplaySampleData());
            context.setVariable("appInfos", element.getGenericAppInfos());
            context.setVariable("code", element.getSourceCode());

            // Bestehende Variablen (aus anderen Templates abgeleitet)
            context.setVariable("diagramType", xsdDocService.imageOutputMethod.name());
            context.setVariable("diagramContent", generateDiagram(element));
            context.setVariable("breadCrumbs", generateBreadcrumbs(element));
            context.setVariable("namespace", formatNamespaces(xsdDocumentationData.getNamespaces()));

            final String html = templateEngine.process("details/templateDetail", context);
            final File outputFile = new File(outputDirectory + "/details", element.getPageName());
            Files.writeString(outputFile.toPath(), html, StandardCharsets.UTF_8);

        } catch (IOException e) {
            logger.error("Failed to generate detail page for element: {}", element.getCurrentXpath(), e);
            // Optional: re-throw as a runtime exception to stop the build
            // throw new RuntimeException("Failed to generate detail page for " + element.getCurrentXpath(), e);
        }
    }

    /**
     * Generiert das Diagramm für ein gegebenes Element.
     * Je nach Konfiguration wird entweder ein SVG-String oder der Pfad zu einer PNG-Datei zurückgegeben.
     *
     * @param element Das Element, für das das Diagramm erstellt werden soll.
     * @return Der SVG-Inhalt oder der relative Pfad zur PNG-Datei.
     */
    private String generateDiagram(XsdExtendedElement element) {
        if (xsdDocumentationImageService == null) {
            logger.warn("XsdDocumentationImageService is not initialized. Cannot generate diagram.");
            return "<!-- Image service not available -->";
        }
        try {
            if (xsdDocService.imageOutputMethod == XsdDocumentationService.ImageOutputMethod.SVG) {
                // Gibt den rohen SVG-String direkt zurück
                return xsdDocumentationImageService.generateSvgString(element);
            } else {
                // Generiert eine PNG-Datei und gibt den relativen Pfad zurück
                String relativePath = ASSETS_PATH + "/" + element.getPageName().replace(".html", ".png");
                File outputFile = new File(outputDirectory, relativePath);
                xsdDocumentationImageService.generateImage(element, outputFile);
                return relativePath; // z.B. "assets/MyElement_hash.png"
            }
        } catch (Exception e) {
            logger.error("Could not generate diagram for element '{}'", element.getCurrentXpath(), e);
            return "<!-- Error generating diagram -->";
        }
    }


    /**
     * Generiert die Breadcrumb-Navigation für ein Element.
     *
     * @param element Das aktuelle Element.
     * @return Ein HTML-String, der die Breadcrumb-Navigation darstellt.
     */
    private String generateBreadcrumbs(XsdExtendedElement element) {
        if (element == null) {
            return "";
        }
        LinkedList<String> crumbs = new LinkedList<>();
        XsdExtendedElement current = element;

        // Traverse up the hierarchy from the current element to the root
        while (current != null) {
            String link;
            // The current page's element should not be a link
            if (current == element) {
                link = "<span class='font-medium text-slate-500'>" + escapeHtml(current.getElementName()) + "</span>";
            } else {
                // The link needs to be relative from the 'details' subdirectory
                link = "<a href='" + getPageName(current.getCurrentXpath()) + "' class='text-sky-600 hover:underline'>" + escapeHtml(current.getElementName()) + "</a>";
            }
            crumbs.addFirst(link);
            current = (current.getParentXpath() != null) ? xsdDocumentationData.getExtendedXsdElementMap().get(current.getParentXpath()) : null;
        }

        // Add a "Home" link at the beginning, pointing to the root index.html
        crumbs.addFirst("<a href='../index.html' class='text-sky-600 hover:underline'>Schema</a>");

        return String.join(" <span class='mx-1 text-slate-400'>/</span> ", crumbs);
    }


    /**
     * Formatiert die Liste der Namespaces in einen lesbaren HTML-String.
     *
     * @param namespaces Eine Map von Namespace-Präfixen zu URIs.
     * @return Ein formatierter HTML-String.
     */
    private String formatNamespaces(Map<String, String> namespaces) {
        if (namespaces == null || namespaces.isEmpty()) {
            return "No namespaces defined.";
        }
        return namespaces.entrySet().stream()
                .map(entry -> "<b>" + escapeHtml(entry.getKey()) + "</b>" + " = \"" + escapeHtml(entry.getValue()) + "\"")
                .collect(Collectors.joining("<br />"));
    }

    void generateComplexTypesListPage() {
        final var context = new Context();
        context.setVariable("xsdComplexTypes", xsdDocumentationData.getGlobalComplexTypes());
        context.setVariable("this", this);
        final var result = templateEngine.process("complexTypes", context);
        final var outputFilePath = Paths.get(outputDirectory.getPath(), "complexTypes.html");
        try {
            Files.writeString(outputFilePath, result, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not write complex types list page", e);
        }
    }

    void generateSimpleTypesListPage() {
        final var context = new Context();
        context.setVariable("xsdSimpleTypes", xsdDocumentationData.getGlobalSimpleTypes());
        context.setVariable("this", this);
        final var result = templateEngine.process("simpleTypes", context);
        final var outputFilePath = Paths.get(outputDirectory.getPath(), "simpleTypes.html");
        try {
            Files.writeString(outputFilePath, result, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not write simple types list page", e);
        }
    }

    void generateDataDictionaryPage() {
        final var context = new Context();
        List<XsdExtendedElement> allElements = new ArrayList<>(xsdDocumentationData.getExtendedXsdElementMap().values());
        allElements.sort(Comparator.comparing(XsdExtendedElement::getCounter));
        context.setVariable("allElements", allElements);
        context.setVariable("this", this);
        final var result = templateEngine.process("dataDictionary", context);
        final var outputFilePath = Paths.get(outputDirectory.getPath(), "dataDictionary.html");
        try {
            Files.writeString(outputFilePath, result, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not write data dictionary page", e);
        }
    }

    void generateSearchIndex() {
        List<Map<String, String>> searchData = new ArrayList<>();
        for (XsdExtendedElement element : xsdDocumentationData.getExtendedXsdElementMap().values()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("name", element.getElementName());
            item.put("url", "details/" + element.getPageName());
            item.put("xpath", element.getCurrentXpath());
            String docText = element.getDocumentations().stream()
                    .map(XsdExtendedElement.DocumentationInfo::content)
                    .collect(Collectors.joining(" "));
            item.put("doc", docText);
            searchData.add(item);
        }

        StringBuilder jsonBuilder = new StringBuilder("[\n");
        for (int i = 0; i < searchData.size(); i++) {
            Map<String, String> item = searchData.get(i);
            jsonBuilder.append("  {\n");
            jsonBuilder.append("    \"name\": \"").append(escapeJson(item.get("name"))).append("\",\n");
            jsonBuilder.append("    \"url\": \"").append(escapeJson(item.get("url"))).append("\",\n");
            jsonBuilder.append("    \"xpath\": \"").append(escapeJson(item.get("xpath"))).append("\",\n");
            jsonBuilder.append("    \"doc\": \"").append(escapeJson(item.get("doc"))).append("\"\n");
            jsonBuilder.append(i < searchData.size() - 1 ? "  },\n" : "  }\n");
        }
        jsonBuilder.append("]");

        final var outputFilePath = Paths.get(outputDirectory.getPath(), "search_index.json");
        try {
            Files.writeString(outputFilePath, jsonBuilder.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not write search index file", e);
        }
    }

    // ... [copyResources, deleteDirectory, etc. remain the same] ...
    void copyResources() {
        try {
            // Use a more robust deletion method
            if (outputDirectory.exists()) {
                deleteDirectory(outputDirectory);
            }

            Files.createDirectories(outputDirectory.toPath());
            Files.createDirectories(Paths.get(outputDirectory.getPath(), ASSETS_PATH));
            Files.createDirectories(Paths.get(outputDirectory.getPath(), "details"));
            Files.createDirectories(Paths.get(outputDirectory.getPath(), "complexTypes"));
            Files.createDirectories(Paths.get(outputDirectory.getPath(), "simpleTypes"));

            copyAssets("/xsdDocumentation/assets/prism.js", outputDirectory);
            copyAssets("/xsdDocumentation/assets/prism.css", outputDirectory);
            copyAssets("/xsdDocumentation/assets/freexmltoolkit-docs.css", outputDirectory);
            copyAssets("/xsdDocumentation/assets/search.js", outputDirectory);
            copyAssets("/xsdDocumentation/assets/logo.png", outputDirectory);
            copyAssets("/xsdDocumentation/assets/Roboto-Regular.ttf", outputDirectory);
            copyAssets("/xsdDocumentation/assets/Inter.ttf", outputDirectory);
            copyAssets("/xsdDocumentation/assets/Inter-Italic.ttf", outputDirectory);
        } catch (Exception e) {
            logger.error("Could not copy resources. Error: {}", e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void copyAssets(String resourcePath, File assetsDirectory) throws Exception {
        copyResource(resourcePath, assetsDirectory, ASSETS_PATH);
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


    // =================================================================================
    // Helper methods for Thymeleaf to interact with DOM Nodes
    // =================================================================================
    public String getAttributeValue(Node node, String attrName) {
        return getAttributeValue(node, attrName, null);
    }

    public String getDocumentationFromNode(Node node) {
        if (node == null) return "";

        Node annotationNode = getDirectChildElement(node, "annotation");
        if (annotationNode == null) return "";

        List<String> docStrings = new ArrayList<>();
        for (Node docNode : getDirectChildElements(annotationNode, "documentation")) {
            docStrings.add(docNode.getTextContent());
        }
        return String.join("\n", docStrings);
    }

    public String getRestrictionBase(Node simpleTypeNode) {
        Node restrictionNode = getDirectChildElement(simpleTypeNode, "restriction");
        return (restrictionNode != null) ? getAttributeValue(restrictionNode, "base") : "";
    }

    public List<Node> getRestrictionFacets(Node simpleTypeNode) {
        Node restrictionNode = getDirectChildElement(simpleTypeNode, "restriction");
        if (restrictionNode == null) return Collections.emptyList();
        List<Node> facets = new ArrayList<>();
        for (Node child = restrictionNode.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && !"annotation".equals(child.getLocalName())) {
                facets.add(child);
            }
        }
        return facets;
    }

    private Node getDirectChildElement(Node parent, String childName) {
        if (parent == null) return null;
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && childName.equals(child.getLocalName())) {
                return child;
            }
        }
        return null;
    }

    /**
     * Sammelt alle Attribute für einen gegebenen complexType, inklusive der Attribute
     * aus Basis-Typen (Vererbung) und referenzierten Attributgruppen.
     *
     * @param complexTypeNode Der DOM-Knoten des complexType.
     * @return Eine Liste von DOM-Knoten, die die Attribute repräsentieren.
     */
    public List<Node> getAttributes(Node complexTypeNode) {
        if (complexTypeNode == null) {
            return Collections.emptyList();
        }

        List<Node> attributes = new ArrayList<>();
        Set<String> processedAttributeNames = new HashSet<>();

        // Iterative Verarbeitung der Vererbungshierarchie mit einem Stack
        Deque<Node> nodesToProcess = new ArrayDeque<>();
        nodesToProcess.push(complexTypeNode);

        while (!nodesToProcess.isEmpty()) {
            Node currentNode = nodesToProcess.pop();
            Node attributeContainer = currentNode;
            String baseTypeName = null;

            // Prüfen, ob es sich um eine Erweiterung handelt (<complexContent> oder <simpleContent>)
            Node complexContent = getDirectChildElement(currentNode, "complexContent");
            if (complexContent != null) {
                Node extension = getDirectChildElement(complexContent, "extension");
                if (extension != null) {
                    attributeContainer = extension;
                    baseTypeName = getAttributeValue(extension, "base");
                }
            } else {
                Node simpleContent = getDirectChildElement(currentNode, "simpleContent");
                if (simpleContent != null) {
                    Node extension = getDirectChildElement(simpleContent, "extension");
                    if (extension != null) {
                        attributeContainer = extension;
                        baseTypeName = getAttributeValue(extension, "base");
                    }
                }
            }

            // Attribute aus dem aktuellen Container (Typ oder Extension) sammeln
            processAttributesInContainer(attributeContainer, attributes, processedAttributeNames);

            // Basistyp zur weiteren Verarbeitung auf den Stack legen
            if (baseTypeName != null) {
                Node baseTypeNode = xsdDocService.findTypeNodeByName(baseTypeName);
                if (baseTypeNode != null) {
                    nodesToProcess.push(baseTypeNode);
                }
            }
        }

        // Attribute in der Vererbungsreihenfolge (Basis zuerst) anzeigen
        Collections.reverse(attributes);
        return attributes;
    }

    /**
     * Eine Hilfsmethode, die Attribute und Attributgruppen innerhalb eines Knotens verarbeitet.
     */
    private void processAttributesInContainer(Node containerNode, List<Node> attributes, Set<String> processedAttributeNames) {
        // Direkte Attribute
        for (Node attrNode : getDirectChildElements(containerNode, "attribute")) {
            String attrName = getAttributeValue(attrNode, "name");
            if (attrName != null && processedAttributeNames.add(attrName)) {
                attributes.add(attrNode);
            }
        }
        // Referenzierte Attributgruppen
        for (Node attrGroupRefNode : getDirectChildElements(containerNode, "attributeGroup")) {
            String ref = getAttributeValue(attrGroupRefNode, "ref");
            if (ref != null) {
                Node attributeGroupNode = xsdDocService.findReferencedNode("attributeGroup", ref);
                if (attributeGroupNode != null) {
                    processAttributesInContainer(attributeGroupNode, attributes, processedAttributeNames);
                }
            }
        }
    }

    public List<Node> getDirectChildElements(Node parent, String childName) {
        if (parent == null) return Collections.emptyList();
        List<Node> children = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && childName.equals(child.getLocalName())) {
                children.add(child);
            }
        }
        return children;
    }

    /**
     * Ermittelt die Kardinalität eines Elements (z.B. "0..1", "1..*", "1").
     *
     * @param element Das XsdExtendedElement.
     * @return Ein String, der die Kardinalität repräsentiert.
     */
    public String getCardinality(XsdExtendedElement element) {
        if (element == null || element.getCurrentNode() == null) {
            return "1"; // Default
        }

        Node node = element.getCurrentNode();
        // Für Attribute wird die Kardinalität durch "use" bestimmt
        if ("attribute".equals(node.getLocalName())) {
            String use = getAttributeValue(node, "use", "optional");
            return "required".equals(use) ? "1" : "0..1";
        }

        // Für Elemente wird minOccurs/maxOccurs verwendet
        String minOccurs = getAttributeValue(node, "minOccurs", "1");
        String maxOccurs = getAttributeValue(node, "maxOccurs", "1");

        if (minOccurs.equals(maxOccurs)) {
            return minOccurs;
        }
        if ("unbounded".equals(maxOccurs)) {
            return minOccurs + "..*";
        }
        return minOccurs + ".." + maxOccurs;
    }

    /**
     * Ruft den Datentyp eines Kind-Elements anhand seines XPath ab.
     *
     * @param childXpath Der XPath des Kind-Elements.
     * @return Der Typname oder ein leerer String.
     */
    public String getChildType(String childXpath) {
        XsdExtendedElement child = xsdDocService.xsdDocumentationData.getExtendedXsdElementMap().get(childXpath);
        return (child != null) ? child.getElementType() : "";
    }

    /**
     * Ruft die Beispieldaten für ein Kind-Element anhand seines XPath ab.
     *
     * @param childXpath Der XPath des Kind-Elements.
     * @return Die Beispieldaten oder ein leerer String.
     */
    public String getChildSampleData(String childXpath) {
        XsdExtendedElement child = xsdDocService.xsdDocumentationData.getExtendedXsdElementMap().get(childXpath);
        return (child != null) ? child.getSampleData() : "";
    }

    // Fügen Sie auch diese private Hilfsmethode hinzu, falls sie nicht schon existiert
    public String getAttributeValue(Node node, String attrName, String defaultValue) {
        if (node == null || node.getAttributes() == null) return defaultValue;
        Node attrNode = node.getAttributes().getNamedItem(attrName);
        return (attrNode != null) ? attrNode.getNodeValue() : defaultValue;
    }

    /**
     * Prüft, ob der Typ eines Attributs ein benutzerdefinierter SimpleType ist,
     * für den eine Detailseite existiert.
     *
     * @param attrNode Der Attribut-Knoten.
     * @return true, wenn der Typ verlinkbar ist.
     */
    public boolean isAttributeTypeLinkable(Node attrNode) {
        if (attrNode == null) {
            return false;
        }
        String typeName = getAttributeValue(attrNode, "type");
        if (typeName == null || typeName.isEmpty() || typeName.startsWith("xs:") || typeName.startsWith("xsd:")) {
            return false;
        }
        // Prüft, ob der Typ in der Liste der globalen (Simple-)Typen bekannt ist.
        return xsdDocService.findTypeNodeByName(typeName) != null;
    }

    /**
     * Generiert den relativen Pfad zur Detailseite des Attribut-Typs.
     *
     * @param attrNode Der Attribut-Knoten.
     * @return Der Pfad zur HTML-Seite, z.B. "../simpleTypes/MySimpleType.html".
     */
    public String getAttributeTypePageName(Node attrNode) {
        if (attrNode == null) {
            return "";
        }
        String typeName = getAttributeValue(attrNode, "type");
        if (typeName == null || typeName.isEmpty()) {
            return "";
        }
        String cleanTypeName = xsdDocService.stripNamespace(typeName);
        return "../simpleTypes/" + cleanTypeName + ".html";
    }

    // =================================================================================
    // Helper methods for linking and data retrieval from templates
    // =================================================================================

    public String getPageName(String xpath) {
        XsdExtendedElement element = xsdDocumentationData.getExtendedXsdElementMap().get(xpath);
        return (element != null) ? element.getPageName() : "#";
    }


    public String getChildDocumentation(String xpath) {
        XsdExtendedElement element = xsdDocumentationData.getExtendedXsdElementMap().get(xpath);
        return (element != null) ? element.getDocumentationAsHtml() : "";
    }

    public boolean isChildTypeLinkable(String childXPath) {
        XsdExtendedElement childElement = xsdDocumentationData.getExtendedXsdElementMap().get(childXPath);
        if (childElement != null && childElement.getElementType() != null) {
            return !childElement.getElementType().startsWith("xs:");
        }
        return false;
    }

    public String getChildTypePageName(String childXPath) {
        XsdExtendedElement childElement = xsdDocumentationData.getExtendedXsdElementMap().get(childXPath);
        if (childElement == null || childElement.getElementType() == null) {
            return "#";
        }

        String typeName = childElement.getElementType();
        boolean isSimpleType = xsdDocumentationData.getGlobalSimpleTypes().stream()
                .anyMatch(st -> typeName.equals(getAttributeValue(st, "name")));
        if (isSimpleType) {
            return "../simpleTypes/" + typeName + ".html";
        }

        boolean isComplexType = xsdDocumentationData.getGlobalComplexTypes().stream()
                .anyMatch(ct -> typeName.equals(getAttributeValue(ct, "name")));
        if (isComplexType) {
            return "../complexTypes/" + typeName + ".html";
        }

        return "#";
    }
    public String parseJavadocLinks(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        // Regex to find {@link SomeElementName}
        Pattern linkPattern = Pattern.compile("\\{@link\\s+([^}]+)\\}");
        Matcher matcher = linkPattern.matcher(content);

        // Replace each found link with a proper HTML <a> tag
        return matcher.replaceAll(matchResult -> {
            String targetName = matchResult.group(1).trim();

            // Find the element by its name. This is a simple lookup.
            // For schemas with duplicate names, this might link to the first one found.
            Optional<XsdExtendedElement> targetElement = xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                    .filter(e -> targetName.equals(e.getElementName()))
                    .findFirst();

            if (targetElement.isPresent()) {
                // If found, create a link to its detail page
                String url = "details/" + targetElement.get().getPageName();
                return "<a href='" + url + "'><code>" + escapeHtml(targetName) + "</code></a>";
            } else {
                // If the link target is not found, just return the name as plain code text.
                return "<code>" + escapeHtml(targetName) + "</code>";
            }
        });
    }

    public String getBreadCrumbs(XsdExtendedElement element) {
        if (element == null) {
            return "";
        }
        LinkedList<String> crumbs = new LinkedList<>();
        XsdExtendedElement current = element;

        // Traverse up the hierarchy from the current element to the root
        while (current != null) {
            String link;
            // The current page's element should not be a link
            if (current == element) {
                link = "<span class='text-slate-500'>" + escapeHtml(current.getElementName()) + "</span>";
            } else {
                link = "<a href='" + getPageName(current.getCurrentXpath()) + "' class='text-sky-600 hover:underline'>" + escapeHtml(current.getElementName()) + "</a>";
            }
            crumbs.addFirst(link);
            current = (current.getParentXpath() != null) ? xsdDocumentationData.getExtendedXsdElementMap().get(current.getParentXpath()) : null;
        }

        // Add a "Home" link at the beginning
        crumbs.addFirst("<a href='../index.html' class='text-sky-600 hover:underline'>Schema</a>");

        return String.join(" <span class='text-slate-400'>/</span> ", crumbs);
    }

    /**
     * Ruft die Dokumentation für den Typ eines Elements ab, das durch seinen XPath identifiziert wird.
     * Diese Methode ist jetzt robust und behandelt eingebaute XSD-Typen (z.B. "xs:string") korrekt,
     * indem sie für diese einen leeren String zurückgibt.
     *
     * @param xpath Der XPath des Elements.
     * @return Die Dokumentation des Element-Typs oder ein leerer String, falls keine
     * benutzerdefinierte Dokumentation gefunden wurde oder der Typ ein eingebauter ist.
     */
    public String getTypeDocumentation(String xpath) {
        if (xpath == null || xpath.isEmpty()) {
            return "";
        }

        // 1. Das Element anhand seines XPath finden.
        XsdExtendedElement element = xsdDocumentationData.getExtendedXsdElementMap().get(xpath);
        if (element == null) {
            return ""; // Element nicht im Service gefunden.
        }

        // 2. Den Typnamen des Elements abrufen.
        String typeName = element.getElementType();
        if (typeName == null || typeName.isEmpty()) {
            return ""; // Element hat keinen definierten Typ.
        }

        // 3. WICHTIG: Eingebaute XSD-Typen ignorieren, da sie keine lokale Dokumentation haben.
        if (typeName.startsWith("xs:") || typeName.startsWith("xsd:")) {
            return ""; // Dies verhindert die NullPointerException.
        }

        // 4. Den Definitions-Knoten für den benutzerdefinierten Typ finden.
        // KORREKTUR: xsdDocService statt xsdDocumentationService verwenden
        Node typeNode = xsdDocService.findTypeNodeByName(typeName);
        if (typeNode == null) {
            // Typ wurde nicht in der Schema-Map gefunden.
            return "";
        }

        // 5. Die Dokumentation vom gefundenen Typ-Knoten sicher abrufen.
        return getDocumentationFromNode(typeNode);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}