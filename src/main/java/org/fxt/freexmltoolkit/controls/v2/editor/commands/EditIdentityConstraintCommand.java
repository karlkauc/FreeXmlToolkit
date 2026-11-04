package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdField;
import org.fxt.freexmltoolkit.controls.v2.model.XsdIdentityConstraint;
import org.fxt.freexmltoolkit.controls.v2.model.XsdKeyRef;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSelector;

/**
 * Edits an identity constraint (xs:key / xs:keyref / xs:unique) in one undo step: name,
 * selector, the list of fields and, for a keyref, {@code refer}.
 *
 * @since 2.6
 */
public class EditIdentityConstraintCommand implements XsdCommand {

    private record State(String name, String selector, List<String> fields, String refer) {
    }

    private final XsdIdentityConstraint constraint;
    private final State oldState;
    private final State newState;

    /**
     * @param constraint the constraint
     * @param name       the new name
     * @param selector   the new selector XPath
     * @param fields     the new field XPaths (blank entries are dropped)
     * @param refer      the new refer (keyrefs only)
     */
    public EditIdentityConstraintCommand(XsdIdentityConstraint constraint, String name, String selector,
                                         List<String> fields, String refer) {
        if (constraint == null) {
            throw new IllegalArgumentException("Constraint is required");
        }
        this.constraint = constraint;
        this.oldState = stateOf(constraint);
        List<String> cleaned = fields == null ? List.of()
                : fields.stream().filter(f -> f != null && !f.isBlank()).map(String::trim).toList();
        this.newState = new State(trim(name), trim(selector), cleaned,
                constraint instanceof XsdKeyRef ? trim(refer) : null);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static State stateOf(XsdIdentityConstraint constraint) {
        XsdSelector selector = constraint.getSelector();
        List<String> fields = new ArrayList<>();
        for (XsdField field : constraint.getFields()) {
            fields.add(field.getXpath());
        }
        return new State(constraint.getName(), selector != null ? selector.getXpath() : null, fields,
                constraint instanceof XsdKeyRef keyRef ? keyRef.getRefer() : null);
    }

    /** @return the rule check: name, selector, at least one field, refer of a keyref */
    public EditCheck check() {
        if (newState.name() == null || !XsdEditRules.isNcName(newState.name())) {
            return EditCheck.blocked("Enter a valid name for the constraint.");
        }
        if (newState.selector() == null || newState.selector().isEmpty()) {
            return EditCheck.blocked("Enter the selector XPath.");
        }
        if (newState.fields().isEmpty()) {
            return EditCheck.blocked("Enter at least one field XPath.");
        }
        if (constraint instanceof XsdKeyRef && (newState.refer() == null || newState.refer().isEmpty())) {
            return EditCheck.blocked("A keyref must refer to a key or unique constraint.");
        }
        return EditCheck.allowed();
    }

    @Override
    public boolean execute() {
        if (!check().isAllowed()) {
            return false;
        }
        apply(newState);
        return true;
    }

    @Override
    public boolean undo() {
        apply(oldState);
        return true;
    }

    private void apply(State state) {
        constraint.setName(state.name());
        XsdSelector selector = constraint.getSelector();
        if (selector == null) {
            selector = new XsdSelector(state.selector());
            constraint.addChild(0, selector);
        } else {
            selector.setXpath(state.selector());
        }
        List<XsdField> current = constraint.getFields();
        for (int i = 0; i < Math.max(current.size(), state.fields().size()); i++) {
            if (i < current.size() && i < state.fields().size()) {
                current.get(i).setXpath(state.fields().get(i));
            } else if (i < state.fields().size()) {
                constraint.addChild(new XsdField(state.fields().get(i)));
            } else {
                constraint.removeChild(current.get(i));
            }
        }
        if (constraint instanceof XsdKeyRef keyRef) {
            keyRef.setRefer(state.refer());
        }
    }

    @Override
    public String getDescription() {
        return "Edit " + constraint.getNodeType().name().toLowerCase() + " '" + oldState.name() + "'";
    }

    /** @return the constraint being edited */
    public XsdNode getConstraint() {
        return constraint;
    }
}
