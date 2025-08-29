package org.fxt.freexmltoolkit.domain;

/**
 * Represents metadata about a global type definition in an XSD schema.
 * Used by the Type Library Panel to display and manage global types.
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
        String contentModel
) {
    /**
     * Defines the category/type of XSD type definition.
     */
    public enum TypeCategory {
        SIMPLE_TYPE("Simple Type"),
        COMPLEX_TYPE("Complex Type");

        private final String displayName;

        TypeCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Creates a TypeInfo for a simple type.
     */
    public static TypeInfo simpleType(String name, String baseType, int usageCount,
                                      String documentation, String xpath) {
        return new TypeInfo(name, TypeCategory.SIMPLE_TYPE, baseType, usageCount,
                documentation, xpath, false, false, null, null);
    }

    /**
     * Creates a TypeInfo for a complex type.
     */
    public static TypeInfo complexType(String name, String baseType, int usageCount,
                                       String documentation, String xpath, boolean isAbstract,
                                       boolean isMixed, String derivationType, String contentModel) {
        return new TypeInfo(name, TypeCategory.COMPLEX_TYPE, baseType, usageCount,
                documentation, xpath, isAbstract, isMixed, derivationType, contentModel);
    }

    /**
     * Gets a formatted description of the type for display purposes.
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
}