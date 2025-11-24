package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

/**
 * Enumeration of all XML node types in the V2 model.
 *
 * <p>Corresponds to the W3C DOM node types:</p>
 * <ul>
 *   <li>DOCUMENT - The root document node</li>
 *   <li>ELEMENT - Element nodes (e.g., &lt;book&gt;)</li>
 *   <li>TEXT - Text content nodes</li>
 *   <li>CDATA - CDATA sections</li>
 *   <li>COMMENT - Comment nodes</li>
 *   <li>PROCESSING_INSTRUCTION - Processing instructions (e.g., &lt;?xml-stylesheet?&gt;)</li>
 *   <li>ATTRIBUTE - Attribute nodes (stored in elements, not as separate nodes in tree)</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public enum XmlNodeType {

    /**
     * Document node - the root of the XML tree.
     */
    DOCUMENT,

    /**
     * Element node - represents XML elements.
     */
    ELEMENT,

    /**
     * Text node - represents text content.
     */
    TEXT,

    /**
     * CDATA section node.
     */
    CDATA,

    /**
     * Comment node.
     */
    COMMENT,

    /**
     * Processing instruction node.
     */
    PROCESSING_INSTRUCTION,

    /**
     * Attribute node (logical type, stored in elements).
     */
    ATTRIBUTE
}
