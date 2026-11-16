package org.fxt.freexmltoolkit.plugins.kagmeldewesen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.plugin.PluginUi;
import org.fxt.freexmltoolkit.plugins.IndustryPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The KAG-Meldewesen panel's DeRiMV import (CSV → XML document) and export (document → Excel). */
@ExtendWith(ApplicationExtension.class)
class KagPanelToolsTest {

    @TempDir
    Path dir;

    private EditorHost host;
    private PluginUi ui;
    private IndustryPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        KagExtras.skipRegistrationForTests();
        host = new EditorHost();
        ui = new PluginUi() {
            @Override public EditorHost editorHost() { return host; }
            @Override public Window window() { return stage; }
            @Override public void openFile(Path file) { host.openFile(file); }
            @Override public void showActivity(String id) { }
            @Override public void refreshContributions() { }
            @Override public void showEditorBar(Node bar) { }
            @Override public void hideEditorBar() { }
        };
        panel = KagPanelTools.install(new IndustryPanel(ui, "KAG", "About.", List.of(), KagMeldewesenPlugin::describe,
                (content, path, name) -> List.of(), List.of()));
        stage.setScene(new Scene(new HBox(host, panel), 1100, 700));
        stage.show();
    }

    @AfterEach
    void resetHook() {
        KagPanelTools.chooser = null;
    }

    @Test
    void importACsvAndExportItAgain() throws Exception {
        Path csv = Files.writeString(dir.resolve("q4.csv"),
                "Beispiel KAG;12345;20254\nB1;AT0000A1B2C4;Fonds A;1,5;2,5;Comm;\nB2;AT0000A1B2E0;Fonds B;3;4;aVaR;10\n",
                StandardCharsets.UTF_8);
        Path xlsx = dir.resolve("out.xlsx");
        KagPanelTools.chooser = (save, name) -> save ? xlsx : csv;

        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            KagPanelTools.importDerimv(ui, panel);
            return null;
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> host.getActiveText().orElse("").contains("<Derivatemeldung"));
        String xml = WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.getActiveText().orElseThrow());
        assertTrue(xml.contains("<ISIN>AT0000A1B2E0</ISIN>") && xml.contains("<GrenzenGesamtrisiko>10.00</GrenzenGesamtrisiko>"), xml);
        assertEquals("2 funds imported from q4.csv.", WaitForAsyncUtils.waitForAsyncFx(5000, () -> panel.status().getText()));

        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            KagPanelTools.exportDerimv(ui, panel);
            return null;
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> Files.isRegularFile(xlsx) && Files.size(xlsx) > 0
                && panel.status().getText().startsWith("2 funds written"));
        try (var in = Files.newInputStream(xlsx)) {
            assertEquals(2, DerimvSpreadsheet.readWorkbook(in).report().funds().size());
        }
    }
}
