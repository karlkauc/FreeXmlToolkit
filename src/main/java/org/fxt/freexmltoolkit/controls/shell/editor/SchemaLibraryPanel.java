package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.*;
import org.fxt.freexmltoolkit.service.*;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Side panel of the Schema Library activity: namespace mappings, registered XML catalogs
 * and the remote schema cache. All disk/network work runs on the shared executor.
 */
public class SchemaLibraryPanel extends VBox {

    private final EditorHost editorHost;
    private final SchemaLibraryService library;
    private final SchemaResourceCache cache;
    private final XmlService xmlService;

    @FXML private TabPane tabs;
    @FXML private Tab mappingsTab;
    @FXML private Tab catalogsTab;
    @FXML private Tab cacheTab;

    // Mappings
    @FXML private TextField filter;
    private final FilteredList<SchemaLibraryEntry> filtered;
    @FXML private TableView<SchemaLibraryEntry> mappings;
    @FXML private TableColumn<SchemaLibraryEntry, SchemaLibraryEntry> statusColumn;
    @FXML private TableColumn<SchemaLibraryEntry, String> namespaceColumn;
    @FXML private TableColumn<SchemaLibraryEntry, String> locationColumn;
    @FXML private TableColumn<SchemaLibraryEntry, String> kindColumn;
    @FXML private TableColumn<SchemaLibraryEntry, String> sourceColumn;
    @FXML private TableColumn<SchemaLibraryEntry, Boolean> enabledColumn;
    @FXML private Button editButton;
    @FXML private Button removeButton;
    @FXML private Button toggleButton;
    @FXML private Button addCurrentButton;
    @FXML private Button downloadButton;
    @FXML private Label status;

    // Catalogs
    @FXML private ListView<SchemaCatalogRef> catalogList;
    @FXML private Button catalogRemoveButton;
    @FXML private Button catalogToggleButton;
    @FXML private Button catalogImportButton;

    // Cache
    private final ObservableList<SchemaCacheEntry> cacheEntries = FXCollections.observableArrayList();
    private final FilteredList<SchemaCacheEntry> cacheFiltered = new FilteredList<>(cacheEntries);
    @FXML private TextField cacheFilter;
    @FXML private TableView<SchemaCacheEntry> cacheTable;
    @FXML private TableColumn<SchemaCacheEntry, String> cacheUrlColumn;
    @FXML private TableColumn<SchemaCacheEntry, String> cacheNamespaceColumn;
    @FXML private TableColumn<SchemaCacheEntry, String> cacheSizeColumn;
    @FXML private TableColumn<SchemaCacheEntry, String> cacheDownloadedColumn;
    @FXML private TableColumn<SchemaCacheEntry, String> cacheHitsColumn;
    @FXML private Button cacheOpenButton;
    @FXML private Button cacheRefreshButton;
    @FXML private Button cacheDeleteButton;
    @FXML private Label cacheFooter;
    @FXML private Label legacyInfo;

    public SchemaLibraryPanel(EditorHost editorHost) {
        this(editorHost, SchemaLibraryServiceImpl.shared(), SchemaResourceCache.shared(),
                ServiceRegistry.get(XmlService.class));
    }

    /** Loads the layout from {@code /pages/panels/SchemaLibraryPanel.fxml} and wires the three tabs. */
    public SchemaLibraryPanel(EditorHost editorHost, SchemaLibraryService library,
                              SchemaResourceCache cache, XmlService xmlService) {
        this.editorHost = editorHost;
        this.library = library;
        this.cache = cache;
        this.xmlService = xmlService;
        filtered = new FilteredList<>(library.getEntries());
        FxmlViews.load(this, "/pages/panels/SchemaLibraryPanel.fxml");

        initMappingsTab();
        initCatalogsTab();
        initCacheTab();
        tabs.getSelectionModel().selectedItemProperty().addListener((o, a, t) -> {
            if (t == cacheTab) refreshCache();
        });
    }

    public void showCacheTab() { tabs.getSelectionModel().select(cacheTab); }

    // ------------------------------------------------------------------ Mappings

