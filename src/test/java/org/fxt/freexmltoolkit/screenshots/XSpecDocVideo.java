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
 * The screen part of the tutorial video "Test stylesheets with XSpec" (storyboard {@code videos/xspec.json},
 * after {@code docs/guides/transform-query/xspec.md}, whose example it uses plus one failing test).
 *
 * <pre>{@code
 * python3 scripts/make-video.py xspec --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class XSpecDocVideo {

    private static final String PRICE_XSL = """
            <xsl:stylesheet version="3.0"
                            xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:xs="http://www.w3.org/2001/XMLSchema"
                            xmlns:f="urn:example:price"
                            exclude-result-prefixes="xs f">

              <!-- Net price of a gross price that includes 20 % tax -->
              <xsl:function name="f:net" as="xs:decimal">
                <xsl:param name="gross" as="xs:decimal"/>
                <xsl:sequence select="round($gross div 1.2, 2)"/>
              </xsl:function>

              <xsl:template match="item">
                <line net="{f:net(@price)}">
                  <xsl:value-of select="upper-case(@name)"/>
                </line>
              </xsl:template>

            </xsl:stylesheet>
            """;

    private static final String PRICE_XSPEC = """
            <x:description xmlns:x="http://www.jenitennison.com/xslt/xspec"
                           xmlns:f="urn:example:price"
                           stylesheet="price.xsl">

              <x:scenario label="f:net with a gross price of 120">
                <x:call function="f:net">
                  <x:param select="120"/>
                </x:call>
                <x:expect label="returns 100" select="100.0"/>
              </x:scenario>

              <x:scenario label="an item">
                <x:context>
                  <item name="pen" price="2.40"/>
                </x:context>
                <x:expect label="becomes a line with the net price and the name in upper case">
                  <line net="2">PEN</line>
                </x:expect>
              </x:scenario>

              <x:scenario label="f:net with a gross price of 121.20">
                <x:call function="f:net">
                  <x:param select="121.20"/>
                </x:call>
                <x:expect label="returns 101" select="102.0"/>
              </x:scenario>

            </x:description>
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
        Path dir = DocFiles.work("xspec");
        Path xsl = dir.resolve("price.xsl");
        Path xspec = dir.resolve("price.xspec");
        Files.writeString(xsl, PRICE_XSL);
        Files.writeString(xspec, PRICE_XSPEC);
        doc.open(xsl, "f:net");
        doc.open(xspec, "x:description");
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        video.start();
        try {
            video.scene("why", () -> video.spoken(0.95));

            video.scene("stylesheet", () -> {
                doc.open(xsl, "f:net");
                zoomEditor(video);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("tests", () -> {
                doc.open(xspec, "x:description");
                zoomEditor(video);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("new", () -> {
                video.moveTo(doc.<Node>find("#action-new"));
                video.spoken(0.95);
            });

            video.scene("run", () -> {
                video.click(doc.<Node>find("#menu-tools"));
                video.pause(600);
                video.click(video.inPopup("#menu-tools-xspec"));
                try {
                    doc.waitUntilFx(90, () -> {
                        Node summary = doc.stage().getScene().getRoot().lookup("#xspec-summary");
                        return summary instanceof javafx.scene.control.Labeled l && l.getText() != null
                                && !l.getText().isBlank();
                    });
                } catch (Exception e) {
                    System.out.println("[video] no XSpec result after 90 s");
                }
                video.pause(1000);
                Node summary = doc.find("#xspec-summary");
                if (summary != null) {
                    video.zoom(summary);
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("result", () -> {
                zoomEditor(video);
                Node expected = doc.find("#xspec-expected");
                if (expected != null) {
                    video.moveTo(expected);
                    video.pause(800);
                    video.moveTo(doc.<Node>find("#xspec-actual"));
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("fix", () -> {
                doc.open(xspec, "x:description");
                String text = host.getActiveText().orElseThrow();
                int at = text.indexOf("select=\"102.0\"") + "select=\"".length();
                if (at > 8) {
                    video.retype(at, 5, "101.0");
                }
                video.pause(600);
                Node tab = doc.fx(() -> host.lookupAll(".tab").stream()
                        .filter(t -> t.lookupAll(".tab-label").stream().anyMatch(l -> l instanceof javafx.scene.control.Labeled lb
                                && lb.getText() != null && lb.getText().startsWith("XSpec")))
                        .findFirst().orElse(null));
                if (tab != null) {
                    video.click(tab);
                    video.pause(800);
                }
                Node again = doc.find("#xspec-run-again");
                if (again != null) {
                    video.click(again);
                    video.pause(6000);
                }
                video.spoken(0.6);
                video.moveTo(doc.<Node>find("#xspec-html"));
                video.pause(500);
                video.moveTo(doc.<Node>find("#xspec-junit"));
                video.spoken(0.95);
            });

            video.scene("more", () -> video.spoken(0.95));
        } finally {
            video.stop();
        }
    }

    private void zoomEditor(DocVideoRecorder video) {
        Bounds editor = video.screenBounds(doc.find("#editorHost"));
        video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.8, editor.getHeight() * 0.8));
    }
}
