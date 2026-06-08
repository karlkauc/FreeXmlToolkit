package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the editor-level document-action toolbar group on the {@link UnifiedShellView}:
 * the four buttons exist and are correctly type-gated against the active document's
 * {@link EditorFileType} (no document → all disabled; XSD → Generate Docs / Type Editor
 * enabled, Transform disabled; XML → Validate / Transform enabled, Generate Docs disabled).
 */
@ExtendWith(ApplicationExtension.class)
class EditorActionsTest {

    private UnifiedShellView shell;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        shell = new UnifiedShellView();
        stage.setScene(new Scene(shell, 1280, 800));
        stage.show();
    }

    @Test
    void applicableForGate() {
        // Pure gate logic (no JavaFX needed) — the static contract the toolbar relies on.
        assertFalse(EditorActions.applicableFor(null, EditorActions.EditorAction.VALIDATE));
        assertTrue(EditorActions.applicableFor(EditorFileType.XML, EditorActions.EditorAction.VALIDATE));
        assertTrue(EditorActions.applicableFor(EditorFileType.XML, EditorActions.EditorAction.TRANSFORM));
        assertFalse(EditorActions.applicableFor(EditorFileType.XML, EditorActions.EditorAction.GENERATE_DOCS));
        assertTrue(EditorActions.applicableFor(EditorFileType.XSD, EditorActions.EditorAction.GENERATE_DOCS));
        assertTrue(EditorActions.applicableFor(EditorFileType.XSD, EditorActions.EditorAction.TYPE_EDITOR));
        assertFalse(EditorActions.applicableFor(EditorFileType.XSD, EditorActions.EditorAction.TRANSFORM));
        assertTrue(EditorActions.applicableFor(EditorFileType.JSON, EditorActions.EditorAction.VALIDATE));
    }

    @Test
    void buttonsExistAndAllDisabledWhenNoDocumentOpen() {
        WaitForAsyncUtils.waitForFxEvents();
        for (String id : new String[]{"doc-action-validate", "doc-action-transform",
                "doc-action-generate-docs", "doc-action-type-editor"}) {
            Button button = (Button) shell.lookup("#" + id);
            assertNotNull(button, "document-action button must exist: " + id);
            assertTrue(button.isDisable(),
                    "with no document open, " + id + " must be disabled");
        }
    }

    @Test
    void xsdEnablesDocsAndTypeEditorButNotTransform(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd,
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n"
                        + "  <xs:complexType name=\"PersonType\">\n"
                        + "    <xs:sequence><xs:element name=\"name\" type=\"xs:string\"/></xs:sequence>\n"
                        + "  </xs:complexType>\n"
                        + "</xs:schema>\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getEditorHost().openFile(xsd));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("PersonType")).orElse(false));
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(button("doc-action-generate-docs").isDisable(),
                "Generate Documentation must be enabled for an XSD");
        assertFalse(button("doc-action-type-editor").isDisable(),
                "Open Type Editor must be enabled for an XSD");
        assertTrue(button("doc-action-transform").isDisable(),
                "Transform must be disabled for an XSD");
        assertTrue(button("doc-action-validate").isDisable() == false,
                "Validate must be enabled for an XSD");
    }

    @Test
    void xmlEnablesValidateAndTransformButNotGenerateDocs(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root><a>x</a></root>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getEditorHost().openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(button("doc-action-validate").isDisable(),
                "Validate must be enabled for an XML document");
        assertFalse(button("doc-action-transform").isDisable(),
                "Transform must be enabled for an XML document");
        assertTrue(button("doc-action-generate-docs").isDisable(),
                "Generate Documentation must be disabled for an XML document");
        assertTrue(button("doc-action-type-editor").isDisable(),
                "Open Type Editor must be disabled for an XML document");
    }

    private Button button(String id) {
        return (Button) shell.lookup("#" + id);
    }
}
