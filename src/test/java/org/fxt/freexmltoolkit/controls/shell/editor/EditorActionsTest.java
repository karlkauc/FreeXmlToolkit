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
        assertTrue(EditorActions.applicableFor(EditorFileType.XQUERY, EditorActions.EditorAction.RUN_QUERY));
        assertTrue(EditorActions.applicableFor(EditorFileType.XPATH, EditorActions.EditorAction.RUN_QUERY));
        assertFalse(EditorActions.applicableFor(EditorFileType.XML, EditorActions.EditorAction.RUN_QUERY));
        assertFalse(EditorActions.applicableFor(EditorFileType.XQUERY, EditorActions.EditorAction.VALIDATE));
        assertFalse(EditorActions.applicableFor(EditorFileType.XQUERY, EditorActions.EditorAction.TRANSFORM));
    }

    @Test
    void buttonsExistAndAllDisabledWhenNoDocumentOpen() {
        WaitForAsyncUtils.waitForFxEvents();
        for (String id : new String[]{"doc-action-validate", "doc-action-transform",
                "doc-action-generate-docs", "doc-action-type-editor", "doc-action-run-query"}) {
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

    @Test
    void xqueryEnablesRunQueryAndXmlDoesNot(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root><a>x</a></root>");
        Path xq = tmp.resolve("query.xq");
        Files.writeString(xq, "for $a in /root/a return $a");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getEditorHost().openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(button("doc-action-run-query").isDisable(),
                "Run Query must be disabled for an XML document");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getEditorHost().openFile(xq));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("return")).orElse(false));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(button("doc-action-run-query").isDisable(),
                "Run Query must be enabled for an XQuery document");
        assertTrue(button("doc-action-validate").isDisable(),
                "Validate must be disabled for an XQuery document");
    }

    @Test
    void runActiveQueryRunsAgainstTheLastXmlDocument(@TempDir Path tmp) throws Exception {
        EditorHost host = shell.getEditorHost();
        Path xml = tmp.resolve("order.xml");
        Files.writeString(xml, "<order><item>A</item><item>B</item></order>");
        Path xq = tmp.resolve("items.xq");
        Files.writeString(xq, "for $i in /order/item return $i");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("order")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xq));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("return")).orElse(false));

        EditorActions actions = new EditorActions(host);
        var out = WaitForAsyncUtils.waitForAsyncFx(2000, host::transformOutputPanel);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            actions.runActiveQuery();
            return null;
        });

        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> out.getOutputText() != null && out.getOutputText().contains("item"));
        assertTrue(out.isShowing(), "the OUTPUT panel must appear after a query run");
        assertTrue(out.getOutputText().contains("A") && out.getOutputText().contains("B"),
                "the XQuery result must contain both items: " + out.getOutputText());
    }

    @Test
    void runActiveQueryWithoutAnXmlDocumentShowsAGuardError(@TempDir Path tmp) throws Exception {
        EditorHost host = shell.getEditorHost();
        Path xpath = tmp.resolve("expr.xpath");
        Files.writeString(xpath, "//item");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xpath));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("item")).orElse(false));

        EditorActions actions = new EditorActions(host);
        var out = WaitForAsyncUtils.waitForAsyncFx(2000, host::transformOutputPanel);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            actions.runActiveQuery();
            return null;
        });

        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> out.getOutputText() != null && out.getOutputText().contains("Open an XML document"));
        assertTrue(out.getOutputText().contains("Open an XML document first"),
                "with no XML-family document open, a guard message must be shown: " + out.getOutputText());
    }

    private Button button(String id) {
        return (Button) shell.lookup("#" + id);
    }
}
