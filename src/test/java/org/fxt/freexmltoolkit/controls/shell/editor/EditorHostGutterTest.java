package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class EditorHostGutterTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void attachesGutterToXsltEditor(@TempDir Path tmp) throws Exception {
        Path xsl = tmp.resolve("sheet.xslt");
        Files.writeString(xsl,
                "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\"/>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsl));
        WaitForAsyncUtils.waitForFxEvents();
        boolean applied = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.setActiveEditorGutterFactory(line -> (Node) new Region()));
        assertTrue(applied, "XSLT editor should accept a gutter factory");
    }

    @Test
    void savedXsltDocumentGetsTheStandaloneBreakpointGutter(@TempDir Path tmp) throws Exception {
        Path xsl = tmp.resolve("sheet.xsl");
        Files.writeString(xsl, "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">\n"
                + "  <xsl:template match=\"/\"/>\n</xsl:stylesheet>\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsl));
        WaitForAsyncUtils.waitFor(10, java.util.concurrent.TimeUnit.SECONDS, () -> breakpointSlot(0) != null);
        assertTrue(breakpointSlot(1) != null, "every line of a saved XSLT document has a breakpoint slot");
    }

    @Test
    void xmlDocumentHasNoBreakpointGutter(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("data.xml");
        Files.writeString(xml, "<root>\n  <item/>\n</root>\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(10, java.util.concurrent.TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                    org.fxmisc.richtext.CodeArea ca = codeArea();
                    return ca != null && ca.getParagraphs().size() > 1 && ca.getParagraphGraphic(0) != null;
                }));
        assertFalse(breakpointSlot(0) != null, "breakpoints are an XSLT feature");
    }

    /** FX-thread only. */
    private org.fxmisc.richtext.CodeArea codeArea() {
        Node node = host.lookup(".code-area");
        return node instanceof org.fxmisc.richtext.CodeArea ca ? ca : null;
    }

    /** The rendered breakpoint slot of a paragraph (queried on the FX thread), or {@code null}. */
    private Node breakpointSlot(int paragraph) {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            org.fxmisc.richtext.CodeArea ca = codeArea();
            if (ca == null || paragraph >= ca.getParagraphs().size()) {
                return null;
            }
            Node graphic = ca.getParagraphGraphic(paragraph);
            return graphic == null ? null : graphic.lookup(
                    "." + org.fxt.freexmltoolkit.debugger.ui.BreakpointGutterFactory.SLOT_STYLE_CLASS);
        });
    }

    @Test
    void jsonEditorRejectsGutter(@TempDir Path tmp) throws Exception {
        Path json = tmp.resolve("data.json");
        Files.writeString(json, "{}");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        WaitForAsyncUtils.waitForFxEvents();
        boolean applied = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.setActiveEditorGutterFactory(line -> (Node) new Region()));
        assertFalse(applied, "JSON editor has no gutter support");
    }
}
