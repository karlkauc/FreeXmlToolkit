package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.List;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

/**
 * The cards of one Settings group in a regular grid: two equal columns across the available width (one
 * when narrow), the cards of a row as tall as the tallest of them. Heights are measured at the real
 * column width, so wrapped help texts are accounted for; the grid never gets less than that height,
 * which is what let a card grow over the next group's heading in the {@code FlowPane} it replaces.
 *
 * <p>{@code color} gives every card of the group the same icon colour.
 *
 * <p>FXML: {@code <SettingsCardGrid color="#1373D9">} with {@link SettingsCard}s as children.
 */
public final class SettingsCardGrid extends Pane {

    /** Narrower than this, the cards stack in one column. */
    static final double TWO_COLUMN_MIN_WIDTH = 720;

    private final DoubleProperty gap = new SimpleDoubleProperty(this, "gap", 16);
    private final StringProperty color = new SimpleStringProperty(this, "color");

    public SettingsCardGrid() {
        getStyleClass().add("fxt-settings-card-grid");
        gap.addListener(obs -> requestLayout());
        color.addListener(obs -> applyColor());
        getChildren().addListener((ListChangeListener<Node>) change -> applyColor());
    }

    private void applyColor() {
        String hex = color.get();
        if (hex == null || hex.isBlank()) {
            return;
        }
        for (Node child : getChildren()) {
            if (child instanceof SettingsCard card) {
                card.setColor(hex);
            }
        }
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected double computePrefWidth(double height) {
        Insets in = getInsets();
        return in.getLeft() + in.getRight() + 2 * 400 + gap.get();
    }

    @Override
    protected double computeMinWidth(double height) {
        Insets in = getInsets();
        return in.getLeft() + in.getRight() + 280;
    }

    @Override
    protected double computePrefHeight(double width) {
        Insets in = getInsets();
        double inner = (width < 0 ? computePrefWidth(-1) : width) - in.getLeft() - in.getRight();
        List<Double> rows = rowHeights(inner);
        double height = 0;
        for (double row : rows) {
            height += row;
        }
        return in.getTop() + in.getBottom() + height + Math.max(0, rows.size() - 1) * gap.get();
    }

    /** Never squeezed below the content: a squeezed grid let its cards overlap the next heading. */
    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected void layoutChildren() {
        Insets in = getInsets();
        double inner = getWidth() - in.getLeft() - in.getRight();
        int columns = columns(inner);
        double columnWidth = columnWidth(inner, columns);
        List<Node> cards = managedCards();
        List<Double> rows = rowHeights(inner);
        double y = in.getTop();
        for (int row = 0; row < rows.size(); row++) {
            double rowHeight = rows.get(row);
            for (int col = 0; col < columns; col++) {
                int index = row * columns + col;
                if (index >= cards.size()) {
                    break;
                }
                double x = in.getLeft() + col * (columnWidth + gap.get());
                cards.get(index).resizeRelocate(snapPositionX(x), snapPositionY(y),
                        snapSizeX(columnWidth), snapSizeY(rowHeight));
            }
            y += rowHeight + gap.get();
        }
    }

    /** @return the height of every row at {@code innerWidth} */
    private List<Double> rowHeights(double innerWidth) {
        int columns = columns(innerWidth);
        double columnWidth = columnWidth(innerWidth, columns);
        List<Node> cards = managedCards();
        List<Double> rows = new ArrayList<>();
        for (int start = 0; start < cards.size(); start += columns) {
            double height = 0;
            for (int i = start; i < Math.min(start + columns, cards.size()); i++) {
                height = Math.max(height, cards.get(i).prefHeight(columnWidth));
            }
            rows.add(height);
        }
        return rows;
    }

    private int columns(double innerWidth) {
        return innerWidth >= TWO_COLUMN_MIN_WIDTH ? 2 : 1;
    }

    private double columnWidth(double innerWidth, int columns) {
        return Math.max(0, (innerWidth - (columns - 1) * gap.get()) / columns);
    }

    private List<Node> managedCards() {
        return getManagedChildren();
    }

    public DoubleProperty gapProperty() {
        return gap;
    }

    public double getGap() {
        return gap.get();
    }

    public void setGap(double value) {
        gap.set(value);
    }

    public StringProperty colorProperty() {
        return color;
    }

    public String getColor() {
        return color.get();
    }

    public void setColor(String hex) {
        color.set(hex);
    }
}
