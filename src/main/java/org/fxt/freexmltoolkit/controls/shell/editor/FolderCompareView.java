package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.diff.DiffView;
import org.fxt.freexmltoolkit.controls.shell.FxmlColumns;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.FolderComparison;
import org.fxt.freexmltoolkit.service.FolderComparison.Entry;
import org.fxt.freexmltoolkit.service.FolderComparison.Status;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/**
 * Tools → Compare Folders: compares two folders file by file in the background
 * ({@link FolderComparison}) and lists the files that differ or exist on one side only. A
 * double-click opens a file pair in the Compare view (or a one-sided file in the editor).
 */
public class FolderCompareView extends VBox {

    private static final Logger logger = LogManager.getLogger(FolderCompareView.class);
    private static final DateTimeFormatter MODIFIED =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

    @FXML private Label leftPath;
    @FXML private Label rightPath;
    @FXML private Button compareButton;
    @FXML private CheckBox compareXml;
    @FXML private CheckBox showIdentical;
    @FXML private ProgressIndicator progress;
    @FXML private Label summary;
    @FXML private TableView<Entry> table;
    @FXML private TableColumn<Entry, String> pathColumn;
    @FXML private TableColumn<Entry, String> statusColumn;
    @FXML private TableColumn<Entry, String> leftSizeColumn;
    @FXML private TableColumn<Entry, String> rightSizeColumn;
    @FXML private TableColumn<Entry, String> leftModifiedColumn;
    @FXML private TableColumn<Entry, String> rightModifiedColumn;
    @FXML private TableColumn<Entry, String> noteColumn;

    private final EditorHost editorHost;
    private Path left;
    private Path right;
    private FolderComparison.Result result;
    private AtomicBoolean running = new AtomicBoolean();

