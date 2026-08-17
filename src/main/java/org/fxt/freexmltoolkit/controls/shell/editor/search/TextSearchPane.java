package org.fxt.freexmltoolkit.controls.shell.editor.search;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.PatternSyntaxException;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.OpenDocument;
import org.fxt.freexmltoolkit.controls.shell.editor.PanelProgress;
import org.fxt.freexmltoolkit.controls.shell.editor.PanelStatus;
import org.fxt.freexmltoolkit.controls.shell.editor.search.FileSearchRunner.FileSearchResult;
import org.fxt.freexmltoolkit.controls.shell.editor.search.FileSearchRunner.TextMatch;
import org.fxt.freexmltoolkit.controls.shell.editor.search.FileSearchRunner.TextSearchQuery;
import org.fxt.freexmltoolkit.controls.shell.editor.search.ReplaceApplier.FileReplacePlan;
import org.fxt.freexmltoolkit.controls.shell.editor.search.ReplaceApplier.ReplaceOutcome;

/**
 * Text mode of the Search panel: VS-Code-style find/replace across all files of
 * a folder (default: the Explorer workspace). Search runs off the UI thread with
 * a generation guard; matches are individually checkable and replacement is
 * applied through open editor buffers (one undo step per document) or atomically
 * on disk, both planned by {@link ReplaceApplier}.
 */
public class TextSearchPane extends VBox {

    private final EditorHost editorHost;
    private final Supplier<Path> workspaceRoot;

