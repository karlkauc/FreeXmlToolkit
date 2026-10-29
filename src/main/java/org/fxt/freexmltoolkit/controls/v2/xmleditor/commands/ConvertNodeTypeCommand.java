package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlCData;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;

/**
 * Changes the type of a node in place (XMLSpy: click the node-type icon / Type submenu):
 * element ↔ attribute ↔ text ↔ CDATA ↔ comment ↔ processing instruction. Name and value are
 * kept where the target has them (a value-only node that becomes an element/attribute/PI
 * gets a default name). Attributes always come first, so a node that becomes an attribute
 * moves into its parent's attribute list and an attribute that becomes a node is inserted
 * as its owner's first child. An element with child elements cannot change its type.
 */
public final class ConvertNodeTypeCommand implements XmlCommand {

    /** The node kinds a grid row can be converted between. */
    public enum Kind {
        ELEMENT("Element"),
        ATTRIBUTE("Attribute"),
        TEXT("Text"),
        CDATA("CDATA"),
        COMMENT("Comment"),
        PROCESSING_INSTRUCTION("Processing Instruction");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        /** @return the menu label */
        public String label() {
            return label;
        }
    }

    // Source: either a node, or an attribute (owner + name)
    private final XmlNode sourceNode;
    private final XmlElement attributeOwner;
    private final String attributeName;
    private final Kind target;

    // Undo state
    private XmlElement container;           // the element whose children/attributes change
    private int childIndex = -1;            // where the source node sat / the new node goes
    private LinkedHashMap<String, String> attributesBefore;
    private XmlNode created;
    private boolean executed;

    private ConvertNodeTypeCommand(XmlNode sourceNode, XmlElement attributeOwner, String attributeName, Kind target) {
        this.sourceNode = sourceNode;
        this.attributeOwner = attributeOwner;
        this.attributeName = attributeName;
        this.target = target;
    }

    /** Converts a child node (element, text, CDATA, comment, PI) of an element. */
    public static ConvertNodeTypeCommand ofNode(XmlNode node, Kind target) {
        return new ConvertNodeTypeCommand(node, null, null, target);
    }

    /** Converts the attribute {@code name} of {@code owner}. */
    public static ConvertNodeTypeCommand ofAttribute(XmlElement owner, String name, Kind target) {
        return new ConvertNodeTypeCommand(null, owner, name, target);
    }

    /** @return the kind of a node, or {@code null} for nodes that cannot be converted */
    public static Kind kindOf(XmlNode node) {
        if (node instanceof XmlElement) {
            return Kind.ELEMENT;
        }
        if (node instanceof XmlText) {
            return Kind.TEXT;
        }
        if (node instanceof XmlCData) {
            return Kind.CDATA;
        }
        if (node instanceof XmlComment) {
            return Kind.COMMENT;
        }
        if (node instanceof XmlProcessingInstruction) {
            return Kind.PROCESSING_INSTRUCTION;
        }
        return null;
    }

    /**
     * @param node a child node
     * @return the kinds the node can be converted to (empty when it cannot change its type:
     *         an element with child elements, a top-level node, an unknown node)
     */
    public static List<Kind> targetsOfNode(XmlNode node) {
        Kind current = kindOf(node);
        if (current == null || !(node.getParent() instanceof XmlElement)
                || (node instanceof XmlElement el && el.hasElementChildren())) {
            return List.of();
        }
        List<Kind> kinds = new ArrayList<>(List.of(Kind.values()));
        kinds.remove(current);
        return kinds;
    }

    /** @return the kinds an attribute can be converted to */
    public static List<Kind> targetsOfAttribute() {
        List<Kind> kinds = new ArrayList<>(List.of(Kind.values()));
        kinds.remove(Kind.ATTRIBUTE);
        return kinds;
    }

    @Override
    public boolean execute() {
        String name;
        String value;
        if (attributeOwner != null) {
            if (target == Kind.ATTRIBUTE || !attributeOwner.hasAttribute(attributeName)) {
                return false;
            }
            container = attributeOwner;
            name = attributeName;
            value = attributeOwner.getAttribute(attributeName);
            attributesBefore = new LinkedHashMap<>(attributeOwner.getAttributes());
            created = createNode(target, name, value);
            attributeOwner.removeAttribute(attributeName);
            childIndex = 0;
            attributeOwner.addChild(0, created);
        } else {
            if (!targetsOfNode(sourceNode).contains(target)) {
                return false;
            }
            container = (XmlElement) sourceNode.getParent();
            childIndex = container.indexOf(sourceNode);
            name = nameOf(sourceNode);
            value = valueOf(sourceNode);
            attributesBefore = new LinkedHashMap<>(container.getAttributes());
            container.removeChild(sourceNode);
            if (target == Kind.ATTRIBUTE) {
                container.setAttribute(uniqueAttributeName(container, name), value == null ? "" : value);
            } else {
                created = createNode(target, name, value);
                container.addChild(childIndex, created);
            }
        }
        executed = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!executed) {
            return false;
        }
        if (created != null) {
            container.removeChild(created);
        }
        container.clearAttributes();
        attributesBefore.forEach(container::setAttribute);
        if (sourceNode != null) {
            container.addChild(childIndex, sourceNode);
        }
        executed = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Change type to " + target.label();
    }

    /** @return the converted node (null when the target is an attribute or before execution) */
    public XmlNode getCreated() {
        return created;
    }

    private static String nameOf(XmlNode node) {
        if (node instanceof XmlElement el) {
            return el.getName();
        }
        if (node instanceof XmlProcessingInstruction pi) {
            return pi.getTarget();
        }
        return null;
    }

    private static String valueOf(XmlNode node) {
        if (node instanceof XmlElement el) {
            return el.getTextContent();
        }
        if (node instanceof XmlText t) {
            return t.getText();
        }
        if (node instanceof XmlCData c) {
            return c.getText();
        }
        if (node instanceof XmlComment c) {
            return c.getText();
        }
        if (node instanceof XmlProcessingInstruction pi) {
            return pi.getData();
        }
        return null;
    }

    private static XmlNode createNode(Kind kind, String name, String value) {
        String v = value == null ? "" : value;
        return switch (kind) {
            case ELEMENT -> {
                XmlElement el = new XmlElement(validName(name, "element"));
                if (!v.isEmpty()) {
                    el.addChild(new XmlText(v));
                }
                yield el;
            }
            case TEXT -> new XmlText(v);
            case CDATA -> new XmlCData(v);
            case COMMENT -> new XmlComment(v);
            case PROCESSING_INSTRUCTION -> new XmlProcessingInstruction(validName(name, "pi"), v);
            case ATTRIBUTE -> throw new IllegalArgumentException("attributes are not nodes");
        };
    }

    private static String validName(String name, String fallback) {
        return name == null || name.isBlank() ? fallback : name;
    }

    private static String uniqueAttributeName(XmlElement owner, String name) {
        String base = validName(name, "attribute");
        String candidate = base;
        int n = 2;
        Map<String, String> attributes = owner.getAttributes();
        while (attributes.containsKey(candidate)) {
            candidate = base + n++;
        }
        return candidate;
    }
}
