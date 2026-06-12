package org.fxt.freexmltoolkit.controls.shell;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * The slim, VS-Code-style left rail of the Unified shell. Renders one icon
 * button per {@link Activity} and keeps its selection in sync with an
 * {@link ActivitySelectionModel}. Per the Figma mockups, {@link Activity#HELP}
 * and {@link Activity#SETTINGS} are pinned to the bottom.
 * <p>
 * Visuals come from {@code unified-shell.css} via the design-token variables,
 * so the bar themes for light/dark automatically.
 */
public class ActivityBar extends VBox {

    private static final EnumSet<Activity> BOTTOM = EnumSet.of(Activity.HELP, Activity.SETTINGS);

    private final ActivitySelectionModel selectionModel;
    private final Map<Activity, ToggleButton> buttons = new EnumMap<>(Activity.class);
    private final ToggleGroup group = new ToggleGroup();
    private Runnable onUserSelect;

    public ActivityBar(ActivitySelectionModel selectionModel) {
        this.selectionModel = selectionModel;
        getStyleClass().add("fxt-activity-bar");
        setFillWidth(true);

        rebuild(group);

        syncSelection();
        selectionModel.activeProperty().addListener((obs, oldV, newV) -> syncSelection());
    }

    /**
     * Rebuilds the bar's buttons into {@code group}. The conditional
     * {@link Activity#FUNDSXML} button is only added when the FundsXML extension
     * is enabled.
     */
    private void rebuild(ToggleGroup group) {
        for (Activity a : Activity.values()) {
            if (BOTTOM.contains(a)) {
                continue;
            }
            if (a == Activity.FUNDSXML
                    && !org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlRunner.isEnabled()) {
                continue;
            }
            getChildren().add(createButton(a, group));
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        getChildren().add(spacer);

        for (Activity a : BOTTOM) {
            getChildren().add(createButton(a, group));
        }
    }

    /**
     * Rebuilds the activity bar so a change to the FundsXML enable flag adds or
     * removes its button. Selection state is re-synced from the model.
     */
    public void refresh() {
        getChildren().clear();
        buttons.clear();
        rebuild(group);
        Activity active = selectionModel.getActive();
        if (active == null || !buttons.containsKey(active)) {
            // The previously-active activity (e.g. FUNDSXML) is gone after the
            // rebuild. Fall back to the default so the side-panel listener fires
            // and swaps away the now-orphaned panel.
            selectionModel.select(Activity.defaultActivity());
        }
        syncSelection();
    }

    /**
     * Called on every BUTTON press, even when the pressed activity is already the
     * active one (which fires no model change event). The shell uses this to
     * reveal the side panel from the full-width dashboard.
     */
    public void setOnUserSelect(Runnable callback) {
        this.onUserSelect = callback;
    }

    private ToggleButton createButton(Activity activity, ToggleGroup group) {
        ToggleButton button = new ToggleButton();
        button.setId("activity-" + activity.id());
        button.setToggleGroup(group);
        button.getStyleClass().add("fxt-activity-button");
        button.setFocusTraversable(false);
        button.setUserData(activity);

        IconifyIcon icon = new IconifyIcon(activity.icon());
        icon.setIconSize(22);
        button.setGraphic(icon);

        Tooltip tooltip = new Tooltip(activity.label());
        tooltip.setShowDelay(Duration.millis(300));
        button.setTooltip(tooltip);

        // A ToggleButton in a group would otherwise allow deselecting the active one;
        // route every press through the model, which keeps exactly one active.
        button.setOnAction(e -> {
            selectionModel.select(activity);
            if (onUserSelect != null) {
                onUserSelect.run();
            }
        });

        buttons.put(activity, button);
        return button;
    }

    private void syncSelection() {
        Activity active = selectionModel.getActive();
        buttons.forEach((activity, button) -> button.setSelected(activity == active));
    }
}
