package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The screen part of "Trust in the data: quality and control", film 4 of "FundsXML for business"
 * (storyboard {@code videos/fundsxml/biz-quality.json}); the rest of that film are slides. One run: the
 * data-quality report of a FundsXML delivery from the FundsXML panel.
 *
 * <pre>{@code
 * python3 scripts/make-video.py biz-quality --set fundsxml --lang en
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class FundsXmlBizQualityDocVideo {

    private final DocShell doc = new DocShell("video");

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        FundsXmlVideoFiles.enableExtension();
        I18n.initialize(null);
        doc.start(stage, DocVideoRecorder.WIDTH, DocVideoRecorder.HEIGHT);
    }

    @Test
    void record() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        Path folder = FundsXmlVideoFiles.folder("biz-quality");
        doc.open(folder.resolve(FundsXmlBizContentDocVideo.FILE), "<AssetMasterData>");
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            // ----- the data-quality report
            video.scene("demo-dq", () -> {
                video.zoomOut();
                video.spoken(0.2);
                video.click(doc.<Node>find("#activity-fundsxml"));
                doc.waitUntilFx(10, () -> doc.find("#fundsxml-dq") != null);
                video.pause(700);
                Bounds button = video.screenBounds(doc.find("#fundsxml-dq"));
                video.zoom(new BoundingBox(button.getMinX() - 20, button.getMinY() - 60, 520, 160));
                video.spoken(0.55);
                video.click(doc.<Node>find("#fundsxml-dq"));
                doc.waitUntilFx(60, () -> host.getActiveDocument()
                        .map(d -> d.getDisplayName().endsWith("-dq.html")).orElse(false));
                video.pause(1200);
                video.zoomOut();
            });

            video.scene("demo-report", () -> {
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.8, editor.getHeight() * 0.7));
                video.spoken(0.95);
                video.zoomOut();
            });
        } finally {
            video.stop();
        }
    }
}
