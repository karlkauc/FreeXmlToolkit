package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import javafx.scene.layout.HBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorContext;
import org.fxt.freexmltoolkit.controls.editor.StatusLineController;

/**
 * Status line manager for XmlCodeEditorV2.
 * TODO: Implement V2 version.
 *
 * <p>For now, delegates to V1.</p>
 */
public class StatusLineManagerV2 {

    private static final Logger logger = LogManager.getLogger(StatusLineManagerV2.class);

    private final StatusLineController v1Controller;

    public StatusLineManagerV2(EditorContext editorContext) {
        // Delegate to V1 for now - need PropertiesService
        org.fxt.freexmltoolkit.service.PropertiesService propertiesService =
            org.fxt.freexmltoolkit.service.PropertiesServiceImpl.getInstance();
        this.v1Controller = new StatusLineController(editorContext.getCodeArea(), propertiesService);
        logger.info("StatusLineManagerV2 created (using V1 delegate)");
    }

    /**
     * Gets the status line UI component.
     */
    public HBox getStatusLine() {
        return v1Controller.getStatusLine();
    }

    /**
     * Refreshes cursor position display.
     */
    public void refreshCursorPosition() {
        v1Controller.refreshCursorPosition();
    }
}
