package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorEventBus;
import org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The hover tooltip hit-tests the text only once the mouse rests, never per mouse move, and not at
 * all while a very long paragraph is on screen (telemetry {@code a4bb881765dbb6ff}: 2 s stalls from
 * hit tests on 100k-character lines).
 */
@ExtendWith(ApplicationExtension.class)
class EventHandlerManagerHoverTest {

    /** Counts hit tests. */
    private static final class CountingArea extends CodeArea {
        final AtomicInteger hits = new AtomicInteger();

        @Override
        public org.fxmisc.richtext.CharacterHit hit(double x, double y) {
            hits.incrementAndGet();
            return super.hit(x, y);
        }
    }

    private CountingArea area;

    @Start
    void start(Stage stage) {
        area = new CountingArea();
        EventHandlerManager manager = new EventHandlerManager(
                new EditorContext(area, new EditorEventBus(), new MutableXmlSchemaProvider()));
        manager.setupHandlers();
        stage.setScene(new Scene(area, 600, 300));
        stage.show();
    }

    @Test
    void mouseMovesHitTestOnceTheMouseRests() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            area.replaceText("<root>\n  <child/>\n</root>");
            for (int x = 5; x < 60; x++) {
                area.fireEvent(moved(x, 8));
            }
            return null;
        });
        assertEquals(0, area.hits.get(), "no hit test while the mouse moves");

        WaitForAsyncUtils.waitFor(2, java.util.concurrent.TimeUnit.SECONDS, () -> area.hits.get() > 0);
        Thread.sleep(300);
        assertEquals(1, area.hits.get(), "one hit test once the mouse rests");
    }

    @Test
    void longParagraphOnScreenIsNotHitTested() throws Exception {
        String longLine = "<a>" + "x".repeat(EventHandlerManager.MAX_HOVER_PARAGRAPH_LENGTH + 10) + "</a>";
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            area.replaceText("<root>\n" + longLine + "\n</root>");
            area.moveTo(0);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            area.fireEvent(moved(20, 8));
            return null;
        });
        Thread.sleep(500);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(0, area.hits.get());
    }

    private static MouseEvent moved(double x, double y) {
        return new MouseEvent(MouseEvent.MOUSE_MOVED, x, y, x, y, MouseButton.NONE, 0,
                false, false, false, false, false, false, false, false, false, false, null);
    }
}
