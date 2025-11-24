package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

/**
 * Visitor interface for traversing the XML node tree.
 *
 * <p>Implements the Visitor pattern to allow extensible operations
 * on the node tree without modifying the node classes.</p>
 *
 * <p>Example use cases:</p>
 * <ul>
 *   <li>Serialization to different formats (XML, JSON, etc.)</li>
 *   <li>Validation against XSD schema</li>
 *   <li>XPath query evaluation</li>
 *   <li>Statistics collection (node count, depth, etc.)</li>
 *   <li>Transformation operations</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * XmlNodeVisitor counter = new XmlNodeVisitor() {
 *     private int elementCount = 0;
 *
 *     public void visit(XmlElement element) {
 *         elementCount++;
 *         // Visit children
 *         element.getChildren().forEach(child -> child.accept(this));
 *     }
 *
 *     // Implement other visit methods...
 * };
 *
 * document.accept(counter);
 * }</pre>
 *
 * @author Claude Code
 * @since 2.0
 */
public interface XmlNodeVisitor {

    /**
     * Visits an XmlDocument node.
     *
     * @param document the document to visit
     */
    default void visit(XmlDocument document) {
        // Default: do nothing
    }

    /**
     * Visits an XmlElement node.
     *
     * @param element the element to visit
     */
    default void visit(XmlElement element) {
        // Default: do nothing
    }

    /**
     * Visits an XmlText node.
     *
     * @param text the text node to visit
     */
    default void visit(XmlText text) {
        // Default: do nothing
    }

    /**
     * Visits an XmlAttribute node.
     *
     * @param attribute the attribute to visit
     */
    default void visit(XmlAttribute attribute) {
        // Default: do nothing
    }

    /**
     * Visits an XmlComment node.
     *
     * @param comment the comment to visit
     */
    default void visit(XmlComment comment) {
        // Default: do nothing
    }

    /**
     * Visits an XmlCData node.
     *
     * @param cdata the CDATA section to visit
     */
    default void visit(XmlCData cdata) {
        // Default: do nothing
    }

    /**
     * Visits an XmlProcessingInstruction node.
     *
     * @param pi the processing instruction to visit
     */
    default void visit(XmlProcessingInstruction pi) {
        // Default: do nothing
    }
}
