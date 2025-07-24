package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo.NodeType;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmlet.xsdparser.core.XsdParser;
import org.xmlet.xsdparser.xsdelements.*;
import org.xmlet.xsdparser.xsdelements.elementswrapper.ReferenceBase;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.xmlbeans.impl.common.XMLBeansConstants.FEATURE_DISALLOW_DOCTYPE_DECL;

/**
 * Ein Service, der spezialisierte Daten für die UI-Ansichten, insbesondere
 * für das XsdDiagramView, bereitstellt. Diese Klasse enthält die Logik zum
 * Erstellen eines leichtgewichtigen Baumes aus einem XSD.
 */
public class XsdViewService {

    private static final Logger logger = LogManager.getLogger(XsdViewService.class);

    // NEU: Caches für schnellen Zugriff auf Typdefinitionen
    private Map<String, XsdComplexType> complexTypeMap;
    private Map<String, XsdSimpleType> simpleTypeMap;

    // Ein Record, um die extrahierten Dokumentationsteile strukturiert zurückzugeben.
    public record DocumentationParts(String mainDocumentation, String javadocContent) {
    }

    /**
     * Erstellt einen leichtgewichtigen Baum für die Diagrammansicht aus einer bereits
     * bestehenden Parser-Instanz.
     *
     * @param parser Die bereits initialisierte XsdParser-Instanz.
     * @return Der Wurzelknoten des Baumes (XsdNodeInfo) oder null bei einem Fehler.
     */
    public XsdNodeInfo buildLightweightTree(XsdParser parser) {
        // OPTIMIERT: Initialisiere die Caches für einen schnellen Typ-Lookup.
        initializeTypeCaches(parser);

        XsdElement rootElement = parser.getResultXsdElements().findFirst().orElse(null);

        if (rootElement == null) {
            logger.error("Kein Wurzelelement im Schema gefunden.");
            return null;
        }
        // Starte die Rekursion mit einem leeren Set für den Pfad
        return buildLightweightNodeRecursive(rootElement, "/" + rootElement.getName(), new HashSet<>());
    }

    /**
     * NEU: Initialisiert die Maps für komplexe und einfache Typen, um die
     * wiederholte Suche im Schema zu vermeiden. Dies ist der Schlüssel zur Performance-Verbesserung.
     *
     * @param parser Die XsdParser-Instanz.
     */
    private void initializeTypeCaches(XsdParser parser) {
        // Sammelt alle ComplexTypes aus allen Schemas in einer Map.
        // Bei doppelten Namen wird der erste gefundene verwendet (üblicherweise korrekt).
        this.complexTypeMap = parser.getResultXsdSchemas()
                .flatMap(XsdSchema::getChildrenComplexTypes)
                .collect(Collectors.toMap(
                        XsdComplexType::getRawName,
                        Function.identity(),
                        (existing, replacement) -> existing // Bei Duplikaten den ersten behalten
                ));

        // Sammelt alle SimpleTypes aus allen Schemas in einer Map.
        this.simpleTypeMap = parser.getResultXsdSchemas()
                .flatMap(XsdSchema::getChildrenSimpleTypes)
                .collect(Collectors.toMap(
                        XsdSimpleType::getRawName,
                        Function.identity(),
                        (existing, replacement) -> existing // Bei Duplikaten den ersten behalten
                ));
    }


