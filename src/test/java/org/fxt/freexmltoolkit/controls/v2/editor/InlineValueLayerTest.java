package org.fxt.freexmltoolkit.controls.v2.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The debugger's inline values sit after the end of their lines and go away on edits. */
@ExtendWith(ApplicationExtension.class)
class InlineValueLayerTest {

    private XmlCodeEditorV2 editor;

    @Start
    void start(Stage stage) {
        editor = new XmlCodeEditorV2(new MutableXmlSchemaProvider());
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 200; i++) {
            text.append(i == 3 ? "  <xsl:variable name=\"n\" select=\"@n\"/>   " : "<line" + i + "/>").append('\n');
        }
        editor.getCodeArea().replaceText(text.toString());
        stage.setScene(new Scene(editor, 900, 400));
        stage.show();
    }

    private List<Label> labels() {
        return WaitForAsyncUtils.waitForAsyncFx(5000, () -> editor.lookupAll("." + InlineValueLayer.VALUE_CLASS).stream()
                .map(Label.class::cast).toList());
    }

    @Test
    void valuesFollowTheLineEndAndOnlyVisibleLinesGetALabel() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> editor.setDebugInlineValues(Map.of(3, "$n = 7", 190, "$far = 1")));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> labels().size() == 1);
        Label label = labels().getFirst();
        assertEquals("$n = 7", label.getText());
        double[] codeEnd = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            var area = editor.getCodeArea();
            int start = area.getAbsolutePosition(2, 0);
            int end = start + area.getParagraph(2).getText().stripTrailing().length();
            Bounds b = area.getCharacterBoundsOnScreen(end - 1, end).orElseThrow();
            Bounds l = label.localToScreen(label.getBoundsInLocal());
            return new double[]{b.getMaxX(), l.getMinX(), b.getMinY(), l.getMinY()};
        });
        assertTrue(codeEnd[1] > codeEnd[0], "the label starts after the code: " + codeEnd[0] + " < " + codeEnd[1]);
        assertTrue(codeEnd[1] - codeEnd[0] < 40, "trailing blanks do not push it away");
        assertEquals(codeEnd[2], codeEnd[3], 2.0, "on the line itself");

        // Scrolling to the far line shows that one instead.
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> editor.getCodeArea().showParagraphAtTop(185));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> labels().size() == 1 && "$far = 1".equals(labels().getFirst().getText()));
    }

    @Test
    void anEditOrAnEmptyMapClearsTheValues() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> editor.setDebugInlineValues(Map.of(3, "$n = 7")));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> labels().size() == 1);
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> editor.getCodeArea().insertText(0, "x"));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> labels().isEmpty());
        assertTrue(editor.getDebugInlineValues().isEmpty());

        WaitForAsyncUtils.waitForAsyncFx(5000, () -> editor.setDebugInlineValues(Map.of(3, "$n = 7")));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> labels().size() == 1);
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> editor.setDebugInlineValues(Map.of()));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> labels().isEmpty());
        Node layer = editor.lookup(".fxt-debug-inline-layer");
        assertTrue(layer.isMouseTransparent(), "clicks reach the code area");
    }
}
