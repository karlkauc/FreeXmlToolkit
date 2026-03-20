package org.fxt.freexmltoolkit.controls.v2.editor.core;

import java.io.File;

/**
 * Represents a request to navigate to an element definition in an XSD file.
 *
 * @param xsdFile     the XSD file containing the definition
 * @param elementName the name of the element to navigate to
 */
public record NavigationRequest(File xsdFile, String elementName) {
}
