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
    void f9TogglesTheBreakpointOnTheCaretLine(@TempDir Path tmp) throws Exception {
        Path xsl = tmp.resolve("toggle.xsl");
        Files.writeString(xsl, "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">\n"
                + "  <xsl:template match=\"/\"/>\n</xsl:stylesheet>\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsl));
        WaitForAsyncUtils.waitFor(10, java.util.concurrent.TimeUnit.SECONDS, () -> breakpointSlot(0) != null);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, host::canToggleBreakpoint));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            codeArea().moveTo(1, 0); // caret into line 2
            return host.toggleBreakpointAtCaret();
        });
        WaitForAsyncUtils.waitFor(10, java.util.concurrent.TimeUnit.SECONDS, () -> hasBreakpointIcon(1));
        assertFalse(hasBreakpointIcon(0), "only the caret line got the breakpoint");

        WaitForAsyncUtils.waitForAsyncFx(2000, host::toggleBreakpointAtCaret);
        WaitForAsyncUtils.waitFor(10, java.util.concurrent.TimeUnit.SECONDS, () -> !hasBreakpointIcon(1));
    }

    private boolean hasBreakpointIcon(int paragraph) {
        return Boolean.TRUE.equals(WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                breakpointSlot(paragraph) instanceof javafx.scene.Parent p && p.getChildrenUnmodifiable().stream()
                        .anyMatch(n -> n instanceof org.fxt.freexmltoolkit.controls.icons.IconifyIcon)));
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

    @Test
    void breakpointsFollowInsertedAndDeletedLines(@TempDir Path tmp) throws Exception {
        Path xsl = tmp.resolve("follow.xsl");
        Files.writeString(xsl, "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">\n"
                + "  <xsl:template match=\"/\">\n    <out/>\n  </xsl:template>\n</xsl:stylesheet>\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsl));
        WaitForAsyncUtils.waitFor(10, java.util.concurrent.TimeUnit.SECONDS, () -> breakpointSlot(0) != null);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            codeArea().moveTo(2, 0); // line 3: <out/>
            return host.toggleBreakpointAtCaret();
        });
        WaitForAsyncUtils.waitFor(10, java.util.concurrent.TimeUnit.SECONDS, () -> hasBreakpointIcon(2));
        assertTrue(storedLines(xsl).contains(3), "loading did not move it: " + storedLines(xsl));

        // Two new lines after line 1 push the breakpoint from line 3 to line 5.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> codeArea().insertText(0, codeArea().getParagraph(0).length(), "\n\n"));
        WaitForAsyncUtils.waitFor(5, java.util.concurrent.TimeUnit.SECONDS, () -> storedLines(xsl).equals(java.util.Set.of(5)));
        WaitForAsyncUtils.waitFor(10, java.util.concurrent.TimeUnit.SECONDS, () -> hasBreakpointIcon(4));

        // Deleting the breakpoint's line removes the breakpoint.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            int start = codeArea().getAbsolutePosition(3, codeArea().getParagraph(3).length());
            int end = codeArea().getAbsolutePosition(4, codeArea().getParagraph(4).length());
            codeArea().deleteText(start, end);
        });
        WaitForAsyncUtils.waitFor(5, java.util.concurrent.TimeUnit.SECONDS, () -> storedLines(xsl).isEmpty());
    }

    private static java.util.Set<Integer> storedLines(Path file) {
        java.util.Set<Integer> lines = new java.util.HashSet<>();
        org.fxt.freexmltoolkit.debugger.FileBreakpoints.persistent(file).ownBreakpoints()
                .forEach(bp -> lines.add(bp.lineNumber()));
        return lines;
    }
}
