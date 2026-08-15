package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import javafx.scene.Node;
import javafx.scene.layout.HBox;

/**
 * Composes several named gutter contributors into the editor's single extra-gutter
 * slot ({@code EditorView.setExtraGutterFactory}), so features like the XSLT
 * debugger's breakpoint markers and the quick-fix lightbulb can coexist. A line's
 * gutter node is an {@link HBox} of every contributor's non-null node for that
 * line; contributors are keyed so each feature replaces only its own entry.
 */
public final class CompositeGutterFactory implements IntFunction<Node> {

    private final Map<String, IntFunction<Node>> contributors = new LinkedHashMap<>();

    /**
     * Registers, replaces or removes a contributor.
     *
     * @param key         stable feature key (e.g. {@code "xslt-debugger"}, {@code "quickfix"})
     * @param contributor per-line node factory, or {@code null} to remove the entry
     */
    public synchronized void set(String key, IntFunction<Node> contributor) {
        if (contributor == null) {
            contributors.remove(key);
        } else {
            contributors.put(key, contributor);
        }
    }

    /** @return {@code true} when no contributor is registered (gutter slot can collapse) */
    public synchronized boolean isEmpty() {
        return contributors.isEmpty();
    }

    @Override
    public Node apply(int line) {
        List<IntFunction<Node>> current;
        synchronized (this) {
            current = new ArrayList<>(contributors.values());
        }
        if (current.size() == 1) {
            return current.get(0).apply(line);
        }
        HBox box = new HBox(2);
        for (IntFunction<Node> contributor : current) {
            Node node = contributor.apply(line);
            if (node != null) {
                box.getChildren().add(node);
            }
        }
        return box.getChildren().isEmpty() ? null : box;
    }
}
