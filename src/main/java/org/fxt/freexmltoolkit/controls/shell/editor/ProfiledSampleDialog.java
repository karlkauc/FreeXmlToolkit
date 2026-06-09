package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;
import java.util.Map;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

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
    private final Spinner<Integer> batchCount = new Spinner<>(1, 1000, 1);
    private final TextField fileNamePattern = new TextField("sample_{seq:3}.xml");
    private final CheckBox mandatoryOnly = new CheckBox("Only mandatory elements");
    private final Spinner<Integer> maxOccurrences = new Spinner<>(1, 50, 2);

    public ProfiledSampleDialog(List<XPathInfo> xpaths) {
        setTitle("Generate Sample XML (Advanced)");
        setResizable(true);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        for (XPathInfo info : xpaths) {
            rows.add(new RuleRow(info));
        }

        getDialogPane().setContent(buildContent());
        setResultConverter(button -> button == ButtonType.OK ? currentProfile() : null);
    }

    private VBox buildContent() {
        TableView<RuleRow> table = new TableView<>(rows);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(320);
        table.setPrefWidth(640);

        TableColumn<RuleRow, String> xpathCol = new TableColumn<>("XPath");
        xpathCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getXpath()));
        xpathCol.setEditable(false);

        TableColumn<RuleRow, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTypeName()));
        typeCol.setEditable(false);
        typeCol.setMaxWidth(140);

        TableColumn<RuleRow, GenerationStrategy> stratCol = new TableColumn<>("Strategy");
        stratCol.setCellValueFactory(c -> c.getValue().strategyProperty());
        stratCol.setCellFactory(ComboBoxTableCell.forTableColumn(GenerationStrategy.values()));
        stratCol.setOnEditCommit(e -> e.getRowValue().strategyProperty().set(e.getNewValue()));
        stratCol.setMaxWidth(170);

        TableColumn<RuleRow, String> configCol = new TableColumn<>("Value / Pattern");
        configCol.setCellValueFactory(c -> c.getValue().configProperty());
        configCol.setCellFactory(TextFieldTableCell.forTableColumn());
        configCol.setOnEditCommit(e -> e.getRowValue().configProperty().set(e.getNewValue()));

        table.getColumns().add(xpathCol);
        table.getColumns().add(typeCol);
        table.getColumns().add(stratCol);
        table.getColumns().add(configCol);

        maxOccurrences.setEditable(true);
        batchCount.setEditable(true);

        GridPane opts = new GridPane();
        opts.setHgap(10);
        opts.setVgap(8);
        opts.add(mandatoryOnly, 0, 0, 2, 1);
        opts.add(new Label("Max. repetitions:"), 0, 1);
        opts.add(maxOccurrences, 1, 1);
        opts.add(new Label("Batch count:"), 0, 2);
        opts.add(batchCount, 1, 2);
        opts.add(new Label("File name pattern:"), 0, 3);
        opts.add(fileNamePattern, 1, 3);

        Label hint = new Label("Set a strategy per XPath. Config: FIXED=value · SEQUENCE/TEMPLATE=pattern"
                + " (e.g. ORD-{seq:4}) · RANDOM_FROM_LIST=comma list · XPATH_REF=source xpath.");
        hint.setWrapText(true);
        hint.getStyleClass().add("fxt-inspector-hint");

        VBox box = new VBox(10, buildProfilesBar(), new Label("Per-XPath generation rules:"),
                table, hint, opts);
        box.setPadding(new Insets(16));
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    /** A saved-profile selector with Load / Save As… backed by {@link GenerationProfileService}. */
    private HBox buildProfilesBar() {
        ComboBox<String> profileCombo = new ComboBox<>();
        profileCombo.setPromptText("(saved profiles)");
        refreshProfileNames(profileCombo);

        Button loadBtn = new Button("Load");
        loadBtn.setOnAction(e -> {
            String name = profileCombo.getValue();
            if (name != null && !name.isBlank()) {
                GenerationProfile loaded = GenerationProfileService.getInstance().load(name);
                applyProfile(loaded);
            }
        });

        Button saveBtn = new Button("Save As…");
        saveBtn.setOnAction(e -> {
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
        });

        HBox bar = new HBox(8, new Label("Profile:"), profileCombo, loadBtn, saveBtn);
        bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return bar;
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
