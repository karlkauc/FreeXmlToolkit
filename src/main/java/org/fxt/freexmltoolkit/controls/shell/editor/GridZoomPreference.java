package org.fxt.freexmltoolkit.controls.shell.editor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * Loads and saves the grid zoom factor ({@code grid.zoom}) through the
 * {@link PropertiesService}. One value is shared by every XML and JSON grid; when the
 * service is unavailable (isolated tests) the zoom simply defaults to 100 % and saving
 * is a no-op.
 */
final class GridZoomPreference {

    private static final Logger logger = LogManager.getLogger(GridZoomPreference.class);

    private GridZoomPreference() {
    }

    /** @return the persisted zoom factor, or 1.0 when none is available */
    static double load() {
        PropertiesService service = service();
        return service != null ? service.getGridZoom() : 1.0;
    }

    /** @param zoom the zoom factor to persist (ignored when no service is available) */
    static void save(double zoom) {
        PropertiesService service = service();
        if (service != null) {
            service.setGridZoom(zoom);
        }
    }

    private static PropertiesService service() {
        try {
            return ServiceRegistry.get(PropertiesService.class);
        } catch (Throwable t) {
            logger.debug("PropertiesService not available; grid zoom is not persisted", t);
            return null;
        }
    }
}
