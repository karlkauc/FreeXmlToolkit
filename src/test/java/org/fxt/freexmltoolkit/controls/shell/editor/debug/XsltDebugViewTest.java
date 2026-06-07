package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class XsltDebugViewTest {

    private XsltDebugController controller;

    @Start
    void start(Stage stage) {
        controller = new XsltDebugController(Runnable::run);
    }

    @Test
    void buildsWithAllSections() {
        XsltDebugView view = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> new XsltDebugView(controller, "", () -> "", line -> { }, () -> { }));
        assertNotNull(view.getBreakpointsView());
        assertNotNull(view.getVariablesView());
        assertNotNull(view.getCallStackView());
        assertNotNull(view.getWatchView());
        assertTrue(view.getChildren().size() >= 2, "toolbar + content");
    }
}
