package org.fxt.freexmltoolkit.controls.shell;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Manual aid (gated by {@code FXT_SHELL_SNAPSHOT}): opens the real ~2.86 MB FundsXML4.xsd in the
 * shell Text view and snapshots it, to eyeball that syntax highlighting now works on a &gt;2 MB file
 * (viewport mode) instead of being disabled. Writes {@code /tmp/fxt_smoke/large_xsd_text.png}.
 */
@ExtendWith(ApplicationExtension.class)
class LargeXsdHighlightSmokeTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 1100, 760));
        stage.show();
    }

    @Test
    void largeXsdIsHighlighted() throws Exception {
        if (!"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return;
        }
        Path out = Path.of(System.getProperty("java.io.tmpdir"), "fxt_smoke");
        java.nio.file.Files.createDirectories(out);

        File xsd = new File("src/test/resources/FundsXML4.xsd");
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.openFile(xsd.toPath()));
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("xs:schema")).orElse(false));
        // Ensure the top is visible, then give the viewport-mode debounce time to highlight it.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            var ca = host.getActiveCodeArea();
            if (ca != null) {
                ca.moveTo(0);
                ca.showParagraphAtTop(0);
            }
            return null;
        });
        WaitForAsyncUtils.sleep(2, TimeUnit.SECONDS);
        WaitForAsyncUtils.waitForFxEvents();

        var img = WaitForAsyncUtils.waitForAsyncFx(5000,
                () -> host.snapshot(new SnapshotParameters(), null));
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out.resolve("large_xsd_text.png").toFile());
        System.out.println("[SMOKE] FundsXML4.xsd bytes=" + xsd.length()
                + " textLen=" + host.getActiveText().map(String::length).orElse(0));
    }
}
