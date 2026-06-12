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

    private SignaturePanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        EditorHost host = new EditorHost();
        panel = new SignaturePanel(host);
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
}
