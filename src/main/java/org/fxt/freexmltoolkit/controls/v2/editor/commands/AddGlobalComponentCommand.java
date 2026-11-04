package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * Adds a top-level component (element, type, group, attribute group, attribute, notation
 * or annotation) to the schema at a given position.
 * <p>
 * The index is clamped so components stay behind the directives (imports, includes, …);
 * an index of {@code -1} appends after the last component of the same kind, or at the end.
 *
 * @since 2.6
 */
public class AddGlobalComponentCommand implements XsdCommand {

    private final XsdSchema schema;
    private final GlobalComponentKind kind;
    private final String name;
    private final int requestedIndex;
    private XsdNode added;

    /**
     * @param schema the schema
     * @param kind   the component kind
     * @param name   the name (ignored for annotations)
     * @param index  the insert position among the schema's children, or -1 for the default
     */
    public AddGlobalComponentCommand(XsdSchema schema, GlobalComponentKind kind, String name, int index) {
        if (schema == null || kind == null) {
            throw new IllegalArgumentException("Schema and kind are required");
        }
        this.schema = schema;
        this.kind = kind;
        this.name = name == null ? "" : name.trim();
        this.requestedIndex = index;
    }

    @Override
    public boolean execute() {
        if (!SchemaLayoutRules.addComponent(schema, kind, name).isAllowed()) {
            return false;
        }
        if (added == null) {
            added = kind.create(name, XsdEditRules.xsdPrefix(schema));
        }
        schema.addChild(insertIndex(), added);
        return true;
    }

    private int insertIndex() {
        List<XsdNode> children = schema.getChildren();
        int index = requestedIndex;
        if (index < 0) {
            index = children.size();
            for (int i = children.size() - 1; i >= 0; i--) {
                if (GlobalComponentKind.of(children.get(i)) == kind) {
                    index = i + 1;
                    break;
                }
            }
        }
        index = Math.min(index, children.size());
        if (kind != GlobalComponentKind.ANNOTATION) {
            index = Math.max(index, SchemaLayoutRules.firstComponentIndex(schema));
        }
        return index;
    }

    @Override
    public boolean undo() {
        return added != null && schema.removeChild(added);
    }

    @Override
    public String getDescription() {
        return kind.isNamed() ? "Add " + kind.label().toLowerCase() + " '" + name + "'"
                : "Add " + kind.label().toLowerCase();
    }

    /** @return the created node (after execution) */
    public XsdNode getAdded() {
        return added;
    }
}
