package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Deletes several nodes as one undoable step (e.g. "Remove unused components").
 * <p>
 * {@link DeleteNodeCommand} captures each node's child index at construction time, so the
 * deletions run in <em>descending</em> index order per parent — an earlier deletion then never
 * shifts a later node's captured index — and undo re-inserts in the reverse (ascending) order,
 * which restores the original sibling order exactly. On a partial failure the already executed
 * deletions are undone again so the model stays consistent.
 */
public final class DeleteNodesCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(DeleteNodesCommand.class);

    private final List<DeleteNodeCommand> deletes;
    private final String description;

    /**
     * @param nodes       the nodes to delete (each must have a parent); duplicates are ignored
     * @param description the undo-history label, e.g. "Remove 3 unused components"
     */
    public DeleteNodesCommand(List<XsdNode> nodes, String description) {
        Objects.requireNonNull(nodes, "nodes");
        this.description = description != null ? description : "Delete " + nodes.size() + " nodes";
        List<XsdNode> ordered = new ArrayList<>();
        for (XsdNode node : nodes) {
            if (node == null || node.getParent() == null) {
                throw new IllegalArgumentException("Every node must have a parent: " + node);
            }
            if (!ordered.contains(node)) {
                ordered.add(node);
            }
        }
        // Descending child index within each parent; parents kept in first-seen order.
        List<XsdNode> parents = new ArrayList<>();
        for (XsdNode node : ordered) {
            if (!parents.contains(node.getParent())) {
                parents.add(node.getParent());
            }
        }
        ordered.sort(Comparator.<XsdNode>comparingInt(n -> parents.indexOf(n.getParent()))
                .thenComparing(Comparator.<XsdNode>comparingInt(n -> n.getParent().getChildren().indexOf(n)).reversed()));
        List<DeleteNodeCommand> commands = new ArrayList<>(ordered.size());
        for (XsdNode node : ordered) {
            commands.add(new DeleteNodeCommand(node));
        }
        this.deletes = List.copyOf(commands);
    }

    /** @return the number of nodes this command deletes. */
    public int size() {
        return deletes.size();
    }

    @Override
    public boolean execute() {
        int done = 0;
        for (DeleteNodeCommand delete : deletes) {
            if (!delete.execute()) {
                logger.warn("Deletion {} of {} failed — rolling back", done + 1, deletes.size());
                for (int i = done - 1; i >= 0; i--) {
                    deletes.get(i).undo();
                }
                return false;
            }
            done++;
        }
        return true;
    }

    @Override
    public boolean undo() {
        boolean ok = true;
        for (int i = deletes.size() - 1; i >= 0; i--) {
            ok &= deletes.get(i).undo();
        }
        return ok;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean canUndo() {
        return deletes.stream().allMatch(DeleteNodeCommand::canUndo);
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        return false;
    }
}
