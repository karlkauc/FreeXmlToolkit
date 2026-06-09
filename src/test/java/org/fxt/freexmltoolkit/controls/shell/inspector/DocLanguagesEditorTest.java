package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The per-language documentation editor: edits a list of (lang, text) xs:documentation entries. */
@ExtendWith(ApplicationExtension.class)
class DocLanguagesEditorTest {

    private DocLanguagesEditor editor;
    private final AtomicReference<List<XsdDocumentation>> changed = new AtomicReference<>();

    @Start
    void start(Stage stage) {
        editor = new DocLanguagesEditor();
        editor.setOnChange(changed::set);
    }

    @Test
    void editsAndReportsPerLanguageEntries() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            editor.setEntries(List.of(new XsdDocumentation("Hi", "en")));
            return null;
        });
        assertEquals(1, WaitForAsyncUtils.waitForAsyncFx(2000, () -> editor.getRowCount()));

        List<XsdDocumentation> result = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            editor.addRow("de", "Hallo");
            editor.commit();
            return changed.get();
        });
        assertEquals(2, result.size());
        assertEquals("en", result.get(0).getLang());
        assertEquals("Hi", result.get(0).getText());
        assertEquals("de", result.get(1).getLang());
        assertEquals("Hallo", result.get(1).getText());
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> editor.getCombinedText())
                .contains("Hi"));
    }

    @Test
    void blankEntriesAreDroppedAndEmptyShowsOneRow() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            editor.setEntries(List.of());
            return null;
        });
        // an empty list still shows one editable row to type into
        assertEquals(1, WaitForAsyncUtils.waitForAsyncFx(2000, () -> editor.getRowCount()));
        // a blank row contributes no documentation entry
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            editor.commit();
            return changed.get();
        }).isEmpty());
    }
}
