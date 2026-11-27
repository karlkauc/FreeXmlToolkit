package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;

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

    private static final String LINK = "<UniqueID>ID_0002</UniqueID>";
    private static final String BOND = "<UniqueID>ID_0035</UniqueID>";
    private static final String FUND_UNIT = "<UniqueID>ID_0046</UniqueID>";
    private static final String FORWARD = "<UniqueID>ID_0069</UniqueID>";

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
        doc.open(folder.resolve(FundsXmlBizContentDocVideo.FILE), "<AssetMasterData>");
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        FundsXmlVideoKit kit = new FundsXmlVideoKit(doc, video);
        video.start();
        try {
            video.scene("portfolio", () -> {
                kit.scrollTo(kit.line("<Portfolios>"));
                int line = kit.line("<Portfolio>");
                video.zoom(kit.lines(line, line + 3));
                video.spoken(0.4);
                video.zoom(kit.lines(line + 1, line + 2));
            });

            video.scene("position", () -> {
                video.zoomOut();
                int first = kit.line(LINK);
                kit.scrollTo(first - 2);
                video.zoom(kit.lines(first - 1, kit.line("</Position>", first)));
                video.spoken(0.1);
                video.zoom(kit.lines(first, first + 3));
                video.spoken(0.28);
                video.zoom(kit.lines(kit.line("<Currency>", first), kit.line("<TotalPercentage>", first)));
                video.spoken(0.55);
                video.zoom(kit.lines(kit.line("<FXRates>", first), kit.line("</FXRates>", first)));
                video.spoken(0.68);
                video.zoom(kit.lines(kit.line("<Equity>", first), kit.line("</Equity>", first)));
            });

            video.scene("kinds", () -> {
                video.zoomOut();
                int bond = kit.line("<Bond>");
                kit.scrollTo(bond - 10);
                video.zoom(kit.lines(bond, kit.line("</Bond>", bond)));
                video.spoken(0.4);
                video.zoomOut();
                kit.scrollTo(kit.line("<ShareClass>") - 10);
                video.spoken(0.5);
                kit.scrollTo(kit.line("<Option>") - 10);
                video.spoken(0.72);
                kit.scrollTo(kit.line("<CallMoney>") - 14);
                video.spoken(1);
            });

            video.scene("link", () -> {
                kit.scrollTo(kit.line(LINK) - 2);
                video.zoom(kit.textBounds(LINK));
                video.spoken(0.18);
                video.zoomOut();
                int asset = kit.line(LINK, kit.line("<AssetMasterData>"));
                kit.scrollTo(asset - 1);
                video.zoom(kit.lines(asset - 1, kit.line("</Issuer>", asset)));
                video.spoken(0.75);
                video.zoom(kit.lines(asset - 1, asset + 1));
            });

            video.scene("bond", () -> {
                video.zoomOut();
                int pos = kit.line(BOND);
                kit.scrollTo(pos - 2);
                video.zoom(kit.lines(pos - 1, pos + 8));
                video.spoken(0.08);
                int block = kit.line("<Bond>", pos);
                video.zoom(kit.lines(block, kit.line("</InterestClaimGross>", block)));
                video.spoken(0.38);
                video.zoomOut();
                int asset = kit.line(BOND, kit.line("<AssetMasterData>"));
                kit.scrollTo(asset - 2);
                video.zoom(kit.lines(asset - 1, kit.line("</Coupon>", asset)));
                video.spoken(0.62);
                video.zoom(kit.lines(kit.line("<Issuer>", asset), kit.line("</Issuer>", asset)));
                video.spoken(0.8);
                video.zoom(kit.lines(kit.line("<MaturityDate>", asset), kit.line("</Coupon>", asset)));
                video.spoken(0.97);
            });

            video.scene("fundunit", () -> {
                video.zoomOut();
                int pos = kit.line(FUND_UNIT);
                kit.scrollTo(pos - 2);
                video.zoom(kit.lines(kit.line("<ShareClass>", pos) - 1, kit.line("</ShareClass>", pos)));
                video.spoken(0.3);
                video.zoomOut();
                int asset = kit.line(FUND_UNIT, kit.line("<AssetMasterData>"));
                kit.scrollTo(asset - 2);
                video.zoom(kit.lines(asset - 1, kit.line("<ExchangeTradedFundFlag>", asset)));
                video.spoken(0.97);
            });

            video.scene("fxforward", () -> {
                video.zoomOut();
                int asset = kit.line(FORWARD, kit.line("<AssetMasterData>"));
                kit.scrollTo(asset - 2);
                video.zoom(kit.lines(asset - 1, kit.line("</Counterparty>", asset)));
                video.spoken(0.2);
                video.zoom(kit.lines(kit.line("<CurrencyBuy>", asset), kit.line("<MaturityDate>", asset)));
                video.spoken(0.5);
                video.zoom(kit.lines(kit.line("<Counterparty>", asset), kit.line("</Counterparty>", asset)));
                video.spoken(0.97);
            });

            video.scene("break", () -> {
                video.zoomOut();
                kit.scrollTo(kit.line(LINK) - 2);
                int at = kit.offset(LINK) + "<UniqueID>ID_".length();
                video.zoom(kit.lines(kit.lineOf(at) - 1, kit.lineOf(at) + 3));
                Bounds digit = kit.chars(at, at + 1);
                video.click(digit.getMaxX(), digit.getCenterY());
                video.spoken(0.3);
                video.retype(at, 1, "9");
                video.spoken(0.4);
                video.zoomOut();
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(60, kit::hasErrorsOrWarnings);
                video.pause(800);
                video.zoom(doc.<Node>find("#problems-panel-list"));
            });

            video.scene("repair", () -> {
                video.zoomOut();
                int at = kit.offset("<UniqueID>ID_9002</UniqueID>") + "<UniqueID>ID_".length();
                Bounds digit = kit.chars(at, at + 1);
                video.click(digit.getMaxX(), digit.getCenterY());
                video.retype(at, 1, "0");
                video.key(KeyCode.F8, "F8");
                doc.waitUntilFx(60, () -> !kit.hasErrorsOrWarnings());
                video.pause(600);
            });
        } finally {
            video.stop();
        }
    }
}
