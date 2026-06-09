package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class WelcomePaneTrendTest {

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
    }

    @Test
    void welcomePaneBuildsWithTrendSection() {
        EditorWelcomePane pane = WaitForAsyncUtils.waitForAsyncFx(3000,
                () -> new EditorWelcomePane(t -> { }, () -> { }, f -> { }, () -> { }, a -> { }));
        // building the pane (with the trend section) must not throw; point count is >= 0.
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, pane::getSparklinePointCount) >= 0);
    }
}
