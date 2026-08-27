package org.fxt.freexmltoolkit.domain;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Determines the namespace an <em>instance</em> element must be in for a given schema
 * declaration.
 *
 * <p>The answer depends on the schema document that declares the element, not on the main
 * schema: a locally declared child of a complexType from an imported schema lives in that
 * imported schema's target namespace (when {@code elementFormDefault="qualified"}). Each schema
 * file is parsed into its own DOM {@link Document}, so the declaring schema is simply the
 * owner document of the element's DOM node.</p>
 */
public final class XsdElementNamespaceResolver {

    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    private XsdElementNamespaceResolver() {
    }

    /**
     * Resolves the namespace URI an instance element for {@code element} must carry.
     *
     * @param element the schema element (as collected by the documentation service)
     * @param data    the schema data (used for the main target namespace fallback)
     * @return the namespace URI, or {@code null} if the instance element is unqualified
     */
    public static String resolveNamespaceUri(XsdExtendedElement element, XsdDocumentationData data) {
        if (element == null) {
            return null;
        }
        String explicit = element.getSourceNamespace();
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }

        String mainTargetNamespace = data != null ? blankToNull(data.getTargetNamespace()) : null;
        Node node = element.getCurrentNode();
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
            return mainTargetNamespace;
        }

        Element schemaRoot = schemaRootOf(node);
        if (schemaRoot == null) {
            return mainTargetNamespace;
        }
        // A chameleon schema (no targetNamespace) takes the namespace of the including schema.
        String targetNamespace = blankToNull(schemaRoot.getAttribute("targetNamespace"));
        if (targetNamespace == null) {
            targetNamespace = mainTargetNamespace;
        }

        if (isGlobalDeclaration(node)) {
            return targetNamespace;
        }

        String form = blankToNull(((Element) node).getAttribute("form"));
        if (form == null) {
            form = blankToNull(schemaRoot.getAttribute("elementFormDefault"));
        }
        return "qualified".equals(form) ? targetNamespace : null;
    }

    private static Element schemaRootOf(Node node) {
        Document owner = node.getOwnerDocument();
        Element root = owner != null ? owner.getDocumentElement() : null;
        if (root != null && "schema".equals(root.getLocalName())) {
            return root;
        }
        // Detached fragment: walk up to the outermost xs:schema if there is one.
        Node current = node.getParentNode();
        Element found = null;
        while (current != null) {
            if (current.getNodeType() == Node.ELEMENT_NODE && "schema".equals(current.getLocalName())) {
                found = (Element) current;
            }
            current = current.getParentNode();
        }
        return found;
    }

    private static boolean isGlobalDeclaration(Node node) {
        Node parent = node.getParentNode();
        return parent != null && parent.getNodeType() == Node.ELEMENT_NODE
                && "schema".equals(parent.getLocalName())
                && (parent.getNamespaceURI() == null || XSD_NS.equals(parent.getNamespaceURI()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
