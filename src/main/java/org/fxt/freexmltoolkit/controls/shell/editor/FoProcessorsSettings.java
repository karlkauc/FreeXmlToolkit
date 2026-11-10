package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.AdminPolicy;
import org.fxt.freexmltoolkit.service.fo.FoProcessorConfig;
import org.fxt.freexmltoolkit.service.fo.FoProcessorStore;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Settings → XSL-FO Processors: lists the bundled Apache FOP and the external processors, marks
 * the default and adds, edits and removes processors ({@link FoProcessorDialog}). Every change is
 * saved to the settings at once ({@link FoProcessorStore}).
 */
public class FoProcessorsSettings extends VBox {

    /** One row: an external processor, or {@code null} for the bundled Apache FOP. */
    record Row(FoProcessorConfig processor) {
        String id() {
            return processor == null ? FoProcessorStore.BUILTIN_ID : processor.id();
        }
    }

    @FXML private ListView<Row> processorList;
    @FXML private Button addButton;
    @FXML private Button editButton;
    @FXML private Button removeButton;
    @FXML private Button defaultButton;
    @FXML private Label policyNote;

    private final FoProcessorStore store;
    /** Program shown per processor id, looked up once per refresh (PATH lookups touch the disk). */
    private final java.util.Map<String, String> programs = new java.util.HashMap<>();

    /** A view on the application settings. */
    public FoProcessorsSettings() {
        this(FoProcessorStore.settings());
    }

    FoProcessorsSettings(FoProcessorStore store) {
        this.store = store;
        FxmlViews.load(this, "/pages/views/FoProcessorsSettings.fxml");
        processorList.setCellFactory(list -> new ProcessorCell());
        processorList.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !editButton.isDisabled()) {
                onEdit();
            }
        });
        AdminPolicy policy = AdminPolicy.current();
        boolean allowed = store.externalAllowed();
        boolean listLocked = policy.isLocked(FoProcessorStore.KEY_PROCESSORS);
        boolean defaultLocked = policy.isLocked(FoProcessorStore.KEY_DEFAULT);
        policyNote.setVisible(!allowed);
        policyNote.setManaged(!allowed);
        addButton.setDisable(!allowed || listLocked);
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
                    .orElse(I18n.t("FoProcessorsSettings.notFound")));
        });
        processorList.getSelectionModel().clearSelection(); // setAll with a selection can throw
        processorList.getItems().setAll(rows);
        String target = selectId != null ? selectId : FoProcessorStore.BUILTIN_ID;
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
        new FoProcessorDialog(null, names(null)).showAndWait().ifPresent(processor -> {
            List<FoProcessorConfig> list = new ArrayList<>(store.list());
            list.add(processor);
            store.save(list);
            if (list.size() == 1 && FoProcessorStore.BUILTIN_ID.equals(store.defaultId())
                    && DialogHelper.showConfirmation(I18n.t("FoProcessorsSettings.makeDefault.title"),
                    I18n.t("FoProcessorsSettings.makeDefault.header", processor.name()),
                    I18n.t("FoProcessorsSettings.makeDefault.content"))) {
                store.setDefaultId(processor.id());
            }
            refresh(processor.id());
        });
    }

    @FXML
    public void onEdit() {
        Row row = processorList.getSelectionModel().getSelectedItem();
        if (row == null || row.processor() == null) {
            return;
        }
        new FoProcessorDialog(row.processor(), names(row.processor().id())).showAndWait().ifPresent(edited -> {
            List<FoProcessorConfig> list = new ArrayList<>(store.list());
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
        if (!DialogHelper.showConfirmation(I18n.t("FoProcessorsSettings.remove.title"),
                I18n.t("FoProcessorsSettings.remove.header", row.processor().name()), "")) {
            return;
        }
        List<FoProcessorConfig> list = new ArrayList<>(store.list());
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
        return store.list().stream().filter(p -> !p.id().equals(exceptId)).map(FoProcessorConfig::name)
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
            String name = row.processor() == null ? I18n.t("FoProcessorsSettings.builtin") : row.processor().name();
            String detail;
            if (row.processor() == null) {
                detail = I18n.t("FoProcessorsSettings.builtin.detail");
            } else {
                detail = programs.getOrDefault(row.id(), "");
            }
            setText(name + (isDefault ? "  ·  " + I18n.t("FoProcessorsSettings.default") : "") + "\n" + detail);
            IconifyIcon icon = new IconifyIcon(isDefault ? "bi-star-fill" : "bi-printer");
            icon.setIconSize(16);
            setGraphic(icon);
        }
    }
}
