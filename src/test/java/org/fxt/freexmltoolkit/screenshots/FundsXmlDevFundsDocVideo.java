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

    private static final String ISIN = "ATXMPLF03016";
    /** The ISIN typed into the checker: the class on the screen before it. */
    private static final String CHECKED = "ATXMPLF03024";
    private static final String LEI = "XMPL00FNDMULTI000170";
    private static final String NAV = "<Amount ccy=\"EUR\">278791093.07</Amount>";
    private static final String USD_TNA = "<Amount ccy=\"USD\" isShareClassCcy=\"true\">61201893.49</Amount>";

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
        doc.open(folder.resolve(FundsXmlBizContentDocVideo.FILE), "<AssetMasterData>");
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        FundsXmlVideoKit kit = new FundsXmlVideoKit(doc, video);
        video.start();
        try {
            video.scene("fund", () -> {
                int fund = kit.line("<Fund>");
                kit.scrollTo(fund - 4);
                video.zoom(kit.lines(fund, kit.line("<SingleFundFlag>")));
                video.spoken(0.3);
                video.zoom(kit.textBounds("<LEI>" + LEI + "</LEI>"));
                video.spoken(0.7);
                video.zoom(kit.lines(kit.line("<Names>", fund), kit.line("<Currency>", fund)));
            });

            video.scene("flag", () -> {
                video.zoom(kit.textBounds("<SingleFundFlag>true</SingleFundFlag>"));
                video.spoken(1);
            });

            video.scene("static", () -> {
                int stat = kit.line("<FundStaticData>");
                video.zoom(kit.lines(stat, stat + 9));
                video.spoken(0.45);
                video.zoomOut();
                int costs = kit.line("<OngoingCosts>");
                kit.scrollTo(costs - 4);
                video.zoom(kit.lines(costs, costs + 6));
            });

            video.scene("nav", () -> {
                video.zoomOut();
                int dyn = kit.line("<FundDynamicData>");
                kit.scrollTo(dyn - 4);
                video.zoom(kit.lines(dyn, dyn + 10));
                video.spoken(0.35);
                video.zoom(kit.textBounds(NAV));
                video.spoken(0.75);
                video.zoom(kit.textBounds("ccy=\"EUR\">278791093.07"));
            });

            video.scene("shareclass", () -> {
                video.zoomOut();
                int first = kit.line("<ISIN>" + ISIN + "</ISIN>");
                kit.scrollTo(first - 4);
                video.zoom(kit.lines(first - 2, first + 11));
                video.spoken(0.22);
                video.zoom(kit.lines(first - 2, first + 4));
                video.spoken(0.38);
                video.zoom(kit.lines(first + 5, first + 10));
                video.spoken(0.52);
                video.zoomOut();
                int price = kit.line("<NavDate>2025-10-31</NavDate>", first);
                kit.scrollTo(price - 8);
                video.zoom(kit.lines(price - 2, price + 5));
                video.spoken(0.8);
                video.zoom(kit.textBounds("<NavPrice>128.41</NavPrice>", price));
            });

            video.scene("currencies", () -> {
                video.zoomOut();
                int line = kit.line(USD_TNA);
                kit.scrollTo(line - 10);
                video.zoom(kit.lines(line - 1, line + 3));
                video.spoken(0.3);
                video.zoom(kit.textBounds(USD_TNA));
                video.spoken(0.55);
                video.zoom(kit.textBounds("<Amount ccy=\"EUR\" isFundCcy=\"true\">52970307.68</Amount>"));
                video.spoken(0.72);
                video.zoom(kit.lines(line - 1, line + 3));
            });

            video.scene("second", () -> {
                video.zoomOut();
                int second = kit.line("<ISIN>" + CHECKED + "</ISIN>");
                kit.scrollTo(second - 4);
                video.zoom(kit.lines(second - 2, second + 10));
                video.spoken(0.3);
                video.zoomOut();
                int price = kit.line("<NavDate>2025-10-31</NavDate>", second);
                kit.scrollTo(price - 8);
                video.zoom(kit.textBounds("<NavPrice>1186.05</NavPrice>", price));
                video.spoken(0.55);
                video.zoomOut();
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
                video.typeIntoFocused(CHECKED);
                video.pause(600);
            });

            video.scene("typo", () -> {
                video.spoken(0.1);
                video.typeIntoFocused(CHECKED.substring(0, CHECKED.length() - 1) + "5");
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
