package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * Puts the schema's top-level children into a new order (one undo step). Used for move
 * up/down, drag and drop and the explicit "Sort" action of the Schema Overview.
 *
 * @since 2.6
 */
public class ReorderSchemaChildrenCommand implements XsdCommand {

    /** The two sort orders offered for an explicit sort. */
    public enum SortMode {
        /** By component kind (attribute, attributeGroup, complexType, …), then by name. */
        TYPE_THEN_NAME("Type, then Name"),
        /** By name, then by component kind. */
        NAME_THEN_TYPE("Name, then Type");

        private final String label;

        SortMode(String label) {
            this.label = label;
        }

        /** @return the user-facing label */
        public String label() {
            return label;
        }
    }

    private final XsdSchema schema;
    private final List<XsdNode> oldOrder;
    private final List<XsdNode> newOrder;
    private final String description;

    /**
     * @param schema      the schema
     * @param newOrder    a permutation of the schema's children
     * @param description the text shown in the undo history
     */
    public ReorderSchemaChildrenCommand(XsdSchema schema, List<XsdNode> newOrder, String description) {
        if (schema == null || newOrder == null) {
            throw new IllegalArgumentException("Schema and order are required");
        }
        this.schema = schema;
        this.oldOrder = List.copyOf(schema.getChildren());
        this.newOrder = List.copyOf(newOrder);
        this.description = description;
    }

    /** @return the rule check of the new order */
    public EditCheck check() {
        if (!new HashSet<>(oldOrder).equals(new HashSet<>(newOrder)) || oldOrder.size() != newOrder.size()) {
            return EditCheck.blocked("The schema changed in the meantime.");
        }
        return SchemaLayoutRules.checkOrder(newOrder);
    }

    /** @return true when the new order differs from the current one */
    public boolean changesOrder() {
        return !oldOrder.equals(newOrder);
    }

    @Override
    public boolean execute() {
        if (!check().isAllowed() || !schema.getChildren().equals(oldOrder)) {
            return false;
        }
        schema.reorderChildren(newOrder);
        return true;
    }

    @Override
    public boolean undo() {
        if (!schema.getChildren().equals(newOrder)) {
            return false;
        }
        schema.reorderChildren(oldOrder);
        return true;
    }

    @Override
    public String getDescription() {
        return description;
    }

    /** @return the order this command sets */
    public List<XsdNode> getNewOrder() {
        return newOrder;
    }

    // ------------------------------------------------------------------ factories

    /**
     * Sorts the components. Directives and xs:defaultOpenContent keep their order at the
     * top; each annotation or comment travels with the component that follows it, trailing
     * ones stay at the end.
     */
    public static ReorderSchemaChildrenCommand sorted(XsdSchema schema, SortMode mode) {
        List<List<XsdNode>> header = new ArrayList<>();
        List<List<XsdNode>> components = new ArrayList<>();
        List<XsdNode> pending = new ArrayList<>();
        for (XsdNode child : schema.getChildren()) {
            pending.add(child);
            if (!SchemaLayoutRules.isFloating(child)) {
                (SchemaLayoutRules.isComponent(child) ? components : header).add(pending);
                pending = new ArrayList<>();
            }
        }
        Comparator<String> text = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
        Comparator<List<XsdNode>> byName = Comparator.comparing(s -> s.getLast().getName(), text);
        Comparator<List<XsdNode>> byKind = Comparator.comparing(s -> kindKey(s.getLast()), text);
        components.sort(mode == SortMode.TYPE_THEN_NAME ? byKind.thenComparing(byName) : byName.thenComparing(byKind));
        List<XsdNode> order = new ArrayList<>();
        header.forEach(order::addAll);
        components.forEach(order::addAll);
        order.addAll(pending);
        return new ReorderSchemaChildrenCommand(schema, order, "Sort schema components (" + mode.label() + ")");
    }

    private static String kindKey(XsdNode node) {
        GlobalComponentKind kind = GlobalComponentKind.of(node);
        return kind != null ? kind.xsdName() : node.getNodeType().name().toLowerCase(Locale.ROOT);
    }

    /**
     * Moves the given children one place up ({@code delta < 0}) or down; blocks of selected
     * neighbours move together.
     */
    public static ReorderSchemaChildrenCommand moved(XsdSchema schema, Collection<XsdNode> nodes, int delta) {
        return moved(schema, nodes, delta, node -> true);
    }

    /**
     * Like {@link #moved(XsdSchema, Collection, int)}, but a step skips the children that are
     * not {@code visible} (rows hidden in the Overview), so every move changes what the user sees.
     */
    public static ReorderSchemaChildrenCommand moved(XsdSchema schema, Collection<XsdNode> nodes, int delta,
                                                     java.util.function.Predicate<XsdNode> visible) {
        List<XsdNode> all = new ArrayList<>(schema.getChildren());
        List<Integer> positions = new ArrayList<>();
        List<XsdNode> order = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (visible.test(all.get(i)) || nodes.contains(all.get(i))) {
                positions.add(i);
                order.add(all.get(i));
            }
        }
        Set<XsdNode> selected = new HashSet<>(nodes);
        if (delta < 0) {
            for (int i = 1; i < order.size(); i++) {
                if (selected.contains(order.get(i)) && !selected.contains(order.get(i - 1))) {
                    order.set(i - 1, order.set(i, order.get(i - 1)));
                }
            }
        } else {
            for (int i = order.size() - 2; i >= 0; i--) {
                if (selected.contains(order.get(i)) && !selected.contains(order.get(i + 1))) {
                    order.set(i + 1, order.set(i, order.get(i + 1)));
                }
            }
        }
        for (int i = 0; i < positions.size(); i++) {
            all.set(positions.get(i), order.get(i));
        }
        return new ReorderSchemaChildrenCommand(schema, all, delta < 0 ? "Move up" : "Move down");
    }

    /**
     * Moves the given children (kept in their relative order) so they start at
     * {@code targetIndex} of the current list (drag and drop).
     */
    public static ReorderSchemaChildrenCommand movedTo(XsdSchema schema, List<XsdNode> nodes, int targetIndex) {
        List<XsdNode> current = schema.getChildren();
        Set<XsdNode> selected = new HashSet<>(nodes);
        int insertAt = 0;
        for (int i = 0; i < Math.min(targetIndex, current.size()); i++) {
            if (!selected.contains(current.get(i))) {
                insertAt++;
            }
        }
        List<XsdNode> order = new ArrayList<>();
        List<XsdNode> moving = new ArrayList<>();
        for (XsdNode child : current) {
            (selected.contains(child) ? moving : order).add(child);
        }
        order.addAll(insertAt, moving);
        return new ReorderSchemaChildrenCommand(schema, order, "Move " + moving.size() + " component(s)");
    }
}
