package org.fxt.freexmltoolkit.controls.shell.editor.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.AdminPolicy;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.ai.AiSettings;
import org.fxt.freexmltoolkit.service.ai.context.DataLevel;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderConfig;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderSecrets;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderStore;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Settings → AI: switch the assistant on, choose how much of a document may be sent (locally and to remote
 * providers), preview requests, and manage the providers. Everything is saved at once (like the XSL-FO processors);
 * policy-locked settings are disabled. The whole group is removed by {@code SettingsPanel} under
 * {@code ai.allowed=false}.
 */
public class AiSettingsPane extends VBox {

    @FXML private CheckBox enabledBox;
    @FXML private ComboBox<DataLevel> dataLevelChoice;
    @FXML private ComboBox<DataLevel> dataLevelRemoteChoice;
    @FXML private CheckBox previewBox;
    @FXML private ListView<ProviderConfig> providerList;
    @FXML private Button addButton;
    @FXML private Button editButton;
    @FXML private Button removeButton;
    @FXML private Button defaultButton;
    @FXML private Label policyNote;
    @FXML private VBox details;

    private final PropertiesService properties;
    private final ProviderStore store;
    private final ProviderSecrets secrets;
    private boolean loading;

    public AiSettingsPane() {
        this(PropertiesServiceImpl.getInstance(), ProviderStore.settings(), ProviderSecrets.settings());
    }

