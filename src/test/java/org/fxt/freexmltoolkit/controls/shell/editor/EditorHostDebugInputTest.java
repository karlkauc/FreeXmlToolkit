package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** While paused, the context node's line is marked in the input document; Show input selects it. */
@ExtendWith(ApplicationExtension.class)
class EditorHostDebugInputTest {

    private static final String XSLT = "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
            + "  <xsl:template match=\"/\"><out><xsl:value-of select=\"count(//item)\"/></out></xsl:template>\n"
            + "</xsl:stylesheet>\n";
    private static final String XML = "<?xml version=\"1.0\"?>\n<order>\n  <item n=\"1\"/>\n</order>\n";

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @AfterEach
    void end() {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.endXsltDebug());
    }

    private DebugSession pausedSession() throws Exception {
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> host.activeDebugPane() != null
                && host.activeDebugPane().getController().getSession().getState() == DebugSession.State.PAUSED);
        return host.activeDebugPane().getController().getSession();
    }

    @Test
    void inputFileGetsTheMarkerAndShowInputSelectsIt(@TempDir Path tmp) throws Exception {
        Path xsl = tmp.resolve("sheet.xsl");
        Path in = tmp.resolve("input.xml");
        Files.writeString(xsl, XSLT);
        Files.writeString(in, XML);
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            host.setDebugInputFile(in);
            host.startXsltDebug(xsl.toFile(), XML, Map.of(), OutputFormat.XML); // pauses on entry
        });
        pausedSession();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> inputDocument(in) != null && host.debugInputLineOf(inputDocument(in)) == 2));
        assertEquals("sheet.xsl", WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getActiveDocument().orElseThrow().getDisplayName()), "the stylesheet stays in front");
        assertFalse(host.activeDebugPane().lookup("#debug-show-input").isDisabled());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeDebugPane().onShowInput());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> in.equals(host.getActiveDocument().orElseThrow().getPath())));
    }

    @Test
    void inMemoryInputIsMatchedToTheOpenDocument(@TempDir Path tmp) throws Exception {
        Path xsl = tmp.resolve("sheet2.xsl");
        Path in = tmp.resolve("open.xml");
        Files.writeString(xsl, XSLT);
        Files.writeString(in, XML);
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.openFile(in));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getActiveText().map(XML::equals).orElse(false)));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.startXsltDebug(xsl.toFile(), XML, Map.of(), OutputFormat.XML));
        pausedSession();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.debugInputLineOf(inputDocument(in)) == 2));
    }

    private OpenDocument inputDocument(Path file) {
        for (OpenDocument doc : host.getOpenDocuments()) {
            if (file.equals(doc.getPath())) {
                return doc;
            }
        }
        return null;
    }

    @Test
    void aConfigurationSetsInputParametersAndPauseAndReRunReadsTheInputAgain(@TempDir Path tmp) throws Exception {
        Path xsl = tmp.resolve("params.xsl");
        Path in = tmp.resolve("data.xml");
        Files.writeString(xsl, "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
                + "  <xsl:param name=\"who\" select=\"'nobody'\"/>\n"
                + "  <xsl:template match=\"/\"><out who=\"{$who}\" n=\"{count(//item)}\"/></xsl:template>\n"
                + "</xsl:stylesheet>\n");
        Files.writeString(in, "<order><item/></order>");
        org.fxt.freexmltoolkit.debugger.DebugLaunchConfig config = new org.fxt.freexmltoolkit.debugger.DebugLaunchConfig(
                in.toString(), Map.of("who", "Karl"), "", false);
        // Without breakpoints the run pauses on entry as usual; Continue lets it finish.
        String error = WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.debugWithConfig(xsl.toFile(), config));
        assertNull(error);
        pausedSession();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeDebugPane().onContinue());
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> host.activeDebugPane() != null
                && host.activeDebugPane().getOutputView().getText().contains("who=\"Karl\""));
        assertTrue(host.activeDebugPane().getOutputView().getText().contains("n=\"1\""), "the configured input");
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, host::canRerunLastDebug));

        // The input changes on disk; Re-run reads it again.
        Files.writeString(in, "<order><item/><item/><item/></order>");
        assertNull(WaitForAsyncUtils.waitForAsyncFx(5000, host::rerunLastDebug));
        pausedSession();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeDebugPane().onContinue());
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> host.activeDebugPane() != null
                && host.activeDebugPane().getOutputView().getText().contains("n=\"3\""));

        // "Pause on entry" in the configuration pauses even when a breakpoint is set.
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.endXsltDebug()); // writes the session's breakpoints back first
        org.fxt.freexmltoolkit.debugger.FileBreakpoints.persistent(xsl).toggleBreakpoint(xsl.toString(), 3);
        String paused = WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.debugWithConfig(xsl.toFile(),
                new org.fxt.freexmltoolkit.debugger.DebugLaunchConfig(in.toString(), Map.of(), "", true)));
        assertNull(paused);
        DebugSession session = pausedSession();
        assertEquals(1, session.getBreakpoints().size(), "the breakpoint is there");
        assertTrue(host.activeDebugPane().isPauseOnEntrySelected(), "and pause on entry is forced on");
    }

    @Test
    void theUnsavedEditorTextIsDebugged(@TempDir Path tmp) throws Exception {
        Path xsl = tmp.resolve("dirty.xsl");
        Files.writeString(xsl, "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n"
                + "  <xsl:template match=\"/\"><out>saved</out></xsl:template>\n</xsl:stylesheet>\n");
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.openFile(xsl));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getActiveText().map(t -> t.contains("saved")).orElse(false)));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            var area = host.getActiveCodeArea();
            int at = area.getText().indexOf("saved");
            area.replaceText(at, at + "saved".length(), "edited");
        });
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.startXsltDebug(xsl.toFile(), XML, Map.of(), OutputFormat.XML));
        pausedSession();
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.activeDebugPane().getMessagesView().getRowCount() == 1);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeDebugPane().onContinue());
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> host.activeDebugPane().getOutputView().getText().contains("edited"));
        assertTrue(Files.readString(xsl).contains("saved"), "the file itself is untouched");
    }

    @Test
    void pausedValuesShowAfterTheirDeclarationsAndGoWhenTheRunResumes(@TempDir Path tmp) throws Exception {
        Path xsl = tmp.resolve("values.xsl");
        Files.writeString(xsl, "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" // 1
                + "  <xsl:variable name=\"title\" select=\"concat('Order of ', count(//item))\"/>\n"                                        // 2
                + "  <xsl:template match=\"item\">\n"                                                             // 3
                + "    <xsl:variable name=\"n\" select=\"string(@n)\"/>\n"                                     // 4
                + "    <x><xsl:value-of select=\"$title, $n\"/></x>\n"                                            // 5
                + "  </xsl:template>\n"
                + "  <!-- the global is evaluated on first use: here, before the item template runs (a constant would be inlined) -->\n"
                + "  <xsl:template match=\"/\"><out t=\"{$title}\"><xsl:apply-templates select=\"//item\"/></out></xsl:template>\n"
                + "</xsl:stylesheet>\n");
        org.fxt.freexmltoolkit.debugger.BreakpointStore.save(xsl.toString(),
                java.util.List.of(new org.fxt.freexmltoolkit.debugger.Breakpoint(xsl.toString(), 5, true)));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.startXsltDebug(xsl.toFile(), XML, Map.of(), OutputFormat.XML));
        pausedSession();
        OpenDocument sheet = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveDocument().orElseThrow());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.debugInlineValuesOf(sheet).size() == 2));
        assertEquals(Map.of(2, "$title = Order of 1", 4, "$n = 1"),
                WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.debugInlineValuesOf(sheet)));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeDebugPane().onContinue());
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.debugInlineValuesOf(sheet).isEmpty()));
    }
}
