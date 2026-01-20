package org.fxt.freexmltoolkit.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Reference for XSD 1.1 built-in types and their properties.
 * Provides information about new types introduced in XSD 1.1.
 */
public class Xsd11BuiltinTypes {

    /**
     * Information about a built-in type
     * @param name The type name
     * @param baseType The base type name
     * @param description The type description
     * @param example An example value
     * @param isXsd11Only Whether the type is XSD 1.1 only
     */
    public record TypeInfo(
            String name,
            String baseType,
            String description,
            String example,
            boolean isXsd11Only
    ) {
    }

    private static final Map<String, TypeInfo> BUILTIN_TYPES = new HashMap<>();

    static {
        // XSD 1.1 specific types
        BUILTIN_TYPES.put("xs:dateTimeStamp", new TypeInfo(
                "xs:dateTimeStamp",
                "xs:dateTime",
                "A dateTime value that requires a timezone indicator. " +
                        "Unlike xs:dateTime, the timezone is mandatory.",
                "2024-01-15T14:30:00+01:00",
                true
        ));

        BUILTIN_TYPES.put("xs:yearMonthDuration", new TypeInfo(
                "xs:yearMonthDuration",
                "xs:duration",
                "A duration value with only year and month components. " +
                        "Day, hour, minute, and second components are not allowed.",
                "P2Y3M (2 years, 3 months)",
                true
        ));

        BUILTIN_TYPES.put("xs:dayTimeDuration", new TypeInfo(
                "xs:dayTimeDuration",
                "xs:duration",
                "A duration value with only day, hour, minute, and second components. " +
                        "Year and month components are not allowed.",
                "P1DT12H30M (1 day, 12 hours, 30 minutes)",
                true
        ));

        BUILTIN_TYPES.put("xs:precisionDecimal", new TypeInfo(
                "xs:precisionDecimal",
                "xs:decimal",
                "A decimal value with arbitrary precision. " +
                        "Unlike xs:decimal, trailing zeros are significant.",
                "123.4500 (exactly 4 decimal places)",
                true
        ));

        // Add XSD 1.0 types that are commonly used (for reference)
        addXsd10CommonTypes();
    }

    /**
     * Adds commonly used XSD 1.0 types for reference
     */
    private static void addXsd10CommonTypes() {
        BUILTIN_TYPES.put("xs:string", new TypeInfo(
                "xs:string",
                "xs:anySimpleType",
                "Character string",
                "Hello World",
                false
        ));

        BUILTIN_TYPES.put("xs:boolean", new TypeInfo(
                "xs:boolean",
                "xs:anySimpleType",
                "Boolean value",
                "true or false",
                false
        ));

        BUILTIN_TYPES.put("xs:decimal", new TypeInfo(
                "xs:decimal",
                "xs:anySimpleType",
                "Decimal number",
                "123.45",
                false
        ));

        BUILTIN_TYPES.put("xs:integer", new TypeInfo(
                "xs:integer",
                "xs:decimal",
                "Integer number",
                "42",
                false
        ));

        BUILTIN_TYPES.put("xs:int", new TypeInfo(
                "xs:int",
                "xs:long",
                "32-bit integer",
                "2147483647",
                false
        ));

        BUILTIN_TYPES.put("xs:long", new TypeInfo(
                "xs:long",
                "xs:integer",
                "64-bit integer",
                "9223372036854775807",
                false
        ));

        BUILTIN_TYPES.put("xs:double", new TypeInfo(
                "xs:double",
                "xs:anySimpleType",
                "Double-precision floating-point",
                "3.14159",
                false
        ));

        BUILTIN_TYPES.put("xs:float", new TypeInfo(
                "xs:float",
                "xs:anySimpleType",
                "Single-precision floating-point",
                "3.14",
                false
        ));

        BUILTIN_TYPES.put("xs:date", new TypeInfo(
                "xs:date",
                "xs:anySimpleType",
                "Calendar date",
                "2024-01-15",
                false
        ));

        BUILTIN_TYPES.put("xs:time", new TypeInfo(
                "xs:time",
                "xs:anySimpleType",
                "Time of day",
                "14:30:00",
                false
        ));

        BUILTIN_TYPES.put("xs:dateTime", new TypeInfo(
                "xs:dateTime",
                "xs:anySimpleType",
                "Date and time (timezone optional)",
                "2024-01-15T14:30:00",
                false
        ));

        BUILTIN_TYPES.put("xs:duration", new TypeInfo(
                "xs:duration",
                "xs:anySimpleType",
                "Duration of time",
                "P1Y2M3DT10H30M",
                false
        ));

        BUILTIN_TYPES.put("xs:anyURI", new TypeInfo(
                "xs:anyURI",
                "xs:anySimpleType",
                "URI reference",
                "https://example.com",
                false
        ));

        BUILTIN_TYPES.put("xs:QName", new TypeInfo(
                "xs:QName",
                "xs:anySimpleType",
                "Qualified name",
                "xs:element",
                false
        ));
    }

    /**
     * Returns information about a built-in type
     *
     * @param typeName The type name (with or without xs: prefix)
     * @return TypeInfo or null if not found
     */
    public static TypeInfo getTypeInfo(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return null;
        }

        // Normalize type name (add xs: prefix if missing)
        String normalizedName = typeName;
        if (!typeName.startsWith("xs:") && !typeName.startsWith("xsd:")) {
            normalizedName = "xs:" + typeName;
        }

        return BUILTIN_TYPES.get(normalizedName);
    }

    /**
     * Checks if a type is an XSD 1.1 specific type
     */
    public static boolean isXsd11Type(String typeName) {
        TypeInfo info = getTypeInfo(typeName);
        return info != null && info.isXsd11Only();
    }

    /**
     * Returns all XSD 1.1 specific type names
     */
    public static Set<String> getXsd11TypeNames() {
        return Set.of(
                "xs:dateTimeStamp",
                "xs:yearMonthDuration",
                "xs:dayTimeDuration",
                "xs:precisionDecimal"
        );
    }

    /**
     * Returns all known built-in type names
     */
    public static Set<String> getAllTypeNames() {
        return BUILTIN_TYPES.keySet();
    }

    /**
     * Generates documentation for a built-in type
     */
    public static String generateDocumentation(String typeName) {
        TypeInfo info = getTypeInfo(typeName);
        if (info == null) {
            return "Unknown type: " + typeName;
        }

        StringBuilder doc = new StringBuilder();
        doc.append("Type: ").append(info.name()).append("\n");
        doc.append("Base Type: ").append(info.baseType()).append("\n");
        if (info.isXsd11Only()) {
            doc.append("XSD Version: 1.1 only\n");
        } else {
            doc.append("XSD Version: 1.0+\n");
        }
        doc.append("\nDescription:\n");
        doc.append(info.description()).append("\n");
        doc.append("\nExample:\n");
        doc.append(info.example()).append("\n");

        return doc.toString();
    }

    /**
     * Validates if a type name is a known built-in type
     */
    public static boolean isBuiltinType(String typeName) {
        return getTypeInfo(typeName) != null;
    }

    /**
     * Returns a formatted list of all XSD 1.1 types
     */
    public static String getXsd11TypesList() {
        StringBuilder list = new StringBuilder();
        list.append("XSD 1.1 New Built-in Types:\n\n");

        for (String typeName : getXsd11TypeNames()) {
            TypeInfo info = BUILTIN_TYPES.get(typeName);
            list.append(String.format("• %s\n  %s\n  Example: %s\n\n",
                    info.name(), info.description(), info.example()));
        }

        return list.toString();
    }
}
