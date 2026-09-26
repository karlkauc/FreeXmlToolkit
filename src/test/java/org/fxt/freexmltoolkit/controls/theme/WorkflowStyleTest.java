package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.layout.StackPane;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/** {@link WorkflowStyle} keeps exactly one workflow scope class on a node. */
@ExtendWith(ApplicationExtension.class)
class WorkflowStyleTest {

    @Test
    void applyReplacesPreviousWorkflowClass() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                StackPane node = new StackPane();
                node.getStyleClass().add("fxt-side-panel-host");
                WorkflowStyle.apply(node, Workflow.VALIDATION);
                assertTrue(node.getStyleClass().contains("fxt-wf-validation"));
                WorkflowStyle.apply(node, Workflow.TRANSFORM);
                assertTrue(node.getStyleClass().contains("fxt-wf-transform"));
                assertFalse(node.getStyleClass().contains("fxt-wf-validation"));
                assertTrue(node.getStyleClass().contains("fxt-side-panel-host"), "other classes untouched");
                WorkflowStyle.clear(node);
                assertTrue(node.getStyleClass().stream().noneMatch(c -> c.startsWith("fxt-wf-")));
                WorkflowStyle.apply(node, null);
                assertTrue(node.getStyleClass().stream().noneMatch(c -> c.startsWith("fxt-wf-")));
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
