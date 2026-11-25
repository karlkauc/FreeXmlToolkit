package org.fxt.freexmltoolkit.screenshots;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.TransformPanel;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The screen part of the tutorial video "Find slow stylesheets and queries" (storyboard
 * {@code videos/profiler.json}, after {@code docs/guides/transform-query/profile.md}).
 *
 * <pre>{@code
 * python3 scripts/make-video.py profiler --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class ProfilerDocVideo {

    /** A stylesheet with a lookup per item that is slow on purpose (the whole document is searched each time). */
    private static final String SLOW_XSLT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:f="urn:example:functions"
                            exclude-result-prefixes="xs f">
                <xsl:output method="html" indent="yes"/>

                <xsl:function name="f:line-total" as="xs:decimal">
                    <xsl:param name="item" as="element(item)"/>
                    <xsl:sequence select="xs:decimal($item/@quantity) * xs:decimal($item/@price)"/>
                </xsl:function>

                <xsl:function name="f:same-price-count" as="xs:integer">
                    <xsl:param name="item" as="element(item)"/>
                    <xsl:sequence select="count(root($item)//item[@price = $item/@price])"/>
                </xsl:function>

                <xsl:template match="/order">
                    <html><body>
                        <table>
                            <xsl:apply-templates select="items/item"/>
                        </table>
                    </body></html>
                </xsl:template>

                <xsl:template match="item">
                    <tr>
                        <td><xsl:value-of select="@sku"/></td>
                        <td><xsl:value-of select="f:line-total(.)"/></td>
                        <td><xsl:value-of select="f:same-price-count(.)"/></td>
                    </tr>
                </xsl:template>
            </xsl:stylesheet>
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
        Path dir = DocFiles.work("profile");
        Path xml = dir.resolve("orders-20000.xml");
        Path xslt = dir.resolve("order-report.xslt");
        Files.writeString(xslt, SLOW_XSLT);
        try (BufferedWriter w = Files.newBufferedWriter(xml, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<order id=\"PO-9002\" date=\"2026-03-14\">\n  <items>\n");
            for (int i = 1; i <= 20_000; i++) {
                w.write("    <item sku=\"BK-" + i + "\" quantity=\"" + (1 + i % 5) + "\" price=\"" + (10 + i % 40) + ".90\">Book " + i + "</item>\n");
            }
            w.write("  </items>\n</order>\n");
        }
        doc.open(xml, "PO-9002");
        doc.activity(Activity.TRANSFORM);
        doc.settle(800);
        TransformPanel panel = doc.findType(TransformPanel.class);
        doc.onFx(() -> {
            panel.setRunAutomatically(false);
            panel.setXsltFile(xslt.toFile());
            panel.setInputOverride(xml.toFile());
        });
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("baseline", () -> {
                video.click(doc.<Node>find("#transform-run"));
                video.pause(1500);
                waitForOutput();
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getCenterY(), editor.getWidth(), editor.getHeight() / 2));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("run", () -> {
                Node profile = doc.find("#transform-profile");
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.click(profile);
                video.spoken(0.4);
                video.click(doc.<Node>find("#transform-run"));
                try {
                    doc.waitUntilFx(120, () -> doc.stage().getScene().getRoot().lookup("#profile-summary") != null);
                } catch (Exception e) {
                    System.out.println("[video] no profile tab after 120 s");
                }
                video.pause(1500);
                video.zoomOut();
                video.spoken(0.95);
            });

            video.scene("hotspots", () -> {
                tab("#profile-tab-hotspots");
                zoomEditor(video);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("tree", () -> {
                tab("#profile-tab-tree");
                zoomEditor(video);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("flame", () -> {
                tab("#profile-tab-flame");
                video.pause(1000);
                Node flame = doc.find("#profile-flame-graph");
                if (flame != null) {
                    Bounds b = video.screenBounds(flame);
                    video.spoken(0.4);
                    video.click(b.getMinX() + b.getWidth() * 0.3, b.getMinY() + 60);
                    video.pause(2000);
                    Node reset = doc.find("#profile-flame-reset");
                    if (reset != null) {
                        video.click(reset);
                    }
                }
                video.spoken(0.95);
            });

            video.scene("lines", () -> {
                tab("#profile-tab-lines");
                zoomEditor(video);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("fix", () -> {
                video.moveTo(doc.<Node>find("#profile-again"));
                video.spoken(0.95);
            });

            video.scene("cli", () -> {
                Node export = doc.find("#profile-export");
                if (export != null) {
                    video.click(export);
                    video.pause(1500);
                    doc.onFx(() -> javafx.stage.Window.getWindows().stream()
                            .filter(w -> w instanceof javafx.stage.PopupWindow && w.isShowing()).toList()
                            .forEach(javafx.stage.Window::hide));
                }
                video.spoken(0.95);
            });
        } finally {
            video.stop();
        }
    }

    private void waitForOutput() {
        try {
            doc.waitUntilFx(120, () -> doc.stage().getScene().getRoot().lookupAll(".fxt-output-status").stream()
                    .anyMatch(n -> n instanceof Labeled l && l.getText() != null && l.getText().contains("ms")));
        } catch (Exception e) {
            System.out.println("[video] no output status");
        }
    }

    private void zoomEditor(DocVideoRecorder video) {
        Bounds editor = video.screenBounds(doc.find("#editorHost"));
        video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), editor.getHeight() * 0.75));
    }

    private void tab(String id) {
        Node tabs = doc.find("#profile-tabs");
        if (tabs instanceof javafx.scene.control.TabPane tp) {
            doc.onFx(() -> tp.getTabs().stream().filter(t -> id.substring(1).equals(t.getId())).findFirst()
                    .ifPresent(t -> tp.getSelectionModel().select(t)));
        }
        doc.settle(800);
    }
}
