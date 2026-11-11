package org.fxt.freexmltoolkit.controls.shell.editor.ai;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.ai.AiPolicy;
import org.fxt.freexmltoolkit.service.ai.AiSettings;
import org.fxt.freexmltoolkit.service.ai.provider.AuthHeader;
import org.fxt.freexmltoolkit.service.ai.provider.CancelToken;
import org.fxt.freexmltoolkit.service.ai.provider.ChatProvider;
import org.fxt.freexmltoolkit.service.ai.provider.ChatRequest;
import org.fxt.freexmltoolkit.service.ai.provider.ChatResponse;
import org.fxt.freexmltoolkit.service.ai.provider.OpenAiCompatibleProvider;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderConfig;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderException;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderKind;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderPreset;
import org.fxt.freexmltoolkit.service.ai.provider.TokenLimitField;
import org.fxt.freexmltoolkit.service.ai.provider.anthropic.AnthropicProvider;
import org.fxt.freexmltoolkit.util.DialogHelper;

/**
 * Settings → AI → Add / Edit provider: preset, name, base URL, model (from the provider's model list or typed),
 * API key (stored encrypted) or the name of an environment variable holding it, and the protocol details. Test sends
 * a one-line request and shows the model, the time it took and whether the host counts as local.
 */
public class AiProviderDialog extends Dialog<AiProviderDialog.Result> {

    /**
     * @param apiKey the key typed in the dialog: {@code null} = unchanged, empty = remove the stored key
     */
    public record Result(ProviderConfig config, String apiKey) {
    }

    @FXML private ComboBox<ProviderPreset> presetChoice;
    @FXML private TextField nameField;
    @FXML private TextField baseUrlField;
    @FXML private ComboBox<String> modelChoice;
    @FXML private Button loadModelsButton;
    @FXML private PasswordField apiKeyField;
    @FXML private Label apiKeyNote;
    @FXML private TextField apiKeyEnvField;
    @FXML private ComboBox<AuthHeader> authHeaderChoice;
    @FXML private ComboBox<TokenLimitField> tokenFieldChoice;
    @FXML private Spinner<Integer> maxTokensSpinner;
    @FXML private Spinner<Integer> timeoutSpinner;
    @FXML private Label localBadge;
    @FXML private Button testButton;
    @FXML private Label testStatus;
    @FXML private Label error;

    private final String id;
    private final Set<String> otherNames;
    private final boolean hasStoredKey;
    private boolean nameFromPreset;

