package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The screen part of "Regulatory templates in XML", film 6 of "FundsXML for developers" (storyboard
 * {@code videos/fundsxml/dev-regulatory.json}): the RegulatoryReportings block of
 * Mixed-Fund_Regulatory.xml with an EMT V4.3 entry and a TPT V8 portfolio.
 *
 * <pre>{@code
 * python3 scripts/make-video.py dev-regulatory --set fundsxml --lang en
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class FundsXmlDevRegulatoryDocVideo {

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
        Path folder = FundsXmlVideoFiles.folder("dev-regulatory");
        doc.open(folder.resolve("Mixed-Fund_Regulatory.xml"), "<RegulatoryReportings>");
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        FundsXmlVideoKit kit = new FundsXmlVideoKit(doc, video);
        video.start();
        try {
            video.scene("block", () -> {
                int at = show(kit, "<RegulatoryReportings>");
                video.zoom(kit.lines(at - 2, at + 4));
                video.spoken(0.5);
                video.zoom(kit.lines(at + 2, at + 2));
            });

            video.scene("emt-info", () -> {
                video.zoomOut();
                int at = show(kit, "<DataSetInformation>");
                video.zoom(kit.lines(at - 1, at + 20));
                video.spoken(0.2);
                video.zoom(kit.lines(at, at + 7));
                video.spoken(0.55);
                video.zoom(kit.lines(at + 8, at + 19));
            });

            video.scene("emt-target", () -> {
                video.zoomOut();
                int at = show(kit, "<TargetMarket>");
                video.zoom(kit.lines(at, at + 12));
                video.spoken(0.35);
                int risk = kit.lineOf(kit.offset("<RiskTolerance>"));
                video.zoom(kit.lines(risk - 5, risk + 2));
                video.spoken(0.6);
                int goals = kit.lineOf(kit.offset("<ClientObjectives>"));
                kit.scrollTo(goals - 10);
                video.zoom(kit.lines(goals, goals + 10));
            });

            video.scene("emt-costs", () -> {
                video.zoomOut();
                int at = show(kit, "<CostsAndChargesExAnte>");
                video.zoom(kit.lines(at, at + 8));
                video.spoken(0.7);
                video.zoom(kit.textBounds("<OngoingCosts>0.0180</OngoingCosts>"));
            });

            video.scene("tpt", () -> {
                video.zoomOut();
                int at = show(kit, "<TripartiteTemplateSolvencyII_V8>");
                video.zoom(kit.lines(at, at + 18));
                video.spoken(0.3);
                video.zoom(kit.lines(at + 2, at + 12));
            });

            video.scene("tpt-position", () -> {
                video.zoomOut();
                int at = show(kit, "<CICCodeOfTheInstrument>US31</CICCodeOfTheInstrument>");
                video.zoom(kit.lines(at - 1, at + 26));
                video.spoken(0.1);
                video.zoom(kit.textBounds("<CICCodeOfTheInstrument>US31</CICCodeOfTheInstrument>"));
                video.spoken(0.45);
                video.zoom(kit.lines(at + 2, at + 6));
                video.spoken(0.6);
                video.zoom(kit.lines(at + 6, at + 18));
                video.spoken(0.85);
                video.zoom(kit.lines(at + 18, at + 24));
                video.spoken(0.97);
                video.zoomOut();
            });

            video.scene("tpt-bond", () -> {
                int at = show(kit, "<CICCodeOfTheInstrument>DE11</CICCodeOfTheInstrument>");
                video.zoom(kit.lines(at - 1, at + 28));
                video.spoken(0.12);
                video.zoom(kit.textBounds("<CICCodeOfTheInstrument>DE11</CICCodeOfTheInstrument>"));
                video.spoken(0.35);
                int rate = kit.lineOf(kit.offset("<InterestRateInstrumentCharacteristics>"));
                video.zoom(kit.lines(rate, rate + 9));
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