    AiSettingsPane(PropertiesService properties, ProviderStore store, ProviderSecrets secrets) {
        this.properties = properties;
        this.store = store;
        this.secrets = secrets;
        FxmlViews.load(this, "/pages/views/AiSettingsPane.fxml");
        StringConverter<DataLevel> levelNames = new StringConverter<>() {
            @Override
            public String toString(DataLevel level) {
                return level == null ? "" : I18n.t("Ai.level." + level.name());
            }

            @Override
            public DataLevel fromString(String text) {
                return null;
            }
        };
        dataLevelChoice.getItems().setAll(DataLevel.values());
        dataLevelRemoteChoice.getItems().setAll(DataLevel.values());
        dataLevelChoice.setConverter(levelNames);
        dataLevelRemoteChoice.setConverter(levelNames);
        providerList.setCellFactory(list -> new ProviderCell());
        providerList.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !editButton.isDisabled()) {
                onEdit();
            }
        });

        load();
        enabledBox.selectedProperty().addListener((obs, o, on) -> save(AiSettings.KEY_ENABLED, String.valueOf(on)));
        dataLevelChoice.valueProperty().addListener((obs, o, v) -> save(AiSettings.KEY_DATA_LEVEL, v.name()));
        dataLevelRemoteChoice.valueProperty().addListener((obs, o, v) -> save(AiSettings.KEY_DATA_LEVEL_REMOTE, v.name()));
        previewBox.selectedProperty().addListener((obs, o, on) -> save(AiSettings.KEY_PREVIEW_ALWAYS, String.valueOf(on)));
        details.disableProperty().bind(enabledBox.selectedProperty().not());

        AdminPolicy policy = AdminPolicy.current();
        lock(enabledBox, policy, AiSettings.KEY_ENABLED);
        lock(dataLevelChoice, policy, AiSettings.KEY_DATA_LEVEL);
        lock(dataLevelRemoteChoice, policy, AiSettings.KEY_DATA_LEVEL_REMOTE);
        lock(previewBox, policy, AiSettings.KEY_PREVIEW_ALWAYS);
        boolean fixed = store.isPolicyFixed();
        policyNote.setVisible(fixed);
        policyNote.setManaged(fixed);
        addButton.setDisable(fixed);
        providerList.getSelectionModel().selectedItemProperty().addListener((obs, o, p) -> updateButtons(p));
        refresh(null);
    }

    /** The controls a policy key can lock, for {@code SettingsPanel.policyKeys()}. */
    public java.util.Map<Node, String[]> policyKeys() {
        return java.util.Map.of(enabledBox, new String[] {AiSettings.KEY_ENABLED},
                dataLevelChoice, new String[] {AiSettings.KEY_DATA_LEVEL},
                dataLevelRemoteChoice, new String[] {AiSettings.KEY_DATA_LEVEL_REMOTE},
                previewBox, new String[] {AiSettings.KEY_PREVIEW_ALWAYS},
                providerList, new String[] {ProviderStore.KEY_PROVIDERS, ProviderStore.KEY_DEFAULT});
    }

    private static void lock(Node node, AdminPolicy policy, String key) {
        if (policy.isLocked(key)) {
            node.setDisable(true);
        }
    }

    private void load() {
        loading = true;
        AiSettings settings = new AiSettings(properties::get);
        enabledBox.setSelected(Boolean.parseBoolean(properties.get(AiSettings.KEY_ENABLED)));
        dataLevelChoice.setValue(settings.dataLevel());
        dataLevelRemoteChoice.setValue(settings.dataLevelRemote());
        previewBox.setSelected(settings.previewAlways());
        loading = false;
    }

    private void save(String key, String value) {
        if (!loading) {
            properties.set(key, value);
            AiState.refresh();
        }
    }

    private void updateButtons(ProviderConfig selected) {
        boolean fixed = store.isPolicyFixed();
        editButton.setDisable(selected == null || fixed);
        removeButton.setDisable(selected == null || fixed);
        defaultButton.setDisable(selected == null || store.isDefaultFixed() || selected.id().equals(store.defaultId())
                || store.refusal(selected) != null);
    }

    void refresh(String selectId) {
        List<ProviderConfig> providers = store.list();
        providerList.getSelectionModel().clearSelection();
        providerList.getItems().setAll(providers);
        for (int i = 0; i < providers.size(); i++) {
            if (providers.get(i).id().equals(selectId)) {
                providerList.getSelectionModel().select(i);
            }
        }
        if (providerList.getSelectionModel().isEmpty() && !providers.isEmpty()) {
            providerList.getSelectionModel().select(0);
        }
        updateButtons(providerList.getSelectionModel().getSelectedItem());
        AiState.refresh();
    }

    @FXML
    public void onAdd() {
        new AiProviderDialog(null, names(null), false).showAndWait().ifPresent(result -> {
            List<ProviderConfig> list = new ArrayList<>(store.list());
            list.add(result.config());
            store.save(list);
            if (result.apiKey() != null) {
                secrets.store(result.config().id(), result.apiKey());
            }
            if (list.size() > 1 && store.refusal(result.config()) == null
                    && DialogHelper.showConfirmation(I18n.t("AiSettingsPane.makeDefault.title"),
                    I18n.t("AiSettingsPane.makeDefault.header", result.config().name()), "")) {
                store.setDefaultId(result.config().id());
            }
            refresh(result.config().id());
        });
    }

    @FXML
    public void onEdit() {
        ProviderConfig selected = providerList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        AiProviderDialog dialog = new AiProviderDialog(selected, names(selected.id()), secrets.hasStoredKey(selected.id()));
        dialog.showAndWait().ifPresent(result -> {
            List<ProviderConfig> list = new ArrayList<>(store.list());
            list.replaceAll(p -> p.id().equals(result.config().id()) ? result.config() : p);
            store.save(list);
            if (result.apiKey() != null) {
                secrets.store(result.config().id(), result.apiKey());
            } else if (dialog.clearKeyRequested()) {
                secrets.remove(result.config().id());
            }
            refresh(result.config().id());
        });
    }

    @FXML
    public void onRemove() {
        ProviderConfig selected = providerList.getSelectionModel().getSelectedItem();
        if (selected == null || !DialogHelper.showConfirmation(I18n.t("AiSettingsPane.remove.title"),
                I18n.t("AiSettingsPane.remove.header", selected.name()), "")) {
            return;
        }
        List<ProviderConfig> list = new ArrayList<>(store.list());
        list.removeIf(p -> p.id().equals(selected.id()));
        store.save(list);
        secrets.remove(selected.id());
        refresh(null);
    }

    @FXML
    public void onMakeDefault() {
        ProviderConfig selected = providerList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                store.setDefaultId(selected.id());
            } catch (IllegalArgumentException e) {
                DialogHelper.showWarning(I18n.t("AiSettingsPane.makeDefault.title"), e.getMessage(), "");
            }
            refresh(selected.id());
        }
    }

    private Set<String> names(String exceptId) {
        return store.list().stream().filter(p -> !p.id().equals(exceptId)).map(ProviderConfig::name)
                .collect(Collectors.toSet());
    }

    /** Name · model (· default), then the host and local/remote. */
    private final class ProviderCell extends ListCell<ProviderConfig> {
        @Override
        protected void updateItem(ProviderConfig p, boolean empty) {
            super.updateItem(p, empty);
            if (empty || p == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            boolean isDefault = p.id().equals(store.defaultId());
            boolean local = p.isLocal(store.localHosts());
            String refused = store.refusal(p) != null ? "  ·  " + I18n.t("AiSettingsPane.refused") : "";
            setText(p.name() + "  ·  " + (p.model().isEmpty() ? "—" : p.model())
                    + (isDefault ? "  ·  " + I18n.t("AiSettingsPane.default") : "") + "\n" + p.host() + "  ·  "
                    + I18n.t(local ? "Ai.badge.local" : "Ai.badge.remote") + refused);
            IconifyIcon icon = new IconifyIcon(isDefault ? "bi-star-fill" : "bi-stars");
            icon.setIconSize(16);
            setGraphic(icon);
        }
    }
}
