package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.AdminPolicy;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.finance.gleif.Gleif;

/**
 * Settings → GLEIF (LEI lookup): whether a validation and the editor hover send LEIs to GLEIF (both off by default;
 * an explicit lookup in an industry panel or {@code fxt-cli lei} always may), how long answers are kept, and a button
 * that forgets them. Changes are saved at once. With {@code gleif.allowed=false} everything is disabled.
 */
public class GleifSettings extends VBox {

    @FXML private Label policyNote;
    @FXML private CheckBox onValidation;
    @FXML private CheckBox hover;
    @FXML private Label hoursLabel;
    @FXML private Spinner<Integer> hours;
    @FXML private Button clearButton;

    private final PropertiesService properties;

    public GleifSettings() {
        this(PropertiesServiceImpl.getInstance());
    }

    GleifSettings(PropertiesService properties) {
        this.properties = properties;
        FxmlViews.load(this, "/pages/views/GleifSettings.fxml");
        AdminPolicy policy = AdminPolicy.current();
        boolean allowed = Gleif.allowed();
        policyNote.setVisible(!allowed);
        policyNote.setManaged(!allowed);
        onValidation.setSelected(allowed && Gleif.onValidation());
        hover.setSelected(allowed && Gleif.hoverLookups());
        hours.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 24 * 30, Gleif.cacheHours()));
        hours.valueProperty().addListener((obs, o, n) -> {
            if (n != null) {
                properties.set(Gleif.KEY_CACHE_HOURS, String.valueOf(n));
                Gleif.cacheHoursChanged();
            }
        });
        hoursLabel.setLabelFor(hours);
        onValidation.setDisable(!allowed || policy.isLocked(Gleif.KEY_ON_VALIDATION));
        hover.setDisable(!allowed || policy.isLocked(Gleif.KEY_HOVER));
        hours.setDisable(!allowed || policy.isLocked(Gleif.KEY_CACHE_HOURS));
        clearButton.setDisable(!allowed);
    }

    @FXML
    public void onValidationChanged() {
        properties.set(Gleif.KEY_ON_VALIDATION, String.valueOf(onValidation.isSelected()));
    }

    @FXML
    public void onHoverChanged() {
        properties.set(Gleif.KEY_HOVER, String.valueOf(hover.isSelected()));
    }

    @FXML
    public void onClear() {
        Gleif.cache().clear();
        clearButton.setText(I18n.t("GleifSettings.cleared"));
    }
}
