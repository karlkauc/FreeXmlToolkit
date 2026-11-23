package org.fxt.freexmltoolkit.screenshots;

import java.util.concurrent.TimeUnit;

import javafx.scene.control.ListView;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.TransformPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Captures the images of {@code docs/get-started/first-steps.md}, following its steps with the bundled
 * getting-started example: the Welcome page of a first start ({@code first-steps-welcome}), the order
 * with {@code quantity="two"} after F8 ({@code first-steps-validate}) and the HTML summary in the
 * OUTPUT panel ({@code first-steps-transform}).
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*FirstStepsDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class FirstStepsDocScreenshotGenerator {

    private final DocShell doc = new DocShell("first-steps-screenshot");

    @Start
    void start(Stage stage) throws Exception {
        // a first start: without recent files the Welcome page offers "Open an Example"
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        doc.start(stage);
    }

    @Test
    void generateFirstStepsScreenshots() throws Exception {
        var shell = doc.boot();
        EditorHost host = doc.host();

        // 1. the Welcome page of a first start, with "Open an Example"
        doc.scene("first-steps-welcome", () -> doc.shot("first-steps-welcome"));

        // 2. "Open an Example", quantity="two", F8: the PROBLEMS panel lists the error
        doc.scene("first-steps-validate", () -> {
            doc.call(shell, "openExample");
            WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                    () -> host.getActiveText().map(t -> t.contains("PO-1042")).orElse(false));
            doc.settle(1500);
            doc.onFx(() -> {
                String text = host.getActiveText().orElseThrow();
                int at = text.indexOf("quantity=\"2\"") + "quantity=\"".length();
                host.replaceActiveTextRegion(at, at + 1, "two");
            });
            doc.settle(600);
            doc.onFx(host::runValidation);
            doc.waitUntilFx(30, () -> !host.getActiveProblems().isEmpty());
            doc.settle(800);
            // "Select the problem. The caret moves to the line that caused it."
            doc.onFx(() -> {
                ListView<?> problems = doc.find("#problems-panel-list");
                problems.getSelectionModel().select(0);
            });
            doc.settle(800);
            doc.shot("first-steps-validate");
            // repair it again, as the page says
            doc.onFx(() -> {
                String text = host.getActiveText().orElseThrow();
                int at = text.indexOf("quantity=\"two\"") + "quantity=\"".length();
                host.replaceActiveTextRegion(at, at + 3, "2");
            });
            doc.onFx(host::runValidation);
            doc.settle(1500);
        });

        // 4. Tools → Transform with XSLT… with order-summary.xslt: the HTML summary in OUTPUT
        doc.scene("first-steps-transform", () -> {
            java.io.File xslt = host.getActiveDocument().orElseThrow().getPath()
                    .resolveSibling("order-summary.xslt").toFile();
            doc.activity(Activity.TRANSFORM);
            TransformPanel panel = doc.find(".fxt-transform-panel");
            doc.onFx(() -> {
                host.bottomDock().setPrefHeight(440); // room for the whole summary
                panel.setInputOverride(null); // the active document, as Tools → Transform with XSLT… does
                panel.setXsltFile(xslt);
                panel.transform();
            });
            doc.waitUntilFx(60, () -> panel.getOutputText() != null && panel.getOutputText().contains("Order total"));
            doc.settle(1500);
            doc.shot("first-steps-transform");
        });
    }
}
