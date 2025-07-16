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
import org.fxt.freexmltoolkit.service.TaskProgressListener.ProgressUpdate;
import org.fxt.freexmltoolkit.service.TaskProgressListener.ProgressUpdate.Status;
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
import java.util.*;
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

    private TaskProgressListener progressListener;

    String schemaPrefix;

    XsdDocumentationHtmlService xsdDocumentationHtmlService = new XsdDocumentationHtmlService();
    private final SampleDataGenerator sampleDataGenerator = new SampleDataGenerator();

    public void setProgressListener(TaskProgressListener progressListener) {
        this.progressListener = progressListener;
    }

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

        executeAndTrack("Ressourcen kopieren", xsdDocumentationHtmlService::copyResources);
        executeAndTrack("Startseite generieren", xsdDocumentationHtmlService::generateRootPage);
        executeAndTrack("Liste der komplexen Typen generieren", xsdDocumentationHtmlService::generateComplexTypesListPage);
        executeAndTrack("Liste der einfachen Typen generieren", xsdDocumentationHtmlService::generateSimpleTypesListPage);
        executeAndTrack("Datenwörterbuch generieren", xsdDocumentationHtmlService::generateDataDictionaryPage);
        executeAndTrack("Suchindex generieren", xsdDocumentationHtmlService::generateSearchIndex);

        if (parallelProcessing) {
            executeAndTrack("Detailseiten für komplexe Typen generieren (parallel)", xsdDocumentationHtmlService::generateComplexTypePagesInParallel);
            executeAndTrack("Detailseiten für einfache Typen generieren (parallel)", xsdDocumentationHtmlService::generateSimpleTypePagesInParallel);
            executeAndTrack("Detailseiten für Elemente generieren (parallel)", xsdDocumentationHtmlService::generateDetailsPagesInParallel);
        } else {
            executeAndTrack("Detailseiten für komplexe Typen generieren", xsdDocumentationHtmlService::generateComplexTypePages);
            executeAndTrack("Detailseiten für einfache Typen generieren", xsdDocumentationHtmlService::generateSimpleTypePages);
            executeAndTrack("Detailseiten für Elemente generieren", xsdDocumentationHtmlService::generateDetailPages);
        }

    }

    public void processXsd(Boolean useMarkdownRenderer) {
        this.useMarkdownRenderer = useMarkdownRenderer;
        parser = new XsdParser(xsdFilePath);
        xmlService.setCurrentXmlFile(new File(xsdFilePath));

        xsdDocumentationData.setElements(parser.getResultXsdElements().collect(Collectors.toList()));
        xsdDocumentationData.setXmlSchema(parser.getResultXsdSchemas().toList());

        // Sammle die Typen aus ALLEN geparsten Schema-Dateien.
        // Wir verwenden flatMap, um die Streams von jedem Schema zu einem einzigen zu verbinden.
        List<XsdComplexType> allComplexTypes = parser.getResultXsdSchemas()
                .flatMap(XsdSchema::getChildrenComplexTypes)
                .collect(Collectors.toList());
        xsdDocumentationData.setXsdComplexTypes(allComplexTypes);

        List<XsdSimpleType> allSimpleTypes = parser.getResultXsdSchemas()
                .flatMap(XsdSchema::getChildrenSimpleTypes)
                .collect(Collectors.toList());
        xsdDocumentationData.setXsdSimpleTypes(allSimpleTypes);

        // Sammle alle Namespaces aus allen Schema-Dateien
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

        Node finalCurrentNode = currentNode;
        switch (xsdAbstractElement) {
            case XsdElement xsdElement -> {
                final String parentXpath = "/" + String.join("/", prevElementPath);
                String currentXpath = prevElementPath.isEmpty()
                        ? "/" + xsdElement.getName()
                        : parentXpath + "/" + xsdElement.getName();

                if (!prevElementPath.isEmpty()) {
                    xsdDocumentationData.getExtendedXsdElementMap().get(parentXpath).getChildren().add(currentXpath);
                }

                // Setze den Typ von Anfang an. Dieser Wert wird nur überschrieben,
                // wenn es absolut notwendig ist (z.B. bei anonymen inline-Typen).
                if (xsdElement.getType() != null) {
                    extendedXsdElement.setElementType(xsdElement.getType());
                }

                // Schritt 1: Verarbeite Annotationen direkt am Element-Tag
                processAnnotations(xsdElement.getAnnotation(), extendedXsdElement);

                // Javadoc-Vererbung
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

                // Element in die Map legen, damit Kind-Elemente es finden können
                xsdDocumentationData.getExtendedXsdElementMap().put(currentXpath, extendedXsdElement);

                // Suche nach global definierten, referenzierten Typen und übernehme deren Dokumentation.
                // Dies ist entscheidend für Elemente, die einen Typ über das 'type'-Attribut referenzieren (z.B. type="tns:MyType").
                if (xsdElement.getType() != null && !xsdElement.getType().isBlank()) {
                    String typeName = xsdElement.getType();

                    // Suche in den globalen ComplexTypes
                    xsdDocumentationData.getXsdComplexTypes().stream()
                            .filter(ct -> typeName.equals(ct.getName()))
                            .findFirst()
                            .ifPresent(complexType -> processAnnotations(complexType.getAnnotation(), extendedXsdElement));

                    // Suche in den globalen SimpleTypes
                    xsdDocumentationData.getXsdSimpleTypes().stream()
                            .filter(st -> typeName.equals(st.getName()))
                            .findFirst()
                            .ifPresent(simpleType -> processAnnotations(simpleType.getAnnotation(), extendedXsdElement));
                }

                // Rekursionsschutz
                var currentType = xsdElement.getType();
                if (currentType != null && prevElementTypes.contains(currentType.trim())) {
                    logger.info("Recursion detected for type {}. Stopping traversal.", currentType);
                    // Wichtig: Typ ist hier bereits korrekt gesetzt.
                    extendedXsdElement.setSampleData(sampleDataGenerator.generate(extendedXsdElement));
                    return;
                }

                // Vorbereitung für rekursive Aufrufe
                ArrayList<String> prevTemp = new ArrayList<>(prevElementTypes);
                if (currentType != null) prevTemp.add(currentType.trim());
                ArrayList<String> prevPathTemp = new ArrayList<>(prevElementPath);
                prevPathTemp.add(xsdElement.getName());

                // Detaillierte Typ- und Inhaltsverarbeitung für inline-Definitionen und Kinder
                if (xsdElement.getXsdComplexType() != null) {
                    XsdComplexType complexType = xsdElement.getXsdComplexType();
                    // Verarbeite auch die Annotation des inline definierten Typs
                    processAnnotations(complexType.getAnnotation(), extendedXsdElement);

                    // Explizite und gezielte Behandlung von simpleContent
                    if (complexType.getSimpleContent() != null) {
                        XsdSimpleContent simpleContent = complexType.getSimpleContent();

                        // Fall 1: simpleContent mit einer Extension
                        if (simpleContent.getXsdExtension() != null) {
                            XsdExtension extension = simpleContent.getXsdExtension();
                            // KORREKTUR: Nur den Typ setzen, wenn noch keiner explizit definiert wurde.
                            if (extendedXsdElement.getElementType() == null) {
                                extendedXsdElement.setElementType(String.valueOf(extension.getBase()));
                            }

                            // Verarbeite die Attribute, die in der Erweiterung definiert sind
                            if (extension.getXsdAttributes() != null) {
                                extension.getXsdAttributes().forEach(attr ->
                                        getXsdAbstractElementInfo(level + 1, attr, prevTemp, prevPathTemp, finalCurrentNode, extendedXsdElement.getJavadocInfo())
                                );
                            }
                        }
                        // Fall 2: simpleContent mit einer Restriction
                        else if (simpleContent.getXsdRestriction() != null) {
                            XsdRestriction restriction = simpleContent.getXsdRestriction();
                            extendedXsdElement.setXsdRestriction(restriction);
                            // KORREKTUR: Nur den Typ setzen, wenn noch keiner explizit definiert wurde.
                            if (extendedXsdElement.getElementType() == null) {
                                extendedXsdElement.setElementType(restriction.getBase());
                            }

                            // Verarbeite die Attribute, die in der Beschränkung definiert sind
                            if (restriction.getXsdAttributes() != null) {
                                restriction.getXsdAttributes().forEach(attr ->
                                        getXsdAbstractElementInfo(level + 1, attr, prevTemp, prevPathTemp, finalCurrentNode, extendedXsdElement.getJavadocInfo())
                                );
                            }
                        }
                    } else {
                        // KORREKTUR: Nur einen Fallback-Typ setzen, wenn noch kein Typ vorhanden ist.
                        if (extendedXsdElement.getElementType() == null) {
                            if (complexType.getName() != null && !complexType.getName().isEmpty()) {
                                extendedXsdElement.setElementType(complexType.getName());
                            } else {
                                extendedXsdElement.setElementType("(Complex Content)");
                            }
                        }

                        XsdAbstractElement child = null;
                        if (complexType.getComplexContent() != null) child = complexType.getComplexContent();
                        else if (complexType.getChildAsSequence() != null) child = complexType.getChildAsSequence();
                        else if (complexType.getChildAsChoice() != null) child = complexType.getChildAsChoice();
                        else if (complexType.getChildAsAll() != null) child = complexType.getChildAsAll();

                        if (child != null) {
                            getXsdAbstractElementInfo(level, child, prevTemp, prevPathTemp, currentNode, extendedXsdElement.getJavadocInfo());
                        }
                    }

                    if (complexType.getAllXsdAttributes() != null) {
                        complexType.getAllXsdAttributes().forEach(attr ->
                                getXsdAbstractElementInfo(level + 1, attr, prevTemp, prevPathTemp, finalCurrentNode, extendedXsdElement.getJavadocInfo())
                        );
                    }
                } else if (xsdElement.getXsdSimpleType() != null) {
                    XsdSimpleType simpleType = xsdElement.getXsdSimpleType();
                    processAnnotations(simpleType.getAnnotation(), extendedXsdElement); // Annotation für inline simpleType
                    if (simpleType.getRestriction() != null) {
                        extendedXsdElement.setXsdRestriction(simpleType.getRestriction());
                        // KORREKTUR: Nur den Typ setzen, wenn noch keiner explizit definiert wurde.
                        if (extendedXsdElement.getElementType() == null) {
                            extendedXsdElement.setElementType(simpleType.getRestriction().getBase());
                        }
                    }
                }
                // Der Fall "else if (xsdElement.getType() != null)" ist nicht mehr nötig,
                // da dies bereits am Anfang der Methode behandelt wurde.

                // FINALER SCHRITT: Generiere Beispieldaten für das Element selbst.
                extendedXsdElement.setSampleData(sampleDataGenerator.generate(extendedXsdElement));
            }
            // ... Der Rest der Methode bleibt unverändert
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
                // Beispieldaten für das Attribut generieren
                extendedXsdElement.setSampleData(sampleDataGenerator.generate(extendedXsdElement));
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
            case XsdExtension xsdExtension -> {
                // 1. Verarbeite die Elemente und Attribute des Basistyps
                String baseTypeName = String.valueOf(xsdExtension.getBase());
                if (baseTypeName != null && !baseTypeName.isBlank()) {
                    // Finde den ComplexType des Basistyps in der globalen Liste
                    xsdDocumentationData.getXsdComplexTypes().stream()
                            .filter(ct -> baseTypeName.equals(ct.getName()) || baseTypeName.equals(ct.getRawName()))
                            .findFirst()
                            .ifPresent(baseComplexType -> {
                                // Verarbeite das Kind-Element des Basistyps korrekt
                                XsdAbstractElement baseChild = null;
                                if (baseComplexType.getComplexContent() != null) baseChild = baseComplexType.getComplexContent();
                                else if (baseComplexType.getSimpleContent() != null) baseChild = baseComplexType.getSimpleContent();
                                else if (baseComplexType.getChildAsSequence() != null) baseChild = baseComplexType.getChildAsSequence();
                                else if (baseComplexType.getChildAsChoice() != null) baseChild = baseComplexType.getChildAsChoice();
                                else if (baseComplexType.getChildAsAll() != null) baseChild = baseComplexType.getChildAsAll();

                                if (baseChild != null) {
                                    // Wir bleiben auf dem gleichen 'level', da es eine Vererbung ist
                                    getXsdAbstractElementInfo(level, baseChild, prevElementTypes, prevElementPath, parentNode, parentJavadocInfo);
                                }
                                // Verarbeite auch alle Attribute des Basistyps
                                if (baseComplexType.getAllXsdAttributes() != null) {
                                    baseComplexType.getAllXsdAttributes().forEach(attr ->
                                            getXsdAbstractElementInfo(level + 1, attr, prevElementTypes, prevElementPath, parentNode, parentJavadocInfo)
                                    );
                                }
                            });
                }

                // 2. Verarbeite die Elemente, die in der Erweiterung selbst definiert sind
                xsdExtension.getElements().forEach(x ->
                        getXsdAbstractElementInfo(level + 1, x.getElement(), prevElementTypes, prevElementPath, parentNode, parentJavadocInfo)
                );
                // Verarbeite auch die Attribute, die in der Erweiterung selbst definiert sind
                if (xsdExtension.getXsdAttributes() != null) {
                    xsdExtension.getXsdAttributes().forEach(attr ->
                            getXsdAbstractElementInfo(level + 1, attr, prevElementTypes, prevElementPath, parentNode, parentJavadocInfo)
                    );
                }
            }
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
        if (element == null || (mandatoryOnly && !element.isMandatory())) {
            return;
        }

        // Attribute werden innerhalb ihres Elternelements behandelt, daher überspringen wir sie hier.
        if (element.getElementName().startsWith("@")) {
            return;
        }

        int repeatCount = 1;
        if (element.getXsdElement() != null && element.getXsdElement().getMaxOccurs() != null) {
            String max = element.getXsdElement().getMaxOccurs();
            if ("unbounded".equals(max)) {
                repeatCount = maxOccurrences;
            } else {
                try {
                    int maxVal = Integer.parseInt(max);
                    if (maxVal > 1) {
                        // Nutze den kleineren Wert zwischen dem Schema-Maximum und der Benutzereingabe
                        repeatCount = Math.min(maxVal, maxOccurrences);
                    }
                } catch (NumberFormatException e) {
                    // Ignorieren, falls maxOccurs keine Zahl ist
                }
            }
        }


        for (int i = 0; i < repeatCount; i++) {
            String indent = "\t".repeat(indentLevel);
            sb.append(indent).append("<").append(element.getElementName());

            // Trenne die Kinder in Attribute und Kind-Elemente auf
            List<ExtendedXsdElement> attributes = new ArrayList<>();
            List<ExtendedXsdElement> childElements = new ArrayList<>();

            if (element.hasChildren()) {
                for (String childXPath : element.getChildren()) {
                    ExtendedXsdElement child = xsdDocumentationData.getExtendedXsdElementMap().get(childXPath);
                    if (child != null) {
                        if (child.getElementName().startsWith("@")) {
                            attributes.add(child);
                        } else {
                            childElements.add(child);
                        }
                    }
                }
            }

            // Rendere die Attribute im öffnenden Tag
            for (ExtendedXsdElement attr : attributes) {
                if (mandatoryOnly && !attr.isMandatory()) {
                    continue;
                }
                String attrName = attr.getElementName().substring(1); // Entferne das '@'
                String attrValue = attr.getSampleData() != null ? attr.getSampleData() : "";
                sb.append(" ").append(attrName).append("=\"").append(attrValue).append("\"");
            }

            // Entscheide, wie das Tag geschlossen wird (basierend auf Inhalt)
            String sampleData = element.getSampleData() != null ? element.getSampleData() : "";
            if (childElements.isEmpty() && sampleData.isEmpty()) {
                sb.append("/>\n"); // Selbstschließendes Tag, wenn keine Kinder und kein Inhalt
            } else {
                sb.append(">"); // Schließe das öffnende Tag

                // KORREKTUR: Füge den Textinhalt des Elements hinzu, falls vorhanden.
                // Dies geschieht VOR dem Rendern der Kind-Elemente für "mixed content".
                sb.append(sampleData);

                if (!childElements.isEmpty()) {
                    sb.append("\n");
                    for (ExtendedXsdElement childElement : childElements) {
                        // Rekursiver Aufruf NUR für Kind-Elemente
                        buildXmlElement(sb, childElement, mandatoryOnly, maxOccurrences, indentLevel + 1);
                    }
                    sb.append(indent);
                }

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

        // Dokumentationen werden jetzt hinzugefügt, nicht mehr überschrieben.
        if (annotation.getDocumentations() != null && !annotation.getDocumentations().isEmpty()) {
            if (extendedXsdElement.getXsdDocumentation() == null) {
                extendedXsdElement.setXsdDocumentation(new ArrayList<>());
            }
            extendedXsdElement.getXsdDocumentation().addAll(annotation.getDocumentations());
        }

        List<XsdAppInfo> appInfos = annotation.getAppInfoList();
        if (appInfos == null || appInfos.isEmpty()) {
            return;
        }

        // Stelle sicher, dass JavadocInfo und genericAppInfos zum Hinzufügen bereit sind.
        JavadocInfo javadocInfo = extendedXsdElement.getJavadocInfo();
        if (javadocInfo == null) {
            javadocInfo = new JavadocInfo();
        }
        List<String> genericAppInfos = extendedXsdElement.getGenericAppInfos();
        if (genericAppInfos == null) {
            genericAppInfos = new ArrayList<>();
        }

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

    /**
     * Erstellt einen leichtgewichtigen Baum für die Diagrammansicht direkt aus der XSD-Datei,
     * ohne die aufwändige Dokumentationsverarbeitung durchzuführen.
     *
     * @param xsdPath Der Pfad zur XSD-Datei.
     * @return Der Wurzelknoten des Baumes (XsdNodeInfo) oder null bei einem Fehler.
     */
    public XsdNodeInfo buildLightweightTree(String xsdPath) {
        try {
            // Delegiere an die Methode, die eine Parser-Instanz entgegennimmt.
            return buildLightweightTree(new XsdParser(xsdPath));
        } catch (Exception e) {
            logger.error("Fehler beim Parsen der XSD-Datei: " + xsdPath, e);
            return null;
        }
    }

    /**
     * Erstellt einen leichtgewichtigen Baum für die Diagrammansicht aus einer bereits
     * bestehenden Parser-Instanz.
     *
     * @param parser Die bereits initialisierte XsdParser-Instanz.
     * @return Der Wurzelknoten des Baumes (XsdNodeInfo) oder null bei einem Fehler.
     */
    public XsdNodeInfo buildLightweightTree(XsdParser parser) {
        XsdElement rootElement = parser.getResultXsdElements().findFirst().orElse(null);

        if (rootElement == null) {
            logger.error("Kein Wurzelelement im Schema gefunden.");
            return null;
        }
        return buildLightweightNodeRecursive(parser, rootElement, "/" + rootElement.getName());
    }

    /**
     * Rekursive Hilfsmethode, um den XsdNodeInfo-Baum aus den nativen Parser-Objekten aufzubauen.
     *
     * @param parser       Die Parser-Instanz, um globale Typen aufzulösen.
     * @param element      Das aktuelle XSD-Element (oder Attribut) vom Parser.
     * @param currentXPath Der aktuelle XPath zu diesem Element.
     * @return Ein XsdNodeInfo-Objekt, das dieses Element und seine Kinder repräsentiert.
     */
    private XsdNodeInfo buildLightweightNodeRecursive(XsdParser parser, XsdAbstractElement element, String currentXPath) {
        if (element == null) {
            return null;
        }

        String name = "";
        String type = "";
        String documentation = "";
        List<XsdNodeInfo> children = new ArrayList<>();

        if (element instanceof XsdElement xsdElement) {
            name = xsdElement.getName();
            type = xsdElement.getType();

            // Extrahiere Dokumentation direkt vom Element oder seinem Typ
            if (xsdElement.getAnnotation() != null) {
                documentation = xsdElement.getAnnotation().getDocumentations().stream()
                        .map(XsdAnnotationChildren::getContent).collect(Collectors.joining("\n"));
            }

            // Sammle Kinder (Elemente und Attribute)
            // KORREKTUR: Die Logik wurde robuster gemacht, um Abstürze zu vermeiden
            // und auch referenzierte globale Typen korrekt aufzulösen.
            XsdComplexType complexType = xsdElement.getXsdComplexType();
            if (complexType == null && xsdElement.getType() != null) {
                // Wenn der Typ referenziert ist, suche ihn in den globalen Definitionen.
                String typeName = xsdElement.getType();
                complexType = parser.getResultXsdSchemas()
                        .flatMap(XsdSchema::getChildrenComplexTypes)
                        .filter(ct -> typeName.equals(ct.getRawName()))
                        .findFirst().orElse(null);
            }

            if (complexType != null) {
                // Behandle die verschachtelte Struktur sicher.
                XsdMultipleElements particle = null;
                if (complexType.getComplexContent() != null) {
                    // Fall 1: Inhalt ist in <complexContent> (typisch für Vererbung)
                    var content = complexType.getComplexContent();
                    XsdAbstractElement contentChild = null;
                    if (content.getXsdExtension() != null) {
                        contentChild = content.getXsdExtension();
                    } else if (content.getXsdRestriction() != null) {
                        contentChild = content.getXsdRestriction();
                    }

                    // KORREKTUR: Prüfen, ob die Extension/Restriction Kind-Elemente enthalten kann.
                    // In manchen Fällen (z.B. nur Hinzufügen von Attributen) ist dies nicht der Fall,
                    // was zu einem ClassCastException führen würde.
                    if (contentChild instanceof XsdMultipleElements) {
                        particle = (XsdMultipleElements) contentChild;
                    }
                } else if (complexType.getSimpleContent() != null) {
                    // KORREKTUR: Fall 1b: Inhalt ist simpleContent.
                    // Ein complexType mit simpleContent hat keine Kind-Elemente, nur Attribute.
                    // Das explizite Abfangen verhindert den Absturz in der Parser-Bibliothek.
                    // Die Attribute werden weiter unten separat verarbeitet.
                } else {
                    // Fall 2: Direkte Partikel wie <sequence>, <choice>
                    if (complexType.getChildAsSequence() != null) particle = complexType.getChildAsSequence();
                    else if (complexType.getChildAsChoice() != null) particle = complexType.getChildAsChoice();
                    else if (complexType.getChildAsAll() != null) particle = complexType.getChildAsAll();
                }

                if (particle != null) {
                    addParticleChildren(parser, children, particle, currentXPath);
                }

                // Attribute hinzufügen
                complexType.getAllXsdAttributes().forEach(attribute ->
                        children.add(buildLightweightNodeRecursive(parser, attribute, currentXPath + "/@" + attribute.getName()))
                );
            }

        } else if (element instanceof XsdAttribute xsdAttribute) {
            name = "@" + xsdAttribute.getName();
            type = xsdAttribute.getType();
            if (xsdAttribute.getAnnotation() != null) {
                documentation = xsdAttribute.getAnnotation().getDocumentations().stream()
                        .map(XsdAnnotationChildren::getContent).collect(Collectors.joining("\n"));
            }
        } else if (element instanceof XsdAny xsdAny) {
            name = "any";
            String namespaceInfo = xsdAny.getNamespace() != null ? xsdAny.getNamespace() : "##any";
            String processContentsInfo = xsdAny.getProcessContents() != null ? xsdAny.getProcessContents() : "strict";
            type = String.format("Wildcard (namespace: %s, process: %s)", namespaceInfo, processContentsInfo);
            if (xsdAny.getAnnotation() != null) {
                documentation = xsdAny.getAnnotation().getDocumentations().stream()
                        .map(XsdAnnotationChildren::getContent).collect(Collectors.joining("\n"));
            }
        }

        return new XsdNodeInfo(
                name,
                type != null ? type : "",
                documentation,
                children,
                currentXPath
        );
    }

    /**
     * Rekursive Hilfsmethode, die die Kinder eines Partikels (sequence, choice, group) verarbeitet
     * und zur Kinderliste des Elternknotens hinzufügt.
     *
     * @param parser      Die Parser-Instanz für weitere rekursive Aufrufe.
     * @param children    Die Liste der Kinder des Elternknotens.
     * @param particle    Das Partikel-Element (sequence, choice, etc.).
     * @param parentXpath Der XPath des Elternknotens.
     */
    private void addParticleChildren(XsdParser parser, List<XsdNodeInfo> children, XsdMultipleElements particle, String parentXpath) {
        particle.getElements().stream()
                .map(ReferenceBase::getElement)
                .filter(Objects::nonNull)
                .forEach(child -> {
                    if (child instanceof XsdElement xsdElement) {
                        // Für ein Element, erstelle einen neuen Knoten mit einem neuen XPath.
                        children.add(buildLightweightNodeRecursive(parser, xsdElement, parentXpath + "/" + xsdElement.getName()));
                    } else if (child instanceof XsdMultipleElements xsdMultipleElements) {
                        // Für Container (choice, sequence, group), verarbeite deren Kinder rekursiv,
                        // ohne den XPath zu ändern.
                        addParticleChildren(parser, children, xsdMultipleElements, parentXpath);
                    } else if (child instanceof XsdAny xsdAny) {
                        // Erstelle einen Knoten für <xs:any>.
                        children.add(buildLightweightNodeRecursive(parser, xsdAny, parentXpath + "/any"));
                    }
                });
    }

    private void executeAndTrack(String taskName, Runnable task) {
        if (progressListener != null) {
            // Melde den Start des Tasks
            progressListener.onProgressUpdate(new ProgressUpdate(taskName, Status.STARTED, 0));
        }

        long startTime = System.nanoTime();
        task.run();
        long durationNanos = System.nanoTime() - startTime;

        if (progressListener != null) {
            // Melde das Ende des Tasks mit der benötigten Zeit
            progressListener.onProgressUpdate(new ProgressUpdate(taskName, Status.FINISHED, durationNanos / 1_000_000));
        }
    }
}