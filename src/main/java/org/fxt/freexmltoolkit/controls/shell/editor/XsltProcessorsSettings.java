package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.AdminPolicy;
import org.fxt.freexmltoolkit.service.progress.Progress;
import org.fxt.freexmltoolkit.service.xsltproc.SaxonInstaller;
import org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorConfig;
import org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorPreset;
import org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorStore;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Settings → XSLT Processors: lists the bundled Saxon HE and the external processors, marks
 * the default and adds, edits and removes processors ({@link XsltProcessorDialog}). "Add Saxon HE 13"
 * downloads a newer Saxon HE ({@link SaxonInstaller}, after a confirmation) and adds it as an external
 * processor. Every change is saved to the settings at once ({@link XsltProcessorStore}).
 */
public class XsltProcessorsSettings extends VBox {

    /** One row: an external processor, or {@code null} for the bundled Saxon HE. */
    record Row(XsltProcessorConfig processor) {
        String id() {
            return processor == null ? XsltProcessorStore.BUILTIN_ID : processor.id();
        }
    }

    @FXML private ListView<Row> processorList;
    @FXML private Button addButton;
    @FXML private Button addSaxonButton;
    @FXML private Label saxonStatus;
    @FXML private Button editButton;
    @FXML private Button removeButton;
    @FXML private Button defaultButton;
    @FXML private Label policyNote;

    private final XsltProcessorStore store;
    private final SaxonInstaller saxonInstaller;
    /** Asks before the download (test hook). */
    private java.util.function.BooleanSupplier downloadConfirmer = this::confirmDownload;
    /** Shows a failed download (test hook). */
    private java.util.function.Consumer<Exception> downloadFailure = this::showDownloadFailure;
    /** Program shown per processor id, looked up once per refresh (PATH lookups touch the disk). */
    private final java.util.Map<String, String> programs = new java.util.HashMap<>();

    /** A view on the application settings. */
    public XsltProcessorsSettings() {
        this(XsltProcessorStore.settings(), SaxonInstaller.standard());
    }

