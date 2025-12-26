package org.fxt.freexmltoolkit.controls.dashboard;

import javafx.geometry.Insets;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.domain.statistics.DailyStatistics;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A compact sparkline chart showing activity trends over the past 7 days.
 * Uses JavaFX LineChart for visualization.
 */
public class TrendSparkline extends VBox {

    private final LineChart<String, Number> chart;
    private final XYChart.Series<String, Number> series;
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEE");

    public TrendSparkline() {
        getStyleClass().add("trend-sparkline");
        setPadding(new Insets(10));

        // Create axes
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("");
        xAxis.setTickLabelsVisible(true);
        xAxis.setTickMarkVisible(false);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("");
        yAxis.setTickLabelsVisible(false);
        yAxis.setTickMarkVisible(false);
        yAxis.setMinorTickVisible(false);
        yAxis.setAutoRanging(true);

        // Create chart
        chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Weekly Activity");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setPrefHeight(150);
        chart.getStyleClass().add("trend-chart");

        // Create series
        series = new XYChart.Series<>();
        series.setName("Activity");
        chart.getData().add(series);

        getChildren().add(chart);

        // Initialize with empty data
        updateWithEmptyData();
    }

    /**
     * Update the chart with daily statistics
     */
    public void updateData(List<DailyStatistics> dailyStats) {
        series.getData().clear();

        if (dailyStats == null || dailyStats.isEmpty()) {
            updateWithEmptyData();
            return;
        }

        // Reverse order so oldest is first
        for (int i = dailyStats.size() - 1; i >= 0; i--) {
            DailyStatistics day = dailyStats.get(i);
            String dayLabel = day.getDate().format(DAY_FORMAT);
            int activity = day.getTotalActivity();
            series.getData().add(new XYChart.Data<>(dayLabel, activity));
        }
    }

    /**
     * Initialize with empty placeholder data
     */
    private void updateWithEmptyData() {
        series.getData().clear();
        LocalDate today = LocalDate.now();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String dayLabel = date.format(DAY_FORMAT);
            series.getData().add(new XYChart.Data<>(dayLabel, 0));
        }
    }

    /**
     * Set the chart title
     */
    public void setChartTitle(String title) {
        chart.setTitle(title);
    }
}
