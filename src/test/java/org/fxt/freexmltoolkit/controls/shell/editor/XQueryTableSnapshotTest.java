package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Manual aid (gated by {@code FXT_SHELL_SNAPSHOT}): snapshots the XQuery result-table view. */
@ExtendWith(ApplicationExtension.class)
class XQueryTableSnapshotTest {

    private EditorHost host;
    private TransformPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new TransformPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 680));
        stage.show();
    }

    @Test
    void snapshotXQueryTable(@TempDir Path tmp) throws Exception {
        if (!"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return;
        }
        Path out = Path.of(System.getProperty("java.io.tmpdir"), "fxt_smoke");
        Files.createDirectories(out);
        Path xml = tmp.resolve("orders.xml");
        Files.writeString(xml, "<orders>"
                + "<order id=\"1001\"><customer>ACME</customer><total>250.00</total><status>open</status></order>"
                + "<order id=\"1002\"><customer>Globex</customer><total>99.90</total><status>shipped</status></order>"
                + "<order id=\"1003\"><customer>Initech</customer><total>1200.00</total><status>open</status></order>"
                + "</orders>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("orders")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXQuery("for $o in /orders/order return $o");
            panel.runXQuery();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> panel.getResultRowCount() == 3);
        WaitForAsyncUtils.sleep(400, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();

        var img = WaitForAsyncUtils.waitForAsyncFx(5000, () -> panel.snapshot(new SnapshotParameters(), null));
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out.resolve("xquery_table.png").toFile());
        System.out.println("[SMOKE] XQuery table cols=" + panel.getResultColumns()
                + " rows=" + panel.getResultRowCount() + " tableShown=" + panel.isResultTableShown());
    }
}
