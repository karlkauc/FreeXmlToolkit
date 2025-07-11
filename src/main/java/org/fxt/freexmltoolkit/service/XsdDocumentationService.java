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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.ExtendedXsdElement;
import org.fxt.freexmltoolkit.domain.JavadocInfo;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmlet.xsdparser.core.XsdParser;
import org.xmlet.xsdparser.xsdelements.*;
import org.xmlet.xsdparser.xsdelements.elementswrapper.ReferenceBase;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class XsdDocumentationService {

    static final int MAX_ALLOWED_DEPTH = 99;
    private final static Logger logger = LogManager.getLogger(XsdDocumentationService.class);

    String xsdFilePath;
    public XsdDocumentationData xsdDocumentationData = new XsdDocumentationData();

    int counter;
    boolean parallelProcessing = false;

    public enum ImageOutputMethod {
        SVG,
        PNG
    }

    public ImageOutputMethod imageOutputMethod;
    Boolean useMarkdownRenderer = true;

    XsdParser parser;
    XmlService xmlService = XmlServiceImpl.getInstance();

    String schemaPrefix;

    XsdDocumentationHtmlService xsdDocumentationHtmlService = new XsdDocumentationHtmlService();
    private final SampleDataGenerator sampleDataGenerator = new SampleDataGenerator();

    public XsdDocumentationService() {
        imageOutputMethod = ImageOutputMethod.SVG;
    }

    public void setXsdFilePath(String xsdFilePath) {
        this.xsdFilePath = xsdFilePath;
        this.xsdDocumentationData.setXsdFilePath(xsdFilePath);
        this.schemaPrefix = getSchemaPrefix();
    }

    public void setMethod(ImageOutputMethod method) {
        this.imageOutputMethod = method;
    }

    public void setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    public void setUseMarkdownRenderer(Boolean useMarkdownRenderer) {
        this.useMarkdownRenderer = useMarkdownRenderer;
    }

    public void generateXsdDocumentation(File outputDirectory) {
        logger.debug("Bin in generateXsdDocumentation");
        processXsd(this.useMarkdownRenderer);

        xsdDocumentationHtmlService.setOutputDirectory(outputDirectory);
        xsdDocumentationHtmlService.setDocumentationData(xsdDocumentationData);
        xsdDocumentationHtmlService.setXsdDocumentationService(this);

        xsdDocumentationHtmlService.copyResources();
        xsdDocumentationHtmlService.generateRootPage();

        xsdDocumentationHtmlService.generateComplexTypesListPage();
        xsdDocumentationHtmlService.generateSimpleTypesListPage();
        xsdDocumentationHtmlService.generateDataDictionaryPage();

        if (parallelProcessing) {
            xsdDocumentationHtmlService.generateComplexTypePagesInParallel();
            xsdDocumentationHtmlService.generateSimpleTypePagesInParallel();
            xsdDocumentationHtmlService.generateDetailsPagesInParallel();
        } else {
            xsdDocumentationHtmlService.generateComplexTypePages();
            xsdDocumentationHtmlService.generateSimpleTypePages();
            xsdDocumentationHtmlService.generateDetailPages();
        }
    }

    public void processXsd(Boolean useMarkdownRenderer) {
        this.useMarkdownRenderer = useMarkdownRenderer;
        parser = new XsdParser(xsdFilePath);
        xmlService.setCurrentXmlFile(new File(xsdFilePath));

        xsdDocumentationData.setElements(parser.getResultXsdElements().collect(Collectors.toList()));
        xsdDocumentationData.setXmlSchema(parser.getResultXsdSchemas().toList());
        parser.getResultXsdSchemas().forEach(n -> xsdDocumentationData.getNamespaces().putAll(n.getNamespaces()));

        if (!xsdDocumentationData.getXmlSchema().isEmpty()) {
            XsdSchema rootSchema = xsdDocumentationData.getXmlSchema().getFirst();
            if (rootSchema.getVersion() != null) {
                xsdDocumentationData.setVersion(rootSchema.getVersion());
            }
        }

        counter = 0;

        for (XsdElement xsdElement : xsdDocumentationData.getElements()) {
            var elementName = xsdElement.getRawName();
            final Node startNode = xmlService.getNodeFromXpath("//" + this.schemaPrefix + ":element[@name='" + elementName + "']");
            getXsdAbstractElementInfo(0, xsdElement, List.of(), List.of(), startNode, null);
        }

        logger.debug("Building type usage index for faster lookups...");
        Map<String, List<ExtendedXsdElement>> typeUsageMap = xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(element -> element.getElementType() != null && !element.getElementType().isEmpty())
                .collect(Collectors.groupingBy(ExtendedXsdElement::getElementType));

        typeUsageMap.values().parallelStream()
                .forEach(list -> list.sort(Comparator.comparing(ExtendedXsdElement::getCurrentXpath)));

        xsdDocumentationData.setTypeUsageMap(typeUsageMap);
        logger.debug("Type usage index built with {} types.", typeUsageMap.size());
    }

    public void getXsdAbstractElementInfo(int level,
                                          XsdAbstractElement xsdAbstractElement,
                                          List<String> prevElementTypes,
                                          List<String> prevElementPath,
                                          Node parentNode,
                                          JavadocInfo parentJavadocInfo) {
        if (level > MAX_ALLOWED_DEPTH) {
            logger.error("Max recursion depth reached. Aborting.");
            return;
        }

        ExtendedXsdElement extendedXsdElement = new ExtendedXsdElement();
        extendedXsdElement.setCounter(counter++);
        extendedXsdElement.setUseMarkdownRenderer(useMarkdownRenderer);

        Node currentNode = null;
        String elementNameForXpath = null;
        String nodeTypeForXpath = null;

        if (xsdAbstractElement instanceof XsdElement xsdElement) {
            elementNameForXpath = xsdElement.getRawName();
            nodeTypeForXpath = "element";
        } else if (xsdAbstractElement instanceof XsdAttribute xsdAttribute) {
            elementNameForXpath = xsdAttribute.getName();
            nodeTypeForXpath = "attribute";
        } else if (xsdAbstractElement instanceof XsdAny) {
            nodeTypeForXpath = "any";
        }

        if (nodeTypeForXpath != null) {
            String xpathQuery;
            if (elementNameForXpath != null) {
                xpathQuery = this.schemaPrefix + ":" + nodeTypeForXpath + "[@name='" + elementNameForXpath + "']";
            } else {
                xpathQuery = this.schemaPrefix + ":" + nodeTypeForXpath;
            }
            currentNode = xmlService.getNodeFromXpath("//" + xpathQuery, parentNode);
            if (currentNode == null) {
                currentNode = parentNode;
            }
            extendedXsdElement.setCurrentNode(currentNode);
            extendedXsdElement.setSourceCode(xmlService.getNodeAsString(currentNode));
        }

        switch (xsdAbstractElement) {
            case XsdElement xsdElement -> {
                final String parentXpath = "/" + String.join("/", prevElementPath);
                String currentXpath = prevElementPath.isEmpty()
                        ? "/" + xsdElement.getName()
                        : parentXpath + "/" + xsdElement.getName();

                if (!prevElementPath.isEmpty()) {
                    xsdDocumentationData.getExtendedXsdElementMap().get(parentXpath).getChildren().add(currentXpath);
                }

                processAnnotations(xsdElement.getAnnotation(), extendedXsdElement);

                if (parentJavadocInfo != null && parentJavadocInfo.hasData()) {
                    JavadocInfo currentJavadoc = extendedXsdElement.getJavadocInfo();
                    if (currentJavadoc == null) {
                        currentJavadoc = new JavadocInfo();
                        extendedXsdElement.setJavadocInfo(currentJavadoc);
                    }
                    if ((currentJavadoc.getSince() == null || currentJavadoc.getSince().trim().isEmpty())
                            && parentJavadocInfo.getSince() != null && !parentJavadocInfo.getSince().trim().isEmpty()) {
                        currentJavadoc.setSince(parentJavadocInfo.getSince());
                    }
                    if ((currentJavadoc.getDeprecated() == null || currentJavadoc.getDeprecated().trim().isEmpty())
                            && parentJavadocInfo.getDeprecated() != null && !parentJavadocInfo.getDeprecated().trim().isEmpty()) {
                        currentJavadoc.setDeprecated(parentJavadocInfo.getDeprecated());
                    }
                    if (currentJavadoc.getSee().isEmpty() && !parentJavadocInfo.getSee().isEmpty()) {
                        currentJavadoc.getSee().addAll(parentJavadocInfo.getSee());
                    }
                }

                extendedXsdElement.setParentXpath(parentXpath);
                extendedXsdElement.setCurrentXpath(currentXpath);
                extendedXsdElement.setLevel(level);
                extendedXsdElement.setElementName(xsdElement.getRawName());
                extendedXsdElement.setXsdElement(xsdElement);

                if (xsdElement.getAnnotation() != null && xsdElement.getAnnotation().getDocumentations() != null) {
                    extendedXsdElement.setXsdDocumentation(xsdElement.getAnnotation().getDocumentations());
                }

                if (xsdElement.getType() != null) {
                    extendedXsdElement.setElementType(xsdElement.getType());
                } else if (xsdElement.getXsdSimpleType() != null && xsdElement.getXsdSimpleType().getRestriction() != null) {
                    extendedXsdElement.setElementType(xsdElement.getXsdSimpleType().getRestriction().getBase());
                } else {
                    extendedXsdElement.setElementType("(Complex Content)");
                }

                var currentType = xsdElement.getType();
                if (currentType != null && prevElementTypes.contains(currentType.trim())) {
                    logger.info("Recursion detected for type {}. Stopping traversal.", currentType);
                    xsdDocumentationData.getExtendedXsdElementMap().put(currentXpath, extendedXsdElement);
                    return;
                }

                ArrayList<String> prevTemp = new ArrayList<>(prevElementTypes);
                if (currentType != null) prevTemp.add(currentType.trim());

                ArrayList<String> prevPathTemp = new ArrayList<>(prevElementPath);
                prevPathTemp.add(xsdElement.getName());

                xsdDocumentationData.getExtendedXsdElementMap().put(currentXpath, extendedXsdElement);

                if (xsdElement.getXsdComplexType() != null) {
                    XsdComplexType xsdComplexType = xsdElement.getXsdComplexType();
                    if (xsdComplexType.getElements() != null) {
                        for (ReferenceBase referenceBase : xsdComplexType.getElements()) {
                            getXsdAbstractElementInfo(level + 1, referenceBase.getElement(), prevTemp, prevPathTemp, currentNode, extendedXsdElement.getJavadocInfo());
                        }
                    }
                    if (xsdComplexType.getAllXsdAttributes() != null) {
                        Node finalCurrentNode = currentNode;
                        xsdComplexType.getAllXsdAttributes().forEach(xsdAttribute ->
                                getXsdAbstractElementInfo(level + 1, xsdAttribute, prevTemp, prevPathTemp, finalCurrentNode, extendedXsdElement.getJavadocInfo())
                        );
                    }
                } else if (xsdElement.getXsdSimpleType() != null) {
                    if (xsdElement.getXsdSimpleType().getRestriction() != null) {
                        extendedXsdElement.setXsdRestriction(xsdElement.getXsdSimpleType().getRestriction());
                    }
                    extendedXsdElement.setSampleData(sampleDataGenerator.generate(extendedXsdElement));
                } else if (xsdElement.getType() != null) {
                    extendedXsdElement.setSampleData(sampleDataGenerator.generate(extendedXsdElement));
                }
            }
            case XsdAttribute xsdAttribute -> {
                final String parentXpath = "/" + String.join("/", prevElementPath);
                String currentXpath = parentXpath + "/@" + xsdAttribute.getName();

                extendedXsdElement.setParentXpath(parentXpath);
                extendedXsdElement.setCurrentXpath(currentXpath);
                extendedXsdElement.setLevel(level);
                extendedXsdElement.setElementName("@" + xsdAttribute.getName());

                processAnnotations(xsdAttribute.getAnnotation(), extendedXsdElement);

                if (parentJavadocInfo != null && parentJavadocInfo.hasData()) {
                    JavadocInfo currentJavadoc = extendedXsdElement.getJavadocInfo();
                    if (currentJavadoc == null) {
                        currentJavadoc = new JavadocInfo();
                        extendedXsdElement.setJavadocInfo(currentJavadoc);
                    }
                    if ((currentJavadoc.getSince() == null || currentJavadoc.getSince().trim().isEmpty())
                            && parentJavadocInfo.getSince() != null && !parentJavadocInfo.getSince().trim().isEmpty()) {
                        currentJavadoc.setSince(parentJavadocInfo.getSince());
                    }
                    if ((currentJavadoc.getDeprecated() == null || currentJavadoc.getDeprecated().trim().isEmpty())
                            && parentJavadocInfo.getDeprecated() != null && !parentJavadocInfo.getDeprecated().trim().isEmpty()) {
                        currentJavadoc.setDeprecated(parentJavadocInfo.getDeprecated());
                    }
                    if (currentJavadoc.getSee().isEmpty() && !parentJavadocInfo.getSee().isEmpty()) {
                        currentJavadoc.getSee().addAll(parentJavadocInfo.getSee());
                    }
                }

                if (!prevElementPath.isEmpty()) {
                    xsdDocumentationData.getExtendedXsdElementMap().get(parentXpath).getChildren().add(currentXpath);
                }

                if (xsdAttribute.getAnnotation() != null && xsdAttribute.getAnnotation().getDocumentations() != null) {
                    extendedXsdElement.setXsdDocumentation(xsdAttribute.getAnnotation().getDocumentations());
                }

                extendedXsdElement.setElementType(xsdAttribute.getType());
                if (xsdAttribute.getXsdSimpleType() != null && xsdAttribute.getXsdSimpleType().getRestriction() != null) {
                    extendedXsdElement.setXsdRestriction(xsdAttribute.getXsdSimpleType().getRestriction());
                    if (extendedXsdElement.getElementType() == null) {
                        extendedXsdElement.setElementType(xsdAttribute.getXsdSimpleType().getRestriction().getBase());
                    }
                }
                xsdDocumentationData.getExtendedXsdElementMap().put(currentXpath, extendedXsdElement);
            }
            case XsdChoice xsdChoice -> xsdChoice.getElements().forEach(x ->
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode, parentJavadocInfo)
            );
            case XsdSequence xsdSequence -> xsdSequence.getElements().forEach(x ->
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode, parentJavadocInfo)
            );
            case XsdAll xsdAll -> xsdAll.getElements().forEach(x ->
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode, parentJavadocInfo)
            );
            case XsdGroup xsdGroup -> xsdGroup.getElements().forEach(x ->
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode, parentJavadocInfo)
            );
            case XsdAttributeGroup xsdAttributeGroup -> xsdAttributeGroup.getElements().forEach(x ->
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode, parentJavadocInfo)
            );
            case XsdExtension xsdExtension -> xsdExtension.getElements().forEach(x ->
                    getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode, parentJavadocInfo)
            );
            case XsdComplexContent xsdComplexContent -> {
                if (xsdComplexContent.getXsdExtension() != null) {
                    getXsdAbstractElementInfo(level, xsdComplexContent.getXsdExtension(), prevElementTypes, prevElementPath, parentNode, parentJavadocInfo);
                }
                if (xsdComplexContent.getXsdRestriction() != null) {
                    getXsdAbstractElementInfo(level, xsdComplexContent.getXsdRestriction(), prevElementTypes, prevElementPath, parentNode, parentJavadocInfo);
                }
            }
            case XsdSimpleContent xsdSimpleContent -> {
                if (xsdSimpleContent.getXsdExtension() != null) {
                    getXsdAbstractElementInfo(level, xsdSimpleContent.getXsdExtension(), prevElementTypes, prevElementPath, parentNode, parentJavadocInfo);
                }
                if (xsdSimpleContent.getXsdRestriction() != null) {
                    getXsdAbstractElementInfo(level, xsdSimpleContent.getXsdRestriction(), prevElementTypes, prevElementPath, parentNode, parentJavadocInfo);
                }
            }
            case XsdRestriction xsdRestriction -> {
                if (xsdRestriction.getXsdAttributes() != null) {
                    xsdRestriction.getXsdAttributes().forEach(xsdAttribute ->
                            getXsdAbstractElementInfo(level + 1, xsdAttribute, prevElementTypes, prevElementPath, parentNode, parentJavadocInfo)
                    );
                }
                xsdRestriction.getElements().forEach(x ->
                        getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode, parentJavadocInfo)
                );
            }
            case XsdAny xsdAny -> {
                final String parentXpath = "/" + String.join("/", prevElementPath);
                String currentXpath = parentXpath + "/any_" + extendedXsdElement.getCounter();
                extendedXsdElement.setParentXpath(parentXpath);
                extendedXsdElement.setCurrentXpath(currentXpath);
                extendedXsdElement.setLevel(level);
                extendedXsdElement.setElementName("any");

                if (!prevElementPath.isEmpty()) {
                    xsdDocumentationData.getExtendedXsdElementMap().get(parentXpath).getChildren().add(currentXpath);
                }
                if (xsdAny.getAnnotation() != null && xsdAny.getAnnotation().getDocumentations() != null) {
                    extendedXsdElement.setXsdDocumentation(xsdAny.getAnnotation().getDocumentations());
                }
                String namespaceInfo = xsdAny.getNamespace() != null ? xsdAny.getNamespace() : "##any";
                String processContentsInfo = xsdAny.getProcessContents() != null ? xsdAny.getProcessContents() : "strict";
                extendedXsdElement.setElementType(String.format("Wildcard (namespace: %s, process: %s)", namespaceInfo, processContentsInfo));

                xsdDocumentationData.getExtendedXsdElementMap().put(currentXpath, extendedXsdElement);
            }
            default -> throw new IllegalStateException("Unexpected value: " + xsdAbstractElement);
        }
    }

    public String generateSampleXml(boolean mandatoryOnly, int maxOccurrences) {
        if (xsdDocumentationData.getExtendedXsdElementMap().isEmpty()) {
            processXsd(false);
        }

        List<ExtendedXsdElement> rootElements = xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> e.getParentXpath().isEmpty() || e.getParentXpath().equals("/"))
                .toList();

        if (rootElements.isEmpty()) {
            return "<!-- No root element found in XSD -->";
        }

        StringBuilder xmlBuilder = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        ExtendedXsdElement root = rootElements.getFirst();
        buildXmlElement(xmlBuilder, root, mandatoryOnly, maxOccurrences, 0);
        return xmlBuilder.toString();
    }

    private void buildXmlElement(StringBuilder sb, ExtendedXsdElement element, boolean mandatoryOnly, int maxOccurrences, int indentLevel) {
        if (mandatoryOnly && !element.isMandatory()) {
            return;
        }

        int repeatCount = 1;
        if (element.getXsdElement() != null && element.getXsdElement().getMaxOccurs() != null) {
            String max = element.getXsdElement().getMaxOccurs();
            if (max.equals("unbounded") || Integer.parseInt(max) > 1) {
                repeatCount = maxOccurrences;
            }
        }

        for (int i = 0; i < repeatCount; i++) {
            String indent = "\t".repeat(indentLevel);
            sb.append(indent).append("<").append(element.getElementName());
            sb.append(">");

            if (element.hasChildren()) {
                sb.append("\n");
                for (String childXPath : element.getChildren()) {
                    ExtendedXsdElement childElement = xsdDocumentationData.getExtendedXsdElementMap().get(childXPath);
                    if (childElement != null) {
                        buildXmlElement(sb, childElement, mandatoryOnly, maxOccurrences, indentLevel + 1);
                    }
                }
                sb.append(indent).append("</").append(element.getElementName()).append(">\n");
            } else {
                sb.append(element.getSampleData() != null ? element.getSampleData() : "");
                sb.append("</").append(element.getElementName()).append(">\n");
            }
        }
    }

    private String getSchemaPrefix() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new File(xsdFilePath));
            Element rootElement = doc.getDocumentElement();
            if (rootElement != null && "schema".equals(rootElement.getLocalName())) {
                return rootElement.getPrefix();
            }
        } catch (Exception e) {
            logger.error("Fehler beim Parsen der XML-Datei: {}", e.getMessage());
        }
        return "xs"; // Fallback
    }

    private void processAnnotations(XsdAnnotation annotation, ExtendedXsdElement extendedXsdElement) {
        if (annotation == null) {
            return;
        }

        if (annotation.getDocumentations() != null) {
            extendedXsdElement.setXsdDocumentation(annotation.getDocumentations());
        }

        List<XsdAppInfo> appInfos = annotation.getAppInfoList();
        if (appInfos == null || appInfos.isEmpty()) {
            return;
        }

        JavadocInfo javadocInfo = new JavadocInfo();
        List<String> genericAppInfos = new ArrayList<>();

        for (XsdAppInfo appInfo : appInfos) {
            String source = appInfo.getSource();
            if (source == null || source.trim().isEmpty()) {
                String content = appInfo.getContent();
                if (content != null && !content.trim().isEmpty()) {
                    genericAppInfos.add(content.trim());
                }
                continue;
            }

            source = source.trim();
            if (source.startsWith("@since")) {
                javadocInfo.setSince(source.substring("@since".length()).trim());
            } else if (source.startsWith("@see")) {
                javadocInfo.getSee().add(source.substring("@see".length()).trim());
            } else if (source.startsWith("@deprecated")) {
                javadocInfo.setDeprecated(source.substring("@deprecated".length()).trim());
            } else {
                genericAppInfos.add(source);
            }
        }

        if (javadocInfo.hasData()) {
            extendedXsdElement.setJavadocInfo(javadocInfo);
        }
        if (!genericAppInfos.isEmpty()) {
            extendedXsdElement.setGenericAppInfos(genericAppInfos);
        }
    }

    /**
     * Baut den Baum für die grafische Ansicht aus den bereits verarbeiteten XSD-Daten.
     *
     * @return Der Wurzelknoten des Baumes oder null, wenn keiner gefunden wurde.
     */
    public XsdNodeInfo buildTreeFromProcessedData() {
        if (xsdDocumentationData.getExtendedXsdElementMap().isEmpty()) {
            logger.warn("XSD data has not been processed yet. Processing now.");
            processXsd(false);
        }

        ExtendedXsdElement rootXsdElement = xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> e.getParentXpath().isEmpty() || e.getParentXpath().equals("/"))
                .findFirst()
                .orElse(null);

        if (rootXsdElement == null) {
            logger.error("No root element found to build the diagram tree.");
            return null;
        }

        return buildNodeInfoRecursive(rootXsdElement);
    }

    /**
     * Rekursive Hilfsmethode zum Aufbau des XsdNodeInfo-Baums.
     */
    private XsdNodeInfo buildNodeInfoRecursive(ExtendedXsdElement element) {
        if (element == null) return null;

        List<XsdNodeInfo> children = new ArrayList<>();
        if (element.hasChildren()) {
            for (String childXPath : element.getChildren()) {
                ExtendedXsdElement childElement = xsdDocumentationData.getExtendedXsdElementMap().get(childXPath);
                if (childElement != null) {
                    children.add(buildNodeInfoRecursive(childElement));
                }
            }
        }

        String doc = "";
        if (element.getXsdDocumentation() != null && !element.getXsdDocumentation().isEmpty()) {
            doc = element.getXsdDocumentation().getFirst().getContent();
        }

        return new XsdNodeInfo(
                element.getElementName(),
                element.getElementType(),
                doc,
                children,
                element.getCurrentXpath()
        );
    }
}