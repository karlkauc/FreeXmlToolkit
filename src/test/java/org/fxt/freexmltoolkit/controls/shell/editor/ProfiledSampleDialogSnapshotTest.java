package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.domain.GenerationStrategy;
import org.fxt.freexmltoolkit.domain.XPathInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Manual aid (gated by {@code FXT_SHELL_SNAPSHOT}): snapshots the advanced sample-data dialog. */
@ExtendWith(ApplicationExtension.class)
class ProfiledSampleDialogSnapshotTest {

    private StackPane root;

    @Start
    void start(Stage stage) {
        ProfiledSampleDialog dialog = new ProfiledSampleDialog(List.of(
                new XPathInfo("/order/@id", "xs:string", false, true, 0),
                new XPathInfo("/order/orderDate", "xs:date", true, false, 1),
                new XPathInfo("/order/country", "ISOCountryType", true, false, 2),
                new XPathInfo("/order/items/item/sku", "xs:string", true, false, 3),
                new XPathInfo("/order/items/item/qty", "xs:int", true, false, 4)));
        dialog.getRows().get(0).strategyProperty().set(GenerationStrategy.SEQUENCE);
        dialog.getRows().get(0).configProperty().set("ORD-{seq:5}");
        dialog.getRows().get(2).strategyProperty().set(GenerationStrategy.ENUM_CYCLE);
        dialog.getRows().get(3).strategyProperty().set(GenerationStrategy.TEMPLATE);
        dialog.getRows().get(3).configProperty().set("SKU-{random:6}");
        dialog.setBatch(10, "order_{seq:3}.xml");

        // Render the dialog's content node directly (avoid a modal showAndWait + DialogPane's own
        // header/buttonbar layout in a headless test).
        javafx.scene.Node content = dialog.getDialogPane().getContent();
        root = new StackPane(content);
        stage.setScene(new Scene(root, 760, 520));
        stage.show();
    }

    @Test
    void snapshotDialog() throws Exception {
        if (!"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return;
        }
        Path out = Path.of(System.getProperty("java.io.tmpdir"), "fxt_smoke");
        java.nio.file.Files.createDirectories(out);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            root.applyCss();
            root.layout();
            return null;
        });
        WaitForAsyncUtils.sleep(400, java.util.concurrent.TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        var img = WaitForAsyncUtils.waitForAsyncFx(5000, () -> root.snapshot(new SnapshotParameters(), null));
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png",
                out.resolve("profiled_sample_dialog.png").toFile());
    }
}
