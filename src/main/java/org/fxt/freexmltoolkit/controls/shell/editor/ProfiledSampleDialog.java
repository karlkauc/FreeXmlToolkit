package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;
import java.util.Map;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.domain.GenerationProfile;
import org.fxt.freexmltoolkit.domain.GenerationStrategy;
import org.fxt.freexmltoolkit.domain.XPathInfo;
import org.fxt.freexmltoolkit.domain.XPathRule;
import org.fxt.freexmltoolkit.service.GenerationProfileService;

/**
 * Advanced sample-XML generation dialog: turns the schema's extracted XPaths into a table of editable
 * per-XPath generation rules (a {@link GenerationStrategy} + a single config value, interpreted per
 * strategy) plus batch options (count + file-name pattern) and the common mandatory-only /
 * max-repetition knobs. Yields a {@link GenerationProfile}. Generation itself stays in
 * {@link ProfiledSampleRunner}.
 */
public class ProfiledSampleDialog extends Dialog<GenerationProfile> {

    /** One editable rule row: an XPath + the chosen strategy and its single config value. */
    public static final class RuleRow {
        private final XPathInfo info;
        private final ObjectProperty<GenerationStrategy> strategy =
                new SimpleObjectProperty<>(GenerationStrategy.AUTO);
        private final StringProperty config = new SimpleStringProperty("");

        RuleRow(XPathInfo info) {
            this.info = info;
        }

        public String getXpath() {
            return info.xpath();
        }

        public String getTypeName() {
            return info.typeName();
        }

        public boolean isMandatory() {
            return info.mandatory();
        }

        public ObjectProperty<GenerationStrategy> strategyProperty() {
            return strategy;
        }

        public StringProperty configProperty() {
            return config;
        }
    }

    private final ObservableList<RuleRow> rows = FXCollections.observableArrayList();
    @FXML private ComboBox<String> profileCombo;
    @FXML private TableView<RuleRow> table;
    @FXML private TableColumn<RuleRow, String> xpathCol;
    @FXML private TableColumn<RuleRow, String> typeCol;
    @FXML private TableColumn<RuleRow, GenerationStrategy> stratCol;
    @FXML private TableColumn<RuleRow, String> configCol;
    @FXML private Spinner<Integer> batchCount;
    @FXML private TextField fileNamePattern;
    @FXML private CheckBox mandatoryOnly;
    @FXML private Spinner<Integer> maxOccurrences;

