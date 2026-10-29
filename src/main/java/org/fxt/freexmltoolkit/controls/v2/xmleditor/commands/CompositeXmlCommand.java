package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs several XML commands as one undoable step (e.g. inserting a table column into every
 * member of a repeating group, renaming a column, pasting a block of cells). Executes the
 * parts in order; when a part fails, the parts already executed are undone again so the
 * document is left as it was. Undo reverts the parts in reverse order.
 */
public final class CompositeXmlCommand implements XmlCommand {

    private final String description;
    private final List<XmlCommand> parts;
    private int executedParts = 0;

    /**
     * @param description the human-readable description of the whole step
     * @param parts       the commands to run, in order (never modified)
     */
    public CompositeXmlCommand(String description, List<? extends XmlCommand> parts) {
        this.description = description;
        this.parts = new ArrayList<>(parts);
    }

    /** @return the commands this step consists of, in execution order */
    public List<XmlCommand> getParts() {
        return List.copyOf(parts);
    }

    /** @return whether the composite has no parts at all */
    public boolean isEmpty() {
        return parts.isEmpty();
    }

    @Override
    public boolean execute() {
        executedParts = 0;
        for (XmlCommand part : parts) {
            if (!part.execute()) {
                rollback();
                return false;
            }
            executedParts++;
        }
        return true;
    }

    /** Undoes the parts executed so far (after a failed part), in reverse order. */
    private void rollback() {
        for (int i = executedParts - 1; i >= 0; i--) {
            parts.get(i).undo();
        }
        executedParts = 0;
    }

    @Override
    public boolean undo() {
        boolean ok = true;
        for (int i = executedParts - 1; i >= 0; i--) {
            ok &= parts.get(i).undo();
        }
        executedParts = 0;
        return ok;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
