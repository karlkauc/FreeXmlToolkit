package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

import java.util.*;

/**
 * Represents an XML element node.
 *
 * <p>An XML element contains:</p>
 * <ul>
 *   <li>Tag name (required)</li>
 *   <li>Attributes (key-value pairs)</li>
 *   <li>Child nodes (elements, text, comments, CDATA, etc.)</li>
 *   <li>Namespace prefix and URI (optional)</li>
 * </ul>
 *
 * <p>Example:</p>
 * <pre>{@code
 * <book id="123" xmlns="http://example.com/books">
 *   <title>XML Guide</title>
 *   <author>John Doe</author>
 * </book>
 * }</pre>
 *
 * <p><strong>Observable Properties:</strong></p>
 * <ul>
 *   <li>name - Element tag name</li>
 *   <li>namespacePrefix - Namespace prefix (e.g., "xsd" in xsd:element)</li>
 *   <li>namespaceURI - Namespace URI</li>
 *   <li>attributes - Attribute map</li>
 *   <li>children - Child nodes list</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlElement extends XmlNode {

    /**
     * Element tag name (without namespace prefix).
     */
    private String name;

    /**
     * Namespace prefix (e.g., "xsd" in xsd:element).
     * Null if no namespace prefix.
     */
    private String namespacePrefix;

    /**
     * Namespace URI (e.g., "http://www.w3.org/2001/XMLSchema").
     * Null if no namespace.
     */
    private String namespaceURI;

    /**
     * Element attributes (ordered map).
     * Uses LinkedHashMap to preserve insertion order.
     */
    private final Map<String, String> attributes = new LinkedHashMap<>();

    /**
     * Child nodes (elements, text, comments, CDATA, etc.).
     */
    private final List<XmlNode> children = new ArrayList<>();

    /**
     * Constructs a new XmlElement with the given name.
     *
     * @param name the element name
     */
    public XmlElement(String name) {
        super();
        this.name = name;
    }

    /**
     * Constructs a new XmlElement with the given name and namespace.
     *
     * @param name            the element name
     * @param namespacePrefix the namespace prefix
     * @param namespaceURI    the namespace URI
     */
    public XmlElement(String name, String namespacePrefix, String namespaceURI) {
        super();
        this.name = name;
        this.namespacePrefix = namespacePrefix;
        this.namespaceURI = namespaceURI;
    }

    /**
     * Copy constructor for deep copy operations.
     *
     * @param original the original element to copy from
     */
    private XmlElement(XmlElement original, String suffix) {
        super(original);
        this.name = original.name + (suffix != null ? suffix : "");
        this.namespacePrefix = original.namespacePrefix;
        this.namespaceURI = original.namespaceURI;
        this.attributes.putAll(original.attributes);
        // Children copied by deepCopy() method
    }

    // ==================== Basic Properties ====================

    /**
     * Returns the element name (without namespace prefix).
     *
     * @return the element name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the element name.
     * Fires a "name" property change event.
     *
     * @param name the new element name
     */
    public void setName(String name) {
        String oldName = this.name;
        this.name = name;
        firePropertyChange("name", oldName, name);
    }

    /**
     * Returns the qualified name (prefix:name or just name).
     *
     * @return the qualified name
     */
    public String getQualifiedName() {
        if (namespacePrefix != null && !namespacePrefix.isEmpty()) {
            return namespacePrefix + ":" + name;
        }
        return name;
    }

    /**
     * Returns the namespace prefix.
     *
     * @return the namespace prefix, or null if none
     */
    public String getNamespacePrefix() {
        return namespacePrefix;
    }

    /**
     * Sets the namespace prefix.
     * Fires a "namespacePrefix" property change event.
     *
     * @param namespacePrefix the new namespace prefix
     */
    public void setNamespacePrefix(String namespacePrefix) {
        String oldPrefix = this.namespacePrefix;
        this.namespacePrefix = namespacePrefix;
        firePropertyChange("namespacePrefix", oldPrefix, namespacePrefix);
    }

    /**
     * Returns the namespace URI.
     *
     * @return the namespace URI, or null if none
     */
    public String getNamespaceURI() {
        return namespaceURI;
    }

    /**
     * Sets the namespace URI.
     * Fires a "namespaceURI" property change event.
     *
     * @param namespaceURI the new namespace URI
     */
    public void setNamespaceURI(String namespaceURI) {
        String oldURI = this.namespaceURI;
        this.namespaceURI = namespaceURI;
        firePropertyChange("namespaceURI", oldURI, namespaceURI);
    }

    // ==================== Attributes ====================

    /**
     * Returns an unmodifiable view of the attributes.
     * Use setAttribute() and removeAttribute() to modify.
     *
     * @return the attributes map
     */
    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * Returns the value of an attribute.
     *
     * @param name the attribute name
     * @return the attribute value, or null if not found
     */
    public String getAttribute(String name) {
        return attributes.get(name);
    }

    /**
     * Sets an attribute value.
     * Fires an "attribute:{name}" property change event.
     *
     * @param name  the attribute name
     * @param value the attribute value
     */
    public void setAttribute(String name, String value) {
        String oldValue = attributes.put(name, value);
        firePropertyChange("attribute:" + name, oldValue, value);
        firePropertyChange("attributes", null, attributes);
    }

    /**
     * Removes an attribute.
     * Fires an "attribute:{name}" property change event.
     *
     * @param name the attribute name
     * @return the previous value, or null if not found
     */
    public String removeAttribute(String name) {
        String oldValue = attributes.remove(name);
        if (oldValue != null) {
            firePropertyChange("attribute:" + name, oldValue, null);
            firePropertyChange("attributes", null, attributes);
        }
        return oldValue;
    }

    /**
     * Checks if an attribute exists.
     *
     * @param name the attribute name
     * @return true if the attribute exists
     */
    public boolean hasAttribute(String name) {
        return attributes.containsKey(name);
    }

    /**
     * Removes all attributes.
     * Fires an "attributes" property change event.
     */
    public void clearAttributes() {
        Map<String, String> oldAttributes = new LinkedHashMap<>(attributes);
        attributes.clear();
        firePropertyChange("attributes", oldAttributes, attributes);
    }

    /**
     * Returns the number of attributes.
     *
     * @return the attribute count
     */
    public int getAttributeCount() {
        return attributes.size();
    }

    // ==================== Children Management ====================

    /**
     * Returns an unmodifiable view of the children.
     * Use addChild() and removeChild() to modify.
     *
     * @return the children list
     */
    public List<XmlNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Adds a child node at the end of the children list.
     * Fires a "children" property change event.
     *
     * @param child the child to add
     */
    public void addChild(XmlNode child) {
        children.add(child);
        child.setParent(this);
        firePropertyChange("children", null, children);
    }

    /**
     * Adds a child node at the specified index.
     * Fires a "children" property change event.
     *
     * @param index the index to insert at
     * @param child the child to add
     */
    public void addChild(int index, XmlNode child) {
        children.add(index, child);
        child.setParent(this);
        firePropertyChange("children", null, children);
    }

    /**
     * Removes a child node.
     * Fires a "children" property change event.
     *
     * @param child the child to remove
     * @return true if the child was removed
     */
    public boolean removeChild(XmlNode child) {
        boolean removed = children.remove(child);
        if (removed) {
            child.setParent(null);
            firePropertyChange("children", child, null);
        }
        return removed;
    }

    /**
     * Removes the child at the specified index.
     * Fires a "children" property change event.
     *
     * @param index the index to remove
     * @return the removed child
     */
    public XmlNode removeChild(int index) {
        XmlNode child = children.remove(index);
        child.setParent(null);
        firePropertyChange("children", child, null);
        return child;
    }

    /**
     * Removes all children.
     * Fires a "children" property change event.
     */
    public void clearChildren() {
        List<XmlNode> oldChildren = new ArrayList<>(children);
        for (XmlNode child : oldChildren) {
            child.setParent(null);
        }
        children.clear();
        firePropertyChange("children", oldChildren, children);
    }

    /**
     * Returns the number of children.
     *
     * @return the child count
     */
    public int getChildCount() {
        return children.size();
    }

    /**
     * Returns the index of a child node.
     *
     * @param child the child to find
     * @return the index, or -1 if not found
     */
    public int indexOf(XmlNode child) {
        return children.indexOf(child);
    }

    /**
     * Returns the child at the specified index.
     *
     * @param index the index
     * @return the child node
     */
    public XmlNode getChild(int index) {
        return children.get(index);
    }

    // ==================== Convenience Methods ====================

    /**
     * Returns the text content of this element.
     * Concatenates all text children (including CDATA).
     *
     * @return the text content
     */
    public String getTextContent() {
        StringBuilder sb = new StringBuilder();
        for (XmlNode child : children) {
            if (child instanceof XmlText) {
                sb.append(((XmlText) child).getText());
            } else if (child instanceof XmlCData) {
                sb.append(((XmlCData) child).getText());
            }
        }
        return sb.toString();
    }

    /**
     * Sets the text content of this element.
     * Removes all existing children and adds a single text node.
     *
     * @param text the text content
     */
    public void setTextContent(String text) {
        clearChildren();
        if (text != null && !text.isEmpty()) {
            addChild(new XmlText(text));
        }
    }

    /**
     * Checks if this element has text content.
     *
     * @return true if the element has at least one text child
     */
    public boolean hasTextContent() {
        for (XmlNode child : children) {
            if (child instanceof XmlText || child instanceof XmlCData) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if this element has non-whitespace text content.
     * Used for mixed content validation - whitespace-only text is ignored.
     *
     * @return true if the element has at least one text child with non-whitespace content
     */
    public boolean hasNonWhitespaceTextContent() {
        for (XmlNode child : children) {
            if (child instanceof XmlText) {
                String text = ((XmlText) child).getText();
                if (text != null && !text.trim().isEmpty()) {
                    return true;
                }
            } else if (child instanceof XmlCData) {
                String text = ((XmlCData) child).getText();
                if (text != null && !text.trim().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if this element has any element children (not text, comments, etc.).
     * Used to prevent mixed content (text + elements in the same node).
     *
     * @return true if at least one child is an XmlElement
     */
    public boolean hasElementChildren() {
        for (XmlNode child : children) {
            if (child instanceof XmlElement) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns all child elements (excludes text, comments, etc.).
     *
     * @return list of child elements
     */
    public List<XmlElement> getChildElements() {
        List<XmlElement> elements = new ArrayList<>();
        for (XmlNode child : children) {
            if (child instanceof XmlElement) {
                elements.add((XmlElement) child);
            }
        }
        return elements;
    }

    /**
     * Returns all child elements with the given name.
     *
     * @param name the element name to match
     * @return list of matching child elements
     */
    public List<XmlElement> getChildElements(String name) {
        List<XmlElement> elements = new ArrayList<>();
        for (XmlNode child : children) {
            if (child instanceof XmlElement element) {
                if (element.getName().equals(name)) {
                    elements.add(element);
                }
            }
        }
        return elements;
    }

    /**
     * Returns the first child element with the given name.
     *
     * @param name the element name to match
     * @return the first matching element, or null if not found
     */
    public XmlElement getChildElement(String name) {
        for (XmlNode child : children) {
            if (child instanceof XmlElement element) {
                if (element.getName().equals(name)) {
                    return element;
                }
            }
        }
        return null;
    }

    // ==================== XmlNode Implementation ====================

    @Override
    public XmlNodeType getNodeType() {
        return XmlNodeType.ELEMENT;
    }

    @Override
    public XmlNode deepCopy(String suffix) {
        XmlElement copy = new XmlElement(this, suffix);

        // Deep copy all children
        for (XmlNode child : children) {
            XmlNode childCopy = child.deepCopy(suffix);
            copy.addChild(childCopy);
        }

        return copy;
    }

    @Override
    public String serialize(int indent) {
        StringBuilder sb = new StringBuilder();
        String indentStr = " ".repeat(indent * 2);

        // Opening tag
        sb.append(indentStr).append("<").append(getQualifiedName());

        // Attributes
        for (Map.Entry<String, String> attr : attributes.entrySet()) {
            sb.append(" ").append(attr.getKey()).append("=\"");
            sb.append(escapeXml(attr.getValue())).append("\"");
        }

        // Empty element or with children
        if (children.isEmpty()) {
            sb.append("/>");
        } else {
            sb.append(">");

            // Children
            boolean hasElementChildren = children.stream().anyMatch(c -> c instanceof XmlElement);
            if (hasElementChildren) {
                sb.append("\n");
            }

            for (XmlNode child : children) {
                if (child instanceof XmlElement || child instanceof XmlComment || child instanceof XmlProcessingInstruction) {
                    sb.append(child.serialize(indent + 1));
                    sb.append("\n");
                } else {
                    sb.append(child.serialize(0));
                }
            }

            // Closing tag
            if (hasElementChildren) {
                sb.append(indentStr);
            }
            sb.append("</").append(getQualifiedName()).append(">");
        }

        return sb.toString();
    }

    @Override
    public void accept(XmlNodeVisitor visitor) {
        visitor.visit(this);
        // Visit all children
        for (XmlNode child : children) {
            child.accept(visitor);
        }
    }

    @Override
    public String toString() {
        return "XmlElement[id=" + getId() + ", name=" + getQualifiedName() + ", children=" + children.size() + ", attributes=" + attributes.size() + "]";
    }

    // ==================== Utility ====================

    /**
     * Escapes XML special characters.
     *
     * @param text the text to escape
     * @return the escaped text
     */
    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