    public ProfiledSampleDialog(List<XPathInfo> xpaths) {
        setTitle("Generate Sample XML (Advanced)");
        setResizable(true);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        for (XPathInfo info : xpaths) {
            rows.add(new RuleRow(info));
        }

        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/ProfiledSampleDialog.fxml"));
        refreshProfileNames(profileCombo);

        table.setItems(rows);
        xpathCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getXpath()));
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTypeName()));
        stratCol.setCellValueFactory(c -> c.getValue().strategyProperty());
        stratCol.setCellFactory(ComboBoxTableCell.forTableColumn(GenerationStrategy.values()));
        stratCol.setOnEditCommit(e -> e.getRowValue().strategyProperty().set(e.getNewValue()));
        configCol.setCellValueFactory(c -> c.getValue().configProperty());
        configCol.setCellFactory(TextFieldTableCell.forTableColumn());
        configCol.setOnEditCommit(e -> e.getRowValue().configProperty().set(e.getNewValue()));

        setResultConverter(button -> button == ButtonType.OK ? currentProfile() : null);
    }

    /** Loads the saved profile selected in the profile bar (backed by {@link GenerationProfileService}). */
    @FXML
    public void onLoadProfile() {
        String name = profileCombo.getValue();
        if (name != null && !name.isBlank()) {
            GenerationProfile loaded = GenerationProfileService.getInstance().load(name);
            applyProfile(loaded);
        }
    }

    /** Saves the current control state as a named profile. */
    @FXML
    public void onSaveProfile() {
        TextInputDialog prompt = new TextInputDialog(
                profileCombo.getValue() != null ? profileCombo.getValue() : "Profile");
        prompt.setTitle("Save Profile");
        prompt.setHeaderText(null);
        prompt.setContentText("Profile name:");
        prompt.showAndWait().ifPresent(name -> {
            if (!name.isBlank()) {
                GenerationProfile profile = currentProfile();
                profile.setName(name.trim());
                GenerationProfileService.getInstance().save(profile);
                refreshProfileNames(profileCombo);
                profileCombo.setValue(name.trim());
            }
        });
    }

    private static void refreshProfileNames(ComboBox<String> combo) {
        try {
            combo.getItems().setAll(GenerationProfileService.getInstance().loadAll().stream()
                    .map(GenerationProfile::getName).sorted().toList());
        } catch (Exception e) {
            // best-effort: no saved profiles available
        }
    }

    /** Sets the batch options (for tests/observers). */
    public void setBatch(int count, String pattern) {
        batchCount.getValueFactory().setValue(count);
        fileNamePattern.setText(pattern);
    }

    /**
     * Loads a saved {@link GenerationProfile} into the controls: applies its batch/mandatory options
     * and, for each rule whose XPath matches a row, sets that row's strategy + config. Rows without a
     * matching rule are reset to {@code AUTO}. Inverse of {@link #currentProfile()}.
     */
    public void applyProfile(GenerationProfile profile) {
        if (profile == null) {
            return;
        }
        mandatoryOnly.setSelected(profile.isMandatoryOnly());
        maxOccurrences.getValueFactory().setValue(profile.getMaxOccurrences() <= 0 ? 2 : profile.getMaxOccurrences());
        batchCount.getValueFactory().setValue(Math.max(1, profile.getBatchCount()));
        if (profile.getFileNamePattern() != null && !profile.getFileNamePattern().isBlank()) {
            fileNamePattern.setText(profile.getFileNamePattern());
        }
        for (RuleRow row : rows) {
            row.strategyProperty().set(GenerationStrategy.AUTO);
            row.configProperty().set("");
        }
        for (XPathRule rule : profile.getRules()) {
            for (RuleRow row : rows) {
                if (row.getXpath().equals(rule.getXpath())) {
                    row.strategyProperty().set(rule.getStrategy());
                    row.configProperty().set(singleConfigValue(rule));
                }
            }
        }
    }

    /** Extracts the single config value for a rule (inverse of {@link #configFor}). */
    private static String singleConfigValue(XPathRule rule) {
        String value = switch (rule.getStrategy()) {
            case FIXED -> rule.getConfigValue("value");
            case SEQUENCE, TEMPLATE -> rule.getConfigValue("pattern");
            case RANDOM_FROM_LIST -> rule.getConfigValue("values");
            case XPATH_REF -> rule.getConfigValue("ref");
            default -> "";
        };
        return value == null ? "" : value;
    }

    /** @return the editable rule rows (for tests/observers). */
    public ObservableList<RuleRow> getRows() {
        return rows;
    }

    /** @return a {@link GenerationProfile} reflecting the current control state. */
    public GenerationProfile currentProfile() {
        GenerationProfile profile = new GenerationProfile("Custom");
        profile.setMandatoryOnly(mandatoryOnly.isSelected());
        profile.setMaxOccurrences(maxOccurrences.getValue());
        profile.setBatchCount(batchCount.getValue());
        profile.setFileNamePattern(fileNamePattern.getText());
        for (RuleRow row : rows) {
            GenerationStrategy strategy = row.strategyProperty().get();
            if (strategy == null || strategy == GenerationStrategy.AUTO) {
                continue; // AUTO = default type-based generation; no rule needed
            }
            profile.addRule(new XPathRule(row.getXpath(), strategy, configFor(strategy, row.configProperty().get())));
        }
        return profile;
    }

    /** Maps the single config value to the strategy's config key(s). */
    private static Map<String, String> configFor(GenerationStrategy strategy, String text) {
        String value = text == null ? "" : text.trim();
        return switch (strategy) {
            case FIXED -> Map.of("value", value);
            case SEQUENCE, TEMPLATE -> Map.of("pattern", value);
            case RANDOM_FROM_LIST -> Map.of("values", value);
            case XPATH_REF -> Map.of("ref", value);
            default -> Map.of();
        };
    }
}
