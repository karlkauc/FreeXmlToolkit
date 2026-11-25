package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ExampleFiles;
import org.fxt.freexmltoolkit.controls.shell.editor.TransformPanel;
import org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorConfig;
import org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorPreset;
import org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "Run an XSLT stylesheet" (storyboard {@code videos/xslt-run.json},
 * after {@code docs/guides/transform-query/run-xslt.md}): choose the example stylesheet in the Transform
 * panel, run it, read the result as preview and text, pass the {@code currency} parameter, follow an edit
 * with the live preview, and look at the processor choice and its manage dialog.
 *
 * <pre>{@code
 * python3 scripts/make-video.py xslt-run --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class XsltRunDocVideo {

    private final DocShell doc = new DocShell("video");

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        org.fxt.freexmltoolkit.i18n.I18n.initialize(null);
        doc.start(stage, DocVideoRecorder.WIDTH, DocVideoRecorder.HEIGHT);
    }

    @Test
    void record() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        // before the film starts: xsltproc is set up as an external processor (so the panel shows the
        // Processor line), the example stylesheet and order are open, the order is the active tab
        XsltProcessorStore.settings().save(List.of(XsltProcessorConfig.fromPreset(XsltProcessorPreset.XSLTPROC)));
        XsltProcessorStore.settings().setLastId(XsltProcessorStore.BUILTIN_ID);
        Path folder = ExampleFiles.defaultTarget();
        ExampleFiles.install(folder);
        Path xml = folder.resolve("order.xml");
        Path xslt = folder.resolve("order-summary.xslt");
        doc.onFx(() -> host.openFile(xslt));
        doc.waitUntilFx(30, () -> host.getActiveText().orElse("").contains("xsl:stylesheet"));
        doc.onFx(() -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PO-1042")).orElse(false));
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("panel", () -> {
                video.spoken(0.55);
                Bounds bar = video.screenBounds(doc.find("#activityBarHost"));
                video.zoom(new BoundingBox(bar.getMinX(), bar.getMinY(), 700, 500));
                video.spoken(0.8);
                video.click(doc.<Node>find("#activity-transform"));
                video.pause(1200);
                TransformPanel panel = doc.findType(TransformPanel.class);
                doc.onFx(() -> panel.setRunAutomatically(false));
                video.zoomOut();
            });

            video.scene("concept", () -> {
                video.spoken(0.1);
                doc.onFx(() -> host.openFile(xslt));
                doc.waitUntilFx(20, () -> host.getActiveText().orElse("").contains("xsl:stylesheet"));
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), editor.getHeight() * 0.8));
                video.spoken(0.85);
                video.zoomOut();
                doc.onFx(() -> host.openFile(xml));
                doc.waitUntilFx(20, () -> host.getActiveText().orElse("").contains("PO-1042"));
            });

            video.scene("choose", () -> {
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.spoken(0.4);
                Node row = doc.find("#transform-xslt-row");
                Node change = doc.fx(() -> row.lookupAll(".button").stream()
                        .filter(n -> n instanceof Labeled l && "Ändern".equals(l.getText()) || n instanceof Labeled l2
                                && "Change".equals(l2.getText()))
                        .findFirst().orElseThrow());
                video.click(change);
                video.pause(900);
                video.spoken(0.8);
                video.click(video.inPopup("#transform-xslt-tab-0"));
                video.pause(1200);
            });

            video.scene("input", () -> {
                video.moveTo(doc.<Node>find("#transform-input-row"));
                video.spoken(0.35);
                video.moveTo(doc.<Node>find("#transform-output-method"));
                video.spoken(0.75);
                video.moveTo(doc.<Node>find("#transform-output-indent"));
            });

            video.scene("start", () -> {
                Node start = doc.find("#transform-start");
                reveal(start);
                video.moveTo(start);
                video.spoken(0.5);
                video.click(start);
                video.pause(2500);
                video.key(KeyCode.ESCAPE, "Esc");
                video.pause(300);
                doc.onFx(() -> ((javafx.scene.control.ComboBox<?>) start).hide());
            });

            video.scene("run", () -> {
                video.click(doc.<Node>find("#transform-run"));
                video.pause(1500);
                video.zoomOut();
                video.spoken(0.4);
                video.zoom(lowerEditorHalf());
                video.pause(3000); // the output panel takes a moment under the recording's software rendering
            });

            video.scene("preview", () -> {
                video.click(outputToggle("Text"));
                video.pause(800);
                video.spoken(0.55);
                video.moveTo(outputPart(".fxt-output-status"));
            });

            video.scene("save", () -> {
                video.spoken(0.2);
                video.moveTo(doc.<Node>find("#transform-output-editor"));
                video.pause(900);
                video.moveTo(doc.<Node>find("#transform-output-browser"));
                video.pause(900);
                video.moveTo(doc.<Node>find("#transform-output-save"));
                video.pause(900);
                video.click(outputToggle("Vorschau", "Preview"));
            });

            video.scene("params", () -> {
                video.zoomOut();
                TextField value = currencyField();
                reveal(value);
                video.pause(500);
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.spoken(0.35);
                video.click(value);
                video.typeIntoFocused("CHF");
                video.spoken(0.7);
                video.click(doc.<Node>find("#transform-run"));
                video.pause(1500);
                video.zoomOut();
                video.zoom(lowerEditorHalf());
                video.pause(2500);
            });

            video.scene("live", () -> {
                video.zoomOut();
                Node live = doc.find("#transform-live-preview");
                reveal(live);
                video.click(live);
                video.pause(600);
                video.spoken(0.4);
                String text = host.getActiveText().orElseThrow();
                int at = text.indexOf("quantity=\"2\"") + "quantity=\"".length();
                video.zoom(editorTop());
                video.retype(at, 1, "4");
                video.pause(800);
                video.zoom(lowerEditorHalf());
                video.pause(3500);
            });

            video.scene("switches", () -> {
                Node watch = doc.find("#transform-watch-xslt");
                reveal(watch);
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.spoken(0.15);
                video.moveTo(watch);
                video.spoken(0.55);
                Node auto = doc.find("#transform-auto-run");
                reveal(auto);
                video.moveTo(auto);
            });

            video.scene("processor", () -> {
                video.zoomOut();
                Node choice = doc.find("#transform-xslt-processor");
                reveal(choice);
                video.pause(400);
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.spoken(0.4);
                video.click(doc.<Node>find("#transform-xslt-processor-combo"));
                video.pause(2500);
                video.key(KeyCode.ESCAPE, "Esc");
                video.pause(400);
            });

            video.scene("manage", () -> {
                video.spoken(0.1);
                // the dialog is modal: open it later, so the scene can go on while it shows
                Node gear = doc.find("#transform-xslt-processor-manage");
                video.moveTo(gear);
                video.pause(300);
                video.zoomOut();
                video.click(gear);
                video.pause(1500);
                javafx.scene.Scene dialogScene = video.activeScene();
                Node dialogRoot = doc.fx(dialogScene::getRoot);
                javafx.stage.Window dialogWindow = doc.fx(dialogScene::getWindow);
                video.zoom(doc.fx(() -> new BoundingBox(dialogWindow.getX(), dialogWindow.getY(),
                        dialogWindow.getWidth(), dialogWindow.getHeight())));
                video.spoken(0.45);
                Node add = doc.fx(() -> dialogRoot.lookupAll(".button").stream()
                        .filter(n -> n instanceof Labeled l && l.getText() != null && l.getText().contains("13.1"))
                        .findFirst().orElse(null));
                if (add != null) {
                    video.moveTo(add);
                }
                video.spoken(0.95);
                video.key(KeyCode.ESCAPE, "Esc");
                video.pause(600);
                doc.onFx(() -> {
                    if (dialogScene.getWindow() != null && dialogScene.getWindow() != doc.stage()) {
                        dialogScene.getWindow().hide(); // in case Escape did not reach the dialog
                    }
                });
                video.zoomOut();
            });
        } finally {
            video.stop();
        }
    }

    /** @return the value field of the parameter row {@code currency} the panel offers for the stylesheet */
    private TextField currencyField() throws Exception {
        TransformPanel panel = doc.findType(TransformPanel.class);
        doc.waitUntilFx(20, () -> findCurrencyField(panel) != null);
        return doc.fx(() -> findCurrencyField(panel));
    }

    private static TextField findCurrencyField(TransformPanel panel) {
        for (Node n : panel.lookupAll(".text-field")) {
            if (n.getParent() instanceof HBox row && row.getChildren().size() >= 3
                    && row.getChildren().get(0) instanceof TextField name && "currency".equals(name.getText())
                    && row.getChildren().get(2) instanceof TextField value) {
                return value;
            }
        }
        return null;
    }

    /** Scrolls the side panel so {@code node} is in view. */
    private void reveal(Node node) {
        doc.onFx(() -> {
            for (Parent p = node.getParent(); p != null; p = p.getParent()) {
                if (p instanceof ScrollPane scroll && scroll.getContent() != null) {
                    Bounds content = scroll.getContent().getLayoutBounds();
                    Bounds at = scroll.getContent().sceneToLocal(node.localToScene(node.getBoundsInLocal()));
                    double room = content.getHeight() - scroll.getViewportBounds().getHeight();
                    if (room > 0) {
                        scroll.setVvalue(Math.max(0, Math.min(1, (at.getMinY() - 40) / room)));
                    }
                    return;
                }
            }
        });
        doc.settle(300);
    }

    /** @return the output panel's view toggle with one of the given texts */
    private ToggleButton outputToggle(String... texts) {
        Node output = doc.findType(org.fxt.freexmltoolkit.controls.shell.editor.TransformOutputPanel.class);
        return doc.fx(() -> output.lookupAll(".toggle-button").stream()
                .filter(n -> n instanceof ToggleButton t && t.isVisible() && List.of(texts).contains(t.getText()))
                .map(ToggleButton.class::cast).findFirst().orElseThrow());
    }

    /** @return the first node with {@code selector} in the output panel */
    private Node outputPart(String selector) {
        Node output = doc.findType(org.fxt.freexmltoolkit.controls.shell.editor.TransformOutputPanel.class);
        return doc.fx(() -> output.lookup(selector));
    }

    private Bounds lowerEditorHalf() {
        Bounds editor = editorBounds();
        return new BoundingBox(editor.getMinX(), editor.getCenterY(), editor.getWidth(), editor.getHeight() / 2);
    }

    private Bounds editorBounds() {
        Node host = doc.find("#editorHost");
        return doc.fx(() -> host.localToScreen(host.getBoundsInLocal()));
    }

    private Bounds editorTop() {
        Bounds editor = editorBounds();
        return new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.7, editor.getHeight() * 0.45);
    }
}
