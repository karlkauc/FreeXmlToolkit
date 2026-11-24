package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;

import javafx.scene.Node;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The screen part of "Funds, share classes and NAV", film 3 of "FundsXML for developers" (storyboard
 * {@code videos/fundsxml/dev-funds.json}): the fund, its static and dynamic data, the share classes
 * with an amount in two currencies, and a share class ISIN and the fund's LEI in the identifier
 * checker of the FundsXML panel.
 *
 * <pre>{@code
 * python3 scripts/make-video.py dev-funds --set fundsxml --lang en
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class FundsXmlDevFundsDocVideo {

    private static final String ISIN = "AT0000A2QM74";
    private static final String LEI = "529900T8BM49AURSDO55";

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
        Path folder = FundsXmlVideoFiles.folder("dev-funds");
        doc.open(folder.resolve("Mixed-Fund_Positions.xml"), "<AssetMasterData>");
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        FundsXmlVideoKit kit = new FundsXmlVideoKit(doc, video);
        video.start();
        try {
            video.scene("fund", () -> {
                kit.scrollTo(12);
                video.zoom(kit.lines(17, 26));
                video.spoken(0.3);
                video.zoom(kit.textBounds("<LEI>529900T8BM49AURSDO55</LEI>"));
                video.spoken(0.7);
                video.zoom(kit.lines(22, 25));
            });

            video.scene("flag", () -> {
                video.zoom(kit.textBounds("<SingleFundFlag>true</SingleFundFlag>"));
                video.spoken(1);
            });

            video.scene("static", () -> {
                video.zoom(kit.lines(27, 36));
                video.spoken(0.5);
                video.zoom(kit.lines(30, 34));
            });

            video.scene("nav", () -> {
                kit.scrollTo(20);
                video.zoom(kit.lines(37, 46));
                video.spoken(0.35);
                video.zoom(kit.textBounds("<Amount ccy=\"EUR\">112500000</Amount>"));
                video.spoken(0.75);
                video.zoom(kit.textBounds("ccy=\"EUR\">112500000"));
            });

            video.scene("shareclass", () -> {
                video.zoomOut();
                int first = kit.lineOf(kit.offset("<SingleFund>"));
                kit.scrollTo(first);
                video.zoom(kit.lines(first, first + 16));
                video.spoken(0.25);
                video.zoom(kit.lines(first + 3, first + 9));
                video.spoken(0.5);
                video.zoom(kit.lines(first + 10, first + 18));
                video.spoken(0.8);
                video.zoom(kit.lines(first + 19, first + 28));
            });

            video.scene("currencies", () -> {
                int line = kit.lineOf(kit.offset("<Amount ccy=\"EUR\">78937500</Amount>"));
                video.zoom(kit.lines(line - 1, line + 2));
                video.spoken(0.5);
                video.zoom(kit.textBounds("<Amount ccy=\"USD\">85722000</Amount>"));
            });

            video.scene("second", () -> {
                video.zoomOut();
                int second = kit.lineOf(kit.offset("<ISIN>AT0000A2QM66</ISIN>"));
                kit.scrollTo(second - 3);
                video.zoom(kit.lines(second - 2, second + 24));
                video.spoken(0.25);
                video.zoom(kit.lines(second - 2, second + 4));
                video.spoken(0.4);
                video.zoom(kit.textBounds("<NavPrice>134.25</NavPrice>"));
                video.spoken(0.6);
                int first = kit.lineOf(kit.offset("<ISIN>AT0000A2QM74</ISIN>"));
                kit.scrollTo(first - 3);
                video.zoom(kit.lines(first - 2, second + 6)); // both classes: what fits on the screen
                video.spoken(0.97);
            });

            video.scene("checker", () -> {
                video.zoomOut();
                video.spoken(0.12);
                video.click(doc.<Node>find("#activity-fundsxml"));
                doc.waitUntilFx(10, () -> doc.find("#industry-identifier") != null);
                video.pause(800);
                Node field = doc.find("#industry-identifier");
                video.zoom(checkerRegion(video, field));
                video.click(field);
                video.spoken(0.35);
                video.typeIntoFocused(ISIN);
                video.pause(600);
            });

            video.scene("typo", () -> {
                video.spoken(0.1);
                video.typeIntoFocused(ISIN.substring(0, ISIN.length() - 1) + "5");
                video.pause(600);
                video.spoken(0.9);
            });

            video.scene("lei", () -> {
                // the checker's kind: from ISIN to LEI
                @SuppressWarnings("unchecked")
                javafx.scene.control.ComboBox<org.fxt.freexmltoolkit.plugins.IdentifierKind> kind = doc.fx(() ->
                        doc.stage().getScene().getRoot().lookupAll(".combo-box").stream()
                                .filter(n -> n instanceof javafx.scene.control.ComboBox<?> c && n.isVisible()
                                        && c.getItems().contains(org.fxt.freexmltoolkit.plugins.IdentifierKind.LEI))
                                .map(n -> (javafx.scene.control.ComboBox<org.fxt.freexmltoolkit.plugins.IdentifierKind>) n)
                                .findFirst().orElseThrow());
                video.click(kind);
                video.pause(700);
                doc.onFx(() -> {
                    kind.setValue(org.fxt.freexmltoolkit.plugins.IdentifierKind.LEI);
                    kind.hide();
                });
                video.pause(500);
                video.click(doc.<Node>find("#industry-identifier"));
                video.spoken(0.1);
                video.typeIntoFocused(LEI);
                video.pause(600);
                video.spoken(0.55);
                video.zoomOut();
                video.moveTo(doc.<Node>find("#fundsxml-identifiers-online"));
                video.spoken(0.97);
            });
        } finally {
            video.stop();
        }
    }

    /** The identifier checker: its field, the result below it and some air. */
    private javafx.geometry.Bounds checkerRegion(DocVideoRecorder video, Node field) {
        var f = video.screenBounds(field);
        Node result = doc.find("#industry-identifier-result");
        var r = result == null ? f : video.screenBounds(result);
        return new javafx.geometry.BoundingBox(f.getMinX() - 30, f.getMinY() - 90, Math.max(f.getWidth(), 300) + 60,
                Math.max(r.getMaxY(), f.getMaxY() + 80) - f.getMinY() + 130);
    }
}
