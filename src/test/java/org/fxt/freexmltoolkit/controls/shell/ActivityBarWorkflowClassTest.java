package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/** Each Activity-Bar button carries its workflow style class so CSS can colour the selected indicator. */
@ExtendWith(ApplicationExtension.class)
class ActivityBarWorkflowClassTest {

    @Test
    void everyButtonCarriesItsWorkflowClass() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                ActivityBar bar = new ActivityBar(new ActivitySelectionModel());
                new Scene(new StackPane(bar), 100, 600);
                var buttons = bar.lookupAll(".fxt-activity-button");
                assertTrue(buttons.size() >= 11, "expected the rail buttons, got " + buttons.size());
                for (var node : buttons) {
                    ToggleButton button = (ToggleButton) node;
                    Activity activity = (Activity) button.getUserData();
                    assertTrue(button.getStyleClass().contains(activity.workflow().cssClass()),
                            () -> activity + " is missing " + activity.workflow().cssClass());
                }
            } catch (Throwable t) {
                failure[0] = t;
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS), "FX thread timed out");
        if (failure[0] != null) {
            throw new AssertionError(failure[0].getMessage(), failure[0]);
        }
    }
}
