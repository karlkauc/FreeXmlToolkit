package org.fxt.freexmltoolkit.controls.v2.editor.core;

import java.io.File;

/**
 * Represents a request to navigate to an element definition in an XSD file.
 *
 * @param xsdFile       the XSD file containing the definition
 * @param elementName   the name of the element to navigate to (the fallback when the path finds nothing)
 * @param instanceXPath the element's path in the document from the root ({@code /a/b/c}, positions and
 *                      prefixes allowed), so the declaration is found by structure; null = by name only
 */
public record NavigationRequest(File xsdFile, String elementName, String instanceXPath) {

    /** A request by name only. */
    public NavigationRequest(File xsdFile, String elementName) {
        this(xsdFile, elementName, null);
    }
}
