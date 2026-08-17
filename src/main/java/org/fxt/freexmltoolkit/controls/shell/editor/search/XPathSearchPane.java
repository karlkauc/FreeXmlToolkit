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
import javafx.geometry.Pos;
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
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
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

    private final TextArea xpathArea = new TextArea();
    private final TextArea nsArea = new TextArea();
    private final ToggleButton currentDocToggle = new ToggleButton("Document");
    private final ToggleButton folderToggle = new ToggleButton("Folder");
    private final Label folderLabel = new Label("(no folder)");
    private final TextField globField = new TextField();
    private final HBox folderRow;
    private final ChoiceBox<String> modeChoice = new ChoiceBox<>();
    private final TextArea argumentArea = new TextArea();
    private final Button applyButton = new Button("Replace checked…");
    private final Label status = new Label("Enter an XPath and find matches");
    private final PanelProgress progress = new PanelProgress();
    private final SearchResultsTree resultsTree = new SearchResultsTree();

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
        getStyleClass().add("fxt-search-xpath-pane");
        setSpacing(6);

        // --- XPath input ------------------------------------------------------
        xpathArea.setId("xpath-query");
        xpathArea.setPromptText("//Amount[@ccy='EUR']");
        xpathArea.setPrefRowCount(2);
        xpathArea.getStyleClass().add("fxt-query-input");
        xpathArea.setWrapText(true);

        // --- namespaces -------------------------------------------------------
        nsArea.setId("xpath-namespaces");
        nsArea.setPromptText("prefix=uri per line (empty prefix = default namespace)");
        nsArea.setPrefRowCount(2);
        nsArea.setText(loadPref("search.xpath.namespaces", ""));
        Button detect = new Button("Detect");
        detect.getStyleClass().add("fxt-tool-button");
        detect.setTooltip(new Tooltip("Read the namespace bindings from the active document's root"));
        detect.setOnAction(e -> detectNamespaces());
        Label nsLabel = new Label("Namespaces");
        nsLabel.getStyleClass().add("fxt-sp-section-label");
        Region nsSpacer = new Region();
        HBox.setHgrow(nsSpacer, Priority.ALWAYS);
        HBox nsHeader = new HBox(6, nsLabel, nsSpacer, detect);
        nsHeader.setAlignment(Pos.CENTER_LEFT);

        // --- scope ------------------------------------------------------------
        ToggleGroup scope = new ToggleGroup();
        for (ToggleButton toggle : new ToggleButton[]{currentDocToggle, folderToggle}) {
            toggle.setToggleGroup(scope);
            toggle.getStyleClass().addAll("fxt-tool-button", "fxt-search-mode-toggle");
            toggle.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(toggle, Priority.ALWAYS);
        }
        currentDocToggle.setId("xpath-scope-doc");
        folderToggle.setId("xpath-scope-folder");
        HBox scopeRow = new HBox(4, currentDocToggle, folderToggle);
        folderLabel.getStyleClass().add("fxt-vp-source-name");
        folderLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(folderLabel, Priority.ALWAYS);
        Button browse = new Button();
        browse.setGraphic(new IconifyIcon("bi-folder2-open"));
        browse.getStyleClass().add("fxt-tool-button");
        browse.setTooltip(new Tooltip("Choose folder"));
        browse.setOnAction(e -> chooseFolder());
        folderRow = new HBox(6, folderLabel, browse);
        folderRow.setAlignment(Pos.CENTER_LEFT);
        globField.setId("xpath-glob");
        globField.setPromptText("File globs, e.g. *.xml");
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

        // --- find -------------------------------------------------------------
        Button find = new Button("Find matches");
        find.setId("xpath-find");
        find.getStyleClass().add("fxt-primary-button");
        find.setMaxWidth(Double.MAX_VALUE);
        find.setOnAction(e -> runSearch());

        // --- replace section --------------------------------------------------
        modeChoice.getItems().addAll(MODE_LABELS);
        modeChoice.getSelectionModel().select(0);
        modeChoice.setMaxWidth(Double.MAX_VALUE);
        modeChoice.setId("xpath-mode");
        argumentArea.setId("xpath-argument");
        argumentArea.setPrefRowCount(2);
        argumentArea.setWrapText(true);
        modeChoice.getSelectionModel().selectedIndexProperty().addListener(
                (obs, oldV, newV) -> refreshArgumentPrompt(newV.intValue()));
        refreshArgumentPrompt(0);
        applyButton.setId("xpath-apply");
        applyButton.getStyleClass().add("fxt-primary-button");
        applyButton.setMaxWidth(Double.MAX_VALUE);
        applyButton.setOnAction(e -> onApply());
        Label replaceLabel = new Label("Replace");
        replaceLabel.getStyleClass().add("fxt-sp-section-label");

        status.getStyleClass().add("fxt-vp-status");
        status.setWrapText(true);
        VBox.setVgrow(resultsTree, Priority.ALWAYS);
        resultsTree.setOnNavigate(this::navigateTo);

        getChildren().addAll(xpathArea, nsHeader, nsArea, scopeRow, folderRow, globField, find,
                replaceLabel, modeChoice, argumentArea, applyButton, status, progress, resultsTree);
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

    private void detectNamespaces() {
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

    private void chooseFolder() {
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
        folderLabel.setText(folder != null ? folder.getFileName() + "  (" + folder + ")" : "(no folder)");
        folderLabel.setTooltip(folder != null ? new Tooltip(folder.toString()) : null);
    }

    private List<String> globs() {
        String text = globField.getText();
        return text == null || text.isBlank() ? List.of("*.xml") : List.of(text.split(","));
    }

    // --- search ------------------------------------------------------------

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

    private void onApply() {
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
