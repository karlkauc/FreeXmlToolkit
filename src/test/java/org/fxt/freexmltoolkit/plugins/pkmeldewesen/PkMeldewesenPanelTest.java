package org.fxt.freexmltoolkit.plugins.pkmeldewesen;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.plugin.PluginUi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The PK-Meldewesen panel describes the active report and opens the taxonomy's schema. */
@ExtendWith(ApplicationExtension.class)
class PkMeldewesenPanelTest {

    @TempDir
    static Path store;
    private EditorHost host;
    private PkMeldewesenPanel panel;
    private Path opened;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        PkTaxonomy taxonomy = new PkTaxonomy(store);
        taxonomy.ensureBundled();
        PluginUi ui = new PluginUi() {
            @Override public EditorHost editorHost() { return host; }
            @Override public Window window() { return stage; }
            @Override public void openFile(Path file) { opened = file; }
            @Override public void showActivity(String id) { }
            @Override public void refreshContributions() { }
            @Override public void showEditorBar(Node bar) { }
            @Override public void hideEditorBar() { }
        };
        panel = new PkMeldewesenPanel(ui, taxonomy);
        stage.setScene(new Scene(new HBox(panel, host), 1100, 700));
        stage.show();
    }

    private String text(String id) {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> ((Label) panel.lookup(id)).getText());
    }

    @Test
    void describesTheActiveReportAndOpensItsSchema(@TempDir Path tmp) throws Exception {
        assertTrue(text("#pk-report-summary").contains("QMV"), "without a report the panel says what it is for");
        Path report = tmp.resolve("report.xml");
        try (var in = getClass().getResourceAsStream("/pkmeldewesen/synthetic-pfq.xml")) {
            Files.write(report, in.readAllBytes());
        }
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(report));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> text("#pk-report-summary").startsWith("PK-QMV Q1/2026"));
        String summary = text("#pk-report-summary");
        assertTrue(summary.contains("Quartalsmeldung"), summary);
        assertTrue(summary.contains("99"), summary);
        assertTrue(summary.contains("Testmeldung"), summary);
        assertTrue(text("#pk-taxonomy").contains(PkTaxonomy.BUNDLED_VERSION), text("#pk-taxonomy"));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.onOpenSchema();
            return null;
        });
        assertNotNull(opened);
        assertTrue(opened.getFileName().toString().startsWith("pk_meldung_pfq_"), String.valueOf(opened));
    }

    @Test
    void ruleReportOpensInThePreviewAndOptionsAreStored(@TempDir Path tmp) throws Exception {
        Path report = tmp.resolve("rules-pfq.xml");
        try (var in = getClass().getResourceAsStream("/pkmeldewesen/rules-pfq.xml")) {
            Files.write(report, in.readAllBytes());
        }
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(report));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> text("#pk-report-summary").startsWith("PK-QMV"));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ((javafx.scene.control.TextField) panel.lookup("#pk-tolerance")).setText("60,5");
            ((javafx.scene.control.TextField) panel.lookup("#pk-tolerance")).fireEvent(new javafx.event.ActionEvent());
            ((javafx.scene.control.CheckBox) panel.lookup("#pk-additional-checks")).setSelected(false);
            return null;
        });
        PkRuleCheck.Options stored = PkSettings.ruleOptions();
        assertEquals(new java.math.BigDecimal("60.5"), stored.tolerance());
        assertFalse(stored.additional());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.onRuleReport();
            return null;
        });
        WaitForAsyncUtils.waitFor(60, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getActiveDocument().map(d -> d.getDisplayName().equals("rules-pfq-pruefbericht.html")).orElse(false)));
        String html = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveText().orElse(""));
        assertTrue(html.contains("Prüfbericht"), "the report opened");
        assertFalse(html.contains("INT_A02"), "additional checks were switched off");
        PkSettings.setRuleOptions(PkRuleCheck.Options.defaults());
    }
}
