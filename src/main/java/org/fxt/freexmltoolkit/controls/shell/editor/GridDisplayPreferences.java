package org.fxt.freexmltoolkit.controls.shell.editor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * Loads and saves the grid display preferences shared by every XML and JSON grid: word wrap
 * ({@code grid.wordWrap}) and the cell-height limit ({@code grid.maxCellLines}), through the
 * {@link PropertiesService}. Without a service (isolated tests) the defaults apply and
 * saving is a no-op — the same contract as {@link GridZoomPreference}.
 */
final class GridDisplayPreferences {

    private static final Logger logger = LogManager.getLogger(GridDisplayPreferences.class);

    private GridDisplayPreferences() {
    }

    /** @return whether values wrap (the default when nothing is persisted) */
    static boolean loadWordWrap() {
        PropertiesService service = service();
        return service == null || service.isGridWordWrap();
    }

    /** @param wrap the word-wrap setting to persist (ignored without a service) */
    static void saveWordWrap(boolean wrap) {
        PropertiesService service = service();
        if (service != null) {
            service.setGridWordWrap(wrap);
        }
    }

    /** @return the persisted cell-line limit, 0 (unlimited) when none is available */
    static int loadMaxCellLines() {
        PropertiesService service = service();
        return service != null ? service.getGridMaxCellLines() : 0;
    }

    /** @param lines the cell-line limit to persist (ignored without a service) */
    static void saveMaxCellLines(int lines) {
        PropertiesService service = service();
        if (service != null) {
            service.setGridMaxCellLines(lines);
        }
    }

    /** @return the persisted sibling-group size, 0 (no grouping) when none is available */
    static int loadSiblingGroupSize() {
        PropertiesService service = service();
        return service != null ? service.getGridSiblingGroupSize() : 0;
    }

    /** @param size the sibling-group size to persist (ignored without a service) */
    static void saveSiblingGroupSize(int size) {
        PropertiesService service = service();
        if (service != null) {
            service.setGridSiblingGroupSize(size);
        }
    }

    /** Loads the user's grid table colours (both themes) into {@link GridColors}. */
    static void loadColors() {
        PropertiesService service = service();
        if (service == null) {
            return;
        }
        for (org.fxt.freexmltoolkit.controls.theme.DesignTokens.Theme theme
                : org.fxt.freexmltoolkit.controls.theme.DesignTokens.Theme.values()) {
            for (GridColors.Slot slot : GridColors.Slot.values()) {
                String hex = service.getGridColor(GridColors.key(slot), GridColors.key(theme));
                javafx.scene.paint.Color color = null;
                if (hex != null) {
                    try {
                        color = javafx.scene.paint.Color.web(hex);
                    } catch (IllegalArgumentException e) {
                        logger.debug("Ignoring invalid grid colour {} = {}", slot, hex);
                    }
                }
                if (!java.util.Objects.equals(color, GridColors.override(theme, slot))) {
                    GridColors.setOverride(theme, slot, color);
                }
            }
        }
    }

    private static PropertiesService service() {
        try {
            return ServiceRegistry.get(PropertiesService.class);
        } catch (Throwable t) {
            logger.debug("PropertiesService not available; grid display preferences are not persisted", t);
            return null;
        }
    }
}
