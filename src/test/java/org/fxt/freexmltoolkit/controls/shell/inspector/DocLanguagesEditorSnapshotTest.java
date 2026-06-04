package org.fxt.freexmltoolkit.controls.shell.inspector;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Manual aid (gated by FXT_SHELL_SNAPSHOT): snapshots the per-language documentation editor. */
@ExtendWith(ApplicationExtension.class)
class DocLanguagesEditorSnapshotTest {

    private VBox root;

    @Start
    void start(Stage stage) {
        DocLanguagesEditor editor = new DocLanguagesEditor();
        editor.setEntries(List.of(
                new XsdDocumentation("This element contains the data supplier.", "en"),
                new XsdDocumentation("Dieses Element enthält den Datenlieferanten.", "de")));
        root = new VBox(editor);
        root.setStyle("-fx-padding: 16; -fx-background-color: white;");
        stage.setScene(new Scene(root, 460, 280));
        stage.show();
    }

    @Test
    void snapshot() throws Exception {
        if (!"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return;
        }
        Path out = Path.of(System.getProperty("java.io.tmpdir"), "fxt_smoke");
        java.nio.file.Files.createDirectories(out);
        WaitForAsyncUtils.sleep(400, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        var img = WaitForAsyncUtils.waitForAsyncFx(5000, () -> root.snapshot(new SnapshotParameters(), null));
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out.resolve("doc_languages.png").toFile());
    }
}
