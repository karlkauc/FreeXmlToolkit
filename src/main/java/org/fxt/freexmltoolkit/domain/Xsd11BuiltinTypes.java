package org.fxt.freexmltoolkit.domain;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.fxt.freexmltoolkit.domain.XsdBuiltinTypeCatalog.BuiltinType;

/**
 * Reference for XSD built-in types with an emphasis on the XSD 1.1 additions.
 *
 * <p>A thin facade over {@link XsdBuiltinTypeCatalog}: every answer is derived from the catalog,
 * reported with the conventional {@code xs:} prefix.
 */
public class Xsd11BuiltinTypes {

    private static final String PREFIX = "xs";

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

    private Xsd11BuiltinTypes() {
    }

    /**
     * Returns information about a built-in type
     *
     * @param typeName The type name (with or without xs: prefix)
     * @return TypeInfo or null if not found
     */
    public static TypeInfo getTypeInfo(String typeName) {
        return XsdBuiltinTypeCatalog.find(typeName).map(Xsd11BuiltinTypes::toInfo).orElse(null);
    }

    private static TypeInfo toInfo(BuiltinType type) {
        return new TypeInfo(type.qualified(PREFIX),
                type.baseType() == null ? null : PREFIX + ":" + type.baseType(),
                type.description(), type.example(), type.xsd11Only());
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
        return XsdBuiltinTypeCatalog.all().stream()
                .filter(BuiltinType::xsd11Only)
                .map(t -> t.qualified(PREFIX))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Returns all known built-in type names
     */
    public static Set<String> getAllTypeNames() {
        return XsdBuiltinTypeCatalog.all().stream()
                .map(t -> t.qualified(PREFIX))
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
        if (info.baseType() != null) {
            doc.append("Base Type: ").append(info.baseType()).append("\n");
        }
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
            TypeInfo info = getTypeInfo(typeName);
            list.append(String.format("• %s%n  %s%n  Example: %s%n%n",
                    info.name(), info.description(), info.example()));
        }

        return list.toString();
    }
}
