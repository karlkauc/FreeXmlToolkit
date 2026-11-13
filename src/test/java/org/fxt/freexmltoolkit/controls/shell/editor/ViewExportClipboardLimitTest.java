package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.geometry.Rectangle2D;
import javafx.scene.paint.Color;

import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.controls.v2.view.render.DiagramGraphics;
import org.fxt.freexmltoolkit.controls.v2.view.render.ExportableDiagram;
import org.fxt.freexmltoolkit.service.export.DiagramExportOptions;
import org.junit.jupiter.api.Test;

/**
 * "Copy View as Image" on a view whose whole content is too large for the clipboard copies the visible
 * area instead of failing (telemetry: an error dialog on a 470 KB grid).
 */
class ViewExportClipboardLimitTest {

    private static ExportableDiagram diagram(double width, double height) {
        return new ExportableDiagram() {
            @Override
            public Rectangle2D exportContentBounds() {
                return new Rectangle2D(0, 0, width, height);
            }

            @Override
            public Rectangle2D exportVisibleBounds() {
                return new Rectangle2D(0, 0, 1200, 800);
            }

            @Override
            public String exportTitle() {
                return "test";
            }

            @Override
            public void paintExport(DiagramGraphics g, Rectangle2D region, DesignTokens.Theme theme) {
            }

            @Override
            public Color exportBackground(DesignTokens.Theme theme) {
                return Color.WHITE;
            }
        };
    }

    @Test
    void aLargeWholeViewDoesNotFitButItsVisibleAreaDoes() {
        DiagramExportOptions whole = DiagramExportOptions.defaults(DiagramExportOptions.Format.PNG);
        ExportableDiagram large = diagram(3000, 60000); // a long grid: 6000 × 120000 px at scale 2

        assertFalse(ViewExport.fitsClipboard(large, whole));
        assertTrue(ViewExport.fitsClipboard(large, whole.withArea(DiagramExportOptions.Area.VISIBLE)));
    }

    @Test
    void aSmallViewFitsAndAnEmptyOneIsLeftToTheCopy() {
        DiagramExportOptions whole = DiagramExportOptions.defaults(DiagramExportOptions.Format.PNG);
        assertTrue(ViewExport.fitsClipboard(diagram(800, 600), whole));
        assertTrue(ViewExport.fitsClipboard(diagram(0, 0), whole), "the copy reports an empty view itself");
    }
}
