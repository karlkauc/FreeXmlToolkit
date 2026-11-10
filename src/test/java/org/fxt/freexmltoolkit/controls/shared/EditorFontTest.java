package org.fxt.freexmltoolkit.controls.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2;
import org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The user's editor font: normalisation, storage, zoom and live application to the editors. */
@ExtendWith(ApplicationExtension.class)
class EditorFontTest {

    private XmlCodeEditorV2 editor;
    private CodeArea panelArea;

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        EditorFont.update(EditorFont.Settings.defaults());
        editor = new XmlCodeEditorV2(new MutableXmlSchemaProvider());
        panelArea = new CodeArea();
        CodeAreaFontZoom.install(panelArea);
        stage.setScene(new Scene(new StackPane(editor, panelArea), 600, 400));
        stage.show();
    }

    @AfterEach
    void reset() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> EditorFont.update(EditorFont.Settings.defaults()));
    }

    @Test
    void settingsAreNormalised() {
        var settings = new EditorFont.Settings("  ", 200, 9.0, true);
        assertEquals(EditorFont.DEFAULT_FAMILY, settings.family());
        assertEquals(EditorFont.MAX_SIZE, settings.size());
        assertEquals(EditorFont.MAX_LINE_SPACING, settings.lineSpacing());
        assertTrue(new EditorFont.Settings("Fira Code", 12, 1, true).cssFamily().startsWith("'"),
                "one quoted family (JavaFX CSS takes no list)");
    }

    @Test
    void anUpdateIsStoredAndReachesEveryEditor() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                EditorFont.update(new EditorFont.Settings("Courier New", 15, 1.5, true)));
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        assertEquals("Courier New", props.get(EditorFont.FAMILY_KEY));
        assertEquals("15", props.get(EditorFont.SIZE_KEY));
        assertEquals("1.50", props.get(EditorFont.LINE_SPACING_KEY));

        String style = editor.getCodeArea().getStyle();
        assertTrue(style.contains("-fx-font-size: 15pt"), style);
        String family = "'" + EditorFont.resolveFamily("Courier New") + "'";
        assertTrue(style.contains("-fx-font-family: " + family + ";"), style);
        assertTrue(editor.getCodeArea().getStylesheets().stream().anyMatch(s -> s.startsWith("data:text/css")),
                "line spacing above 1 adds the paragraph padding sheet");
        assertTrue(panelArea.getStyle().contains("-fx-font-size: 15px"), "code panels use the size in px");
        assertTrue(panelArea.getStyle().contains(family));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> EditorFont.update(EditorFont.Settings.defaults()));
        assertFalse(editor.getCodeArea().getStylesheets().stream().anyMatch(s -> s.startsWith("data:text/css")),
                "single spacing removes it again");
    }

    @Test
    void zoomWithRememberChangesTheSettingForAllEditors() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> editor.setFontSize(14));
        assertEquals(14, EditorFont.current().size());
        assertTrue(panelArea.getStyle().contains("-fx-font-size: 14px"), "the code panels follow");

        WaitForAsyncUtils.waitForAsyncFx(2000, editor::resetFontSize);
        assertEquals(EditorFont.DEFAULT_SIZE, EditorFont.current().size());
    }

    @Test
    void zoomWithoutRememberStaysInTheEditor() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                EditorFont.update(new EditorFont.Settings(null, 12, 1.0, false)));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> editor.setFontSize(20));
        assertEquals(12, EditorFont.current().size(), "the setting is untouched");
        assertEquals(20, editor.getFontSize());
        assertTrue(editor.getCodeArea().getStyle().contains("-fx-font-size: 20pt"));

        WaitForAsyncUtils.waitForAsyncFx(2000, editor::resetFontSize);
        assertEquals(12, editor.getFontSize(), "Ctrl+0 returns to the settings' size");
    }

    @Test
    void aMissingFontFallsBackToAnInstalledMonospaceFont() {
        String resolved = WaitForAsyncUtils.waitForAsyncFx(2000, () -> EditorFont.resolveFamily("No Such Font 4711"));
        assertFalse(resolved.equals("No Such Font 4711"));
    }

    @Test
    void theBundledFontIsListedFirst() {
        var families = WaitForAsyncUtils.waitForAsyncFx(5000, EditorFont::monospaceFamilies);
        assertEquals(EditorFont.DEFAULT_FAMILY, families.getFirst());
    }
}
