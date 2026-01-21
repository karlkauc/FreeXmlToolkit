package org.fxt.freexmltoolkit.domain;

import java.util.List;

/**
 * Represents metadata about a global type definition in an XSD schema.
 * Used by the Type Library Panel to display and manage global types.
 *
 * @param name The type name
 * @param category The type category (SIMPLE_TYPE or COMPLEX_TYPE)
 * @param baseType The base type name
 * @param usageCount Number of times the type is used
 * @param documentation The type documentation
 * @param xpath The XPath of the type definition
 * @param isAbstract Whether the type is abstract
 * @param isMixed Whether the type has mixed content
 * @param derivationType The derivation method (extension/restriction)
 * @param contentModel The content model description
 * @param usageXPaths List of XPaths where the type is used
 */
public record TypeInfo(
        String name,
        TypeCategory category,
        String baseType,
        int usageCount,
        String documentation,
        String xpath,
        boolean isAbstract,
        boolean isMixed,
        String derivationType,
        String contentModel,
        List<String> usageXPaths
) {
    /**
     * Defines the category/type of XSD type definition.
     */
    public enum TypeCategory {
        /** Represents an xs:simpleType. */
        SIMPLE_TYPE("Simple Type"),
        /** Represents an xs:complexType. */
        COMPLEX_TYPE("Complex Type");

        private final String displayName;

        TypeCategory(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Gets the display name.
         *
         * @return The display name of the category
         */
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Creates a TypeInfo for a simple type.
     *
     * @param name The type name
     * @param baseType The base type name
     * @param usageCount The number of times the type is used
     * @param documentation The type documentation
     * @param xpath The XPath of the type definition
     * @return A new TypeInfo instance
     */
    public static TypeInfo simpleType(String name, String baseType, int usageCount,
                                      String documentation, String xpath) {
        return new TypeInfo(name, TypeCategory.SIMPLE_TYPE, baseType, usageCount,
                documentation, xpath, false, false, null, null, List.of());
    }

    /**
     * Creates a TypeInfo for a simple type with usage XPaths.
     *
     * @param name The type name
     * @param baseType The base type name
     * @param usageCount The number of times the type is used
     * @param documentation The type documentation
     * @param xpath The XPath of the type definition
     * @param usageXPaths List of XPaths where the type is used
     * @return A new TypeInfo instance
     */
    public static TypeInfo simpleType(String name, String baseType, int usageCount,
                                      String documentation, String xpath, List<String> usageXPaths) {
        return new TypeInfo(name, TypeCategory.SIMPLE_TYPE, baseType, usageCount,
                documentation, xpath, false, false, null, null, usageXPaths);
    }

    /**
     * Creates a TypeInfo for a complex type.
     *
     * @param name The type name
     * @param baseType The base type name
     * @param usageCount The number of times the type is used
     * @param documentation The type documentation
     * @param xpath The XPath of the type definition
     * @param isAbstract Whether the type is abstract
     * @param isMixed Whether the type has mixed content
     * @param derivationType The derivation method (extension/restriction)
     * @param contentModel The content model description
     * @return A new TypeInfo instance
     */
    public static TypeInfo complexType(String name, String baseType, int usageCount,
                                       String documentation, String xpath, boolean isAbstract,
                                       boolean isMixed, String derivationType, String contentModel) {
        return new TypeInfo(name, TypeCategory.COMPLEX_TYPE, baseType, usageCount,
                documentation, xpath, isAbstract, isMixed, derivationType, contentModel, List.of());
    }

    /**
     * Creates a TypeInfo for a complex type with usage XPaths.
     *
     * @param name The type name
     * @param baseType The base type name
     * @param usageCount The number of times the type is used
     * @param documentation The type documentation
     * @param xpath The XPath of the type definition
     * @param isAbstract Whether the type is abstract
     * @param isMixed Whether the type has mixed content
     * @param derivationType The derivation method (extension/restriction)
     * @param contentModel The content model description
     * @param usageXPaths List of XPaths where the type is used
     * @return A new TypeInfo instance
     */
    public static TypeInfo complexType(String name, String baseType, int usageCount,
                                       String documentation, String xpath, boolean isAbstract,
                                       boolean isMixed, String derivationType, String contentModel,
                                       List<String> usageXPaths) {
        return new TypeInfo(name, TypeCategory.COMPLEX_TYPE, baseType, usageCount,
                documentation, xpath, isAbstract, isMixed, derivationType, contentModel, usageXPaths);
    }

    /**
     * Gets a formatted description of the type for display purposes.
     *
     * @return A description of the type
     */
    public String getTypeDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append(category.getDisplayName());

        if (baseType != null && !baseType.isEmpty()) {
            desc.append(" extends ").append(baseType);
        }

        if (category == TypeCategory.COMPLEX_TYPE) {
            if (isAbstract) {
                desc.append(" (abstract)");
            }
            if (isMixed) {
                desc.append(" (mixed)");
            }
            if (contentModel != null && !contentModel.isEmpty()) {
                desc.append(" [").append(contentModel).append("]");
            }
        }

        return desc.toString();
    }

    /**
     * Gets usage information as a formatted string.
     *
     * @return A formatted usage string
     */
    public String getUsageInfo() {
        if (usageCount == 0) {
            return "Unused";
        } else if (usageCount == 1) {
            return "1 reference";
        } else {
            return usageCount + " references";
        }
    }

    /**
     * Gets all usage XPaths as a formatted string for display.
     *
     * @return A formatted string of usage XPaths
     */
    public String getUsageXPathsFormatted() {
        if (usageXPaths == null || usageXPaths.isEmpty()) {
            return "";
        }
        return String.join("; ", usageXPaths);
    }

    /**
     * Gets all usage XPaths as a formatted string for CSV export.
     *
     * @return A newline-separated string of usage XPaths
     */
    public String getUsageXPathsForCsv() {
        if (usageXPaths == null || usageXPaths.isEmpty()) {
            return "";
        }
        return String.join("\n", usageXPaths);
    }
}