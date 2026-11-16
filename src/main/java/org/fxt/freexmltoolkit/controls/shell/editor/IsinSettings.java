package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.AdminPolicy;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.finance.isin.Isin;

/**
 * Settings → Instrument lookup (ESMA FIRDS, OpenFIGI): whether a validation and the editor hover send ISINs (both off
 * by default; an explicit lookup always may), the OpenFIGI fallback (on by default) and its API key (stored
 * encrypted), how long answers are kept, and a button that forgets them. Changes are saved at once. With
 * {@code isin.allowed=false} everything is disabled; {@code isin.openfigi.allowed=false} disables the fallback.
 */
public class IsinSettings extends VBox {

    @FXML private Label policyNote;
    @FXML private CheckBox onValidation;
    @FXML private CheckBox hover;
    @FXML private CheckBox openFigi;
    @FXML private Label apiKeyLabel;
    @FXML private PasswordField apiKey;
    @FXML private Label hoursLabel;
    @FXML private Spinner<Integer> hours;
    @FXML private Button clearButton;

    private final PropertiesService properties;

    public IsinSettings() {
        this(PropertiesServiceImpl.getInstance());
    }

    IsinSettings(PropertiesService properties) {
        this.properties = properties;
        FxmlViews.load(this, "/pages/views/IsinSettings.fxml");
        AdminPolicy policy = AdminPolicy.current();
        boolean allowed = Isin.allowed();
        boolean figiAllowed = allowed && policy.openFigiAllowed();
        policyNote.setVisible(!allowed);
        policyNote.setManaged(!allowed);
        onValidation.setSelected(allowed && Isin.onValidation());
        hover.setSelected(allowed && Isin.hoverLookups());
        openFigi.setSelected(figiAllowed && Isin.openFigi());
        String key = properties.get(Isin.KEY_OPENFIGI_API_KEY);
        apiKey.setText(key == null ? "" : key);
        apiKey.focusedProperty().addListener((obs, was, now) -> {
            if (!now) {
                saveApiKey();
            }
        });
        apiKey.setOnAction(e -> saveApiKey());
        apiKeyLabel.setLabelFor(apiKey);
        hours.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 24 * 30, Isin.cacheHours()));
        hours.valueProperty().addListener((obs, o, n) -> {
            if (n != null) {
                properties.set(Isin.KEY_CACHE_HOURS, String.valueOf(n));
                Isin.cacheHoursChanged();
            }
        });
        hoursLabel.setLabelFor(hours);
        onValidation.setDisable(!allowed || policy.isLocked(Isin.KEY_ON_VALIDATION));
        hover.setDisable(!allowed || policy.isLocked(Isin.KEY_HOVER));
        openFigi.setDisable(!figiAllowed || policy.isLocked(Isin.KEY_OPENFIGI));
        apiKey.setDisable(!figiAllowed);
        hours.setDisable(!allowed || policy.isLocked(Isin.KEY_CACHE_HOURS));
        clearButton.setDisable(!allowed);
    }

    private void saveApiKey() {
        String old = properties.get(Isin.KEY_OPENFIGI_API_KEY);
        String now = apiKey.getText() == null ? "" : apiKey.getText().strip();
        if (!now.equals(old == null ? "" : old)) {
            properties.set(Isin.KEY_OPENFIGI_API_KEY, now);
        }
    }

    @FXML
    public void onValidationChanged() {
        properties.set(Isin.KEY_ON_VALIDATION, String.valueOf(onValidation.isSelected()));
    }

    @FXML
    public void onHoverChanged() {
        properties.set(Isin.KEY_HOVER, String.valueOf(hover.isSelected()));
    }

    @FXML
    public void onOpenFigiChanged() {
        properties.set(Isin.KEY_OPENFIGI, String.valueOf(openFigi.isSelected()));
    }

    @FXML
    public void onClear() {
        Isin.cache().clear();
        clearButton.setText(I18n.t("GleifSettings.cleared"));
    }
}
