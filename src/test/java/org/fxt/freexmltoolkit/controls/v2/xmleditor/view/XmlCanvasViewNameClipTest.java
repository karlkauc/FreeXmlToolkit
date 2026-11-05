package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A narrowed name column clips the names (they never run under the value column): a table glyph
 * cut off by it is not clickable, the click belongs to the value column.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewNameClipTest {

    private static final String XML = """
            <FundsXML4>
              <AssetMasterData><AssetWithAVeryLongElementName><UniqueID>ID_1</UniqueID></AssetWithAVeryLongElementName><AssetWithAVeryLongElementName><UniqueID>ID_2</UniqueID></AssetWithAVeryLongElementName></AssetMasterData>
            </FundsXML4>
            """;

    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 640, 300));
        stage.show();
    }

    /** @return the drawn x of the long-named group's table glyph after laying out with the given name width */
    private double glyphXWith(Double nameColumnWidth) {
        double x = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            view.expandAll();
            FlatRow root = view.visibleRowList().getFirst();
            view.viewState.setNameColumnWidth(view.keyOf(root), null);
            FlatRow assetMasterData = view.visibleRowList().stream()
                    .filter(r -> "AssetMasterData".equals(r.getLabel())).findFirst().orElseThrow();
            view.viewState.setNameColumnWidth(view.keyOf(assetMasterData), nameColumnWidth);
            view.recalculateVisibleRows();
            view.render();
            FlatRow asset = view.visibleRowList().stream().filter(r -> "AssetWithAVeryLongElementName".equals(r.getLabel()))
                    .findFirst().orElseThrow();
            return asset.getTableGlyphX();
        });
        WaitForAsyncUtils.waitForFxEvents();
        return x;
    }

    @Test
    void aGlyphCutOffByTheNameColumnIsNotClickable() {
        assertFalse(Double.isNaN(glyphXWith(null)), "with the automatic width the glyph is drawn and clickable");
        assertTrue(Double.isNaN(glyphXWith((double) GridCanvasView.MIN_NAME_COL_WIDTH)),
                "a name column narrower than the label clips the glyph away");
    }
}
