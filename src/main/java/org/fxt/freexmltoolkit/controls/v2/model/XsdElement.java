package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an XSD element (xs:element).
 *
 * @since 2.0
 */
public class XsdElement extends XsdNode {

    private String type;
    private String ref;  // Reference to a global element
    private boolean nillable;
    private boolean abstractElement;
    private String fixed;
    private String defaultValue;
    private String substitutionGroup;
    private String form; // qualified, unqualified

    // XSD constraints
    private final List<String> patterns = new ArrayList<>();
    private final List<String> enumerations = new ArrayList<>();
    private final List<String> assertions = new ArrayList<>();

    /**
     * Creates a new XSD element with default name.
     */
    public XsdElement() {
        super("element");
    }

    /**
     * Creates a new XSD element.
     *
     * @param name the element name
     */
    public XsdElement(String name) {
        super(name);
    }

    /**
     * Gets the type reference.
     *
     * @return the type name (e.g., "xs:string", "MyCustomType")
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type reference.
     *
     * @param type the type name
     */
    public void setType(String type) {
        String oldValue = this.type;
        this.type = type;
        pcs.firePropertyChange("type", oldValue, type);
    }

    /**
     * Gets the element reference (ref attribute).
     * When set, this element references a global element definition.
     *
     * @return the element reference (e.g., "ds:Signature"), or null
     */
    public String getRef() {
        return ref;
    }

    /**
     * Sets the element reference (ref attribute).
     * When set, this element references a global element definition.
     *
     * @param ref the element reference
     */
    public void setRef(String ref) {
        String oldValue = this.ref;
        this.ref = ref;
        pcs.firePropertyChange("ref", oldValue, ref);
    }

    /**
     * Checks if this element is nillable.
     *
     * @return true if nillable
     */
    public boolean isNillable() {
        return nillable;
    }

    /**
     * Sets the nillable flag.
     *
     * @param nillable true if nillable
     */
    public void setNillable(boolean nillable) {
        boolean oldValue = this.nillable;
        this.nillable = nillable;
        pcs.firePropertyChange("nillable", oldValue, nillable);
    }

    /**
     * Checks if this element is abstract.
     *
     * @return true if abstract
     */
    public boolean isAbstract() {
        return abstractElement;
    }

    /**
     * Sets the abstract flag.
     *
     * @param abstractElement true if abstract
     */
    public void setAbstract(boolean abstractElement) {
        boolean oldValue = this.abstractElement;
        this.abstractElement = abstractElement;
        pcs.firePropertyChange("abstract", oldValue, abstractElement);
    }

    /**
     * Gets the fixed value.
     *
     * @return the fixed value, or null
     */
    public String getFixed() {
        return fixed;
    }

    /**
     * Sets the fixed value.
     *
     * @param fixed the fixed value
     */
    public void setFixed(String fixed) {
        String oldValue = this.fixed;
        this.fixed = fixed;
        pcs.firePropertyChange("fixed", oldValue, fixed);
    }

    /**
     * Gets the default value.
     *
     * @return the default value, or null
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Sets the default value.
     *
     * @param defaultValue the default value
     */
    public void setDefaultValue(String defaultValue) {
        String oldValue = this.defaultValue;
        this.defaultValue = defaultValue;
        pcs.firePropertyChange("defaultValue", oldValue, defaultValue);
    }

    /**
     * Gets the substitution group.
     *
     * @return the substitution group, or null
     */
    public String getSubstitutionGroup() {
        return substitutionGroup;
    }

    /**
     * Sets the substitution group.
     *
     * @param substitutionGroup the substitution group
     */
    public void setSubstitutionGroup(String substitutionGroup) {
        String oldValue = this.substitutionGroup;
        this.substitutionGroup = substitutionGroup;
        pcs.firePropertyChange("substitutionGroup", oldValue, substitutionGroup);
    }

    /**
     * Gets the form attribute (qualified, unqualified).
     *
     * @return the form value
     */
    public String getForm() {
        return form;
    }

    /**
     * Sets the form attribute.
     *
     * @param form the form value (qualified, unqualified)
     */
    public void setForm(String form) {
        String oldValue = this.form;
        this.form = form;
        pcs.firePropertyChange("form", oldValue, form);
    }

    /**
     * Gets an unmodifiable list of regex patterns.
     *
     * @return the patterns list
     */
    public List<String> getPatterns() {
        return Collections.unmodifiableList(patterns);
    }

