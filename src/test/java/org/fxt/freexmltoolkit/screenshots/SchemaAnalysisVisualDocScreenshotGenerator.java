package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.util.concurrent.TimeUnit;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.TypeLibraryPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import javax.imageio.ImageIO;

/**
 * Visual verification of the Schema Analysis tool tab in Light and Dark theme (output goes to
 * {@code build/visual-verify/}, not the docs). Run with
 * {@code xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*SchemaAnalysisVisualDocScreenshotGenerator*"}.
 */
@ExtendWith(ApplicationExtension.class)
class SchemaAnalysisVisualDocScreenshotGenerator {

    private static final File OUT_DIR = new File("build/visual-verify");
    private static final File XSD = new File(System.getProperty("fxt.analysis.xsd", "release/examples/xsd/FundsXML_428.xsd"));

    private Parent root;
    private UnifiedShellView shell;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1680, 1000));
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    @Test
    void verifySchemaAnalysisVisually() throws Exception {
        OUT_DIR.mkdirs();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        shell = (UnifiedShellView) root.lookup(".fxt-shell");
        EditorHost host = shell.getEditorHost();
        settle(800);

        onFx(() -> host.openFile(XSD.toPath()));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("schema")).orElse(false));
        onFx(() -> shell.getSelectionModel().select(Activity.SCHEMA));
        settle(800);
        onFx(() -> {
            if (shell.lookup(".fxt-schema-panel") instanceof TypeLibraryPanel library) {
                library.analyzeActive();
            }
        });
        WaitForAsyncUtils.waitFor(90, TimeUnit.SECONDS, () -> {
            var done = new java.util.concurrent.atomic.AtomicBoolean();
            onFx(() -> done.set(shell.lookup("#analysis-status") instanceof Label l
                    && l.getText().startsWith("Analyzed")));
            return done.get();
        });
        settle(800);
        shot("analysis-statistics-light");
        selectSubTab(1);
        shot("analysis-quality-light");
        selectSubTab(2);
        shot("analysis-constraints-light");
        selectSubTab(3);
        shot("analysis-xpath-light");

        onFx(() -> ThemeManager.apply(shell.getScene(), true));
        settle(800);
        shot("analysis-quality-dark");
        selectSubTab(2);
        shot("analysis-constraints-dark");
        selectSubTab(3);
        shot("analysis-xpath-dark");
        selectSubTab(0);
        shot("analysis-statistics-dark");

        // A small schema with broken constraint XPaths so the XPath tab shows findings.
        java.nio.file.Path broken = java.nio.file.Files.createTempFile("broken-xpath-", ".xsd");
        java.nio.file.Files.writeString(broken, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                           vc:minVersion="1.1">
                  <xs:element name="orders">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="order" maxOccurs="unbounded">
                          <xs:complexType>
                            <xs:attribute name="id" type="xs:string"/>
                            <xs:attribute name="customer" type="xs:string"/>
                            <xs:attribute name="total" type="xs:decimal"/>
                            <xs:assert test="@total >= 0"/>
                            <xs:assert test="@total >= (("/>
                          </xs:complexType>
                        </xs:element>
                        <xs:element name="customer" maxOccurs="unbounded">
                          <xs:complexType><xs:attribute name="id" type="xs:string"/></xs:complexType>
                        </xs:element>
                      </xs:sequence>
                    </xs:complexType>
                    <xs:key name="orderKey"><xs:selector xpath="order"/><xs:field xpath="@id"/></xs:key>
                    <xs:key name="customerKey"><xs:selector xpath="customer"/><xs:field xpath="@id"/></xs:key>
                    <xs:keyref name="orderCustomer" refer="customerKey"><xs:selector xpath="order"/><xs:field xpath="@customer"/></xs:keyref>
                    <xs:unique name="invoiceUnique"><xs:selector xpath="invoice"/><xs:field xpath="@number"/></xs:unique>
                    <xs:keyref name="danglingRef" refer="noSuchKey"><xs:selector xpath="order"/><xs:field xpath="@id"/></xs:keyref>
                  </xs:element>
                </xs:schema>
                """);
        onFx(() -> host.openFile(broken));
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("danglingRef")).orElse(false));
        onFx(() -> shell.getSelectionModel().select(Activity.SCHEMA));
        settle(500);
        onFx(() -> {
            if (shell.lookup(".fxt-schema-panel") instanceof TypeLibraryPanel library) {
                library.analyzeActive();
            }
        });
        WaitForAsyncUtils.waitFor(60, TimeUnit.SECONDS, () -> {
            var done = new java.util.concurrent.atomic.AtomicBoolean();
            onFx(() -> done.set(shell.lookup("#analysis-status") instanceof Label l
                    && l.getText().startsWith("Analyzed broken-xpath")));
            return done.get();
        });
        settle(600);
        selectSubTab(3);
        shot("analysis-xpath-findings-dark");
        onFx(() -> ThemeManager.apply(shell.getScene(), false));
        settle(800);
        shot("analysis-xpath-findings-light");
        selectSubTab(2);
        shot("analysis-constraints-findings-light");
        java.nio.file.Files.deleteIfExists(broken);
        onFx(() -> ThemeManager.apply(shell.getScene(), false));
        settle(300);
    }

    private void selectSubTab(int index) {
        onFx(() -> {
            if (shell.lookup("#schema-analysis-tabs") instanceof TabPane tabs) {
                tabs.getSelectionModel().select(index);
                String tableId = index == 1 ? "#analysis-quality-table" : index == 2 ? "#analysis-constraints-table"
                        : index == 3 ? "#analysis-xpath-table" : null;
                if (tableId != null && shell.lookup(tableId) instanceof TableView<?> table
                        && !table.getItems().isEmpty()) {
                    table.getSelectionModel().select(0);
                }
            }
        });
        settle(600);
        // Selecting an issue reveals its node in the Tree view (switches the document tab);
        // come back to the tool tab so the details pane is what gets captured.
        onFx(() -> shell.getEditorHost().openOrFocusToolTab(
                org.fxt.freexmltoolkit.controls.shell.editor.analysis.SchemaAnalysisView.TITLE,
                org.fxt.freexmltoolkit.controls.shell.editor.analysis.SchemaAnalysisView.ICON,
                () -> { throw new IllegalStateException("analysis tab should already be open"); }));
        settle(800);
    }

    private void onFx(Runnable action) {
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            action.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void shot(String name) throws Exception {
        var img = WaitForAsyncUtils.waitForAsyncFx(8000, () -> shell.snapshot(new SnapshotParameters(), null));
        File out = new File(OUT_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("[visual] wrote " + out.getAbsolutePath());
    }
}
