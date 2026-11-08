package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import org.fxt.freexmltoolkit.i18n.I18n;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.shell.FxmlColumns;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.service.XsltProfile;
import org.fxt.freexmltoolkit.service.XsltProfile.CallNode;
import org.fxt.freexmltoolkit.service.XsltProfile.ComponentStats;
import org.fxt.freexmltoolkit.service.XsltProfile.LineStats;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/**
 * The XSLT profile of one run ({@link XsltProfile}): hot spots (templates and functions by self
 * time), the call tree, a flame graph and the self time per stylesheet line. A double-click opens
 * the source line; Profile Again measures once more (the JIT is warm then); Export writes JSON or text.
 */
public class ProfileView extends VBox {

    @FXML private Label summary;
    @FXML private Button againButton;
    @FXML private TabPane tabs;
    @FXML private TableView<ComponentStats> components;
    @FXML private TableColumn<ComponentStats, String> nameColumn;
    @FXML private TableColumn<ComponentStats, String> kindColumn;
    @FXML private TableColumn<ComponentStats, String> locationColumn;
    @FXML private TableColumn<ComponentStats, String> callsColumn;
    @FXML private TableColumn<ComponentStats, String> selfColumn;
    @FXML private TableColumn<ComponentStats, String> selfPercentColumn;
    @FXML private TableColumn<ComponentStats, String> totalColumn;
    @FXML private TableColumn<ComponentStats, String> avgColumn;
    @FXML private TreeTableView<CallNode> tree;
    @FXML private TreeTableColumn<CallNode, String> treeNameColumn;
    @FXML private TreeTableColumn<CallNode, String> treeCallsColumn;
    @FXML private TreeTableColumn<CallNode, String> treeTotalColumn;
    @FXML private TreeTableColumn<CallNode, String> treePercentColumn;
    @FXML private TreeTableColumn<CallNode, String> treeSelfColumn;
    @FXML private ScrollPane flameScroll;
    @FXML private TableView<LineStats> lines;
    @FXML private TableColumn<LineStats, String> lineLocationColumn;
    @FXML private TableColumn<LineStats, String> lineSourceColumn;
    @FXML private TableColumn<LineStats, String> lineHitsColumn;
    @FXML private TableColumn<LineStats, String> lineSelfColumn;
    @FXML private TableColumn<LineStats, String> lineSelfPercentColumn;
    @FXML private Label note;

    private final XsltProfile profile;
    private final FlameGraph flameGraph;
    private final Map<String, List<String>> sourceLines = new HashMap<>();
    private Runnable profileAgain;
    private BiConsumer<String, Integer> sourceOpener = (systemId, line) -> { };