    /**
     * Adds a regex pattern constraint.
     *
     * @param pattern the regex pattern to add
     */
    public void addPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return;
        }
        List<String> oldValue = new ArrayList<>(patterns);
        patterns.add(pattern.trim());
        pcs.firePropertyChange("patterns", oldValue, new ArrayList<>(patterns));
    }

    /**
     * Removes a regex pattern constraint.
     *
     * @param pattern the pattern to remove
     * @return true if the pattern was removed
     */
    public boolean removePattern(String pattern) {
        List<String> oldValue = new ArrayList<>(patterns);
        boolean removed = patterns.remove(pattern);
        if (removed) {
            pcs.firePropertyChange("patterns", oldValue, new ArrayList<>(patterns));
        }
        return removed;
    }

    /**
     * Clears all regex pattern constraints.
     */
    public void clearPatterns() {
        if (!patterns.isEmpty()) {
            List<String> oldValue = new ArrayList<>(patterns);
            patterns.clear();
            pcs.firePropertyChange("patterns", oldValue, new ArrayList<>(patterns));
        }
    }

    /**
     * Gets an unmodifiable list of enumeration values.
     *
     * @return the enumerations list
     */
    public List<String> getEnumerations() {
        return Collections.unmodifiableList(enumerations);
    }

    /**
     * Adds an enumeration value.
     *
     * @param enumeration the enumeration value to add
     */
    public void addEnumeration(String enumeration) {
        if (enumeration == null || enumeration.trim().isEmpty()) {
            return;
        }
        List<String> oldValue = new ArrayList<>(enumerations);
        enumerations.add(enumeration.trim());
        pcs.firePropertyChange("enumerations", oldValue, new ArrayList<>(enumerations));
    }

    /**
     * Removes an enumeration value.
     *
     * @param enumeration the enumeration to remove
     * @return true if the enumeration was removed
     */
    public boolean removeEnumeration(String enumeration) {
        List<String> oldValue = new ArrayList<>(enumerations);
        boolean removed = enumerations.remove(enumeration);
        if (removed) {
            pcs.firePropertyChange("enumerations", oldValue, new ArrayList<>(enumerations));
        }
        return removed;
    }

    /**
     * Clears all enumeration values.
     */
    public void clearEnumerations() {
        if (!enumerations.isEmpty()) {
            List<String> oldValue = new ArrayList<>(enumerations);
            enumerations.clear();
            pcs.firePropertyChange("enumerations", oldValue, new ArrayList<>(enumerations));
        }
    }

    /**
     * Gets an unmodifiable list of XSD 1.1 assertions.
     *
     * @return the assertions list
     */
    public List<String> getAssertions() {
        return Collections.unmodifiableList(assertions);
    }

    /**
     * Adds an XSD 1.1 assertion (XPath expression).
     *
     * @param assertion the assertion to add
     */
    public void addAssertion(String assertion) {
        if (assertion == null || assertion.trim().isEmpty()) {
            return;
        }
        List<String> oldValue = new ArrayList<>(assertions);
        assertions.add(assertion.trim());
        pcs.firePropertyChange("assertions", oldValue, new ArrayList<>(assertions));
    }

    /**
     * Removes an assertion.
     *
     * @param assertion the assertion to remove
     * @return true if the assertion was removed
     */
    public boolean removeAssertion(String assertion) {
        List<String> oldValue = new ArrayList<>(assertions);
        boolean removed = assertions.remove(assertion);
        if (removed) {
            pcs.firePropertyChange("assertions", oldValue, new ArrayList<>(assertions));
        }
        return removed;
    }

    /**
     * Clears all assertions.
     */
    public void clearAssertions() {
        if (!assertions.isEmpty()) {
            List<String> oldValue = new ArrayList<>(assertions);
            assertions.clear();
            pcs.firePropertyChange("assertions", oldValue, new ArrayList<>(assertions));
        }
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.ELEMENT;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        // Create new element with suffix appended to name
        String newName = suffix != null ? getName() + suffix : getName();
        XsdElement copy = new XsdElement(newName);

        // Copy XsdElement-specific properties
        copy.setType(this.type);
        copy.setNillable(this.nillable);
        copy.setAbstract(this.abstractElement);
        copy.setFixed(this.fixed);
        copy.setDefaultValue(this.defaultValue);
        copy.setSubstitutionGroup(this.substitutionGroup);
        copy.setForm(this.form);

        // Copy constraint lists
        for (String pattern : this.patterns) {
            copy.addPattern(pattern);
        }
        for (String enumeration : this.enumerations) {
            copy.addEnumeration(enumeration);
        }
        for (String assertion : this.assertions) {
            copy.addAssertion(assertion);
        }

        // Copy base properties and children
        copyBasicPropertiesTo(copy);

        return copy;
    }
}
