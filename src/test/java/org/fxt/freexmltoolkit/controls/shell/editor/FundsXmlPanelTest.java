package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class FundsXmlPanelTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
    }

    @Test
    void buildsWithoutThrowing() {
        FundsXmlPanel panel = WaitForAsyncUtils.waitForAsyncFx(3000, () -> new FundsXmlPanel(host));
        assertNotNull(panel);
        // Title + management/validate/docs sections + buttons + spacer/status.
        assertTrue(panel.getChildren().size() > 3,
                "panel should have built its sections, but had " + panel.getChildren().size() + " children");
    }
}
