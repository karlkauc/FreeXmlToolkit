package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph;
import org.fxt.freexmltoolkit.controls.v2.model.*;

/**
 * Renames a global component and, optionally, every reference to it in the schema's own
 * files ({@code type}, {@code base}, {@code ref}, {@code substitutionGroup}, {@code itemType},
 * {@code memberTypes}) in one undo step. References inside imported schemas or included
 * files are not changed; {@link #skippedReferences()} counts them.
 *
 * @since 2.6
 */
public class RenameGlobalComponentCommand implements XsdCommand {

    /** One attribute value that names the renamed component. */
    private record Edit(Supplier<String> getter, Consumer<String> setter, XsdNode referrer, boolean mirrorsName) {
    }

    private final XsdNode component;
    private final String oldName;
    private final String newName;
    private final List<Edit> edits = new ArrayList<>();
    private final List<String> oldValues = new ArrayList<>();
    private final XsdSchema schema;
    private int skipped;

    /**
     * @param component        the global component
     * @param newName          its new name
     * @param updateReferences whether to update the references too
     */
    public RenameGlobalComponentCommand(XsdNode component, String newName, boolean updateReferences) {
        if (component == null) {
            throw new IllegalArgumentException("Component is required");
        }
        this.component = component;
        this.oldName = component.getName();
        this.newName = newName == null ? "" : newName.trim();
        this.schema = component.getParent() instanceof XsdSchema s ? s : null;
        if (updateReferences && schema != null) {
            collect(schema);
        }
    }

    /** @return the number of references found in the schema's own files */
    public static int countReferences(XsdNode component) {
        if (!(component.getParent() instanceof XsdSchema)) {
            return 0;
        }
        return new RenameGlobalComponentCommand(component, component.getName(), true).edits.size();
    }

    private void collect(XsdSchema schema) {
        SchemaReferenceGraph.ComponentKey key = SchemaReferenceGraph.keyOf(component);
        if (key == null) {
            return;
        }
        for (SchemaReferenceGraph.Reference reference : SchemaReferenceGraph.build(schema).referencesTo(key)) {
            XsdNode referrer = reference.referrer();
            if (reference.fromImport() || referrer.isFromInclude()) {
                skipped++;
                continue;
            }
            Edit edit = editFor(referrer, reference.type());
            if (edit != null) {
                edits.add(edit);
            }
        }
    }

    private static Edit editFor(XsdNode referrer, org.fxt.freexmltoolkit.controls.v2.editor.usage.UsageReferenceType type) {
        return switch (type) {
            case ELEMENT_TYPE -> referrer instanceof XsdElement e ? new Edit(e::getType, e::setType, e, false) : null;
            case ATTRIBUTE_TYPE -> referrer instanceof XsdAttribute a ? new Edit(a::getType, a::setType, a, false) : null;
            case RESTRICTION_BASE -> referrer instanceof XsdRestriction r ? new Edit(r::getBase, r::setBase, r, false) : null;
            case EXTENSION_BASE -> referrer instanceof XsdExtension x ? new Edit(x::getBase, x::setBase, x, false) : null;
            case LIST_ITEM_TYPE -> referrer instanceof XsdList l ? new Edit(l::getItemType, l::setItemType, l, false) : null;
            case ALTERNATIVE_TYPE -> referrer instanceof XsdAlternative a ? new Edit(a::getType, a::setType, a, false) : null;
            case UNION_MEMBER_TYPE -> referrer instanceof XsdUnion u ? new Edit(
                    () -> u.getMemberTypes() == null ? null : String.join(" ", u.getMemberTypes()),
                    v -> u.setMemberTypes(v == null || v.isBlank() ? new ArrayList<>()
                            : new ArrayList<>(List.of(v.trim().split("\\s+")))),
                    u, false) : null;
            case ELEMENT_REF -> referrer instanceof XsdElement e ? new Edit(e::getRef, e::setRef, e, true) : null;
            case ATTRIBUTE_REF -> referrer instanceof XsdAttribute a ? new Edit(a::getRef, a::setRef, a, true) : null;
            case GROUP_REF -> referrer instanceof XsdGroup g ? new Edit(g::getRef, g::setRef, g, false) : null;
            case ATTRIBUTE_GROUP_REF -> referrer instanceof XsdAttributeGroup g ? new Edit(g::getRef, g::setRef, g, false) : null;
            case SUBSTITUTION_GROUP -> referrer instanceof XsdElement e
                    ? new Edit(e::getSubstitutionGroup, e::setSubstitutionGroup, e, false) : null;
        };
    }

    /** @return {@code value} with each whitespace-separated QName whose local part is the old name renamed */
    private String renamed(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (String token : value.trim().split("\\s+")) {
            int colon = token.indexOf(':');
            String local = token.substring(colon + 1);
            if (!out.isEmpty()) {
                out.append(' ');
            }
            boolean ours = colon < 0 || java.util.Objects.equals(
                    schema.getNamespaces().get(token.substring(0, colon)), schema.getTargetNamespace());
            out.append(ours && local.equals(oldName) ? token.substring(0, colon + 1) + newName : token);
        }
        return out.toString();
    }

    /** @return the rule check of the new name */
    public EditCheck check() {
        return SchemaLayoutRules.rename(component, newName);
    }

    /** @return how many references this command updates */
    public int referenceCount() {
        return edits.size();
    }

    /** @return how many references inside imported/included files stay unchanged */
    public int skippedReferences() {
        return skipped;
    }

    @Override
    public boolean execute() {
        if (!check().isAllowed()) {
            return false;
        }
        oldValues.clear();
        for (Edit edit : edits) {
            String old = edit.getter().get();
            oldValues.add(old);
            String value = renamed(old);
            edit.setter().accept(value);
            if (edit.mirrorsName() && old != null && old.equals(edit.referrer().getName())) {
                edit.referrer().setName(value);
            }
        }
        component.setName(newName);
        return true;
    }

    @Override
    public boolean undo() {
        component.setName(oldName);
        for (int i = edits.size() - 1; i >= 0; i--) {
            Edit edit = edits.get(i);
            String current = edit.getter().get();
            edit.setter().accept(oldValues.get(i));
            if (edit.mirrorsName() && current != null && current.equals(edit.referrer().getName())) {
                edit.referrer().setName(oldValues.get(i));
            }
        }
        return true;
    }

    @Override
    public String getDescription() {
        return "Rename '" + oldName + "' to '" + newName + "'"
                + (edits.isEmpty() ? "" : " (" + edits.size() + " reference" + (edits.size() == 1 ? "" : "s") + ")");
    }
}
