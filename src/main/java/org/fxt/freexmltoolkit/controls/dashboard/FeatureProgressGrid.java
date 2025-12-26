package org.fxt.freexmltoolkit.controls.dashboard;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import org.fxt.freexmltoolkit.domain.statistics.FeatureUsage;
import org.fxt.freexmltoolkit.service.SkillTracker;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.function.Consumer;

/**
 * A grid component showing feature discovery progress.
 * Displays which features have been discovered and allows navigation to features.
 */
public class FeatureProgressGrid extends VBox {

    private final FlowPane featurePane;
    private final Label progressLabel;
    private final ProgressBar progressBar;
    private Consumer<String> onFeatureClick;

    public FeatureProgressGrid() {
        getStyleClass().add("feature-progress-grid");
        setSpacing(12);
        setPadding(new Insets(12));

        // Header with progress
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("Feature Discovery");
        titleLabel.getStyleClass().add("feature-grid-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        progressLabel = new Label("0/15 features discovered");
        progressLabel.getStyleClass().add("feature-grid-progress-text");

        header.getChildren().addAll(titleLabel, spacer, progressLabel);

        // Progress bar
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.getStyleClass().add("feature-progress-bar");

        // Feature grid
        featurePane = new FlowPane();
        featurePane.setHgap(8);
        featurePane.setVgap(8);
        featurePane.getStyleClass().add("feature-pane");

        getChildren().addAll(header, progressBar, featurePane);
    }

    /**
     * Update the grid with current feature usage data
     */
    public void updateFeatures(List<FeatureUsage> features) {
        featurePane.getChildren().clear();

        if (features == null || features.isEmpty()) {
            // Show all features from SkillTracker
            for (SkillTracker.FeatureDefinition def : SkillTracker.getAllFeatureDefinitions()) {
                addFeatureChip(def.id(), def.name(), def.iconLiteral(), false, def.pageLink());
            }
            updateProgress(0, SkillTracker.getAllFeatureDefinitions().size());
            return;
        }

        int discovered = 0;
        int total = features.size();

        for (FeatureUsage feature : features) {
            SkillTracker.FeatureDefinition def = SkillTracker.getFeatureDefinition(feature.getFeatureId());
            String icon = def != null ? def.iconLiteral() : "bi-circle";
            String pageLink = def != null ? def.pageLink() : null;

            addFeatureChip(feature.getFeatureId(), feature.getFeatureName(), icon,
                feature.isDiscovered(), pageLink);

            if (feature.isDiscovered()) {
                discovered++;
            }
        }

        updateProgress(discovered, total);
    }

    private void addFeatureChip(String featureId, String featureName, String iconLiteral,
                                boolean discovered, String pageLink) {
        HBox chip = new HBox(6);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setPadding(new Insets(6, 10, 6, 10));
        chip.getStyleClass().add("feature-chip");

        if (discovered) {
            chip.getStyleClass().add("discovered");
        } else {
            chip.getStyleClass().add("undiscovered");
        }

        // Icon
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(14);

        // Check mark for discovered features
        FontIcon checkIcon = new FontIcon(discovered ? "bi-check-circle-fill" : "bi-circle");
        checkIcon.setIconSize(12);
        checkIcon.getStyleClass().add(discovered ? "check-icon-discovered" : "check-icon-undiscovered");

        // Feature name
        Label nameLabel = new Label(featureName);
        nameLabel.getStyleClass().add("feature-chip-name");

        chip.getChildren().addAll(checkIcon, icon, nameLabel);

        // Tooltip
        String tooltipText = discovered
            ? featureName + " - Discovered!"
            : featureName + " - Click to explore";
        Tooltip.install(chip, new Tooltip(tooltipText));

        // Click handler for navigation
        if (!discovered && pageLink != null && onFeatureClick != null) {
            chip.setOnMouseClicked(e -> onFeatureClick.accept(pageLink));
            chip.setStyle("-fx-cursor: hand;");
        }

        featurePane.getChildren().add(chip);
    }

    private void updateProgress(int discovered, int total) {
        progressLabel.setText(discovered + "/" + total + " features discovered");
        progressBar.setProgress(total > 0 ? (double) discovered / total : 0);
    }

    /**
     * Set the callback for when a feature chip is clicked
     */
    public void setOnFeatureClick(Consumer<String> callback) {
        this.onFeatureClick = callback;
    }
}
