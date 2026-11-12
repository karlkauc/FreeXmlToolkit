package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The output panel's "Run anyway" button after a refused (FXT-COMPAT) run. */
@ExtendWith(ApplicationExtension.class)
class TransformOutputRunAnywayTest {

    private TransformOutputPanel out;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        EditorHost host = new EditorHost();
        out = host.transformOutputPanel();
        stage.setScene(new Scene(new HBox(host), 900, 500));
        stage.show();
    }

    @Test
    void runAnywayRunsOnceAndDisappears() {
        AtomicInteger runs = new AtomicInteger();
        Button button = WaitForAsyncUtils.waitForAsyncFx(4000, () -> {
            out.showIncompatible("XSLT 2.0 compatibility check failed", runs::incrementAndGet);
            return (Button) out.lookup("#transform-output-run-anyway");
        });
        assertTrue(button.isVisible());
        WaitForAsyncUtils.waitForAsyncFx(4000, () -> {
            button.fire();
            return null;
        });
        assertEquals(1, runs.get());
        assertFalse(button.isVisible());

        WaitForAsyncUtils.waitForAsyncFx(4000, () -> {
            out.showIncompatible("again", runs::incrementAndGet);
            out.showError("something else");
            return null;
        });
        assertFalse(button.isVisible(), "any other output hides the button");
    }
}
