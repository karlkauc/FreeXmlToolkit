package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an XML document - the root node of the XML tree.
 *
 * <p>An XML document contains:</p>
 * <ul>
 *   <li>XML declaration (version, encoding, standalone)</li>
 *   <li>Document type declaration (DTD) - optional</li>
 *   <li>Root element (required)</li>
 *   <li>Processing instructions</li>
 *   <li>Comments</li>
 * </ul>
 *
 * <p>Example:</p>
 * <pre>{@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <!-- Comment -->
 * <root>
 *   ...
 * </root>
 * }</pre>
 *
 * <p><strong>Observable Properties:</strong></p>
 * <ul>
 *   <li>version - XML version (default: "1.0")</li>
 *   <li>encoding - Character encoding (default: "UTF-8")</li>
 *   <li>standalone - Standalone declaration (default: null)</li>
 *   <li>children - Document children (root element, comments, PIs)</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlDocument extends XmlNode {

    /**
     * XML version (default: "1.0").
     */
    private String version = "1.0";

    /**
     * Character encoding (default: "UTF-8").
     */
    private String encoding = "UTF-8";

    /**
     * Standalone declaration (yes/no, or null if not specified).
     */
    private Boolean standalone;

    /**
     * Document children (root element, processing instructions, comments).
     */
    private final List<XmlNode> children = new ArrayList<>();

    /**
     * Constructs a new empty XmlDocument.
     */
    public XmlDocument() {
        super();
    }

    /**
     * Copy constructor for deep copy operations.
     *
     * @param original the original document to copy from
     */
    private XmlDocument(XmlDocument original) {
        super(original);
        this.version = original.version;
        this.encoding = original.encoding;
        this.standalone = original.standalone;
        // Children copied by deepCopy() method
    }

    // ==================== Properties ====================

    /**
     * Returns the XML version.
     *
     * @return the version (e.g., "1.0", "1.1")
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the XML version.
     * Fires a "version" property change event.
     *
     * @param version the new version
     */
    public void setVersion(String version) {
        String oldVersion = this.version;
        this.version = version;
        firePropertyChange("version", oldVersion, version);
    }

    /**
     * Returns the character encoding.
     *
     * @return the encoding (e.g., "UTF-8", "ISO-8859-1")
     */
    public String getEncoding() {
        return encoding;
    }

    /**
     * Sets the character encoding.
     * Fires an "encoding" property change event.
     *
     * @param encoding the new encoding
     */
    public void setEncoding(String encoding) {
        String oldEncoding = this.encoding;
        this.encoding = encoding;
        firePropertyChange("encoding", oldEncoding, encoding);
    }

    /**
     * Returns the standalone declaration.
     *
     * @return true if standalone="yes", false if standalone="no", null if not specified
     */
    public Boolean getStandalone() {
        return standalone;
    }

    /**
     * Sets the standalone declaration.
     * Fires a "standalone" property change event.
     *
     * @param standalone the new standalone value
     */
    public void setStandalone(Boolean standalone) {
        Boolean oldStandalone = this.standalone;
        this.standalone = standalone;
        firePropertyChange("standalone", oldStandalone, standalone);
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
     * Returns the root element of the document.
     * The root element is the first XmlElement in the children list.
     *
     * @return the root element, or null if not set
     */
    public XmlElement getRootElement() {
        for (XmlNode child : children) {
            if (child instanceof XmlElement) {
                return (XmlElement) child;
            }
        }
        return null;
    }

    /**
     * Sets the root element of the document.
     * If a root element already exists, it is replaced.
     * Fires a "children" property change event.
     *
     * @param rootElement the new root element
     */
    public void setRootElement(XmlElement rootElement) {
        // Remove existing root element
        XmlElement oldRoot = getRootElement();
        if (oldRoot != null) {
            children.remove(oldRoot);
            oldRoot.setParent(null);
        }

        // Add new root element
        if (rootElement != null) {
            // Insert root element after any PIs/comments, or at beginning
            int insertIndex = 0;
            for (int i = 0; i < children.size(); i++) {
                XmlNode child = children.get(i);
                if (child instanceof XmlElement) {
                    insertIndex = i;
                    break;
                }
                insertIndex = i + 1;
            }

            children.add(insertIndex, rootElement);
            rootElement.setParent(this);
        }

        firePropertyChange("children", oldRoot, rootElement);
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

    // ==================== XmlNode Implementation ====================

    @Override
    public XmlNodeType getNodeType() {
        return XmlNodeType.DOCUMENT;
    }

    @Override
    public XmlNode deepCopy(String suffix) {
        XmlDocument copy = new XmlDocument(this);

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

        // XML declaration
        sb.append("<?xml version=\"").append(version).append("\"");
        if (encoding != null && !encoding.isEmpty()) {
            sb.append(" encoding=\"").append(encoding).append("\"");
        }
        if (standalone != null) {
            sb.append(" standalone=\"").append(standalone ? "yes" : "no").append("\"");
        }
        sb.append("?>\n");

        // Serialize all children
        for (XmlNode child : children) {
            sb.append(child.serialize(0));
            if (child.getNodeType() != XmlNodeType.TEXT) {
                sb.append("\n");
            }
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
        XmlElement root = getRootElement();
        String rootName = root != null ? root.getName() : "no-root";
        return "XmlDocument[version=" + version + ", root=" + rootName + "]";
    }
}
