package org.fxt.freexmltoolkit.controls.shell;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Objects;

/**
 * Tracks the currently active {@link Activity} in the Unified shell.
 * <p>
 * Exposes a JavaFX {@link ObjectProperty} so the Activity Bar (selection
 * highlight) and the side-panel host can bind reactively: selecting an activity
 * swaps the side panel. Re-selecting the active activity is a no-op (no event),
 * matching JavaFX property semantics.
 */
public class ActivitySelectionModel {

    private final ObjectProperty<Activity> active =
            new SimpleObjectProperty<>(this, "active", Activity.defaultActivity());

    /** @return the observable active-activity property. */
    public ObjectProperty<Activity> activeProperty() {
        return active;
    }

    /** @return the currently active activity. */
    public Activity getActive() {
        return active.get();
    }

    /**
     * Selects the given activity, notifying observers if it changed.
     *
     * @param activity the activity to activate (non-null)
     */
    public void select(Activity activity) {
        active.set(Objects.requireNonNull(activity, "activity"));
    }
}