    /**
     * @param profile the profile
     * @param result  the measured run (output size for the summary), or {@code null}
     */
    public ProfileView(XsltProfile profile, XsltTransformationResult result) {
        this.profile = profile;
        FxmlViews.load(this, "/pages/views/ProfileView.fxml");
        int outputSize = result == null || result.getOutputContent() == null ? 0 : result.getOutputContent().length();
        summary.setText(I18n.t("ProfileView.total-ms-template-function-calls", XsltProfile.millis(profile.totalNanos()), profile.totalCalls(), outputSize, (profile.warmupRuns() > 0 ? I18n.t("ProfileView.afterWarmup", profile.warmupRuns()) : "")));
        againButton.setVisible(false);
        againButton.setManaged(false);

        FxmlColumns.text(nameColumn, s -> s.component().name());
        FxmlColumns.text(kindColumn, s -> s.component().kind().label());
        FxmlColumns.text(locationColumn, s -> s.component().module() + ":" + s.component().line());
        FxmlColumns.text(callsColumn, s -> Long.toString(s.calls()));
        FxmlColumns.text(selfColumn, s -> XsltProfile.millis(s.selfNanos()));
        FxmlColumns.text(selfPercentColumn, s -> profile.percent(s.selfNanos()));
        FxmlColumns.text(totalColumn, s -> XsltProfile.millis(s.totalNanos()));
        FxmlColumns.text(avgColumn, s -> XsltProfile.millis(s.calls() == 0 ? 0 : s.totalNanos() / s.calls()));
        components.getItems().setAll(profile.components());
        components.setRowFactory(t -> openOnDoubleClick(new TableRow<>(),
                s -> open(s.component().systemId(), s.component().line())));

        treeNameColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getValue().label()));
        treeCallsColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(Long.toString(c.getValue().getValue().calls())));
        treeTotalColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(XsltProfile.millis(c.getValue().getValue().totalNanos())));
        treePercentColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(profile.percent(c.getValue().getValue().totalNanos())));
        treeSelfColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(XsltProfile.millis(c.getValue().getValue().selfNanos())));
        tree.setRoot(treeItem(profile.root(), 0));
        tree.getRoot().setExpanded(true);
        expandHotPath(tree.getRoot());
        tree.setRowFactory(t -> {
            TreeTableRow<CallNode> row = new TreeTableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && row.getItem() != null && row.getItem().component() != null) {
                    open(row.getItem().component().systemId(), row.getItem().component().line());
                }
            });
            return row;
        });

        flameGraph = new FlameGraph(profile);
        flameGraph.setOnOpen(node -> open(node.component().systemId(), node.component().line()));
        flameScroll.setContent(flameGraph);

        FxmlColumns.text(lineLocationColumn, l -> l.module() + ":" + l.line());
        FxmlColumns.text(lineSourceColumn, l -> sourceLine(l.systemId(), l.line()));
        FxmlColumns.text(lineHitsColumn, l -> Long.toString(l.hits()));
        FxmlColumns.text(lineSelfColumn, l -> XsltProfile.millis(l.selfNanos()));
        FxmlColumns.text(lineSelfPercentColumn, l -> profile.percent(l.selfNanos()));
        lines.getItems().setAll(profile.lines().subList(0, Math.min(500, profile.lines().size())));
        lines.setRowFactory(t -> openOnDoubleClick(new TableRow<>(), l -> open(l.systemId(), l.line())));
    }

    /** Shows Profile Again, which runs {@code action}. */
    public void setOnProfileAgain(Runnable action) {
        profileAgain = action;
        againButton.setVisible(action != null);
        againButton.setManaged(action != null);
    }

    /** Opens a stylesheet module at a line (double-click on a row or a bar). */
    public void setSourceOpener(BiConsumer<String, Integer> opener) {
        sourceOpener = opener == null ? (systemId, line) -> { } : opener;
    }

    private <T> TableRow<T> openOnDoubleClick(TableRow<T> row, java.util.function.Consumer<T> action) {
        row.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && !row.isEmpty()) {
                action.accept(row.getItem());
            }
        });
        return row;
    }

    private void open(String systemId, int line) {
        if (systemId != null && !systemId.isEmpty()) {
            sourceOpener.accept(systemId, line);
        }
    }

    private static TreeItem<CallNode> treeItem(CallNode node, int depth) {
        TreeItem<CallNode> item = new TreeItem<>(node);
        if (depth < 300) {
            for (CallNode child : node.children()) {
                item.getChildren().add(treeItem(child, depth + 1));
            }
        }
        return item;
    }

    /** Opens the slowest path a few levels deep, so the tree starts at the interesting part. */
    private static void expandHotPath(TreeItem<CallNode> item) {
        TreeItem<CallNode> current = item;
        for (int level = 0; level < 8 && current != null; level++) {
            current.setExpanded(true);
            current = current.getChildren().isEmpty() ? null : current.getChildren().get(0);
        }
    }

    /** The text of a stylesheet line (read once per module), trimmed. */
    private String sourceLine(String systemId, int line) {
        List<String> text = sourceLines.computeIfAbsent(systemId, ProfileView::readLines);
        return line > 0 && line <= text.size() ? text.get(line - 1).strip() : "";
    }

    private static List<String> readLines(String systemId) {
        Path path = toPath(systemId);
        if (path == null) {
            return List.of();
        }
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /** @return the local file of a {@code file:} URI or a path, or {@code null} */
    public static Path toPath(String systemId) {
        if (systemId == null || systemId.isBlank()) {
            return null;
        }
        try {
            Path path = systemId.startsWith("file:") ? Path.of(URI.create(systemId)) : Path.of(systemId);
            return Files.isRegularFile(path) ? path : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @FXML
    public void onProfileAgain() {
        if (profileAgain != null) {
            againButton.setDisable(true);
            profileAgain.run();
        }
    }

    @FXML
    public void onResetZoom() {
        flameGraph.resetZoom();
    }

    @FXML
    public void onExportJson() {
        export("profile.json", "JSON", "*.json", profile.toJson());
    }

    @FXML
    public void onExportText() {
        export("profile.txt", "Text", "*.txt", profile.toText(100));
    }

    private void export(String name, String type, String glob, String content) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("ProfileView.export-profile"));
        chooser.setInitialFileName(name);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(type, glob));
        File file = FileChooserHelper.showSaveDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            DialogHelper.showError(I18n.t("ProfileView.export-profile"), I18n.t("ProfileView.the-profile-could-not-be"), e.getMessage(), e);
        }
    }

    // test access
    public int getRowCount() {
        return components.getItems().size();
    }

    public String getSummaryText() {
        return summary.getText();
    }

    FlameGraph flameGraphForTesting() {
        return flameGraph;
    }

    TreeTableView<CallNode> treeForTesting() {
        return tree;
    }

    List<LineStats> linesForTesting() {
        return List.copyOf(lines.getItems());
    }

    String sourceLineForTesting(LineStats line) {
        return sourceLine(line.systemId(), line.line());
    }
}
