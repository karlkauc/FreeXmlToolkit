package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;
import java.util.List;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The screen part of "Anatomy of a delivery: structure and ControlData", film 2 of "FundsXML for
 * developers" (storyboard {@code videos/fundsxml/dev-anatomy.json}): a FundsXML document folded to
 * its main blocks, ControlData field by field, the schema's documentation of DataOperation in the
 * Properties panel, and the schema's root element in the Graphic view.
 *
 * <pre>{@code
 * python3 scripts/make-video.py dev-anatomy --set fundsxml --lang en
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class FundsXmlDevAnatomyDocVideo {

    private static final String EXAMPLE = "Mixed-Fund_Positions.xml";

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
        Path folder = FundsXmlVideoFiles.folder("dev-anatomy");
        doc.open(folder.resolve(EXAMPLE), "<AssetMasterData>");
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        FundsXmlVideoKit kit = new FundsXmlVideoKit(doc, video);
        video.start();
        try {
            // ----- the main blocks
            video.scene("root", () -> {
                video.spoken(0.2);
                video.zoom(kit.lines(1, 3));
                video.spoken(0.55);
                video.zoom(kit.textBounds("<FundsXML4"));
                video.spoken(0.8);
                video.zoom(kit.textBounds("xsi:noNamespaceSchemaLocation=\"FundsXML4.xsd\""));
            });

            video.scene("fold", () -> {
                video.zoomOut();
                // from the bottom up: a fold changes nothing above it
                kit.fold(kit.lineOf(kit.offset("<AssetMasterData>")));
                kit.fold(kit.lineOf(kit.offset("<Funds>")));
                kit.fold(4);
                video.moveTo(1300, 760); // away from the text: no hover tooltip
                video.pause(400);
                video.zoom(kit.lines(1, kit.lineOf(kit.offset("</FundsXML4>"))));
                video.spoken(0.3);
                video.zoom(kit.lines(4, 4));
                video.spoken(0.5);
                video.zoom(kit.lines(kit.lineOf(kit.offset("<Funds>")), kit.lineOf(kit.offset("<Funds>"))));
                video.spoken(0.75);
                video.zoom(kit.lines(kit.lineOf(kit.offset("<AssetMasterData>")), kit.lineOf(kit.offset("<AssetMasterData>"))));
            });

            // ----- ControlData
            video.scene("control", () -> {
                video.zoomOut();
                kit.unfoldAll();
                kit.scrollTo(1);
                video.zoom(kit.lines(4, 15));
                video.spoken(0.12);
                video.zoom(kit.lines(5, 5));
                video.spoken(0.32);
                video.zoom(kit.lines(6, 6));
                video.spoken(0.45);
                video.zoom(kit.lines(7, 7));
                video.spoken(0.58);
                video.zoom(kit.lines(8, 8));
                video.spoken(0.75);
                video.zoom(kit.lines(5, 8));
            });

            video.scene("supplier", () -> {
                video.zoom(kit.lines(9, 14));
            });

            video.scene("operation", () -> {
                video.zoom(kit.lines(13, 16));
                video.spoken(0.3);
                video.zoomOut();
                kit.clickInto("<DataOperation>INITIAL</DataOperation>", 0);
                video.pause(900);
                video.zoom(doc.<Node>find("#inspectorWrapper"));
                video.spoken(0.97);
                video.zoomOut();
            });

            // ----- a correction
            video.scene("amend", () -> {
                kit.scrollTo(1);
                video.zoom(kit.lines(4, 19));
                video.spoken(0.12);
                int id = kit.offset("FUNDSXML_FILE_1") + "FUNDSXML_FILE_".length();
                Bounds idChar = kit.chars(id, id + 1);
                video.click(idChar.getMaxX(), idChar.getCenterY());
                video.retype(id, 1, "2");
                video.spoken(0.3);
                int op = kit.offset("INITIAL</DataOperation>");
                Bounds opChars = kit.chars(op, op + 7);
                video.click(opChars.getMaxX(), opChars.getCenterY());
                video.retype(op, "INITIAL".length(), "AMEND");
                video.spoken(0.48);
                int after = kit.offset("</DataOperation>") + "</DataOperation>".length();
                String related = "\n      <RelatedDocumentIDs>\n         <RelatedDocumentID>FUNDSXML_FILE_1</RelatedDocumentID>\n      </RelatedDocumentIDs>";
                doc.onFx(() -> host.replaceActiveTextRegion(after, after, related));
                video.pause(800);
                video.spoken(0.66);
                video.zoomOut();
                video.key(javafx.scene.input.KeyCode.F8, "F8");
                video.pause(4000);
                doc.waitUntilFx(60, () -> !kit.hasErrorsOrWarnings());
                video.zoom(kit.lines(4, 19));
                video.spoken(0.97);
                video.zoomOut();
            });

            // ----- the whole schema
            video.scene("modules", () -> {
                doc.open(folder.resolve("FundsXML4.xsd"), "FundsXML4");
                doc.onFx(() -> host.setActiveViewMode(org.fxt.freexmltoolkit.controls.shell.editor.ViewMode.GRAPHIC));
                doc.waitUntilFx(120, () -> host.getActiveSchema().isPresent());
                video.pause(1500);
                var schema = host.getActiveSchema().orElseThrow();
                // the cards along the instance path: the diagram opens RegulatoryReportings and IndirectReporting
                List<XsdNode> path = org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator.locateElement(schema,
                        List.of("FundsXML4", "RegulatoryReportings", "IndirectReporting", "TripartiteTemplateSolvencyII_V8"))
                        .orElseThrow().particlePath();
                doc.onFx(() -> host.revealPathInActiveGraph(path));
                video.pause(1500);
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY() + 40, editor.getWidth() * 0.75,
                        editor.getHeight() * 0.8));
                video.spoken(0.95);
                video.zoomOut();
            });
        } finally {
            video.stop();
        }
    }
}
