package org.fxt.freexmltoolkit.controls.shell.editor.search;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.PanelProgress;
import org.fxt.freexmltoolkit.controls.shell.editor.PanelStatus;
import org.fxt.freexmltoolkit.controls.shell.editor.search.ReplaceApplier.ReplaceOutcome;
import org.fxt.freexmltoolkit.controls.shell.editor.search.XPathBatchRunner.FileXPathResult;
import org.fxt.freexmltoolkit.service.xmledit.EditPlan;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.NodeMatch;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.XPathEditException;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.XPathQuery;
import org.fxt.freexmltoolkit.service.xmledit.XPathReplacePlanner;
import org.fxt.freexmltoolkit.service.xmledit.XPathReplacePlanner.ReplaceMode;

/**
 * XPath mode of the Search panel: evaluate an XPath 3.1 expression against the
 * active document or a folder, list the matched nodes, and replace them — set a
 * literal value, compute a value per match, delete the nodes, or replace them
 * with an XML fragment. All edits are formatting-preserving text-range edits
 * ({@link XPathReplacePlanner}); open documents get exactly one undo step.
 */
public class XPathSearchPane extends VBox {

    private final EditorHost editorHost;
    private final Supplier<Path> workspaceRoot;

    @FXML private ToggleGroup scope;
    @FXML private TextArea xpathArea;
    @FXML private TextArea nsArea;
    @FXML private ToggleButton currentDocToggle;
    @FXML private ToggleButton folderToggle;
    @FXML private Label folderLabel;
    @FXML private TextField globField;
    @FXML private HBox folderRow;
    @FXML private ChoiceBox<String> modeChoice;
    @FXML private TextArea argumentArea;
    @FXML private Button applyButton;
    @FXML private Label status;
    @FXML private PanelProgress progress;
    @FXML private SearchResultsTree resultsTree;

    private static final String[] MODE_LABELS = {
            "Set value", "Compute value (XPath)", "Delete nodes", "Replace with XML"};
    private static final ReplaceMode[] MODES = {
            ReplaceMode.SET_VALUE, ReplaceMode.COMPUTE_VALUE,
            ReplaceMode.DELETE, ReplaceMode.REPLACE_FRAGMENT};

    /** Explicit folder chosen via Browse; falls back to the Explorer workspace. */
    private Path chosenFolder;
    private int generation;
    private AtomicBoolean cancelled = new AtomicBoolean();
    /** Snapshot of the last successful search (exactly one of the two is set). */
    private String docSnapshot;
    private List<FileXPathResult> batchResults;
    private XPathQuery lastQuery;

