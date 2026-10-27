package org.fxt.freexmltoolkit.controls.v2.view;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.editor.commands.EditCheck;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdEditRules;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAll;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAny;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAnyAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttributeGroup;
import org.fxt.freexmltoolkit.controls.v2.model.XsdChoice;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdExtension;
import org.fxt.freexmltoolkit.controls.v2.model.XsdGroup;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSequence;

/**
 * Works out where a node dragged in the graphic XSD view lands: the model parent and index for a
 * drop before, after or into a target card, and whether that keeps the schema valid
 * ({@link XsdEditRules}). Pure model logic, no JavaFX, so it is unit-testable.
 *
 * @since 2.0
 */
public final class XsdDropPlanner {

    /** Where a drop lands relative to the target card. */
    public enum Position {
        /** As the sibling right before the target. */
        BEFORE,
        /** As the sibling right after the target. */
        AFTER,
        /** As the last child of the target's content (compositor or attribute container). */
        INTO
    }

    /**
     * A drop to perform (or refuse).
     *
     * @param node     the dragged node
     * @param target   the node of the card under the cursor
     * @param parent   the new parent ({@code null} when blocked before a parent was found)
     * @param index    the index in {@code parent} as {@link
     *                 org.fxt.freexmltoolkit.controls.v2.editor.commands.MoveNodeCommand} applies it
     *                 (after removing the node from its old parent)
     * @param position the drop position
     * @param copy     whether a copy is inserted instead of moving the node
     * @param check    the rule verdict
     */
    public record Plan(XsdNode node, XsdNode target, XsdNode parent, int index, Position position,
                       boolean copy, EditCheck check) {

        public boolean isBlocked() {
            return check.isBlocked();
        }
    }

    /** Share of the card height (top and bottom each) that means "before" / "after". */
    private static final double EDGE_SHARE = 0.25;

    private final XsdSchema schema;

    /**
     * @param schema the edited schema; nodes outside it (imported schemas) are read-only
     */
    public XsdDropPlanner(XsdSchema schema) {
        this.schema = schema;
    }

    /**
     * The drop position for a cursor at {@code y} over a card spanning {@code top..top+height}:
     * the upper and lower quarter mean before / after, the middle into. Without an "into" option
     * the card is split in half.
     */
    public static Position zone(double y, double top, double height, boolean intoPossible) {
        double rel = height <= 0 ? 0.5 : (y - top) / height;
        if (!intoPossible) {
            return rel < 0.5 ? Position.BEFORE : Position.AFTER;
        }
        if (rel < EDGE_SHARE) {
            return Position.BEFORE;
        }
        if (rel > 1 - EDGE_SHARE) {
            return Position.AFTER;
        }
        return Position.INTO;
    }

    /**
     * @return whether a drop into {@code target} can make sense at all (it can hold content);
     * compositor cards only accept drops into them
     */
    public static boolean acceptsInto(XsdNode target) {
        return target instanceof XsdElement || isCompositor(target) || target instanceof XsdComplexType
                || target instanceof XsdGroup g && !hasText(g.getRef())
                || target instanceof XsdAttributeGroup ag && !hasText(ag.getRef());
    }

    /** @return whether {@code target} only takes drops into it (no before/after zones) */
    public static boolean intoOnly(XsdNode target) {
        return isCompositor(target);
    }

    /**
     * Plans dropping {@code dragged} at {@code position} relative to {@code target}.
     *
     * @return the plan, or {@code null} when the drop would not change anything
     */
    public Plan plan(XsdNode dragged, XsdNode target, Position position, boolean copy) {
        if (dragged == null || target == null || position == null) {
            return null;
        }
        if (dragged == target) {
            return null;
        }
        if (target.isDescendantOf(dragged)) {
            return blocked(dragged, target, position, copy, "A node cannot be dropped into itself.");
        }
        if (!copy && !inEditedSchema(dragged)) {
            return blocked(dragged, target, position, copy,
                    "'" + label(dragged) + "' belongs to an imported schema and cannot be moved here.");
        }
        if (!inEditedSchema(target)) {
            return blocked(dragged, target, position, copy,
                    "'" + label(target) + "' belongs to an imported schema, which cannot be edited here.");
        }
        if (!isParticle(dragged) && !isAttributeKind(dragged)) {
            return blocked(dragged, target, position, copy,
                    "Only elements, compositors, group references and attributes can be dragged.");
        }

        XsdNode parent;
        int index;
        if (position == Position.INTO) {
            parent = isAttributeKind(dragged) ? attributeTarget(target) : particleTarget(dragged, target);
            if (parent == null) {
                EditCheck reason = isAttributeKind(dragged)
                        ? XsdEditRules.addAttribute(target) : XsdEditRules.addElement(target);
                return blocked(dragged, target, position, copy, reason.isBlocked() ? reason.reason()
                        : "'" + label(dragged) + "' cannot be dropped into '" + label(target) + "'.");
            }
            index = parent.getChildren().size();
        } else {
            parent = target.getParent();
            if (parent == null) {
                return blocked(dragged, target, position, copy, "Nothing can be placed next to the schema root.");
            }
            String sibling = siblingConflict(dragged, target, parent);
            if (sibling != null) {
                return blocked(dragged, target, position, copy, sibling);
            }
            index = parent.getChildren().indexOf(target) + (position == Position.AFTER ? 1 : 0);
        }

        if (dragged.getParent() instanceof XsdSchema && !(parent instanceof XsdSchema) && !copy) {
            return blocked(dragged, target, position, copy, "A global declaration cannot be moved into a "
                    + "content model; add a reference to it instead.");
        }
        if (parent instanceof XsdSchema && !(dragged.getParent() instanceof XsdSchema)) {
            return blocked(dragged, target, position, copy,
                    "A local declaration cannot be moved to the top level of the schema.");
        }

        if (!copy && dragged.getParent() == parent) {
            int oldIndex = parent.getChildren().indexOf(dragged);
            if (oldIndex >= 0 && oldIndex < index) {
                index--; // MoveNodeCommand removes the node before inserting it
            }
            if (index == oldIndex) {
                return null;
            }
        }

        EditCheck check = copy ? XsdEditRules.paste(parent, dragged) : XsdEditRules.move(dragged, parent);
        if (check.needsConfirmation()) {
            // A replacing variant needs a different command; drops only do plain placements.
            check = EditCheck.blocked(check.reason());
        }
        return new Plan(dragged, target, parent, index, position, copy, check);
    }

