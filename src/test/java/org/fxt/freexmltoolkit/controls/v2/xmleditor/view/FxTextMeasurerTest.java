package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.text.Text;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** TestFX check that the FX measurer matches real {@link Text} layout and is stable. */
@ExtendWith(ApplicationExtension.class)
class FxTextMeasurerTest {

    @Start
    void start(Stage stage) {
        // Toolkit only; no scene needed for Text measurement.
    }

    private static double nodeWidth(String s, GridFont font) {
        Text t = new Text(s);
        t.setFont(font.toFont());
        return t.getLayoutBounds().getWidth();
    }

    @Test
    void asciiWidthMatchesTextNodeMeasurement() {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            TextMeasurer m = TextMeasurer.fx();
            String s = "Hello, grid <value> 12345";
            assertEquals(nodeWidth(s, GridFont.ROW), m.width(s, GridFont.ROW), 0.5);
            assertEquals(nodeWidth(s, GridFont.ROW_BOLD), m.width(s, GridFont.ROW_BOLD), 0.5);
            assertEquals(nodeWidth(s, GridFont.SMALL), m.width(s, GridFont.SMALL), 0.5);
            // Monospace: four glyphs are four times one glyph.
            assertEquals(4 * m.width("M", GridFont.ROW), m.width("MMMM", GridFont.ROW), 0.01);
            return null;
        });
    }

    @Test
    void nonAsciiGoesThroughTheNodeAndIsCached() {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            TextMeasurer m = TextMeasurer.fx();
            String s = "Größe – 東京";
            double first = m.width(s, GridFont.ROW);
            assertEquals(nodeWidth(s, GridFont.ROW), first, 0.5);
            assertEquals(first, m.width(s, GridFont.ROW), 0.0, "cached value must be identical");
            assertTrue(first > 0);
            assertEquals(0.0, m.width("", GridFont.ROW), 0.0);
            assertEquals(0.0, m.width(null, GridFont.ROW), 0.0);
            return null;
        });
    }
}
