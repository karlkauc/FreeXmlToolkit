package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.editor.ExplorerPanel;
import org.fxt.freexmltoolkit.controls.shell.editor.search.XPathSearchPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * Captures {@code search-in-files}: the Search activity with an XPath search over the XML files of a
 * folder, its matches per file and the selected match shown in the editor. The orders are synthetic
 * and written at run time.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*FindInFilesDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class FindInFilesDocScreenshotGenerator {

    private final DocShell doc = new DocShell("search-screenshot");

    @Start
    void start(Stage stage) throws Exception {
        doc.start(stage);
    }

    @Test
    void generateSearchScreenshot() throws Exception {
        doc.boot();
        Path dir = DocFiles.work("orders-2026");
        String[] customers = {"Northwind Books", "Harbour Stationery", "Example Trading", "Lakeside School", "City Library"};
        String[][] items = {{"BK-101", "24.90", "XML in a Nutshell"}, {"BK-207", "39.50", "XSLT 3.0 Cookbook"},
                {"BK-318", "12.00", "XPath Pocket Guide"}, {"BK-402", "54.00", "Schema Design Patterns"}};
        Path first = null;
        for (int i = 0; i < 6; i++) {
            StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append(String.format(Locale.ROOT, "<order id=\"PO-%d\" date=\"2026-%02d-14\">\n", 1042 + i, 3 + i));
            xml.append("  <customer>\n    <name>").append(customers[i % customers.length]).append("</name>\n  </customer>\n");
            xml.append("  <items>\n");
            for (int j = 0; j < 3; j++) {
                String[] item = items[(i + j) % items.length];
                xml.append("    <item sku=\"").append(item[0]).append("\" quantity=\"").append(1 + (i * 2 + j * 3) % 6)
                        .append("\" price=\"").append(item[1]).append("\">").append(item[2]).append("</item>\n");
            }
            xml.append("  </items>\n</order>\n");
            Path file = dir.resolve("order-" + (1042 + i) + ".xml");
            Files.writeString(file, xml);
            if (first == null) {
                first = file;
            }
        }

        doc.open(first, "PO-1042");
        doc.activity(Activity.EXPLORER);
        doc.onFx(() -> doc.findType(ExplorerPanel.class).setWorkspaceFolder(dir));
        doc.activity(Activity.SEARCH);
        doc.scene("search-in-files", () -> {
            doc.onFx(() -> ((ToggleButton) doc.find("#search-mode-xpath")).fire());
            doc.settle(500);
            XPathSearchPane pane = doc.findType(XPathSearchPane.class);
            doc.onFx(() -> {
                ((TextArea) pane.lookup("#xpath-query")).setText("//item[@quantity >= 4]");
                ((ToggleButton) pane.lookup("#xpath-scope-folder")).fire();
            });
            doc.settle(300);
            doc.onFx(() -> ((Button) pane.lookup("#xpath-find")).fire());
            TreeView<?> results = (TreeView<?>) doc.fx(() -> pane.lookup(".fxt-search-results"));
            doc.waitUntilFx(60, () -> results.getRoot() != null && results.getRoot().getChildren().size() > 1);
            doc.settle(800);
            // select a match: the file opens with the node selected
            doc.onFx(() -> selectFirstMatch(results));
            doc.settle(2000);
            doc.shot("search-in-files");
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void selectFirstMatch(TreeView results) {
        for (Object file : results.getRoot().getChildren()) {
            TreeItem<?> item = (TreeItem<?>) file;
            if (!item.getChildren().isEmpty()) {
                results.getSelectionModel().select(item.getChildren().getFirst());
                return;
            }
        }
    }
}
