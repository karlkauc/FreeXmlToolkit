package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
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
        assertTrue(EditorActions.applicableFor(EditorFileType.XSLT, EditorActions.EditorAction.RUN_TRANSFORM));
        assertFalse(EditorActions.applicableFor(EditorFileType.XML, EditorActions.EditorAction.RUN_TRANSFORM));
        assertFalse(EditorActions.applicableFor(EditorFileType.XQUERY, EditorActions.EditorAction.RUN_TRANSFORM));
        assertFalse(EditorActions.applicableFor(EditorFileType.XSLT, EditorActions.EditorAction.RUN_QUERY));
        assertTrue(EditorActions.applicableFor(EditorFileType.XPROC, EditorActions.EditorAction.RUN_PIPELINE));
        assertTrue(EditorActions.applicableFor(EditorFileType.XPROC, EditorActions.EditorAction.VALIDATE));
        assertFalse(EditorActions.applicableFor(EditorFileType.XML, EditorActions.EditorAction.RUN_PIPELINE));
        assertFalse(EditorActions.applicableFor(EditorFileType.XSLT, EditorActions.EditorAction.RUN_PIPELINE));
        assertFalse(EditorActions.applicableFor(EditorFileType.XQUERY, EditorActions.EditorAction.RUN_PIPELINE));
        assertFalse(EditorActions.applicableFor(EditorFileType.XPROC, EditorActions.EditorAction.RUN_QUERY));
        assertFalse(EditorActions.applicableFor(EditorFileType.XPROC, EditorActions.EditorAction.RUN_TRANSFORM));
        assertFalse(EditorActions.applicableFor(EditorFileType.XPROC, EditorActions.EditorAction.TRANSFORM));
    }

    @Test
    void buttonsExistAndAllDisabledWhenNoDocumentOpen() {
        WaitForAsyncUtils.waitForFxEvents();
        // Validate/Transform are direct buttons; run/docs/type-editor live as MenuItems
        // inside the Run and Schema SplitMenuButtons — actionDisabled() resolves both.
        for (String id : new String[]{"doc-action-validate", "doc-action-transform",
                "doc-action-generate-docs", "doc-action-type-editor", "doc-action-run-query",
                "doc-action-run-transform", "doc-action-run-pipeline", "doc-action-run"}) {
            assertTrue(actionDisabled(id),
                    "with no document open, " + id + " must be disabled");
        }
        var targetButton = shell.lookup("#doc-query-target");
        assertNotNull(targetButton, "the Target dropdown must exist");
        assertFalse(targetButton.isVisible(),
                "with no document open, the Target dropdown must be hidden");
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

        assertFalse(actionDisabled("doc-action-generate-docs"),
                "Generate Documentation must be enabled for an XSD");
        assertFalse(actionDisabled("doc-action-type-editor"),
                "Open Type Editor must be enabled for an XSD");
        assertTrue(actionDisabled("doc-action-transform"),
                "Transform must be disabled for an XSD");
        assertTrue(actionDisabled("doc-action-validate") == false,
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

        assertFalse(actionDisabled("doc-action-validate"),
                "Validate must be enabled for an XML document");
        assertFalse(actionDisabled("doc-action-transform"),
                "Transform must be enabled for an XML document");
        assertTrue(actionDisabled("doc-action-generate-docs"),
                "Generate Documentation must be disabled for an XML document");
        assertTrue(actionDisabled("doc-action-type-editor"),
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
        assertTrue(actionDisabled("doc-action-run-query"),
                "Run Query must be disabled for an XML document");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getEditorHost().openFile(xq));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("return")).orElse(false));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(actionDisabled("doc-action-run-query"),
                "Run Query must be enabled for an XQuery document");
        assertTrue(actionDisabled("doc-action-validate"),
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
    void xprocEnablesRunPipelineAndTargetDropdown(@TempDir Path tmp) throws Exception {
        Path xpl = tmp.resolve("pipe.xpl");
        Files.writeString(xpl, """
                <p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">
                  <p:input port="source"/>
                  <p:output port="result"/>
                  <p:identity/>
                </p:declare-step>
                """);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getEditorHost().openFile(xpl));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("declare-step")).orElse(false));
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(actionDisabled("doc-action-run-pipeline"),
                "Run Pipeline must be enabled for an XProc document");
        assertFalse(actionDisabled("doc-action-validate"),
                "Validate must be enabled for an XProc document (it is XML)");
        assertTrue(actionDisabled("doc-action-run-query"),
                "Run Query must be disabled for an XProc document");
        assertTrue(actionDisabled("doc-action-run-transform"),
                "Run Transform must be disabled for an XProc document");
        var targetButton = shell.lookup("#doc-query-target");
        assertTrue(targetButton.isVisible(),
                "the Target dropdown must be visible for an XProc document");
    }

    @Test
    void runActivePipelineRunsAgainstTheLastXmlDocument(@TempDir Path tmp) throws Exception {
        EditorHost host = shell.getEditorHost();
        Path xml = tmp.resolve("order.xml");
        Files.writeString(xml, "<order><item>PIPELINE-ITEM</item></order>");
        Path xpl = tmp.resolve("identity.xpl");
        Files.writeString(xpl, """
                <p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">
                  <p:input port="source"/>
                  <p:output port="result"/>
                  <p:identity/>
                </p:declare-step>
                """);

        openAndAwait(host, xml, "PIPELINE-ITEM");
        openAndAwait(host, xpl, "declare-step");

        EditorActions actions = new EditorActions(host);
        var out = WaitForAsyncUtils.waitForAsyncFx(2000, host::transformOutputPanel);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            actions.runActivePipeline();
            return null;
        });

        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS,
                () -> out.getOutputText() != null && out.getOutputText().contains("PIPELINE-ITEM"));
        assertTrue(out.isShowing(), "the OUTPUT panel must appear after a pipeline run");
        assertTrue(out.getOutputText().contains("PIPELINE-ITEM"),
                "the pipeline result must contain the input document: " + out.getOutputText());
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

    @Test
    void queryTargetButtonVisibilityAndLabel(@TempDir Path tmp) throws Exception {
        EditorHost host = shell.getEditorHost();
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root/>");
        Path xq = tmp.resolve("query.xq");
        Files.writeString(xq, "/root");

        openAndAwait(host, xml, "root");
        var targetButton = (javafx.scene.control.MenuButton) shell.lookup("#doc-query-target");
        assertFalse(targetButton.isVisible(),
                "the Target dropdown must be hidden for an XML document");

        openAndAwait(host, xq, "root");
        assertTrue(targetButton.isVisible(),
                "the Target dropdown must be visible for a query document");
        assertTrue(targetButton.getText().contains("Automatic"),
                "with no explicit target the label must show Automatic: " + targetButton.getText());
    }

    @Test
    void runActiveQueryAgainstExplicitlyChosenOpenDocument(@TempDir Path tmp) throws Exception {
        EditorHost host = shell.getEditorHost();
        Path a = tmp.resolve("a.xml");
        Files.writeString(a, "<a><item>FROM-A</item></a>");
        Path b = tmp.resolve("b.xml");
        Files.writeString(b, "<b><item>FROM-B</item></b>");
        Path xq = tmp.resolve("q.xq");
        Files.writeString(xq, "//item/text()");

        openAndAwait(host, a, "FROM-A");
        openAndAwait(host, b, "FROM-B");
        openAndAwait(host, xq, "item");

        // b.xml was active most recently; explicitly target a.xml instead.
        var docA = host.getOpenDocuments().stream()
                .filter(d -> "a.xml".equals(d.getDisplayName())).findFirst().orElseThrow();
        var queryDoc = host.getActiveDocument().orElseThrow();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setQueryTarget(queryDoc, new QueryTarget.OpenDoc(docA));
            return null;
        });

        EditorActions actions = new EditorActions(host);
        var out = WaitForAsyncUtils.waitForAsyncFx(2000, host::transformOutputPanel);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            actions.runActiveQuery();
            return null;
        });

        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> out.getOutputText() != null && out.getOutputText().contains("FROM-A"));
        assertFalse(out.getOutputText().contains("FROM-B"),
                "the query must run against the chosen document, not the last active one: "
                        + out.getOutputText());
    }

    @Test
    void runActiveQueryAgainstFileSystemTarget(@TempDir Path tmp) throws Exception {
        EditorHost host = shell.getEditorHost();
        Path ext = tmp.resolve("ext.xml");
        Files.writeString(ext, "<r><item>FROM-EXT</item></r>");
        Path xq = tmp.resolve("q.xq");
        Files.writeString(xq, "//item/text()");

        // Only the query document is open; the target is a file that is NOT open.
        openAndAwait(host, xq, "item");
        var queryDoc = host.getActiveDocument().orElseThrow();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setQueryTarget(queryDoc, new QueryTarget.FsFile(ext.toFile()));
            return null;
        });

        EditorActions actions = new EditorActions(host);
        var out = WaitForAsyncUtils.waitForAsyncFx(2000, host::transformOutputPanel);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            actions.runActiveQuery();
            return null;
        });

        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> out.getOutputText() != null && out.getOutputText().contains("FROM-EXT"));
        assertTrue(out.getOutputText().contains("FROM-EXT"),
                "the query must run against the file-system target: " + out.getOutputText());
    }

    @Test
    void targetFallsBackToAutomaticWhenTargetDocumentIsClosed(@TempDir Path tmp) throws Exception {
        EditorHost host = shell.getEditorHost();
        Path a = tmp.resolve("a.xml");
        Files.writeString(a, "<a><item>FROM-A</item></a>");
        Path b = tmp.resolve("b.xml");
        Files.writeString(b, "<b><item>FROM-B</item></b>");
        Path xq = tmp.resolve("q.xq");
        Files.writeString(xq, "//item/text()");

        openAndAwait(host, a, "FROM-A");
        openAndAwait(host, b, "FROM-B");
        openAndAwait(host, xq, "item");

        var docB = host.getOpenDocuments().stream()
                .filter(d -> "b.xml".equals(d.getDisplayName())).findFirst().orElseThrow();
        var queryDoc = host.getActiveDocument().orElseThrow();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setQueryTarget(queryDoc, new QueryTarget.OpenDoc(docB));
            return null;
        });

        // Close the chosen target programmatically (onClosed does not fire here —
        // this exercises the defensive fallback in resolveQueryTarget).
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            var tabPane = (javafx.scene.control.TabPane) host.lookup(".fxt-editor-tabpane");
            tabPane.getTabs().removeIf(t -> t.getText() != null && t.getText().contains("b.xml"));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        EditorActions actions = new EditorActions(host);
        var out = WaitForAsyncUtils.waitForAsyncFx(2000, host::transformOutputPanel);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            actions.runActiveQuery();
            return null;
        });

        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> out.getOutputText() != null && out.getOutputText().contains("FROM-A"));
        assertTrue(out.getOutputText().contains("FROM-A"),
                "after the chosen target was closed, the query must fall back to the remaining "
                        + "XML document: " + out.getOutputText());
    }

    @Test
    void runActiveTransformShowsResultInOutputPanel(@TempDir Path tmp) throws Exception {
        EditorHost host = shell.getEditorHost();
        Path xml = tmp.resolve("order.xml");
        Files.writeString(xml, "<order><item>A</item></order>");
        Path xsl = tmp.resolve("sheet.xsl");
        Files.writeString(xsl,
                "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
                        + "  <xsl:output method=\"text\"/>\n"
                        + "  <xsl:template match=\"/\">MARKER-<xsl:value-of select=\"/order/item\"/></xsl:template>\n"
                        + "</xsl:stylesheet>\n");

        openAndAwait(host, xml, "order");
        openAndAwait(host, xsl, "MARKER");

        // Automatic target: the stylesheet itself is XML-family but must never be its
        // own target — the transform has to pick the open XML document instead.
        EditorActions actions = new EditorActions(host);
        var out = WaitForAsyncUtils.waitForAsyncFx(2000, host::transformOutputPanel);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            actions.runActiveTransform();
            return null;
        });

        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> out.getOutputText() != null && out.getOutputText().contains("MARKER-A"));
        assertTrue(out.isShowing(), "the OUTPUT panel must appear after a transform run");
    }

    @Test
    void runActiveTransformWithoutAnXmlDocumentShowsAGuardError(@TempDir Path tmp) throws Exception {
        EditorHost host = shell.getEditorHost();
        Path xsl = tmp.resolve("sheet.xsl");
        Files.writeString(xsl,
                "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
                        + "  <xsl:template match=\"/\">MARKER</xsl:template>\n"
                        + "</xsl:stylesheet>\n");

        openAndAwait(host, xsl, "MARKER");

        EditorActions actions = new EditorActions(host);
        var out = WaitForAsyncUtils.waitForAsyncFx(2000, host::transformOutputPanel);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            actions.runActiveTransform();
            return null;
        });

        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> out.getOutputText() != null && out.getOutputText().contains("Open an XML document"));
        assertTrue(out.getOutputText().contains("Open an XML document first"),
                "with only the stylesheet open, a guard message must be shown: " + out.getOutputText());
    }

    private void openAndAwait(EditorHost host, Path file, String marker) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains(marker)).orElse(false));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /**
     * Disable state of a toolbar action — either a ButtonBase node in the toolbar or a
     * MenuItem inside one of the SplitMenuButtons (variant E folds the run/docs/type-editor
     * actions into the Run and Schema split buttons).
     */
    private boolean actionDisabled(String id) {
        javafx.scene.Node node = shell.lookup("#" + id);
        if (node instanceof javafx.scene.control.ButtonBase b) {
            return b.isDisable();
        }
        javafx.scene.control.MenuItem item = menuItem(id);
        assertNotNull(item, "toolbar action must exist: " + id);
        return item.isDisable();
    }

    private javafx.scene.control.MenuItem menuItem(String id) {
        for (javafx.scene.Node n : shell.lookupAll(".fxt-tool-split")) {
            if (n instanceof javafx.scene.control.SplitMenuButton smb) {
                for (javafx.scene.control.MenuItem item : smb.getItems()) {
                    if (id.equals(item.getId())) {
                        return item;
                    }
                }
            }
        }
        return null;
    }
}
