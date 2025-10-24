package org.fxt.freexmltoolkit.domain;

/**
 * Enumeration of XSD 1.1 specific features.
 * Used to track which features are used in a schema and to validate compatibility.
 */
public enum Xsd11Feature {
    /**
     * xs:assert - Assertions with XPath 2.0 expressions for complex validation rules
     */
    ASSERTIONS("xs:assert", "Assertions", "XPath 2.0 based validation constraints"),

    /**
     * xs:alternative - Conditional type assignment based on XPath expressions
     */
    ALTERNATIVES("xs:alternative", "Type Alternatives", "Conditional type assignment"),

    /**
     * xs:all with enhanced cardinality - Extended minOccurs/maxOccurs support
     */
    ALL_EXTENSIONS("xs:all", "Enhanced xs:all", "Extended cardinality for xs:all groups"),

    /**
     * xs:override - Schema override mechanism (replaces xs:redefine)
     */
    OVERRIDE("xs:override", "Override", "Schema component override mechanism"),

    /**
     * xs:openContent - Interspersed wildcard support
     */
    OPEN_CONTENT("xs:openContent", "Open Content", "Wildcards interspersed with explicit elements"),

    /**
     * Enhanced xs:any and xs:anyAttribute - notQName and notNamespace attributes
     */
    ENHANCED_WILDCARDS("xs:any", "Enhanced Wildcards", "notQName and notNamespace support"),

    /**
     * New built-in types: dateTimeStamp, yearMonthDuration, dayTimeDuration, precisionDecimal
     */
    NEW_BUILTIN_TYPES("xs:dateTimeStamp", "New Built-in Types", "Additional XSD 1.1 built-in types"),

    /**
     * explicitTimezone facet for date/time types
     */
    EXPLICIT_TIMEZONE("explicitTimezone", "Explicit Timezone", "Timezone requirement control"),

    /**
     * defaultAttributes on xs:schema and xs:complexType
     */
    DEFAULT_ATTRIBUTES("defaultAttributes", "Default Attributes", "Schema-wide default attribute groups"),

    /**
     * targetNamespace on local element declarations
     */
    LOCAL_TARGET_NAMESPACE("targetNamespace", "Local Target Namespace", "Namespace control on local elements"),

    /**
     * inheritable attribute on xs:attribute
     */
    INHERITABLE_ATTRIBUTES("inheritable", "Inheritable Attributes", "Attribute inheritance control"),

    /**
     * Version indicator: vc:minVersion="1.1"
     */
    VERSION_INDICATOR("vc:minVersion", "Version Indicator", "XSD version declaration");

    private final String xmlTag;
    private final String displayName;
    private final String description;

    Xsd11Feature(String xmlTag, String displayName, String description) {
        this.xmlTag = xmlTag;
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Returns the XML tag or attribute name for this feature
     */
    public String getXmlTag() {
        return xmlTag;
    }

    /**
     * Returns a user-friendly display name for this feature
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns a brief description of this feature
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this is a critical feature (requires XSD 1.1 processor)
     */
    public boolean isCritical() {
        return switch (this) {
            case ASSERTIONS, ALTERNATIVES, OVERRIDE, OPEN_CONTENT, VERSION_INDICATOR -> true;
            default -> false;
        };
    }

    @Override
    public String toString() {
        return displayName + " (" + xmlTag + ")";
    }
}
