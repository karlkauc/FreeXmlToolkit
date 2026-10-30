package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.AddElementCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetAttributeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetElementTextCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.XmlCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNodeXPath;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.TableEditOps.Placement;

/**
 * The grid's entry helpers (XMLSpy: the Siblings / Children / Values windows): what the bound
 * schema allows next to and below the selected node, and the commands that insert it. Pure
 * model code; the panel shows the lists and executes the commands through the editor context.
 *
 * <p>An entry is <em>available</em> while the schema still allows it at that place (an
 * attribute that is not yet set, an element below its {@code maxOccurs}); unavailable entries
 * are shown greyed out. <em>Mandatory</em> entries ({@code use="required"},
 * {@code minOccurs > 0}) carry a marker.</p>
 */
public final class EntryHelperModel {

    private EntryHelperModel() {
    }

    /** The kind of an entry. */
    public enum Kind {
        ELEMENT,
        ATTRIBUTE,
        VALUE
    }

    /**
     * One entry of a list.
     *
     * @param kind      element, attribute or value
     * @param name      the element / attribute name or the value
     * @param mandatory whether the schema requires it
     * @param available whether it can still be added at this place (values: not the current one)
     */
    public record Entry(Kind kind, String name, boolean mandatory, boolean available) {
    }

    /**
     * What the helpers refer to, derived from the selected row.
     *
     * @param owner         the element whose children the Children list shows ({@code null} for
     *                      attribute, text, comment … rows)
     * @param siblingParent the element whose children the Siblings list shows ({@code null} at
     *                      the document level)
     * @param reference     the node new siblings go before / after ({@code null} for an
     *                      attribute row: new siblings are appended)
     * @param attribute     the attribute name of an attribute row, else {@code null}
     */
    public record Focus(XmlElement owner, XmlElement siblingParent, XmlNode reference, String attribute) {

        /** The focus of a selected node; an attribute row passes its element and attribute name. */
        public static Focus of(XmlNode node, String attributeName) {
            if (node instanceof XmlElement element && attributeName != null) {
                return new Focus(null, element, null, attributeName);
            }
            if (node == null) {
                return new Focus(null, null, null, null);
            }
            XmlElement parent = node.getParent() instanceof XmlElement p ? p : null;
            return new Focus(node instanceof XmlElement el ? el : null, parent, node, null);
        }
    }

    // ==================== Lists ====================

    /** @return the attributes and elements the schema allows in the sibling parent */
    public static List<Entry> siblings(XmlSchemaProvider schema, Focus focus) {
        return focus.siblingParent() == null ? List.of() : contentOf(schema, focus.siblingParent());
    }

    /** @return the attributes and elements the schema allows in the selected element */
    public static List<Entry> children(XmlSchemaProvider schema, Focus focus) {
        return focus.owner() == null ? List.of() : contentOf(schema, focus.owner());
    }

