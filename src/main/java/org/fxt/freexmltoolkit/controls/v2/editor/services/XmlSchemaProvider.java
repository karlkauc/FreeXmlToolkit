package org.fxt.freexmltoolkit.controls.v2.editor.services;

import org.fxt.freexmltoolkit.domain.XsdDocumentationData;

/**
 * Interface for providing XSD schema information to the editor.
 * This abstraction decouples the editor from specific schema implementations.
 *
 * <p>Implementations should provide access to XSD documentation data for
 * context-sensitive IntelliSense and validation.</p>
 */
public interface XmlSchemaProvider {

    /**
     * Checks if an XSD schema is currently available.
     *
     * @return true if schema is loaded, false otherwise
     */
    boolean hasSchema();

    /**
     * Gets the XSD documentation data.
     *
     * @return the documentation data, or null if no schema is loaded
     */
    XsdDocumentationData getXsdDocumentationData();

    /**
     * Gets the path to the XSD file.
     *
     * @return the XSD file path, or null if no schema is loaded
     */
    String getXsdFilePath();

    /**
     * Finds the best matching XSD element for a given XPath.
     * This method should handle partial matches and context-based lookups.
     *
     * @param xpath the XPath to search for
     * @return the matching element documentation, or null if not found
     */
    org.fxt.freexmltoolkit.domain.XsdExtendedElement findBestMatchingElement(String xpath);
}
