package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.diff.DiffView;
import org.fxt.freexmltoolkit.controls.diff.MergeView;
import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ExplorerPanel;
import org.fxt.freexmltoolkit.controls.shell.editor.FolderCompareView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * Captures the compare and merge images: two versions of a price list side by side as text
 * ({@code compare-text}) and as XML structure ({@code compare-xml}), two folders
 * ({@code compare-folders}) and a three-way merge with a conflict ({@code merge-three-way}). All
 * files are synthetic and written at run time.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*CompareDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class CompareDocScreenshotGenerator {

    private static final String BASE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <priceList valid-from="2026-01-01" currency="EUR">
              <!-- Office furniture, list prices without VAT -->
              <product sku="FU-1001" status="active">
                <name>Oak desk</name>
                <price>349.00</price>
                <delivery days="5"/>
              </product>
              <product sku="FU-1002" status="active">
                <name>Desk chair</name>
                <price>189.50</price>
                <delivery days="3"/>
              </product>
              <product sku="LI-2001" status="active">
                <name>Floor lamp</name>
                <price>79.90</price>
                <delivery days="3"/>
              </product>
              <product sku="ST-0312" status="active">
                <name>Filing cabinet</name>
                <price>229.00</price>
                <delivery days="7"/>
              </product>
              <product sku="AC-4001" status="active">
                <name>Monitor arm</name>
                <price>59.00</price>
                <delivery days="2"/>
              </product>
            </priceList>
            """;

    private final DocShell doc = new DocShell("compare-screenshot");

    @Start
    void start(Stage stage) throws Exception {
        doc.start(stage);
    }

    @Test
    void generateCompareScreenshots() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        Path dir = DocFiles.work("compare");

        // the new version: a price change, a status change with swapped attributes, a note, another last product
        String next = BASE
                .replace("valid-from=\"2026-01-01\"", "valid-from=\"2026-07-01\"")
                .replace("<price>349.00</price>", "<price>369.00</price>")
                .replace("<product sku=\"ST-0312\" status=\"active\">", "<product status=\"discontinued\" sku=\"ST-0312\">")
                .replace("<delivery days=\"3\"/>\n  </product>\n  <product sku=\"LI-2001\"",
                        "<delivery days=\"3\"/>\n    <note>New fabric colours</note>\n  </product>\n  <product sku=\"LI-2001\"")
                .replace("sku=\"AC-4001\"", "sku=\"AC-4002\"")
                .replace("<name>Monitor arm</name>", "<name>Cable tray</name>")
                .replace("<price>59.00</price>", "<price>19.90</price>");
        Path oldFile = dir.resolve("price-list-2026-01.xml");
        Path newFile = dir.resolve("price-list-2026-07.xml");
        Files.writeString(oldFile, BASE);
        Files.writeString(newFile, next);

        doc.open(oldFile, "priceList"); // the side panel shows once a document is open
        doc.activity(Activity.EXPLORER);
        doc.onFx(() -> {
            doc.findType(ExplorerPanel.class).setWorkspaceFolder(dir);
        });

        // --- two files side by side, as text and as XML structure ---
        doc.scene("compare-text / compare-xml", () -> {
            DiffView diff = doc.fx(() -> host.openDiffOfFiles(oldFile.toFile(), newFile.toFile()))
                    .get(30, TimeUnit.SECONDS);
            doc.waitUntilFx(30, () -> !diff.getChunksForTesting().isEmpty());
            doc.settle(1200);
            doc.shot("compare-text");

            doc.onFx(() -> diff.compareAsXml(DiffView.savedXmlOptions()));
            doc.waitUntilFx(30, () -> doc.shell().lookup("#diff-xml-table") instanceof TableView<?> table
                    && !table.getItems().isEmpty());
            doc.settle(1200);
            doc.shot("compare-xml");
            doc.closeAllTabs();
        });

        // --- two folders ---
        doc.scene("compare-folders", () -> {
            Path left = Files.createDirectories(dir.resolve("release-1.0"));
            Path right = Files.createDirectories(dir.resolve("release-1.1"));
            String schema = """
                    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                      <xs:element name="priceList" type="xs:anyType"/>
                    </xs:schema>
                    """;
            Files.writeString(left.resolve("price-list.xml"), BASE);
            Files.writeString(right.resolve("price-list.xml"), next);
            Files.writeString(left.resolve("price-list.xsd"), schema);
            Files.writeString(right.resolve("price-list.xsd"), schema);
            // the same document, only formatted differently: equivalent as XML
            String customers = "<customers>\n  <customer id=\"C-17\" country=\"AT\"><name>Example Trading</name></customer>\n</customers>\n";
            Files.writeString(left.resolve("customers.xml"), customers);
            Files.writeString(right.resolve("customers.xml"),
                    "<customers>\n  <customer country=\"AT\" id=\"C-17\">\n    <name>Example Trading</name>\n  </customer>\n</customers>\n");
            Files.createDirectories(left.resolve("reports"));
            Files.createDirectories(right.resolve("reports"));
            String report = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">\n"
                    + "  <xsl:template match=\"/\"><report/></xsl:template>\n</xsl:stylesheet>\n";
            Files.writeString(left.resolve("reports/summary.xsl"), report);
            Files.writeString(right.resolve("reports/summary.xsl"), report.replace("<report/>", "<report version=\"2\"/>"));
            Files.writeString(left.resolve("reports/legacy-export.xsl"), report);
            Files.writeString(right.resolve("reports/stock-export.xsl"), report);
            Files.writeString(right.resolve("CHANGES.txt"), "1.1: new prices from July 2026\n");

            FolderCompareView view = doc.fx(() -> {
                FolderCompareView v = new FolderCompareView(host, left, right);
                host.openToolTab("Compare release-1.0 ↔ release-1.1", "bi-folder2-open", v);
                return v;
            });
            doc.waitUntilFx(60, () -> view.lookup("#folder-compare-summary") instanceof Label summary
                    && summary.getText() != null && summary.getText().contains("files"));
            doc.onFx(() -> ((javafx.scene.control.CheckBox) view.lookup("#folder-compare-show-identical")).setSelected(true));
            doc.settle(1200);
            doc.shot("compare-folders");
            doc.closeAllTabs();
        });

        // --- three-way merge: one change of each side merges, the price of the desk conflicts ---
        doc.scene("merge-three-way", () -> {
            Path merge = Files.createDirectories(dir.resolve("merge"));
            Path base = merge.resolve("price-list.base.xml");
            Path mine = merge.resolve("price-list.mine.xml");
            Path theirs = merge.resolve("price-list.theirs.xml");
            Files.writeString(base, BASE);
            Files.writeString(mine, BASE.replace("<price>349.00</price>", "<price>369.00</price>")
                    .replace("<delivery days=\"7\"/>", "<delivery days=\"10\"/>"));
            Files.writeString(theirs, BASE.replace("<price>349.00</price>", "<price>359.00</price>")
                    .replace("<name>Floor lamp</name>", "<name>Floor lamp, dimmable</name>"));
            MergeView view = MergeView.open(base, mine, theirs, merge.resolve("price-list.xml")).get(30, TimeUnit.SECONDS);
            doc.onFx(() -> host.openToolTab("Merge price-list.mine.xml", "bi-sign-merge-left", view));
            doc.settle(800);
            doc.onFx(view::onNext);
            doc.settle(1000);
            doc.shot("merge-three-way");
            doc.closeAllTabs();
        });
    }
}
