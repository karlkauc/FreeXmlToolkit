package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.MenuButton;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The screen part of "What a FundsXML delivery contains", film 2 of "FundsXML for business"
 * (storyboard {@code videos/fundsxml/biz-content.json}); the rest of that film are slides. One run: a
 * real FundsXML file, its main parts folded, a named value, and the FundsXML query "Top 10 positions"
 * as a table.
 *
 * <pre>{@code
 * python3 scripts/make-video.py biz-content --set fundsxml --lang en
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class FundsXmlBizContentDocVideo {

    private static final String NAV = "<Amount ccy=\"EUR\">278791093.07</Amount>";
    static final String FILE = "Multi-Asset-Derivatives-Fund_2025-10.xml";

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
        UnifiedShellView shell = doc.boot();
        Path folder = FundsXmlVideoFiles.folder("biz-content");
        doc.open(folder.resolve(FILE), "<AssetMasterData>");
        // the extension registers its queries at the application's start, which the test shell skips; the
        // film shows the analysis queries only (the "DQ:" checks would push them off the screen)
        var repository = org.fxt.freexmltoolkit.service.XPathSnippetRepository.getInstance();
        org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQuality.registerSnippets(repository);
        for (var snippet : repository.getAllSnippets()) {
            if (snippet.getName() != null && snippet.getName().startsWith("DQ:")) {
                repository.deleteSnippet(snippet.getId());
            }
        }
        doc.settle(1500);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        FundsXmlVideoKit kit = new FundsXmlVideoKit(doc, video);
        video.start();
        try {
            // ----- a real file
            video.scene("demo-open", () -> {
                video.spoken(0.25);
                video.zoom(kit.lines(1, kit.line("</ControlData>")));
                video.spoken(0.7);
                video.zoom(kit.lines(kit.line("<Funds>"), kit.line("<FundStaticData>")));
            });

            video.scene("demo-parts", () -> {
                video.zoomOut();
                // from the bottom up: a fold changes nothing above it
                kit.fold(kit.line("<RegulatoryReportings>"));
                kit.fold(kit.line("<AssetMasterData>"));
                kit.fold(kit.line("<Funds>"));
                kit.fold(kit.line("<ControlData>"));
                video.moveTo(1300, 760); // away from the text: no hover tooltip
                video.pause(400);
                video.zoom(kit.lines(1, kit.lineOf(kit.offset("</FundsXML4>"))));
                video.spoken(0.3);
                video.zoom(kit.lines(kit.line("<ControlData>"), kit.line("<ControlData>")));
                video.spoken(0.45);
                video.zoom(kit.lines(kit.line("<Funds>"), kit.line("<Funds>")));
                video.spoken(0.65);
                video.zoom(kit.lines(kit.line("<AssetMasterData>"), kit.line("<AssetMasterData>")));
                video.spoken(0.82);
                video.zoom(kit.lines(kit.line("<RegulatoryReportings>"), kit.line("<RegulatoryReportings>")));
            });

            video.scene("demo-read", () -> {
                video.zoomOut();
                kit.unfoldAll();
                int nav = kit.lineOf(kit.offset(NAV));
                kit.scrollTo(nav - 12);
                video.zoom(kit.lines(nav - 6, nav + 3));
                video.spoken(0.3);
                video.zoom(kit.textBounds(NAV));
                video.spoken(0.95);
            });

            video.scene("demo-query", () -> {
                video.zoomOut();
                video.spoken(0.12);
                video.showKey("Ctrl+K");
                doc.onFx(shell::toggleQueryConsole);
                video.pause(1200);
                MenuButton snippets = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".menu-button").stream()
                        .filter(n -> n instanceof MenuButton m && I18n.t("QueryConsole.snippetsMenu.text").equals(m.getText())
                                && n.isVisible() && n.getScene() != null)
                        .map(MenuButton.class::cast).findFirst().orElseThrow());
                Bounds menu = video.screenBounds(snippets);
                video.zoom(new BoundingBox(menu.getMinX() - 300, menu.getMinY() - 20, 900, 520));
                video.spoken(0.4);
                video.click(snippets);
                video.pause(700);
                Node item = video.menuItem("XQuery: FundsXML: Top 10 positions");
                video.moveTo(item);
                video.pause(900);
                Bounds row = video.screenBounds(item);
                Node load = submenuItem("Load into console", row);
                Bounds l = video.screenBounds(load);
                video.moveTo(l.getMinX() + 10, row.getCenterY()); // along the row into the submenu
                load = submenuItem("Load into console", row);
                video.click(load); // at once: the submenu closes when the pointer rests outside it
                video.spoken(0.75);
                Node run = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".button").stream()
                        .filter(n -> n instanceof javafx.scene.control.Button b
                                && b.getText() != null && b.getText().startsWith(I18n.t("QueryConsole.runButton.text"))
                                && n.isVisible() && n.getScene() != null)
                        .findFirst().orElseThrow());
                video.click(run);
                doc.waitUntilFx(30, () -> {
                    var results = doc.stage().getScene().getRoot().lookup(".fxt-query-results");
                    return results instanceof org.fxmisc.richtext.CodeArea a && a.getText().contains("ID_0");
                });
                video.pause(500);
            });

            video.scene("demo-table", () -> {
                video.zoomOut();
                video.click(doc.<Node>find("#query-show-table"));
                video.pause(1500);
                Node table = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".table-view").stream()
                        .filter(n -> n.isVisible() && n.getScene() != null && n.localToScreen(n.getBoundsInLocal()) != null)
                        .findFirst().orElse(null));
                if (table != null) {
                    Bounds t = video.screenBounds(table);
                    video.zoom(new BoundingBox(t.getMinX(), t.getMinY() - 30, Math.min(t.getWidth(), 1000), t.getHeight() + 30));
                }
                video.spoken(0.95);
                video.zoomOut();
                doc.onFx(shell::toggleQueryConsole);
            });
        } finally {
            video.stop();
        }
    }

    /** The showing menu item labelled {@code text} nearest to the height of {@code row} (the open submenu's). */
    private Node submenuItem(String text, Bounds row) throws Exception {
        Node[] found = new Node[1];
        doc.waitUntilFx(5, () -> {
            double best = Double.MAX_VALUE;
            for (javafx.stage.Window window : javafx.stage.Window.getWindows()) {
                if (!(window instanceof javafx.stage.PopupWindow) || !window.isShowing() || window.getScene() == null) {
                    continue;
                }
                for (Node item : window.getScene().getRoot().lookupAll(".menu-item")) {
                    Bounds b = item.localToScreen(item.getBoundsInLocal());
                    boolean match = item.lookupAll(".label").stream()
                            .anyMatch(l -> l instanceof javafx.scene.control.Label label && text.equals(label.getText()));
                    if (match && b != null && item.isVisible() && b.getMinX() > row.getMinX()
                            && Math.abs(b.getCenterY() - row.getCenterY()) < best) {
                        best = Math.abs(b.getCenterY() - row.getCenterY());
                        found[0] = item;
                    }
                }
            }
            return found[0] != null;
        });
        return found[0];
    }
}
