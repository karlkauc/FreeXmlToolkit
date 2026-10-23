package org.fxt.freexmltoolkit.controls.shell;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.dialogs.ErrorReportDialog;
import org.fxt.freexmltoolkit.controls.shell.editor.AboutDialog;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorActions;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorActions.EditorAction;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlRunner;
import org.fxt.freexmltoolkit.controls.shell.editor.KeyboardShortcutsDialog;
import org.fxt.freexmltoolkit.controls.shell.editor.OpenDocument;
import org.fxt.freexmltoolkit.controls.shell.editor.UpdateActionRunner;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.ApplicationLauncherLocator;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.telemetry.UsageEvents;
import org.fxt.freexmltoolkit.util.DialogHelper;
import org.fxt.freexmltoolkit.util.ProjectLinks;

/**
 * The shell's menu bar (File / Edit / View / Tools / Help): every item triggers an action the
 * toolbar, the Activity Bar or a keyboard shortcut already offers. The layout lives in
 * {@code /pages/views/ShellMenuBar.fxml}; this class fills the data-driven parts (view modes,
 * recent files, activities) and keeps the items' enabled state in step with the active document.
 *
 * <p>On Windows and Linux the bar sits at the top of the window. On macOS
 * {@code app/MacMenuIntegration} moves it into the system menu bar and takes the
 * application-level items ({@link #detachApplicationItems()}) into the application menu.
 *
 * <p>The accelerators shown here are the shell's existing shortcuts. The shell's own key
 * handler stays the one that executes them: it consumes the key press before the scene's
 * accelerators are processed, so nothing runs twice.
 */
public final class ShellMenuBar extends MenuBar {

    /** Whether the application runs on macOS (system menu bar, Cmd-based conventions). */
    static final boolean MAC = ApplicationLauncherLocator.isMacOS();

    /** Items that need an open document. */
    private static final Set<String> DOCUMENT_ITEMS = Set.of(
            "menu-file-save", "menu-file-save-as", "menu-file-save-all",
            "menu-edit-undo", "menu-edit-redo", "menu-edit-find", "menu-edit-replace",
            "menu-edit-format", "menu-edit-minify", "menu-edit-insert-template",
            "menu-tools-set-schema", "menu-tools-compare");

    /** Items that only apply to some file types. */
    private static final Map<String, EditorAction> TYPE_ITEMS = Map.of(
            "menu-tools-validate", EditorAction.VALIDATE,
            "menu-tools-transform", EditorAction.TRANSFORM,
            "menu-tools-debug-xslt", EditorAction.TRANSFORM,
            "menu-tools-generate-docs", EditorAction.GENERATE_DOCS,
            "menu-tools-generate-sample", EditorAction.TYPE_EDITOR,
            "menu-tools-type-editor", EditorAction.TYPE_EDITOR);

    private final UnifiedShellView shell;
    private final EditorHost editorHost;
    private final Map<ViewMode, MenuItem> viewModeItems = new EnumMap<>(ViewMode.class);

    @FXML private Menu fileMenu;
    @FXML private Menu openRecentMenu;
    @FXML private Menu viewMenu;
    @FXML private Menu activitiesMenu;
    @FXML private Menu helpMenu;
    @FXML private ShellMenuItem closeTabItem;
    @FXML private SeparatorMenuItem fileAppSeparator;
    @FXML private ShellMenuItem settingsItem;
    @FXML private ShellMenuItem exitItem;
    @FXML private ShellMenuItem replaceItem;
    @FXML private ShellMenuItem fullScreenItem;
    @FXML private ShellMenuItem reportProblemItem;
    @FXML private SeparatorMenuItem fundsSeparator;
    @FXML private ShellMenuItem fundsSiteItem;
    @FXML private ShellMenuItem fundsDocsItem;
    @FXML private ShellMenuItem sponsorItem;
    @FXML private SeparatorMenuItem helpAppSeparator;
    @FXML private ShellMenuItem updatesItem;
    @FXML private ShellMenuItem aboutItem;

