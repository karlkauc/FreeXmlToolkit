package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ObservableList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import net.sf.saxon.s9api.XdmValue;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.debugger.VariableBinding;

/**
 * The variables in scope at the current pause point, as a tree: a node value unfolds into its
 * attributes and child nodes, a sequence into its items, a map into its entries, an array into
 * its members (built when a row is expanded). Shown as the "Variables" tab of the DEBUG pane.
 * A click on a value (or Enter, or Open Value…) opens it in full, Copy Value copies it, and
 * Add to Watches adds the row's XPath ({@code $order/item[2]/@id}) as a watch.
 */
public class VariablesView extends VBox {

    private final TreeTableView<VariableTree.Node> table = new TreeTableView<>();
    private final TreeItem<VariableTree.Node> root = new TreeItem<>();
    /** Names the selected outer call stack frame; hidden while the innermost frame is shown. */
    private final Label frameInfo = new Label();
    private final Label placeholder = new Label("Not paused.");
    private Consumer<VariableBinding> onOpenValue = b -> { };
    private Consumer<VariableBinding> onCopyValue = b -> { };
    private Consumer<ValueSource> onOpenNested = s -> { };
    private Consumer<ValueSource> onCopyNested = s -> { };
    private Consumer<String> onAddToWatches = e -> { };

