package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.MenuButton;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The screen part of the FundsXML tutorial video "Portfolio positions and asset master data"
 * (storyboard {@code videos/fundsxml/fundsxml-portfolio.json}): a position, the kinds of instruments,
 * the link from a position to its asset, a broken link found by the schema, and the FundsXML snippet
 * "Top 10 positions" in the Query Console.
 *
 * <pre>{@code
 * python3 scripts/make-video.py fundsxml-portfolio --set fundsxml --lang en
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class FundsXmlPortfolioDocVideo {

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
        UnifiedShellView shell = doc.boot();
        Path folder = FundsXmlVideoFiles.folder("fundsxml-portfolio");
        doc.open(folder.resolve("Mixed-Fund_Positions.xml"), "<AssetMasterData>");
        // the extension registers its queries at the application's start, which the test shell skips; the
        // film shows the analysis queries only (the 55 "DQ:" checks would push them off the screen)
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
                kit.scrollTo(kit.lineOf(kit.offset("<Option>")) - 10);
                video.spoken(0.7);
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

            video.scene("query", () -> {
                video.spoken(0.18);
                video.showKey("Ctrl+K");
                doc.onFx(shell::toggleQueryConsole);
                video.pause(1200);
                MenuButton snippets = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".menu-button").stream()
                        .filter(n -> n instanceof MenuButton m && I18n.t("QueryConsole.snippetsMenu.text").equals(m.getText())
                                && n.isVisible() && n.getScene() != null)
                        .map(MenuButton.class::cast).findFirst().orElseThrow());
                Bounds menu = video.screenBounds(snippets);
                video.zoom(new BoundingBox(menu.getMinX() - 300, menu.getMinY() - 20, 900, 520));
                video.spoken(0.45);
                video.click(snippets);
                video.pause(700);
                Node item = video.menuItem("XQuery: FundsXML: Top 10 positions");
                video.spoken(0.62);
                video.moveTo(item);
                video.pause(900);
                Bounds row = video.screenBounds(item);
                Node load = submenuItem("Load into console", row);
                Bounds l = video.screenBounds(load);
                video.moveTo(l.getMinX() + 10, row.getCenterY()); // along the row into the submenu
                load = submenuItem("Load into console", row);
                video.click(load); // at once: the submenu closes when the pointer rests outside it
                video.spoken(0.82);
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

            video.scene("results", () -> {
                video.zoomOut();
                video.spoken(0.08);
                video.click(doc.<Node>find("#query-show-table"));
                video.pause(1500);
                Node table = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".table-view").stream()
                        .filter(n -> n.isVisible() && n.getScene() != null && n.localToScreen(n.getBoundsInLocal()) != null)
                        .findFirst().orElse(null));
                if (table != null) {
                    Bounds t = video.screenBounds(table);
                    video.zoom(new BoundingBox(t.getMinX(), t.getMinY() - 30, Math.min(t.getWidth(), 1000), t.getHeight() + 30));
                }
                video.spoken(1);
                video.zoomOut();
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
