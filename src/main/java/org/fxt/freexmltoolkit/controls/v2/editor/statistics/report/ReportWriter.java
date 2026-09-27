package org.fxt.freexmltoolkit.controls.v2.editor.statistics.report;

import java.io.IOException;
import java.nio.file.Path;

/** Renders a {@link ReportModel} into one file format. */
public interface ReportWriter {

    /**
     * Writes {@code model} to {@code target}, creating parent directories as needed.
     *
     * @throws IOException when the file cannot be written or the renderer fails
     */
    void write(ReportModel model, Path target) throws IOException;
}