    /**
     * @param shell the shell whose actions the items trigger; its editor host must be available
     */
    public ShellMenuBar(UnifiedShellView shell) {
        this.shell = shell;
        this.editorHost = shell.getEditorHost();
        FxmlViews.load(this, "/pages/views/ShellMenuBar.fxml");

        openRecentMenu.setGraphic(ShellMenuItem.icon("bi-clock-history", ActionColor.NAVIGATE));
        activitiesMenu.setGraphic(ShellMenuItem.icon("bi-grid", ActionColor.NAVIGATE));
        buildViewModeItems();
        buildActivityItems();
        rebuildRecentFiles();
        sponsorItem.setGraphic(ProjectLinks.sponsorIcon(16));
        if (!ErrorReportDialog.isAvailable()) {
            helpMenu.getItems().remove(reportProblemItem);
        }
        if (MAC) {
            applyMacConventions();
        }

        // The enabled state follows the active document. Native (macOS) menus are not
        // guaranteed to report "showing", so it is kept current by listeners as well.
        InvalidationListener refresh = obs -> refresh();
        editorHost.activeTabProperty().addListener(refresh);
        editorHost.activeViewModeProperty().addListener(refresh);
        editorHost.getOpenDocuments().addListener((InvalidationListener) obs -> {
            refresh();
            rebuildRecentFiles();
        });
        for (Menu menu : getMenus()) {
            menu.addEventHandler(Menu.ON_SHOWING, e -> refresh());
        }
        fileMenu.addEventHandler(Menu.ON_SHOWING, e -> rebuildRecentFiles());
        refresh();
    }

    /** Cmd+H hides the application on macOS, and full screen is Ctrl+Cmd+F there. */
    private void applyMacConventions() {
        replaceItem.setAccelerator(KeyCombination.valueOf("Shortcut+Alt+F"));
        fullScreenItem.setAccelerator(KeyCombination.valueOf("Ctrl+Meta+F"));
        // The system menu bar has no mnemonics; drop the "_" markers from the menu titles.
        for (Menu menu : getMenus()) {
            menu.setMnemonicParsing(false);
            menu.setText(menu.getText().replace("_", ""));
        }
    }

    private void buildViewModeItems() {
        int index = 0;
        for (ViewMode mode : ViewMode.values()) {
            ShellMenuItem item = new ShellMenuItem(mode.label() + " View", mode.icon(), ActionColor.NAVIGATE);
            item.setId("menu-view-mode-" + mode.name().toLowerCase(java.util.Locale.ROOT));
            item.setOnAction(e -> run("view_" + mode.name().toLowerCase(java.util.Locale.ROOT),
                    () -> editorHost.setActiveViewMode(mode)));
            viewModeItems.put(mode, item);
            viewMenu.getItems().add(index++, item);
        }
    }

    private void buildActivityItems() {
        for (Activity activity : Activity.values()) {
            // Settings has its own item; FundsXML only exists while the extension is enabled.
            if (activity == Activity.SETTINGS
                    || (activity == Activity.FUNDSXML && !FundsXmlRunner.isEnabled())) {
                continue;
            }
            ShellMenuItem item = new ShellMenuItem(activity.label(), activity.icon(), ActionColor.NAVIGATE);
            item.setId("menu-view-activity-" + activity.id());
            item.setOnAction(e -> run("activity_" + activity.id(), () -> shell.showActivity(activity)));
            activitiesMenu.getItems().add(item);
        }
    }

    /** Refills "Open Recent" from the stored recent files (the store is not observable). */
    private void rebuildRecentFiles() {
        List<MenuItem> items = new ArrayList<>();
        for (File file : recentFiles()) {
            ShellMenuItem item = new ShellMenuItem(file.getAbsolutePath(), "bi-file-earmark-text",
                    ActionColor.TOOL);
            item.setMnemonicParsing(false); // file names may contain underscores
            item.setOnAction(e -> run("open_recent", () -> shell.openFile(file)));
            items.add(item);
        }
        if (items.isEmpty()) {
            MenuItem none = new ShellMenuItem("No Recent Files", "bi-clock-history", ActionColor.NEUTRAL);
            none.setDisable(true);
            items.add(none);
        }
        openRecentMenu.getItems().setAll(items);
    }

    private static List<File> recentFiles() {
        try {
            return ServiceRegistry.get(PropertiesService.class).getLastOpenFiles();
        } catch (RuntimeException e) {
            return List.of(); // service not available (e.g. isolated tests)
        }
    }

