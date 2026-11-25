package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ExampleFiles;
import org.fxt.freexmltoolkit.controls.shell.editor.FopPanel;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "Create a PDF with XSL-FO" (storyboard {@code videos/publish-pdf.json},
 * after {@code docs/guides/publish-pdf.md}): an invoice from the example order.
 *
 * <pre>{@code
 * python3 scripts/make-video.py publish-pdf --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class PublishPdfDocVideo {

    /** A one-page invoice for order.xml in XSL-FO. */
    static final String INVOICE_FO = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!-- FreeXmlToolkit example: an invoice for order.xml as XSL-FO (PDF/FOP). -->
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:fo="http://www.w3.org/1999/XSL/Format" xmlns:xs="http://www.w3.org/2001/XMLSchema"
                            exclude-result-prefixes="xs">
                <xsl:template match="/order">
                    <fo:root>
                        <fo:layout-master-set>
                            <fo:simple-page-master master-name="A4" page-height="29.7cm" page-width="21cm" margin="2cm">
                                <fo:region-body/>
                            </fo:simple-page-master>
                        </fo:layout-master-set>
                        <fo:page-sequence master-reference="A4">
                            <fo:flow flow-name="xsl-region-body" font-family="Helvetica" font-size="11pt">
                                <fo:block font-size="20pt" font-weight="bold" space-after="6mm">Invoice <xsl:value-of select="@id"/></fo:block>
                                <fo:block space-after="2mm"><xsl:value-of select="customer/name"/></fo:block>
                                <fo:block space-after="8mm"><xsl:value-of select="customer/address/street"/>, <xsl:value-of select="customer/address/city"/></fo:block>
                                <fo:table width="100%" border-bottom="0.5pt solid #444">
                                    <fo:table-column column-width="60%"/>
                                    <fo:table-column column-width="10%"/>
                                    <fo:table-column column-width="15%"/>
                                    <fo:table-column column-width="15%"/>
                                    <fo:table-header font-weight="bold">
                                        <fo:table-row>
                                            <fo:table-cell><fo:block>Item</fo:block></fo:table-cell>
                                            <fo:table-cell><fo:block>Qty</fo:block></fo:table-cell>
                                            <fo:table-cell><fo:block>Price</fo:block></fo:table-cell>
                                            <fo:table-cell><fo:block>Total</fo:block></fo:table-cell>
                                        </fo:table-row>
                                    </fo:table-header>
                                    <fo:table-body>
                                        <xsl:for-each select="items/item">
                                            <fo:table-row>
                                                <fo:table-cell><fo:block><xsl:value-of select="."/></fo:block></fo:table-cell>
                                                <fo:table-cell><fo:block><xsl:value-of select="@quantity"/></fo:block></fo:table-cell>
                                                <fo:table-cell><fo:block><xsl:value-of select="@price"/></fo:block></fo:table-cell>
                                                <fo:table-cell><fo:block><xsl:value-of select="format-number(@quantity * @price, '0.00')"/></fo:block></fo:table-cell>
                                            </fo:table-row>
                                        </xsl:for-each>
                                    </fo:table-body>
                                </fo:table>
                                <fo:block space-before="6mm" font-weight="bold" text-align="end">Total: <xsl:value-of
                                        select="format-number(sum(items/item/(xs:decimal(@quantity) * xs:decimal(@price))), '0.00')"/> EUR</fo:block>
                            </fo:flow>
                        </fo:page-sequence>
                    </fo:root>
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
        Path folder = ExampleFiles.defaultTarget();
        ExampleFiles.install(folder);
        Path xml = folder.resolve("order.xml");
        Path xsl = folder.resolve("invoice-fo.xsl");
        Files.writeString(xsl, INVOICE_FO);
        doc.onFx(() -> host.openFile(xsl));
        doc.waitUntilFx(20, () -> host.getActiveText().orElse("").contains("fo:root"));
        doc.onFx(() -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PO-1042")).orElse(false));
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        VideoTyping keys = new VideoTyping(doc, video);
        video.start();
        try {
            video.scene("how", () -> video.spoken(0.95));

            video.scene("stylesheet", () -> {
                doc.onFx(() -> host.openFile(xsl));
                video.pause(800);
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), editor.getHeight() * 0.8));
                video.spoken(0.95);
                video.zoomOut();
                doc.onFx(() -> host.openFile(xml));
            });

            video.scene("panel", () -> {
                video.click(doc.<Node>find("#activity-pdf"));
                video.pause(1200);
                FopPanel panel = doc.findType(FopPanel.class);
                doc.onFx(() -> {
                    panel.setXmlOverride(xml.toFile());
                    panel.setXslFile(xsl.toFile());
                });
                video.pause(800);
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.moveTo(doc.<Node>find("#fop-xml-row"));
                video.pause(500);
                video.moveTo(doc.<Node>find("#fop-xsl-row"));
                video.spoken(0.5);
                Node title = doc.find("#fop-meta-title");
                video.click(title);
                video.typeIntoFocused(keys.say("Rechnung PO-1042", "Invoice PO-1042"));
                Node author = doc.find("#fop-meta-author");
                video.click(author);
                video.typeIntoFocused("Northwind Books");
                video.spoken(0.95);
            });

            video.scene("options", () -> {
                video.moveTo(doc.<Node>find("#fop-pdfa"));
                video.spoken(0.6);
                video.moveTo(doc.<Node>find("#fop-page-size"));
                video.spoken(0.95);
            });

            video.scene("generate", () -> {
                // the button asks for the target in the system's save dialog, which the recording cannot drive:
                // point at the button and generate into a file of the example folder
                video.moveTo(doc.<Node>find("#fop-generate"));
                video.pause(400);
                FopPanel fop = doc.findType(FopPanel.class);
                java.io.File pdf = xml.resolveSibling("invoice.pdf").toFile();
                doc.onFx(() -> fop.generateTo(pdf));
                video.pause(1500);
                try {
                    doc.waitUntilFx(90, () -> {
                        Node preview = doc.stage().getScene().getRoot().lookup("#fop-preview");
                        return preview != null && !preview.isDisabled();
                    });
                } catch (Exception e) {
                    System.out.println("[video] no PDF after 90 s");
                }
                video.spoken(0.4);
                video.click(doc.<Node>find("#fop-preview"));
                video.pause(3000);
                video.zoomOut();
                video.spoken(0.95);
            });

            video.scene("messages", () -> {
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.moveTo(doc.<Node>find("#fop-messages"));
                video.spoken(0.7);
                video.moveTo(doc.<Node>find("#fop-open"));
                video.spoken(0.95);
            });

            video.scene("show-fo", () -> {
                video.click(doc.<Node>find("#fop-show-fo"));
                video.pause(2500);
                video.zoomOut();
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), editor.getHeight() * 0.7));
                video.spoken(0.6);
                video.zoomOut();
                video.moveTo(doc.<Node>find("#fop-tool-debug"));
                video.spoken(0.95);
            });

            video.scene("processors", () -> {
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.moveTo(doc.<Node>find("#fop-processor"));
                video.spoken(0.5);
                video.moveTo(doc.<Node>find("#fop-manage-processors"));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("automation", () -> video.spoken(0.95));
        } finally {
            video.stop();
        }
    }
}