    /**
     * @param editorHost opens file pairs and single files
     * @param left       the left folder
     * @param right      the right folder
     */
    public FolderCompareView(EditorHost editorHost, Path left, Path right) {
        this.editorHost = editorHost;
        this.left = left;
        this.right = right;
        FxmlViews.load(this, "/pages/views/FolderCompareView.fxml");

        FxmlColumns.text(pathColumn, Entry::relativePath);
        FxmlColumns.text(statusColumn, FolderCompareView::statusText);
        FxmlColumns.text(leftSizeColumn, e -> size(e.leftSize()));
        FxmlColumns.text(rightSizeColumn, e -> size(e.rightSize()));
        FxmlColumns.text(leftModifiedColumn, e -> modified(e.leftModified()));
        FxmlColumns.text(rightModifiedColumn, e -> modified(e.rightModified()));
        FxmlColumns.text(noteColumn, Entry::note);
        table.setRowFactory(t -> {
            TableRow<Entry> row = new TableRow<>() {
                @Override
                protected void updateItem(Entry item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeIf(c -> c.startsWith("fxt-folder-compare-"));
                    if (!empty && item != null) {
                        getStyleClass().add("fxt-folder-compare-" + item.status().name().toLowerCase(Locale.ROOT));
                    }
                }
            };
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    open(row.getItem());
                }
            });
            return row;
        });
        showIdentical.selectedProperty().addListener((obs, o, n) -> showResult());
        compareXml.selectedProperty().addListener((obs, o, n) -> onCompare());
        updatePaths();
        onCompare();
    }

    private static String statusText(Entry entry) {
        return entry.status() == Status.DIFFERENT && entry.xmlDifferences() > 0
                ? "Different (" + entry.xmlDifferences() + (entry.xmlDifferences() == 1 ? " difference)" : " differences)")
                : entry.status().label();
    }

    private static String size(long bytes) {
        if (bytes < 0) {
            return "";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        return bytes < 1024 * 1024 ? String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
                : String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String modified(Instant instant) {
        return instant == null ? "" : MODIFIED.format(instant);
    }

    private void updatePaths() {
        leftPath.setText(left.toString());
        rightPath.setText(right.toString());
    }

    @FXML
    public void onChangeLeft() {
        chooseFolder("Left folder", left).ifPresent(folder -> {
            left = folder;
            updatePaths();
            onCompare();
        });
    }

    @FXML
    public void onChangeRight() {
        chooseFolder("Right folder", right).ifPresent(folder -> {
            right = folder;
            updatePaths();
            onCompare();
        });
    }

    private java.util.Optional<Path> chooseFolder(String title, Path current) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        if (current != null && current.toFile().isDirectory()) {
            chooser.setInitialDirectory(current.toFile());
        }
        File folder = FileChooserHelper.showDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        return java.util.Optional.ofNullable(folder).map(File::toPath);
    }

    /** Compares the folders (again) in the background; a running comparison is cancelled. */
    @FXML
    public void onCompare() {
        running.set(true);                       // cancels the previous run
        AtomicBoolean cancelled = new AtomicBoolean();
        running = cancelled;
        FolderComparison.Options options = new FolderComparison.Options(compareXml.isSelected(),
                DiffView.savedXmlOptions());
        Path l = left;
        Path r = right;
        progress.setVisible(true);
        progress.setManaged(true);
        summary.setText(I18n.t("FolderCompareView.comparing"));
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                FolderComparison.Result done = FolderComparison.compare(l, r, options,
                        count -> { }, cancelled::get);
                Platform.runLater(() -> {
                    if (!cancelled.get()) {
                        result = done;
                        progress.setVisible(false);
                        progress.setManaged(false);
                        showResult();
                    }
                });
            } catch (CancellationException ignored) {
                // a newer comparison replaced this one
            } catch (java.io.IOException | RuntimeException e) {
                logger.warn("Folder comparison failed", e);
                Platform.runLater(() -> {
                    if (!cancelled.get()) {
                        progress.setVisible(false);
                        progress.setManaged(false);
                        summary.setText(I18n.t("FolderCompareView.cannot-compare-the-folders", e.getMessage()));
                        table.getItems().clear();
                    }
                });
            }
        });
    }

    private void showResult() {
        if (result == null) {
            return;
        }
        List<Entry> shown = result.entries().stream()
                .filter(e -> showIdentical.isSelected() || e.status() != Status.IDENTICAL && e.status() != Status.EQUIVALENT)
                .toList();
        table.getItems().setAll(shown);
        summary.setText(summaryText(result));
    }

    static String summaryText(FolderComparison.Result result) {
        int files = result.entries().size();
        if (files == 0) {
            return "Both folders are empty.";
        }
        String text = files + (files == 1 ? " file: " : " files: ")
                + result.count(Status.IDENTICAL) + " identical, "
                + result.count(Status.EQUIVALENT) + " equivalent as XML, "
                + result.count(Status.DIFFERENT) + " different, "
                + result.count(Status.ONLY_LEFT) + " only left, "
                + result.count(Status.ONLY_RIGHT) + " only right";
        return result.same() ? text + " - the folders match." : text;
    }

    private void open(Entry entry) {
        Path leftFile = left.resolve(entry.relativePath());
        Path rightFile = right.resolve(entry.relativePath());
        switch (entry.status()) {
            case ONLY_LEFT -> editorHost.openFile(leftFile);
            case ONLY_RIGHT -> editorHost.openFile(rightFile);
            default -> editorHost.openDiffOfFiles(leftFile.toFile(), rightFile.toFile());
        }
    }

    // test access
    FolderComparison.Result resultForTesting() {
        return result;
    }

    List<Entry> shownEntriesForTesting() {
        return List.copyOf(table.getItems());
    }

    void setShowIdenticalForTesting(boolean show) {
        showIdentical.setSelected(show);
    }

    void openForTesting(Entry entry) {
        open(entry);
    }

    String summaryForTesting() {
        return summary.getText();
    }
}