    public AiProviderDialog(ProviderConfig provider, Set<String> otherNames, boolean hasStoredKey) {
        DialogHelper.prepare(this);
        ProviderConfig start = provider != null ? provider : ProviderConfig.fromPreset(ProviderPreset.OLLAMA);
        this.id = start.id();
        this.hasStoredKey = hasStoredKey;
        this.otherNames = otherNames.stream().map(n -> n.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        setTitle(I18n.t(provider == null ? "AiProviderDialog.title.add" : "AiProviderDialog.title.edit"));
        setHeaderText(I18n.t("AiProviderDialog.header"));
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/AiProviderDialog.fxml"));

        presetChoice.getItems().setAll(ProviderPreset.values());
        presetChoice.setValue(start.preset());
        nameField.setText(start.name());
        nameFromPreset = start.name().equals(start.preset().displayName());
        baseUrlField.setText(start.baseUrl());
        modelChoice.setEditable(true);
        modelChoice.getEditor().setText(start.model());
        apiKeyEnvField.setText(start.apiKeyEnv() == null ? "" : start.apiKeyEnv());
        apiKeyNote.setText(I18n.t(hasStoredKey ? "AiProviderDialog.key.stored" : "AiProviderDialog.key.none"));
        authHeaderChoice.getItems().setAll(AuthHeader.values());
        authHeaderChoice.setValue(start.authHeader());
        tokenFieldChoice.getItems().setAll(TokenLimitField.values());
        tokenFieldChoice.setValue(start.tokenField());
        maxTokensSpinner.getValueFactory().setValue(start.maxOutputTokens());
        timeoutSpinner.getValueFactory().setValue(start.readTimeoutSeconds());

        presetChoice.valueProperty().addListener((obs, old, preset) -> applyPreset(old, preset));
        nameField.textProperty().addListener((obs, o, n) -> {
            if (presetChoice.getValue() == null || !n.equals(presetChoice.getValue().displayName())) {
                nameFromPreset = false;
            }
            validate();
        });
        baseUrlField.textProperty().addListener((obs, o, n) -> validate());
        modelChoice.getEditor().textProperty().addListener((obs, o, n) -> validate());
        validate();
        setResultConverter(button -> button == ButtonType.OK && validate() == null
                ? new Result(config(), apiKeyField.getText().isEmpty() ? null : apiKeyField.getText()) : null);
    }

    private void applyPreset(ProviderPreset old, ProviderPreset preset) {
        if (preset == null) {
            return;
        }
        String url = baseUrlField.getText() == null ? "" : baseUrlField.getText().strip();
        if (url.isEmpty() || (old != null && url.equals(old.baseUrl()))) {
            baseUrlField.setText(preset.baseUrl());
        }
        if (nameFromPreset || nameField.getText() == null || nameField.getText().isBlank()) {
            nameField.setText(preset.displayName());
            nameFromPreset = true;
        }
        tokenFieldChoice.setValue(preset.tokenField());
        validate();
    }

    /** The provider the fields describe. */
    ProviderConfig config() {
        ProviderPreset preset = presetChoice.getValue();
        return new ProviderConfig(id, nameField.getText(), preset.kind(), preset, baseUrlField.getText(),
                modelChoice.getEditor().getText(), apiKeyEnvField.getText(), authHeaderChoice.getValue(),
                tokenFieldChoice.getValue(), maxTokensSpinner.getValue(), timeoutSpinner.getValue());
    }

    /** @return the problem, or {@code null}; disables OK and Test */
    String validate() {
        String problem = null;
        String name = nameField.getText() == null ? "" : nameField.getText().strip();
        if (name.isEmpty()) {
            problem = I18n.t("AiProviderDialog.error.name");
        } else if (otherNames.contains(name.toLowerCase(Locale.ROOT))) {
            problem = I18n.t("AiProviderDialog.error.duplicate", name);
        }
        ProviderConfig config = presetChoice.getValue() == null ? null : config();
        if (problem == null && config != null) {
            problem = config.problem();
        }
        boolean local = config != null && config.isLocal(AiSettings.current().localHosts());
        localBadge.setText(I18n.t(local ? "Ai.badge.local" : "Ai.badge.remote"));
        localBadge.getStyleClass().removeAll("fxt-wf-validation", "fxt-wf-pdf");
        localBadge.getStyleClass().add(local ? "fxt-wf-validation" : "fxt-wf-pdf");
        if (problem == null && !local && !AiPolicy.remoteAllowed()) {
            problem = I18n.t("AiProviderDialog.error.remote");
        }
        boolean openAi = presetChoice.getValue() == null || presetChoice.getValue().kind() == ProviderKind.OPENAI_COMPATIBLE;
        authHeaderChoice.setDisable(!openAi);
        tokenFieldChoice.setDisable(!openAi);
        error.setText(problem == null ? "" : problem);
        error.setVisible(problem != null);
        error.setManaged(problem != null);
        var ok = getDialogPane().lookupButton(ButtonType.OK);
        if (ok != null) {
            ok.setDisable(problem != null);
        }
        testButton.setDisable(problem != null);
        loadModelsButton.setDisable(config == null || config.baseUrl().isEmpty());
        return problem;
    }

    private String keyForTest(ProviderConfig config) {
        String typed = apiKeyField.getText();
        if (typed != null && !typed.isEmpty()) {
            return typed;
        }
        return org.fxt.freexmltoolkit.service.ai.provider.ProviderSecrets.settings().apiKey(config).orElse(null);
    }

    private static ChatProvider providerFor(ProviderConfig config, String key) {
        return config.kind() == ProviderKind.ANTHROPIC ? new AnthropicProvider(config, key)
                : new OpenAiCompatibleProvider(config, key);
    }

    @FXML
    public void onLoadModels() {
        ProviderConfig config = config().withModel("x");
        String key = keyForTest(config);
        loadModelsButton.setDisable(true);
        testStatus.setText(I18n.t("AiProviderDialog.loadingModels"));
        FxtGui.executorService.submit(() -> {
            try {
                List<String> models = providerFor(config, key).listModels(CancelToken.none());
                Platform.runLater(() -> {
                    String current = modelChoice.getEditor().getText();
                    modelChoice.getItems().setAll(models);
                    modelChoice.getEditor().setText(current);
                    testStatus.setText(models.isEmpty() ? I18n.t("AiProviderDialog.noModels")
                            : I18n.t("AiProviderDialog.models", models.size()));
                    loadModelsButton.setDisable(false);
                    if (!models.isEmpty() && (current == null || current.isBlank())) {
                        modelChoice.show();
                    }
                });
            } catch (ProviderException | RuntimeException e) {
                Platform.runLater(() -> {
                    testStatus.setText(e.getMessage());
                    loadModelsButton.setDisable(false);
                });
            }
        });
    }

    @FXML
    public void onTest() {
        if (validate() != null) {
            return;
        }
        ProviderConfig config = config();
        String key = keyForTest(config);
        boolean local = config.isLocal(AiSettings.current().localHosts());
        testButton.setDisable(true);
        testStatus.setText(I18n.t("AiProviderDialog.testing", config.host()));
        FxtGui.executorService.submit(() -> {
            long start = System.nanoTime();
            String status;
            try {
                ChatResponse response = providerFor(config, key).chat(new ChatRequest("Answer with the single word OK.",
                        List.of(org.fxt.freexmltoolkit.service.ai.provider.ChatMessage.user("Say OK.")), List.of(),
                        32, null), null, CancelToken.none());
                long ms = (System.nanoTime() - start) / 1_000_000;
                status = I18n.t("AiProviderDialog.testOk", response.model() == null ? config.model() : response.model(),
                        ms, I18n.t(local ? "Ai.badge.local" : "Ai.badge.remote"));
            } catch (ProviderException e) {
                status = I18n.t("AiProviderDialog.testFailed", e.getMessage());
            } catch (RuntimeException e) {
                status = I18n.t("AiProviderDialog.testFailed", e.getClass().getSimpleName());
            }
            String text = status;
            Platform.runLater(() -> {
                testStatus.setText(text);
                testButton.setDisable(false);
                getDialogPane().getScene().getWindow().sizeToScene();
            });
        });
    }

    /** The stored key is kept unless a new one is typed; the button removes it. */
    @FXML
    public void onClearKey() {
        apiKeyField.setText("");
        apiKeyNote.setText(I18n.t(hasStoredKey ? "AiProviderDialog.key.willRemove" : "AiProviderDialog.key.none"));
        clearKey = true;
    }

    private boolean clearKey;

    /** Whether the user asked to remove the stored key. */
    public boolean clearKeyRequested() {
        return clearKey;
    }
}
