package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.service.XSpecRunner;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/**
 * Tools → Run XSpec Tests: runs the active {@code .xspec} document ({@link XSpecRunner}) in the
 * background and shows its scenarios and tests in a tree; a selected test shows the expected and the
 * actual result. Run Again uses the current editor text; HTML Report opens XSpec's report in a
 * preview tab; Export JUnit writes the report for CI servers.
 */
public class XSpecView extends VBox {

    static final String TAB_PREFIX = "XSpec: ";

    @FXML private IconifyIcon summaryIcon;
    @FXML private Label summary;
    @FXML private Label subject;
    @FXML private Button runButton;
    @FXML private Button htmlButton;
    @FXML private Button junitButton;
    @FXML private TreeTableView<Object> tree;
    @FXML private TreeTableColumn<Object, String> labelColumn;
    @FXML private TreeTableColumn<Object, String> statusColumn;
    @FXML private TextArea expected;
    @FXML private TextArea actual;

    private final EditorHost editorHost;
    private final Path xspecFile;
    private final XSpecRunner runner = new XSpecRunner();
    private XSpecRunner.Result result;

    XSpecView(EditorHost editorHost, Path xspecFile) {
        this.editorHost = editorHost;
        this.xspecFile = xspecFile;
        FxmlViews.load(this, "/pages/views/XSpecView.fxml");
        labelColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(labelOf(c.getValue().getValue())));
        statusColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(statusOf(c.getValue().getValue())));
        tree.setRowFactory(t -> new TreeTableRow<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("fxt-xspec-failed", "fxt-xspec-passed", "fxt-xspec-pending");
                if (!empty && item != null) {
                    String status = statusOf(item);
                    if (status.startsWith("Failed")) {
                        getStyleClass().add("fxt-xspec-failed");
                    } else if (status.startsWith("Passed")) {
                        getStyleClass().add("fxt-xspec-passed");
                    } else if (status.startsWith("Pending")) {
                        getStyleClass().add("fxt-xspec-pending");
                    }
                }
            }
        });
        tree.getSelectionModel().selectedItemProperty().addListener((obs, o, item) -> {
            if (item != null && item.getValue() instanceof XSpecRunner.Test test) {
                expected.setText(test.expected());
                actual.setText(test.actual());
            } else {
                expected.clear();
                actual.clear();
            }
        });
        subject.setText(xspecFile.toString());
    }

    /**
     * Runs the active XSpec document and shows the result in its tab (reused per file).
     *
     * @return {@code false} when the active document is no saved XSpec file
     */
    public static boolean runActive(EditorHost editorHost) {
        Optional<OpenDocument> document = editorHost.getActiveDocument();
        String text = editorHost.getActiveText().orElse("");
        if (document.isEmpty() || document.get().getPath() == null || !XSpecRunner.isXSpec(text)) {
            DialogHelper.showInformation("Run XSpec Tests", "No XSpec document",
                    "Open a saved .xspec file (an x:description) to run its tests.");
            return false;
        }
        Path file = document.get().getPath();
        XSpecView view = null;
        for (var tab : editorHost.getToolTabs()) {
            if (tab.getContent() instanceof XSpecView existing && existing.xspecFile.equals(file)) {
                view = existing;
                editorHost.selectTab(tab);
            }
        }
        if (view == null) {
            view = new XSpecView(editorHost, file);
            editorHost.openToolTab(TAB_PREFIX + file.getFileName(), "bi-ui-checks", view);
        }
        view.run(text);
        return true;
    }

    /** Runs the tests of {@code text} (the XSpec document) in the background. */
    void run(String text) {
        setBusy(true);
        summary.setText("Running…");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            XSpecRunner.Result done = runner.run(text, xspecFile);
            Platform.runLater(() -> show(done));
        });
    }

    private void setBusy(boolean busy) {
        runButton.setDisable(busy);
        htmlButton.setDisable(busy || result == null || result.reportXml() == null);
        junitButton.setDisable(busy || result == null || result.reportXml() == null);
    }

    void show(XSpecRunner.Result done) {
        result = done;
        summary.setText(done.summary());
        summary.getStyleClass().removeAll("fxt-xspec-failed", "fxt-xspec-passed");
        summary.getStyleClass().add(done.passed() ? "fxt-xspec-passed" : "fxt-xspec-failed");
        summaryIcon.setIconLiteral(done.passed() ? "bi-check-circle-fill" : "bi-x-circle-fill");
        org.fxt.freexmltoolkit.controls.theme.SemanticIcon.paint(summaryIcon, done.passed()
                ? org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.SUCCESS
                : org.fxt.freexmltoolkit.controls.theme.DesignTokens.ColorToken.DANGER);
        TreeItem<Object> root = new TreeItem<>("root");
        for (XSpecRunner.Scenario scenario : done.scenarios()) {
            root.getChildren().add(item(scenario));
        }
        tree.setRoot(root);
        expandFailures(root);
        setBusy(false);
    }

    private static TreeItem<Object> item(XSpecRunner.Scenario scenario) {
        TreeItem<Object> item = new TreeItem<>(scenario);
        scenario.scenarios().forEach(s -> item.getChildren().add(item(s)));
        scenario.tests().forEach(t -> item.getChildren().add(new TreeItem<>(t)));
        return item;
    }

    /** Opens every scenario that contains a failed test (and selects the first failure). */
    private boolean expandFailures(TreeItem<Object> item) {
        boolean failed = item.getValue() instanceof XSpecRunner.Test test && test.status() == XSpecRunner.Status.FAILED;
        for (TreeItem<Object> child : item.getChildren()) {
            failed |= expandFailures(child);
        }
        if (failed) {
            item.setExpanded(true);
            if (item.getValue() instanceof XSpecRunner.Test && tree.getSelectionModel().getSelectedItem() == null) {
                tree.getSelectionModel().select(item);
            }
        }
        return failed;
    }

    static String labelOf(Object value) {
        if (value instanceof XSpecRunner.Scenario scenario) {
            return scenario.label().isEmpty() ? "(scenario)" : scenario.label();
        }
        if (value instanceof XSpecRunner.Test test) {
            return test.label().isEmpty() ? "(expectation)" : test.label();
        }
        return "";
    }

    static String statusOf(Object value) {
        if (value instanceof XSpecRunner.Test test) {
            return switch (test.status()) {
                case PASSED -> "Passed";
                case FAILED -> "Failed";
                case PENDING -> "Pending";
            };
        }
        if (value instanceof XSpecRunner.Scenario scenario) {
            var tests = scenario.allTests();
            long failed = tests.stream().filter(t -> t.status() == XSpecRunner.Status.FAILED).count();
            if (scenario.pending()) {
                return "Pending";
            }
            return failed > 0 ? "Failed (" + failed + " of " + tests.size() + ")"
                    : tests.isEmpty() ? "" : "Passed (" + tests.size() + ")";
        }
        return "";
    }

    @FXML
    public void onRunAgain() {
        editorHost.getOpenDocuments().stream().filter(d -> xspecFile.equals(d.getPath())).findFirst()
                .ifPresentOrElse(d -> {
                    editorHost.selectDocument(d);
                    run(editorHost.getActiveText().orElse(""));
                }, () -> {
                    try {
                        run(Files.readString(xspecFile, StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        summary.setText("Cannot read " + xspecFile + ": " + e.getMessage());
                    }
                });
    }

    @FXML
    public void onHtmlReport() {
        XSpecRunner.Result current = result;
        if (current == null || current.reportXml() == null) {
            return;
        }
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                String html = runner.html(current);
                Platform.runLater(() -> {
                    String name = xspecFile.getFileName().toString().replaceFirst("\\.xspec$", "") + "-report.html";
                    editorHost.openGeneratedDocument(html, EditorFileType.HTML, name);
                    editorHost.setActiveViewMode(ViewMode.PREVIEW);
                });
            } catch (Exception e) {
                Platform.runLater(() -> summary.setText("Cannot create the HTML report: " + e.getMessage()));
            }
        });
    }

    @FXML
    public void onExportJUnit() {
        XSpecRunner.Result current = result;
        if (current == null || current.reportXml() == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export JUnit Report");
        chooser.setInitialFileName(xspecFile.getFileName().toString().replaceFirst("\\.xspec$", "") + "-junit.xml");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JUnit XML", "*.xml"));
        File target = FileChooserHelper.showSaveDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (target == null) {
            return;
        }
        try {
            Files.writeString(target.toPath(), runner.junit(current), StandardCharsets.UTF_8);
        } catch (Exception e) {
            DialogHelper.showError("Export JUnit Report", "The report could not be written.", e.getMessage(), e);
        }
    }

    // test access
    XSpecRunner.Result resultForTesting() {
        return result;
    }

    TreeTableView<Object> treeForTesting() {
        return tree;
    }

    String expectedForTesting() {
        return expected.getText();
    }

    String actualForTesting() {
        return actual.getText();
    }
}
