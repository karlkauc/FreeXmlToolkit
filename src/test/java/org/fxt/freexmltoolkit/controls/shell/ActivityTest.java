package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Optional;

import org.fxt.freexmltoolkit.controls.icons.IconifyIconService;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Activity}, the catalog of left-rail activities in the new
 * Unified shell (per the Figma Activity Bar).
 */
class ActivityTest {

    @Test
    void allExpectedActivitiesArePresentInOrder() {
        Activity[] expected = {
                Activity.EXPLORER, Activity.FAVORITES, Activity.VALIDATION,
                Activity.TRANSFORM, Activity.SCHEMA, Activity.PDF_FOP,
                Activity.SIGNATURE, Activity.HELP, Activity.SETTINGS
        };
        assertArrayEquals(expected, Activity.values(),
                "Activity order drives the Activity Bar layout");
    }

    @Test
    void everyActivityHasNonBlankIdLabelAndIcon() {
        for (Activity a : Activity.values()) {
            assertNotNull(a.id());
            assertFalse(a.id().isBlank(), () -> a + " has blank id");
            assertFalse(a.label().isBlank(), () -> a + " has blank label");
            assertNotNull(a.icon());
            assertTrue(a.icon().startsWith("bi-"), () -> a + " icon must be a Bootstrap icon");
        }
    }

    @Test
    void activityIdsAreUnique() {
        long distinct = Arrays.stream(Activity.values()).map(Activity::id).distinct().count();
        assertEquals(Activity.values().length, distinct, "Activity ids must be unique");
    }

    @Test
    void everyActivityIconResolvesInTheBundle() {
        IconifyIconService icons = IconifyIconService.getInstance();
        for (Activity a : Activity.values()) {
            assertTrue(icons.exists(a.icon()),
                    () -> a + " uses unknown icon '" + a.icon() + "'");
        }
    }

    @Test
    void defaultActivityIsExplorer() {
        assertEquals(Activity.EXPLORER, Activity.defaultActivity());
    }

    @Test
    void fromIdResolvesKnownAndRejectsUnknown() {
        assertEquals(Optional.of(Activity.SCHEMA), Activity.fromId("schema"));
        assertEquals(Optional.of(Activity.PDF_FOP), Activity.fromId("pdf"));
        assertEquals(Optional.empty(), Activity.fromId("does-not-exist"));
        assertEquals(Optional.empty(), Activity.fromId(null));
    }
}
