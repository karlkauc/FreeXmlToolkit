package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The sticky scroll header shows the ancestors of the topmost visible row; a click selects one. */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewScrollHeaderTest {

    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        StringBuilder xml = new StringBuilder("<root><section><title>t</title><body>");
        for (int i = 0; i < 60; i++) {
            xml.append("<line").append(i).append(">v</line").append(i).append('>');
        }
        xml.append("</body></section></root>");
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(xml.toString());
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 800, 400));
        stage.show();
    }

    private void fx(Runnable r) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            r.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void headerListsTheScrolledOffAncestorsAndAClickSelectsOne() {
        fx(view::expandAll);
        assertTrue(view.scrollHeaderRows().isEmpty(), "no header at the top");

        FlatRow line30 = view.visibleRowList().stream().filter(r -> "line30".equals(r.getLabel())).findFirst().orElseThrow();
        fx(() -> view.scrollToForTest(view.rowTopAt(view.visibleRowList().indexOf(line30))));

        List<String> chain = view.scrollHeaderRows().stream().map(FlatRow::getLabel).toList();
        assertEquals(List.of("root", "section", "body"), chain);

        // Click the "section" segment (second): root occupies the first ~60 px
        double y = GridCanvasView.SCROLL_HEADER_HEIGHT / 2;
        for (double x = 8; x < 400; x += 4) {
            double fx = x;
            fx(() -> Event.fireEvent(view.canvasNode(), new MouseEvent(MouseEvent.MOUSE_CLICKED, fx, y, fx, y,
                    MouseButton.PRIMARY, 1, false, false, false, false,
                    true, false, false, true, false, true, null)));
            if (view.getSelectedNode() instanceof XmlElement el && "section".equals(el.getName())) {
                return;
            }
        }
        throw new AssertionError("clicking the header never selected 'section'");
    }
}
