package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Files;
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
 * The screen part of the tutorial video "Run XProc pipelines" (storyboard {@code videos/xproc.json},
 * after {@code docs/guides/transform-query/xproc.md}, whose first example it uses).
 *
 * <pre>{@code
 * python3 scripts/make-video.py xproc --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class XProcDocVideo {

    private static final String ORDERS = """
            <orders>
              <order id="1" status="open"><total>120.00</total></order>
              <order id="2" status="shipped"><total>80.50</total></order>
              <order id="3" status="open"><total>15.00</total></order>
            </orders>
            """;

    private static final String PIPELINE = """
            <p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">

              <p:input port="source"/>
              <p:output port="result" serialization="map{'indent': true()}"/>

              <!-- 1. Drop every order that is not open -->
              <p:delete match="order[@status != 'open']"/>

              <!-- 2. Summarise what is left with an inline stylesheet -->
              <p:xslt>
                <p:with-input port="stylesheet">
                  <p:inline expand-text="false">
                    <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
                      <xsl:template match="/orders">
                        <open-orders count="{count(order)}" total="{sum(order/total)}">
                          <xsl:copy-of select="order"/>
                        </open-orders>
                      </xsl:template>
                    </xsl:stylesheet>
                  </p:inline>
                </p:with-input>
              </p:xslt>

              <!-- 3. Stamp the root element -->
              <p:add-attribute match="/*" attribute-name="source" attribute-value="orders.xml"/>

            </p:declare-step>
            """;

    private final DocShell doc = new DocShell("video");

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        I18n.initialize(null);
        doc.start(stage, DocVideoRecorder.WIDTH, DocVideoRecorder.HEIGHT);
    }

    @Test
    void record() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        Path dir = DocFiles.work("xproc");
        Path orders = dir.resolve("orders.xml");
        Path xpl = dir.resolve("open-orders.xpl");
        Files.writeString(orders, ORDERS);
        Files.writeString(xpl, PIPELINE);
        doc.open(orders, "<orders>");
        doc.open(xpl, "p:declare-step");
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("why", () -> video.spoken(0.95));

            video.scene("input", () -> {
                doc.open(orders, "<orders>");
                zoomEditor(video);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("pipeline", () -> {
                doc.open(xpl, "p:declare-step");
                zoomEditor(video);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("run", () -> {
                Node run = doc.find("#doc-action-run");
                Bounds b = video.screenBounds(run);
                video.click(b.getMaxX() - 8, b.getCenterY());
                video.pause(800);
                Node item = video.inPopup("#doc-action-run-pipeline");
                video.spoken(0.4);
                video.click(item);
                video.pause(4000);
                video.spoken(0.95);
            });

            video.scene("result", () -> {
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getCenterY(), editor.getWidth(), editor.getHeight() / 2));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("examples", () -> {
                Path example = Path.of("release/examples/xproc/07-schematron-report.xpl").toAbsolutePath();
                if (Files.exists(example)) {
                    doc.open(example, "p:");
                    zoomEditor(video);
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("limits", () -> video.spoken(0.95));
        } finally {
            video.stop();
        }
    }

    private void zoomEditor(DocVideoRecorder video) {
        Bounds editor = video.screenBounds(doc.find("#editorHost"));
        video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.8, editor.getHeight() * 0.8));
    }
}
