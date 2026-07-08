package org.fxt.freexmltoolkit.controls.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of {@link CodeAreaFontZoom}: Ctrl+MouseWheel grows and
 * shrinks the font within the clamp bounds, Ctrl+0 resets, and a plain wheel
 * (no Ctrl) leaves the font size alone.
 */
@ExtendWith(ApplicationExtension.class)
class CodeAreaFontZoomTest {

    private CodeArea area;

    @Start
    void start(Stage stage) {
        area = new CodeArea();
        CodeAreaFontZoom.install(area);
        stage.setScene(new Scene(new StackPane(area), 300, 200));
        stage.show();
    }

    @Test
    void ctrlScrollUpAndDownChangesTheFontSize() {
        fireScroll(40, true);
        assertEquals(CodeAreaFontZoom.DEFAULT_FONT_SIZE + 1, CodeAreaFontZoom.fontSizeOf(area),
                "Ctrl+scroll-up should grow the font by 1px");
        assertTrue(area.getStyle().contains("-fx-font-size: " + (CodeAreaFontZoom.DEFAULT_FONT_SIZE + 1) + "px"),
                "the new size must be applied as an inline style: " + area.getStyle());

        fireScroll(-40, true);
        fireScroll(-40, true);
        assertEquals(CodeAreaFontZoom.DEFAULT_FONT_SIZE - 1, CodeAreaFontZoom.fontSizeOf(area),
                "Ctrl+scroll-down should shrink the font by 1px each time");
    }

    @Test
    void fontSizeIsClampedToMinAndMax() {
        for (int i = 0; i < CodeAreaFontZoom.DEFAULT_FONT_SIZE - CodeAreaFontZoom.MIN_FONT_SIZE + 5; i++) {
            fireScroll(-40, true);
        }
        assertEquals(CodeAreaFontZoom.MIN_FONT_SIZE, CodeAreaFontZoom.fontSizeOf(area),
                "shrinking must stop at the minimum font size");

        for (int i = 0; i < CodeAreaFontZoom.MAX_FONT_SIZE - CodeAreaFontZoom.MIN_FONT_SIZE + 5; i++) {
            fireScroll(40, true);
        }
        assertEquals(CodeAreaFontZoom.MAX_FONT_SIZE, CodeAreaFontZoom.fontSizeOf(area),
                "growing must stop at the maximum font size");
    }

    @Test
    void ctrlZeroResetsToTheDefaultSize() {
        fireScroll(40, true);
        fireScroll(40, true);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> Event.fireEvent(area,
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DIGIT0, false, true, false, false)));
        assertEquals(CodeAreaFontZoom.DEFAULT_FONT_SIZE, CodeAreaFontZoom.fontSizeOf(area),
                "Ctrl+0 should reset the font size to the default");
    }

    @Test
    void plainScrollWithoutCtrlDoesNotZoom() {
        fireScroll(40, false);
        assertEquals(CodeAreaFontZoom.DEFAULT_FONT_SIZE, CodeAreaFontZoom.fontSizeOf(area),
                "a wheel scroll without Ctrl must not change the font size");
    }

    /** Fires a vertical scroll event with {@code deltaY} on the code area (FX thread). */
    private void fireScroll(double deltaY, boolean controlDown) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> Event.fireEvent(area, new ScrollEvent(
                ScrollEvent.SCROLL, 0, 0, 0, 0,
                false, controlDown, false, false, false, false,
                0, deltaY, 0, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0,
                0, null)));
    }
}
