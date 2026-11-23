package org.fxt.freexmltoolkit.screenshots;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import javafx.scene.control.Button;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.LargeFileView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * Captures the Large File Viewer images for {@code docs/guides/edit-xml/large-files.md}: the Structure
 * tab with the records of the selected element ({@code large-file-structure}) and the Statistics tab
 * after its scan ({@code large-file-statistics}). The viewer normally opens for files of 50 MB and
 * more; here it is opened directly on a synthetic file of a few megabytes written at run time.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*LargeFileDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class LargeFileDocScreenshotGenerator {

    private static final int RECORDS = 25_000;

    private final DocShell doc = new DocShell("large-file-screenshot");

    @Start
    void start(Stage stage) throws Exception {
        doc.start(stage);
    }

    @Test
    void generateLargeFileScreenshots() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        Path file = DocFiles.work("large-file").resolve("transactions-2026.xml");
        writeTransactions(file);

        LargeFileView view = doc.fx(() -> host.openLargeFileViewer(file));
        doc.waitUntilFx(120, () -> doc.call(view, "indexForTesting") != null);
        doc.settle(800);
        TabPane tabs = (TabPane) doc.fx(() -> view.lookup("#large-file-views"));

        // --- Structure: the tree on the left, the children of the selected element as records ---
        doc.scene("large-file-structure", () -> {
            doc.onFx(() -> tabs.getSelectionModel().select(2));
            doc.waitUntilFx(120, () -> view.lookup("#large-file-records") instanceof TableView<?> records
                    && !records.getItems().isEmpty());
            doc.onFx(() -> {
                TreeView<?> tree = (TreeView<?>) view.lookup("#large-file-tree");
                if (tree.getRoot() != null) {
                    tree.getRoot().setExpanded(true);
                }
            });
            doc.settle(1500);
            doc.shot("large-file-structure");
        });

        // --- Statistics: one pass over the file, counts per element path ---
        doc.scene("large-file-statistics", () -> {
            doc.onFx(() -> tabs.getSelectionModel().select(4));
            doc.settle(500);
            doc.onFx(() -> ((Button) view.lookup("#large-file-stats-scan")).fire());
            doc.waitUntilFx(120, () -> view.lookup("#large-file-stats") instanceof TableView<?> paths
                    && !paths.getItems().isEmpty());
            doc.settle(1200);
            doc.shot("large-file-statistics");
        });
    }

    /** Booked payments, about 6 MB (synthetic). */
    private static void writeTransactions(Path file) throws Exception {
        String[] currencies = {"EUR", "EUR", "EUR", "USD", "CHF", "GBP"};
        String[] types = {"transfer", "card", "direct-debit", "standing-order", "fee"};
        String[] countries = {"AT", "DE", "CH", "IT", "NL", "FR", "ES"};
        try (BufferedWriter out = Files.newBufferedWriter(file)) {
            out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<transactions year=\"2026\" source=\"booking-system\" records=\"" + RECORDS + "\">\n");
            for (int i = 1; i <= RECORDS; i++) {
                int day = 1 + i * 7 % 28;
                int month = 1 + i * 11 % 12;
                out.write(String.format(Locale.ROOT,
                        "  <transaction id=\"T-%07d\" status=\"%s\">\n"
                                + "    <booked>2026-%02d-%02d</booked>\n"
                                + "    <account>ACC-%05d</account>\n"
                                + "    <counterparty country=\"%s\">Customer %04d</counterparty>\n"
                                + "    <amount currency=\"%s\">%.2f</amount>\n"
                                + "    <type>%s</type>\n",
                        i, i % 97 == 0 ? "pending" : "booked", month, day, 10000 + i * 13 % 800,
                        countries[i * 3 % countries.length], 1 + i * 17 % 2500,
                        currencies[i % currencies.length], 5 + (i * 7919L % 250000) / 100.0,
                        types[i * 5 % types.length]));
                if (i % 4 == 0) {
                    out.write("    <reference>INV-2026-" + (100000 + i) + "</reference>\n");
                }
                out.write("  </transaction>\n");
            }
            out.write("</transactions>\n");
        }
    }
}