    public XPathSearchPane(EditorHost editorHost, Supplier<Path> workspaceRoot) {
        this.editorHost = editorHost;
        this.workspaceRoot = workspaceRoot;
        FxmlViews.load(this, "/pages/panels/XPathSearchPane.fxml");

        nsArea.setText(loadPref("search.xpath.namespaces", ""));
        globField.setText(loadPref("search.xpath.glob", "*.xml"));
        scope.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                scope.selectToggle(oldV != null ? oldV : currentDocToggle);
                return;
            }
            boolean folder = newV == folderToggle;
            folderRow.setVisible(folder);
            folderRow.setManaged(folder);
            globField.setVisible(folder);
            globField.setManaged(folder);
            refreshFolderLabel();
        });
        currentDocToggle.setSelected(true);
        folderRow.setVisible(false);
        folderRow.setManaged(false);
        globField.setVisible(false);
        globField.setManaged(false);

        modeChoice.getItems().addAll(MODE_LABELS);
        modeChoice.getSelectionModel().select(0);
        modeChoice.getSelectionModel().selectedIndexProperty().addListener(
                (obs, oldV, newV) -> refreshArgumentPrompt(newV.intValue()));
        refreshArgumentPrompt(0);
        resultsTree.setEmptyHint("Results appear here. Enter an XPath expression above to find matching nodes in all XML files of the folder.");
        resultsTree.setOnNavigate(this::navigateTo);
        refreshFolderLabel();
    }

    // ---------------------------------------------------------------------

    private ReplaceMode selectedMode() {
        return MODES[Math.max(0, modeChoice.getSelectionModel().getSelectedIndex())];
    }

    private void refreshArgumentPrompt(int modeIndex) {
        boolean delete = MODES[modeIndex] == ReplaceMode.DELETE;
        argumentArea.setVisible(!delete);
        argumentArea.setManaged(!delete);
        argumentArea.setPromptText(switch (MODES[modeIndex]) {
            case SET_VALUE -> "New value (literal text)";
            case COMPUTE_VALUE -> "Value expression per match, e.g. concat(., '-suffix')";
            case REPLACE_FRAGMENT -> "XML fragment, e.g. <NewElement>…</NewElement>";
            case DELETE -> "";
        });
    }

    private Map<String, String> parseNamespaces() {
        Map<String, String> namespaces = new LinkedHashMap<>();
        String text = nsArea.getText();
        if (text != null) {
            for (String line : text.split("\n")) {
                String trimmed = line.trim();
                int eq = trimmed.indexOf('=');
                if (trimmed.isEmpty() || trimmed.startsWith("#") || eq < 0) {
                    continue;
                }
                namespaces.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
            }
        }
        return namespaces;
    }

    @FXML
    public void onDetectNamespaces() {
        String text = editorHost.getActiveText().orElse(null);
        if (text == null || text.isBlank()) {
            PanelStatus.precondition(status, "Open an XML document to detect its namespaces");
            return;
        }
        Map<String, String> namespaces = XPathMatchLocator.extractRootNamespaces(text);
        if (namespaces.isEmpty()) {
            PanelStatus.info(status, "The document declares no namespaces");
            return;
        }
        StringBuilder sb = new StringBuilder();
        namespaces.forEach((prefix, uri) -> sb.append(prefix).append('=').append(uri).append('\n'));
        nsArea.setText(sb.toString().stripTrailing());
        savePref("search.xpath.namespaces", nsArea.getText());
        PanelStatus.success(status, namespaces.size() + " namespace binding(s) detected");
    }

    @FXML
    public void onChooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose folder");
        Path current = effectiveFolder();
        if (current != null) {
            chooser.setInitialDirectory(current.toFile());
        }
        var picked = chooser.showDialog(getScene() != null ? getScene().getWindow() : null);
        if (picked != null) {
            chosenFolder = picked.toPath();
            refreshFolderLabel();
        }
    }

    private Path effectiveFolder() {
        if (chosenFolder != null && Files.isDirectory(chosenFolder)) {
            return chosenFolder;
        }
        return workspaceRoot.get();
    }

    private void refreshFolderLabel() {
        Path folder = effectiveFolder();
        folderLabel.setText(folder != null ? String.valueOf(folder.getFileName()) : "No folder — choose one");
        folderLabel.setTooltip(new Tooltip(folder != null ? folder.toString() : "Choose the folder to search"));
        folderLabel.setTextOverrun(javafx.scene.control.OverrunStyle.CENTER_ELLIPSIS);
    }

    private List<String> globs() {
        String text = globField.getText();
        return text == null || text.isBlank() ? List.of("*.xml") : List.of(text.split(","));
    }

    // --- search ------------------------------------------------------------

    @FXML
    public void onFind() {
        runSearch();
    }

    private void runSearch() {
        String xpath = xpathArea.getText();
        if (xpath == null || xpath.isBlank()) {
            PanelStatus.precondition(status, "Enter an XPath expression");
            return;
        }
        XPathQuery query = new XPathQuery(xpath.strip(), parseNamespaces());
        savePref("search.xpath.namespaces", nsArea.getText() == null ? "" : nsArea.getText());
        savePref("search.xpath.glob", globField.getText() == null ? "*.xml" : globField.getText());
        generation++;
        int gen = generation;
        cancelled.set(true);
        cancelled = new AtomicBoolean(false);
        AtomicBoolean cancelFlag = cancelled;
        docSnapshot = null;
        batchResults = null;
        lastQuery = query;

        if (currentDocToggle.isSelected()) {
            var doc = editorHost.getActiveDocument().orElse(null);
            String text = editorHost.getActiveText().orElse(null);
            if (text == null || text.isBlank()) {
                PanelStatus.precondition(status, "Open an XML document first");
                return;
            }
            Path path = doc != null ? doc.getPath() : null;
            PanelStatus.info(status, "Evaluating…");
            progress.beginIndeterminate(() -> cancelFlag.set(true));
            FxtGui.executorService.submit(() -> {
                try {
                    List<NodeMatch> matches = XPathMatchLocator.locate(text, query);
                    Platform.runLater(() -> {
                        if (gen != generation) {
                            return;
                        }
                        progress.finish();
                        docSnapshot = text;
                        showDocResults(path, matches);
                    });
                } catch (XPathEditException e) {
                    Platform.runLater(() -> {
                        if (gen != generation) {
                            return;
                        }
                        progress.finish();
                        PanelStatus.precondition(status, e.getMessage());
                    });
                }
            });
        } else {
            Path root = effectiveFolder();
            if (root == null) {
                PanelStatus.precondition(status, "Open a workspace folder or choose one");
                return;
            }
            Map<Path, String> buffers = snapshotDirtyBuffers();
            PanelStatus.info(status, "Searching…");
            progress.beginIndeterminate(() -> cancelFlag.set(true));
            FxtGui.executorService.submit(() -> {
                List<FileXPathResult> results = XPathBatchRunner.search(
                        root, globs(), query, buffers::get, null, cancelFlag::get);
                Platform.runLater(() -> {
                    if (gen != generation) {
                        return;
                    }
                    progress.finish();
                    batchResults = results;
                    showBatchResults(results, cancelFlag.get());
                });
            });
        }
    }

    private void showDocResults(Path path, List<NodeMatch> matches) {
        Path display = path != null ? path
                : Path.of(editorHost.getActiveDocument()
                        .map(d -> d.getDisplayName()).orElse("untitled.xml"));
        resultsTree.setResults(List.of(toEntry(display, null, matches)));
        long located = matches.stream().filter(NodeMatch::located).count();
        PanelStatus.success(status, located + " match(es)"
                + (located < matches.size() ? " (" + (matches.size() - located) + " not locatable)" : ""));
        Map<Integer, Integer> ranges = new HashMap<>();
        for (NodeMatch match : matches) {
            if (match.located()) {
                ranges.put(match.span().start(), match.span().end() - match.span().start());
            }
        }
        if (ranges.isEmpty()) {
            editorHost.clearActiveSearchHighlights();
        } else {
            editorHost.setActiveSearchHighlights(ranges);
        }
    }

    private void showBatchResults(List<FileXPathResult> results, boolean wasCancelled) {
        List<SearchResultsTree.FileEntry> entries = new ArrayList<>();
        int total = 0;
        int errors = 0;
        for (FileXPathResult result : results) {
            entries.add(toEntry(result.file(), result.error(), result.matches()));
            total += (int) result.matches().stream().filter(NodeMatch::located).count();
            if (result.error() != null) {
                errors++;
            }
        }
        resultsTree.setResults(entries);
        String summary = total + " match(es) in "
                + results.stream().filter(r -> r.error() == null).count() + " file(s)";
        if (errors > 0) {
            summary += " (" + errors + " file(s) failed)";
        }
        if (wasCancelled) {
            summary += " — cancelled";
        }
        if (total == 0 && errors == 0) {
            PanelStatus.info(status, "No matches");
        } else {
            PanelStatus.success(status, summary);
        }
    }

    private SearchResultsTree.FileEntry toEntry(Path file, String error, List<NodeMatch> matches) {
        List<SearchResultsTree.MatchRow> rows = new ArrayList<>();
        int unlocated = 0;
        for (NodeMatch match : matches) {
            if (!match.located()) {
                unlocated++;
                continue;
            }
            rows.add(new SearchResultsTree.MatchRow(file, match.line(), match.preview(),
                    match.span().start(), match.span().end(), match.matchedText(), match));
        }
        String note = unlocated > 0
                ? unlocated + " match(es) could not be located in the text and are excluded"
                : null;
        return new SearchResultsTree.FileEntry(file, error, false, note, rows);
    }

    private void navigateTo(SearchResultsTree.MatchRow match) {
        if (docSnapshot != null) {
            // current-document mode: select in the active editor
            Platform.runLater(() -> {
                var codeArea = editorHost.getActiveCodeArea();
                String current = editorHost.getActiveText().orElse(null);
                if (codeArea != null && current != null && current.equals(docSnapshot)) {
                    codeArea.selectRange(match.start(), match.end());
                    codeArea.requestFollowCaret();
                } else {
                    editorHost.goToLine(match.lineNumber());
                }
            });
        } else if (match.file() != null) {
            Platform.runLater(() -> editorHost.openFileAndSelect(match.file(),
                    match.start(), match.end(), match.matched(), match.lineNumber()));
        }
    }

    private Map<Path, String> snapshotDirtyBuffers() {
        Map<Path, String> buffers = new HashMap<>();
        for (var doc : editorHost.getOpenDocuments()) {
            Path path = doc.getPath();
            if (path != null && doc.isDirty()) {
                editorHost.getDocumentText(doc).ifPresent(text -> buffers.put(path, text));
            }
        }
        return buffers;
    }

    // --- apply -------------------------------------------------------------

    @FXML
    public void onApply() {
        List<SearchResultsTree.MatchRow> checked = resultsTree.getCheckedMatches();
        if (checked.isEmpty() || lastQuery == null) {
            PanelStatus.precondition(status, "Find matches first and keep at least one checked");
            return;
        }
        ReplaceMode mode = selectedMode();
        String argument = argumentArea.getText() == null ? "" : argumentArea.getText();
        if (mode != ReplaceMode.DELETE && argument.isBlank()) {
            PanelStatus.precondition(status, "Enter the replacement "
                    + (mode == ReplaceMode.REPLACE_FRAGMENT ? "fragment" : "value"));
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("XPath Replace");
        confirm.setHeaderText(modeChoice.getValue() + ": " + checked.size() + " match(es)");
        confirm.setContentText(docSnapshot != null
                ? "The active document is edited in place — one Ctrl+Z undoes everything."
                : "Open files are edited in the editor; the rest are written to disk.");
        if (confirm.showAndWait().filter(bt -> bt == ButtonType.OK).isEmpty()) {
            return;
        }
        if (docSnapshot != null) {
            applyToActiveDocument(checked, mode, argument);
        } else if (batchResults != null) {
            applyToBatch(checked, mode, argument);
        }
    }

    private void applyToActiveDocument(List<SearchResultsTree.MatchRow> checked,
                                       ReplaceMode mode, String argument) {
        String current = editorHost.getActiveText().orElse(null);
        if (current == null || !current.equals(docSnapshot)) {
            PanelStatus.precondition(status, "The document changed — run the search again");
            return;
        }
        try {
            List<NodeMatch> matches = checked.stream()
                    .map(row -> (NodeMatch) row.payload()).toList();
            var result = XPathReplacePlanner.plan(docSnapshot, lastQuery, mode, argument, matches);
            if (result.plan().isEmpty()) {
                PanelStatus.info(status, "Nothing to replace");
                return;
            }
            String merged = result.plan().mergedRegion(docSnapshot);
            editorHost.replaceActiveTextRegion(
                    result.plan().minStart(), result.plan().maxEnd(), merged);
            resultsTree.clearResults();
            editorHost.clearActiveSearchHighlights();
            docSnapshot = null;
            String summary = "Replaced " + result.plan().edits().size() + " match(es) — Ctrl+Z undoes";
            if (result.subsumed() > 0) {
                summary += " (" + result.subsumed() + " nested match(es) covered by their ancestor)";
            }
            PanelStatus.success(status, summary);
        } catch (XPathEditException e) {
            PanelStatus.failure(status, "XPath Replace", e.getMessage());
        }
    }

    private void applyToBatch(List<SearchResultsTree.MatchRow> checked,
                              ReplaceMode mode, String argument) {
        Map<Path, List<NodeMatch>> perFile = new HashMap<>();
        for (SearchResultsTree.MatchRow row : checked) {
            perFile.computeIfAbsent(row.file(), f -> new ArrayList<>())
                    .add((NodeMatch) row.payload());
        }
        Map<Path, FileXPathResult> resultsByFile = new HashMap<>();
        for (FileXPathResult result : batchResults) {
            resultsByFile.put(result.file(), result);
        }
        // Editor-buffer texts must be read on the FX thread; snapshot up front.
        Map<Path, String> openBuffers = new HashMap<>();
        for (Path file : perFile.keySet()) {
            editorHost.getOpenDocumentText(file).ifPresent(text -> openBuffers.put(file, text));
        }
        XPathQuery query = lastQuery;
        int gen = ++generation;
        progress.beginIndeterminate(null);
        FxtGui.executorService.submit(() -> {
            List<ReplaceOutcome> outcomes = new ArrayList<>();
            record EditorApply(Path file, FileXPathResult result, EditPlan plan) {
            }
            List<EditorApply> editorApplies = new ArrayList<>();
            for (Map.Entry<Path, List<NodeMatch>> entry : perFile.entrySet()) {
                Path file = entry.getKey();
                FileXPathResult result = resultsByFile.get(file);
                if (result == null || result.error() != null) {
                    continue;
                }
                try {
                    var planned = XPathReplacePlanner.plan(result.baseText(), query, mode,
                            argument, entry.getValue());
                    if (planned.plan().isEmpty()) {
                        continue;
                    }
                    if (openBuffers.containsKey(file)) {
                        editorApplies.add(new EditorApply(file, result, planned.plan()));
                    } else {
                        outcomes.add(XPathBatchRunner.applyToDisk(result, planned.plan()));
                    }
                } catch (XPathEditException e) {
                    outcomes.add(new ReplaceOutcome(file, 0, false, e.getMessage()));
                }
            }
            Platform.runLater(() -> {
                if (gen != generation) {
                    return;
                }
                for (EditorApply apply : editorApplies) {
                    String current = editorHost.getOpenDocumentText(apply.file()).orElse(null);
                    if (current == null || !current.equals(apply.result().baseText())) {
                        outcomes.add(new ReplaceOutcome(apply.file(), 0, true,
                                "Document changed while replacing — skipped"));
                        continue;
                    }
                    String merged = apply.plan().mergedRegion(apply.result().baseText());
                    boolean ok = editorHost.replaceDocumentTextRegion(apply.file(),
                            apply.plan().minStart(), apply.plan().maxEnd(), merged);
                    outcomes.add(new ReplaceOutcome(apply.file(),
                            ok ? apply.plan().edits().size() : 0, true,
                            ok ? null : "Editor tab no longer open — skipped"));
                }
                progress.finish();
                resultsTree.clearResults();
                editorHost.clearActiveSearchHighlights();
                batchResults = null;
                int applied = outcomes.stream().mapToInt(ReplaceOutcome::applied).sum();
                List<String> problems = outcomes.stream()
                        .filter(o -> o.error() != null)
                        .map(o -> o.file().getFileName() + ": " + o.error())
                        .toList();
                if (problems.isEmpty()) {
                    PanelStatus.success(status, "Replaced " + applied + " match(es) in "
                            + outcomes.size() + " file(s)");
                } else {
                    PanelStatus.failure(status, "XPath Replace",
                            "Replaced " + applied + " match(es); " + problems.size()
                                    + " file(s) skipped:\n" + String.join("\n", problems));
                }
            });
        });
    }

    // --- persistence (registry may be absent in tests) ----------------------

    private static String loadPref(String key, String fallback) {
        try {
            String value = org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class).get(key);
            return value != null ? value : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static void savePref(String key, String value) {
        try {
            org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class).set(key, value);
        } catch (Throwable ignored) {
            // properties service unavailable — nothing to persist
        }
    }
}
