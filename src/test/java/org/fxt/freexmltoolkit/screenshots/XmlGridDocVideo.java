package org.fxt.freexmltoolkit.screenshots;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridVideoProbe;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.XmlCanvasView;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "The XML grid" (storyboard {@code videos/xml-grid.json},
 * after {@code docs/guides/edit-xml/grid.md}): switch the example order to the Graphic view, expand
 * it, look at the table of repeating elements, edit a cell, sort a column, and find both changes in
 * the text.
 *
 * <pre>{@code
 * python3 scripts/make-video.py xml-grid --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class XmlGridDocVideo {

    private final DocShell doc = new DocShell("video");

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        org.fxt.freexmltoolkit.i18n.I18n.initialize(null);
        doc.start(stage, DocVideoRecorder.WIDTH, DocVideoRecorder.HEIGHT);
    }

    @Test
    void record() throws Exception {
        var shell = doc.boot();
        EditorHost host = doc.host();
        // before the film starts: the example order is open as text
        doc.call(shell, "openExample");
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PO-1042")).orElse(false));
        doc.settle(2000);

        DocVideoRecorder video = new DocVideoRecorder(doc);
        VideoTyping keys = new VideoTyping(doc, video);
        video.start();
        try {
            video.scene("switch", () -> {
                video.spoken(0.4);
                List<Node> modes = viewSwitch(host);
                Bounds first = video.screenBounds(modes.getFirst());
                Bounds last = video.screenBounds(modes.getLast());
                video.zoom(new BoundingBox(first.getMinX(), first.getMinY(),
                        last.getMaxX() - first.getMinX(), first.getHeight()));
                video.spoken(0.7);
                video.click(modes.get(2)); // Text, Tree, Graphic
                doc.waitUntilFx(30, () -> GridVideoProbe.view(host) != null);
                video.pause(600);
                video.zoomOut();
            });
            XmlCanvasView grid = doc.fx(() -> GridVideoProbe.view(host));

            video.scene("blocks", () -> {
                video.spoken(0.55);
                video.click(doc.<Node>find("#grid-expand-all"));
                video.pause(700);
                Bounds canvas = doc.fx(() -> GridVideoProbe.canvas(grid));
                video.zoom(new BoundingBox(canvas.getMinX(), canvas.getMinY(), 1000, 520));
            });

            video.scene("collapse", () -> {
                video.zoomOut();
                video.spoken(0.1);
                video.click(doc.<Node>find("#grid-collapse-all"));
                video.pause(1500);
                Bounds canvas = doc.fx(() -> GridVideoProbe.canvas(grid));
                video.zoom(new BoundingBox(canvas.getMinX(), canvas.getMinY(), 1000, 420));
                video.spoken(0.7);
                video.zoomOut();
                video.click(doc.<Node>find("#grid-expand-all"));
                video.pause(800);
            });

            video.scene("layout", () -> {
                video.zoomOut();
                video.spoken(0.15);
                video.click(doc.<Node>find("#grid-zoom-in"));
                video.pause(700);
                video.click(doc.<Node>find("#grid-zoom-in"));
                video.pause(900);
                video.click(doc.<Node>find("#grid-zoom-out"));
                video.pause(500);
                video.click(doc.<Node>find("#grid-zoom-out"));
                video.spoken(0.6);
                video.click(doc.<Node>find("#grid-optimal-widths"));
                video.pause(800);
            });

            video.scene("table", () -> {
                video.spoken(0.1);
                video.zoom(doc.fx(() -> GridVideoProbe.table(grid)));
                video.spoken(0.5);
                Bounds cell = doc.fx(() -> GridVideoProbe.cell(grid, 0, "sku"));
                Bounds end = doc.fx(() -> GridVideoProbe.cell(grid, 2, "sku"));
                video.moveTo(cell.getCenterX(), cell.getCenterY());
                video.moveTo(end.getCenterX(), end.getCenterY());
            });

            video.scene("sort", () -> {
                video.spoken(0.35);
                Bounds arrow = doc.fx(() -> GridVideoProbe.sortArrow(grid, "price"));
                video.click(arrow.getCenterX(), arrow.getCenterY());
                doc.waitUntilFx(15, () -> {
                    String text = host.getActiveText().orElse("");
                    return text.indexOf("BK-318") < text.indexOf("BK-101");
                });
                video.spoken(0.75);
                keys.shortcut(KeyCode.Z, false, "Z");
                doc.waitUntilFx(15, () -> {
                    String text = host.getActiveText().orElse("");
                    return text.indexOf("BK-101") < text.indexOf("BK-318");
                });
            });

            video.scene("flip", () -> {
                video.zoom(doc.fx(() -> GridVideoProbe.table(grid)));
                video.spoken(0.15);
                Bounds corner = doc.fx(() -> GridVideoProbe.corner(grid));
                video.click(corner.getCenterX(), corner.getCenterY());
                video.pause(1500);
                video.zoomOut();
                video.zoom(doc.fx(() -> GridVideoProbe.table(grid)));
                video.spoken(0.8);
                Bounds back = doc.fx(() -> GridVideoProbe.corner(grid));
                video.click(back.getCenterX(), back.getCenterY());
                video.pause(1200);
                video.zoomOut();
                video.zoom(doc.fx(() -> GridVideoProbe.table(grid)));
            });

            video.scene("list", () -> {
                Bounds cell = doc.fx(() -> GridVideoProbe.cell(grid, 0, "sku"));
                video.spoken(0.3);
                video.rightClick(cell.getCenterX(), cell.getCenterY());
                video.pause(500);
                video.click(video.menuItem(I18n.t("XmlGridContextMenu.display-as-table")));
                video.pause(1800);
                video.zoomOut();
                Bounds canvas = doc.fx(() -> GridVideoProbe.canvas(grid));
                video.zoom(new BoundingBox(canvas.getMinX(), canvas.getMinY(), 1000, 600));
                video.spoken(0.8);
                video.zoomOut();
                Bounds items = doc.fx(() -> GridVideoProbe.nameCell(grid, "item"));
                if (items != null) {
                    video.rightClick(items.getMinX() + 20, items.getCenterY());
                    video.pause(500);
                    video.click(video.menuItem(I18n.t("XmlGridContextMenu.display-as-table")));
                    video.pause(1200);
                }
                keys.hidePopups();
                System.out.println("[video] table back: " + doc.fx(() -> { try { GridVideoProbe.table(grid); return true; } catch (RuntimeException e) { return false; } }));
            });

            video.scene("find", () -> {
                Bounds canvas = doc.fx(() -> GridVideoProbe.canvas(grid));
                video.click(canvas.getMinX() + 40, canvas.getMinY() + 20);
                video.pause(300);
                keys.shortcut(KeyCode.F, false, "F");
                video.pause(900);
                Node owner = doc.fx(() -> doc.stage().getScene().getFocusOwner());
                if (owner instanceof javafx.scene.control.TextInputControl) {
                    video.typeIntoFocused("Cookbook");
                    video.spoken(0.35);
                    video.key(KeyCode.ENTER, "Enter");
                    video.pause(1500);
                } else {
                    System.out.println("[video] no find field in the grid: " + owner);
                }
                video.spoken(0.9);
                video.key(KeyCode.ESCAPE, "Esc");
                video.pause(400);
            });

            video.scene("edit", () -> {
                // at once: the cell editor takes a moment to open under the recording's software rendering
                Bounds cell = doc.fx(() -> GridVideoProbe.cell(grid, 0, "quantity"));
                video.doubleClick(cell.getCenterX(), cell.getCenterY());
                video.spoken(0.5);
                video.typeIntoFocused("5");
                video.spoken(0.85);
                video.key(KeyCode.ENTER, "Enter");
                doc.waitUntilFx(15, () -> host.getActiveText().orElse("").contains("quantity=\"5\""));
            });

            video.scene("invalid", () -> {
                Bounds cell = doc.fx(() -> GridVideoProbe.cell(grid, 1, "quantity"));
                video.doubleClick(cell.getCenterX(), cell.getCenterY());
                video.spoken(0.25);
                video.typeIntoFocused("0");
                video.pause(2500);
                video.spoken(0.85);
                video.key(KeyCode.ESCAPE, "Esc");
                video.pause(500);
            });

            video.scene("rows", () -> {
                video.zoomOut();
                video.zoom(doc.fx(() -> GridVideoProbe.table(grid)));
                Bounds cell = doc.fx(() -> GridVideoProbe.cell(grid, 2, "sku"));
                video.spoken(0.15);
                video.rightClick(cell.getCenterX(), cell.getCenterY());
                video.pause(500);
                Node tableItem = video.menuItem(I18n.t("XmlGridContextMenu.table"));
                Bounds tb = video.screenBounds(tableItem);
                video.moveTo(tb.getMinX() + 30, tb.getCenterY());
                video.pause(900);
                video.spoken(0.6);
                Node append = video.menuItem(I18n.t("XmlGridContextMenu.append-row"));
                Bounds ab = video.screenBounds(append);
                // into the submenu sideways first: a diagonal path crosses other items and closes it
                video.moveTo(ab.getMinX() + 20, tb.getCenterY());
                video.pause(300);
                video.click(ab.getMinX() + 40, ab.getCenterY());
                try {
                    doc.waitUntilFx(15, () -> host.getActiveText().orElse("").split("<item\\b", -1).length - 1 >= 4);
                } catch (Exception e) {
                    System.out.println("[video] no row appended; items in text: "
                            + (host.getActiveText().orElse("").split("<item\\b", -1).length - 1));
                }
                video.pause(1200);
                video.zoomOut();
                video.zoom(doc.fx(() -> GridVideoProbe.table(grid)));
                Bounds sku = doc.fx(() -> GridVideoProbe.cell(grid, 3, "sku"));
                video.doubleClick(sku.getCenterX(), sku.getCenterY());
                video.typeIntoFocused("BK-404");
                video.key(KeyCode.ENTER, "Enter");
                video.pause(600);
                // a new item gets a quantity and a price too, or the formula later has nothing to multiply
                for (String[] v : new String[][] {{"quantity", "1"}, {"price", "9.90"}}) {
                    Bounds c = doc.fx(() -> GridVideoProbe.cell(grid, 3, v[0]));
                    video.doubleClick(c.getCenterX(), c.getCenterY());
                    video.typeIntoFocused(v[1]);
                    video.key(KeyCode.ENTER, "Enter");
                    video.pause(500);
                }
                System.out.println("[video] new item: " + host.getActiveText().orElse("").lines()
                        .filter(l -> l.contains("BK-404")).findFirst().orElse("-"));
            });

            video.scene("column", () -> {
                Bounds cell = doc.fx(() -> GridVideoProbe.cell(grid, 0, "price"));
                video.rightClick(cell.getCenterX(), cell.getCenterY());
                video.pause(500);
                Node tableItem = video.menuItem(I18n.t("XmlGridContextMenu.table"));
                Bounds tb = video.screenBounds(tableItem);
                video.moveTo(tb.getMinX() + 30, tb.getCenterY());
                video.pause(900);
                Node insert = video.menuItem(I18n.t("XmlGridContextMenu.insert-column-after"));
                Bounds ib = video.screenBounds(insert);
                video.moveTo(ib.getMinX() + 20, tb.getCenterY());
                video.pause(300);
                video.click(ib.getMinX() + 40, ib.getCenterY());
                video.pause(1200);
                System.out.println("[video] column dialog: " + keys.dialog());
                if (keys.dialog() != null) {
                    video.typeIntoFocused("@discount");
                    video.spoken(0.4);
                    clickDefault(video, keys);
                    video.pause(1500);
                }
                video.zoomOut();
                video.zoom(doc.fx(() -> GridVideoProbe.table(grid)));
                video.spoken(0.85);
                System.out.println("[video] discount in text: " + host.getActiveText().orElse("").contains("discount"));
                if (host.getActiveText().orElse("").contains("discount")) {
                    keys.shortcut(KeyCode.Z, false, "Z");
                    video.pause(800);
                }
            });

            video.scene("spreadsheet", () -> {
                Bounds cell = doc.fx(() -> GridVideoProbe.cell(grid, 1, "price"));
                video.spoken(0.1);
                video.rightClick(cell.getCenterX(), cell.getCenterY());
                video.pause(500);
                Node copy = video.menuItem(I18n.t("XmlGridContextMenu.copy-table-as-tab-separated"));
                video.moveTo(copy);
                video.spoken(0.85);
                keys.hidePopups();
            });

            video.scene("filter", () -> {
                Bounds cell = doc.fx(() -> GridVideoProbe.cell(grid, 0, "price"));
                video.rightClick(cell.getCenterX(), cell.getCenterY());
                video.pause(500);
                video.click(video.menuItem(I18n.t("XmlGridContextMenu.filter")));
                video.pause(1200);
                video.typeIntoFocused("item[@price > 20]");
                video.spoken(0.3);
                clickDefault(video, keys);
                video.pause(1500);
                video.zoomOut();
                Bounds canvas = doc.fx(() -> GridVideoProbe.canvas(grid));
                video.zoom(new BoundingBox(canvas.getMinX(), canvas.getMinY(), 1100, 600));
                video.spoken(0.8);
                Bounds again = doc.fx(() -> GridVideoProbe.cell(grid, 0, "price"));
                video.rightClick(again.getCenterX(), again.getCenterY());
                video.pause(500);
                Node remove = video.menuItem(I18n.t("XmlGridContextMenu.remove-filter"));
                video.click(remove);
                video.pause(1000);
            });

            video.scene("formula", () -> {
                Bounds items = doc.fx(() -> GridVideoProbe.nameCell(grid, "items"));
                System.out.println("[video] items name cell " + items + " labels " + doc.fx(() -> GridVideoProbe.labels(grid)));
                if (items == null) {
                    return;
                }
                video.rightClick(items.getMinX() + 20, items.getCenterY());
                video.pause(500);
                video.click(video.menuItem(I18n.t("XmlGridContextMenu.add-formula")));
                video.pause(1200);
                javafx.stage.Window dialog = keys.dialog();
                if (dialog != null) {
                    java.util.List<Node> fields = doc.fx(() -> dialog.getScene().getRoot().lookupAll(".text-field").stream()
                            .filter(n -> n.isVisible()).toList());
                    System.out.println("[video] formula fields " + fields.size());
                    if (fields.size() >= 2) {
                        video.click(fields.get(0));
                        video.typeIntoFocused(keys.say("Summe", "Total"));
                        video.click(fields.get(1));
                        video.typeIntoFocused("sum(item/(@quantity * @price))");
                    } else if (fields.size() == 1) {
                        video.click(fields.get(0));
                        video.typeIntoFocused("sum(item/(@quantity * @price))");
                    }
                    video.spoken(0.6);
                    clickDefault(video, keys);
                }
                video.pause(1500);
                video.zoomOut();
                Bounds canvas = doc.fx(() -> GridVideoProbe.canvas(grid));
                video.zoom(new BoundingBox(canvas.getMinX(), canvas.getMinY(), 1100, 600));
                video.spoken(0.8);
                Bounds q = doc.fx(() -> GridVideoProbe.cell(grid, 1, "quantity"));
                video.doubleClick(q.getCenterX(), q.getCenterY());
                video.typeIntoFocused("3");
                video.key(KeyCode.ENTER, "Enter");
                video.pause(1200);
            });

            video.scene("helpers", () -> {
                video.zoomOut();
                video.spoken(0.1);
                Node toggle = doc.find("#grid-entry-helpers-toggle");
                video.click(toggle);
                video.pause(1200);
                Bounds name = doc.fx(() -> GridVideoProbe.nameCell(grid, "note"));
                if (name != null) {
                    video.click(name.getMinX() + 20, name.getCenterY());
                }
                video.pause(1000);
                Node panel = doc.find("#grid-entry-helpers");
                if (panel != null) {
                    video.zoom(panel);
                }
                video.spoken(0.9);
                video.zoomOut();
                video.click(toggle);
                video.pause(500);
            });

            video.scene("text", () -> {
                video.zoomOut();
                video.spoken(0.3);
                video.click(viewSwitch(host).getFirst());
                video.pause(900);
                String text = host.getActiveText().orElseThrow();
                int from = text.indexOf("<items>");
                int to = text.indexOf("</items>") + "</items>".length();
                CodeArea area = doc.findType(CodeArea.class);
                Bounds lines = doc.fx(() -> area.getCharacterBoundsOnScreen(from, to).orElseThrow());
                video.zoom(new BoundingBox(lines.getMinX(), lines.getMinY(), 820, lines.getHeight()));
                video.spoken(0.85);
                video.showKey(keys.ctrl() + "+Z");
            });
        } finally {
            video.stop();
        }
    }

    /** Clicks the default (OK) button of the newest dialog. */
    private void clickDefault(DocVideoRecorder video, VideoTyping keys) {
        javafx.stage.Window dialog = keys.dialog();
        if (dialog == null) {
            return;
        }
        Node ok = doc.fx(() -> dialog.getScene().getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof javafx.scene.control.Button b && b.isDefaultButton() && !b.isDisabled())
                .findFirst().orElse(null));
        if (ok != null) {
            video.click(ok);
        }
    }

    /** The buttons of the view switch that are showing, in their order (Text, Tree, Graphic). */
    private List<Node> viewSwitch(EditorHost host) {
        return doc.fx(() -> host.getViewSwitch().lookupAll(".fxt-view-seg").stream()
                .filter(n -> n.isVisible() && n.isManaged()).toList());
    }
}
