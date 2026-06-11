package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the {@link TransformOutputPanel} docked below the editor:
 * it appears on a run with format badge + status, switches between Preview/Text/Table,
 * hides via ✕ and re-shows on the next run, and survives a Transform panel
 * recreation (activity switch) because it is owned by the {@link EditorHost}.
 */
@ExtendWith(ApplicationExtension.class)
class TransformOutputPanelTest {

    private static final String XML = "<greeting>Hello</greeting>";
    private static final String XML_XSLT = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="xml"/>
              <xsl:template match="/greeting"><out><xsl:value-of select="."/></out></xsl:template>
            </xsl:stylesheet>
            """;

    private EditorHost host;
    private TransformPanel panel;
    private TransformOutputPanel out;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new TransformPanel(host);
        out = host.transformOutputPanel();
        stage.setScene(new Scene(new HBox(host, panel), 1100, 600));
        stage.show();
    }

    @Test
    void outputPanelIsHiddenUntilTheFirstRun(@TempDir Path tmp) throws Exception {
        assertFalse(out.isShowing(), "the OUTPUT panel must be hidden before any run");
        transformGreeting(tmp);
        assertTrue(out.isShowing(), "the OUTPUT panel must appear after a transform");
    }

    @Test
    void successfulTransformShowsBadgeAndOkStatus(@TempDir Path tmp) throws Exception {
        transformGreeting(tmp);
        javafx.scene.control.Label badge = (javafx.scene.control.Label) out.lookup(".fxt-output-badge");
        assertNotNull(badge, "the format badge must exist");
        assertTrue(badge.isVisible(), "the format badge must be visible after a run");
        assertEquals("XML", badge.getText());
        assertTrue(out.getStatsText().matches("\\d+ ms · \\d+ chars"), out.getStatsText());
        assertNotNull(out.lookup(".sev-ok"), "a successful run must show the green check icon");
    }

    @Test
    void failedTransformShowsErrorStatus(@TempDir Path tmp) throws Exception {
        openGreeting(tmp);
        Path sheet = tmp.resolve("bad.xslt");
        Files.writeString(sheet, "<xsl:not-a-stylesheet/>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(sheet.toFile());
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> out.getOutputText().startsWith("ERROR"));
        assertEquals("error", out.getStatsText());
        assertNotNull(out.lookup(".sev-error"), "a failed run must show the error icon");
    }

    @Test
    void closeHidesAndNextRunReshows(@TempDir Path tmp) throws Exception {
        transformGreeting(tmp);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            out.hide();
            return null;
        });
        assertFalse(out.isShowing(), "✕ must hide the OUTPUT panel");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, out::isShowing);
        assertTrue(out.isShowing(), "the next run must re-show the OUTPUT panel");
    }

    @Test
    void xqueryTableResultSelectsTheTableView(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("order.xml");
        Files.writeString(xml, "<order><item><sku>A</sku><qty>2</qty></item>"
                + "<item><sku>B</sku><qty>5</qty></item></order>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("order")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXQuery("for $i in /order/item return $i");
            panel.runXQuery();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> out.getResultRowCount() == 2);
        assertTrue(out.isResultTableShown(), "a tabular XQuery result must auto-select the Table view");
        assertEquals(java.util.List.of("sku", "qty"), out.getResultColumns());
    }

    @Test
    void saveResultWritesTheOutputToAFile(@TempDir Path tmp) throws Exception {
        transformGreeting(tmp);
        // saveResult() opens a FileChooser (not scriptable headless) — exercise the
        // write path it delegates to.
        Path target = tmp.resolve("result.xml");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            out.saveResultTo(target.toFile());
            return null;
        });
        assertTrue(Files.readString(target).contains("<out>Hello</out>"));
    }

    @Test
    void editorActionOpensAndReusesTheResultTab(@TempDir Path tmp) throws Exception {
        transformGreeting(tmp);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            out.openResultInEditor();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        long first = countResultTabs();
        assertEquals(1, first, "the Editor action must open the result as a tab");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            out.openResultInEditor();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, countResultTabs(), "re-triggering the Editor action must reuse the tab");
    }

    @Test
    void resultSurvivesATransformPanelRecreation(@TempDir Path tmp) throws Exception {
        transformGreeting(tmp);
        String before = out.getOutputText();
        // Simulate an activity switch: the shell builds a brand-new TransformPanel.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> new TransformPanel(host));
        WaitForAsyncUtils.waitForFxEvents();
        assertSame(out, host.transformOutputPanel(), "the host must keep one long-lived OUTPUT panel");
        assertEquals(before, out.getOutputText(), "the result must survive an activity switch");
        assertTrue(out.isShowing());
    }

    private long countResultTabs() {
        return host.getOpenDocuments().stream()
                .filter(d -> d.getDisplayName() != null && d.getDisplayName().startsWith("Transform-Result"))
                .count();
    }

    private void openGreeting(Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("greeting")).orElse(false));
    }

    private void transformGreeting(Path tmp) throws Exception {
        openGreeting(tmp);
        Path sheet = tmp.resolve("t.xslt");
        Files.writeString(sheet, XML_XSLT);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(sheet.toFile());
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> out.getOutputText().contains("<out>Hello</out>"));
    }
}
