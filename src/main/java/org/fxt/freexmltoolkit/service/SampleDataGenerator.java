package org.fxt.freexmltoolkit.service;

import com.mifmif.common.regex.Generex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.ExtendedXsdElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xmlet.xsdparser.xsdelements.XsdAppInfo;
import org.xmlet.xsdparser.xsdelements.XsdRestriction;
import org.xmlet.xsdparser.xsdelements.xsdrestrictions.XsdEnumeration;
import org.xmlet.xsdparser.xsdelements.xsdrestrictions.XsdPattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generiert Beispieldaten für XSD-Elemente basierend auf deren Typ,
 * Einschränkungen und Annotationen.
 */
public class SampleDataGenerator {

    private static final Logger logger = LogManager.getLogger(SampleDataGenerator.class);
    private static final int MAX_RECURSION_DEPTH = 10;

    /**
     * Öffentliche Methode zum Starten der Datengenerierung für ein Element.
     *
     * @param element Das Element, für das Daten generiert werden sollen.
     * @return Ein String mit den passenden Beispieldaten.
     */
    public String generate(ExtendedXsdElement element) {
        return generateRecursive(element, 0);
    }

    /**
     * Generiert Beispieldaten für ein gegebenes XSD-Element unter Berücksichtigung
     * von Datentypen und Einschränkungen.
     */
    private String generateRecursive(ExtendedXsdElement element, int recursionDepth) {
        if (element == null || recursionDepth > MAX_RECURSION_DEPTH) {
            return "";
        }

        // Priorität 1: AppInfo mit Altova-Beispieldaten
        if (element.getXsdElement() != null && element.getXsdElement().getAnnotation() != null) {
            List<XsdAppInfo> appInfoList = element.getXsdElement().getAnnotation().getAppInfoList();
            if (appInfoList != null) {
                for (XsdAppInfo appInfo : appInfoList) {
                    String sampleValue = extractAltovaSample(appInfo);
                    if (sampleValue != null) {
                        logger.debug("Found sample data in appinfo for {}: {}", element.getElementName(), sampleValue);
                        return sampleValue;
                    }
                }
            }
        }

        XsdRestriction restriction = element.getXsdRestriction();

        // Priorität 2: Enumerations (Auswahllisten)
        if (restriction != null && restriction.getEnumeration() != null && !restriction.getEnumeration().isEmpty()) {
            List<XsdEnumeration> enumerations = restriction.getEnumeration();
            int randomIndex = java.util.concurrent.ThreadLocalRandom.current().nextInt(enumerations.size());
            return enumerations.get(randomIndex).getValue();
        }

        // NEU: Priorität 3: Regex-Pattern
        if (restriction != null && restriction.getPattern() != null) {
            XsdPattern pattern = restriction.getPattern();

            if (pattern != null && pattern.getValue() != null) {
                try {
                    Generex generex = new Generex(pattern.getValue());
                    return generex.random();
                } catch (Exception e) {
                    logger.warn("Could not generate sample from pattern '{}' for element '{}'. Falling back. Error: {}",
                            pattern.getValue(), element.getElementName(), e.getMessage());
                    // Bei Fehler wird zum nächsten Mechanismus übergegangen (Fallback)
                }
            }
        }

        // Priorität 4: Datentyp-basierte Generierung
        String elementType = element.getElementType();
        if (elementType == null && restriction != null) {
            elementType = restriction.getBase();
        }

        if (elementType == null || (element.getXsdElement() != null && element.getXsdElement().getXsdComplexType() != null)) {
            return "..."; // Für komplexe Typen ohne einfachen Inhalt
        }

        String finalType = elementType.substring(elementType.lastIndexOf(":") + 1);

        return switch (finalType.toLowerCase()) {
            case "string", "token", "normalizedstring", "language", "name", "ncname" ->
                    generateStringSample(restriction);
            case "decimal" -> "123.45";
            case "integer", "positiveinteger", "nonnegativeinteger", "negativeinteger", "nonpositiveinteger", "long",
                 "int", "short", "byte", "unsignedlong", "unsignedint", "unsignedshort", "unsignedbyte" -> "100";
            case "date" -> LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "datetime" -> LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            case "time" -> LocalTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME);
            case "gyear" -> String.valueOf(LocalDate.now().getYear());
            case "boolean" -> "true";
            default -> {
                // Rekursiver Versuch für abgeleitete Typen
                if (restriction != null && restriction.getBase() != null) {
                    ExtendedXsdElement tempElement = new ExtendedXsdElement();
                    tempElement.setElementType(restriction.getBase());
                    tempElement.setXsdRestriction(restriction);
                    yield generateRecursive(tempElement, recursionDepth + 1);
                }
                yield "";
            }
        };
    }

    private String generateStringSample(XsdRestriction restriction) {
        String sample = "Sample Data";
        if (restriction != null) {
            if (restriction.getLength() != null) {
                return "x".repeat(Math.max(0, restriction.getLength().getValue()));
            }
            if (restriction.getMinLength() != null) {
                int min = restriction.getMinLength().getValue();
                while (sample.length() < min) sample += " Text";
            }
            if (restriction.getMaxLength() != null) {
                int max = restriction.getMaxLength().getValue();
                if (sample.length() > max) sample = sample.substring(0, max);
            }
        }
        return sample;
    }

    private String extractAltovaSample(XsdAppInfo appInfo) {
        if (appInfo.getContent() == null) return null;

        Document appInfoDoc = convertStringToDocument(appInfo.getContent());
        if (appInfoDoc == null) return null;

        Element root = appInfoDoc.getDocumentElement();
        if (root != null && "altova:exampleValues".equals(root.getNodeName())) {
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE && "altova:example".equals(child.getNodeName())) {
                    Element exampleElement = (Element) child;
                    if (exampleElement.hasAttribute("value")) {
                        String value = exampleElement.getAttribute("value");
                        if (!value.isBlank()) return value;
                    }
                }
            }
        }
        return null;
    }

    private Document convertStringToDocument(String xmlStr) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // VERBESSERUNG: Namespace-Awareness aktivieren, um mit Prefixen wie "altova:" korrekt umzugehen.
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xmlStr)));
        } catch (Exception e) {
            logger.error("Error converting string to XML document: {}", e.getMessage());
            return null;
        }
    }
}