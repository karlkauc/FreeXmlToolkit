package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;

/**
 * The dock at the bottom of the {@link EditorHost} (editor column only — the Inspector stays
 * full height). It hosts the transform OUTPUT panel and, while an XSLT debug session is
 * alive, the DEBUG pane; a strip of segmented toggles switches between them and only appears
 * once more than one panel is registered, so with OUTPUT alone the dock looks like the plain
 * panel it replaces. The height is user-draggable at the top edge and persisted.
 */
public final class EditorBottomDock extends VBox {

    /** Properties key of the persisted dock height. */
    static final String HEIGHT_KEY = "editor.bottomDock.height";
    static final double DEFAULT_HEIGHT = 260;
    static final double MIN_HEIGHT = 120;
    /** Style class on a toggle whose panel received an update while another one was selected. */
    static final String UPDATED_CLASS = "fxt-dock-updated";

    private static final class Entry {
        final ToggleButton toggle;
        final Region panel;

        Entry(ToggleButton toggle, Region panel) {
            this.toggle = toggle;
            this.panel = panel;
        }
    }

    @FXML private Region resizer;
    @FXML private HBox strip;
    @FXML private HBox tabsBox;
    @FXML private StackPane content;
    private final ToggleGroup group = new ToggleGroup();
    private final Map<String, Entry> panels = new LinkedHashMap<>();
    private final PauseTransition heightSaveDebounce = new PauseTransition(Duration.millis(400));
    private double dragStartY;
    private double dragStartHeight;

    public EditorBottomDock() {
        FxmlViews.load(this, "/pages/panels/EditorBottomDock.fxml");
        setMinHeight(MIN_HEIGHT);
        setPrefHeight(loadHeight());
        heightSaveDebounce.setOnFinished(e -> saveHeight(getPrefHeight()));
        // A segmented control always keeps one segment active while panels exist.
        group.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == null && oldT != null && panels.values().stream().anyMatch(en -> en.toggle == oldT)) {
                oldT.setSelected(true);
                return;
            }
            updateContent();
        });
        resizer.setOnMousePressed(e -> {
            dragStartY = e.getScreenY();
            dragStartHeight = getHeight();
            e.consume();
        });
        resizer.setOnMouseDragged(e -> {
            double max = getParent() instanceof Region parent && parent.getHeight() > 0
                    ? Math.max(MIN_HEIGHT, parent.getHeight() - 80) : Double.MAX_VALUE;
            double height = Math.max(MIN_HEIGHT, Math.min(max, dragStartHeight + (dragStartY - e.getScreenY())));
            setPrefHeight(height);
            heightSaveDebounce.playFromStart();
            e.consume();
        });
        hide();
    }

    /**
     * Adds (or replaces) a panel under {@code key} with a toggle labelled {@code title}. The
     * panel starts hidden until it is {@linkplain #show(String) shown}.
     */
    public void register(String key, Region panel, String title) {
        remove(key);
        ToggleButton toggle = new ToggleButton(title);
        toggle.setId("bottom-dock-" + key);
        toggle.getStyleClass().add("fxt-seg");
        toggle.setToggleGroup(group);
        toggle.selectedProperty().addListener((obs, was, selected) -> {
            if (selected) {
                toggle.getStyleClass().remove(UPDATED_CLASS);
            }
        });
        panel.setVisible(false);
        panel.setManaged(false);
        panels.put(key, new Entry(toggle, panel));
        tabsBox.getChildren().add(toggle);
        content.getChildren().add(panel);
        if (group.getSelectedToggle() == null) {
            toggle.setSelected(true);
        }
        updateStrip();
        updateContent();
    }

    /** Removes the panel registered under {@code key}; hides the dock when none is left. */
    public void remove(String key) {
        Entry entry = panels.remove(key);
        if (entry == null) {
            return;
        }
        boolean wasSelected = entry.toggle.isSelected();
        entry.toggle.setToggleGroup(null);
        tabsBox.getChildren().remove(entry.toggle);
        content.getChildren().remove(entry.panel);
        if (wasSelected) {
            if (panels.isEmpty()) {
                hide();
            } else {
                panels.values().iterator().next().toggle.setSelected(true);
            }
        }
        updateStrip();
        updateContent();
    }

    /** Shows the dock with {@code key}'s panel selected. */
    public void show(String key) {
        show(key, true);
    }

    /**
     * Shows the dock. With {@code select}, {@code key}'s panel comes to the front; otherwise
     * the current selection stays (unless nothing is selected yet).
     */
    public void show(String key, boolean select) {
        Entry entry = panels.get(key);
        if (entry == null) {
            return;
        }
        setVisible(true);
        setManaged(true);
        if (select || group.getSelectedToggle() == null) {
            entry.toggle.setSelected(true);
        }
        updateContent();
    }

    /** Brings {@code key}'s panel to the front without changing the dock's visibility. */
    public void select(String key) {
        Entry entry = panels.get(key);
        if (entry != null) {
            entry.toggle.setSelected(true);
        }
    }

    /** Flags {@code key}'s toggle as holding fresh content, unless that panel is in front. */
    public void markUpdated(String key) {
        Entry entry = panels.get(key);
        if (entry != null && !entry.toggle.isSelected() && !entry.toggle.getStyleClass().contains(UPDATED_CLASS)) {
            entry.toggle.getStyleClass().add(UPDATED_CLASS);
        }
    }

    /** Hides the whole dock; the registered panels stay and come back with the next show. */
    public void hide() {
        setVisible(false);
        setManaged(false);
    }

    /** @return {@code true} while the dock is visible below the editor. */
    public boolean isShowing() {
        return isVisible();
    }

    /** @return {@code true} while the dock is visible with {@code key}'s panel in front. */
    public boolean isShowing(String key) {
        return isShowing() && isSelected(key);
    }

    /** @return {@code true} if {@code key}'s panel is the selected one (visible or not). */
    public boolean isSelected(String key) {
        Entry entry = panels.get(key);
        return entry != null && entry.toggle.isSelected();
    }

    /** @return {@code true} if a panel is registered under {@code key}. */
    public boolean has(String key) {
        return panels.containsKey(key);
    }

    /** @return the registered keys in registration order (for tests/observers). */
    public List<String> keys() {
        return new ArrayList<>(panels.keySet());
    }

    /** @return {@code true} while the toggle strip is shown (more than one panel registered). */
    public boolean isStripShown() {
        return strip.isVisible();
    }

    private void updateStrip() {
        boolean several = panels.size() > 1;
        strip.setVisible(several);
        strip.setManaged(several);
    }

    private void updateContent() {
        Toggle selected = group.getSelectedToggle();
        for (Entry entry : panels.values()) {
            boolean front = entry.toggle == selected;
            entry.panel.setVisible(front);
            entry.panel.setManaged(front);
        }
    }

    private static double loadHeight() {
        try {
            String value = org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class).get(HEIGHT_KEY);
            if (value != null) {
                return Math.max(MIN_HEIGHT, Double.parseDouble(value));
            }
        } catch (Throwable ignored) {
            // properties service unavailable (tests) or malformed value — use the default
        }
        return DEFAULT_HEIGHT;
    }

    private static void saveHeight(double height) {
        try {
            org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .set(HEIGHT_KEY, String.valueOf(Math.round(height)));
        } catch (Throwable ignored) {
            // nothing to persist to
        }
    }
}
