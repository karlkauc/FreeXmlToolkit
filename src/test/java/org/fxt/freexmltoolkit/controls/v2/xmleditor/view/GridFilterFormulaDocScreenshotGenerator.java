package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Documentation screenshots of grid formulas and filters inside opened table cells
 * ({@code docs/img/unified-shell-grid-*.png}), on a synthetic QMV-like report: a list of
 * records, each with a table of Datenpunkte, named sums (German number format) and a filter
 * saved in the document.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*GridFilterFormulaDocScreenshotGenerator*"
 * }</pre>
 * It lives in the grid's package to drive the canvas directly (open a cell, the cell's context menu).
 */
@ExtendWith(ApplicationExtension.class)
class GridFilterFormulaDocScreenshotGenerator {

    private static final File IMG_DIR = new File("docs/img");
    private static final String LIST = "PK.006.02.01.01_List_of_Assets";

    private Parent root;
    private UnifiedShellView shell;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .set(org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlHint.SHOWN_KEY, "true");
        org.fxt.freexmltoolkit.controls.v2.view.XsdTypeIconPaths.registerAll();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1680, 1000));
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    /** A synthetic report (made-up identifiers and amounts). */
    private static String sample() {
        String[][] rows = {
            {"LU0000000101", "ISIN", "5273.4420", "5509112.61"},
            {"AT0000000202", "ISIN", "126211.0000", "15569073.02"},
            {"DE0000000303", "ISIN", "24962.0000", "2937597.25"},
            {"AT0000000404", "ISIN", "723481.0000", "132085853.61"},
            {"LU0000000505", "ISIN", "38080.8860", "3824031.55"},
            {"DE0000000606", "ISIN", "48043.0000", "5833018.40"},
            {"ACC_0000_EUR", "CAU/INST", "1.0000", "1250000.00"},
        };
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Nationale_Meldung PK-Nummer=\"99\" GJ=\"2026\" Stichtag=\"Q1\">\n");
        for (int list = 0; list < 2; list++) {
            xml.append("  <").append(LIST).append(" VRG=\"").append(list == 0 ? 2 : 4).append("\">\n");
            xml.append("    <?fxt-filter expression=\"Datenpunkt[starts-with(Wert_C0010PK, 'AT')]\"?>\n");
            for (int i = 0; i < (list == 0 ? rows.length : 3); i++) {
                String[] r = rows[i];
                xml.append("    <Datenpunkt><TemplateCode>PK.006.02.01.01</TemplateCode>")
                        .append("<Wert_C0010PK>").append(r[0]).append("</Wert_C0010PK>")
                        .append("<Wert_C0020PK>").append(r[1]).append("</Wert_C0020PK>")
                        .append("<Wert_C0040>AT</Wert_C0040>")
                        .append("<Wert_C0060>").append(r[2]).append("</Wert_C0060>")
                        .append("<Wert_C0080>").append(r[3]).append("</Wert_C0080>")
                        .append("</Datenpunkt>\n");
            }
            for (String column : new String[] {"Wert_C0060", "Wert_C0080"}) {
                xml.append("    <?fxt-formula name=\"Summe_").append(column).append("\" store=\"false\" expression=\"")
                        .append("format-number(sum(Datenpunkt/").append(column)
                        .append(" ! xs:decimal(.)), '#.##0,00', 'de')\"?>\n");
            }
            xml.append("  </").append(LIST).append(">\n");
        }
        return xml.append("</Nationale_Meldung>\n").toString();
    }

    @Test
    void generateGridFilterFormulaScreenshots() throws Exception {
        IMG_DIR.mkdirs();
        Path file = Path.of("build/doc-samples/qmv-report.xml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, sample());

        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        shell = (UnifiedShellView) root.lookup(".fxt-shell");
        EditorHost host = shell.getEditorHost();
        settle(800);
        onFx(() -> host.openFile(file));
        settle(1500);
        onFx(() -> host.setActiveViewMode(ViewMode.GRAPHIC));
        settle(1200);
        XmlCanvasView view = call(() -> host.lookupAll("*").stream()
                .filter(n -> n instanceof XmlCanvasView && n.isVisible() && n.getScene() != null)
                .map(XmlCanvasView.class::cast).findFirst().orElseThrow());

        // 1. The saved filter comes back switched off: grey funnel line "… · saved", nothing filtered
        openFirstCell(view);
        shot("unified-shell-grid-saved-filter");

        // 2. Switched on (Filter Active): only the AT positions remain, the sums still cover all
        onFx(() -> {
            FlatRow list = listRow(view);
            view.showTableContextMenu(0, 0, list, list.getRepeatingTable(), 0, "Datenpunkt");
            view.contextMenu().hide();
            view.toggleFilterActiveOfSelection();
        });
        openFirstCell(view);
        shot("unified-shell-grid-cell-filter");

        // 3. Without filters: the record's named sums at the top of its opened cell
        onFx(view::removeAllFilters);
        openFirstCell(view);
        shot("unified-shell-grid-cell-formulas");
    }

    private static FlatRow listRow(XmlCanvasView view) {
        return view.visibleRowList().stream().filter(r -> r.getLabel() != null && r.getLabel().startsWith(LIST))
                .findFirst().orElseThrow();
    }

    /** Expands the grid and opens the Datenpunkt cell of the first record. */
    private void openFirstCell(XmlCanvasView view) {
        onFx(() -> {
            view.expandAll();
            RepeatingElementsTable table = listRow(view).getRepeatingTable();
            RepeatingElementsTable.TableRow record = table.getRows().getFirst();
            if (!record.isColumnExpanded("Datenpunkt")) {
                record.toggleColumnExpanded("Datenpunkt");
            }
            table.recalculateColumnWidths();
            view.recalculateVisibleRows();
            view.updateScrollBars();
            view.render();
        });
        settle(800);
    }

    private <T> T call(java.util.concurrent.Callable<T> action) {
        T result = WaitForAsyncUtils.waitForAsyncFx(60_000, action);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    private void onFx(Runnable action) {
        call(() -> {
            action.run();
            return null;
        });
    }

    private void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void shot(String name) throws Exception {
        var img = call(() -> {
            Node target = shell != null ? shell : root;
            return target.snapshot(new SnapshotParameters(), null);
        });
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("[grid-screenshot] wrote " + out.getAbsolutePath()
                + " (" + (int) img.getWidth() + "x" + (int) img.getHeight() + ")");
    }
}
