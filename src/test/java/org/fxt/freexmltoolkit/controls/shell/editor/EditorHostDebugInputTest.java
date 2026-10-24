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
}
