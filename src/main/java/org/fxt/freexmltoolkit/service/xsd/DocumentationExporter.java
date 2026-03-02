package org.fxt.freexmltoolkit.service.xsd;

import java.io.File;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.service.TaskProgressListener;

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
