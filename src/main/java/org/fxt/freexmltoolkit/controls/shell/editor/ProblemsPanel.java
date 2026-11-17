package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;
import java.util.stream.Collectors;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.editor.quickfix.QuickFixMenuFactory;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.util.ProjectLinks;

/**
 * The PROBLEMS panel below the editor (Figma "Redesign · Unified — Validation",
 * node 42:3): a header bar with severity counters and a collapse toggle, plus
 * one row per {@link ValidationProblem} of the active document
 * ({@link EditorHost#getActiveProblems()}). Clicking a row jumps to its line; right-click
 * (or Ctrl+C) copies a problem's text, which the row may cut off. A click on the error or
 * warning counter shows only that severity; a second click shows all problems again.
 * The panel hides itself entirely while there are no problems and re-appears
 * automatically when validation reports some.
 */
public class ProblemsPanel extends VBox {

    private static final KeyCombination COPY = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
    /** Occurrences shown under one tree node; the rest is one "show in list" row. */
    static final int MAX_LEAVES = 2_000;

    private final EditorHost editorHost;
    private final ObservableList<ValidationProblem> items = FXCollections.observableArrayList();
    @FXML private ListView<ValidationProblem> list;
    @FXML private TreeView<Object> tree;
    @FXML private javafx.scene.layout.StackPane content;
    @FXML private ToggleButton errorChip;
    @FXML private ToggleButton warningChip;
    @FXML private ToggleButton hintChip;
    @FXML private MenuButton categoryMenu;
    @FXML private TextField search;
    @FXML private HBox focusBox;
    @FXML private Label focusLabel;
    @FXML private ComboBox<GroupChoice> groupBy;
    @FXML private MenuButton exportMenu;

    /** Which problems the list shows. */
    enum Filter { ALL, ERRORS, WARNINGS, HINTS }

    private Filter filter = Filter.ALL;
    /** Categories switched off in the category menu. */
    private final java.util.EnumSet<ProblemCategory> hiddenCategories = java.util.EnumSet.noneOf(ProblemCategory.class);
    /** Problems per category of the active document (all, unfiltered) - the category menu's counts. */
    private java.util.Map<ProblemCategory, Integer> categoryCounts = java.util.Map.of();
    private String query = "";
    /** A group the user chose "show in list" for: its description and test, or {@code null}. */
    private String focusText;
    private java.util.function.Predicate<ValidationProblem> focus;
    private final javafx.animation.PauseTransition searchDelay =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
    /** Counts every regrouping; an older background grouping is dropped. */
    private long groupingRun;

    @FXML private IconifyIcon collapseIcon;
    @FXML private javafx.scene.layout.Region resizer;
    private boolean collapsed;
    private double expandedHeight;

    /** Properties key of the persisted height. */
    static final String HEIGHT_KEY = "editor.problems.height";
    /** Properties key of the chosen grouping. */
    static final String GROUP_KEY = "editor.problems.groupBy";
    static final double DEFAULT_HEIGHT = 220;
    /** Resize handle, header bar and about two rows. */
    static final double MIN_HEIGHT = 96;

    /** An entry of the "group by" box: none (the list) or a grouping. */
    record GroupChoice(ProblemGroups.By by, String text) {
        @Override
        public String toString() {
            return text;
        }
    }

    /** Loads the layout from {@code /pages/panels/ProblemsPanel.fxml} and binds the list to the host's problems. */
    public ProblemsPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        FxmlViews.load(this, "/pages/panels/ProblemsPanel.fxml");

