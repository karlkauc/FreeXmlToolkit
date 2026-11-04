package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdAnyAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAssert;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttributeGroup;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Adds an {@code <xs:attributeGroup ref="…"/>} to a complex type, an extension/restriction
 * or an attribute group, in front of a trailing xs:anyAttribute / xs:assert.
 *
 * @since 2.6
 */
public class AddAttributeGroupRefCommand implements XsdCommand {

    private final XsdNode container;
    private final XsdAttributeGroup created = new XsdAttributeGroup();

    /**
     * @param owner the element, complex type, extension/restriction or attribute group
     * @param ref   the referenced attribute group (QName)
     */
    public AddAttributeGroupRefCommand(XsdNode owner, String ref) {
        this.container = XsdEditRules.attributeContainer(owner);
        if (container == null) {
            throw new IllegalArgumentException("This node cannot hold attributes");
        }
        created.setRef(ref == null ? "" : ref.trim());
    }

    /** @return the rule check: a ref is required, an attribute group may not reference itself */
    public EditCheck check() {
        String ref = created.getRef();
        if (ref == null || ref.isBlank()) {
            return EditCheck.blocked("Choose the attribute group to reference.");
        }
        if (container instanceof XsdAttributeGroup group
                && ref.substring(ref.indexOf(':') + 1).equals(group.getName())) {
            return EditCheck.blocked("An attribute group cannot reference itself.");
        }
        return EditCheck.allowed();
    }

    @Override
    public boolean execute() {
        if (!check().isAllowed()) {
            return false;
        }
        List<XsdNode> children = container.getChildren();
        int index = children.size();
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) instanceof XsdAnyAttribute || children.get(i) instanceof XsdAssert) {
                index = i;
                break;
            }
        }
        container.addChild(index, created);
        return true;
    }

    @Override
    public boolean undo() {
        return container.removeChild(created);
    }

    @Override
    public String getDescription() {
        return "Add attribute group reference '" + created.getRef() + "'";
    }

    /** @return the new reference node */
    public XsdAttributeGroup getCreated() {
        return created;
    }
}
