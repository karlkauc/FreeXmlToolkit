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
 * Signature side panel (nav + KEYSTORE + sign form) for visual comparison
 * against the Figma mockup "Redesign · Unified — Signature" (50:2).
 */
@ExtendWith(ApplicationExtension.class)
class SignaturePanelSnapshotTest {

    private EditorHost host;
    private SignaturePanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new SignaturePanel(host);
        javafx.scene.layout.HBox root = new javafx.scene.layout.HBox(panel, host);
        javafx.scene.layout.HBox.setHgrow(host, javafx.scene.layout.Priority.ALWAYS);
        Scene scene = new Scene(root, 1100, 820);
        for (String sheet : new String[]{"/css/design-tokens.css", "/css/unified-shell.css"}) {
            var css = getClass().getResource(sheet);
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
        }
        panel.setPrefWidth(260);
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void snapshotSignaturePanel() throws Exception {
        if (!"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return;
        }
        Path out = Path.of(System.getProperty("java.io.tmpdir"), "fxt_smoke");
        Files.createDirectories(out);

        Path tmp = Files.createTempDirectory("fxt_sig_snapshot");
        Path ks = tmp.resolve("keystore.jks");
        Files.writeString(ks, "stub");
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            panel.setKeystore(ks.toFile());
            panel.setCredentials("fxt-signer", "secret", "secret");
            return null;
        });

        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        var img = WaitForAsyncUtils.waitForAsyncFx(5000,
                () -> panel.snapshot(new SnapshotParameters(), null));
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png",
                out.resolve("signature_panel.png").toFile());
    }

    @Test
    void snapshotSignCard() throws Exception {
        if (!"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return;
        }
        Path out = Path.of(System.getProperty("java.io.tmpdir"), "fxt_smoke");
        Files.createDirectories(out);

        Path tmp = Files.createTempDirectory("fxt_sig_card");
        Path xml = tmp.resolve("FundsXML_433_Bond_Fund.xml");
        Files.writeString(xml, "<FundsXML4>demo</FundsXML4>");
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.openFile(xml));
        WaitForAsyncUtils.sleep(800, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            panel.setCredentials("fxt-signer", "secret", "secret");
            panel.openSignCard();
            return null;
        });

        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        var img = WaitForAsyncUtils.waitForAsyncFx(5000,
                () -> host.snapshot(new SnapshotParameters(), null));
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png",
                out.resolve("signature_card.png").toFile());
    }
}
