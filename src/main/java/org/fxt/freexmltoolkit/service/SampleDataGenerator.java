package org.fxt.freexmltoolkit.service;

import com.mifmif.common.regex.Generex;
import jakarta.xml.bind.DatatypeConverter;
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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
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
            // Spezifischere String-Typen für bessere Beispiele
            case "string", "token", "normalizedstring", "name" ->
                    generateStringSample(restriction);
            case "language" -> "en-US";
            case "ncname" -> "exampleName1";

            // Numerische Typen mit zufälligen Werten, formatiert nach XML-Standard
            case "decimal" -> {
                double randomDouble = java.util.concurrent.ThreadLocalRandom.current().nextDouble(1.0, 1000.0);
                // BigDecimal verwenden für präzise Dezimaldarstellung
                BigDecimal bd = BigDecimal.valueOf(randomDouble).setScale(2, RoundingMode.HALF_UP);
                yield DatatypeConverter.printDecimal(bd);
            }
            case "integer", "positiveinteger", "nonnegativeinteger" -> {
                int randomInt = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 10000);
                yield DatatypeConverter.printInteger(BigInteger.valueOf(randomInt));
            }
            case "negativeinteger", "nonpositiveinteger" -> {
                int randomInt = java.util.concurrent.ThreadLocalRandom.current().nextInt(-10000, -1);
                yield DatatypeConverter.printInteger(BigInteger.valueOf(randomInt));
            }
            // Aufgetrennt für präzisere Generierung und korrekte Konvertierung
            case "long", "unsignedlong" -> // unsignedLong kann größer sein, aber für Beispiele ist long ok
                    DatatypeConverter.printLong(java.util.concurrent.ThreadLocalRandom.current().nextLong(10000, 50000));
            case "int", "unsignedint" ->
                    DatatypeConverter.printInt(java.util.concurrent.ThreadLocalRandom.current().nextInt(100, 5000));
            case "short", "unsignedshort" ->
                    DatatypeConverter.printShort((short) java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 100));
            case "byte", "unsignedbyte" ->
                    DatatypeConverter.printByte((byte) java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 127));


            // Datums-/Zeit-Typen mit zufälligen Werten
            case "date" -> {
                long minDay = LocalDate.of(2020, 1, 1).toEpochDay();
                long maxDay = LocalDate.now().toEpochDay();
                long randomDay = java.util.concurrent.ThreadLocalRandom.current().nextLong(minDay, maxDay);
                yield LocalDate.ofEpochDay(randomDay).format(DateTimeFormatter.ISO_LOCAL_DATE);
            }
            case "datetime" -> {
                LocalDateTime start = LocalDateTime.now().minusYears(1);
                long startSeconds = start.toEpochSecond(java.time.ZoneOffset.UTC);
                long endSeconds = LocalDateTime.now().toEpochSecond(java.time.ZoneOffset.UTC);
                long randomSeconds = java.util.concurrent.ThreadLocalRandom.current().nextLong(startSeconds, endSeconds);
                yield LocalDateTime.ofEpochSecond(randomSeconds, 0, java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME);
            }
            case "time" -> LocalTime.of(
                    java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 24),
                    java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 60),
                    java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 60)
            ).format(DateTimeFormatter.ISO_LOCAL_TIME);
            case "gyear" -> String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextInt(1990, LocalDate.now().getYear() + 1));

            // Boolean mit zufälligem Wert
            case "boolean" -> String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextBoolean());

            default -> {
                // Rekursiver Versuch für abgeleitete Typen
                if (restriction != null && restriction.getBase() != null) {
                    ExtendedXsdElement tempElement = new ExtendedXsdElement();
                    tempElement.setElementType(restriction.getBase());
                    tempElement.setXsdRestriction(restriction);
                    yield generateRecursive(tempElement, recursionDepth + 1);
                }
                yield ""; // Fallback für unbekannte einfache Typen
            }
        };
    }

    private String generateStringSample(XsdRestriction restriction) {
        // Ein allgemeinerer Basis-String
        String sample = "ExampleText";

        if (restriction != null) {
            // Genaue Länge hat höchste Priorität
            if (restriction.getLength() != null) {
                int length = restriction.getLength().getValue();
                return "a".repeat(Math.max(0, length));
            }

            Integer min = (restriction.getMinLength() != null) ? restriction.getMinLength().getValue() : null;
            Integer max = (restriction.getMaxLength() != null) ? restriction.getMaxLength().getValue() : null;

            // Wenn nur minLength definiert ist, erstelle einen String dieser Länge
            if (min != null && max == null) {
                return "a".repeat(min);
            }

            // Wenn nur maxLength definiert ist, kürze den Beispiel-String bei Bedarf
            if (min == null && max != null) {
                return sample.length() > max ? sample.substring(0, max) : sample;
            }

            // Wenn beide definiert sind, erstelle einen String mit einer zufälligen Länge im erlaubten Bereich
            if (min != null && max != null) {
                // Stelle sicher, dass min nicht größer als max ist
                if (min > max) {
                    logger.warn("minLenth ({}) is greater than maxLength ({}) for a string restriction. Using minLength.", min, max);
                    return "a".repeat(min);
                }
                int targetLength = (min.equals(max)) ? min : java.util.concurrent.ThreadLocalRandom.current().nextInt(min, max + 1);
                return "a".repeat(targetLength);
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