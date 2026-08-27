package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.*;
import org.fxt.freexmltoolkit.service.*;
import org.fxt.freexmltoolkit.util.DialogHelper;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Side panel of the Schema Library activity: namespace mappings, registered XML catalogs
 * and the remote schema cache. All disk/network work runs on the shared executor.
 */
public class SchemaLibraryPanel extends VBox {

    private final EditorHost editorHost;
    private final SchemaLibraryService library;
    private final SchemaResourceCache cache;
    private final XmlService xmlService;

    private final TabPane tabs = new TabPane();
    private final Tab mappingsTab = new Tab();
    private final Tab catalogsTab = new Tab();
    private final Tab cacheTab = new Tab();

    // Mappings
    private final TextField filter = new TextField();
    private final FilteredList<SchemaLibraryEntry> filtered;
    private final TableView<SchemaLibraryEntry> mappings = new TableView<>();
    private final Label status = new Label();

    // Catalogs
    private final ListView<SchemaCatalogRef> catalogList = new ListView<>();

    // Cache
    private final ObservableList<SchemaCacheEntry> cacheEntries = FXCollections.observableArrayList();
    private final FilteredList<SchemaCacheEntry> cacheFiltered = new FilteredList<>(cacheEntries);
    private final TableView<SchemaCacheEntry> cacheTable = new TableView<>(cacheFiltered);
    private final Label cacheFooter = new Label();
    private final Label legacyInfo = new Label();

    public SchemaLibraryPanel(EditorHost editorHost) {
        this(editorHost, SchemaLibraryServiceImpl.shared(), SchemaResourceCache.shared(),
                ServiceRegistry.get(XmlService.class));
    }

    public SchemaLibraryPanel(EditorHost editorHost, SchemaLibraryService library,
                              SchemaResourceCache cache, XmlService xmlService) {
        this.editorHost = editorHost;
        this.library = library;
        this.cache = cache;
        this.xmlService = xmlService;
        getStyleClass().add("fxt-schema-library-panel");

        Label title = new Label("SCHEMA LIBRARY");
        title.getStyleClass().addAll("fxt-side-panel-title", "fxt-vp-title");
        HBox header = new HBox(title);
        header.getStyleClass().add("fxt-vp-header");
        header.setAlignment(Pos.CENTER_LEFT);

        filtered = new FilteredList<>(library.getEntries());
        mappingsTab.setContent(buildMappingsTab());
        catalogsTab.setContent(buildCatalogsTab());
        cacheTab.setContent(buildCacheTab());
        tabs.getSelectionModel().selectedItemProperty().addListener((o, a, t) -> {
            if (t == cacheTab) refreshCache();
        });
        for (Tab t : new Tab[]{mappingsTab, catalogsTab, cacheTab}) {
            t.setClosable(false);
            t.getStyleClass().add("utility-tab");
        }
        // Icon-only tabs (with a tooltip carrying the full name): at the default side-panel
        // width (~260px) even text-only labels ("Mappings"/"Catalogs"/"Cache") were too wide
        // for all three to fit without triggering the TabPane overflow menu. A 16px icon per
        // tab plus the tightened header/tab padding below comfortably fits three tabs.
        mappingsTab.setGraphic(tabIcon("bi-diagram-3"));
        catalogsTab.setGraphic(tabIcon("bi-journal-bookmark"));
        cacheTab.setGraphic(tabIcon("bi-hdd"));
        mappingsTab.setTooltip(new Tooltip("Mappings"));
        catalogsTab.setTooltip(new Tooltip("Catalogs"));
        cacheTab.setTooltip(new Tooltip("Cache"));
        tabs.setTabMinWidth(0);
        tabs.setId("schema-library-tabs");
        tabs.getTabs().addAll(mappingsTab, catalogsTab, cacheTab);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        status.setId("library-status");
        status.getStyleClass().add("fxt-lib-status");
        status.setWrapText(true);
        getChildren().addAll(header, tabs, status);
    }

    public void showCacheTab() { tabs.getSelectionModel().select(cacheTab); }

    // ------------------------------------------------------------------ Mappings

