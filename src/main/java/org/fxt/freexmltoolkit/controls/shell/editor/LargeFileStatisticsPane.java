package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.Locale;
import java.util.function.Consumer;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlColumns;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.largefile.LargeXmlStatistics;
import org.fxt.freexmltoolkit.service.largefile.LargeXmlStatistics.PathStats;
import org.fxt.freexmltoolkit.service.largefile.LargeXmlStructure;

/**
 * The Statistics tab of the {@link LargeFileView}: one streaming pass over the file lists every
 * element path with its count, occurrences per parent, attributes, whether it has text and a
 * sample. Double-click a path to show its first element in the Structure tab.
 */
final class LargeFileStatisticsPane extends VBox {

    /** What the pane needs from the viewer. */
    interface Host {
        LargeXmlStructure structure();

        <T> void background(String label, LargeFileView.Job<T> job, Consumer<T> done, Runnable failed);

        /** Shows the first element with this path (names only) in the Structure tab. */
        void showPath(String path);
    }

    @FXML private Button scanButton;
    @FXML private Label summaryLabel;
    @FXML private TableView<PathStats> paths;
    @FXML private TableColumn<PathStats, String> pathColumn;
    @FXML private TableColumn<PathStats, String> countColumn;
    @FXML private TableColumn<PathStats, String> perParentColumn;
    @FXML private TableColumn<PathStats, String> attributesColumn;
    @FXML private TableColumn<PathStats, String> textColumn;
    @FXML private TableColumn<PathStats, String> sampleColumn;

    private final Host host;
    private LargeXmlStatistics.Result result;

    LargeFileStatisticsPane(Host host) {
        this.host = host;
        FxmlViews.load(this, "/pages/views/LargeFileStatisticsPane.fxml");
        FxmlColumns.text(pathColumn, p -> "  ".repeat(Math.max(0, p.depth() - 1)) + p.path().substring(p.path().lastIndexOf('/') + 1));
        FxmlColumns.text(countColumn, p -> String.format(Locale.ROOT, "%,d", p.count()));
        FxmlColumns.text(perParentColumn, p -> p.depth() <= 1 ? "" : p.minPerParent() == p.maxPerParent()
                ? String.format(Locale.ROOT, "%,d", p.maxPerParent())
                : String.format(Locale.ROOT, "%,d–%,d", p.minPerParent(), p.maxPerParent()));
        FxmlColumns.text(attributesColumn, p -> String.join(", ", p.attributes()));
        FxmlColumns.text(textColumn, p -> p.hasText() ? I18n.t("LargeFileStatisticsPane.yes") : "");
        FxmlColumns.text(sampleColumn, PathStats::sample);
        paths.setRowFactory(t -> {
            TableRow<PathStats> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    host.showPath(row.getItem().path());
                }
            });
            if (row.getTooltip() == null) {
                row.itemProperty().addListener((obs, o, item) -> row.setTooltip(item == null ? null
                        : new javafx.scene.control.Tooltip(item.path())));
            }
            return row;
        });
    }

    @FXML
    public void onScan() {
        LargeXmlStructure structure = host.structure();
        if (structure == null) {
            return;
        }
        scanButton.setDisable(true);
        long t0 = System.nanoTime();
        host.background(I18n.t("LargeFileStatisticsPane.scanning"),
                (cancelled, progress) -> LargeXmlStatistics.scan(structure, cancelled, progress), stats -> {
                    scanButton.setDisable(false);
                    show(stats, (System.nanoTime() - t0) / 1_000_000);
                }, () -> scanButton.setDisable(false));
    }

    private void show(LargeXmlStatistics.Result stats, long ms) {
        result = stats;
        paths.getItems().setAll(stats.paths());
        String text = I18n.t("LargeFileStatisticsPane.summary", String.format(Locale.ROOT, "%,d", stats.elements()),
                String.format(Locale.ROOT, "%,d", stats.attributes()), stats.maxDepth(),
                String.format(Locale.ROOT, "%,d", stats.paths().size()), String.format(Locale.ROOT, "%,d", ms));
        if (stats.droppedPaths() > 0) {
            text += " " + I18n.t("LargeFileStatisticsPane.dropped", String.format(Locale.ROOT, "%,d", stats.droppedPaths()));
        }
        summaryLabel.setText(text);
    }

    /** Forgets the statistics (after the file changed). */
    void reset() {
        result = null;
        paths.getItems().clear();
        summaryLabel.setText(I18n.t("LargeFileStatisticsPane.summaryLabel.text"));
    }

    // test access
    LargeXmlStatistics.Result resultForTesting() {
        return result;
    }

    TableView<PathStats> tableForTesting() {
        return paths;
    }
}
