package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
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
    private final Tab mappingsTab = new Tab("Mappings");
    private final Tab catalogsTab = new Tab("Catalogs");
    private final Tab cacheTab = new Tab("Cache");

    // Mappings
    private final TextField filter = new TextField();
    private final FilteredList<SchemaLibraryEntry> filtered;
    private final TableView<SchemaLibraryEntry> mappings = new TableView<>();
    private final Label status = new Label();

    // Catalogs
    private final ListView<SchemaCatalogRef> catalogList = new ListView<>();

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
        cacheTab.setContent(new Label("Cache — Task 15"));         // replaced in Task 15
        for (Tab t : new Tab[]{mappingsTab, catalogsTab, cacheTab}) {
            t.setClosable(false);
            t.getStyleClass().add("utility-tab");
        }
        mappingsTab.setGraphic(new IconifyIcon("bi-diagram-3"));
        catalogsTab.setGraphic(new IconifyIcon("bi-journal-bookmark"));
        cacheTab.setGraphic(new IconifyIcon("bi-hdd"));
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
                cd.getValue().namespace().isEmpty() ? "<" + cd.getValue().rootElement() + "> (no namespace)" : cd.getValue().namespace()));
        TableColumn<SchemaLibraryEntry, String> locCol = new TableColumn<>("Location");
        locCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().location()));
        TableColumn<SchemaLibraryEntry, String> kindCol = new TableColumn<>("Kind");
        kindCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().kind().label()));
        TableColumn<SchemaLibraryEntry, String> srcCol = new TableColumn<>("Source");
        srcCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().source().name().toLowerCase(Locale.ROOT)));
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
        enabledCol.setPrefWidth(40);
        mappings.getColumns().setAll(java.util.List.of(statusCol, nsCol, locCol, kindCol, srcCol, enabledCol));
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
