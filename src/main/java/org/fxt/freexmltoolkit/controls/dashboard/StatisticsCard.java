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

    /**
     * Creates a default statistics card with placeholder values.
     * The card is initialized with a question mark icon, value "0", title "Metric", and no weekly change.
     */
    public StatisticsCard() {
        this("bi-question-circle", "0", "Metric", 0);
    }

    /**
     * Creates a statistics card with the specified icon, value, title, and weekly change indicator.
     *
     * @param iconLiteral the Ikonli Bootstrap icon literal (e.g., "bi-check-circle")
     * @param value the main value to display on the card
     * @param title the title or label describing the metric
     * @param weeklyChange the change in value compared to last week (positive, negative, or zero)
     */
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

    /**
     * Sets the displayed value on the card using a string.
     *
     * @param value the value to display
     */
    public void setValue(String value) {
        valueLabel.setText(value);
    }

    /**
     * Sets the displayed value on the card using an integer.
     * The integer is converted to its string representation.
     *
     * @param value the integer value to display
     */
    public void setValue(int value) {
        valueLabel.setText(String.valueOf(value));
    }

    /**
     * Sets the title label on the card.
     *
     * @param title the title text describing the metric
     */
    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    /**
     * Sets the icon displayed on the card.
     *
     * @param iconLiteral the Ikonli Bootstrap icon literal (e.g., "bi-check-circle")
     */
    public void setIcon(String iconLiteral) {
        icon.setIconLiteral(iconLiteral);
    }

    /**
     * Sets the color of the icon on the card.
     *
     * @param color the CSS color value (e.g., "#28a745" or "green")
     */
    public void setIconColor(String color) {
        icon.setStyle("-fx-icon-color: " + color + ";");
    }

    /**
     * Updates the trend indicator to show the weekly change.
     * Positive changes are shown with "trend-up" style, negative with "trend-down",
     * and zero with "trend-neutral".
     *
     * @param weeklyChange the change in value compared to last week
     */
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
     * Creates a card for displaying the validations metric.
     * The card uses a green check-circle icon and displays the validation count.
     *
     * @param count the total number of validations performed
     * @param weeklyChange the change in validations compared to last week
     * @return a configured StatisticsCard for validations
     */
    public static StatisticsCard createValidationsCard(int count, int weeklyChange) {
        StatisticsCard card = new StatisticsCard("bi-check-circle", String.valueOf(count), "Validations", weeklyChange);
        card.setIconColor("#28a745");
        return card;
    }

    /**
     * Creates a card for displaying the errors fixed metric.
     * The card uses a red bug icon and displays the count of errors that have been fixed.
     *
     * @param count the total number of errors fixed
     * @param weeklyChange the change in errors fixed compared to last week
     * @return a configured StatisticsCard for errors fixed
     */
    public static StatisticsCard createErrorsFixedCard(int count, int weeklyChange) {
        StatisticsCard card = new StatisticsCard("bi-bug", String.valueOf(count), "Errors Fixed", weeklyChange);
        card.setIconColor("#dc3545");
        return card;
    }

    /**
     * Creates a card for displaying the transformations metric.
     * The card uses a cyan arrow-repeat icon and displays the transformation count.
     *
     * @param count the total number of transformations performed
     * @param weeklyChange the change in transformations compared to last week
     * @return a configured StatisticsCard for transformations
     */
    public static StatisticsCard createTransformationsCard(int count, int weeklyChange) {
        StatisticsCard card = new StatisticsCard("bi-arrow-repeat", String.valueOf(count), "Transformations", weeklyChange);
        card.setIconColor("#17a2b8");
        return card;
    }

    /**
     * Creates a card for displaying the productivity score.
     * The card uses a yellow speedometer icon and shows the score with a level label.
     * The trend label displays "Productivity Score" instead of weekly change.
     *
     * @param score the productivity score value
     * @param level the productivity level label (e.g., "Beginner", "Expert")
     * @return a configured StatisticsCard for productivity score
     */
    public static StatisticsCard createProductivityCard(int score, String level) {
        StatisticsCard card = new StatisticsCard("bi-speedometer2", String.valueOf(score), level, 0);
        card.setIconColor("#ffc107");
        card.trendLabel.setText("Productivity Score");
        card.trendLabel.getStyleClass().add("trend-neutral");
        return card;
    }
}