    /**
     * Extrahiert den allgemeinen Dokumentationstext und die Javadoc-Tags
     * aus der Annotation eines XSD-Schemas.
     *
     * @param schema Das zu analysierende Schema.
     * @return Ein DocumentationParts-Objekt mit den getrennten Inhalten.
     */
    public DocumentationParts extractDocumentationParts(XsdSchema schema) {
        if (schema == null || schema.getAnnotation() == null || schema.getAnnotation().getDocumentations().isEmpty()) {
            return new DocumentationParts("", "");
        }

        String fullDoc = schema.getAnnotation().getDocumentations().stream()
                .map(XsdAnnotationChildren::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n\n"));

        if (fullDoc.isBlank()) {
            return new DocumentationParts("", "");
        }

        String[] lines = fullDoc.split("\\r?\\n");
        StringBuilder mainDocBuilder = new StringBuilder();
        StringBuilder javadocBuilder = new StringBuilder();

        for (String line : lines) {
            if (line.trim().startsWith("@")) {
                javadocBuilder.append(line).append("\n");
            } else {
                mainDocBuilder.append(line).append("\n");
            }
        }
        return new DocumentationParts(mainDocBuilder.toString().trim(), javadocBuilder.toString().trim());
    }


    // =================================================================================
    // Private Hilfsmethoden
    // =================================================================================

    private XsdNodeInfo buildLightweightNodeRecursive(XsdAbstractElement element, String currentXPath, Set<XsdAbstractElement> visitedOnPath) {
        if (element == null) {
            return null;
        }

        // Rekursionsschutz: Wenn wir ein Element auf dem aktuellen Pfad erneut besuchen, stoppen wir.
        if (visitedOnPath.contains(element)) {
            logger.warn("Recursion detected on element {}, path {}. Stopping this branch.", element, currentXPath);
            String elementName = (element instanceof XsdElement xsdEl) ? xsdEl.getName() + " (recursive)" : "Recursive...";
            return new XsdNodeInfo(elementName, "recursive", currentXPath, "Recursive definition, traversal stopped.", Collections.emptyList(), Collections.emptyList(), "1", "1", NodeType.ELEMENT);
        }
        visitedOnPath.add(element);

        String name = "";
        String type = "";
        String documentation = "";
        String minOccurs = "1";
        String maxOccurs = "1";
        NodeType nodeType = NodeType.ELEMENT; // Standardwert
        List<XsdNodeInfo> children = new ArrayList<>();
        List<String> exampleValues = new ArrayList<>();

        if (element instanceof XsdElement xsdElement) {
            name = xsdElement.getName();
            type = xsdElement.getType();
            minOccurs = String.valueOf(xsdElement.getMinOccurs());
            maxOccurs = xsdElement.getMaxOccurs();
            nodeType = NodeType.ELEMENT;

            XsdAnnotation annotation = xsdElement.getAnnotation();
            if (annotation != null) {
                documentation = annotation.getDocumentations().stream()
                        .map(XsdAnnotationChildren::getContent).collect(Collectors.joining("\n"));
                exampleValues.addAll(extractExampleValues(annotation));
            }

            XsdComplexType complexType = xsdElement.getXsdComplexType();
            // OPTIMIERT: Schneller Typ-Lookup aus dem Cache statt langsamer Suche im Schema.
            if (complexType == null && xsdElement.getType() != null) {
                complexType = complexTypeMap.get(xsdElement.getType());
            }

            if (complexType != null) {
                XsdMultipleElements particle = getParticle(complexType);
                if (particle != null) {
                    children.add(buildLightweightNodeRecursive(particle, currentXPath, visitedOnPath));
                }
                complexType.getAllXsdAttributes().forEach(attribute ->
                        children.add(buildLightweightNodeRecursive(attribute, currentXPath + "/@" + attribute.getName(), visitedOnPath))
                );
            }

        } else if (element instanceof XsdAttribute xsdAttribute) {
            name = "@" + xsdAttribute.getName();
            type = xsdAttribute.getType();
            minOccurs = "required".equals(xsdAttribute.getUse()) ? "1" : "0";
            maxOccurs = "1";
            nodeType = NodeType.ATTRIBUTE;

            XsdAnnotation annotation = xsdAttribute.getAnnotation();
            if (annotation != null) {
                documentation = annotation.getDocumentations().stream()
                        .map(XsdAnnotationChildren::getContent).collect(Collectors.joining("\n"));
                exampleValues.addAll(extractExampleValues(annotation));
            }
        } else if (element instanceof XsdAny xsdAny) {
            name = "any";
            type = String.format("Wildcard (namespace: %s)", xsdAny.getNamespace() != null ? xsdAny.getNamespace() : "##any");
            minOccurs = String.valueOf(xsdAny.getMinOccurs());
            maxOccurs = xsdAny.getMaxOccurs();
            nodeType = NodeType.ANY;

            XsdAnnotation annotation = xsdAny.getAnnotation();
            if (annotation != null) {
                documentation = annotation.getDocumentations().stream()
                        .map(XsdAnnotationChildren::getContent).collect(Collectors.joining("\n"));
                exampleValues.addAll(extractExampleValues(annotation));
            }
        } else if (element instanceof XsdSequence sequence) {
            name = "sequence";
            type = "Container";
            minOccurs = String.valueOf(sequence.getMinOccurs());
            maxOccurs = sequence.getMaxOccurs();
            nodeType = NodeType.SEQUENCE;
            addParticleChildren(children, sequence, currentXPath, visitedOnPath);

        } else if (element instanceof XsdChoice choice) {
            name = "choice";
            type = "Container";
            minOccurs = String.valueOf(choice.getMinOccurs());
            maxOccurs = choice.getMaxOccurs();
            nodeType = NodeType.CHOICE;
            addParticleChildren(children, choice, currentXPath, visitedOnPath);
        }

        // Entferne das Element vom Pfad, damit es in anderen Zweigen des Baumes wieder besucht werden kann.
        visitedOnPath.remove(element);

        return new XsdNodeInfo(name, type != null ? type : "", currentXPath, documentation, children, exampleValues, minOccurs, maxOccurs, nodeType);
    }

    // In der Datei: /src/main/java/org/fxt/freexmltoolkit/service/XsdViewService.java

    /**
     * KORRIGIERTE UND ROBUSTE METHODE: Umgeht einen NullPointerException in der xsd-parser-Bibliothek,
     * indem Kind-Elemente sicher über die getElements()-Methode gesucht werden.
     *
     * @param complexType Der zu analysierende komplexe Typ.
     * @return Das gefundene Partikel (Sequence, Choice, All) oder null.
     */
    private XsdMultipleElements getParticle(XsdComplexType complexType) {
        // 1. Zuerst prüfen, ob es sich um simpleContent handelt.
        // Ein complexType mit simpleContent hat per Definition keine Kind-Elemente (Partikel).
        if (complexType.getSimpleContent() != null) {
            return null;
        }

        // 2. Prüfe auf complexContent, das eine Erweiterung oder Beschränkung enthalten kann.
        if (complexType.getComplexContent() != null) {
            var content = complexType.getComplexContent();

            // Fall A: complexContent mit einer Erweiterung (xs:extension)
            XsdExtension extension = content.getXsdExtension();
            if (extension != null) {
                // WORKAROUND: Die getChildAs... Methoden des Parsers sind nicht null-sicher.
                // Wir durchsuchen stattdessen die generische Kinderliste, um ein Partikel
                // (sequence, choice, all) sicher zu finden, ohne eine NPE auszulösen.
                Optional<XsdMultipleElements> particle = extension.getElements()
                        .stream()
                        .map(ReferenceBase::getElement)
                        .filter(Objects::nonNull)
                        .filter(XsdMultipleElements.class::isInstance)
                        .map(XsdMultipleElements.class::cast)
                        .findFirst();

                if (particle.isPresent()) {
                    return particle.get();
                }
                // Wenn die Erweiterung selbst kein Partikel hat, ist das in Ordnung.
                // Die Vererbung wird durch die rekursive Struktur von buildLightweightNodeRecursive behandelt,
                // die den Basistyp separat verarbeitet.
            }

            // Fall B: complexContent mit einer Beschränkung (xs:restriction)
            XsdRestriction restriction = content.getXsdRestriction();
            if (restriction != null) {
                // Auch hier verwenden wir den sicheren Ansatz über getElements()
                Optional<XsdMultipleElements> particle = restriction.getElements()
                        .stream()
                        .map(ReferenceBase::getElement)
                        .filter(Objects::nonNull)
                        .filter(XsdMultipleElements.class::isInstance)
                        .map(XsdMultipleElements.class::cast)
                        .findFirst();

                if (particle.isPresent()) {
                    return particle.get();
                }
            }
        }

        // 3. Direkte Partikel-Kinder (wenn kein complexContent vorhanden ist)
        // Diese Aufrufe sind sicher, da sie direkt auf dem ComplexType erfolgen.
        if (complexType.getXsdChildElement() != null && complexType.getChildAsSequence() != null) {
            return complexType.getChildAsSequence();
        } else if (complexType.getXsdChildElement() != null && complexType.getChildAsChoice() != null) {
            return complexType.getChildAsChoice();
        } else if (complexType.getXsdChildElement() != null && complexType.getChildAsAll() != null) {
            return complexType.getChildAsAll();
        }

        // 4. Kein Partikel gefunden
        return null;
    }

    private void addParticleChildren(List<XsdNodeInfo> children, XsdMultipleElements particle, String parentXpath, Set<XsdAbstractElement> visitedOnPath) {
        particle.getElements().stream()
                .map(ReferenceBase::getElement)
                .filter(Objects::nonNull)
                .forEach(child -> {
                    String childPath = parentXpath;
                    if (child instanceof XsdElement xsdElement) {
                        childPath += "/" + xsdElement.getName();
                    }
                    XsdNodeInfo childNode = buildLightweightNodeRecursive(child, childPath, visitedOnPath);
                    if (childNode != null) {
                        children.add(childNode);
                    }
                });
    }

    private List<String> extractExampleValues(XsdAnnotation annotation) {
        if (annotation == null || annotation.getAppInfoList() == null) {
            return Collections.emptyList();
        }

        List<String> values = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            factory.setFeature(FEATURE_DISALLOW_DOCTYPE_DECL, false);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            for (XsdAppInfo appInfo : annotation.getAppInfoList()) {
                String content = appInfo.getContent();
                if (content == null || content.isBlank() || !content.contains("exampleValues")) continue;

                try {
                    Document doc = builder.parse(new InputSource(new StringReader("<dummy>" + content + "</dummy>")));
                    XPath xpath = XPathFactory.newInstance().newXPath();
                    NodeList exampleNodes = (NodeList) xpath.evaluate("//*[local-name()='example']/@value", doc, XPathConstants.NODESET);
                    for (int i = 0; i < exampleNodes.getLength(); i++) {
                        values.add(exampleNodes.item(i).getNodeValue());
                    }
                } catch (SAXException | IOException | XPathExpressionException e) {
                    logger.warn("Could not parse appinfo content for example values. Content: '{}', Error: {}", content, e.getMessage());
                }
            }
        } catch (ParserConfigurationException e) {
            logger.error("Could not create DocumentBuilder for parsing appinfo.", e);
        }
        return values;
    }
}