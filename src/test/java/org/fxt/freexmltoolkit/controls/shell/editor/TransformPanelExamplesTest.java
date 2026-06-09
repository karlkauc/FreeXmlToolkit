package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;

import javafx.stage.Stage;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class TransformPanelExamplesTest {

    private TransformPanel panel;

    @Start
    void start(Stage stage) {
        panel = new TransformPanel(new EditorHost());
    }

    @ParameterizedTest
    @ValueSource(strings = {"simple", "flwor", "html", "dq"})
    void insertsAnXQueryExample(String key) {
        String text = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.insertXQueryExample(key);
            return panel.getXQueryText();
        });
        assertFalse(text.isBlank(), "example inserted for key: " + key);
    }
}
