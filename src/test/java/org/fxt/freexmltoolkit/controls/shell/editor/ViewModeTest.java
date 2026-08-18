package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.icons.IconifyIconService;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ViewMode}: the four editor view modes and the
 * {@link ViewMode#isStructured()} predicate that separates the model-backed,
 * editable views (Tree/Graphic) from Text and the read-only HTML Preview.
 */
class ViewModeTest {

    @Test
    void previewModeCarriesLabelAndIcon() {
        assertEquals("Preview", ViewMode.PREVIEW.label());
        assertEquals("bi-eye", ViewMode.PREVIEW.icon());
    }

    @Test
    void everyModeIconResolvesInTheBundle() {
        IconifyIconService icons = IconifyIconService.getInstance();
        for (ViewMode mode : ViewMode.values()) {
            assertTrue(icons.exists(mode.icon()), () -> mode + " uses unknown icon '" + mode.icon() + "'");
        }
    }

    @Test
    void onlyTreeAndGraphicAreStructured() {
        assertTrue(ViewMode.TREE.isStructured());
        assertTrue(ViewMode.GRAPHIC.isStructured());
        assertFalse(ViewMode.TEXT.isStructured());
        assertFalse(ViewMode.PREVIEW.isStructured());
    }
}
