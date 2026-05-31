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

    public ActivityBar(ActivitySelectionModel selectionModel) {
        this.selectionModel = selectionModel;
        getStyleClass().add("fxt-activity-bar");
        setFillWidth(true);

        ToggleGroup group = new ToggleGroup();

        for (Activity a : Activity.values()) {
            if (BOTTOM.contains(a)) {
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

        syncSelection();
        selectionModel.activeProperty().addListener((obs, oldV, newV) -> syncSelection());
    }

    private ToggleButton createButton(Activity activity, ToggleGroup group) {
        ToggleButton button = new ToggleButton();
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
        button.setOnAction(e -> selectionModel.select(activity));

        buttons.put(activity, button);
        return button;
    }

    private void syncSelection() {
        Activity active = selectionModel.getActive();
        buttons.forEach((activity, button) -> button.setSelected(activity == active));
    }
}
