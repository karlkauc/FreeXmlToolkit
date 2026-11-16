package org.fxt.freexmltoolkit.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.plugin.DocumentValidator;
import org.fxt.freexmltoolkit.plugin.PluginUi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The industry plugins' side panel: document summary, the check (problems go to the PROBLEMS panel, the panel
 * shows counts), the optional report, the identifier checker.
 */
@ExtendWith(ApplicationExtension.class)
class IndustryPanelTest {

    private EditorHost host;
    private IndustryPanel panel;
    private IndustryPanel reporting;
    private IndustryPanel withLei;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        PluginUi ui = new PluginUi() {
            @Override public EditorHost editorHost() { return host; }
            @Override public Window window() { return stage; }
            @Override public void openFile(Path file) { host.openFile(file); }
            @Override public void showActivity(String id) { }
            @Override public void refreshContributions() { }
            @Override public void showEditorBar(Node bar) { }
            @Override public void hideEditorBar() { }
        };
        DocumentValidator validator = new DocumentValidator() {
            @Override public String name() { return "Orders"; }
            @Override public boolean accepts(String content, String documentName) { return content.contains("<order"); }
            @Override public List<Problem> validate(String content, String documentName) {
                return content.contains("<total>0</total>") ? List.of(new Problem("error", 1, "The total is zero.")) : List.of();
            }
        };
        panel = new IndustryPanel(ui, "ORDERS", "Checks orders.", List.of(), c -> c.contains("<order") ? "An order" : null,
                validator, List.of(IdentifierKind.IBAN, IdentifierKind.AT_UID));
        reporting = new IndustryPanel(ui, "REPORTS", "With a report.", List.of(), c -> c.contains("<order") ? "An order" : null,
                (content, path, name) -> List.of(new DocumentValidator.Problem("warning", 1, "Check the total."),
                        new DocumentValidator.Problem("error", 1, "No customer."), new DocumentValidator.Problem("error", 1, "No date.")),
                List.of()).withReport((content, name) -> "<html><body>Report of " + name + "</body></html>", "-report.html");
        withLei = new IndustryPanel(ui, "FUNDS", "With LEIs.", List.of(), c -> "Funds", validator,
                List.of(IdentifierKind.LEI, IdentifierKind.ISIN));
        stage.setScene(new Scene(new HBox(host, panel, reporting, withLei), 1300, 700));
        stage.show();
    }

    @Test
    void summaryCheckAndIdentifiers() throws Exception {
        assertEquals("No document is open.", WaitForAsyncUtils.waitForAsyncFx(5000, panel::documentSummaryForTesting));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.openGeneratedDocument("<order><total>0</total></order>",
                EditorFileType.XML, "o.xml"));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "An order".equals(panel.documentSummaryForTesting()));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            panel.onCheck();
            return null;
        });
        // without a shell validation the panel runs the plugin's check itself and publishes to PROBLEMS
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "✗ 1 error".equals(panel.checkCountsForTesting()));
        assertEquals(1, WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.getActiveProblems().size()));
        assertEquals("The total is zero.", WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.getActiveProblems().getFirst().message()));
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(5000, panel::reportAvailableForTesting), "no report builder");

        // a checker's warnings and errors are counted apart; the report opens in the preview
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            reporting.onCheck();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "✗ 2 errors · ⚠ 1 warning".equals(reporting.checkCountsForTesting()));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(5000, reporting::reportAvailableForTesting));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            reporting.onReport();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.getActiveText().orElse("").contains("Report of o.xml"));

        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            panel.checkIdentifierForTesting(IdentifierKind.AT_UID, "ATU13585627");
            return null;
        });
        assertEquals("✓ Valid Austrian VAT id (UID)", panel.identifierResultForTesting());
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            panel.checkIdentifierForTesting(IdentifierKind.IBAN, "DE88370400440532013000");
            return null;
        });
        assertTrue(panel.identifierResultForTesting().startsWith("✗ IBAN"), panel.identifierResultForTesting());

        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.openGeneratedDocument("<x/>", EditorFileType.XML, "x.xml"));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> panel.documentSummaryForTesting().startsWith("The active document is not one"));
        assertEquals(null, panel.checkCountsForTesting());
    }

    @Test
    void everyCheckOfAValidDocumentGivesFeedback() throws Exception {
        // like the shell's validation: publishes the same "valid" status on every run
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            host.setValidateHandler(() -> host.publishValidation(host.validationTarget(), EditorHost.ValidationState.VALID,
                    0, "Valid", List.of()));
            host.openGeneratedDocument("<order><total>5</total></order>", EditorFileType.XML, "ok.xml");
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "An order".equals(panel.documentSummaryForTesting()));
        for (int run = 0; run < 2; run++) {
            WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
                panel.status().setText("");
                panel.onCheck();
                return null;
            });
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> panel.status().getText().contains("no problems found"));
            assertTrue(panel.status().getText().startsWith("✓ Checked at"), panel.status().getText());
        }
        assertEquals("✓ No problems found.", panel.checkCountsForTesting());
    }

    @Test
    void aToolButtonIsBusyWhileItsJobRunsAndRefusesASecondRun() throws Exception {
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger runs = new java.util.concurrent.atomic.AtomicInteger();
        javafx.scene.control.Button button = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            panel.withTool(new IndustryPanel.Action("t-slow", "Slow tool", "bi-journal-code",
                    org.fxt.freexmltoolkit.controls.theme.ActionColor.NAVIGATE, u -> panel.background("Slow tool", () -> {
                        runs.incrementAndGet();
                        release.await(10, TimeUnit.SECONDS);
                        return 1;
                    }, r -> { })));
            return (javafx.scene.control.Button) panel.lookup("#t-slow");
        });
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            button.fire();
            return null;
        });
        assertEquals("Running: Slow tool", WaitForAsyncUtils.waitForAsyncFx(5000, button::getText), "busy while running");
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(5000, () -> button.getGraphic() instanceof javafx.scene.control.ProgressIndicator));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            button.fire(); // a second click while it runs
            return null;
        });
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(5000, () -> panel.status().getText()).contains("already running"));
        release.countDown();
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(5000, () -> "Slow tool".equals(button.getText())));
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(5000, () -> button.getGraphic() instanceof javafx.scene.control.ProgressIndicator));
        assertEquals(1, runs.get());
    }

    @Test
    void leisAtGleif() throws Exception {
        org.fxt.freexmltoolkit.service.finance.gleif.Gleif.setCache(new org.fxt.freexmltoolkit.service.finance.gleif.LeiCache(
                new org.fxt.freexmltoolkit.service.finance.gleif.GleifClient(uri -> {
                    try (var in = IndustryPanelTest.class.getResourceAsStream("/gleif/lei-records.json")) {
                        return new org.fxt.freexmltoolkit.service.finance.gleif.GleifClient.Response(200, in.readAllBytes());
                    }
                }, java.time.Clock.systemUTC()), null, java.time.Clock.systemUTC(), java.time.Duration.ofHours(1)));
        try {
            WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
                withLei.checkIdentifierForTesting(IdentifierKind.ISIN, "506700GE1G29325QX363");
                return null;
            });
            assertFalse(withLei.gleifButtonShownForTesting(), "only for an LEI");
            WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
                withLei.checkIdentifierForTesting(IdentifierKind.LEI, "506700GE1G29325QX363");
                return null;
            });
            assertTrue(withLei.gleifButtonShownForTesting());
            WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
                withLei.onGleifLookup();
                return null;
            });
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                    () -> withLei.gleifResultForTesting().contains("Global Legal Entity Identifier Foundation"));
            assertTrue(withLei.gleifResultForTesting().contains("Registration ISSUED, entity ACTIVE"), withLei.gleifResultForTesting());

            // every LEI of the document: GLEIF findings join the PROBLEMS panel
            WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.openGeneratedDocument(
                    "<funds>\n<LEI>506700GE1G29325QX363</LEI>\n<LEI>5299009FXTMMFCP00069</LEI>\n</funds>", EditorFileType.XML, "f.xml"));
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.getActiveText().orElse("").contains("funds"));
            WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
                withLei.checkLeisAtGleif();
                return null;
            });
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !host.getActiveProblems().isEmpty());
            var problem = WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.getActiveProblems().getFirst());
            assertEquals("GLEIF", problem.source());
            assertEquals(3, problem.line());
            assertTrue(withLei.status().getText().contains("2 LEIs"), withLei.status().getText());
        } finally {
            org.fxt.freexmltoolkit.service.finance.gleif.Gleif.setCache(null);
        }
    }
}