    private final TextField queryField = new TextField();
    private final ToggleButton caseToggle = optionToggle("Aa", "Match case");
    private final ToggleButton wordToggle = optionToggle("W", "Whole word");
    private final ToggleButton regexToggle = optionToggle(".*", "Regular expression");
    private final TextField replaceField = new TextField();
    private final Button replaceButton = new Button("Replace…");
    private final VBox replaceRow = new VBox(4);
    private final Label folderLabel = new Label("(no folder)");
    private final TextField globField = new TextField();
    private final ToggleButton replaceToggle = optionToggle("⇄", "Toggle replace");
    private final Label status = new Label("Type to search");
    private final PanelProgress progress = new PanelProgress();
    private final SearchResultsTree resultsTree = new SearchResultsTree();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(400));

    /** Explicit folder chosen via Browse; when null the Explorer workspace is used. */
    private Path chosenFolder;
    /** Monotonic run id so stale async results never render (QueryConsole pattern). */
    private int generation;
    private AtomicBoolean cancelled = new AtomicBoolean();
    /** Snapshot backing the results tree — replace re-verifies against these. */
    private List<FileSearchResult> lastResults;
    private TextSearchQuery lastQuery;

    public TextSearchPane(EditorHost editorHost, Supplier<Path> workspaceRoot) {
        this.editorHost = editorHost;
        this.workspaceRoot = workspaceRoot;
        getStyleClass().add("fxt-search-text-pane");
        setSpacing(6);

        // --- query row --------------------------------------------------------
        queryField.setId("search-query");
        queryField.setPromptText("Search");
        HBox.setHgrow(queryField, Priority.ALWAYS);
        HBox queryRow = new HBox(4, queryField, caseToggle, wordToggle, regexToggle);
        queryRow.setAlignment(Pos.CENTER_LEFT);

        // --- replace row (collapsed until requested) --------------------------
        replaceField.setId("search-replace");
        replaceField.setPromptText("Replace");
        replaceButton.setId("search-replace-button");
        replaceButton.getStyleClass().add("fxt-primary-button");
        replaceButton.setMaxWidth(Double.MAX_VALUE);
        replaceButton.setOnAction(e -> onReplaceChecked());
        replaceRow.getChildren().addAll(replaceField, replaceButton);
        setReplaceRowVisible(false);
        replaceToggle.setId("search-replace-toggle");
        replaceToggle.selectedProperty().addListener(
                (obs, oldV, newV) -> setReplaceRowVisible(newV));
        queryRow.getChildren().add(replaceToggle);

        // --- scope: folder + glob --------------------------------------------
        folderLabel.setId("search-folder");
        folderLabel.getStyleClass().add("fxt-vp-source-name");
        Button browse = new Button();
        browse.setGraphic(new IconifyIcon("bi-folder2-open"));
        browse.getStyleClass().add("fxt-tool-button");
        browse.setTooltip(new Tooltip("Choose folder to search"));
        browse.setOnAction(e -> chooseFolder());
        HBox.setHgrow(folderLabel, Priority.ALWAYS);
        folderLabel.setMaxWidth(Double.MAX_VALUE);
        HBox folderRow = new HBox(6, folderLabel, browse);
        folderRow.setAlignment(Pos.CENTER_LEFT);
        globField.setId("search-glob");
        globField.setPromptText("File globs, e.g. *.xml,*.xsd");
        globField.setText(loadPref("search.glob", String.join(",", FileSearchRunner.DEFAULT_GLOBS)));

        // --- status + progress + results -------------------------------------
        status.getStyleClass().add("fxt-vp-status");
        status.setWrapText(true);
        VBox.setVgrow(resultsTree, Priority.ALWAYS);
        resultsTree.setOnNavigate(match -> Platform.runLater(() ->
                editorHost.openFileAndSelect(match.file(), match.start(), match.end(),
                        match.matched(), match.lineNumber())));

        getChildren().addAll(queryRow, replaceRow, folderRow, globField, status, progress, resultsTree);

        // --- behavior ---------------------------------------------------------
        debounce.setOnFinished(e -> runSearch());
        queryField.textProperty().addListener((obs, oldV, newV) -> debounce.playFromStart());
        queryField.setOnAction(e -> {
            debounce.stop();
            runSearch();
        });
        globField.setOnAction(e -> runSearch());
        for (ToggleButton toggle : List.of(caseToggle, wordToggle, regexToggle)) {
            toggle.selectedProperty().addListener((obs, oldV, newV) -> {
                savePrefs();
                runSearch();
            });
        }
        caseToggle.setSelected(Boolean.parseBoolean(loadPref("search.caseSensitive", "false")));
        wordToggle.setSelected(Boolean.parseBoolean(loadPref("search.wholeWord", "false")));
        regexToggle.setSelected(Boolean.parseBoolean(loadPref("search.regex", "false")));
        String lastFolder = loadPref("search.lastFolder", null);
        if (lastFolder != null && Files.isDirectory(Path.of(lastFolder))) {
            chosenFolder = Path.of(lastFolder);
        }
        refreshFolderLabel();
    }

    // ---------------------------------------------------------------------

    /** Focuses the query field, optionally prefilling it and expanding the replace row. */
    public void focusQuery(String prefill, boolean showReplace) {
        if (prefill != null && !prefill.isBlank()) {
            queryField.setText(prefill);
        }
        if (showReplace) {
            replaceToggle.setSelected(true);
        }
        Platform.runLater(() -> {
            queryField.requestFocus();
            queryField.selectAll();
        });
    }

    private void setReplaceRowVisible(boolean visible) {
        replaceRow.setVisible(visible);
        replaceRow.setManaged(visible);
    }

    private static ToggleButton optionToggle(String text, String tooltip) {
        ToggleButton toggle = new ToggleButton(text);
        toggle.getStyleClass().addAll("fxt-tool-button", "fxt-search-option-toggle");
        toggle.setTooltip(new Tooltip(tooltip));
        return toggle;
    }

    private void chooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose folder to search");
        Path current = effectiveFolder();
        if (current != null) {
            chooser.setInitialDirectory(current.toFile());
        }
        var picked = chooser.showDialog(getScene() != null ? getScene().getWindow() : null);
        if (picked != null) {
            chosenFolder = picked.toPath();
            savePref("search.lastFolder", chosenFolder.toString());
            refreshFolderLabel();
            runSearch();
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
        folderLabel.setText(folder != null ? folder.getFileName() + "  (" + folder + ")" : "(no folder)");
        folderLabel.setTooltip(folder != null ? new Tooltip(folder.toString()) : null);
    }

    private List<String> globs() {
        String text = globField.getText();
        if (text == null || text.isBlank()) {
            return FileSearchRunner.DEFAULT_GLOBS;
        }
        return List.of(text.split(","));
    }

    // --- search ------------------------------------------------------------

    private void runSearch() {
        generation++;
        int gen = generation;
        cancelled.set(true); // stop a previous run
        cancelled = new AtomicBoolean(false);
        AtomicBoolean cancelFlag = cancelled;
        refreshFolderLabel();

        String pattern = queryField.getText();
        if (pattern == null || pattern.isEmpty()) {
            resultsTree.clearResults();
            lastResults = null;
            editorHost.clearActiveSearchHighlights();
            PanelStatus.info(status, "Type to search");
            progress.finish();
            return;
        }
        Path root = effectiveFolder();
        if (root == null) {
            PanelStatus.precondition(status, "Open a workspace folder or choose one to search");
            return;
        }
        TextSearchQuery query = new TextSearchQuery(pattern, caseToggle.isSelected(),
                wordToggle.isSelected(), regexToggle.isSelected(), globs());
        try {
            FileSearchRunner.compile(query);
        } catch (PatternSyntaxException e) {
            PanelStatus.precondition(status, "Invalid regular expression: " + e.getDescription());
            return;
        }
        savePrefs();

        // Dirty open documents are searched via their live buffer (snapshotted on FX thread).
        Map<Path, String> buffers = snapshotBuffers(true);
        PanelStatus.info(status, "Searching…");
        progress.beginIndeterminate(() -> cancelFlag.set(true));
        FxtGui.executorService.submit(() -> {
            List<FileSearchResult> results =
                    FileSearchRunner.search(root, query, buffers::get, null, cancelFlag::get);
            Platform.runLater(() -> {
                if (gen != generation) {
                    return; // a newer search superseded this run
                }
                progress.finish();
                lastResults = results;
                lastQuery = query;
                showResults(results, cancelFlag.get());
            });
        });
    }

    private void showResults(List<FileSearchResult> results, boolean wasCancelled) {
        List<SearchResultsTree.FileEntry> entries = new ArrayList<>();
        int totalMatches = 0;
        int fileErrors = 0;
        for (FileSearchResult result : results) {
            List<SearchResultsTree.MatchRow> rows = new ArrayList<>();
            for (TextMatch match : result.matches()) {
                String matched = null;
                int inLine = match.start() - match.lineStart();
                if (inLine >= 0 && match.end() - match.lineStart() <= match.lineText().length()) {
                    matched = match.lineText().substring(inLine, match.end() - match.lineStart());
                }
                rows.add(new SearchResultsTree.MatchRow(result.file(), match.lineNumber(),
                        match.lineText(), match.start(), match.end(), matched, match));
            }
            totalMatches += rows.size();
            if (result.error() != null) {
                fileErrors++;
            }
            entries.add(new SearchResultsTree.FileEntry(
                    result.file(), result.error(), result.truncated(), rows));
        }
        resultsTree.setResults(entries);
        long fileCount = results.stream().filter(r -> r.error() == null).count();
        String summary = totalMatches + (totalMatches == 1 ? " match in " : " matches in ")
                + fileCount + (fileCount == 1 ? " file" : " files");
        if (fileErrors > 0) {
            summary += " (" + fileErrors + " unreadable)";
        }
        if (wasCancelled) {
            summary += " — cancelled";
        }
        if (totalMatches == 0 && fileErrors == 0) {
            PanelStatus.info(status, "No matches");
        } else {
            PanelStatus.success(status, summary);
        }
        highlightActiveDocument(results);
    }

    /** Overlays the matches of the active document (if it is among the results). */
    private void highlightActiveDocument(List<FileSearchResult> results) {
        Path activePath = editorHost.getActiveDocument().map(OpenDocument::getPath).orElse(null);
        Map<Integer, Integer> ranges = new HashMap<>();
        if (activePath != null) {
            for (FileSearchResult result : results) {
                if (activePath.equals(result.file())) {
                    for (TextMatch match : result.matches()) {
                        ranges.put(match.start(), match.end() - match.start());
                    }
                }
            }
        }
        if (ranges.isEmpty()) {
            editorHost.clearActiveSearchHighlights();
        } else {
            editorHost.setActiveSearchHighlights(ranges);
        }
    }

    /** Snapshots open-document buffer texts on the FX thread (dirty-only or all). */
    private Map<Path, String> snapshotBuffers(boolean dirtyOnly) {
        Map<Path, String> buffers = new HashMap<>();
        for (OpenDocument doc : editorHost.getOpenDocuments()) {
            Path path = doc.getPath();
            if (path == null || (dirtyOnly && !doc.isDirty())) {
                continue;
            }
            editorHost.getDocumentText(doc).ifPresent(text -> buffers.put(path, text));
        }
        return buffers;
    }

    // --- replace -----------------------------------------------------------

    private void onReplaceChecked() {
        if (lastResults == null || lastQuery == null) {
            PanelStatus.precondition(status, "Run a search first");
            return;
        }
        List<SearchResultsTree.MatchRow> checked = resultsTree.getCheckedMatches();
        if (checked.isEmpty()) {
            PanelStatus.precondition(status, "No matches checked");
            return;
        }
        String template = replaceField.getText() == null ? "" : replaceField.getText();
        Map<Path, Set<Integer>> checkedStarts = new HashMap<>();
        for (SearchResultsTree.MatchRow row : checked) {
            checkedStarts.computeIfAbsent(row.file(), f -> new HashSet<>()).add(row.start());
        }
        long editorFiles = checkedStarts.keySet().stream()
                .filter(p -> editorHost.getOpenDocumentText(p).isPresent()).count();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Replace in Files");
        confirm.setHeaderText("Replace " + checked.size() + " match(es) in "
                + checkedStarts.size() + " file(s)?");
        confirm.setContentText(editorFiles > 0
                ? editorFiles + " file(s) are open and will be edited in the editor (undo with Ctrl+Z, "
                + "then save); the rest are written to disk."
                : "The files will be modified on disk.");
        if (confirm.showAndWait().filter(bt -> bt == ButtonType.OK).isEmpty()) {
            return;
        }

        // Snapshot all open buffers on the FX thread; planning runs in the background.
        Map<Path, String> openBuffers = snapshotBuffers(false);
        List<FileSearchResult> results = lastResults;
        TextSearchQuery query = lastQuery;
        int gen = ++generation;
        progress.beginIndeterminate(null);
        FxtGui.executorService.submit(() -> {
            List<FileReplacePlan> plans = ReplaceApplier.plan(
                    results, checkedStarts, query, template, openBuffers::get);
            List<ReplaceOutcome> outcomes = new ArrayList<>();
            List<FileReplacePlan> editorPlans = new ArrayList<>();
            for (FileReplacePlan plan : plans) {
                if (plan.viaEditor()) {
                    editorPlans.add(plan);
                } else {
                    outcomes.add(ReplaceApplier.applyToDisk(plan));
                }
            }
            Platform.runLater(() -> {
                if (gen != generation) {
                    return;
                }
                for (FileReplacePlan plan : editorPlans) {
                    outcomes.add(applyViaEditor(plan));
                }
                progress.finish();
                resultsTree.clearResults();
                lastResults = null;
                editorHost.clearActiveSearchHighlights();
                showReplaceSummary(outcomes);
            });
        });
    }

    /** Applies one plan through the open editor buffer — exactly one undo step. */
    private ReplaceOutcome applyViaEditor(FileReplacePlan plan) {
        if (plan.error() != null) {
            return new ReplaceOutcome(plan.file(), 0, true, plan.error());
        }
        // Staleness guard: the buffer must still equal the planning snapshot.
        String current = editorHost.getOpenDocumentText(plan.file()).orElse(null);
        if (current == null || !current.equals(plan.baseText())) {
            return new ReplaceOutcome(plan.file(), 0, true,
                    "Document changed while replacing — skipped");
        }
        String merged = plan.plan().mergedRegion(plan.baseText());
        boolean ok = editorHost.replaceDocumentTextRegion(plan.file(),
                plan.plan().minStart(), plan.plan().maxEnd(), merged);
        return ok
                ? new ReplaceOutcome(plan.file(), plan.plan().edits().size(), true, null)
                : new ReplaceOutcome(plan.file(), 0, true, "Editor tab no longer open — skipped");
    }

    private void showReplaceSummary(List<ReplaceOutcome> outcomes) {
        int applied = outcomes.stream().mapToInt(ReplaceOutcome::applied).sum();
        long okFiles = outcomes.stream().filter(o -> o.error() == null).count();
        List<String> problems = outcomes.stream()
                .filter(o -> o.error() != null)
                .map(o -> o.file().getFileName() + ": " + o.error())
                .toList();
        if (problems.isEmpty()) {
            PanelStatus.success(status, "Replaced " + applied + " match(es) in " + okFiles + " file(s)");
        } else {
            PanelStatus.failure(status, "Replace in Files",
                    "Replaced " + applied + " match(es) in " + okFiles + " file(s); "
                            + problems.size() + " file(s) skipped:\n"
                            + String.join("\n", problems));
        }
    }

    // --- persistence (registry may be absent in tests) ----------------------

    private void savePrefs() {
        savePref("search.caseSensitive", String.valueOf(caseToggle.isSelected()));
        savePref("search.wholeWord", String.valueOf(wordToggle.isSelected()));
        savePref("search.regex", String.valueOf(regexToggle.isSelected()));
        String glob = globField.getText();
        if (glob != null && !glob.isBlank()) {
            savePref("search.glob", glob);
        }
    }

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