    private void initMappingsTab() {
        filter.textProperty().addListener((o, a, text) -> {
            String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(q.isEmpty() ? null : e ->
                    e.namespace().toLowerCase(Locale.ROOT).contains(q)
                            || e.location().toLowerCase(Locale.ROOT).contains(q)
                            || e.description().toLowerCase(Locale.ROOT).contains(q)
                            || (e.rootElement() != null && e.rootElement().toLowerCase(Locale.ROOT).contains(q)));
        });

        mappings.setItems(filtered);
        mappings.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        statusColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        statusColumn.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(SchemaLibraryEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setTooltip(null); return; }
                SchemaEntryStatus st = library.statusOf(item);
                IconifyIcon icon = switch (st) {
                    case LOCAL_OK, CACHED -> icon("bi-check-circle-fill", org.fxt.freexmltoolkit.controls.theme.SemanticColors.SUCCESS);
                    case NOT_DOWNLOADED -> icon("bi-cloud-download", org.fxt.freexmltoolkit.controls.theme.SemanticColors.INFO);
                    case LOCAL_MISSING -> icon("bi-exclamation-triangle-fill", org.fxt.freexmltoolkit.controls.theme.SemanticColors.WARNING);
                    case ERROR -> icon("bi-x-circle-fill", org.fxt.freexmltoolkit.controls.theme.SemanticColors.DANGER);
                };
                setGraphic(icon);
                setTooltip(new Tooltip(st.name().replace('_', ' ').toLowerCase(Locale.ROOT)
                        + library.lastError(item).map(e -> ": " + e).orElse("")));
            }
        });

        namespaceColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                namespaceLabel(cd.getValue())));
        namespaceColumn.setCellFactory(c -> tooltipCell());
        locationColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().location()));
        locationColumn.setCellFactory(c -> tooltipCell());
        kindColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().kind().label()));
        sourceColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().source().name().toLowerCase(Locale.ROOT)));
        enabledColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleBooleanProperty(cd.getValue().enabled()));
        enabledColumn.setCellFactory(c -> new CheckBoxTableCell<>(idx -> {
            SchemaLibraryEntry e = mappings.getItems().get(idx);
            var prop = new javafx.beans.property.SimpleBooleanProperty(e.enabled());
            prop.addListener((o, was, now) -> {
                // Clear the selection synchronously, then defer the observable-list mutation
                // (library.setEnabled -> rebuildSnapshot -> observable.setAll) past this cell-edit
                // commit, avoiding the TableView setAll-while-editing crash trap.
                mappings.getSelectionModel().clearSelection();
                Platform.runLater(() -> library.setEnabled(e.id(), now));
            });
            return prop;
        }));
        mappings.setRowFactory(tv -> {
            TableRow<SchemaLibraryEntry> row = new TableRow<>() {
                @Override protected void updateItem(SchemaLibraryEntry item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().remove("fxt-lib-bundled");
                    if (!empty && item != null && item.source() == EntrySource.BUNDLED) getStyleClass().add("fxt-lib-bundled");
                }
            };
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) openEntry(row.getItem());
            });
            return row;
        });
        mappings.setContextMenu(mappingsContextMenu());

        var selected = mappings.getSelectionModel().selectedItemProperty();
        editButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> selected.get() == null || selected.get().source() != EntrySource.USER, selected));
        removeButton.disableProperty().bind(editButton.disableProperty());
        toggleButton.disableProperty().bind(selected.isNull());
        downloadButton.disableProperty().bind(selected.isNull());
        addCurrentButton.disableProperty().bind(editorHost.activeSchemaProperty().isNull());
    }

    // ------------------------------------------------------------------ Catalogs

    private void initCatalogsTab() {
        catalogList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(SchemaCatalogRef ref, boolean empty) {
                super.updateItem(ref, empty);
                if (empty || ref == null) { setGraphic(null); setText(null); return; }
                Label path = new Label(ref.path());
                path.getStyleClass().add("fxt-lib-catalog-path");
                String err = library.catalogErrors().get(ref.id());
                Label info = new Label();
                if (err != null) {
                    info.setText("Error: " + err);
                    info.getStyleClass().add("fxt-lib-catalog-error");
                } else {
                    info.setText(library.catalogEntryCount(ref.id()) + " entries" + (ref.enabled() ? "" : " (disabled)"));
                    info.getStyleClass().add("fxt-lib-catalog-count");
                }
                info.setWrapText(true);
                VBox box = new VBox(2, path, info);
                box.setOpacity(ref.enabled() ? 1.0 : 0.6);
                setGraphic(box);
            }
        });
        catalogList.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2) {
                SchemaCatalogRef s = catalogList.getSelectionModel().getSelectedItem();
                if (s != null) editorHost.openFile(s.asPath().toFile());
            }
        });

        var selected = catalogList.getSelectionModel().selectedItemProperty();
        catalogRemoveButton.disableProperty().bind(selected.isNull());
        catalogToggleButton.disableProperty().bind(selected.isNull());
        catalogImportButton.disableProperty().bind(selected.isNull());
        refreshCatalogs();
    }

    @FXML
    public void onRemoveCatalog() {
        SchemaCatalogRef s = catalogList.getSelectionModel().getSelectedItem();
        if (s != null) { catalogList.getSelectionModel().clearSelection(); library.removeCatalog(s.id()); refreshCatalogs(); }
    }

    @FXML
    public void onToggleCatalog() {
        SchemaCatalogRef s = catalogList.getSelectionModel().getSelectedItem();
        if (s != null) { library.setCatalogEnabled(s.id(), !s.enabled()); refreshCatalogs(); }
    }

    @FXML
    public void onReloadCatalogs() {
        library.reloadCatalogs();
        refreshCatalogs();
    }

    void refreshCatalogs() {
        catalogList.getSelectionModel().clearSelection();
        catalogList.getItems().setAll(library.getCatalogs());
    }

    // ------------------------------------------------------------------ Cache

    private void initCacheTab() {
        cacheFilter.textProperty().addListener((o, a, text) -> {
            String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            // remoteUrl() is null for files indexed from disk without an index entry
            // (SchemaResourceCache.loadExistingCache), so never dereference it directly.
            cacheFiltered.setPredicate(q.isEmpty() ? null : e ->
                    (e.remoteUrl() != null && e.remoteUrl().toLowerCase(Locale.ROOT).contains(q))
                            || e.localFilename().toLowerCase(Locale.ROOT).contains(q)
                            || (e.schema() != null && e.schema().targetNamespace() != null
                                && e.schema().targetNamespace().toLowerCase(Locale.ROOT).contains(q)));
        });

        cacheTable.setItems(cacheFiltered);
        cacheTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        cacheUrlColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(urlOf(cd.getValue())));
        cacheUrlColumn.setCellFactory(c -> tooltipCell());
        cacheNamespaceColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().schema() == null || cd.getValue().schema().targetNamespace() == null ? "" : cd.getValue().schema().targetNamespace()));
        cacheNamespaceColumn.setCellFactory(c -> tooltipCell());
        cacheSizeColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                org.apache.commons.io.FileUtils.byteCountToDisplaySize(cd.getValue().fileSizeBytes())));
        cacheDownloadedColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().downloadTimestamp() == null ? "" :
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                .withZone(java.time.ZoneId.systemDefault()).format(cd.getValue().downloadTimestamp())));
        cacheHitsColumn.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().usage() == null ? "0" : String.valueOf(cd.getValue().usage().accessCount())));
        cacheTable.setRowFactory(tv -> {
            TableRow<SchemaCacheEntry> row = new TableRow<>();
            row.setOnMouseClicked(ev -> { if (ev.getClickCount() == 2 && !row.isEmpty()) editorHost.openFile(cache.pathOf(row.getItem()).toFile()); });
            return row;
        });

        var selected = cacheTable.getSelectionModel().selectedItemProperty();
        cacheOpenButton.disableProperty().bind(selected.isNull());
        // A file indexed from disk has no remote URL, so there is nothing to re-download.
        cacheRefreshButton.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> selected.get() == null || selected.get().remoteUrl() == null, selected));
        cacheDeleteButton.disableProperty().bind(selected.isNull());
    }

    @FXML
    public void onOpenCached() {
        var s = cacheTable.getSelectionModel().getSelectedItem();
        if (s != null) editorHost.openFile(cache.pathOf(s).toFile());
    }

    @FXML
    public void onRevealCacheFolder() {
        var s = cacheTable.getSelectionModel().getSelectedItem();
        if (s != null) org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            try { java.awt.Desktop.getDesktop().open(cache.getCacheDirectory().toFile()); }
            catch (Exception e) { Platform.runLater(() -> setStatus("Cannot open folder: " + e.getMessage())); }
        });
    }

    @FXML
    public void onRefreshCached() {
        var s = cacheTable.getSelectionModel().getSelectedItem();
        if (s == null || s.remoteUrl() == null) return;   // nothing to re-download
        String remote = s.remoteUrl();
        setStatus("Refreshing " + remote + "…");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            var result = cache.refresh(remote);
            Platform.runLater(() -> { setStatus(result.isPresent() ? "Refreshed " + remote : "Refresh failed for " + remote); refreshCache(); });
        });
    }

    @FXML
    public void onDeleteCached() {
        var s = cacheTable.getSelectionModel().getSelectedItem();
        if (s != null && DialogHelper.showConfirmation("Delete Cached Schema", "Delete the cached copy of\n" + urlOf(s) + "?",
                "It will be downloaded again on next use.")) {
            deleteSelectedCacheEntryWithoutConfirm();
        }
    }

    @FXML
    public void onClearCache() {
        if (DialogHelper.showConfirmation("Clear Schema Cache", "Delete all cached remote schemas?",
                cache.getCacheDirectory() + "\n\nThis action cannot be undone.")) {
            org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                int n = cache.clearCache();
                Platform.runLater(() -> { setStatus("Deleted " + n + " cached file(s)."); refreshCache(); });
            });
        }
    }

    /** Clears the legacy auto-detected cache (~/.freeXmlToolkit/cache/&lt;MD5&gt;/). */
    @FXML
    public void onClearLegacyCache() {
        if (DialogHelper.showConfirmation("Clear Auto-detected Schemas", "Delete all schemas cached from xsi:schemaLocation downloads?",
                "They are downloaded again when a document referencing them is opened.")) {
            org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                int n = xmlService.clearAutoDetectedSchemaCache();
                Platform.runLater(() -> { setStatus("Deleted " + n + " file(s)."); refreshCache(); });
            });
        }
    }

    /**
     * Namespace column text: the namespace, or - for a no-namespace entry - the root element
     * it binds. Some no-namespace entries (older X3D versions) declare no root element and
     * are only reachable by their location, so they render as plain "(no namespace)".
     */
    private static String namespaceLabel(SchemaLibraryEntry entry) {
        if (!entry.namespace().isEmpty()) return entry.namespace();
        return entry.rootElement() != null ? "<" + entry.rootElement() + "> (no namespace)" : "(no namespace)";
    }

    /**
     * Display text for a cache entry's URL column and status messages: entries indexed from
     * disk ({@code SchemaResourceCache.loadExistingCache}) carry no {@code remoteUrl}, so
     * their local filename is shown instead of {@code null}.
     */
    private static String urlOf(SchemaCacheEntry entry) {
        return entry.remoteUrl() != null ? entry.remoteUrl() : entry.localFilename() + " (local copy)";
    }

    /** Reloads the cache table and footer (FX thread). */
    void refreshCache() {
        cacheTable.getSelectionModel().clearSelection();
        cacheEntries.setAll(cache.listEntries());
        var stats = cache.getStats();
        // Note: stats.totalFiles() counts every file physically in the cache directory,
        // including cache-index.json itself, so it overcounts by one vs. the actual number
        // of cached schemas. Use the entry count (== table row count) instead.
        cacheFooter.setText(cacheEntries.size() + " file(s), " + stats.getTotalSizeFormatted()
                + ", hit ratio " + String.format(Locale.ROOT, "%.0f%%", stats.getHitRatio())
                + "  —  " + cache.getCacheDirectory());
        var dirs = xmlService.listAutoDetectedSchemaCacheDirs();
        legacyInfo.setText(dirs.isEmpty() ? "Empty." : dirs.size() + " cached schema folder(s) under " + dirs.getFirst().getParent());
    }

    /** Test seam: deletes the selected cache entry without confirmation. */
    void deleteSelectedCacheEntryWithoutConfirm() {
        var s = cacheTable.getSelectionModel().getSelectedItem();
        if (s == null) return;
        cacheTable.getSelectionModel().clearSelection();
        cache.removeEntry(s.localFilename());
        refreshCache();
        setStatus("Deleted cached copy of " + urlOf(s));
    }

    @FXML
    public void chooseCatalog() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select XML catalog");
        fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("XML catalogs", "*.xml"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File f = fc.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (f != null) addCatalogFile(f.toPath());
    }

    /** Registers {@code catalog} (also used by tests, no chooser). */
    void addCatalogFile(Path catalog) {
        library.addCatalog(catalog);
        refreshCatalogs();
        String err = library.catalogErrors().get(library.getCatalogs().getLast().id());
        setStatus(err != null ? "Catalog added but unparsable: " + err : "Catalog added: " + catalog.getFileName());
    }

    @FXML
    public void importSelectedCatalog() {
        SchemaCatalogRef s = catalogList.getSelectionModel().getSelectedItem();
        if (s == null) return;
        try {
            var preview = library.importCatalog(s.asPath());
            if (preview.isEmpty()) { setStatus("No importable namespace mappings in " + s.asPath().getFileName()); return; }
            var existing = library.getEntries().stream().map(SchemaLibraryEntry::key).collect(java.util.stream.Collectors.toSet());
            new CatalogImportDialog(preview, existing).showAndWait().ifPresent(chosen -> {
                long alreadyPresent = chosen.stream().filter(e -> existing.contains(e.key())).count();
                int added = importEntries(chosen);
                String msg = "Imported " + added + " mapping(s) from " + s.asPath().getFileName();
                if (alreadyPresent > 0) msg += ", skipped " + alreadyPresent + " already present";
                setStatus(msg);
                tabs.getSelectionModel().select(mappingsTab);
            });
        } catch (java.io.IOException e) {
            setStatus("Cannot read catalog: " + e.getMessage());
        }
    }

    /**
     * Adds {@code chosen} entries to the library as USER mappings, skipping any whose
     * {@code kind|namespace} key already exists (addEntry only de-dups by id, not by key,
     * so re-importing an already-present mapping would otherwise create a duplicate).
     * Returns the number of entries actually added. Package-private test seam.
     */
    int importEntries(java.util.List<SchemaLibraryEntry> chosen) {
        var existingKeys = library.getEntries().stream().map(SchemaLibraryEntry::key).collect(java.util.stream.Collectors.toSet());
        int added = 0;
        for (SchemaLibraryEntry e : chosen) {
            if (existingKeys.contains(e.key())) continue;
            try {
                library.addEntry(e.withSource(EntrySource.USER));
                existingKeys.add(e.key());
                added++;
            } catch (IllegalArgumentException ex) {
                setStatus("Skipped " + e.namespace() + ": " + ex.getMessage());
            }
        }
        return added;
    }

    private ContextMenu mappingsContextMenu() {
        MenuItem open = new MenuItem("Open schema", SemanticIcon.paint(new IconifyIcon("bi-box-arrow-up-right"), ActionColor.NAVIGATE));
        open.setOnAction(e -> { var s = mappings.getSelectionModel().getSelectedItem(); if (s != null) openEntry(s); });
        MenuItem edit = new MenuItem("Edit…", SemanticIcon.paint(new IconifyIcon("bi-pencil"), ActionColor.MODIFY));
        edit.setOnAction(e -> editSelected());
        MenuItem remove = new MenuItem("Remove", SemanticIcon.paint(new IconifyIcon("bi-trash"), ActionColor.DELETE));
        remove.setOnAction(e -> removeSelected());
        MenuItem copy = new MenuItem("Copy namespace", SemanticIcon.paint(new IconifyIcon("bi-clipboard"), ActionColor.NEUTRAL));
        copy.setOnAction(e -> {
            var s = mappings.getSelectionModel().getSelectedItem();
            if (s != null) {
                var cc = new javafx.scene.input.ClipboardContent();
                cc.putString(s.namespace());
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
            }
        });
        return new ContextMenu(open, edit, remove, new SeparatorMenuItem(), copy);
    }

    @FXML
    public void addEntry() {
        new SchemaLibraryEntryDialog(library, null).showAndWait().ifPresent(e -> {
            try { library.addEntry(e); setStatus("Added mapping for " + display(e)); }
            catch (IllegalArgumentException ex) { setStatus(ex.getMessage()); }
        });
    }

    @FXML
    public void editSelected() {
        SchemaLibraryEntry s = mappings.getSelectionModel().getSelectedItem();
        if (s == null || s.source() != EntrySource.USER) return;
        new SchemaLibraryEntryDialog(library, s).showAndWait().ifPresent(e -> {
            try { library.updateEntry(e); setStatus("Updated mapping for " + display(e)); }
            catch (IllegalArgumentException ex) { setStatus(ex.getMessage()); }
        });
    }

    @FXML
    public void removeSelected() {
        SchemaLibraryEntry s = mappings.getSelectionModel().getSelectedItem();
        if (s == null || s.source() != EntrySource.USER) return;
        if (DialogHelper.showConfirmation("Remove Mapping", "Remove the mapping for " + display(s) + "?",
                "The schema file itself is not deleted.")) {
            removeSelectedWithoutConfirm();
        }
    }

    /** Test seam: removes the selected USER entry without the confirmation dialog. */
    void removeSelectedWithoutConfirm() {
        SchemaLibraryEntry s = mappings.getSelectionModel().getSelectedItem();
        if (s != null && s.source() == EntrySource.USER) {
            mappings.getSelectionModel().clearSelection();
            library.removeEntry(s.id());
            setStatus("Removed mapping for " + display(s));
        }
    }

    @FXML
    public void toggleSelected() {
        SchemaLibraryEntry s = mappings.getSelectionModel().getSelectedItem();
        if (s == null) return;
        String id = s.id();
        boolean newValue = !s.enabled();
        // Clear the selection before the observable list is re-set (library.setEnabled ->
        // rebuildSnapshot -> observable.setAll) to avoid the TableView setAll-while-selected
        // crash trap, then restore the selection by id once the new snapshot is in place.
        mappings.getSelectionModel().clearSelection();
        library.setEnabled(id, newValue);
        Platform.runLater(() -> mappings.getItems().stream()
                .filter(e -> e.id().equals(id)).findFirst()
                .ifPresent(e -> mappings.getSelectionModel().select(e)));
    }

    @FXML
    public void addCurrentSchema() {
        File xsd = editorHost.activeSchemaProperty().get();
        if (xsd == null) return;
        library.entryFromFile(xsd.toPath()).ifPresent(pre ->
                new SchemaLibraryEntryDialog(library, pre, true).showAndWait().ifPresent(e -> {
                    try { library.addEntry(e); setStatus("Added mapping for " + display(e)); }
                    catch (IllegalArgumentException ex) { setStatus(ex.getMessage()); }
                }));
    }

    @FXML
    public void downloadSelected() {
        SchemaLibraryEntry s = mappings.getSelectionModel().getSelectedItem();
        if (s == null) return;
        if (library instanceof SchemaLibraryServiceImpl impl) impl.clearFailure(s);
        setStatus("Checking " + s.location() + "…");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            var result = library.materialize(s);
            Platform.runLater(() -> {
                setStatus(result.map(p -> "Available: " + p).orElse("Failed: " + library.lastError(s).orElse("unknown error")));
                mappings.refresh();
            });
        });
    }

    private void openEntry(SchemaLibraryEntry e) {
        setStatus("Opening " + display(e) + "…");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            var file = library.materialize(e);
            Platform.runLater(() -> file.ifPresentOrElse(p -> { editorHost.openFile(p.toFile()); setStatus(""); },
                    () -> setStatus("Cannot open: " + library.lastError(e).orElse("schema not available"))));
        });
    }

    private static String display(SchemaLibraryEntry e) {
        return e.namespace().isEmpty() ? "<" + e.rootElement() + ">" : e.namespace();
    }

    void setStatus(String text) { status.setText(text == null ? "" : text); }

    /**
     * Text cell whose tooltip always carries the full, untruncated value — the column itself
     * may be narrower than the text (namespace URIs, remote URLs) and JavaFX clips the label
     * with an ellipsis, so hovering is the only way to read the rest.
     */
    private static <T> TableCell<T, String> tooltipCell() {
        return new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    setTooltip(new Tooltip(item));
                }
            }
        };
    }

    private static IconifyIcon icon(String literal, String color) {
        IconifyIcon i = new IconifyIcon(literal);
        i.setIconSize(14);
        i.iconColorProperty().bind(new javafx.beans.property.SimpleObjectProperty<>(javafx.scene.paint.Color.web(color)));
        return i;
    }
}
