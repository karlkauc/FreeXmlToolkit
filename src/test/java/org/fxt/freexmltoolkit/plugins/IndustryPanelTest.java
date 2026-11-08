package org.fxt.freexmltoolkit.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** The industry plugins' side panel: document summary, the check, the identifier checker. */
@ExtendWith(ApplicationExtension.class)
class IndustryPanelTest {

    private EditorHost host;
    private IndustryPanel panel;

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
        stage.setScene(new Scene(new HBox(host, panel), 1100, 700));
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
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> {
            String r = panel.checkResultForTesting();
            return r != null && r.startsWith("1 problem");
        });
        assertEquals("1 problem:\n• Line 1: The total is zero.", panel.checkResultForTesting());

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
    }
}
