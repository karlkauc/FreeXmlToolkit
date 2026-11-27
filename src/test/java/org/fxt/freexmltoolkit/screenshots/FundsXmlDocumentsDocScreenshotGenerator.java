package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Captures {@code docs/img/fundsxml-documents.png} for {@code docs/industry/fundsxml.md}: the Documents dialog of the
 * FundsXML panel on the fictional documents delivery (a prospectus as a link, a KID and a factsheet embedded), with
 * the embedded KID selected.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*FundsXmlDocumentsDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class FundsXmlDocumentsDocScreenshotGenerator {

    private static final File IMG_DIR = new File("docs/img");
    private static final String TAG = "fundsxml-documents-screenshot";
    private Stage stage;
    private EditorHost host;
    private FundsXmlPanel panel;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.controls.shell.DialogTheming.installGlobally(); // as FxtGui: the shell look
        for (String font : new String[]{"Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold"}) {
            javafx.scene.text.Font.loadFont(getClass().getResourceAsStream("/css/fonts/" + font + ".ttf"), 10);
        }
        host = new EditorHost();
        panel = new FundsXmlPanel(host);
        Scene scene = new Scene(new HBox(panel, host), 1200, 720);
        for (String sheet : new String[] {"/css/design-tokens.css", "/css/unified-shell.css"}) {
            scene.getStylesheets().add(getClass().getResource(sheet).toExternalForm());
        }
        ThemeManager.apply(scene, false);
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void generateDocumentsDialog() throws Exception {
        IMG_DIR.mkdirs();
        Path file = Files.createTempDirectory("fxt-docs").resolve("Fund-Documents_2025-10.xml");
        Files.copy(Path.of("src/test/resources/fundsxml/video/Fund-Documents_2025-10.xml"), file);
        ScreenshotFx.run(TAG, () -> host.openFile(file.toFile()));
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> ScreenshotFx.call(TAG,
                () -> host.getActiveText().map(t -> t.contains("<Documents>")).orElse(false)));

        ScreenshotFx.run(TAG, panel::onDocuments);
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> ScreenshotFx.call(TAG, () -> table() != null
                && !table().getItems().isEmpty()));
        ScreenshotFx.run(TAG, () -> {
            TableView<?> table = table();
            table.requestFocus();
            table.getSelectionModel().select(Math.min(1, table.getItems().size() - 1)); // an embedded document
        });
        try {
            new java.awt.Robot().mouseMove(0, 0); // no hover tint from the pointer of the virtual screen
        } catch (java.awt.AWTException | RuntimeException headless) {
            // no robot: the image may show a hovered cell
        }
        WaitForAsyncUtils.sleep(800, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        File out = new File(IMG_DIR, "fundsxml-documents.png");
        ImageIO.write(SwingFXUtils.fromFXImage(ScreenshotFx.call(TAG,
                () -> dialogWindow().getScene().getRoot().snapshot(new SnapshotParameters(), null)), null), "png", out);
        System.out.println("[" + TAG + "] wrote " + out.getAbsolutePath());
        ScreenshotFx.run(TAG, () -> dialogWindow().hide());
    }

    private Window dialogWindow() {
        return Window.getWindows().stream()
                .filter(w -> w.getScene() != null && w.getScene().lookup("#documents-dialog") != null)
                .findFirst().orElse(null);
    }

    private TableView<?> table() {
        Window w = dialogWindow();
        return w == null ? null : (TableView<?>) w.getScene().lookup("#documents-table");
    }
}
