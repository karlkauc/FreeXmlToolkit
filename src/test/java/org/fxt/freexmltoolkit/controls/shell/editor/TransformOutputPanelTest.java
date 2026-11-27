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
    void aHugeResultShowsItsStartButSavesAndReportsAllOfIt(@TempDir Path tmp) throws Exception {
        String line = "<row>" + "x".repeat(90) + "</row>\n";
        String huge = "<rows>\n" + line.repeat(TransformOutputPanel.SHOWN_LIMIT_CHARS / line.length() + 500) + "</rows>\n";
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            out.showTransformResult(huge, org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.XML, 5);
            return null;
        });
        String shown = WaitForAsyncUtils.waitForAsyncFx(5000, out::shownText);
        assertTrue(shown.length() < huge.length() && shown.length() <= TransformOutputPanel.SHOWN_LIMIT_CHARS + 300,
                "only the start is shown: " + shown.length());
        int cut = shown.indexOf("\n\n…");
        assertTrue(cut > 0 && huge.startsWith(shown.substring(0, cut)), "cut at a line end, then the note");
        assertTrue(shown.contains(String.valueOf(huge.length()).substring(0, 1)), shown.substring(cut));
        assertEquals(huge, out.getOutputText());

        Path saved = tmp.resolve("result.xml");
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            out.saveResultTo(saved.toFile());
            return null;
        });
        assertEquals(huge, Files.readString(saved));
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
    void failedRunResultIsNotReportedAsAnAppErrorButOtherFailuresAre() {
        var telemetry = new org.fxt.freexmltoolkit.service.telemetry.RecordingTelemetryService();
        org.fxt.freexmltoolkit.service.telemetry.Telemetry.install(telemetry);
        try {
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                // a failed run is already recorded as its own usage event (xslt_transform …)
                out.showTransformResult("ERROR: XTSE0010 broken stylesheet", null, 3);
                return null;
            });
            assertTrue(telemetry.errorReports().isEmpty(), telemetry.errorReports().toString());

            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                out.showFailure("Could not read stylesheet: denied");
                return null;
            });
            assertEquals(java.util.List.of("dialog.Transform failed"), telemetry.errorReports());
        } finally {
            org.fxt.freexmltoolkit.service.telemetry.Telemetry.reset();
        }
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
        // XQuery runs in the Query Console (UX plan 3.3); "Table" shows the result in this panel.
        QueryConsole console = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new QueryConsole(host));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXQuery("for $i in /order/item return $i");
            console.runForTest();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, console::isTableAvailableForTest);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.onShowTable();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> out.getResultRowCount() == 2);
        assertTrue(out.isResultTableShown(), "a tabular XQuery result must auto-select the Table view");
        assertEquals(java.util.List.of("sku", "qty"), out.getResultColumns());
    }

    @Test
    void xmlResultIsSyntaxHighlightedWithLineNumbers(@TempDir Path tmp) throws Exception {
        transformGreeting(tmp);
        // the spans are computed off the FX thread
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                out::outputStyleClassesForTest).contains("anytag"));
        java.util.Set<String> classes =
                WaitForAsyncUtils.waitForAsyncFx(2000, out::outputStyleClassesForTest);
        assertTrue(classes.contains("tagmark") && classes.contains("anytag"),
                "an XML result must carry XML highlight classes, got: " + classes);
        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(out.lookup(".lineno"), "the output CodeArea must show a line-number gutter");
    }

    @Test
    void jsonQueryResultIsSniffedAndHighlighted(@TempDir Path tmp) throws Exception {
        // JSONPath results arrive without an OutputFormat — the panel sniffs the text.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            out.showQueryResult("{\"greeting\": \"Hello\", \"count\": 2}", 7);
            return null;
        });
        // the spans are computed off the FX thread
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                out::outputStyleClassesForTest).contains("json-key"));
        java.util.Set<String> classes =
                WaitForAsyncUtils.waitForAsyncFx(2000, out::outputStyleClassesForTest);
        assertTrue(classes.contains("json-key"),
                "a JSON query result must carry JSON highlight classes, got: " + classes);
    }

    @Test
    void errorMessageStaysUnhighlighted(@TempDir Path tmp) throws Exception {
        openGreeting(tmp);
        Path sheet = tmp.resolve("bad.xslt");
        Files.writeString(sheet, "<xsl:not-a-stylesheet/>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(sheet.toFile());
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> out.getOutputText().startsWith("ERROR"));
        java.util.Set<String> classes =
                WaitForAsyncUtils.waitForAsyncFx(2000, out::outputStyleClassesForTest);
        assertTrue(classes.isEmpty(),
                "error messages must stay unstyled, got: " + classes);
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
    void aLargeHtmlResultIsNotRenderedUntilAsked() throws Exception {
        String row = "<tr><td>" + "x".repeat(90) + "</td></tr>\n";
        String html = "<html><body><table>\n"
                + row.repeat(TransformOutputPanel.PREVIEW_AUTO_LIMIT_CHARS / row.length() + 100) + "</table></body></html>";
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            out.showTransformResult(html, org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.HTML, 5);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, out::isLargePreviewPending), "not rendered on its own");
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, out::isHtmlPreviewShown), "the Text view is shown");
        assertNotNull(WaitForAsyncUtils.waitForAsyncFx(2000, () -> out.lookup("#transform-output-large-html")));

        // "Show anyway" renders it
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            ((javafx.scene.control.Button) out.lookup("#transform-output-render-anyway")).fire();
            return null;
        });
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, out::isLargePreviewPending));

        // a small result renders as before
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            out.showTransformResult("<html><body>ok</body></html>",
                    org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.HTML, 5);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, out::isLargePreviewPending));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, out::isHtmlPreviewShown));
    }

    @Test
    void editorActionOpensHtmlResultRenderedInPreviewMode() throws Exception {
        String html = "<html><body><h1>Report</h1></body></html>";
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            out.showTransformResult(html,
                    org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.HTML, 5);
            out.openResultInEditor();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(EditorFileType.HTML, out.getResultDocument().getFileType(),
                "an HTML result must open as an HTML document, not XML");
        assertEquals(ViewMode.PREVIEW, host.activeViewModeProperty().get(),
                "an HTML result tab must open rendered, in Preview mode");

        // Re-triggering must reuse the tab and keep the rendered view.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            out.openResultInEditor();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, countResultTabs(), "re-triggering the Editor action must reuse the tab");
        assertEquals(ViewMode.PREVIEW, host.activeViewModeProperty().get());
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

    @Test
    void messagesAndTraceLinesOfATransformAreListedUnderTheResult(@TempDir Path tmp) throws Exception {
        openGreeting(tmp);
        Path sheet = tmp.resolve("m.xslt");
        Files.writeString(sheet, """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/greeting">
                    <xsl:message>starting</xsl:message>
                    <out><xsl:value-of select="trace(string(.), 'value')"/></out>
                  </xsl:template>
                </xsl:stylesheet>
                """);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(sheet.toFile());
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> out.getOutputText().contains("<out>Hello</out>"));
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.waitForAsyncFx(1000, out::getRunMessages).size() == 2);
        var lines = WaitForAsyncUtils.waitForAsyncFx(1000, out::getRunMessages);
        assertTrue(lines.get(0).contains("starting") && lines.get(0).contains("m.xslt:3"), lines.toString());
        assertTrue(lines.get(1).startsWith("[TRACE]") && lines.get(1).contains("Hello"), lines.toString());
        javafx.scene.control.ToggleButton toggle = (javafx.scene.control.ToggleButton) out.lookup("#transform-output-messages");
        assertTrue(toggle.isVisible() && toggle.getText().contains("2"), toggle.getText());

        transformGreeting(tmp); // a run without messages hides the strip
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> !toggle.getParent().isVisible());
    }

    @Test
    void theOutputOptionsApplyWhereTheStylesheetSaysNothing(@TempDir Path tmp) throws Exception {
        openGreeting(tmp);
        Path sheet = tmp.resolve("plain.xslt");
        Files.writeString(sheet, """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/greeting"><out><in/></out></xsl:template>
                </xsl:stylesheet>
                """);
        try {
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                ((javafx.scene.control.CheckBox) panel.lookup("#transform-output-indent")).fire();
                ((javafx.scene.control.CheckBox) panel.lookup("#transform-output-omit-declaration")).fire();
                panel.setXsltFile(sheet.toFile());
                panel.transform();
                return null;
            });
            WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> out.getOutputText().contains("<out>"));
            assertEquals("<out><in/></out>", out.getOutputText().strip());
        } finally {
            TransformOutputSettings.set(true, false);
        }
    }

    @Test
    void theStylesheetsPlanOpensInATab(@TempDir Path tmp) throws Exception {
        transformGreeting(tmp);
        WaitForAsyncUtils.waitForAsyncFx(2000, panel::onShowPlan);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.waitForAsyncFx(1000, () -> host.lookup(".fxt-plan-view")) != null);
        PlanView plan = (PlanView) WaitForAsyncUtils.waitForAsyncFx(1000, () -> host.lookup(".fxt-plan-view"));
        assertTrue(plan.getText().contains("templateRule") || plan.getText().contains("template"),
                plan.getText().substring(0, Math.min(300, plan.getText().length())));
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
