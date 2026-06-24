package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAny;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAnyAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to change the {@code namespace} and {@code processContents} attributes of an
 * {@code xs:any} or {@code xs:anyAttribute} wildcard.
 * <p>
 * Both attributes are committed together (mirroring {@link ChangeConstraintsCommand}), and the
 * previous values are stored for undo. Consecutive edits on the same wildcard merge.
 *
 * @since 2.0
 */
public class ChangeWildcardCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(ChangeWildcardCommand.class);

    private final XsdEditorContext editorContext;
    private final XsdNode node;
    private final String oldNamespace;
    private final XsdAny.ProcessContents oldProcessContents;
    private final String newNamespace;
    private final XsdAny.ProcessContents newProcessContents;

    /**
     * Creates a new change-wildcard command.
     *
     * @param editorContext   the editor context
     * @param node            the target wildcard (must be {@link XsdAny} or {@link XsdAnyAttribute})
     * @param namespace       the new namespace value (blank/null clears it)
     * @param processContents the new processContents mode
     * @throws IllegalArgumentException if the node is not a wildcard
     */
    public ChangeWildcardCommand(XsdEditorContext editorContext, XsdNode node, String namespace,
                                 XsdAny.ProcessContents processContents) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (!(node instanceof XsdAny) && !(node instanceof XsdAnyAttribute)) {
            throw new IllegalArgumentException("Wildcard properties can only be set on xs:any / xs:anyAttribute, "
                    + "not on " + (node == null ? "null" : node.getClass().getSimpleName()));
        }
        this.editorContext = editorContext;
        this.node = node;
        this.oldNamespace = readNamespace();
        this.oldProcessContents = readProcessContents();
        this.newNamespace = (namespace == null || namespace.trim().isEmpty()) ? null : namespace.trim();
        this.newProcessContents = processContents;
    }

    private String readNamespace() {
        return node instanceof XsdAny any ? any.getNamespace() : ((XsdAnyAttribute) node).getNamespace();
    }

    private XsdAny.ProcessContents readProcessContents() {
        return node instanceof XsdAny any ? any.getProcessContents() : ((XsdAnyAttribute) node).getProcessContents();
    }

    private void apply(String namespace, XsdAny.ProcessContents processContents) {
        if (node instanceof XsdAny any) {
            any.setNamespace(namespace);
            any.setProcessContents(processContents);
        } else {
            XsdAnyAttribute anyAttr = (XsdAnyAttribute) node;
            anyAttr.setNamespace(namespace);
            anyAttr.setProcessContents(processContents);
        }
        editorContext.markNodeDirty(node);
    }

    @Override
    public boolean execute() {
        try {
            apply(newNamespace, newProcessContents);
            return true;
        } catch (Exception e) {
            logger.error("Failed to change wildcard properties of '{}'", node.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            apply(oldNamespace, oldProcessContents);
            return true;
        } catch (Exception e) {
            logger.error("Failed to undo wildcard change of '{}'", node.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Change wildcard of " + (node.getName() != null ? node.getName() : "(unnamed)");
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        return other instanceof ChangeWildcardCommand o && this.node.getId().equals(o.node.getId());
    }

    @Override
    public XsdCommand mergeWith(XsdCommand other) {
        ChangeWildcardCommand o = (ChangeWildcardCommand) other;
        // Span this command's old values to the newer command's new values ("old -> o.new").
        return new ChangeWildcardCommand(editorContext, node, oldNamespace, oldProcessContents,
                o.newNamespace, o.newProcessContents);
    }

    /** Internal constructor used for merging, carrying an explicit old/new value pair. */
    private ChangeWildcardCommand(XsdEditorContext editorContext, XsdNode node,
                                  String oldNamespace, XsdAny.ProcessContents oldProcessContents,
                                  String newNamespace, XsdAny.ProcessContents newProcessContents) {
        this.editorContext = editorContext;
        this.node = node;
        this.oldNamespace = oldNamespace;
        this.oldProcessContents = oldProcessContents;
        this.newNamespace = newNamespace;
        this.newProcessContents = newProcessContents;
    }
}
