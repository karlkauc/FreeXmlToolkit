package org.fxt.freexmltoolkit.service;

import com.mifmif.common.regex.Generex;
import jakarta.xml.bind.DatatypeConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement.RestrictionInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Generiert Beispieldaten für XSD-Elemente basierend auf deren Typ,
 * Einschränkungen und Annotationen. Diese Version ist unabhängig von xmlet.xsdparser.
 */
public class XsdSampleDataGenerator {

    private static final Logger logger = LogManager.getLogger(XsdSampleDataGenerator.class);
    private static final int MAX_RECURSION_DEPTH = 10;

    /**
     * Öffentliche Methode zum Starten der Datengenerierung für ein Element.
     *
     * @param element Das Element, für das Daten generiert werden sollen.
     * @return Ein String mit den passenden Beispieldaten.
     */
    public String generate(XsdExtendedElement element) {
        return generateRecursive(element, 0);
    }

    /**
     * Generiert Beispieldaten für ein gegebenes XSD-Element unter Berücksichtigung
     * von Datentypen und Einschränkungen.
     */
    private String generateRecursive(XsdExtendedElement element, int recursionDepth) {
        if (element == null || recursionDepth > MAX_RECURSION_DEPTH) {
            return "";
        }

        RestrictionInfo restriction = element.getRestrictionInfo();

        // Priorität 2: Enumerations (Auswahllisten)
        if (restriction != null && restriction.facets().containsKey("enumeration")) {
            List<String> enumerations = restriction.facets().get("enumeration");
            if (enumerations != null && !enumerations.isEmpty()) {
                int randomIndex = java.util.concurrent.ThreadLocalRandom.current().nextInt(enumerations.size());
                return enumerations.get(randomIndex);
            }
        }

        // Priorität 3: Regex-Pattern
        if (restriction != null && restriction.facets().containsKey("pattern")) {
            String patternValue = restriction.facets().get("pattern").stream().findFirst().orElse(null);
            if (patternValue != null) {
                try {
                    // map java regex to XML regex syntax
                    // ^ $ ???
                    Generex generex = new Generex(patternValue);
                    return generex.random();
                } catch (Exception e) {
                    logger.warn("Could not generate sample from pattern '{}' for element '{}'. Falling back. Error: {}",
                            patternValue, element.getElementName(), e.getMessage());
                }
            }
        }

        // Priorität 4: Datentyp-basierte Generierung
        String elementType = element.getElementType();
        if (elementType == null && restriction != null) {
            elementType = restriction.base();
        }

        // Für komplexe Typen mit Kind-Elementen keinen Textinhalt generieren
        if (elementType == null || element.hasChildren()) {
            return "";
        }

        String finalType = elementType.substring(elementType.lastIndexOf(":") + 1);

        return switch (finalType.toLowerCase()) {
            // Spezifischere String-Typen für bessere Beispiele
            case "string", "token", "normalizedstring", "name" -> generateStringSample(restriction);
            case "language" -> "en-US";
            case "ncname" -> "exampleName1";

            // Numerische Typen, die jetzt die neue Hilfsmethode verwenden
            case "decimal" -> {
                BigDecimal randomDecimal = generateNumberInRange(restriction, new BigDecimal("1.00"), new BigDecimal("1000.00"));
                yield DatatypeConverter.printDecimal(randomDecimal.setScale(2, RoundingMode.HALF_UP));
            }
            case "integer", "positiveinteger", "nonnegativeinteger" -> {
                BigDecimal randomDecimal = generateNumberInRange(restriction, BigDecimal.ONE, new BigDecimal("10000"));
                yield DatatypeConverter.printInteger(randomDecimal.toBigInteger());
            }
            case "negativeinteger", "nonpositiveinteger" -> {
                BigDecimal randomDecimal = generateNumberInRange(restriction, new BigDecimal("-10000"), BigDecimal.valueOf(-1));
                yield DatatypeConverter.printInteger(randomDecimal.toBigInteger());
            }
            case "long", "unsignedlong" -> {
                BigDecimal randomDecimal = generateNumberInRange(restriction, new BigDecimal("10000"), new BigDecimal("50000"));
                yield DatatypeConverter.printLong(randomDecimal.longValue());
            }
            case "int", "unsignedint" -> {
                BigDecimal randomDecimal = generateNumberInRange(restriction, new BigDecimal("100"), new BigDecimal("5000"));
                yield DatatypeConverter.printInt(randomDecimal.intValue());
            }
            case "short", "unsignedshort" -> {
                BigDecimal randomDecimal = generateNumberInRange(restriction, BigDecimal.ONE, new BigDecimal("100"));
                yield DatatypeConverter.printShort(randomDecimal.shortValue());
            }
            case "byte", "unsignedbyte" -> {
                BigDecimal randomDecimal = generateNumberInRange(restriction, BigDecimal.ZERO, new BigDecimal("127"));
                yield DatatypeConverter.printByte(randomDecimal.byteValue());
            }

            // Datum, Zeit und Boolean
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
            case "gyear" ->
                    String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextInt(1990, LocalDate.now().getYear() + 1));
            case "boolean" -> String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextBoolean());

