package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import java.util.LinkedHashMap;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;

/**
 * Command to set an element's namespace prefix and URI.
 *
 * <p>Besides updating the model's prefix/URI (which drives the serialized qualified name
 * {@code prefix:name}), this also maintains the matching {@code xmlns[:prefix]="uri"} declaration
 * attribute so the namespace actually round-trips to the text — the serializer emits attributes
 * verbatim and does not synthesize declarations. Clearing the URI removes the declaration; changing
 * the prefix drops the stale declaration. Undo restores the previous prefix, URI and the full
 * attribute set.</p>
 *
 * @since 2.0
 */
public class SetElementNamespaceCommand implements XmlCommand {

    private final XmlElement element;
    private final String newPrefix;
    private final String newUri;
    private final String oldPrefix;
    private final String oldUri;
    private final LinkedHashMap<String, String> oldAttributes;
    private boolean executed = false;

    public SetElementNamespaceCommand(XmlElement element, String prefix, String uri) {
        this.element = element;
        this.newPrefix = blankToNull(prefix);
        this.newUri = blankToNull(uri);
        this.oldPrefix = element.getNamespacePrefix();
        this.oldUri = element.getNamespaceURI();
        this.oldAttributes = new LinkedHashMap<>(element.getAttributes());
    }

    @Override
    public boolean execute() {
        element.setNamespacePrefix(newPrefix);
        element.setNamespaceURI(newUri);

        String oldKey = xmlnsKey(oldPrefix);
        String newKey = xmlnsKey(newPrefix);
        if (!oldKey.equals(newKey)) {
            element.removeAttribute(oldKey); // drop the stale declaration for the previous prefix
        }
        if (newUri != null) {
            element.setAttribute(newKey, newUri);
        } else {
            element.removeAttribute(newKey);
        }
        executed = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!executed) {
            return false;
        }
        element.setNamespacePrefix(oldPrefix);
        element.setNamespaceURI(oldUri);
        element.clearAttributes();
        oldAttributes.forEach(element::setAttribute);
        executed = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Set Namespace on '" + element.getName() + "'";
    }

    private static String xmlnsKey(String prefix) {
        return (prefix != null && !prefix.isEmpty()) ? "xmlns:" + prefix : "xmlns";
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    @Override
    public String toString() {
        return getDescription();
    }
}
