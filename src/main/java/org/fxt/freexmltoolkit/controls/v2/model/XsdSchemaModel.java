package org.fxt.freexmltoolkit.controls.v2.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.*;

/**
 * Root model representing an XSD Schema.
 * Supports both XSD 1.0 and XSD 1.1.
 * <p>
 * This model is observable and fires property change events when modified.
 *
 * @since 2.0
 */
public class XsdSchemaModel {

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    // Unique identifier for this schema model
    private final String id;

    // Schema metadata
    private String version = "1.0"; // "1.0" or "1.1"
    private String targetNamespace;
    private String elementFormDefault = "qualified";
    private String attributeFormDefault = "unqualified";
    private final Map<String, String> namespaces = new LinkedHashMap<>();

    // Global components
    private final List<XsdElementModel> globalElements = new ArrayList<>();
    private final Map<String, XsdComplexTypeModel> globalComplexTypes = new LinkedHashMap<>();
    private final Map<String, XsdSimpleTypeModel> globalSimpleTypes = new LinkedHashMap<>();
    private final Map<String, XsdGroupModel> globalGroups = new LinkedHashMap<>();

    // Property names for change events
    public static final String PROP_VERSION = "version";
    public static final String PROP_TARGET_NAMESPACE = "targetNamespace";
    public static final String PROP_ELEMENT_FORM_DEFAULT = "elementFormDefault";
    public static final String PROP_ATTRIBUTE_FORM_DEFAULT = "attributeFormDefault";
    public static final String PROP_GLOBAL_ELEMENTS = "globalElements";
    public static final String PROP_GLOBAL_COMPLEX_TYPES = "globalComplexTypes";
    public static final String PROP_GLOBAL_SIMPLE_TYPES = "globalSimpleTypes";
    public static final String PROP_GLOBAL_GROUPS = "globalGroups";

    /**
     * Creates a new XSD schema model with a generated unique ID.
     */
    public XsdSchemaModel() {
        this.id = UUID.randomUUID().toString();
    }