    /** Enables exactly the items that apply to the active tab, document and file type. */
    private void refresh() {
        java.util.Optional<OpenDocument> document = editorHost.getActiveDocument();
        EditorFileType type = document.map(OpenDocument::getFileType).orElse(null);
        boolean hasDocument = document.isPresent();
        for (MenuItem item : allItems()) {
            String id = item.getId();
            if (id == null) {
                continue;
            }
            if (DOCUMENT_ITEMS.contains(id)) {
                item.setDisable(!hasDocument);
            } else if (TYPE_ITEMS.containsKey(id)) {
                item.setDisable(!EditorActions.applicableFor(type, TYPE_ITEMS.get(id)));
            } else if ("menu-tools-add-favorite".equals(id)) {
                item.setDisable(document.map(OpenDocument::getPath).isEmpty());
            } else if ("menu-tools-toggle-breakpoint".equals(id)) {
                item.setDisable(!editorHost.canToggleBreakpoint()); // a saved XSLT document
            }
        }
        // Run only for runnable documents, exactly like the toolbar's Run button.
        allItems().stream().filter(i -> "menu-tools-run".equals(i.getId()))
                .forEach(i -> i.setDisable(!shell.isActiveDocumentRunnable()));
        closeTabItem.setDisable(editorHost.activeTabProperty().get() == null);
        // The extension can be switched on and off in the settings while the app runs.
        boolean funds = FundsXmlRunner.isEnabled();
        for (MenuItem item : List.of(fundsSeparator, fundsSiteItem, fundsDocsItem)) {
            item.setVisible(funds);
        }
        viewModeItems.forEach((mode, item) -> item.setDisable(!editorHost.activeSupportsView(mode)));
    }

    /** @return every item of the bar, submenus included (depth-first). */
    List<MenuItem> allItems() {
        List<MenuItem> items = new ArrayList<>();
        for (Menu menu : getMenus()) {
            collect(menu, items);
        }
        return items;
    }

    private static void collect(Menu menu, List<MenuItem> into) {
        for (MenuItem item : menu.getItems()) {
            into.add(item);
            if (item instanceof Menu submenu) {
                collect(submenu, into);
            }
        }
    }

    /**
     * Removes the items that belong into the macOS application menu (and "Exit", which is the
     * system's "Quit" there) from File and Help.
     *
     * @return About, Check for Updates and Settings, in application-menu order
     */
    public List<MenuItem> detachApplicationItems() {
        fileMenu.getItems().removeAll(fileAppSeparator, settingsItem, exitItem);
        helpMenu.getItems().removeAll(helpAppSeparator, updatesItem, aboutItem);
        return List.of(aboutItem, updatesItem, settingsItem);
    }

    /**
     * Builds a copy of the Help menu as a popup, for the header's Help button and F1: the same
     * items with the same actions, without hidden items and without doubled separators.
     *
     * @return a new context menu; its item ids use the prefix {@code help-popup-}
     */
    public ContextMenu createHelpPopup() {
        refresh();
        ContextMenu popup = new ContextMenu();
        popup.setId("help-popup");
        for (MenuItem item : helpMenu.getItems()) {
            if (!item.isVisible()) {
                continue;
            }
            List<MenuItem> copied = popup.getItems();
            if (item instanceof SeparatorMenuItem) {
                if (!copied.isEmpty() && !(copied.getLast() instanceof SeparatorMenuItem)) {
                    copied.add(new SeparatorMenuItem());
                }
                continue;
            }
            MenuItem copy = item == sponsorItem
                    ? new MenuItem(item.getText(), ProjectLinks.sponsorIcon(16))
                    : new ShellMenuItem(item.getText(), ((ShellMenuItem) item).getIconLiteral(),
                            ((ShellMenuItem) item).getColor());
            copy.setId(item.getId() != null ? item.getId().replace("menu-help-", "help-popup-") : null);
            copy.setOnAction(item.getOnAction());
            copy.setDisable(item.isDisable());
            copied.add(copy);
        }
        if (!popup.getItems().isEmpty() && popup.getItems().getLast() instanceof SeparatorMenuItem) {
            popup.getItems().removeLast();
        }
        return popup;
    }

    /** @return the Help menu (macOS inserts the Window menu before it). */
    public Menu getHelpMenu() {
        return helpMenu;
    }

    private void run(String command, Runnable action) {
        shell.runFromMenu(command, action);
    }

    private Window window() {
        // Not this bar's own scene: as a system menu bar it may not be attached to one.
        return shell.getScene() != null ? shell.getScene().getWindow() : null;
    }

    // ===== @FXML action handlers (onAction targets from ShellMenuBar.fxml). Public for jpackage. =====
    @FXML public void onNew() { run("new", shell::onNew); }
    @FXML public void onOpen() { run("open", shell::onOpen); }
    @FXML public void onOpenFromUrl() { run("open_url", editorHost::openFromUrl); }
    @FXML public void onCloseTab() { run("close_tab", editorHost::closeActiveTab); }
    @FXML public void onSave() { run("save", shell::onSave); }
    @FXML public void onSaveAs() { run("save_as", shell::onSaveAs); }
    @FXML public void onSaveAll() { run("save_all", shell::onSaveAll); }
    @FXML public void onSettings() { run("settings", shell::openSettings); }