    /**
     * The named global types and groups whose content the plan changes and that are used by more
     * than one declaration, as user-facing descriptions ("type 'AddressType' (used 3 times)").
     * Builds the schema's reference graph, so call it once per drop, not on every mouse move.
     */
    public List<String> sharedComponents(Plan plan) {
        List<String> shared = new ArrayList<>();
        if (plan == null || plan.parent() == null) {
            return shared;
        }
        List<XsdNode> owners = new ArrayList<>();
        addOwner(owners, sharedOwner(plan.parent()));
        if (!plan.copy()) {
            addOwner(owners, sharedOwner(plan.node().getParent()));
        }
        if (owners.isEmpty()) {
            return shared;
        }
        SchemaReferenceGraph graph = SchemaReferenceGraph.build(schema);
        for (XsdNode owner : owners) {
            int uses = graph.usageCount(SchemaReferenceGraph.keyOf(owner));
            if (uses > 1) {
                String kind = owner instanceof XsdComplexType ? "type" : "group";
                shared.add(kind + " '" + owner.getName() + "' (used " + uses + " times)");
            }
        }
        return shared;
    }

    private static void addOwner(List<XsdNode> owners, XsdNode owner) {
        if (owner != null && !owners.contains(owner)) {
            owners.add(owner);
        }
    }

    /** The named top-level complex type / group / attribute group that contains {@code node}. */
    static XsdNode sharedOwner(XsdNode node) {
        for (XsdNode n = node; n != null; n = n.getParent()) {
            if (n.getParent() instanceof XsdSchema) {
                boolean named = hasText(n.getName());
                return named && (n instanceof XsdComplexType
                        || n instanceof XsdGroup g && !hasText(g.getRef())
                        || n instanceof XsdAttributeGroup ag && !hasText(ag.getRef())) ? n : null;
            }
        }
        return null;
    }

    /** The compositor a particle dropped into {@code target} goes to. */
    private static XsdNode particleTarget(XsdNode dragged, XsdNode target) {
        if (isCompositor(target)) {
            return target;
        }
        if (isCompositor(dragged)) {
            return null; // a compositor only goes into another compositor
        }
        if (target instanceof XsdComplexType || target instanceof XsdGroup g && !hasText(g.getRef())) {
            for (XsdNode child : target.getChildren()) {
                if (isCompositor(child)) {
                    return child;
                }
            }
            return null;
        }
        if (target instanceof XsdElement) {
            return XsdEditRules.contentConflict(target).isBlocked() ? null : XsdEditRules.elementContainer(target);
        }
        return null;
    }

    /** The node an attribute dropped into {@code target} goes to. */
    private static XsdNode attributeTarget(XsdNode target) {
        if (target instanceof XsdElement && XsdEditRules.contentConflict(target).isBlocked()) {
            return null;
        }
        return XsdEditRules.attributeContainer(target);
    }

    /** Why {@code dragged} cannot become a sibling of {@code target} under {@code parent}, or null. */
    private static String siblingConflict(XsdNode dragged, XsdNode target, XsdNode parent) {
        if (isAttributeKind(dragged)) {
            boolean container = parent instanceof XsdComplexType || parent instanceof XsdExtension
                    || parent instanceof XsdRestriction || parent instanceof XsdAttributeGroup;
            return container && isAttributeKind(target) ? null
                    : "An attribute can only be placed next to another attribute.";
        }
        if (isCompositor(parent) || parent instanceof XsdSchema && dragged.getParent() instanceof XsdSchema) {
            return isAttributeKind(target) ? "An element cannot be placed among attributes." : null;
        }
        return "'" + label(dragged) + "' cannot be placed next to '" + label(target) + "'.";
    }

    private boolean inEditedSchema(XsdNode node) {
        XsdNode root = node;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        return schema == null || root == schema;
    }

    private static Plan blocked(XsdNode dragged, XsdNode target, Position position, boolean copy, String reason) {
        return new Plan(dragged, target, null, -1, position, copy, EditCheck.blocked(reason));
    }

    private static boolean isParticle(XsdNode node) {
        return node instanceof XsdElement || node instanceof XsdAny || isCompositor(node)
                || node instanceof XsdGroup g && hasText(g.getRef());
    }

    private static boolean isAttributeKind(XsdNode node) {
        return node instanceof XsdAttribute || node instanceof XsdAnyAttribute
                || node instanceof XsdAttributeGroup ag && hasText(ag.getRef());
    }

    private static boolean isCompositor(XsdNode node) {
        return node instanceof XsdSequence || node instanceof XsdChoice || node instanceof XsdAll;
    }

    private static String label(XsdNode node) {
        if (hasText(node.getName())) {
            return node.getName();
        }
        return node.getClass().getSimpleName().replace("Xsd", "").toLowerCase();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isEmpty();
    }
}
