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
 * a dividend in Earnings and the portfolio's trades of the realistic global equity example.
 *
 * <pre>{@code
 * python3 scripts/make-video.py dev-transactions --set fundsxml --lang en
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class FundsXmlDevTransactionsDocVideo {

    static final String FILE = "Global-Equity-Fund_2025-10.xml";
    private static final String USD_CLASS = "ATXMPLF01036";
    private static final String USD_CLASS_RED = "RH-FLW-003";
    private static final String DIVIDEND = "ERN-2025-10-0003";
    private static final String BUY = "TRX-2025-10-0008";
    private static final String SELL = "TRX-2025-10-0001";

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
        doc.open(folder.resolve(FILE), "<Flows from=");
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        FundsXmlVideoKit kit = new FundsXmlVideoKit(doc, video);
        video.start();
        try {
            video.scene("flows", () -> {
                // the flows of the dollar class (the last share class)
                int at = kit.line("<Flows from=", kit.line("<ISIN>" + USD_CLASS + "</ISIN>"));
                kit.scrollTo(Math.max(1, at - 2));
                video.zoom(kit.lines(at, at + 12));
                video.spoken(0.12);
                video.zoom(kit.lines(at, at));
                video.spoken(0.3);
                video.zoom(kit.lines(at + 2, at + 7));
                video.spoken(0.55);
                video.zoom(kit.lines(at + 7, at + 11));
                video.spoken(0.8);
                video.zoom(kit.lines(at + 1, at + 12));
            });

            video.scene("red", () -> {
                video.zoomOut();
                int at = show(kit, "<TransactionID>" + USD_CLASS_RED + "</TransactionID>");
                video.zoom(kit.lines(at - 2, at + 9));
                video.spoken(0.4);
                video.zoom(kit.lines(at + 4, at + 5));
            });

            video.scene("distribution", () -> {
                video.zoomOut();
                int at = show(kit, "<Distributions>");
                video.zoom(kit.lines(at, at + 20));
                video.spoken(0.2);
                video.zoom(kit.lines(at + 3, at + 3));
                video.spoken(0.35);
                video.zoom(kit.lines(at + 4, at + 8));
                video.spoken(0.62);
                video.zoom(kit.lines(at + 9, at + 18));
            });

            video.scene("earnings", () -> {
                video.zoomOut();
                int at = show(kit, "<Earnings from=");
                video.zoom(kit.lines(at, at + 3));
                video.spoken(0.15);
                video.zoomOut();
                int e = show(kit, "<EarningID>" + DIVIDEND + "</EarningID>");
                video.zoom(kit.lines(e, e + 6));
                video.spoken(0.42);
                video.zoom(kit.lines(kit.line("<EntryValue>", e), kit.line("</FXRates>", e)));
                video.spoken(0.6);
                int dividend = kit.line("<Dividend>", e);
                kit.scrollTo(dividend - 8);
                video.zoom(kit.lines(dividend, kit.line("</Dividend>", e)));
            });

            video.scene("trades", () -> {
                video.zoomOut();
                int at = show(kit, "<Transactions from=");
                video.zoom(kit.lines(at, at + 3));
                video.spoken(0.1);
                video.zoomOut();
                int buy = show(kit, "<TransactionID>" + BUY + "</TransactionID>");
                video.zoom(kit.lines(buy - 1, buy + 12));
                video.spoken(0.32);
                video.zoom(kit.lines(kit.line("<AgreedPrice>", buy), kit.line("</Expenses>", buy)));
                video.spoken(0.55);
                video.zoom(kit.lines(kit.line("<Counterparty>", buy), kit.line("<Exchange>", buy)));
                video.spoken(0.72);
                video.zoomOut();
                int sell = show(kit, "<TransactionID>" + SELL + "</TransactionID>");
                video.zoom(kit.lines(sell - 1, sell + 12));
                video.spoken(0.97);
                video.zoomOut();
            });
        } finally {
            video.stop();
        }
    }

    /** Scrolls so that {@code snippet}'s line is near the top and returns that line. */
    private static int show(FundsXmlVideoKit kit, String snippet) {
        int line = kit.line(snippet);
        kit.scrollTo(Math.max(1, line - 2));
        return line;
    }
}
