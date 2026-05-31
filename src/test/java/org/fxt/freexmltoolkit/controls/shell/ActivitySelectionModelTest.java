package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ActivitySelectionModel}, which tracks the active Unified-shell
 * activity and notifies observers (so the side panel swaps reactively).
 */
class ActivitySelectionModelTest {

    @Test
    void defaultsToTheDefaultActivity() {
        assertEquals(Activity.defaultActivity(), new ActivitySelectionModel().getActive());
    }

    @Test
    void selectChangesTheActiveActivity() {
        ActivitySelectionModel model = new ActivitySelectionModel();
        model.select(Activity.SCHEMA);
        assertEquals(Activity.SCHEMA, model.getActive());
    }

    @Test
    void selectNotifiesObserverWithNewValue() {
        ActivitySelectionModel model = new ActivitySelectionModel();
        AtomicReference<Activity> seen = new AtomicReference<>();
        model.activeProperty().addListener((obs, oldV, newV) -> seen.set(newV));

        model.select(Activity.TRANSFORM);

        assertEquals(Activity.TRANSFORM, seen.get());
    }

    @Test
    void selectingTheSameActivityDoesNotRenotify() {
        ActivitySelectionModel model = new ActivitySelectionModel();
        model.select(Activity.VALIDATION);
        AtomicInteger count = new AtomicInteger();
        model.activeProperty().addListener((obs, oldV, newV) -> count.incrementAndGet());

        model.select(Activity.VALIDATION);

        assertEquals(0, count.get(), "Re-selecting the active activity must not fire a change");
    }

    @Test
    void selectRejectsNull() {
        ActivitySelectionModel model = new ActivitySelectionModel();
        assertThrows(NullPointerException.class, () -> model.select(null));
    }
}
