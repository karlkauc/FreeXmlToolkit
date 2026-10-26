package org.fxt.freexmltoolkit.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The complete catalog of XSD built-in datatypes (XSD 1.0 plus the XSD 1.1 additions), grouped by
 * category and described in one sentence each, for type pickers and documentation.
 *
 * <p>Pure domain data, no UI dependencies. Names are stored without a namespace prefix; callers
 * qualify them with the prefix their schema binds to {@code http://www.w3.org/2001/XMLSchema}
 * via {@link BuiltinType#qualified(String)}.
 */
public final class XsdBuiltinTypeCatalog {

    /** The XML Schema namespace every built-in type lives in. */
    public static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";

    /** Coarse grouping of the built-in types, in display order. */
    public enum Category {
        ANY("Any"),
        STRING("String"),
        NUMERIC("Numeric"),
        DATE_TIME("Date & Time"),
        BINARY("Binary"),
        BOOLEAN("Boolean"),
        URI_NAMES("URI & Names"),
        IDENTITY("IDs & References");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        /** @return the human-readable group title. */
        public String displayName() {
            return displayName;
        }
    }

    /**
     * One built-in type.
     *
     * @param localName      the type name without prefix, e.g. {@code dateTimeStamp}
     * @param category       the display group
     * @param baseType       the local name of the type it derives from ({@code null} for {@code anyType})
     * @param description    one sentence explaining the value space
     * @param example        an example literal (may contain a short hint in parentheses)
     * @param xsd11Only      {@code true} for types introduced by XSD 1.1
     * @param listType       {@code true} for the built-in list types ({@code NMTOKENS}, {@code IDREFS}, {@code ENTITIES})
     * @param limitedSupport {@code true} when common processors (Xerces) do not implement the type
     */
    public record BuiltinType(String localName, Category category, String baseType, String description,
                              String example, boolean xsd11Only, boolean listType, boolean limitedSupport) {

        /** @return the name qualified with {@code prefix} ({@code xs:string}); the bare name for a blank prefix. */
        public String qualified(String prefix) {
            return prefix == null || prefix.isBlank() ? localName : prefix + ":" + localName;
        }
    }

    private static final Map<String, BuiltinType> BY_NAME = new LinkedHashMap<>();
    private static final Map<Category, List<BuiltinType>> BY_CATEGORY = new EnumMap<>(Category.class);

    static {
        // --- Any ------------------------------------------------------------------------
        add("anyType", Category.ANY, null,
                "The root of the type hierarchy: any element content, simple or complex, with any attributes.",
                "<anything/>");
        add("anySimpleType", Category.ANY, "anyType",
                "Any simple (text-only) value; the base of all simple types.", "some text");
        add("anyAtomicType", Category.ANY, "anySimpleType",
                "Any single atomic value: the base of all primitive types (XSD 1.1 and XPath 2.0+).", "42");

        // --- String -----------------------------------------------------------------------
        add("string", Category.STRING, "anyAtomicType",
                "Any sequence of characters; whitespace is preserved exactly as written.", "Hello World");
        add("normalizedString", Category.STRING, "string",
                "A string in which tabs, line feeds and carriage returns are replaced by spaces.", "Hello World");
        add("token", Category.STRING, "normalizedString",
                "A normalized string without leading, trailing or repeated spaces.", "Hello World");
        add("language", Category.STRING, "token",
                "A natural-language tag as defined by BCP 47, e.g. a language and an optional region.", "en-US");
        add("Name", Category.STRING, "token",
                "An XML name: letters, digits, '.', '-', '_' and ':' , not starting with a digit.", "part:Number");
        add("NCName", Category.STRING, "Name",
                "A 'non-colonized' XML name, i.e. a Name without a colon; used for local names.", "partNumber");
        add("NMTOKEN", Category.STRING, "token",
                "A name token: one or more name characters without any whitespace.", "US-EN");
        list("NMTOKENS", Category.STRING, "NMTOKEN",
                "A space-separated list of one or more NMTOKEN values.", "US-EN DE-DE FR-FR");

        // --- Numeric ----------------------------------------------------------------------
        add("decimal", Category.NUMERIC, "anyAtomicType",
                "An arbitrary-precision decimal number, written with an optional sign and fraction.", "-123.45");
        add("integer", Category.NUMERIC, "decimal",
                "A whole number of arbitrary size, without a fractional part.", "-123456789012345678");
        add("nonPositiveInteger", Category.NUMERIC, "integer",
                "An integer that is zero or negative.", "-42");
        add("negativeInteger", Category.NUMERIC, "nonPositiveInteger",
                "An integer strictly less than zero.", "-1");
        add("nonNegativeInteger", Category.NUMERIC, "integer",
                "An integer that is zero or positive.", "0");
        add("positiveInteger", Category.NUMERIC, "nonNegativeInteger",
                "An integer strictly greater than zero.", "1");
        add("long", Category.NUMERIC, "integer",
                "A signed 64-bit integer, from -9223372036854775808 to 9223372036854775807.", "9223372036854775807");
        add("int", Category.NUMERIC, "long",
                "A signed 32-bit integer, from -2147483648 to 2147483647.", "2147483647");
        add("short", Category.NUMERIC, "int",
                "A signed 16-bit integer, from -32768 to 32767.", "32767");
        add("byte", Category.NUMERIC, "short",
                "A signed 8-bit integer, from -128 to 127.", "127");
        add("unsignedLong", Category.NUMERIC, "nonNegativeInteger",
                "An unsigned 64-bit integer, from 0 to 18446744073709551615.", "18446744073709551615");
        add("unsignedInt", Category.NUMERIC, "unsignedLong",
                "An unsigned 32-bit integer, from 0 to 4294967295.", "4294967295");
        add("unsignedShort", Category.NUMERIC, "unsignedInt",
                "An unsigned 16-bit integer, from 0 to 65535.", "65535");
        add("unsignedByte", Category.NUMERIC, "unsignedShort",
                "An unsigned 8-bit integer, from 0 to 255.", "255");
        add("float", Category.NUMERIC, "anyAtomicType",
                "An IEEE single-precision (32-bit) floating-point number, including INF, -INF and NaN.", "3.14E2");
        add("double", Category.NUMERIC, "anyAtomicType",
                "An IEEE double-precision (64-bit) floating-point number, including INF, -INF and NaN.", "3.14159E0");
        BY_NAME.put("precisionDecimal", new BuiltinType("precisionDecimal", Category.NUMERIC, "anyAtomicType",
                "A decimal value with arbitrary precision in which trailing zeros are significant "
                        + "(XSD 1.1; not implemented by Xerces and most validators).",
                "123.4500 (exactly 4 decimal places)", true, false, true));

        // --- Date & Time ------------------------------------------------------------------
        add("dateTime", Category.DATE_TIME, "anyAtomicType",
                "A date with a time of day in ISO 8601 form; the timezone part is optional.", "2024-01-15T14:30:00");
        xsd11("dateTimeStamp", Category.DATE_TIME, "dateTime",
                "A dateTime value that requires a timezone indicator; unlike dateTime the timezone is mandatory.",
                "2024-01-15T14:30:00+01:00");
        add("date", Category.DATE_TIME, "anyAtomicType",
                "A calendar date (year, month, day) with an optional timezone.", "2024-01-15");
        add("time", Category.DATE_TIME, "anyAtomicType",
                "A time of day (hours, minutes, seconds) with an optional timezone.", "14:30:00");
        add("duration", Category.DATE_TIME, "anyAtomicType",
                "A length of time in years, months, days, hours, minutes and seconds (ISO 8601).", "P1Y2M3DT10H30M");
        xsd11("yearMonthDuration", Category.DATE_TIME, "duration",
                "A duration with only year and month components; day and time components are not allowed.",
                "P2Y3M (2 years, 3 months)");
        xsd11("dayTimeDuration", Category.DATE_TIME, "duration",
                "A duration with only day, hour, minute, and second components; years and months are not allowed.",
                "P1DT12H30M (1 day, 12 hours, 30 minutes)");
        add("gYear", Category.DATE_TIME, "anyAtomicType",
                "A Gregorian calendar year.", "2024");
        add("gYearMonth", Category.DATE_TIME, "anyAtomicType",
                "A specific month of a specific year.", "2024-01");
        add("gMonth", Category.DATE_TIME, "anyAtomicType",
                "A recurring Gregorian month, without a year.", "--01");
        add("gMonthDay", Category.DATE_TIME, "anyAtomicType",
                "A recurring day of a month, without a year (e.g. a birthday).", "--12-24");
        add("gDay", Category.DATE_TIME, "anyAtomicType",
                "A recurring day of the month, without month and year.", "---15");

        // --- Binary -----------------------------------------------------------------------
        add("hexBinary", Category.BINARY, "anyAtomicType",
                "Binary data written as hexadecimal digits, two per byte.", "0FB7");
        add("base64Binary", Category.BINARY, "anyAtomicType",
                "Binary data written in Base64 encoding.", "SGVsbG8=");

        // --- Boolean ----------------------------------------------------------------------
        add("boolean", Category.BOOLEAN, "anyAtomicType",
                "A truth value: true, false, 1 or 0.", "true");

        // --- URI & Names ------------------------------------------------------------------
        add("anyURI", Category.URI_NAMES, "anyAtomicType",
                "A Uniform Resource Identifier reference, absolute or relative.", "https://example.com/doc#top");
        add("QName", Category.URI_NAMES, "anyAtomicType",
                "An XML qualified name: an optional namespace prefix in scope plus a local name.", "xs:element");
        add("NOTATION", Category.URI_NAMES, "anyAtomicType",
                "A reference to a notation declared in the schema; only usable as the base of an enumeration.",
                "jpeg");

        // --- IDs & References -------------------------------------------------------------
        add("ID", Category.IDENTITY, "NCName",
                "A unique identifier within the document; no two ID values may be equal.", "item-42");
        add("IDREF", Category.IDENTITY, "NCName",
                "A reference to an ID value that occurs somewhere in the same document.", "item-42");
        list("IDREFS", Category.IDENTITY, "IDREF",
                "A space-separated list of one or more IDREF values.", "item-42 item-43");
        add("ENTITY", Category.IDENTITY, "NCName",
                "The name of an unparsed entity declared in the document's DTD.", "logo");
        list("ENTITIES", Category.IDENTITY, "ENTITY",
                "A space-separated list of one or more ENTITY values.", "logo banner");

        for (BuiltinType type : BY_NAME.values()) {
            BY_CATEGORY.computeIfAbsent(type.category(), c -> new ArrayList<>()).add(type);
        }
        BY_CATEGORY.replaceAll((c, l) -> Collections.unmodifiableList(l));
    }

    private XsdBuiltinTypeCatalog() {
    }

    private static void add(String name, Category category, String base, String description, String example) {
        BY_NAME.put(name, new BuiltinType(name, category, base, description, example, false, false, false));
    }

    private static void list(String name, Category category, String itemType, String description, String example) {
        BY_NAME.put(name, new BuiltinType(name, category, itemType, description, example, false, true, false));
    }

    private static void xsd11(String name, Category category, String base, String description, String example) {
        BY_NAME.put(name, new BuiltinType(name, category, base, description, example, true, false, false));
    }

    /** @return every built-in type, grouped by {@link Category} order and in specification order within a group. */
    public static List<BuiltinType> all() {
        List<BuiltinType> all = new ArrayList<>();
        BY_CATEGORY.values().forEach(all::addAll);
        return Collections.unmodifiableList(all);
    }

    /** @return the built-in types per category, categories in display order. */
    public static Map<Category, List<BuiltinType>> byCategory() {
        return Collections.unmodifiableMap(BY_CATEGORY);
    }

    /** @return the local names of all built-in types, in {@link #all()} order. */
    public static List<String> localNames() {
        return all().stream().map(BuiltinType::localName).toList();
    }

    /**
     * Looks a type up by name, ignoring any namespace prefix ({@code xs:int}, {@code xsd:int} and
     * {@code int} all resolve to the same entry).
     *
     * @param nameOrQName the type name, optionally prefixed; {@code null}/blank yields empty
     */
    public static Optional<BuiltinType> find(String nameOrQName) {
        if (nameOrQName == null || nameOrQName.isBlank()) {
            return Optional.empty();
        }
        String name = nameOrQName.trim();
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        return Optional.ofNullable(BY_NAME.get(name));
    }

    /** @return whether {@code nameOrQName} names a built-in type (prefix ignored). */
    public static boolean isBuiltin(String nameOrQName) {
        return find(nameOrQName).isPresent();
    }
}