        list.setItems(items);
        // A fixed height that the user drags (instead of the list's preferred height): on a small
        // screen the editor above took all the room and pushed the list out of the window.
        DockResizer resizing = new DockResizer(this, resizer, HEIGHT_KEY, MIN_HEIGHT, DEFAULT_HEIGHT);
        expandedHeight = resizing.load();
        setMinHeight(MIN_HEIGHT);
        setPrefHeight(expandedHeight);
        prefHeightProperty().addListener((obs, oldV, newV) -> {
            if (!collapsed) {
                expandedHeight = newV.doubleValue();
            }
        });
        list.setCellFactory(lv -> new ProblemRowCell());
        // Ctrl+C / Cmd+C copies the selected problem like the row menu's "Copy Problem"
        list.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (COPY.match(e) && list.getSelectionModel().getSelectedItem() != null) {
                copyToClipboard(describe(list.getSelectionModel().getSelectedItem(), documentName()));
                e.consume();
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.line() > 0) {
                editorHost.revealProblem(newV.line(), newV.column(), newV.message());
            }
        });

        // grouped view: groups → kinds of problems → occurrences
        tree.setRoot(new TreeItem<>());
        tree.setCellFactory(tv -> new GroupCell());
        tree.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.getValue() instanceof ValidationProblem p && p.line() > 0) {
                editorHost.revealProblem(p.line(), p.column(), p.message());
            }
        });
        tree.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            TreeItem<Object> selected = tree.getSelectionModel().getSelectedItem();
            if (COPY.match(e) && selected != null) {
                copyToClipboard(describeAll(problemsOf(selected.getValue()), documentName()));
                e.consume();
            }
        });

        groupBy.getItems().setAll(
                new GroupChoice(null, I18n.t("ProblemsPanel.group-none")),
                new GroupChoice(ProblemGroups.By.SUBJECT, I18n.t("ProblemsPanel.group-subject")),
                new GroupChoice(ProblemGroups.By.RULE, I18n.t("ProblemsPanel.group-rule")),
                new GroupChoice(ProblemGroups.By.CATEGORY, I18n.t("ProblemsPanel.group-category")));
        groupBy.getSelectionModel().select(storedGrouping());
        groupBy.valueProperty().addListener((obs, oldV, newV) -> {
            storeGrouping(newV);
            refresh();
        });
        categoryMenu.setOnShowing(e -> fillCategoryMenu());
        search.textProperty().addListener((obs, oldV, newV) -> searchDelay.playFromStart());
        searchDelay.setOnFinished(e -> {
            query = search.getText() == null ? "" : search.getText().strip().toLowerCase(java.util.Locale.ROOT);
            refresh();
        });

        editorHost.getActiveProblems().addListener(
                (ListChangeListener<ValidationProblem>) c -> refresh());
        editorHost.problemsRevealRequestsProperty().addListener((obs, oldV, newV) -> {
            setCollapsed(false);
            list.requestFocus();
            if (items.isEmpty()) {
                return;
            }
            // Every request shows a problem in the editor: with the dock already open, only expanding
            // and focusing changed nothing visible (telemetry: "show problems" clicked 13 times in a row).
            ValidationProblem selected = list.getSelectionModel().getSelectedItem();
            if (selected == null) {
                list.getSelectionModel().select(0); // the selection listener reveals it
                list.getFocusModel().focus(0);
            } else if (selected.line() > 0) {
                editorHost.revealProblem(selected.line(), selected.column(), selected.message());
            }
            list.scrollTo(list.getSelectionModel().getSelectedIndex());
        });
        refresh();
    }

    @FXML
    public void onToggleCollapsed() {
        setCollapsed(!collapsed);
    }

    /** The error counter: only errors, or all problems again. */
    @FXML
    public void onFilterErrors() {
        setFilter(filter == Filter.ERRORS ? Filter.ALL : Filter.ERRORS);
    }

    /** The warning counter: only warnings, or all problems again. */
    @FXML
    public void onFilterWarnings() {
        setFilter(filter == Filter.WARNINGS ? Filter.ALL : Filter.WARNINGS);
    }

    /** The hint counter: only hints, or all problems again. */
    @FXML
    public void onFilterHints() {
        setFilter(filter == Filter.HINTS ? Filter.ALL : Filter.HINTS);
    }

    /** Leaves the group shown in the list ("show in list"): all problems again. */
    @FXML
    public void onClearFocus() {
        setFocus(null, null);
    }

    /** Export → the problems shown (severity, categories, search, group). */
    @FXML
    public void onExportShown() {
        export(List.copyOf(shownProblems()), filterDescription());
    }

    /** Export → every problem of the document. */
    @FXML
    public void onExportAll() {
        export(List.copyOf(editorHost.getActiveProblems()), null);
    }

    /** @return which problems the list shows */
    Filter getFilter() {
        return filter;
    }

    /** Shows all problems or only one severity. */
    void setFilter(Filter filter) {
        this.filter = filter;
        refresh();
    }

    /** Shows only the problems of one group in the list (or all again with {@code null}). */
    void setFocus(String text, java.util.function.Predicate<ValidationProblem> test) {
        this.focusText = test == null ? null : text;
        this.focus = test;
        focusLabel.setText(text == null ? "" : text);
        focusBox.setVisible(test != null);
        focusBox.setManaged(test != null);
        refresh();
    }

    /** Hides or shows a category (the category menu's check boxes). */
    void setCategoryShown(ProblemCategory category, boolean shown) {
        if (shown) {
            hiddenCategories.remove(category);
        } else {
            hiddenCategories.add(category);
        }
        refresh();
    }

    /** Groups the problems (or shows the list with {@code null}). */
    void setGrouping(ProblemGroups.By by) {
        for (GroupChoice choice : groupBy.getItems()) {
            if (choice.by() == by) {
                groupBy.getSelectionModel().select(choice);
            }
        }
    }

    /** @return the grouping shown, {@code null} for the list */
    ProblemGroups.By getGrouping() {
        GroupChoice choice = groupBy.getValue();
        return choice == null ? null : choice.by();
    }

    /** Sets the search text at once (tests; the field waits 250 ms after typing). */
    void setQuery(String text) {
        search.setText(text);
        searchDelay.stop();
        query = text == null ? "" : text.strip().toLowerCase(java.util.Locale.ROOT);
        refresh();
    }

    private boolean passes(ValidationProblem p) {
        boolean level = switch (filter) {
            case ALL -> true;
            case ERRORS -> p.level() == ValidationProblem.Level.ERROR;
            case WARNINGS -> p.level() == ValidationProblem.Level.WARNING;
            case HINTS -> p.level() == ValidationProblem.Level.INFO;
        };
        if (!level || (!hiddenCategories.isEmpty() && hiddenCategories.contains(p.effectiveCategory()))) {
            return false;
        }
        if (focus != null && !focus.test(p)) {
            return false;
        }
        return query.isEmpty() || matches(p, query);
    }

    /** @return whether the search text is in the message, source, rule or the subject (key or name) */
    static boolean matches(ValidationProblem p, String lowerQuery) {
        return contains(p.message(), lowerQuery) || contains(p.source(), lowerQuery) || contains(p.ruleId(), lowerQuery)
                || p.subject() != null && (contains(p.subject().key(), lowerQuery) || contains(p.subject().label(), lowerQuery));
    }

    private static boolean contains(String text, String lowerQuery) {
        return text != null && text.toLowerCase(java.util.Locale.ROOT).contains(lowerQuery);
    }

    /** @return the problems shown now (all filters) */
    List<ValidationProblem> shownProblems() {
        return editorHost.getActiveProblems().stream().filter(this::passes).toList();
    }

    /**
     * Mirrors the host's problems into the panel: clears the row selection first
     * (JavaFX ListViewBehavior can throw IndexOutOfBounds when items.setAll runs
     * while a row is selected), updates the severity chips, the category counts and the groups,
     * and shows/hides the panel.
     */
    private void refresh() {
        var problems = editorHost.getActiveProblems();
        long errors = 0;
        long warnings = 0;
        long hints = 0;
        java.util.Map<ProblemCategory, Integer> counts = new java.util.EnumMap<>(ProblemCategory.class);
        List<ValidationProblem> shown = new java.util.ArrayList<>();
        for (ValidationProblem p : problems) {
            switch (p.level()) {
                case ERROR -> errors++;
                case WARNING -> warnings++;
                case INFO -> hints++;
            }
            counts.merge(p.effectiveCategory(), 1, Integer::sum);
        }
        categoryCounts = counts;
        // a filter whose severity is gone (fixed, other document) would hide everything: show all again
        if (filter == Filter.ERRORS && errors == 0 || filter == Filter.WARNINGS && warnings == 0
                || filter == Filter.HINTS && hints == 0) {
            filter = Filter.ALL;
        }
        for (ValidationProblem p : problems) {
            if (passes(p)) {
                shown.add(p);
            }
        }
        list.getSelectionModel().clearSelection();
        ProblemsGroupsShown grouping = groupingShown();
        if (grouping == null) {
            items.setAll(shown);
        } else {
            items.clear();
            regroup(shown, grouping.by());
        }
        showChip(errorChip, errors, filter == Filter.ERRORS, "ProblemsPanel.only-errors");
        showChip(warningChip, warnings, filter == Filter.WARNINGS, "ProblemsPanel.only-warnings");
        showChip(hintChip, hints, filter == Filter.HINTS, "ProblemsPanel.only-hints");
        categoryMenu.setText(hiddenCategories.isEmpty() ? I18n.t("ProblemsPanel.categories")
                : I18n.t("ProblemsPanel.categories-some", counts.size() - (int) counts.keySet().stream()
                        .filter(hiddenCategories::contains).count(), counts.size()));
        boolean show = !problems.isEmpty();
        setVisible(show);
        setManaged(show);
    }

    /** The grouping shown now (the list when {@code null}). */
    private record ProblemsGroupsShown(ProblemGroups.By by) {
    }

    private ProblemsGroupsShown groupingShown() {
        ProblemGroups.By by = getGrouping();
        boolean grouped = by != null;
        list.setVisible(!grouped);
        list.setManaged(!grouped);
        tree.setVisible(grouped);
        tree.setManaged(grouped);
        return grouped ? new ProblemsGroupsShown(by) : null;
    }

    /** Groups the shown problems off the FX thread; a newer refresh drops the result of an older one. */
    private void regroup(List<ValidationProblem> shown, ProblemGroups.By by) {
        long run = ++groupingRun;
        if (shown.size() < 5_000) {
            showGroups(ProblemGroups.group(shown, by));
            return;
        }
        tree.getRoot().getChildren().setAll(List.of(new TreeItem<>(I18n.t("ProblemsPanel.grouping"))));
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            List<ProblemGroups.Group> groups = ProblemGroups.group(shown, by);
            javafx.application.Platform.runLater(() -> {
                if (run == groupingRun) {
                    showGroups(groups);
                }
            });
        });
    }

    private void showGroups(List<ProblemGroups.Group> groups) {
        tree.getSelectionModel().clearSelection();
        List<TreeItem<Object>> children = new java.util.ArrayList<>(groups.size());
        for (ProblemGroups.Group g : groups) {
            children.add(new LazyItem(g));
        }
        if (children.isEmpty()) {
            children.add(new TreeItem<>(I18n.t("ProblemsPanel.no-match")));
        }
        tree.getRoot().getChildren().setAll(children);
    }

    /**
     * A tree node whose children are made when it is first opened: a group's kinds of problems, a kind's occurrences
     * (at most {@link #MAX_LEAVES}, then one "show in list" row).
     */
    private final class LazyItem extends TreeItem<Object> {
        private boolean loaded;

        LazyItem(Object value) {
            super(value);
        }

        @Override
        public boolean isLeaf() {
            return getValue() instanceof ValidationProblem || getValue() instanceof MoreRow;
        }

        @Override
        public javafx.collections.ObservableList<TreeItem<Object>> getChildren() {
            if (!loaded) {
                loaded = true;
                List<TreeItem<Object>> made = new java.util.ArrayList<>();
                if (getValue() instanceof ProblemGroups.Group g) {
                    if (g.kinds().size() == 1) {
                        addLeaves(made, g.kinds().getFirst().problems(), g);
                    } else {
                        g.kinds().forEach(k -> made.add(new LazyItem(k)));
                    }
                } else if (getValue() instanceof ProblemGroups.Kind k) {
                    addLeaves(made, k.problems(), k);
                }
                super.getChildren().setAll(made);
            }
            return super.getChildren();
        }

        private void addLeaves(List<TreeItem<Object>> made, List<ValidationProblem> problems, Object owner) {
            for (int i = 0; i < Math.min(problems.size(), MAX_LEAVES); i++) {
                made.add(new LazyItem(problems.get(i)));
            }
            if (problems.size() > MAX_LEAVES) {
                made.add(new LazyItem(new MoreRow(owner, problems.size() - MAX_LEAVES)));
            }
        }
    }

    /** "… 1,234 more - show in list" under a node with many occurrences. */
    private record MoreRow(Object owner, int more) {
    }

    /** @return every occurrence under a tree value (a group, a kind, one problem) */
    static List<ValidationProblem> problemsOf(Object value) {
        if (value instanceof ProblemGroups.Group g) {
            return g.problems();
        }
        if (value instanceof ProblemGroups.Kind k) {
            return k.problems();
        }
        if (value instanceof ValidationProblem p) {
            return List.of(p);
        }
        if (value instanceof MoreRow m) {
            return problemsOf(m.owner());
        }
        return List.of();
    }

    /** @return a title for "show in list" of a tree value */
    private static String titleOf(Object value) {
        if (value instanceof ProblemGroups.Group g) {
            return g.title();
        }
        if (value instanceof ProblemGroups.Kind k) {
            return k.title();
        }
        if (value instanceof MoreRow m) {
            return titleOf(m.owner());
        }
        return "";
    }

    /** Shows a group's (or kind's) problems in the list. */
    void showInList(Object value) {
        java.util.Set<ValidationProblem> members = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        members.addAll(problemsOf(value));
        setGrouping(null);
        setFocus(titleOf(value), members::contains);
    }

    /** Fills the category menu: one check box per category with its count, grouped, plus All / None. */
    private void fillCategoryMenu() {
        categoryMenu.getItems().clear();
        MenuItem all = menuItem("problems-categories-all", I18n.t("ProblemsPanel.categories-all"), "bi-check2-all", () -> {
            hiddenCategories.clear();
            refresh();
        });
        MenuItem none = menuItem("problems-categories-none", I18n.t("ProblemsPanel.categories-none"), "bi-x-lg", () -> {
            hiddenCategories.addAll(categoryCounts.keySet());
            refresh();
        });
        categoryMenu.getItems().addAll(all, none);
        ProblemCategory.Group group = null;
        for (var e : categoryCounts.entrySet()) {
            if (e.getKey().group() != group) {
                group = e.getKey().group();
                categoryMenu.getItems().add(new SeparatorMenuItem());
                MenuItem heading = new MenuItem(group.title());
                heading.setDisable(true);
                categoryMenu.getItems().add(heading);
            }
            ProblemCategory category = e.getKey();
            javafx.scene.control.CheckMenuItem item = new javafx.scene.control.CheckMenuItem(
                    category.title() + " (" + e.getValue() + ")");
            item.setId("problems-category-" + category.name());
            item.setSelected(!hiddenCategories.contains(category));
            item.setOnAction(ev -> setCategoryShown(category, item.isSelected()));
            categoryMenu.getItems().add(item);
        }
    }

    /** @return the shown filter in words (the export's summary), or {@code null} when everything is shown */
    private String filterDescription() {
        List<String> parts = new java.util.ArrayList<>();
        if (filter != Filter.ALL) {
            parts.add(switch (filter) {
                case ERRORS -> "Errors";
                case WARNINGS -> "Warnings";
                case HINTS -> "Hints";
                case ALL -> "";
            });
        }
        if (!hiddenCategories.isEmpty()) {
            parts.add("hidden categories: " + hiddenCategories.stream().map(ProblemCategory::fullTitle)
                    .collect(Collectors.joining(", ")));
        }
        if (!query.isEmpty()) {
            parts.add("search \"" + query + "\"");
        }
        if (focusText != null) {
            parts.add("group \"" + focusText + "\"");
        }
        return parts.isEmpty() ? null : String.join("; ", parts);
    }

    /** Test hook: chooses the export file instead of a dialog ({@code null} = cancelled). */
    static java.util.function.Function<String, java.io.File> exportTarget;

    /** Writes problems to an Excel workbook in the background. */
    void export(List<ValidationProblem> problems, String filterText) {
        if (problems.isEmpty()) {
            return;
        }
        String sourceName = editorHost.getActiveDocument().map(OpenDocument::getDisplayName).orElse(null);
        String initial = (sourceName != null ? sourceName.replaceFirst("\\.[^.]+$", "") : "validation") + "-problems.xlsx";
        java.io.File target;
        if (exportTarget != null) {
            target = exportTarget.apply(initial);
        } else {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle(I18n.t("ProblemsPanel.export"));
            chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
            chooser.setInitialFileName(initial);
            target = org.fxt.freexmltoolkit.util.FileChooserHelper.showSaveDialog(chooser,
                    getScene() != null ? getScene().getWindow() : null);
        }
        if (target == null) {
            return;
        }
        java.io.File output = target.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")
                ? target : new java.io.File(target.getParentFile(), target.getName() + ".xlsx");
        exportMenu.setDisable(true);
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try {
                ValidationExcelExporter.export(problems, sourceName, filterText, output.toPath());
                javafx.application.Platform.runLater(() -> {
                    exportMenu.setDisable(false);
                    if (onExported != null) {
                        onExported.accept(output);
                    } else {
                        org.fxt.freexmltoolkit.util.DialogHelper.showInformation(I18n.t("ProblemsPanel.export"), null,
                                I18n.t("ProblemsPanel.exported", problems.size(), output.getAbsolutePath()));
                    }
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    exportMenu.setDisable(false);
                    org.fxt.freexmltoolkit.util.DialogHelper.showActionError(I18n.t("ProblemsPanel.export"),
                            I18n.t("ValidationPanel.could-not-write-the-excel"),
                            org.fxt.freexmltoolkit.util.DialogHelper.Remedies.EXPORT, ex);
                });
            }
        });
    }

    /** Test hook: told about a finished export instead of the message box. */
    static java.util.function.Consumer<java.io.File> onExported;

    private static void showChip(ToggleButton chip, long count, boolean active, String onlyKey) {
        chip.setText(String.valueOf(count));
        chip.setVisible(count > 0);
        chip.setManaged(count > 0);
        chip.setSelected(active);
        String text = active ? I18n.t("ProblemsPanel.show-all") : I18n.t(onlyKey);
        chip.setAccessibleText(text);
        if (chip.getTooltip() == null) {
            chip.setTooltip(new Tooltip(text));
        } else {
            chip.getTooltip().setText(text);
        }
    }

    private GroupChoice storedGrouping() {
        String stored = null;
        try {
            stored = org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .get(GROUP_KEY);
        } catch (RuntimeException e) {
            // no settings (tests)
        }
        for (GroupChoice choice : groupBy.getItems()) {
            if (choice.by() != null && choice.by().name().equals(stored)) {
                return choice;
            }
        }
        return groupBy.getItems().getFirst();
    }

    private static void storeGrouping(GroupChoice choice) {
        try {
            org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .set(GROUP_KEY, choice == null || choice.by() == null ? "" : choice.by().name());
        } catch (RuntimeException e) {
            // no settings (tests)
        }
    }

    /** @return {@code true} while the panel shows only its header bar. */
    public boolean isCollapsed() {
        return collapsed;
    }

    /** Collapses the panel to its header bar (or expands it back). */
    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        content.setVisible(!collapsed);
        content.setManaged(!collapsed);
        resizer.setVisible(!collapsed);
        resizer.setManaged(!collapsed);
        // collapsed: only the header bar; expanded: the height the user dragged
        setMinHeight(collapsed ? USE_PREF_SIZE : MIN_HEIGHT);
        setPrefHeight(collapsed ? USE_COMPUTED_SIZE : expandedHeight);
        collapseIcon.setIconLiteral(collapsed ? "bi-chevron-up" : "bi-chevron-down");
    }

    /** @return the number of problems currently shown, in the list or the groups (for tests/observers). */
    public int getProblemCount() {
        return getGrouping() == null ? items.size() : shownProblems().size();
    }

    /**
     * The row's right-click menu: the quick fixes (if any), then Copy Message, Copy Problem
     * (message with severity, source, location, rule and context) and Copy All Problems.
     */
    ContextMenu contextMenu(ValidationProblem item) {
        ContextMenu menu = new ContextMenu();
        if (item.hasFixes()) {
            menu.getItems().addAll(
                    QuickFixMenuFactory.buildQuickFixMenu(item, fix -> editorHost.getQuickFixController().applyFix(item, fix)),
                    new SeparatorMenuItem());
        }
        if (item.hasLink()) {
            menu.getItems().addAll(openLinkItem("problems-ctx-open-link", item), new SeparatorMenuItem());
        }
        menu.getItems().addAll(
                menuItem("problems-ctx-copy-message", I18n.t("ProblemsPanel.copy-message"), "bi-clipboard",
                        () -> copyToClipboard(item.message() == null ? "" : item.message())),
                menuItem("problems-ctx-copy", I18n.t("ProblemsPanel.copy-problem"), "bi-clipboard-plus",
                        () -> copyToClipboard(describe(item, documentName()))),
                menuItem("problems-ctx-copy-all", I18n.t("ProblemsPanel.copy-all-problems"), "bi-clipboard-data",
                        () -> copyToClipboard(describeAll(shownProblems(), documentName()))),
                menuItem("problems-ctx-export-shown", I18n.t("ProblemsPanel.export-shown"), "bi-file-earmark-spreadsheet",
                        this::onExportShown));
        // Explain / Fix with AI — after the deterministic quick fixes and the copy actions, only when AI is on
        menu.getItems().addAll(org.fxt.freexmltoolkit.controls.shell.editor.ai.AiActions.problemItems(editorHost, item));
        return menu;
    }

    /**
     * One problem as plain text: {@code [Error] XSD: message (file : line:column)}, followed by
     * the Schematron rule and context lines when present.
     */
    static String describe(ValidationProblem item, String documentName) {
        StringBuilder text = new StringBuilder()
                .append('[').append(switch (item.level()) {
                    case ERROR -> "Error";
                    case WARNING -> "Warning";
                    case INFO -> "Hint";
                }).append("] ");
        if (item.source() != null && !item.source().isBlank()) {
            text.append(item.source()).append(": ");
        }
        text.append(item.message() == null ? "" : item.message());
        String name = documentName == null ? "" : documentName;
        if (item.line() > 0) {
            text.append(" (").append(name.isEmpty() ? "line " : name + " : ").append(item.line());
            if (item.column() > 0) {
                text.append(':').append(item.column());
            }
            text.append(')');
        } else if (!name.isEmpty()) {
            text.append(" (").append(name).append(')');
        }
        if (item.ruleId() != null && !item.ruleId().isBlank()) {
            text.append("\n    Rule / Test: ").append(item.ruleId());
        }
        if (item.context() != null && !item.context().isBlank()) {
            text.append("\n    Context: ").append(item.context());
        }
        if (item.hasLink()) {
            text.append("\n    Link: ").append(item.link());
        }
        return text.toString();
    }

    /** Opens a problem's web page; tests replace it to keep the browser closed. */
    static java.util.function.Consumer<String> linkOpener = ProjectLinks::openInBrowser;

    /** @return the name of the service behind a problem's web page (GLEIF, ESMA FIRDS, OpenFIGI, else the host) */
    static String linkService(String url) {
        String host;
        try {
            host = java.net.URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            host = null;
        }
        if (host == null) {
            return "Web";
        }
        if (host.endsWith("gleif.org")) {
            return "GLEIF";
        }
        if (host.endsWith("esma.europa.eu")) {
            return "ESMA FIRDS";
        }
        if (host.endsWith("openfigi.com")) {
            return "OpenFIGI";
        }
        return host;
    }

    /** "Open at GLEIF / ESMA FIRDS / OpenFIGI" for a problem with a web page (also used by the Validation panel). */
    static MenuItem openLinkItem(String id, ValidationProblem item) {
        return menuItem(id, I18n.t("ProblemsPanel.open-at", linkService(item.link())), "bi-box-arrow-up-right",
                () -> linkOpener.accept(item.link()));
    }

    /** All problems, one {@link #describe described} problem per line. */
    static String describeAll(List<ValidationProblem> problems, String documentName) {
        return problems.stream().map(p -> describe(p, documentName)).collect(Collectors.joining("\n"));
    }

    private String documentName() {
        return editorHost.getActiveDocument().map(OpenDocument::getDisplayName).orElse("");
    }

    private static void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static MenuItem menuItem(String id, String text, String iconLiteral, Runnable action) {
        MenuItem item = new MenuItem(text, SemanticIcon.bind(icon(iconLiteral, 16), ActionColor.NEUTRAL));
        item.setId(id);
        item.setOnAction(e -> action.run());
        return item;
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(size);
        return icon;
    }

    /**
     * A tooltip with the finding's full message plus — for Schematron problems —
     * the failed rule/test expression and the failing node's XPath context.
     */
    static javafx.scene.control.Tooltip detailTooltip(ValidationProblem item) {
        StringBuilder text = new StringBuilder(item.message() == null ? "" : item.message());
        if (item.ruleId() != null && !item.ruleId().isBlank()) {
            text.append("\n\nRule / Test: ").append(item.ruleId());
        }
        if (item.context() != null && !item.context().isBlank()) {
            text.append("\nContext: ").append(item.context());
        }
        if (item.hasLink()) {
            text.append("\n\n").append(item.link());
        }
        javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(text.toString());
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(640);
        return tooltip;
    }

    /**
     * The row's link to the finding's entry at the online service ("↗ GLEIF"); a click opens it without selecting the
     * row (the press would otherwise jump to the line first).
     */
    private static Hyperlink linkButton(ValidationProblem item) {
        String service = linkService(item.link());
        Hyperlink link = new Hyperlink(service, icon("bi-box-arrow-up-right", 12));
        link.setId("problems-open-link");
        link.getStyleClass().add("fxt-problems-link");
        link.setFocusTraversable(false);
        String text = I18n.t("ProblemsPanel.open-at", service);
        link.setAccessibleText(text);
        link.setTooltip(new javafx.scene.control.Tooltip(text + "\n" + item.link()));
        link.addEventHandler(MouseEvent.MOUSE_PRESSED, MouseEvent::consume);
        link.setOnAction(e -> {
            linkOpener.accept(item.link());
            link.setVisited(false);
        });
        return link;
    }

    /** @return the icon of a severity ({@code sev-error} / {@code sev-warning} / {@code sev-info}) */
    static IconifyIcon levelIcon(ValidationProblem.Level level, int size) {
        IconifyIcon icon = icon(switch (level) {
            case ERROR -> "bi-x-circle";
            case WARNING -> "bi-exclamation-triangle-fill";
            case INFO -> "bi-info-circle";
        }, size);
        icon.getStyleClass().add(switch (level) {
            case ERROR -> "sev-error";
            case WARNING -> "sev-warning";
            case INFO -> "sev-info";
        });
        return icon;
    }

    /**
     * A problem row: severity icon · message (grows, ellipsis) · link to the entry (online lookups) · "file : line" in
     * mono. The row menu is built when it is opened (200,000 rows must not build 200,000 menus).
     */
    private final class ProblemRowCell extends ListCell<ValidationProblem> {
        private final Tooltip tooltip = new Tooltip();

        private ProblemRowCell() {
            // Follow the ListView width (ellipsize) instead of forcing a horizontal scrollbar.
            setPrefWidth(0);
            tooltip.setWrapText(true);
            tooltip.setMaxWidth(640);
            setOnContextMenuRequested(e -> {
                ValidationProblem item = getItem();
                if (item != null) {
                    contextMenu(item).show(this, e.getScreenX(), e.getScreenY());
                    e.consume();
                }
            });
        }

        @Override
        protected void updateItem(ValidationProblem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
                return;
            }
            IconifyIcon severityIcon = levelIcon(item.level(), 15);

            // Source badge (XSD / Schematron / Well-formed / …) so mixed result
            // lists stay attributable at a glance.
            Label source = new Label(item.source());
            source.getStyleClass().add("fxt-problems-source");

            Label message = new Label(item.message());
            message.getStyleClass().add("fxt-problems-msg");
            message.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(message, Priority.ALWAYS);

            String name = documentName();
            Label location = new Label(item.line() > 0 ? name + " : " + item.line() : name);
            location.getStyleClass().add("fxt-problems-loc");

            HBox row = new HBox(10, severityIcon, source, message);
            if (item.hasLink()) {
                row.getChildren().add(linkButton(item));
            }
            row.getChildren().add(location);
            row.setAlignment(Pos.CENTER_LEFT);
            setText(null);
            setGraphic(row);
            tooltip.setText(detailTooltip(item).getText());
            setTooltip(tooltip);
        }
    }

    /**
     * A row of the grouped view: a group (security / rule / category: worst severity, title, occurrences, kinds), a
     * kind of problem, one occurrence ("line: message"), or "… more - show in list".
     */
    private final class GroupCell extends javafx.scene.control.TreeCell<Object> {
        GroupCell() {
            setPrefWidth(0);
            setOnContextMenuRequested(e -> {
                Object value = getItem();
                ContextMenu menu = value instanceof ValidationProblem p ? contextMenu(p)
                        : value instanceof ProblemGroups.Group || value instanceof ProblemGroups.Kind ? groupMenu(value)
                        : null;
                if (menu != null) {
                    menu.show(this, e.getScreenX(), e.getScreenY());
                    e.consume();
                }
            });
            setOnMouseClicked(e -> {
                if (getItem() instanceof MoreRow m && e.getClickCount() == 1) {
                    showInList(m.owner());
                }
            });
        }

        @Override
        protected void updateItem(Object value, boolean empty) {
            super.updateItem(value, empty);
            setText(null);
            if (empty || value == null) {
                setGraphic(null);
                return;
            }
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            if (value instanceof ProblemGroups.Group g) {
                Label title = new Label(g.title());
                title.getStyleClass().add("fxt-problems-group-title");
                Label count = new Label(g.kinds().size() > 1
                        ? I18n.t("ProblemsPanel.group-count-kinds", g.count(), g.kinds().size())
                        : occurrences(g.count()));
                count.getStyleClass().add("fxt-problems-group-count");
                row.getChildren().addAll(levelIcon(g.level(), 14), title, count);
            } else if (value instanceof ProblemGroups.Kind k) {
                Label title = new Label(k.title());
                title.getStyleClass().add("fxt-problems-msg");
                Label count = new Label(occurrences(k.problems().size()));
                count.getStyleClass().add("fxt-problems-group-count");
                row.getChildren().addAll(levelIcon(k.level(), 13), title, count);
            } else if (value instanceof ValidationProblem p) {
                Label location = new Label(p.line() > 0 ? String.valueOf(p.line()) : "–");
                location.getStyleClass().add("fxt-problems-loc");
                Label message = new Label(p.message());
                message.getStyleClass().add("fxt-problems-msg");
                message.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(message, Priority.ALWAYS);
                row.getChildren().addAll(levelIcon(p.level(), 13), location, message);
                if (p.hasLink()) {
                    row.getChildren().add(linkButton(p));
                }
            } else if (value instanceof String note) {
                Label label = new Label(note);
                label.getStyleClass().add("fxt-problems-group-count");
                row.getChildren().add(label);
            } else if (value instanceof MoreRow m) {
                Hyperlink more = new Hyperlink(I18n.t("ProblemsPanel.more-in-list", m.more()));
                more.setOnAction(e -> showInList(m.owner()));
                row.getChildren().add(more);
            }
            setGraphic(row);
        }
    }

    /** @return "1 occurrence" / "12 occurrences" */
    private static String occurrences(int n) {
        return n == 1 ? I18n.t("ProblemsPanel.group-count-one") : I18n.t("ProblemsPanel.group-count", n);
    }

    /** The menu of a group or kind row: show in list, copy, export, and the collective fix where FIRDS knows better. */
    ContextMenu groupMenu(Object value) {
        ContextMenu menu = new ContextMenu();
        List<ValidationProblem> problems = problemsOf(value);
        MenuItem fix = collectiveFixItem(problems);
        if (fix != null) {
            menu.getItems().addAll(fix, new SeparatorMenuItem());
        }
        menu.getItems().addAll(
                menuItem("problems-group-show-in-list", I18n.t("ProblemsPanel.show-in-list"), "bi-list-ul",
                        () -> showInList(value)),
                menuItem("problems-group-copy", I18n.t("ProblemsPanel.copy-group"), "bi-clipboard-data",
                        () -> copyToClipboard(describeAll(problems, documentName()))),
                menuItem("problems-group-export", I18n.t("ProblemsPanel.export-group"), "bi-file-earmark-spreadsheet",
                        () -> export(problems, "group \"" + titleOf(value) + "\"")));
        return menu;
    }

    /**
     * @return "Fix CFI code / issuer LEI of n ISINs in m places (ESMA FIRDS)…" for problems with a known correct
     *         value, or {@code null}
     */
    private MenuItem collectiveFixItem(List<ValidationProblem> problems) {
        java.util.Set<String> isins = new java.util.LinkedHashSet<>();
        java.util.Set<org.fxt.freexmltoolkit.service.finance.IdentifierFixes.Field> fields =
                java.util.EnumSet.noneOf(org.fxt.freexmltoolkit.service.finance.IdentifierFixes.Field.class);
        for (ValidationProblem p : problems) {
            ProblemCategory category = p.effectiveCategory();
            if ((category == ProblemCategory.ISIN_CFI || category == ProblemCategory.ISIN_ISSUER)
                    && p.subject() != null && p.subject().kind() == ProblemSubject.Kind.ISIN) {
                isins.add(p.subject().key());
                fields.add(category == ProblemCategory.ISIN_CFI
                        ? org.fxt.freexmltoolkit.service.finance.IdentifierFixes.Field.CFI
                        : org.fxt.freexmltoolkit.service.finance.IdentifierFixes.Field.ISSUER_LEI);
            }
        }
        if (isins.isEmpty() || !org.fxt.freexmltoolkit.service.finance.isin.Isin.allowed()) {
            return null;
        }
        String text = isins.size() == 1
                ? I18n.t("ProblemsPanel.fix-one", isins.iterator().next())
                : I18n.t("ProblemsPanel.fix-many", isins.size());
        return menuItem("problems-group-fix", text, "bi-magic", () -> applyCollectiveFix(isins, fields));
    }

    /** Test hook: answers the collective fix's confirmation instead of a dialog. */
    static java.util.function.Predicate<String> fixConfirmer;

    /**
     * Plans the collective fix in the background (every place the ISINs report a CFI code / issuer LEI that ESMA FIRDS
     * has differently), asks once, and applies it as one undo step - if the document did not change meanwhile.
     */
    void applyCollectiveFix(java.util.Set<String> isins,
                            java.util.Set<org.fxt.freexmltoolkit.service.finance.IdentifierFixes.Field> fields) {
        String text = editorHost.getActiveText().orElse(null);
        EditorHost.ValidationTarget target = editorHost.validationTarget();
        if (text == null || target == null) {
            return;
        }
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            var plan = org.fxt.freexmltoolkit.service.finance.IdentifierFixes.plan(text, isins::contains, fields,
                    isin -> org.fxt.freexmltoolkit.service.finance.isin.Isin.cache().cached(isin).orElse(null));
            javafx.application.Platform.runLater(() -> confirmAndApply(plan, target, isins, fields));
        });
    }

    private void confirmAndApply(org.fxt.freexmltoolkit.service.finance.IdentifierFixes.Plan plan,
                                 EditorHost.ValidationTarget target, java.util.Set<String> isins,
                                 java.util.Set<org.fxt.freexmltoolkit.service.finance.IdentifierFixes.Field> fields) {
        if (plan.isEmpty()) {
            org.fxt.freexmltoolkit.util.DialogHelper.showInformation(I18n.t("ProblemsPanel.fix-title"), null,
                    I18n.t("ProblemsPanel.fix-nothing"));
            return;
        }
        StringBuilder detail = new StringBuilder();
        int shown = 0;
        for (var f : plan.fixes()) {
            if (shown++ == 12) {
                detail.append(I18n.t("ProblemsPanel.fix-and-more", plan.fixes().size() - 12)).append('\n');
                break;
            }
            detail.append(f.isin()).append(f.name() == null ? "" : " (" + f.name() + ")").append(": ")
                    .append(f.field() == org.fxt.freexmltoolkit.service.finance.IdentifierFixes.Field.CFI ? "CFI " : "Issuer LEI ")
                    .append(f.from()).append(" → ").append(f.to()).append("  ×").append(f.occurrences()).append('\n');
        }
        String header = I18n.t("ProblemsPanel.fix-header", plan.edits().size(), plan.isins());
        boolean yes = fixConfirmer != null ? fixConfirmer.test(header + "\n" + detail)
                : org.fxt.freexmltoolkit.util.DialogHelper.showConfirmation(I18n.t("ProblemsPanel.fix-title"), header,
                        detail + "\n" + I18n.t("ProblemsPanel.fix-note"));
        if (!yes) {
            return;
        }
        EditorHost.ValidationTarget now = editorHost.validationTarget();
        if (now == null || now.tab() != target.tab() || now.revision() != target.revision()) {
            org.fxt.freexmltoolkit.util.DialogHelper.showInformation(I18n.t("ProblemsPanel.fix-title"), null,
                    I18n.t("ProblemsPanel.fix-changed"));
            return;
        }
        if (!editorHost.replaceActiveTextEdits(plan.edits())) {
            return;
        }
        // the fixed findings are gone; the next check confirms it
        java.util.Set<String> fixed = new java.util.HashSet<>();
        plan.fixes().forEach(f -> fixed.add(f.isin() + "|" + f.field()));
        List<ValidationProblem> remaining = editorHost.getActiveProblems().stream().filter(p -> {
            if (p.subject() == null || p.subject().kind() != ProblemSubject.Kind.ISIN) {
                return true;
            }
            ProblemCategory c = p.effectiveCategory();
            String field = c == ProblemCategory.ISIN_CFI ? "CFI" : c == ProblemCategory.ISIN_ISSUER ? "ISSUER_LEI" : null;
            return field == null || !fixed.contains(p.subject().key() + "|" + field);
        }).toList();
        editorHost.setActiveProblems(remaining);
    }
}
