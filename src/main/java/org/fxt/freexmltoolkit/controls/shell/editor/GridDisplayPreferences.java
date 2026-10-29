package org.fxt.freexmltoolkit.controls.shell.editor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
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

    private static PropertiesService service() {
        try {
            return ServiceRegistry.get(PropertiesService.class);
        } catch (Throwable t) {
            logger.debug("PropertiesService not available; grid display preferences are not persisted", t);
            return null;
        }
    }
}
