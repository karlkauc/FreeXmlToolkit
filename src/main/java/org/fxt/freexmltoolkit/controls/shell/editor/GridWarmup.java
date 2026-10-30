package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Loads the classes of the XML grid (Graphic view) on a worker once an XML document is open, so the first
 * switch to the Graphic view does not spend its FX-thread time on class loading. Classes are only loaded,
 * not initialized: static initializers still run on first use, wherever that is.
 */
final class GridWarmup {

    private static final Logger logger = LogManager.getLogger(GridWarmup.class);

    /** The grid's classes in the order the first switch needs them. */
    static final String[] CLASSES = {
            "javafx.fxml.FXMLLoader",
            "org.fxt.freexmltoolkit.controls.shell.editor.XmlGridView",
            "org.fxt.freexmltoolkit.controls.shell.editor.GridViewShell",
            "org.fxt.freexmltoolkit.controls.shell.editor.GridViewHeader",
            "org.fxt.freexmltoolkit.controls.v2.xmleditor.view.XmlCanvasView",
            "org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridCanvasView",
            "org.fxt.freexmltoolkit.controls.v2.xmleditor.view.XmlGridAdapter",
            "org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridModelAdapter",
            "org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRow",
            "org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRow$RowType",
            "org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridViewState",
            "org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable",
            "org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridMetrics",
            "org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColors",
            "org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridFont",
            "org.fxt.freexmltoolkit.controls.v2.xmleditor.view.TextBlock",
            "org.fxt.freexmltoolkit.controls.v2.xmleditor.view.TextWrap",
            "org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FxTextMeasurer",
            "org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNodeXPath",
    };

    private static final AtomicBoolean started = new AtomicBoolean();

    private GridWarmup() {
    }

    /** Starts loading the grid's classes on a worker; only the first call does anything. */
    static void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        try {
            org.fxt.freexmltoolkit.FxtGui.executorService.submit(GridWarmup::loadClasses);
        } catch (RuntimeException e) {
            logger.debug("Grid warm-up not started: {}", e.toString());
        }
    }

    private static void loadClasses() {
        ClassLoader loader = GridWarmup.class.getClassLoader();
        for (String name : CLASSES) {
            try {
                Class.forName(name, false, loader);
            } catch (ClassNotFoundException | LinkageError e) {
                logger.debug("Grid warm-up skipped {}: {}", name, e.toString());
            }
        }
    }
}
