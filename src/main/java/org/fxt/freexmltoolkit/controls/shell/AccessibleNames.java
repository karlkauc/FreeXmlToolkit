package org.fxt.freexmltoolkit.controls.shell;

import javafx.animation.PauseTransition;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Gives controls the names screen readers announce, where the layout leaves them without one:
 *
 * <ul>
 *   <li>a button without text (icon-only) is named after its tooltip, or after the first label
 *       inside it (cards);</li>
 *   <li>an input field without a label is labelled by the {@link Label} right before it in the same
 *       container ({@link Label#setLabelFor}).</li>
 * </ul>
 *
 * Installed once for every window of the application ({@link #installGlobally()}); each scene is
 * walked when it shows and after its content changes (at most once a second). Text
 * editors, lists and tables are not walked: their cells come and go while scrolling.
 */
public final class AccessibleNames {

    private static final String INSTALLED = "fxt.accessibleNames";

    private AccessibleNames() {
    }

    /** Names the controls of every window, now and of those opened later. */
    public static void installGlobally() {
        Window.getWindows().forEach(AccessibleNames::install);
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                change.getAddedSubList().forEach(AccessibleNames::install);
            }
        });
    }

    private static void install(Window window) {
        if (window.getProperties().putIfAbsent(INSTALLED, Boolean.TRUE) != null) {
            return;
        }
        if (window.getScene() != null) {
            install(window.getScene());
        }
        window.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) {
                install(scene);
            }
        });
    }

    /** Names the controls of a scene now and after later changes (debounced). */
    public static void install(Scene scene) {
        if (scene.getProperties().putIfAbsent(INSTALLED, Boolean.TRUE) != null) {
            return;
        }
        // Layout pulses follow every change of the scene graph (and a blinking caret): at most one
        // walk per second while they come, plus one after the last
        long[] last = {0};
        PauseTransition trailing = new PauseTransition(Duration.millis(THROTTLE_MS));
        trailing.setOnFinished(e -> walk(scene, last));
        scene.addPostLayoutPulseListener(() -> {
            if (System.currentTimeMillis() - last[0] >= THROTTLE_MS) {
                walk(scene, last);
            } else {
                trailing.playFromStart();
            }
        });
        scene.focusOwnerProperty().addListener((obs, old, focused) -> {
            if (focused != null) {
                name(focused);
            }
        });
        if (scene.getRoot() != null) {
            apply(scene.getRoot());
        }
    }

    private static final long THROTTLE_MS = 1000;

    private static void walk(Scene scene, long[] last) {
        last[0] = System.currentTimeMillis();
        if (scene.getRoot() != null) {
            apply(scene.getRoot());
        }
    }

    /** Names every control below {@code root}. */
    public static void apply(Parent root) {
        visit(root);
    }

    private static void visit(Node node) {
        name(node);
        if (node instanceof Parent parent && !(node instanceof Control control && isVirtual(control))) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                visit(child);
            }
        }
    }

    private static boolean isVirtual(Control control) {
        String name = control.getClass().getName();
        return name.contains("richtext") || control instanceof javafx.scene.control.ListView<?>
                || control instanceof javafx.scene.control.TableView<?> || control instanceof javafx.scene.control.TreeView<?>
                || control instanceof javafx.scene.control.TreeTableView<?>;
    }

    /** Names one control if it has no name yet. */
    static void name(Node node) {
        if (node instanceof ButtonBase button) {
            if (blank(button.getText()) && blank(button.getAccessibleText())) {
                String name = tooltipText(button.getTooltip());
                if (name == null) {
                    name = firstLabel(button);
                }
                if (name != null) {
                    button.setAccessibleText(name);
                }
            }
        } else if (isInput(node) && blank(((Control) node).getAccessibleText())) {
            Label label = labelBefore(node);
            if (label != null && label.getLabelFor() == null) {
                label.setLabelFor(node);
            }
        }
    }

    private static boolean isInput(Node node) {
        return node instanceof TextInputControl || node instanceof ComboBoxBase<?> || node instanceof ChoiceBox<?>
                || node instanceof Spinner<?>;
    }

    /** @return the label right before the field in its container (also across one wrapping container) */
    private static Label labelBefore(Node node) {
        Node current = node;
        for (int depth = 0; depth < 2 && current.getParent() != null; depth++) {
            Parent parent = current.getParent();
            var siblings = parent.getChildrenUnmodifiable();
            int index = siblings.indexOf(current);
            if (index > 0 && siblings.get(index - 1) instanceof Label label && !blank(label.getText())) {
                return label;
            }
            if (parent instanceof javafx.scene.layout.GridPane) {
                Integer row = javafx.scene.layout.GridPane.getRowIndex(current);
                for (Node sibling : siblings) {
                    if (sibling instanceof Label label && !blank(label.getText())
                            && java.util.Objects.equals(javafx.scene.layout.GridPane.getRowIndex(sibling), row)
                            && orZero(javafx.scene.layout.GridPane.getColumnIndex(sibling))
                            < orZero(javafx.scene.layout.GridPane.getColumnIndex(current))) {
                        return label;
                    }
                }
            }
            if (siblings.size() > 1) {
                return null; // the field shares its container with others: no closer guess
            }
            current = parent;
        }
        return null;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String firstLabel(Node node) {
        if (node instanceof Labeled labeled) {
            if (!(node instanceof ButtonBase) && !blank(labeled.getText())) {
                return labeled.getText();
            }
            // The graphic is a child only once the skin exists
            if (labeled.getGraphic() != null && !labeled.getChildrenUnmodifiable().contains(labeled.getGraphic())) {
                String text = firstLabel(labeled.getGraphic());
                if (text != null) {
                    return text;
                }
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                String text = firstLabel(child);
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String tooltipText(Tooltip tooltip) {
        if (tooltip == null || blank(tooltip.getText())) {
            return null;
        }
        return tooltip.getText().lines().findFirst().orElse("").strip();
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
