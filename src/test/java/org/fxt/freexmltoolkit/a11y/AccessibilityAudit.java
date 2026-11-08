package org.fxt.freexmltoolkit.a11y;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;

/**
 * Walks a scene graph and reports what a screen reader or a keyboard user would miss: buttons
 * without a name (no text and no accessible text), input fields without a label (no accessible
 * text, no {@code Label.labelFor}), buttons the keyboard cannot reach, and canvas-drawn content.
 */
final class AccessibilityAudit {

    enum Kind { UNNAMED_BUTTON, UNLABELED_INPUT, NOT_KEYBOARD_REACHABLE, CANVAS }

    record Finding(Kind kind, String where, String what) {
    }

    private final List<Finding> findings = new ArrayList<>();
    private final Map<Node, Label> labelled = new LinkedHashMap<>();

    static List<Finding> audit(Parent root, String where) {
        AccessibilityAudit audit = new AccessibilityAudit();
        audit.collectLabels(root);
        audit.visit(root, where);
        return audit.findings;
    }

    private void collectLabels(Node node) {
        if (node instanceof Label label && label.getLabelFor() != null) {
            labelled.put(label.getLabelFor(), label);
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(this::collectLabels);
        }
    }

    private void visit(Node node, String where) {
        if (!node.isVisible()) {
            return;
        }
        if (node instanceof Canvas canvas && canvas.getWidth() > 50 && canvas.getHeight() > 50) {
            findings.add(new Finding(Kind.CANVAS, where, describe(node)));
        }
        if (node instanceof ButtonBase button && !(node.getParent() instanceof ComboBoxBase<?>)
                && !isSkinPart(node)) {
            if (blank(button.getText()) && blank(button.getAccessibleText())) {
                findings.add(new Finding(Kind.UNNAMED_BUTTON, where, describe(node)));
            }
            if (!button.isFocusTraversable() && !button.isDisabled()) {
                findings.add(new Finding(Kind.NOT_KEYBOARD_REACHABLE, where, describe(node)));
            }
        }
        if ((node instanceof TextInputControl || node instanceof ComboBoxBase<?> || node instanceof ChoiceBox<?>
                || node instanceof Spinner<?>) && !isSkinPart(node) && !node.isDisabled()) {
            Control control = (Control) node;
            boolean named = !blank(control.getAccessibleText()) || labelled.containsKey(node)
                    || (node instanceof TextInputControl t && !blank(t.getPromptText()))
                    || (node instanceof ComboBoxBase<?> c && !blank(c.getPromptText()));
            if (!named) {
                findings.add(new Finding(Kind.UNLABELED_INPUT, where, describe(node)));
            }
        }
        if (node instanceof Parent parent && !(node instanceof Control control && isEditorOrList(control))) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                visit(child, where);
            }
        }
    }

    /** Text editors, lists and tables: their cells are virtual content, not part of the audit. */
    private static boolean isEditorOrList(Control control) {
        String name = control.getClass().getName();
        return name.contains("richtext") || name.contains("CodeArea") || control instanceof javafx.scene.control.ListView<?>
                || control instanceof javafx.scene.control.TableView<?> || control instanceof javafx.scene.control.TreeView<?>
                || control instanceof javafx.scene.control.TreeTableView<?>;
    }

    /** Buttons that are parts of another control's skin (spinner arrows, scroll bars, tab headers…). */
    private static boolean isSkinPart(Node node) {
        for (Parent p = node.getParent(); p != null; p = p.getParent()) {
            if (p instanceof javafx.scene.control.ScrollBar || p instanceof Spinner<?> || p instanceof ComboBoxBase<?>
                    || p.getClass().getName().contains("TabHeader") || p.getStyleClass().contains("tab-header-area")) {
                return true;
            }
        }
        return false;
    }

    static String describe(Node node) {
        StringBuilder text = new StringBuilder(node.getClass().getSimpleName());
        if (node.getId() != null) {
            text.append('#').append(node.getId());
        } else if (!node.getStyleClass().isEmpty()) {
            text.append('.').append(String.join(".", node.getStyleClass()));
        }
        if (node instanceof Control control) {
            Tooltip tooltip = control.getTooltip();
            if (tooltip != null && !blank(tooltip.getText())) {
                text.append(" tooltip=\"").append(tooltip.getText().lines().findFirst().orElse("")).append('"');
            }
        }
        return text.toString();
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