            default -> {
                // Rekursiver Versuch für abgeleitete Typen
                if (restriction != null && restriction.base() != null) {
                    XsdExtendedElement tempElement = new XsdExtendedElement();
                    tempElement.setElementType(restriction.base());
                    tempElement.setRestrictionInfo(restriction);
                    yield generateRecursive(tempElement, recursionDepth + 1);
                }
                yield ""; // Fallback für unbekannte einfache Typen
            }
        };
    }

    private String generateStringSample(RestrictionInfo restriction) {
        String sample = "ExampleText";

        if (restriction != null) {
            Map<String, List<String>> facets = restriction.facets();
            // Genaue Länge hat höchste Priorität
            if (facets.containsKey("length")) {
                try {
                    int length = Integer.parseInt(facets.get("length").getFirst());
                    return "a".repeat(Math.max(0, length));
                } catch (NumberFormatException e) {
                    logger.warn("Could not parse length value: {}", facets.get("length").getFirst());
                }
            }

            Integer min = facets.containsKey("minLength") ? Integer.parseInt(facets.get("minLength").getFirst()) : null;
            Integer max = facets.containsKey("maxLength") ? Integer.parseInt(facets.get("maxLength").getFirst()) : null;

            if (min != null && max == null) {
                return "a".repeat(min);
            }
            if (min == null && max != null) {
                return sample.length() > max ? sample.substring(0, max) : sample;
            }
            if (min != null) {
                if (min > max) {
                    logger.warn("minLength ({}) is greater than maxLength ({}) for a string restriction. Using minLength.", min, max);
                    return "a".repeat(min);
                }
                int targetLength = (min.equals(max)) ? min : java.util.concurrent.ThreadLocalRandom.current().nextInt(min, max + 1);
                return "a".repeat(targetLength);
            }
        }
        return sample;
    }

    /**
     * Generiert eine Zufallszahl (als BigDecimal) unter Berücksichtigung von
     * min/max-Einschränkungen aus dem XSD.
     */
    private BigDecimal generateNumberInRange(RestrictionInfo restriction, BigDecimal defaultMin, BigDecimal defaultMax) {
        BigDecimal min = defaultMin;
        BigDecimal max = defaultMax;

        if (restriction != null) {
            Map<String, List<String>> facets = restriction.facets();
            if (facets.containsKey("minInclusive")) {
                try {
                    min = new BigDecimal(facets.get("minInclusive").getFirst());
                } catch (NumberFormatException e) { /* ignore */ }
            } else if (facets.containsKey("minExclusive")) {
                try {
                    min = new BigDecimal(facets.get("minExclusive").getFirst()).add(BigDecimal.ONE);
                } catch (NumberFormatException e) { /* ignore */ }
            }

            if (facets.containsKey("maxInclusive")) {
                try {
                    max = new BigDecimal(facets.get("maxInclusive").getFirst());
                } catch (NumberFormatException e) { /* ignore */ }
            } else if (facets.containsKey("maxExclusive")) {
                try {
                    max = new BigDecimal(facets.get("maxExclusive").getFirst()).subtract(BigDecimal.ONE);
                } catch (NumberFormatException e) { /* ignore */ }
            }
        }

        if (min.compareTo(max) > 0) {
            logger.warn("Min value {} is greater than max value {}. Using max value for generation.", min, max);
            return max;
        }
        if (min.compareTo(max) == 0) {
            return min;
        }

        BigDecimal range = max.subtract(min);
        BigDecimal randomValue = min.add(range.multiply(BigDecimal.valueOf(Math.random())));
        return randomValue;
    }
}