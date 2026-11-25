package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Labeled;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ExampleFiles;
import org.fxt.freexmltoolkit.controls.shell.editor.TransformPanel;
import org.fxt.freexmltoolkit.controls.shell.editor.debug.XsltDebugPane;
import org.fxt.freexmltoolkit.debugger.ui.BreakpointGutterFactory;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "Run and debug an XSLT stylesheet" (storyboard
 * {@code videos/xslt-debug.json}, after {@code docs/guides/transform-query/run-xslt.md} and
 * {@code debug.md}): run the example stylesheet from the Transform panel, set a breakpoint in its
 * gutter, debug, continue to the next item, step, mute and run to the end.
 *
 * <pre>{@code
 * python3 scripts/make-video.py xslt-debug --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class XsltDebugDocVideo {

    private final DocShell doc = new DocShell("video");

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        org.fxt.freexmltoolkit.i18n.I18n.initialize(null);
        doc.start(stage, DocVideoRecorder.WIDTH, DocVideoRecorder.HEIGHT);
    }

    /** The stylesheet the film debugs: a function, a variable, a message per item and a template that never runs. */
    private static final String DEBUG_XSLT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!-- FreeXmlToolkit example for the debugger: a table of the order's items. -->
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:f="urn:example:functions"
                            exclude-result-prefixes="xs f">
                <xsl:output method="html" indent="yes"/>
                <xsl:param name="currency" as="xs:string" select="'EUR'"/>

                <xsl:function name="f:line-total" as="xs:decimal">
                    <xsl:param name="item" as="element(item)"/>
                    <xsl:sequence select="xs:decimal($item/@quantity) * xs:decimal($item/@price)"/>
                </xsl:function>

                <xsl:template match="/order">
                    <html>
                        <body>
                            <h1>Order <xsl:value-of select="@id"/></h1>
                            <table>
                                <xsl:for-each select="items/item">
                                    <xsl:variable name="total" select="f:line-total(.)"/>
                                    <xsl:message select="'item ' || @sku || ': ' || $total"/>
                                    <tr>
                                        <td><xsl:value-of select="."/></td>
                                        <td><xsl:value-of select="@quantity"/></td>
                                        <td><xsl:value-of select="format-number($total, '0.00') || ' ' || $currency"/></td>
                                    </tr>
                                </xsl:for-each>
                            </table>
                        </body>
                    </html>
                </xsl:template>

                <xsl:template match="gift-wrap">
                    <p>Gift wrapped</p>
                </xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void record() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        // before the film starts: the example order is open, the Transform panel has it and the debug stylesheet
        Path folder = ExampleFiles.defaultTarget();
        ExampleFiles.install(folder);
        Path xml = folder.resolve("order.xml");
        Path xslt = folder.resolve("order-debug.xslt");
        java.nio.file.Files.writeString(xslt, DEBUG_XSLT);
        doc.onFx(() -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PO-1042")).orElse(false));
        doc.activity(Activity.TRANSFORM);
        doc.settle(800);
        TransformPanel panel = doc.findType(TransformPanel.class);
        doc.onFx(() -> {
            panel.setRunAutomatically(false);
            panel.setXsltFile(xslt.toFile());
            panel.setInputOverride(xml.toFile());
        });
        doc.onFx(() -> host.openFile(xslt));
        doc.waitUntilFx(20, () -> host.getActiveText().orElse("").contains("f:line-total"));
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        VideoTyping keys = new VideoTyping(doc, video);
        video.start();
        try {
            video.scene("why", () -> {
                video.spoken(0.3);
                zoomLines(video, host, "<xsl:function", 5);
                video.spoken(0.6);
                zoomLines(video, host, "<xsl:for-each", 9);
                video.spoken(0.9);
                video.zoomOut();
            });

            video.scene("breakpoint", () -> {
                zoomLines(video, host, "<xsl:for-each", 6);
                video.spoken(0.3);
                video.click(slot(host, "<xsl:variable name=\"total\""));
                video.pause(800);
            });

            video.scene("debug", () -> {
                video.zoomOut();
                video.spoken(0.15);
                video.click(doc.<Node>find("#transform-debug"));
                doc.waitUntilFx(30, () -> paused());
                video.pause(800);
                Bounds pane = video.screenBounds(doc.findType(XsltDebugPane.class));
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), pane.getMaxY() - editor.getMinY()));
            });

            video.scene("pane", () -> {
                video.zoomOut();
                Bounds pane = video.screenBounds(doc.findType(XsltDebugPane.class));
                video.zoom(pane);
                video.spoken(0.1);
                for (String tab : new String[] {"#debug-tab-variables", "#debug-tab-watch", "#debug-tab-call-stack",
                        "#debug-tab-messages", "#debug-tab-breakpoints", "#debug-tab-output"}) {
                    Node t = doc.find(tab);
                    try {
                        video.moveTo(t);
                        video.pause(350);
                    } catch (RuntimeException notShown) {
                        // a tab of the other half may be scrolled away: skip it
                    }
                }
                video.spoken(0.6);
                video.zoomOut();
                video.click(doc.<Node>find("#debug-show-input"));
                video.pause(2000);
                video.spoken(0.95);
                doc.onFx(() -> host.openFile(xslt));
                video.pause(500);
            });

            video.scene("continue", () -> {
                clickTab("#debug-tab-variables");
                video.spoken(0.1);
                step(video, "#debug-continue");
                video.pause(600);
                video.zoom(video.screenBounds(doc.findType(XsltDebugPane.class)));
                video.spoken(0.9);
                video.zoomOut();
            });

            video.scene("step-over", () -> {
                zoomLines(video, host, "<xsl:for-each", 8);
                video.spoken(0.15);
                step(video, "#debug-step-over");
                video.pause(1200);
                step(video, "#debug-step-over");
                video.pause(800);
            });

            video.scene("step-into", () -> {
                video.zoomOut();
                step(video, "#debug-continue");
                video.spoken(0.3);
                step(video, "#debug-step-into");
                video.pause(800);
                zoomLines(video, host, "<xsl:function", 5);
                video.spoken(0.7);
                video.zoomOut();
                clickTab("#debug-tab-call-stack");
                video.zoom(video.screenBounds(doc.findType(XsltDebugPane.class)));
            });

            video.scene("step-out", () -> {
                video.spoken(0.1);
                step(video, "#debug-step-out");
                video.pause(800);
                video.spoken(0.5);
                for (String b : new String[] {"#debug-pause", "#debug-restart", "#debug-stop"}) {
                    Node n = doc.find(b);
                    if (n != null) {
                        video.moveTo(n);
                        video.pause(900);
                    }
                }
                video.zoomOut();
            });

            video.scene("variables", () -> {
                clickTab("#debug-tab-variables");
                video.zoom(video.screenBounds(doc.findType(XsltDebugPane.class)));
                video.spoken(0.6);
                video.zoomOut();
                zoomLines(video, host, "<xsl:for-each", 8);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("call-stack", () -> {
                clickTab("#debug-tab-call-stack");
                video.zoom(video.screenBounds(doc.findType(XsltDebugPane.class)));
                video.spoken(0.4);
                Node list = doc.fx(() -> doc.findType(XsltDebugPane.class).lookupAll(".list-cell, .table-row-cell, .tree-table-row-cell").stream()
                        .filter(n -> n.isVisible() && n.localToScreen(n.getBoundsInLocal()) != null
                                && n instanceof javafx.scene.control.IndexedCell<?> c && c.getIndex() == 1 && !c.isEmpty())
                        .findFirst().orElse(null));
                if (list != null) {
                    video.click(list);
                    video.pause(1200);
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("evaluate", () -> {
                video.spoken(0.05);
                video.click(doc.<Node>find("#debug-evaluate"));
                video.pause(1500);
                javafx.stage.Window window = keys.dialog();
                if (window != null) {
                    video.zoom(doc.fx(() -> new BoundingBox(window.getX(), window.getY(), window.getWidth(), window.getHeight())));
                    Node field = doc.fx(() -> window.getScene().getRoot().lookupAll(".text-field").stream()
                            .filter(Node::isVisible).findFirst().orElse(null));
                    if (field != null) {
                        video.click(field);
                        video.typeIntoFocused("@price * 2");
                        video.key(KeyCode.ENTER, "Enter");
                    }
                    video.spoken(0.85);
                    doc.onFx(window::hide);
                }
                video.zoomOut();
            });

            video.scene("messages", () -> {
                clickTab("#debug-tab-messages");
                video.zoom(video.screenBounds(doc.findType(XsltDebugPane.class)));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("conditional", () -> {
                zoomLines(video, host, "<xsl:for-each", 6);
                Node bp = slot(host, "<xsl:variable name=\"total\"");
                Bounds b = video.screenBounds(bp);
                video.rightClick(b.getCenterX(), b.getCenterY());
                video.pause(600);
                video.click(video.menuItem(I18n.t("BreakpointGutter.properties")));
                video.pause(1200);
                javafx.stage.Window dialog = keys.dialog();
                if (dialog != null) {
                    video.zoom(doc.fx(() -> new BoundingBox(dialog.getX(), dialog.getY(), dialog.getWidth(), dialog.getHeight())));
                    Node field = doc.fx(() -> dialog.getScene().getRoot().lookupAll(".text-field").stream()
                            .filter(Node::isVisible).findFirst().orElse(null));
                    video.click(field);
                    video.typeIntoFocused("@quantity > 2");
                    video.spoken(0.4);
                    clickDefault(video, dialog);
                }
                video.zoomOut();
                video.click(doc.<Node>find("#debug-restart"));
                doc.waitUntilFx(30, () -> paused());
                video.pause(800);
                clickTab("#debug-tab-variables");
                video.zoom(video.screenBounds(doc.findType(XsltDebugPane.class)));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("logpoint", () -> {
                zoomLines(video, host, "<xsl:for-each", 6);
                Node sl = slot(host, "<tr>");
                Bounds b = video.screenBounds(sl);
                video.rightClick(b.getCenterX(), b.getCenterY());
                video.pause(600);
                video.click(video.menuItem(I18n.t("BreakpointGutter.add-logpoint")));
                video.pause(1200);
                javafx.stage.Window dialog = keys.dialog();
                if (dialog != null) {
                    video.zoom(doc.fx(() -> new BoundingBox(dialog.getX(), dialog.getY(), dialog.getWidth(), dialog.getHeight())));
                    java.util.List<Node> fields = doc.fx(() -> dialog.getScene().getRoot().lookupAll(".text-field").stream()
                            .filter(Node::isVisible).toList());
                    if (fields.size() >= 2) {
                        video.click(fields.get(1));
                        video.typeIntoFocused(keys.say("Artikel {@sku}", "item {@sku}"));
                    }
                    video.spoken(0.6);
                    clickDefault(video, dialog);
                }
                video.pause(800);
                video.zoomOut();
            });

            video.scene("breakpoints-tab", () -> {
                clickTab("#debug-tab-breakpoints");
                video.zoom(video.screenBounds(doc.findType(XsltDebugPane.class)));
                video.spoken(0.7);
                video.moveTo(doc.<Node>find("#debug-mute"));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("coverage", () -> {
                video.click(doc.<Node>find("#debug-coverage"));
                video.pause(300);
                video.click(doc.<Node>find("#debug-mute"));
                video.pause(300);
                video.click(doc.<Node>find("#debug-restart"));
                Labeled status = doc.find("#debug-status");
                doc.waitUntilFx(60, () -> status.getText() != null && status.getText().contains(" ms"));
                video.pause(1000);
                doc.onFx(() -> host.openFile(xslt));
                video.pause(800);
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth() * 0.7, editor.getHeight() * 0.75));
                video.spoken(0.6);
                zoomLines(video, host, "<xsl:template match=\"gift-wrap\">", 3);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("output", () -> {
                clickTab("#debug-tab-output");
                video.zoom(video.screenBounds(doc.findType(XsltDebugPane.class)));
                video.spoken(0.6);
                video.zoomOut();
                Node outputDock = doc.find("#bottom-dock-output");
                if (outputDock != null) {
                    video.click(outputDock);
                    video.pause(1500);
                }
                video.spoken(0.95);
            });
        } finally {
            video.stop();
        }
    }

    /** Clicks a debugger button and waits for the next pause; a run that does not pause is reported, not fatal. */
    private void step(DocVideoRecorder video, String id) {
        WaitForAsyncUtils.sleep(300, TimeUnit.MILLISECONDS);
        video.click(doc.<Node>find(id));
        try {
            doc.waitUntilFx(30, () -> paused());
        } catch (Exception e) {
            Labeled status = doc.find("#debug-status");
            System.out.println("[video] no pause after " + id + ": " + doc.fx(status::getText));
        }
    }

    private void clickTab(String id) {
        Node tab = doc.find(id);
        if (tab != null) {
            doc.onFx(() -> {
                for (Node n = tab; n != null; n = n.getParent()) {
                    if (n instanceof javafx.scene.control.TabPane tp) {
                        tp.getTabs().stream().filter(t -> id.substring(1).equals(t.getId())).findFirst()
                                .ifPresent(t -> tp.getSelectionModel().select(t));
                        return;
                    }
                }
            });
            WaitForAsyncUtils.sleep(400, TimeUnit.MILLISECONDS);
        }
    }

    private void clickDefault(DocVideoRecorder video, javafx.stage.Window dialog) {
        Node ok = doc.fx(() -> dialog.getScene().getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof javafx.scene.control.Button b && b.isDefaultButton() && !b.isDisabled())
                .findFirst().orElse(null));
        if (ok != null) {
            video.click(ok);
        } else {
            doc.onFx(dialog::hide);
        }
        video.pause(600);
    }

    /** The breakpoint slot in the gutter next to the first line containing {@code snippet}. */
    private Node slot(EditorHost host, String snippet) {
        String text = host.getActiveText().orElseThrow();
        int at = text.indexOf(snippet);
        CodeArea area = activeArea(host);
        Bounds line = doc.fx(() -> area.getCharacterBoundsOnScreen(at, at + 1).orElseThrow());
        return doc.fx(() -> area.lookupAll("." + BreakpointGutterFactory.SLOT_STYLE_CLASS).stream()
                .filter(n -> {
                    Bounds b = n.localToScreen(n.getBoundsInLocal());
                    return b != null && b.getMinY() <= line.getCenterY() && b.getMaxY() >= line.getCenterY();
                }).findFirst().orElseThrow());
    }

    private void zoomLines(DocVideoRecorder video, EditorHost host, String snippet, int lines) {
        String text = host.getActiveText().orElse("");
        int at = text.indexOf(snippet);
        if (at < 0) {
            return;
        }
        CodeArea area = activeArea(host);
        Bounds line = doc.fx(() -> area.getCharacterBoundsOnScreen(at, at + 1).orElse(null));
        if (line != null) {
            video.zoom(new BoundingBox(line.getMinX() - 380, line.getMinY() - 40, 1200, line.getHeight() * (lines + 2)));
        }
    }

    /** @return whether the debugger is paused (Continue is enabled), read on the FX thread */
    private boolean paused() {
        Node node = doc.stage().getScene().getRoot().lookup("#debug-continue");
        return node instanceof ButtonBase button && !button.isDisabled() && button.isVisible();
    }

    /** @return the code area of the active tab */
    private CodeArea activeArea(EditorHost host) {
        return doc.fx(() -> host.lookupAll(".code-area").stream()
                .filter(n -> n instanceof CodeArea && n.localToScreen(n.getBoundsInLocal()) != null && n.isVisible()
                        && treeVisible(n) && ((CodeArea) n).getText().contains("xsl:stylesheet"))
                .map(CodeArea.class::cast).findFirst().orElseThrow());
    }

    private static boolean treeVisible(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return true;
    }
}