    /**
     * Creates a new XSD schema model with the specified ID.
     *
     * @param id the schema ID
     */
    public XsdSchemaModel(String id) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
    }

    /**
     * Returns the unique identifier of this schema.
     *
     * @return the schema ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the XSD version (1.0 or 1.1).
     *
     * @return the XSD version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the XSD version.
     *
     * @param version the version ("1.0" or "1.1")
     */
    public void setVersion(String version) {
        String oldValue = this.version;
        this.version = version;
        pcs.firePropertyChange(PROP_VERSION, oldValue, version);
    }

    /**
     * Checks if this schema uses XSD 1.1 features.
     *
     * @return true if version is 1.1, false otherwise
     */
    public boolean isXsd11() {
        return "1.1".equals(version);
    }

    /**
     * Returns the target namespace.
     *
     * @return the target namespace, or null if not set
     */
    public String getTargetNamespace() {
        return targetNamespace;
    }

    /**
     * Sets the target namespace.
     *
     * @param targetNamespace the target namespace
     */
    public void setTargetNamespace(String targetNamespace) {
        String oldValue = this.targetNamespace;
        this.targetNamespace = targetNamespace;
        pcs.firePropertyChange(PROP_TARGET_NAMESPACE, oldValue, targetNamespace);
    }

    /**
     * Returns the elementFormDefault attribute.
     *
     * @return the elementFormDefault value
     */
    public String getElementFormDefault() {
        return elementFormDefault;
    }

    /**
     * Sets the elementFormDefault attribute.
     *
     * @param elementFormDefault the value ("qualified" or "unqualified")
     */
    public void setElementFormDefault(String elementFormDefault) {
        String oldValue = this.elementFormDefault;
        this.elementFormDefault = elementFormDefault;
        pcs.firePropertyChange(PROP_ELEMENT_FORM_DEFAULT, oldValue, elementFormDefault);
    }

    /**
     * Returns the attributeFormDefault attribute.
     *
     * @return the attributeFormDefault value
     */
    public String getAttributeFormDefault() {
        return attributeFormDefault;
    }

    /**
     * Sets the attributeFormDefault attribute.
     *
     * @param attributeFormDefault the value ("qualified" or "unqualified")
     */
    public void setAttributeFormDefault(String attributeFormDefault) {
        String oldValue = this.attributeFormDefault;
        this.attributeFormDefault = attributeFormDefault;
        pcs.firePropertyChange(PROP_ATTRIBUTE_FORM_DEFAULT, oldValue, attributeFormDefault);
    }

    /**
     * Returns all namespace declarations.
     *
     * @return unmodifiable map of prefix to URI mappings
     */
    public Map<String, String> getNamespaces() {
        return Collections.unmodifiableMap(namespaces);
    }

    /**
     * Adds a namespace declaration.
     *
     * @param prefix the namespace prefix
     * @param uri    the namespace URI
     */
    public void addNamespace(String prefix, String uri) {
        Objects.requireNonNull(prefix, "Prefix cannot be null");
        Objects.requireNonNull(uri, "URI cannot be null");
        namespaces.put(prefix, uri);
    }

    /**
     * Removes a namespace declaration.
     *
     * @param prefix the namespace prefix to remove
     */
    public void removeNamespace(String prefix) {
        namespaces.remove(prefix);
    }

    // ==================== Global Elements ====================

    /**
     * Returns all global elements.
     *
     * @return unmodifiable list of global elements
     */
    public List<XsdElementModel> getGlobalElements() {
        return Collections.unmodifiableList(globalElements);
    }

    /**
     * Adds a global element.
     *
     * @param element the element to add
     */
    public void addGlobalElement(XsdElementModel element) {
        Objects.requireNonNull(element, "Element cannot be null");
        globalElements.add(element);
        pcs.firePropertyChange(PROP_GLOBAL_ELEMENTS, null, element);
    }

    /**
     * Removes a global element.
     *
     * @param element the element to remove
     */
    public void removeGlobalElement(XsdElementModel element) {
        if (globalElements.remove(element)) {
            pcs.firePropertyChange(PROP_GLOBAL_ELEMENTS, element, null);
        }
    }

    /**
     * Finds a global element by name.
     *
     * @param name the element name
     * @return the element, or null if not found
     */
    public XsdElementModel findGlobalElement(String name) {
        return globalElements.stream()
                .filter(e -> name.equals(e.getName()))
                .findFirst()
                .orElse(null);
    }

    // ==================== Global Complex Types ====================

    /**
     * Returns all global complex types.
     *
     * @return unmodifiable map of type name to type model
     */
    public Map<String, XsdComplexTypeModel> getGlobalComplexTypes() {
        return Collections.unmodifiableMap(globalComplexTypes);
    }

    /**
     * Adds a global complex type.
     *
     * @param name the type name
     * @param type the type model
     */
    public void addGlobalComplexType(String name, XsdComplexTypeModel type) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        globalComplexTypes.put(name, type);
        pcs.firePropertyChange(PROP_GLOBAL_COMPLEX_TYPES, null, type);
    }

    /**
     * Removes a global complex type.
     *
     * @param name the type name
     */
    public void removeGlobalComplexType(String name) {
        XsdComplexTypeModel removed = globalComplexTypes.remove(name);
        if (removed != null) {
            pcs.firePropertyChange(PROP_GLOBAL_COMPLEX_TYPES, removed, null);
        }
    }

    /**
     * Finds a global complex type by name.
     *
     * @param name the type name
     * @return the type, or null if not found
     */
    public XsdComplexTypeModel findGlobalComplexType(String name) {
        return globalComplexTypes.get(name);
    }

    // ==================== Global Simple Types ====================

    /**
     * Returns all global simple types.
     *
     * @return unmodifiable map of type name to type model
     */
    public Map<String, XsdSimpleTypeModel> getGlobalSimpleTypes() {
        return Collections.unmodifiableMap(globalSimpleTypes);
    }

    /**
     * Adds a global simple type.
     *
     * @param name the type name
     * @param type the type model
     */
    public void addGlobalSimpleType(String name, XsdSimpleTypeModel type) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        globalSimpleTypes.put(name, type);
        pcs.firePropertyChange(PROP_GLOBAL_SIMPLE_TYPES, null, type);
    }

    /**
     * Removes a global simple type.
     *
     * @param name the type name
     */
    public void removeGlobalSimpleType(String name) {
        XsdSimpleTypeModel removed = globalSimpleTypes.remove(name);
        if (removed != null) {
            pcs.firePropertyChange(PROP_GLOBAL_SIMPLE_TYPES, removed, null);
        }
    }

    /**
     * Finds a global simple type by name.
     *
     * @param name the type name
     * @return the type, or null if not found
     */
    public XsdSimpleTypeModel findGlobalSimpleType(String name) {
        return globalSimpleTypes.get(name);
    }

    // ==================== Global Groups ====================

    /**
     * Returns all global groups.
     *
     * @return unmodifiable map of group name to group model
     */
    public Map<String, XsdGroupModel> getGlobalGroups() {
        return Collections.unmodifiableMap(globalGroups);
    }

    /**
     * Adds a global group.
     *
     * @param name  the group name
     * @param group the group model
     */
    public void addGlobalGroup(String name, XsdGroupModel group) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(group, "Group cannot be null");
        globalGroups.put(name, group);
        pcs.firePropertyChange(PROP_GLOBAL_GROUPS, null, group);
    }

    /**
     * Removes a global group.
     *
     * @param name the group name
     */
    public void removeGlobalGroup(String name) {
        XsdGroupModel removed = globalGroups.remove(name);
        if (removed != null) {
            pcs.firePropertyChange(PROP_GLOBAL_GROUPS, removed, null);
        }
    }

    /**
     * Finds a global group by name.
     *
     * @param name the group name
     * @return the group, or null if not found
     */
    public XsdGroupModel findGlobalGroup(String name) {
        return globalGroups.get(name);
    }

    // ==================== Property Change Support ====================

    /**
     * Adds a PropertyChangeListener to this model.
     *
     * @param listener the listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Removes a PropertyChangeListener from this model.
     *
     * @param listener the listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Adds a PropertyChangeListener for a specific property.
     *
     * @param propertyName the property name
     * @param listener     the listener to add
     */
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Removes a PropertyChangeListener for a specific property.
     *
     * @param propertyName the property name
     * @param listener     the listener to remove
     */
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(propertyName, listener);
    }

    @Override
    public String toString() {
        return "XsdSchemaModel{" +
                "id='" + id + '\'' +
                ", version='" + version + '\'' +
                ", targetNamespace='" + targetNamespace + '\'' +
                ", globalElements=" + globalElements.size() +
                ", globalComplexTypes=" + globalComplexTypes.size() +
                ", globalSimpleTypes=" + globalSimpleTypes.size() +
                ", globalGroups=" + globalGroups.size() +
                '}';
    }
}
