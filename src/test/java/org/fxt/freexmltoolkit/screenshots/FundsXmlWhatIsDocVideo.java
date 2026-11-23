package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;
import java.util.List;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The screen part of the FundsXML tutorial video "What is FundsXML?" (storyboard
 * {@code videos/fundsxml/fundsxml-what-is.json}): a FundsXML document folded to its three main
 * blocks, each block explained, the Properties panel with the schema's documentation, and the
 * schema's root element in the Graphic view with all modules of the standard.
 *
 * <pre>{@code
 * python3 scripts/make-video.py fundsxml-what-is --set fundsxml --lang en
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class FundsXmlWhatIsDocVideo {

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
        Path folder = FundsXmlVideoFiles.folder("fundsxml-what-is");
        doc.open(folder.resolve(EXAMPLE), "<AssetMasterData>");
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        FundsXmlVideoKit kit = new FundsXmlVideoKit(doc, video);
        video.start();
        try {
            video.scene("why", () -> {
                video.spoken(0.3);
                video.zoom(kit.lines(4, 16));
                video.spoken(0.75);
                video.zoomOut();
            });

            video.scene("standard", () -> {
                video.spoken(0.5);
                video.zoom(kit.textBounds("xsi:noNamespaceSchemaLocation=\"FundsXML4.xsd\""));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("root", () -> {
                video.spoken(0.3);
                video.zoom(kit.lines(2, 3));
                video.spoken(0.95);
            });

            video.scene("fold", () -> {
                video.zoomOut();
                video.spoken(0.3);
                // from the bottom up: a fold changes nothing above it
                int assets = kit.lineOf(kit.offset("<AssetMasterData>"));
                int funds = kit.lineOf(kit.offset("<Funds>"));
                kit.fold(assets);
                kit.fold(funds);
                kit.fold(4);
                video.moveTo(1300, 760); // away from the text: no hover tooltip
                video.pause(400);
                video.zoom(kit.lines(1, kit.lineOf(kit.offset("</FundsXML4>"))));
            });

            video.scene("control", () -> {
                kit.unfoldAll();
                video.zoom(kit.lines(4, 16));
                video.spoken(0.4);
                video.zoom(kit.textBounds("<ContentDate>2025-10-01</ContentDate>"));
                video.spoken(0.65);
                video.zoom(kit.lines(9, 15));
            });

            video.scene("funds", () -> {
                video.zoomOut();
                kit.scrollTo(17);
                video.zoom(kit.lines(17, 46));
                video.spoken(0.45);
                video.zoom(kit.lines(37, 46));
                video.spoken(0.75);
                video.zoomOut();
                kit.scrollTo(kit.lineOf(kit.offset("<SingleFund>")));
                video.zoom(kit.lines(kit.lineOf(kit.offset("<SingleFund>")), kit.lineOf(kit.offset("<SingleFund>")) + 17));
            });

            video.scene("assets", () -> {
                video.zoomOut();
                int line = kit.lineOf(kit.offset("<AssetMasterData>"));
                kit.scrollTo(line);
                video.zoom(kit.lines(line, line + 20));
                video.spoken(0.6);
                video.zoom(kit.lines(line + 1, line + 3));
            });

            video.scene("inspector", () -> {
                video.zoomOut();
                video.spoken(0.1);
                kit.clickInto("<AssetType>EQ</AssetType>", 0);
                video.pause(900);
                video.zoom(doc.<Node>find("#inspectorWrapper"));
                video.spoken(0.85);
                video.zoomOut();
            });

            video.scene("modules", () -> {
                doc.open(folder.resolve("FundsXML4.xsd"), "FundsXML4");
                doc.onFx(() -> host.setActiveViewMode(org.fxt.freexmltoolkit.controls.shell.editor.ViewMode.GRAPHIC));
                doc.waitUntilFx(120, () -> host.getActiveSchema().isPresent());
                video.pause(1500);
                var schema = host.getActiveSchema().orElseThrow();
                XsdNode root = namedElement(schema, "FundsXML4");
                XsdNode regulatory = namedElement(root, "RegulatoryReportings");
                doc.onFx(() -> host.revealPathInActiveGraph(List.of(root, regulatory)));
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

    /** The first element named {@code name} below {@code from} (depth first). */
    static XsdNode namedElement(XsdNode from, String name) {
        var stack = new java.util.ArrayDeque<XsdNode>();
        stack.push(from);
        while (!stack.isEmpty()) {
            XsdNode n = stack.pop();
            if (n instanceof XsdElement && name.equals(n.getName())) {
                return n;
            }
            List<XsdNode> children = n.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }
        throw new IllegalStateException("No element " + name);
    }
}