    /** Closes the window like its title-bar button, so the application's close handling applies. */
    @FXML public void onExit() {
        run("exit", shell::requestExit);
    }

    @FXML public void onUndo() { run("undo", shell::onUndo); }
    @FXML public void onRedo() { run("redo", shell::onRedo); }
    @FXML public void onFind() { run("find", () -> shell.showSearch(false)); }
    @FXML public void onReplace() { run("replace", () -> shell.showSearch(true)); }
    @FXML public void onFindInFiles() { run("find_in_files", () -> shell.openSearchPanel(false)); }
    @FXML public void onReplaceInFiles() { run("replace_in_files", () -> shell.openSearchPanel(true)); }
    @FXML public void onFormat() { run("format", shell::onFormat); }
    @FXML public void onMinify() { run("minify", shell::onMinify); }
    @FXML public void onInsertTemplate() { run("insert_template", shell::onInsertTemplate); }

    @FXML public void onToggleSidePanel() { run("toggle_side_panel", shell::toggleLeftPanel); }
    @FXML public void onToggleInspector() {
        run("toggle_inspector", () -> shell.setInspectorVisible(!shell.isInspectorOpen()));
    }
    @FXML public void onToggleQueryConsole() { run("query_console", shell::toggleQueryConsole); }
    @FXML public void onToggleFavorites() { run("toggle_favorites", shell::toggleFavoritesPanel); }
    @FXML public void onToggleTheme() { run("toggle_theme", shell::onToggleTheme); }
    @FXML public void onToggleFullScreen() { run("full_screen", shell::toggleFullScreen); }

    @FXML public void onValidate() { run("validate", shell::onValidate); }
    @FXML public void onRun() { run("run", shell::executeActive); }
    @FXML public void onTransform() { run("transform", shell::onTransform); }
    @FXML public void onDebugXslt() { run("debug_xslt", shell::onDebugXslt); }
    @FXML public void onToggleBreakpoint() { run("toggle_breakpoint", editorHost::toggleBreakpointAtCaret); }
    @FXML public void onSetSchema() { run("set_schema", shell::onSetSchema); }
    @FXML public void onGenerateDocs() { run("generate_docs", shell::onGenerateDocs); }
    @FXML public void onTypeEditor() { run("type_editor", shell::onTypeEditor); }
    @FXML public void onGenerateSample() { run("generate_sample", shell::onGenerateSample); }
    @FXML public void onCompare() { run("compare", shell::onCompare); }
    @FXML public void onSpreadsheet() { run("spreadsheet", shell::onSpreadsheet); }
    @FXML public void onAddToFavorites() { run("add_favorite", shell::addActiveDocumentToFavorites); }

    @FXML public void onDocs() { run("docs", () -> ProjectLinks.openInBrowser(ProjectLinks.DOCS_URL)); }
    @FXML public void onShortcuts() { run("shortcuts", KeyboardShortcutsDialog::show); }
    @FXML public void onReportProblem() { run("report_problem", () -> ErrorReportDialog.show(window(), null)); }
    @FXML public void onGitHub() { run("github", () -> ProjectLinks.openInBrowser(ProjectLinks.GITHUB_URL)); }
    @FXML public void onSponsor() { run("sponsor", () -> ProjectLinks.openInBrowser(ProjectLinks.SPONSORS_URL)); }
    @FXML public void onFundsXmlSite() {
        run("fundsxml_site", () -> ProjectLinks.openInBrowser(ProjectLinks.FUNDSXML_SITE_URL));
    }
    @FXML public void onFundsXmlDocs() {
        run("fundsxml_docs", () -> ProjectLinks.openInBrowser(ProjectLinks.FUNDSXML_SCHEMA_DOCS_URL));
    }
    @FXML public void onAbout() { run("about", () -> AboutDialog.show(window())); }

    /** Checks for an application update in the background and reports the outcome in a dialog. */
    @FXML public void onCheckForUpdates() {
        run("check_updates", () -> UpdateActionRunner.check().whenComplete((info, error) -> {
            UsageEvents.updateCheck("manual", UpdateActionRunner.usageResult(info, error),
                    UpdateActionRunner.failureReason(info, error));
            Platform.runLater(() -> {
                if (error != null || info == null) {
                    DialogHelper.showWarning("Check for Updates", "Could not check for updates",
                            error != null && error.getMessage() != null ? error.getMessage()
                                    : "The update service did not answer.");
                } else if (info.updateAvailable()) {
                    AboutDialog.showUpdateDialog(info);
                } else {
                    DialogHelper.showInformation("Check for Updates", "FreeXmlToolkit is up to date",
                            UpdateActionRunner.describe(info));
                }
            });
        }));
    }
}
