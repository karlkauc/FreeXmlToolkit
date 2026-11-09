package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.animation.PauseTransition;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * Makes a panel below the editor (OUTPUT/DEBUG dock, PROBLEMS) resizable by dragging a thin
 * handle at its top edge, and remembers the height under a properties key. The panel never
 * grows so far that less than {@link #EDITOR_RESERVE} pixels of its parent are left above it.
 */
final class DockResizer {

    /** Height of the parent that stays for the editor above a dragged panel. */
    static final double EDITOR_RESERVE = 110;

    private final Region panel;
    private final String key;
    private final double min;
    private final double defaultHeight;
    private final PauseTransition saveDebounce = new PauseTransition(Duration.millis(400));
    private double dragStartY;
    private double dragStartHeight;

    /**
     * @param panel   the panel whose preferred height the drag sets
     * @param handle  the drag handle at the panel's top edge
     * @param key     properties key of the persisted height
     * @param min     smallest height
     * @param defaultHeight height before the user dragged it
     */
    DockResizer(Region panel, Region handle, String key, double min, double defaultHeight) {
        this.panel = panel;
        this.key = key;
        this.min = min;
        this.defaultHeight = defaultHeight;
        saveDebounce.setOnFinished(e -> save(panel.getPrefHeight()));
        handle.setOnMousePressed(e -> {
            dragStartY = e.getScreenY();
            dragStartHeight = panel.getHeight();
            e.consume();
        });
        handle.setOnMouseDragged(e -> {
            panel.setPrefHeight(clamp(dragStartHeight + (dragStartY - e.getScreenY())));
            saveDebounce.playFromStart();
            e.consume();
        });
    }

    /** @return {@code height} within the smallest height and the room the parent leaves */
    double clamp(double height) {
        double max = panel.getParent() instanceof Region parent && parent.getHeight() > 0
                ? Math.max(min, parent.getHeight() - EDITOR_RESERVE) : Double.MAX_VALUE;
        return Math.max(min, Math.min(max, height));
    }

    /** @return the persisted height, or the default (also when no properties service is there, e.g. tests) */
    double load() {
        try {
            String value = ServiceRegistry.get(PropertiesService.class).get(key);
            if (value != null) {
                return Math.max(min, Double.parseDouble(value));
            }
        } catch (Throwable ignored) {
            // properties service unavailable (tests) or malformed value — use the default
        }
        return defaultHeight;
    }

    private void save(double height) {
        try {
            ServiceRegistry.get(PropertiesService.class).set(key, String.valueOf(Math.round(height)));
        } catch (Throwable ignored) {
            // nothing to persist to
        }
    }
}
