package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;

/**
 * Package-private parts of the documentation generator for the tutorial video scenarios (they live in
 * the {@code screenshots} package): set the output folder without the native folder chooser, keep the
 * result from opening in the system browser, and read the progress. Call on the FX thread.
 */
public final class DocumentationVideoProbe {

    private DocumentationVideoProbe() {
    }

    /** Sets schema and output folder as the Browse buttons would, and switches "open after" off. */
    public static void prepare(DocumentationView view, File xsd, File outputFolder) {
        view.setFiles(xsd, outputFolder);
        if (view.lookup("#openAfter") instanceof javafx.scene.control.CheckBox open) {
            open.setSelected(false);
        }
    }

    /** @return the status line below the Generate button */
    public static String status(DocumentationView view) {
        return view.getStatusText();
    }
}
