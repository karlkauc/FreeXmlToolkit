package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The screen part of "Portfolios and asset master data", film 4 of "FundsXML for developers"
 * (storyboard {@code videos/fundsxml/dev-portfolio.json}): a position, the kinds of instruments, the
 * link from a position to its asset, a currency forward in the master data, and a broken link found
 * by the schema.
 *
 * <pre>{@code
 * python3 scripts/make-video.py dev-portfolio --set fundsxml --lang en
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class FundsXmlDevPortfolioDocVideo {

    private static final String LINK = "<UniqueID>ID_001</UniqueID>";

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
        Path folder = FundsXmlVideoFiles.folder("dev-portfolio");
        doc.open(folder.resolve("Mixed-Fund_Positions.xml"), "<AssetMasterData>");
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        FundsXmlVideoKit kit = new FundsXmlVideoKit(doc, video);
        video.start();
        try {
            video.scene("portfolio", () -> {
                kit.scrollTo(kit.lineOf(kit.offset("<Portfolios>")));
                int line = kit.lineOf(kit.offset("<Portfolio>"));
                video.zoom(kit.lines(line, line + 3));
                video.spoken(0.4);
                video.zoom(kit.lines(line + 1, line + 2));
            });

            video.scene("position", () -> {
                int first = kit.lineOf(kit.offset(LINK));
                video.zoom(kit.lines(first - 1, first + 15));
                video.spoken(0.1);
                video.zoom(kit.lines(first, first + 3));
                video.spoken(0.3);
                video.zoom(kit.lines(first + 4, first + 8));
                video.spoken(0.6);
                video.zoom(kit.lines(first + 9, first + 15));
            });

            video.scene("kinds", () -> {
                video.zoomOut();
                int bond = kit.lineOf(kit.offset("<Bond>"));
                kit.scrollTo(bond - 12);
                video.zoom(kit.lines(bond - 9, bond + 5));
                video.spoken(0.4);
                video.zoomOut();
                kit.scrollTo(kit.lineOf(kit.offset("<ShareClass>")) - 10);
                video.spoken(0.5);
                kit.scrollTo(kit.lineOf(kit.offset("<Option>")) - 10);
                video.spoken(0.72);
                kit.scrollTo(kit.lineOf(kit.offset("<CallMoney/>")) - 20);
                video.spoken(1);
            });

            video.scene("link", () -> {
                kit.scrollTo(kit.lineOf(kit.offset(LINK)) - 2);
                video.zoom(kit.textBounds(LINK));
                video.spoken(0.18);
                video.zoomOut();
                int asset = kit.lineOf(kit.offset(LINK, kit.offset("<AssetMasterData>")));
                kit.scrollTo(asset - 1);
                video.zoom(kit.lines(asset - 1, asset + 18));
                video.spoken(0.75);
                video.zoom(kit.lines(asset - 1, asset + 1));
            });

            video.scene("bond", () -> {
                video.zoomOut();
                int pos = kit.lineOf(kit.offset("<UniqueID>ID_003</UniqueID>"));
                kit.scrollTo(pos - 2);
                video.zoom(kit.lines(pos - 1, pos + 16));
                video.spoken(0.1);
                video.zoom(kit.lines(pos + 8, pos + 13));
                video.spoken(0.38);
                video.zoomOut();
                int asset = kit.lineOf(kit.offset("<UniqueID>ID_003</UniqueID>", kit.offset("<AssetMasterData>")));
                kit.scrollTo(asset - 2);
                video.zoom(kit.lines(asset - 1, asset + 22));
                video.spoken(0.62);
                video.zoom(kit.lines(asset + 8, asset + 13));
                video.spoken(0.8);
                video.zoom(kit.lines(asset + 14, asset + 18));
                video.spoken(0.97);
            });

            video.scene("fundunit", () -> {
                video.zoomOut();
                int pos = kit.lineOf(kit.offset("<UniqueID>ID_005</UniqueID>"));
                kit.scrollTo(pos - 2);
                video.zoom(kit.lines(pos - 1, pos + 15));
                video.spoken(0.3);
                video.zoomOut();
                int asset = kit.lineOf(kit.offset("<UniqueID>ID_005</UniqueID>", kit.offset("<AssetMasterData>")));
                kit.scrollTo(asset - 2);
                video.zoom(kit.lines(asset - 1, asset + 18));
                video.spoken(0.97);
            });

            video.scene("fxforward", () -> {
                video.zoomOut();
                int asset = kit.lineOf(kit.offset("<UniqueID>ID_015</UniqueID>", kit.offset("<AssetMasterData>")));
                kit.scrollTo(asset - 2);
                video.zoom(kit.lines(asset - 1, asset + 18));
                video.spoken(0.2);
                video.zoom(kit.lines(asset + 5, asset + 10));
                video.spoken(0.5);
                video.zoom(kit.lines(asset + 11, asset + 16));
                video.spoken(0.97);
            });

            video.scene("break", () -> {
                video.zoomOut();
                kit.scrollTo(kit.lineOf(kit.offset(LINK)) - 2);
                int at = kit.offset(LINK) + "<UniqueID>ID_0".length();
                video.zoom(kit.lines(kit.lineOf(at) - 1, kit.lineOf(at) + 3));
                Bounds digits = kit.chars(at, at + 2);
                video.click(digits.getMaxX(), digits.getCenterY());
                video.spoken(0.3);
                video.retype(at, 2, "99");
                video.spoken(0.4);
                video.zoomOut();
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(60, kit::hasErrorsOrWarnings);
                video.pause(800);
                video.zoom(doc.<Node>find("#problems-panel-list"));
            });

            video.scene("repair", () -> {
                video.zoomOut();
                int at = kit.offset("<UniqueID>ID_099</UniqueID>") + "<UniqueID>ID_0".length();
                Bounds digits = kit.chars(at, at + 2);
                video.click(digits.getMaxX(), digits.getCenterY());
                video.retype(at, 2, "01");
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(60, () -> !kit.hasErrorsOrWarnings());
                video.pause(600);
            });
        } finally {
            video.stop();
        }
    }
}
