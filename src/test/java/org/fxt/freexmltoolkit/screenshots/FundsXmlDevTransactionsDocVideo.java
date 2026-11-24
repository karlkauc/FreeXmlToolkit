package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The screen part of "Transactions and investor flows", film 5 of "FundsXML for developers"
 * (storyboard {@code videos/fundsxml/dev-transactions.json}): flows of a share class, a distribution,
 * a dividend in Earnings and the portfolio's trades in Mixed-Fund_Transactions.xml.
 *
 * <pre>{@code
 * python3 scripts/make-video.py dev-transactions --set fundsxml --lang en
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class FundsXmlDevTransactionsDocVideo {

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
        Path folder = FundsXmlVideoFiles.folder("dev-transactions");
        doc.open(folder.resolve("Mixed-Fund_Transactions.xml"), "<Flows from=");
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        FundsXmlVideoKit kit = new FundsXmlVideoKit(doc, video);
        video.start();
        try {
            video.scene("flows", () -> {
                int at = show(kit, "<Flows from=");
                video.zoom(kit.lines(at, at + 15));
                video.spoken(0.12);
                video.zoom(kit.lines(at, at));
                video.spoken(0.3);
                video.zoom(kit.lines(at + 1, at + 7));
                video.spoken(0.55);
                video.zoom(kit.lines(at + 7, at + 11));
                video.spoken(0.8);
                video.zoom(kit.lines(at + 13, at + 14));
            });

            video.scene("red", () -> {
                video.zoomOut();
                int at = show(kit, "<TransactionID>FLOW_2025_0002</TransactionID>");
                video.zoom(kit.lines(at - 2, at + 13));
                video.spoken(0.4);
                video.zoom(kit.lines(at + 4, at + 5));
            });

            video.scene("distribution", () -> {
                video.zoomOut();
                int at = show(kit, "<Distributions>");
                video.zoom(kit.lines(at, at + 24));
                video.spoken(0.2);
                video.zoom(kit.lines(at + 2, at + 3));
                video.spoken(0.35);
                video.zoom(kit.lines(at + 4, at + 8));
                video.spoken(0.62);
                video.zoom(kit.lines(at + 9, at + 24));
            });

            video.scene("earnings", () -> {
                video.zoomOut();
                int at = show(kit, "<Earnings from=");
                video.zoom(kit.lines(at, at + 18));
                video.spoken(0.2);
                video.zoom(kit.lines(at + 1, at + 7));
                video.spoken(0.42);
                video.zoom(kit.lines(at + 11, at + 13));
                video.spoken(0.6);
                int dividend = kit.lineOf(kit.offset("<Dividend>", kit.offset("<Earnings from=")));
                kit.scrollTo(dividend - 8);
                video.zoom(kit.lines(dividend, dividend + 17));
            });

            video.scene("trades", () -> {
                video.zoomOut();
                int at = show(kit, "<Transactions from=");
                video.zoom(kit.lines(at, at + 26));
                video.spoken(0.3);
                video.zoom(kit.lines(at + 8, at + 12));
                video.spoken(0.5);
                video.zoom(kit.lines(at + 14, at + 25));
                video.spoken(0.75);
                int sell = kit.lineOf(kit.offset("<TransactionID>TXN_2025_0002</TransactionID>"));
                kit.scrollTo(sell - 6);
                video.zoom(kit.lines(sell - 1, sell + 20));
                video.spoken(0.97);
                video.zoomOut();
            });
        } finally {
            video.stop();
        }
    }

    /** Scrolls so that {@code snippet}'s line is near the top and returns that line. */
    private static int show(FundsXmlVideoKit kit, String snippet) {
        int line = kit.lineOf(kit.offset(snippet));
        kit.scrollTo(Math.max(1, line - 2));
        return line;
    }
}
