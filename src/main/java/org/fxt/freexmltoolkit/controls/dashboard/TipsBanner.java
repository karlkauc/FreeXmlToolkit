package org.fxt.freexmltoolkit.controls.dashboard;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.domain.statistics.FeatureTip;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A banner component displaying contextual tips for undiscovered features.
 * Shows one tip at a time with navigation buttons and a dismiss option.
 */
public class TipsBanner extends VBox {

    private final List<FeatureTip> tips = new ArrayList<>();
    private int currentTipIndex = 0;
    private final Label tipLabel;
    private final FontIcon tipIcon;
    private final Button actionButton;
    private final HBox navigationBox;
    private final Label tipCounter;
    private Consumer<String> onActionClick;

    /**
     * Creates a new TipsBanner component.
     * The banner is initially hidden and will become visible when tips are added.
     */
    public TipsBanner() {
        getStyleClass().add("tips-banner");
        setSpacing(8);
        setPadding(new Insets(12));
        setVisible(false); // Hidden until tips are set

        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        FontIcon lightbulbIcon = new FontIcon("bi-lightbulb");
        lightbulbIcon.setIconSize(16);
        lightbulbIcon.getStyleClass().add("tips-header-icon");

        Label headerLabel = new Label("Tip");
        headerLabel.getStyleClass().add("tips-header-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button dismissAllButton = new Button();
        dismissAllButton.setGraphic(new FontIcon("bi-x"));
        dismissAllButton.getStyleClass().add("tips-dismiss-button");
        dismissAllButton.setOnAction(e -> {
            tips.clear();
            setVisible(false);
        });

        header.getChildren().addAll(lightbulbIcon, headerLabel, spacer, dismissAllButton);

        // Tip content
        HBox tipContent = new HBox(10);
        tipContent.setAlignment(Pos.CENTER_LEFT);

        tipIcon = new FontIcon("bi-info-circle");
        tipIcon.setIconSize(20);
        tipIcon.getStyleClass().add("tip-icon");

        tipLabel = new Label();
        tipLabel.setWrapText(true);
        tipLabel.getStyleClass().add("tip-message");
        HBox.setHgrow(tipLabel, Priority.ALWAYS);

        actionButton = new Button("Try it");
        actionButton.getStyleClass().add("tip-action-button");
        actionButton.setOnAction(e -> {
            if (onActionClick != null && currentTipIndex < tips.size()) {
                FeatureTip tip = tips.get(currentTipIndex);
                if (tip.getActionLink() != null) {
                    onActionClick.accept(tip.getActionLink());
                }
            }
        });

        tipContent.getChildren().addAll(tipIcon, tipLabel, actionButton);

        // Navigation
        navigationBox = new HBox(8);
        navigationBox.setAlignment(Pos.CENTER);

        Button prevButton = new Button();
        prevButton.setGraphic(new FontIcon("bi-chevron-left"));
        prevButton.getStyleClass().add("tips-nav-button");
        prevButton.setOnAction(e -> showPreviousTip());

        tipCounter = new Label("1/1");
        tipCounter.getStyleClass().add("tips-counter");

        Button nextButton = new Button();
        nextButton.setGraphic(new FontIcon("bi-chevron-right"));
        nextButton.getStyleClass().add("tips-nav-button");
        nextButton.setOnAction(e -> showNextTip());

        navigationBox.getChildren().addAll(prevButton, tipCounter, nextButton);

        getChildren().addAll(header, tipContent, navigationBox);
    }

    /**
     * Sets the tips to display in the banner.
     * Replaces any existing tips and resets to the first tip.
     *
     * @param tips the list of feature tips to display, or null to clear all tips
     */
    public void setTips(List<FeatureTip> tips) {
        this.tips.clear();
        if (tips != null) {
            this.tips.addAll(tips);
        }

        currentTipIndex = 0;
        updateDisplay();
    }

    /**
     * Adds a single tip to the existing list of tips.
     * The display is updated to show the new tip if it is the first one added.
     *
     * @param tip the feature tip to add, ignored if null
     */
    public void addTip(FeatureTip tip) {
        if (tip != null) {
            tips.add(tip);
            updateDisplay();
        }
    }

    private void showNextTip() {
        if (!tips.isEmpty()) {
            currentTipIndex = (currentTipIndex + 1) % tips.size();
            updateDisplay();
        }
    }

    private void showPreviousTip() {
        if (!tips.isEmpty()) {
            currentTipIndex = (currentTipIndex - 1 + tips.size()) % tips.size();
            updateDisplay();
        }
    }

    private void updateDisplay() {
        if (tips.isEmpty()) {
            setVisible(false);
            return;
        }

        setVisible(true);
        FeatureTip currentTip = tips.get(currentTipIndex);

        tipLabel.setText(currentTip.getTipMessage());

        if (currentTip.getIconLiteral() != null) {
            tipIcon.setIconLiteral(currentTip.getIconLiteral());
        }

        tipCounter.setText((currentTipIndex + 1) + "/" + tips.size());
        navigationBox.setVisible(tips.size() > 1);

        actionButton.setVisible(currentTip.getActionLink() != null);
    }

    /**
     * Sets the callback handler for when the action button is clicked.
     * The callback receives the action link string from the current tip.
     *
     * @param callback the consumer to handle action button clicks, receives the action link
     */
    public void setOnActionClick(Consumer<String> callback) {
        this.onActionClick = callback;
    }
}
