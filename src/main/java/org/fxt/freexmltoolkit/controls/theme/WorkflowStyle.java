package org.fxt.freexmltoolkit.controls.theme;

import javafx.scene.Node;

/**
 * Attaches the workflow scope class ({@code fxt-wf-<id>}) to a node so the scoped rules in
 * {@code unified-shell.css} colour its title, primary action, row hover and badges. A node
 * carries at most one workflow class.
 */
public final class WorkflowStyle {

    private WorkflowStyle() {
    }

    /** Removes any previous {@code fxt-wf-*} class and adds {@code workflow}'s; {@code null} only clears. */
    public static void apply(Node node, Workflow workflow) {
        if (node == null) {
            return;
        }
        clear(node);
        if (workflow != null) {
            node.getStyleClass().add(workflow.cssClass());
        }
    }

    /** Removes every {@code fxt-wf-*} class from {@code node}. */
    public static void clear(Node node) {
        if (node != null) {
            node.getStyleClass().removeIf(c -> c.startsWith(Workflow.CSS_CLASS_PREFIX));
        }
    }
}
