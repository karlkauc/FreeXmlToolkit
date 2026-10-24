package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.TimeUnit;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * RichTextFX line numbers pad with spaces; with the shell sheets applied they must use a
 * monospace font, otherwise line 9 is narrower than line 10 and the text jumps sideways.
 */
@ExtendWith(ApplicationExtension.class)
class LineNumberAlignmentTest {

    private static final String[] SHELL_SHEETS = {
            "/css/design-tokens.css", "/css/app-theme.css", "/css/fxt-theme.css", "/css/unified-shell.css"};

    private CodeArea area;
    private String info = "";

    @Start
    void start(Stage stage) {
        // The app registers its bundled fonts at startup (FxtGui); do the same as the shell does.
        for (String font : new String[]{"Inter-Regular", "JetBrainsMono-Regular", "JetBrainsMono-Italic"}) {
            javafx.scene.text.Font.loadFont(
                    LineNumberAlignmentTest.class.getResourceAsStream("/css/fonts/" + font + ".ttf"), 10);
        }
        area = new CodeArea();
        area.getStyleClass().addAll("fxt-transform-output", "fxt-query-results");
        area.setParagraphGraphicFactory(LineNumberFactory.get(area));
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 12; i++) {
            text.append("<line n=\"").append(i).append("\"/>\n");
        }
        area.replaceText(text.toString());
        StackPane root = new StackPane(area);
        for (String sheet : SHELL_SHEETS) {
            root.getStylesheets().add(LineNumberAlignmentTest.class.getResource(sheet).toExternalForm());
        }
        stage.setScene(new Scene(root, 400, 400));
        stage.show();
    }

    @Test
    void singleAndDoubleDigitLineNumbersHaveTheSameWidth() throws Exception {
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(5000,
                () -> lineNumber(8) != null && lineNumber(9) != null && lineNumber(8).getWidth() > 0));
        double[] widths = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            area.applyCss();
            area.layout();
            info = "font=" + lineNumber(8).getFont();
            return new double[]{lineNumber(8).getWidth(), lineNumber(9).getWidth()};
        });
        assertEquals(widths[0], widths[1], 0.5, "line 9 and line 10 gutters must be equally wide: " + info);
    }

    private Label lineNumber(int paragraph) {
        Node graphic = area.getParagraphGraphic(paragraph);
        assertNotNull(graphic, "paragraph " + paragraph + " has a line number");
        return (Label) graphic;
    }
}