    XsltProcessorsSettings(XsltProcessorStore store, SaxonInstaller saxonInstaller) {
        this.store = store;
        this.saxonInstaller = saxonInstaller;
        FxmlViews.load(this, "/pages/views/XsltProcessorsSettings.fxml");
        processorList.setCellFactory(list -> new ProcessorCell());
        processorList.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !editButton.isDisabled()) {
                onEdit();
            }
        });
        AdminPolicy policy = AdminPolicy.current();
        boolean allowed = store.externalAllowed();
        boolean listLocked = policy.isLocked(XsltProcessorStore.KEY_PROCESSORS);
        boolean defaultLocked = policy.isLocked(XsltProcessorStore.KEY_DEFAULT);
        policyNote.setVisible(!allowed);
        policyNote.setManaged(!allowed);
        addButton.setDisable(!allowed || listLocked);
        addSaxonButton.setDisable(!allowed || listLocked);
        processorList.getSelectionModel().selectedItemProperty().addListener((obs, o, row) -> {
            boolean external = row != null && row.processor() != null;
            editButton.setDisable(!external || listLocked);
            removeButton.setDisable(!external || listLocked);
            defaultButton.setDisable(row == null || defaultLocked || row.id().equals(store.defaultId()));
        });
        refresh(null);
    }

    /** Reloads the rows and selects the given id (or the first row). */
    void refresh(String selectId) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(null));
        programs.clear();
        store.list().forEach(p -> {
            rows.add(new Row(p));
            programs.put(p.id(), p.resolveExecutable().map(Object::toString)
                    .orElse(I18n.t("XsltProcessorsSettings.notFound")));
        });
        processorList.getSelectionModel().clearSelection(); // setAll with a selection can throw
        processorList.getItems().setAll(rows);
        String target = selectId != null ? selectId : XsltProcessorStore.BUILTIN_ID;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(target)) {
                processorList.getSelectionModel().select(i);
                return;
            }
        }
        processorList.getSelectionModel().select(0);
    }

    @FXML
    public void onAdd() {
        new XsltProcessorDialog(null, names(null)).showAndWait().ifPresent(processor -> {
            List<XsltProcessorConfig> list = new ArrayList<>(store.list());
            list.add(processor);
            store.save(list);
            if (list.size() == 1 && XsltProcessorStore.BUILTIN_ID.equals(store.defaultId())
                    && DialogHelper.showConfirmation(I18n.t("XsltProcessorsSettings.makeDefault.title"),
                    I18n.t("XsltProcessorsSettings.makeDefault.header", processor.name()),
                    I18n.t("XsltProcessorsSettings.makeDefault.content"))) {
                store.setDefaultId(processor.id());
            }
            refresh(processor.id());
        });
    }

    /**
     * Adds the newer Saxon HE as an external processor: downloads it first (after a confirmation) when it
     * is not there yet; selects the existing processor when one already runs these jars.
     */
    @FXML
    public void onAddSaxon() {
        if (saxonInstaller.isInstalled()) {
            addSaxonProcessor();
            return;
        }
        if (!downloadConfirmer.getAsBoolean()) {
            return;
        }
        addSaxonButton.setDisable(true);
        showSaxonStatus(I18n.t("XsltProcessorsSettings.saxon.downloading", SaxonInstaller.VERSION, 0));
        FxtGui.executorService.submit(() -> {
            try {
                saxonInstaller.install(new Progress() {
                    @Override
                    public void message(String text) {
                    }

                    @Override
                    public void progress(long done, long total) {
                        long percent = total <= 0 ? 0 : done * 100 / total;
                        Platform.runLater(() -> showSaxonStatus(
                                I18n.t("XsltProcessorsSettings.saxon.downloading", SaxonInstaller.VERSION, percent)));
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }
                });
                Platform.runLater(() -> {
                    addSaxonButton.setDisable(false);
                    addSaxonProcessor();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    addSaxonButton.setDisable(false);
                    showSaxonStatus(I18n.t("XsltProcessorsSettings.saxon.failed", SaxonInstaller.VERSION));
                    downloadFailure.accept(e);
                });
            }
        });
    }

    /** Adds (or selects) the processor that runs the installed jars. */
    private void addSaxonProcessor() {
        String classpath = saxonInstaller.classpath();
        var existing = store.list().stream()
                .filter(p -> p.preset() == XsltProcessorPreset.SAXON_HE && p.arguments().contains(classpath))
                .findFirst();
        if (existing.isPresent()) {
            showSaxonStatus(I18n.t("XsltProcessorsSettings.saxon.exists", existing.get().name()));
            refresh(existing.get().id());
            return;
        }
        XsltProcessorConfig processor = saxonInstaller.processorConfig();
        Set<String> taken = names(null).stream().map(n -> n.toLowerCase(java.util.Locale.ROOT)).collect(Collectors.toSet());
        String name = processor.name();
        for (int i = 2; taken.contains(name.toLowerCase(java.util.Locale.ROOT)); i++) {
            name = processor.name() + " (" + i + ")";
        }
        processor = new XsltProcessorConfig(processor.id(), name, processor.preset(), processor.executable(),
                processor.arguments(), processor.parameterTemplate(), processor.workingDir(), processor.timeoutSeconds());
        List<XsltProcessorConfig> list = new ArrayList<>(store.list());
        list.add(processor);
        store.save(list);
        showSaxonStatus(I18n.t("XsltProcessorsSettings.saxon.added", processor.name()));
        refresh(processor.id());
    }

    private boolean confirmDownload() {
        return DialogHelper.showConfirmation(I18n.t("XsltProcessorsSettings.saxon.confirm.title"),
                I18n.t("XsltProcessorsSettings.saxon.confirm.header", SaxonInstaller.VERSION),
                I18n.t("XsltProcessorsSettings.saxon.confirm.content",
                        Math.round(SaxonInstaller.downloadSize() / 1_000_000.0), saxonInstaller.directory()));
    }

    private void showDownloadFailure(Exception e) {
        DialogHelper.showError(I18n.t("XsltProcessorsSettings.saxon.failed.title"),
                I18n.t("XsltProcessorsSettings.saxon.failed", SaxonInstaller.VERSION), String.valueOf(e.getMessage()));
    }

    private void showSaxonStatus(String text) {
        saxonStatus.setText(text);
        saxonStatus.setVisible(true);
        saxonStatus.setManaged(true);
    }

    /** Test hooks: replaces the download confirmation and the failure dialog. */
    void setDownloadHooks(java.util.function.BooleanSupplier confirmer, java.util.function.Consumer<Exception> failure) {
        this.downloadConfirmer = confirmer;
        this.downloadFailure = failure;
    }

    @FXML
    public void onEdit() {
        Row row = processorList.getSelectionModel().getSelectedItem();
        if (row == null || row.processor() == null) {
            return;
        }
        new XsltProcessorDialog(row.processor(), names(row.processor().id())).showAndWait().ifPresent(edited -> {
            List<XsltProcessorConfig> list = new ArrayList<>(store.list());
            list.replaceAll(p -> p.id().equals(edited.id()) ? edited : p);
            store.save(list);
            refresh(edited.id());
        });
    }

    @FXML
    public void onRemove() {
        Row row = processorList.getSelectionModel().getSelectedItem();
        if (row == null || row.processor() == null) {
            return;
        }
        if (!DialogHelper.showConfirmation(I18n.t("XsltProcessorsSettings.remove.title"),
                I18n.t("XsltProcessorsSettings.remove.header", row.processor().name()), "")) {
            return;
        }
        List<XsltProcessorConfig> list = new ArrayList<>(store.list());
        list.removeIf(p -> p.id().equals(row.processor().id()));
        store.save(list);
        refresh(null);
    }

    @FXML
    public void onMakeDefault() {
        Row row = processorList.getSelectionModel().getSelectedItem();
        if (row != null) {
            store.setDefaultId(row.id());
            refresh(row.id());
        }
    }

    /** @return the names of the processors other than {@code exceptId} */
    private Set<String> names(String exceptId) {
        return store.list().stream().filter(p -> !p.id().equals(exceptId)).map(XsltProcessorConfig::name)
                .collect(Collectors.toSet());
    }

    /** Name (+ "default"), then the program or "not found". */
    private final class ProcessorCell extends ListCell<Row> {
        @Override
        protected void updateItem(Row row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            boolean isDefault = row.id().equals(store.defaultId());
            String name = row.processor() == null ? I18n.t("XsltProcessorsSettings.builtin") : row.processor().name();
            String detail;
            if (row.processor() == null) {
                detail = I18n.t("XsltProcessorsSettings.builtin.detail", net.sf.saxon.Version.getProductVersion());
            } else {
                detail = programs.getOrDefault(row.id(), "");
            }
            setText(name + (isDefault ? "  ·  " + I18n.t("XsltProcessorsSettings.default") : "") + "\n" + detail);
            IconifyIcon icon = new IconifyIcon(isDefault ? "bi-star-fill" : "bi-cpu");
            icon.setIconSize(16);
            setGraphic(icon);
        }
    }
}
