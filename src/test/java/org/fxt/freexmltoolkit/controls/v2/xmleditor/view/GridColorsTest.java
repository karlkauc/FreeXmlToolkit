package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** User cell colours of the grid tables: per theme, defaulting to the design tokens, applied live. */
@ExtendWith(ApplicationExtension.class)
class GridColorsTest {

    private XmlCanvasView view;

    @Start
    void start(Stage stage) {
        GridColors.clearAll();
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString("<r><i>1</i><i>2</i></r>");
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 400, 300));
        stage.show();
    }

    @AfterEach
    void reset() {
        GridColors.clearAll();
    }

    @Test
    void overridesArePerThemeAndFallBackToTheTokens() {
        DesignTokens.Theme light = DesignTokens.Theme.LIGHT;
        DesignTokens.Theme dark = DesignTokens.Theme.DARK;
        assertNull(GridColors.override(light, GridColors.Slot.TABLE_HEADER));
        assertEquals(DesignTokens.ColorToken.BG_SURFACE_2.color(light), GridColors.resolve(light, GridColors.Slot.TABLE_HEADER));

        GridColors.setOverride(light, GridColors.Slot.TABLE_HEADER, Color.web("#fff6bf"));
        assertEquals(Color.web("#fff6bf"), GridColors.resolve(light, GridColors.Slot.TABLE_HEADER));
        assertEquals(DesignTokens.ColorToken.BG_SURFACE_2.color(dark), GridColors.resolve(dark, GridColors.Slot.TABLE_HEADER),
                "the dark theme keeps its default");
        assertEquals("#fff6bf", GridColors.toHex(Color.web("#FFF6BF")));
        assertEquals("tableHeader", GridColors.key(GridColors.Slot.TABLE_HEADER));
        assertEquals("dark", GridColors.key(dark));
    }

    @Test
    void anOpenGridRepaintsWithTheNewColour() {
        DesignTokens.Theme theme = DesignTokens.currentTheme();
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            GridColors.setOverride(theme, GridColors.Slot.TABLE_HEADER, Color.web("#fff6bf"));
            return null;
        });
        assertEquals(Color.web("#fff6bf"), view.tableHeaderColorForTest());
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            GridColors.setOverride(theme, GridColors.Slot.TABLE_HEADER, null);
            return null;
        });
        assertEquals(GridColors.defaultColor(theme, GridColors.Slot.TABLE_HEADER), view.tableHeaderColorForTest());
    }
}
