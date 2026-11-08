package org.fxt.freexmltoolkit.plugin;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import javafx.scene.Node;

import org.fxt.freexmltoolkit.controls.theme.Workflow;

/**
 * An activity contributed by a plugin: a button in the Activity Bar (above Settings) and an entry in
 * View → Activities, which show the side panel the factory creates (once, on the JavaFX thread).
 *
 * @param id       a stable id, unique among all activities (the button's node id is {@code activity-<id>})
 * @param label    the tooltip and label
 * @param icon     a Bootstrap icon literal ({@code bi-…})
 * @param workflow the colour family of the button and panel
 * @param panel    creates the side panel
 * @param visible  whether the activity is shown now (asked again when settings change)
 */
public record PluginActivity(String id, String label, String icon, Workflow workflow,
                             Function<PluginUi, Node> panel, BooleanSupplier visible) {

    public PluginActivity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(panel, "panel");
        icon = icon == null ? "bi-puzzle" : icon;
        workflow = workflow == null ? Workflow.NEUTRAL : workflow;
        visible = visible == null ? () -> true : visible;
    }

    /** An activity that is always shown, in the neutral colour family. */
    public PluginActivity(String id, String label, String icon, Function<PluginUi, Node> panel) {
        this(id, label, icon, Workflow.NEUTRAL, panel, null);
    }
}
