package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Manual aid (gated by {@code FXT_SHELL_SNAPSHOT}): snapshots the rebuilt
 * Explorer side panel with seeded open editors and a workspace folder, for
 * visual comparison against the Figma mockup "Redesign · Unified Editor
 * (Light)" (node 28:48).
 */
@ExtendWith(ApplicationExtension.class)
class ExplorerPanelSnapshotTest {

    private EditorHost host;
    private ExplorerPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new ExplorerPanel(host);
        Scene scene = new Scene(panel, 260, 820);
        for (String sheet : new String[]{"/css/design-tokens.css", "/css/unified-shell.css"}) {
            var css = getClass().getResource(sheet);
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
        }
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void snapshotExplorerPanel() throws Exception {
        if (!"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return;
        }
        Path out = Path.of(System.getProperty("java.io.tmpdir"), "fxt_smoke");
        Files.createDirectories(out);

        // Seed a workspace (folders + files), two open editors and a dirty doc.
        Path ws = Files.createTempDirectory("fxt_workspace");
        Path data = Files.createDirectory(ws.resolve("data"));
        Files.writeString(data.resolve("FundsXML_433_Bond_Fund.xml"), "<FundsXML4/>");
        Files.writeString(data.resolve("sample_eqty_02.xml"), "<FundsXML4/>");
        Path schema = Files.createDirectory(ws.resolve("schema"));
        Files.writeString(schema.resolve("FundsXML.xsd"),
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");
        Files.writeString(schema.resolve("business_rules.sch"), "<schema/>");

        WaitForAsyncUtils.waitForAsyncFx(5000,
                () -> host.openFile(data.resolve("FundsXML_433_Bond_Fund.xml")));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.getOpenDocuments().size() == 1);
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.openFile(schema.resolve("FundsXML.xsd")));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.getOpenDocuments().size() == 2);
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            host.selectDocument(host.getOpenDocuments().get(0));
            panel.setWorkspaceFolder(ws);
            return null;
        });

        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        var img = WaitForAsyncUtils.waitForAsyncFx(5000,
                () -> panel.snapshot(new SnapshotParameters(), null));
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png",
                out.resolve("explorer_panel.png").toFile());
    }
}
