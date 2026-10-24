package org.fxt.freexmltoolkit.debugger;

import java.util.Optional;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.type.Type;

/**
 * Where a paused context node lives in its source document: the document's system id (a
 * {@code file:} URI, or empty for in-memory input) and the 1-based line. Needs a processor with
 * line numbering on (the debug processor). Attribute, text and other nodes without a line of
 * their own report their element's line; a document node reports its root element's line.
 *
 * @param systemId the document's system id, {@code ""} when unknown
 * @param line     1-based line number
 */
public record InputLocation(String systemId, int line) {

    public InputLocation {
        systemId = systemId == null ? "" : systemId;
    }

    /** @return the location of {@code item} when it is a node with a known line. */
    public static Optional<InputLocation> of(XdmItem item) {
        if (!(item instanceof XdmNode node)) {
            return Optional.empty();
        }
        NodeInfo n = node.getUnderlyingNode();
        if (node.getNodeKind() == XdmNodeKind.DOCUMENT) {
            n = firstElementChild(n);
        }
        while (n != null && n.getLineNumber() <= 0) {
            n = n.getParent();
        }
        if (n == null) {
            return Optional.empty();
        }
        return Optional.of(new InputLocation(n.getSystemId(), n.getLineNumber()));
    }

    private static NodeInfo firstElementChild(NodeInfo document) {
        for (NodeInfo child : document.children(c -> c.getNodeKind() == Type.ELEMENT)) {
            return child;
        }
        return null;
    }
}
