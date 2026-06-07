package org.fxt.freexmltoolkit.screenshots;

import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controller.MainController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Generates the user-documentation screenshots that are referenced from {@code docs/*.md}
 * but missing from {@code docs/img/}.
 *
 * <p>This is <strong>not</strong> a normal unit test - it launches the real application and
 * captures full-window PNGs. It is excluded from the {@code test} task and must be run via the
 * dedicated {@code docScreenshots} Gradle task on a real X display:
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots
 * }</pre>
 *
 * <p>Capturing uses {@link java.awt.Robot} against the X framebuffer so that JavaFX popups and
 * dialogs (which are separate windows) are included in the image. Each capture is wrapped so a
 * single failure does not abort the rest of the run.
 */
@ExtendWith(ApplicationExtension.class)
class DocScreenshotGenerator {

    private static final File EXAMPLES = new File("release/examples");
    private static final File IMG_DIR = new File("docs/img");

    private static MainController mainController;

    @Start
    private void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/main.fxml"));
        Parent root = loader.load();
        mainController = loader.getController();

        Scene scene = new Scene(root, 1680, 1000);
        stage.setScene(scene);
        stage.setX(0);
        stage.setY(0);
        stage.setWidth(1680);
        stage.setHeight(1000);
        stage.show();
    }

    @Test
    void generateScreenshots() throws Exception {
        IMG_DIR.mkdirs();
        settle(2500);

        // (XSD tools retired in Phase 10c — XSD editing, Type Library/Editor, documentation,
        // flatten and schema analysis live in the Unified Shell.)

        // (Digital signatures retired in Phase 10c — signing, validation, trust
        // validation and certificate creation live in the Unified Shell's Signature panel.)

        // (JSON editor retired in Phase 10c — JSON editing/tree view lives in the Unified Shell.)

        // (XSLT Developer retired in Phase 10c — XSLT/XQuery editing, transform,
        // debugger, batch, profile and trace live in the Unified Shell's Transform panel.)

        // (XSLT Viewer retired in Phase 10c — quick XSLT transforms live in the
        // Unified Shell's Transform panel and the XSLT Developer.)

        // (XSD Validation retired in Phase 10c — XSD/Schematron validation, single
        // and batch, lives in the Unified Shell's Validation activity panel.)

        // (Schematron editor retired in Phase 10c — Schematron editing/validation
        // and the rule tools live in the Unified Shell's Validation activity panel.)

        // --- Templates (Template Builder sub-tab) ---
        safe("templates", () -> {
            runFx(() -> mainController.openTemplateBuilder());
            settle(2500);
            capture("templates-overview");
        });

        // --- IntelliSense (XML editor with schema-backed document) ---
        safe("intellisense", () -> {
            File xml = example("xml", "context-sensitive-demo.xml");
            runFx(() -> mainController.switchToXmlViewAndLoadFile(xml));
            settle(3500);
            capture("intellisense-overview");
        });
    }

    // ---------------------------------------------------------------- helpers

    private interface Capture {
        void run() throws Exception;
    }

    private void safe(String label, Capture c) {
        try {
            c.run();
        } catch (Throwable t) {
            System.out.println("[screenshot] SKIPPED '" + label + "': " + t);
        }
    }

    private static File example(String sub, String name) {
        return new File(new File(EXAMPLES, sub), name);
    }

    private void runFx(Runnable action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(60, TimeUnit.SECONDS)) {
            throw new IllegalStateException("FX action timed out");
        }
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void settle(long millis) throws InterruptedException {
        WaitForAsyncUtils.waitForFxEvents();
        Thread.sleep(millis);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void capture(String name) throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        java.awt.Robot awt = new java.awt.Robot();
        Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage img = awt.createScreenCapture(screen);
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(img, "png", out);
        System.out.println("[screenshot] wrote " + out.getAbsolutePath() + " (" + img.getWidth() + "x" + img.getHeight() + ")");
    }
}
