package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonCodeEditor;
import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the editor host is file-type polymorphic: JSON files get the JSON
 * editor, the XML family gets the schema-aware XML editor.
 */
@ExtendWith(ApplicationExtension.class)
class EditorViewsTest {

    @Start
    void start(Stage stage) {
        // toolkit only
    }

    @Test
    void jsonFileTypeUsesJsonEditorWithoutSchema() {
        EditorView view = WaitForAsyncUtils.waitForAsyncFx(2000, () -> EditorViews.create(EditorFileType.JSON));
        assertInstanceOf(JsonCodeEditor.class, view.getNode());
        assertFalse(view.supportsSchema());
    }

    @Test
    void xmlFamilyUsesXmlEditorWithSchema() {
        EditorView xml = WaitForAsyncUtils.waitForAsyncFx(2000, () -> EditorViews.create(EditorFileType.XML));
        EditorView xsd = WaitForAsyncUtils.waitForAsyncFx(2000, () -> EditorViews.create(EditorFileType.XSD));
        assertInstanceOf(XmlCodeEditorV2.class, xml.getNode());
        assertInstanceOf(XmlCodeEditorV2.class, xsd.getNode());
        assertTrue(xml.supportsSchema());
    }
}
