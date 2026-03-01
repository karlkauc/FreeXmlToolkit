package org.fxt.freexmltoolkit.service.xsd;

import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.service.TaskProgressListener;

import java.io.File;
import java.util.Set;

/**
 * Interface for XSD documentation exporters.
 */
public interface DocumentationExporter {
    /**
     * Exports the given schema documentation to the specified target.
     *
     * @param schema The schema to document
     * @param outputTarget The target file or directory
     * @param includedLanguages Optional set of languages to include
     * @param progressListener Optional listener for progress updates
     * @throws Exception If exportation fails
     */
    void export(XsdSchema schema, File outputTarget, Set<String> includedLanguages, TaskProgressListener progressListener) throws Exception;
}
