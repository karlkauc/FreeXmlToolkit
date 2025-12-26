package org.fxt.freexmltoolkit.controls.dashboard;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A card component displaying a single statistic with icon, value, label, and trend indicator.
 * Used in the gamification dashboard to show metrics like validations, errors fixed, etc.
 */
public class StatisticsCard extends VBox {

    private final FontIcon icon;
    private final Label valueLabel;
    private final Label titleLabel;
    private final Label trendLabel;

    public StatisticsCard() {
        this("bi-question-circle", "0", "Metric", 0);
    }

    public StatisticsCard(String iconLiteral, String value, String title, int weeklyChange) {
        getStyleClass().add("statistics-card");
        setAlignment(Pos.CENTER);
        setSpacing(8);

        // Icon
        icon = new FontIcon(iconLiteral);
        icon.setIconSize(28);
        icon.getStyleClass().add("statistics-card-icon");

        // Value
        valueLabel = new Label(value);
        valueLabel.getStyleClass().add("statistics-card-value");

        // Title
        titleLabel = new Label(title);
        titleLabel.getStyleClass().add("statistics-card-title");

        // Trend indicator
        trendLabel = new Label();
        trendLabel.getStyleClass().add("statistics-card-trend");
        updateTrend(weeklyChange);

        getChildren().addAll(icon, valueLabel, titleLabel, trendLabel);
    }

    public void setValue(String value) {
        valueLabel.setText(value);
    }

    public void setValue(int value) {
        valueLabel.setText(String.valueOf(value));
    }

    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    public void setIcon(String iconLiteral) {
        icon.setIconLiteral(iconLiteral);
    }

    public void setIconColor(String color) {
        icon.setStyle("-fx-icon-color: " + color + ";");
    }

    public void updateTrend(int weeklyChange) {
        trendLabel.getStyleClass().removeAll("trend-up", "trend-down", "trend-neutral");

        if (weeklyChange > 0) {
            trendLabel.setText("+" + weeklyChange + " this week");
            trendLabel.getStyleClass().add("trend-up");
        } else if (weeklyChange < 0) {
            trendLabel.setText(weeklyChange + " this week");
            trendLabel.getStyleClass().add("trend-down");
        } else {
            trendLabel.setText("No change");
            trendLabel.getStyleClass().add("trend-neutral");
        }
    }

    /**
     * Create a card for validations metric
     */
    public static StatisticsCard createValidationsCard(int count, int weeklyChange) {
        StatisticsCard card = new StatisticsCard("bi-check-circle", String.valueOf(count), "Validations", weeklyChange);
        card.setIconColor("#28a745");
        return card;
    }

    /**
     * Create a card for errors fixed metric
     */
    public static StatisticsCard createErrorsFixedCard(int count, int weeklyChange) {
        StatisticsCard card = new StatisticsCard("bi-bug", String.valueOf(count), "Errors Fixed", weeklyChange);
        card.setIconColor("#dc3545");
        return card;
    }

    /**
     * Create a card for transformations metric
     */
    public static StatisticsCard createTransformationsCard(int count, int weeklyChange) {
        StatisticsCard card = new StatisticsCard("bi-arrow-repeat", String.valueOf(count), "Transformations", weeklyChange);
        card.setIconColor("#17a2b8");
        return card;
    }

    /**
     * Create a card for productivity score
     */
    public static StatisticsCard createProductivityCard(int score, String level) {
        StatisticsCard card = new StatisticsCard("bi-speedometer2", String.valueOf(score), level, 0);
        card.setIconColor("#ffc107");
        card.trendLabel.setText("Productivity Score");
        card.trendLabel.getStyleClass().add("trend-neutral");
        return card;
    }
}