    public VariablesView() {
        getStyleClass().add("fxt-debug-panel");

        TreeTableColumn<VariableTree.Node, String> name = column("Name", VariableTree.Node::name, 140);
        // One line per row: serialized nodes are indented over several lines.
        TreeTableColumn<VariableTree.Node, String> value = column("Value",
                n -> n.value() == null ? "" : n.value().replaceAll("\\s*\\R\\s*", " "), 320);
        value.setCellFactory(c -> new ValueCell());
        TreeTableColumn<VariableTree.Node, String> type = column("Type", VariableTree.Node::type, 110);
        TreeTableColumn<VariableTree.Node, String> scope = column("Scope", VariableTree.Node::scope, 70);
        table.getColumns().addAll(List.of(name, value, type, scope));
        // Share the width in proportion to the preferred widths: Value gets the most.
        table.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setRoot(root);
        table.setShowRoot(false);
        table.setPlaceholder(placeholder);
        frameInfo.getStyleClass().add("fxt-debug-frame-info");
        frameInfo.setMaxWidth(Double.MAX_VALUE);
        frameInfo.setVisible(false);
        frameInfo.setManaged(false);
        table.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && selected() != null) {
                open(selected());
                e.consume();
            }
        });
        table.setContextMenu(menu());
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(frameInfo, table);
    }

    private static TreeTableColumn<VariableTree.Node, String> column(String title,
            Function<VariableTree.Node, String> text, double prefWidth) {
        TreeTableColumn<VariableTree.Node, String> column = new TreeTableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue() == null || c.getValue().getValue() == null ? "" : text.apply(c.getValue().getValue())));
        if (prefWidth > 0) {
            column.setPrefWidth(prefWidth);
        }
        return column;
    }

    /** A value cell: clickable (opens the full value), full text as tooltip. */
    private final class ValueCell extends TreeTableCell<VariableTree.Node, String> {
        private final Tooltip tip = new Tooltip();

        ValueCell() {
            setOnMouseClicked(e -> {
                VariableTree.Node node = getTableRow() == null || getTableRow().getItem() == null
                        ? null : getTableRow().getItem();
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1 && node != null
                        && getItem() != null && !getItem().isBlank()) {
                    open(node);
                }
            });
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("fxt-debug-value-cell", CHANGED_CLASS);
            VariableTree.Node node = empty || getTableRow() == null ? null : getTableRow().getItem();
            if (node != null && node.binding() != null && changed.contains(node.name())) {
                getStyleClass().add(CHANGED_CLASS);
            }
            if (empty || item == null || item.isBlank()) {
                setText(empty ? null : item);
                setTooltip(null);
            } else {
                setText(item);
                String preview = item.length() > 400 ? item.substring(0, 400) + "…" : item;
                tip.setText(preview + "\n\nClick to open the full value");
                setTooltip(tip);
                getStyleClass().add("fxt-debug-value-cell");
            }
        }
    }

    /** A tree item that builds its children when it is first expanded. */
    private static final class LazyItem extends TreeItem<VariableTree.Node> {
        private boolean loaded;

        LazyItem(VariableTree.Node node) {
            super(node);
        }

        @Override
        public boolean isLeaf() {
            return !getValue().hasChildren();
        }

        @Override
        public ObservableList<TreeItem<VariableTree.Node>> getChildren() {
            if (!loaded) {
                loaded = true;
                for (VariableTree.Node child : VariableTree.children(getValue())) {
                    super.getChildren().add(new LazyItem(child));
                }
            }
            return super.getChildren();
        }
    }

    private ContextMenu menu() {
        MenuItem openItem = item("Open Value…", "bi-box-arrow-up-right", ActionColor.NAVIGATE, () -> open(selected()));
        MenuItem copyItem = item("Copy Value", "bi-clipboard", ActionColor.TOOL, () -> copy(selected()));
        MenuItem watchItem = item("Add to Watches", "bi-eye", ActionColor.CREATE, () -> {
            VariableTree.Node node = selected();
            if (node != null && node.expr() != null) {
                onAddToWatches.accept(node.expr());
            }
        });
        ContextMenu menu = new ContextMenu(openItem, copyItem, watchItem);
        menu.setOnShowing(e -> {
            VariableTree.Node node = selected();
            openItem.setDisable(node == null);
            copyItem.setDisable(node == null);
            watchItem.setDisable(node == null || node.expr() == null);
        });
        return menu;
    }

    private static MenuItem item(String text, String icon, ActionColor color, Runnable action) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        MenuItem item = new MenuItem(text, SemanticIcon.paint(graphic, color));
        item.setOnAction(e -> action.run());
        return item;
    }

    private VariableTree.Node selected() {
        TreeItem<VariableTree.Node> item = table.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue();
    }

    private void open(VariableTree.Node node) {
        if (node == null) {
            return;
        }
        if (node.binding() != null) {
            onOpenValue.accept(node.binding());
        } else if (node.xdm() != null) {
            onOpenNested.accept(nested(node));
        }
    }

    private void copy(VariableTree.Node node) {
        if (node == null) {
            return;
        }
        if (node.binding() != null) {
            onCopyValue.accept(node.binding());
        } else {
            onCopyNested.accept(nested(node));
        }
    }

    private static ValueSource nested(VariableTree.Node node) {
        String title = node.expr() != null ? node.expr() : node.name();
        return new ValueSource(title, node.type(), "", node.xdm(), node.value(), null);
    }

    /** Shows the variables without their full values (rows do not unfold). */
    public void setVariables(List<VariableBinding> variables) {
        setVariables(variables, b -> null);
    }

    /**
     * Shows the variables; {@code valueOf} supplies each row's full value, so node, sequence, map
     * and array values unfold.
     */
    public void setVariables(List<VariableBinding> variables, Function<VariableBinding, XdmValue> valueOf) {
        setVariables(variables, valueOf, null);
    }

    /** Style class of a value that changed since the previous pause in the same frame. */
    public static final String CHANGED_CLASS = "fxt-debug-changed";

    /** Per frame scope: the variable values shown at the previous pause (bounded). */
    private final java.util.Map<String, ChangeTracker.History> previousByScope = ChangeTracker.newStore();
    /** Names of the top-level rows whose value differs from the previous pause in this scope. */
    private final java.util.Set<String> changed = new java.util.HashSet<>();

    /**
     * Shows the variables and marks the ones that changed since the previous pause in the same
     * {@code scopeKey} (the template / function frame they belong to; {@code null}: no marking).
     * A variable that is new in that scope counts as changed.
     */
    public void setVariables(List<VariableBinding> variables, Function<VariableBinding, XdmValue> valueOf,
                             String scopeKey) {
        changed.clear();
        if (scopeKey != null && variables != null) {
            java.util.Map<String, String> now = new java.util.LinkedHashMap<>();
            variables.forEach(b -> now.put(b.name(), b.value()));
            changed.addAll(ChangeTracker.record(previousByScope, scopeKey, now, true));
        }
        root.getChildren().clear();
        if (variables != null) {
            for (VariableBinding b : variables) {
                XdmValue value;
                try {
                    value = valueOf == null ? null : valueOf.apply(b);
                } catch (RuntimeException e) {
                    value = null;
                }
                root.getChildren().add(new LazyItem(VariableTree.Node.of(b, value)));
            }
        }
    }

    /** @return the names of the rows marked as changed (tests). */
    public java.util.Set<String> getChangedNames() {
        return java.util.Set.copyOf(changed);
    }

    /** Forgets the values of earlier pauses (a new run). */
    public void resetHistory() {
        previousByScope.clear();
        changed.clear();
    }

    public void clear() {
        root.getChildren().clear();
        placeholder.setText("Not paused.");
        setFrameInfo(null);
    }

    /**
     * Names the call stack frame whose variables are shown; {@code null} hides the banner
     * (the innermost frame, i.e. the pause itself).
     */
    public void setFrameInfo(String text) {
        boolean show = text != null && !text.isBlank();
        frameInfo.setText(show ? text : "");
        frameInfo.setVisible(show);
        frameInfo.setManaged(show);
    }

    /** @return the frame banner text, or {@code null} while hidden. */
    public String getFrameInfo() {
        return frameInfo.isVisible() ? frameInfo.getText() : null;
    }

    /** Text shown when the table has no rows. */
    public void setPlaceholderText(String text) {
        placeholder.setText(text);
    }

    /** @return the name of the top-level row at {@code index} (tests). */
    public String getNameAt(int index) {
        return root.getChildren().get(index).getValue().name();
    }

    /** @return the number of top-level rows (variables). */
    public int getRowCount() {
        return root.getChildren().size();
    }

    /** Invoked with the variable row whose full value should be opened (click on a value, Enter, menu). */
    public void setOnOpenValue(Consumer<VariableBinding> handler) {
        this.onOpenValue = handler == null ? b -> { } : handler;
    }

    /** Invoked with the variable row whose full value should be copied (context menu). */
    public void setOnCopyValue(Consumer<VariableBinding> handler) {
        this.onCopyValue = handler == null ? b -> { } : handler;
    }

    /** Invoked to open a nested value (an attribute, a child node, a sequence item…). */
    public void setOnOpenNested(Consumer<ValueSource> handler) {
        this.onOpenNested = handler == null ? s -> { } : handler;
    }

    /** Invoked to copy a nested value. */
    public void setOnCopyNested(Consumer<ValueSource> handler) {
        this.onCopyNested = handler == null ? s -> { } : handler;
    }

    /** Invoked with the XPath of the row to add as a watch (context menu "Add to Watches"). */
    public void setOnAddToWatches(Consumer<String> handler) {
        this.onAddToWatches = handler == null ? e -> { } : handler;
    }

    /**
     * @return whether the row's name is a valid watch expression: {@code .}, {@code $name},
     *         {@code position()}, {@code last()} — not an unnamed slot ({@code $$slot3}), a tunnel
     *         row or another context row
     */
    static boolean isWatchable(VariableBinding b) {
        String n = b == null ? null : b.name();
        return n != null && (n.equals(".") || n.equals("position()") || n.equals("last()")
                || (n.matches("\\$[\\p{L}_][\\w.:-]*")));
    }

    /** Adds row {@code index} as a watch, as the context menu would (tests). */
    void fireAddToWatches(int index) {
        VariableTree.Node node = root.getChildren().get(index).getValue();
        if (node.expr() != null) {
            onAddToWatches.accept(node.expr());
        }
    }

    /** Opens the value of row {@code index} as a click would (tests). */
    void fireOpen(int index) {
        open(root.getChildren().get(index).getValue());
    }

    /** The tree (tests). */
    TreeTableView<VariableTree.Node> tree() {
        return table;
    }
}