    /**
     * @return the enumeration values of the selected attribute, or of the selected element when
     *         it holds a simple value (no child elements); the current value is unavailable
     */
    public static List<Entry> values(XmlSchemaProvider schema, Focus focus) {
        if (!hasSchema(schema)) {
            return List.of();
        }
        List<String> values;
        String current;
        if (focus.attribute() != null && focus.siblingParent() != null) {
            String path = XmlNodeXPath.positional(focus.siblingParent());
            values = schema.getAttributeTypeInfo(path, focus.attribute())
                    .map(XmlSchemaProvider.AttributeTypeInfo::enumerationValues).orElse(List.of());
            current = focus.siblingParent().getAttribute(focus.attribute());
        } else if (focus.owner() != null && !focus.owner().hasElementChildren()) {
            values = schema.getElementTypeInfo(XmlNodeXPath.positional(focus.owner()))
                    .map(XmlSchemaProvider.ElementTypeInfo::enumerationValues).orElse(List.of());
            current = focus.owner().getTextContent();
        } else {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        for (String value : values == null ? List.<String>of() : values) {
            entries.add(new Entry(Kind.VALUE, value, false, !value.equals(current)));
        }
        return entries;
    }

    private static boolean hasSchema(XmlSchemaProvider schema) {
        return schema != null && schema.hasSchema();
    }

    /** Attributes first (as XMLSpy lists them), then the elements in schema order. */
    private static List<Entry> contentOf(XmlSchemaProvider schema, XmlElement element) {
        if (!hasSchema(schema)) {
            return List.of();
        }
        String path = XmlNodeXPath.positional(element);
        List<Entry> entries = new ArrayList<>();
        for (String attribute : schema.getValidAttributes(path)) {
            boolean required = schema.getAttributeTypeInfo(path, attribute)
                    .map(XmlSchemaProvider.AttributeTypeInfo::isRequired).orElse(false);
            entries.add(new Entry(Kind.ATTRIBUTE, attribute, required, !element.hasAttribute(attribute)));
        }
        boolean textContent = element.hasNonWhitespaceTextContent();
        for (String child : schema.getValidChildElements(path)) {
            Optional<XmlSchemaProvider.ElementTypeInfo> info = schema.getElementTypeInfo(path + "/" + child);
            boolean mandatory = schema.isChildElementRequired(path, child);
            int max = maxOccurs(info.map(XmlSchemaProvider.ElementTypeInfo::maxOccurs).orElse("1"));
            long present = element.getChildElements().stream().filter(c -> child.equals(c.getName())).count();
            entries.add(new Entry(Kind.ELEMENT, child, mandatory, !textContent && present < max));
        }
        return entries;
    }

    /** @return the numeric maxOccurs ({@code unbounded} = no limit, unparsable = 1) */
    static int maxOccurs(String value) {
        if (value == null || value.isBlank()) {
            return 1;
        }
        if ("unbounded".equals(value.trim())) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    // ==================== Commands ====================

    /**
     * Inserts an entry of the Siblings list: an attribute is set on the sibling parent, an
     * element goes before / after the reference node or is appended.
     *
     * @return the command, or {@code null} when it cannot be inserted here
     */
    public static XmlCommand insertSibling(XmlSchemaProvider schema, Focus focus, Entry entry, Placement placement) {
        XmlElement parent = focus.siblingParent();
        if (parent == null || entry == null || !entry.available()) {
            return null;
        }
        if (entry.kind() == Kind.ATTRIBUTE) {
            return newAttribute(schema, parent, entry.name());
        }
        if (entry.kind() != Kind.ELEMENT) {
            return null;
        }
        int index = -1;
        if (placement != Placement.APPEND && focus.reference() != null && focus.reference().getParent() == parent) {
            index = parent.indexOf(focus.reference()) + (placement == Placement.BEFORE ? 0 : 1);
        }
        return new AddElementCommand(parent, new XmlElement(entry.name()), index);
    }

    /**
     * Adds an entry of the Children list to the selected element: an attribute is set, an
     * element is appended as the last child.
     *
     * @return the command, or {@code null} when it cannot be added
     */
    public static XmlCommand addChild(XmlSchemaProvider schema, Focus focus, Entry entry) {
        XmlElement owner = focus.owner();
        if (owner == null || entry == null || !entry.available()) {
            return null;
        }
        return switch (entry.kind()) {
            case ATTRIBUTE -> newAttribute(schema, owner, entry.name());
            case ELEMENT -> new AddElementCommand(owner, new XmlElement(entry.name()));
            case VALUE -> null;
        };
    }

    /**
     * Sets a value of the Values list on the selected attribute or element.
     *
     * @return the command, or {@code null} when there is nothing to set
     */
    public static XmlCommand setValue(Focus focus, Entry entry) {
        if (entry == null || entry.kind() != Kind.VALUE || !entry.available()) {
            return null;
        }
        if (focus.attribute() != null && focus.siblingParent() != null) {
            return new SetAttributeCommand(focus.siblingParent(), focus.attribute(), entry.name());
        }
        return focus.owner() != null ? new SetElementTextCommand(focus.owner(), entry.name()) : null;
    }

    /** A new attribute with the schema's fixed or default value, else empty. */
    private static XmlCommand newAttribute(XmlSchemaProvider schema, XmlElement element, String name) {
        if (element.hasAttribute(name)) {
            return null;
        }
        String value = schema == null ? "" : schema.getAttributeTypeInfo(XmlNodeXPath.positional(element), name)
                .map(info -> info.isFixed() ? info.fixedValue()
                        : info.defaultValue() != null ? info.defaultValue() : "")
                .orElse("");
        return new SetAttributeCommand(element, name, value);
    }
}