    private Node buildMappingsTab() {
        filter.setId("library-filter");
        filter.setPromptText("Filter namespace, location, description, root element…");
        filter.textProperty().addListener((o, a, text) -> {
            String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(q.isEmpty() ? null : e ->
                    e.namespace().toLowerCase(Locale.ROOT).contains(q)
                            || e.location().toLowerCase(Locale.ROOT).contains(q)
                            || e.description().toLowerCase(Locale.ROOT).contains(q)
                            || (e.rootElement() != null && e.rootElement().toLowerCase(Locale.ROOT).contains(q)));
        });

        mappings.setId("library-mappings-table");
        mappings.setItems(filtered);
        mappings.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        mappings.setPlaceholder(new Label("No schema mappings yet. Click Add to map a namespace to a schema."));

        TableColumn<SchemaLibraryEntry, SchemaLibraryEntry> statusCol = new TableColumn<>("");
        statusCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        statusCol.setCellFactory(c -> new TableCell<>() {
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
        statusCol.setPrefWidth(28); statusCol.setMinWidth(28); statusCol.setMaxWidth(28);

        TableColumn<SchemaLibraryEntry, String> nsCol = new TableColumn<>("Namespace");
        nsCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                namespaceLabel(cd.getValue())));
        nsCol.setCellFactory(c -> tooltipCell());
        nsCol.setMinWidth(60);
        TableColumn<SchemaLibraryEntry, String> locCol = new TableColumn<>("Location");
        locCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().location()));
        locCol.setCellFactory(c -> tooltipCell());
        locCol.setMinWidth(60);
        // Kind and Source are hidden by default: at the default ~260px side-panel width there
        // is only room for Namespace/Location to stay readable. Both remain reachable via the
        // table's column-visibility menu button (setTableMenuButtonVisible below).
        TableColumn<SchemaLibraryEntry, String> kindCol = new TableColumn<>("Kind");
        kindCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().kind().label()));
        kindCol.setVisible(false);
        TableColumn<SchemaLibraryEntry, String> srcCol = new TableColumn<>("Source");
        srcCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().source().name().toLowerCase(Locale.ROOT)));
        srcCol.setVisible(false);
        TableColumn<SchemaLibraryEntry, Boolean> enabledCol = new TableColumn<>("On");
        enabledCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleBooleanProperty(cd.getValue().enabled()));
        enabledCol.setCellFactory(c -> new CheckBoxTableCell<>(idx -> {
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
        enabledCol.setPrefWidth(40); enabledCol.setMinWidth(40); enabledCol.setMaxWidth(40);
        mappings.getColumns().setAll(java.util.List.of(statusCol, nsCol, locCol, kindCol, srcCol, enabledCol));
        mappings.setTableMenuButtonVisible(true);
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
        Button add = toolButton("library-add", "Add mapping…", "bi-plus-circle", this::addEntry);
        Button edit = toolButton("library-edit", "Edit…", "bi-pencil", this::editSelected);
        Button remove = toolButton("library-remove", "Remove", "bi-trash", this::removeSelected);
        Button toggle = toolButton("library-toggle", "Enable / disable", "bi-toggle-on", this::toggleSelected);
        Button addCurrent = toolButton("library-add-current", "Add schema of current document", "bi-file-earmark-plus", this::addCurrentSchema);
        Button download = toolButton("library-download", "Download / verify", "bi-cloud-download", this::downloadSelected);
        edit.disableProperty().bind(Bindings.createBooleanBinding(
                () -> selected.get() == null || selected.get().source() != EntrySource.USER, selected));
        remove.disableProperty().bind(edit.disableProperty());
        toggle.disableProperty().bind(selected.isNull());
        download.disableProperty().bind(selected.isNull());
        addCurrent.disableProperty().bind(editorHost.activeSchemaProperty().isNull());
        FlowPane tools = new FlowPane(2, 2, add, edit, remove, toggle, addCurrent, download);
        tools.getStyleClass().add("fxt-schema-tools");

        VBox box = new VBox(4, tools, filter, mappings);
        VBox.setVgrow(mappings, Priority.ALWAYS);
        return box;
    }

    // ------------------------------------------------------------------ Catalogs

    private Node buildCatalogsTab() {
        catalogList.setId("library-catalogs-list");
        catalogList.setPlaceholder(new Label("No XML catalogs registered. Add an OASIS catalog.xml to map system IDs and URIs."));
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
        Button add = toolButton("library-catalog-add", "Add catalog…", "bi-plus-circle", this::chooseCatalog);
        Button remove = toolButton("library-catalog-remove", "Remove", "bi-trash", () -> {
            SchemaCatalogRef s = selected.get();
            if (s != null) { catalogList.getSelectionModel().clearSelection(); library.removeCatalog(s.id()); refreshCatalogs(); }
        });
        Button toggle = toolButton("library-catalog-toggle", "Enable / disable", "bi-toggle-on", () -> {
            SchemaCatalogRef s = selected.get();
            if (s != null) { library.setCatalogEnabled(s.id(), !s.enabled()); refreshCatalogs(); }
        });
        Button reload = toolButton("library-catalog-reload", "Reload catalogs", "bi-arrow-clockwise", () -> { library.reloadCatalogs(); refreshCatalogs(); });
        Button importBtn = toolButton("library-catalog-import", "Import entries into Mappings…", "bi-box-arrow-in-down", this::importSelectedCatalog);
        remove.disableProperty().bind(selected.isNull());
        toggle.disableProperty().bind(selected.isNull());
        importBtn.disableProperty().bind(selected.isNull());
        FlowPane tools = new FlowPane(2, 2, add, remove, toggle, reload, importBtn);
        tools.getStyleClass().add("fxt-schema-tools");

        VBox box = new VBox(4, tools, catalogList);
        VBox.setVgrow(catalogList, Priority.ALWAYS);
        refreshCatalogs();
        return box;
    }

    void refreshCatalogs() {
        catalogList.getSelectionModel().clearSelection();
        catalogList.getItems().setAll(library.getCatalogs());
    }

    // ------------------------------------------------------------------ Cache

    private Node buildCacheTab() {
        TextField cacheFilter = new TextField();
        cacheFilter.setId("library-cache-filter");
        cacheFilter.setPromptText("Filter URL or namespace…");
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

        cacheTable.setId("library-cache-table");
        cacheTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        cacheTable.setPlaceholder(new Label("No cached remote schemas."));
        TableColumn<SchemaCacheEntry, String> url = new TableColumn<>("URL");
        url.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(urlOf(cd.getValue())));
        url.setCellFactory(c -> tooltipCell());
        url.setMinWidth(80);
        TableColumn<SchemaCacheEntry, String> ns = new TableColumn<>("Target namespace");
        ns.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().schema() == null || cd.getValue().schema().targetNamespace() == null ? "" : cd.getValue().schema().targetNamespace()));
        ns.setCellFactory(c -> tooltipCell());
        TableColumn<SchemaCacheEntry, String> size = new TableColumn<>("Size");
        size.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                org.apache.commons.io.FileUtils.byteCountToDisplaySize(cd.getValue().fileSizeBytes())));
        // Downloaded and Hits are hidden by default to keep URL/Size readable at the default
        // ~260px side-panel width; both remain reachable via the table menu button below.
        TableColumn<SchemaCacheEntry, String> when = new TableColumn<>("Downloaded");
        when.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().downloadTimestamp() == null ? "" :
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                .withZone(java.time.ZoneId.systemDefault()).format(cd.getValue().downloadTimestamp())));
        when.setVisible(false);
        TableColumn<SchemaCacheEntry, String> hits = new TableColumn<>("Hits");
        hits.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().usage() == null ? "0" : String.valueOf(cd.getValue().usage().accessCount())));
        hits.setVisible(false);
        cacheTable.getColumns().setAll(java.util.List.of(url, ns, size, when, hits));
        cacheTable.setTableMenuButtonVisible(true);
        cacheTable.setRowFactory(tv -> {
            TableRow<SchemaCacheEntry> row = new TableRow<>();
            row.setOnMouseClicked(ev -> { if (ev.getClickCount() == 2 && !row.isEmpty()) editorHost.openFile(cache.pathOf(row.getItem()).toFile()); });
            return row;
        });

        var selected = cacheTable.getSelectionModel().selectedItemProperty();
        Button open = toolButton("library-cache-open", "Open cached file", "bi-box-arrow-up-right",
                () -> { var s = selected.get(); if (s != null) editorHost.openFile(cache.pathOf(s).toFile()); });
        Button reveal = toolButton("library-cache-reveal", "Show in system file manager", "bi-folder2-open", () -> {
            var s = selected.get();
            if (s != null) org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                try { java.awt.Desktop.getDesktop().open(cache.getCacheDirectory().toFile()); }
                catch (Exception e) { Platform.runLater(() -> setStatus("Cannot open folder: " + e.getMessage())); }
            });
        });
        Button refresh = toolButton("library-cache-refresh", "Re-download", "bi-arrow-clockwise", () -> {
            var s = selected.get();
            if (s == null || s.remoteUrl() == null) return;   // nothing to re-download
            String remote = s.remoteUrl();
            setStatus("Refreshing " + remote + "…");
            org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                var result = cache.refresh(remote);
                Platform.runLater(() -> { setStatus(result.isPresent() ? "Refreshed " + remote : "Refresh failed for " + remote); refreshCache(); });
            });
        });
        Button delete = toolButton("library-cache-delete", "Delete cached file", "bi-trash", () -> {
            var s = selected.get();
            if (s != null && DialogHelper.showConfirmation("Delete Cached Schema", "Delete the cached copy of\n" + urlOf(s) + "?",
                    "It will be downloaded again on next use.")) {
                deleteSelectedCacheEntryWithoutConfirm();
            }
        });
        Button clear = toolButton("library-cache-clear", "Clear entire cache", "bi-x-octagon", () -> {
            if (DialogHelper.showConfirmation("Clear Schema Cache", "Delete all cached remote schemas?",
                    cache.getCacheDirectory() + "\n\nThis action cannot be undone.")) {
                org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                    int n = cache.clearCache();
                    Platform.runLater(() -> { setStatus("Deleted " + n + " cached file(s)."); refreshCache(); });
                });
            }
        });
        open.disableProperty().bind(selected.isNull());
        // A file indexed from disk has no remote URL, so there is nothing to re-download.
        refresh.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> selected.get() == null || selected.get().remoteUrl() == null, selected));
        delete.disableProperty().bind(selected.isNull());
        FlowPane tools = new FlowPane(2, 2, open, reveal, refresh, delete, clear);
        tools.getStyleClass().add("fxt-schema-tools");

        cacheFooter.setId("library-cache-footer");
        cacheFooter.getStyleClass().add("fxt-lib-status");

        // Legacy auto-detected cache (~/.freeXmlToolkit/cache/<MD5>/), read-only + clear
        legacyInfo.getStyleClass().add("fxt-lib-status");
        legacyInfo.setWrapText(true);
        Button clearLegacy = toolButton("library-legacy-cache-clear", "Clear auto-detected schema cache", "bi-trash", () -> {
            if (DialogHelper.showConfirmation("Clear Auto-detected Schemas", "Delete all schemas cached from xsi:schemaLocation downloads?",
                    "They are downloaded again when a document referencing them is opened.")) {
                org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                    int n = xmlService.clearAutoDetectedSchemaCache();
                    Platform.runLater(() -> { setStatus("Deleted " + n + " file(s)."); refreshCache(); });
                });
            }
        });
        TitledPane legacy = new TitledPane("Auto-detected schemas (legacy cache)", new VBox(4, legacyInfo, clearLegacy));
        legacy.setExpanded(false);

        VBox box = new VBox(4, tools, cacheFilter, cacheTable, cacheFooter, legacy);
        VBox.setVgrow(cacheTable, Priority.ALWAYS);
        return box;
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

    private void chooseCatalog() {
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

    private void importSelectedCatalog() {
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
        MenuItem open = new MenuItem("Open schema", new IconifyIcon("bi-box-arrow-up-right"));
        open.setOnAction(e -> { var s = mappings.getSelectionModel().getSelectedItem(); if (s != null) openEntry(s); });
        MenuItem edit = new MenuItem("Edit…", new IconifyIcon("bi-pencil"));
        edit.setOnAction(e -> editSelected());
        MenuItem remove = new MenuItem("Remove", new IconifyIcon("bi-trash"));
        remove.setOnAction(e -> removeSelected());
        MenuItem copy = new MenuItem("Copy namespace", new IconifyIcon("bi-clipboard"));
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

    private void addEntry() {
        new SchemaLibraryEntryDialog(library, null).showAndWait().ifPresent(e -> {
            try { library.addEntry(e); setStatus("Added mapping for " + display(e)); }
            catch (IllegalArgumentException ex) { setStatus(ex.getMessage()); }
        });
    }

    private void editSelected() {
        SchemaLibraryEntry s = mappings.getSelectionModel().getSelectedItem();
        if (s == null || s.source() != EntrySource.USER) return;
        new SchemaLibraryEntryDialog(library, s).showAndWait().ifPresent(e -> {
            try { library.updateEntry(e); setStatus("Updated mapping for " + display(e)); }
            catch (IllegalArgumentException ex) { setStatus(ex.getMessage()); }
        });
    }

    private void removeSelected() {
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

    private void toggleSelected() {
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

    private void addCurrentSchema() {
        File xsd = editorHost.activeSchemaProperty().get();
        if (xsd == null) return;
        library.entryFromFile(xsd.toPath()).ifPresent(pre ->
                new SchemaLibraryEntryDialog(library, pre, true).showAndWait().ifPresent(e -> {
                    try { library.addEntry(e); setStatus("Added mapping for " + display(e)); }
                    catch (IllegalArgumentException ex) { setStatus(ex.getMessage()); }
                }));
    }

    private void downloadSelected() {
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

    /** 16px icon for a tab graphic (icon-only tabs; the full name lives in the Tab's tooltip). */
    private static IconifyIcon tabIcon(String literal) {
        IconifyIcon i = new IconifyIcon(literal);
        i.setIconSize(16);
        return i;
    }

    private static IconifyIcon icon(String literal, String color) {
        IconifyIcon i = new IconifyIcon(literal);
        i.setIconSize(14);
        i.iconColorProperty().bind(new javafx.beans.property.SimpleObjectProperty<>(javafx.scene.paint.Color.web(color)));
        return i;
    }

    private static Button toolButton(String id, String tooltip, String iconLiteral, Runnable action) {
        IconifyIcon i = new IconifyIcon(iconLiteral);
        i.setIconSize(16);
        Button b = new Button(null, i);
        b.setId(id);
        b.setTooltip(new Tooltip(tooltip));
        b.getStyleClass().add("fxt-tool-button");
        b.setOnAction(e -> action.run());
        return b;
    }
}
