package org.fxt.freexmltoolkit.controls.shell;

import org.fxt.freexmltoolkit.screenshots.DocFiles;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Captures the Query Console's error view for the documentation ({@code docs/img/query-console-error.png}):
 * a failed XPath on the getting-started order with code, position, the caret under the error, the hint and the
 * mark in the input. In the shell's package to reach the Query Console.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*QueryErrorDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class QueryErrorDocScreenshotGenerator {

    private static final File IMG_DIR = new File("docs/img");
    private static final Path ORDER = DocFiles.gettingStarted("order.xml");

    private Parent root;
    private UnifiedShellView shell;

    @Start
    void start(Stage stage) throws Exception {
        ServiceRegistry.initialize();
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        props.set(org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlHint.SHOWN_KEY, "true");
        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();
        for (String font : new String[]{"Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold",
                "JetBrainsMono-Regular", "JetBrainsMono-Medium", "JetBrainsMono-Bold"}) {
            javafx.scene.text.Font.loadFont(getClass().getResourceAsStream("/css/fonts/" + font + ".ttf"), 10);
        }
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1680, 1000));
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    @Test
    void generateQueryErrorScreenshot() throws Exception {
        IMG_DIR.mkdirs();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        shell = (UnifiedShellView) root.lookup(".fxt-shell");
        var host = shell.getEditorHost();
        onFx(() -> ThemeManager.apply(root.getScene(), false));
        onFx(() -> {
            shell.getSelectionModel().select(Activity.EXPLORER);
            host.openFile(ORDER.toAbsolutePath());
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("<order")).orElse(false));
        onFx(() -> shell.toggleQueryConsole());
        settle(600);
        try {
            // a "/" step on the attribute values: XPTY0019 with its hint
            onFx(() -> {
                var console = shell.getQueryConsole();
                CodeArea field = (CodeArea) console.lookup(".fxt-xpath-field");
                field.replaceText("distinct-values(//item/@sku)/../@price");
                console.run();
            });
            WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                    () -> shell.getQueryConsole().lookup("#query-error-view").isVisible());
            settle(1500);
            shot("query-console-error");
        } finally {
            onFx(() -> shell.toggleQueryConsole());
        }
    }

    private void onFx(Runnable action) {
        WaitForAsyncUtils.waitForAsyncFx(60_000, () -> {
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
        var img = WaitForAsyncUtils.waitForAsyncFx(60_000,
                () -> shell.snapshot(new SnapshotParameters(), null));
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("[query-error-screenshot] wrote " + out.getAbsolutePath());
    }
}
