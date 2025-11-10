package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to change the cardinality (minOccurs/maxOccurs) of an XSD element.
 * Supports undo by restoring the original cardinality.
 * <p>
 * Updates the underlying XsdNode model, which automatically triggers
 * view refresh via PropertyChangeListener (Phase 2.3).
 *
 * @since 2.0
 */
public class ChangeCardinalityCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(ChangeCardinalityCommand.class);

    /**
     * Special value indicating "unbounded" for maxOccurs.
     */
    public static final int UNBOUNDED = -1;

    private final XsdNode node;
    private final int oldMinOccurs;
    private final int oldMaxOccurs;
    private final int newMinOccurs;
    private final int newMaxOccurs;

    /**
     * Creates a new change cardinality command.
     *
     * @param node         the node whose cardinality to change
     * @param newMinOccurs the new minimum occurrences (>= 0)
     * @param newMaxOccurs the new maximum occurrences (>= minOccurs, or UNBOUNDED)
     */
    public ChangeCardinalityCommand(XsdNode node, int newMinOccurs, int newMaxOccurs) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (newMinOccurs < 0) {
            throw new IllegalArgumentException("minOccurs must be >= 0");
        }
        if (newMaxOccurs != UNBOUNDED && newMaxOccurs < newMinOccurs) {
            throw new IllegalArgumentException("maxOccurs must be >= minOccurs or UNBOUNDED");
        }

        this.node = node;
        this.newMinOccurs = newMinOccurs;
        this.newMaxOccurs = newMaxOccurs;

        // Get old cardinality from XsdNode
        this.oldMinOccurs = node.getMinOccurs();
        this.oldMaxOccurs = node.getMaxOccurs();
    }

    @Override
    public boolean execute() {
        // Update the model - this will fire PropertyChangeEvent
        // which triggers automatic view refresh via VisualNode's listener
        node.setMinOccurs(newMinOccurs);
        node.setMaxOccurs(newMaxOccurs);

        String oldCard = formatCardinality(oldMinOccurs, oldMaxOccurs);
        String newCard = formatCardinality(newMinOccurs, newMaxOccurs);

        logger.info("Changed cardinality of '{}' from {} to {}",
                node.getName(), oldCard, newCard);
        return true;
    }

    @Override
    public boolean undo() {
        // Update the model - this will fire PropertyChangeEvent
        node.setMinOccurs(oldMinOccurs);
        node.setMaxOccurs(oldMaxOccurs);

        String oldCard = formatCardinality(oldMinOccurs, oldMaxOccurs);
        String newCard = formatCardinality(newMinOccurs, newMaxOccurs);

        logger.info("Restored cardinality of '{}' from {} back to {}",
                node.getName(), newCard, oldCard);
        return true;
    }

    @Override
    public String getDescription() {
        String oldCard = formatCardinality(oldMinOccurs, oldMaxOccurs);
        String newCard = formatCardinality(newMinOccurs, newMaxOccurs);
        return "Change cardinality of '" + node.getName() + "' from " + oldCard + " to " + newCard;
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Consecutive cardinality changes on the same node can be merged
        if (!(other instanceof ChangeCardinalityCommand otherChange)) {
            return false;
        }

        return this.node.getId().equals(otherChange.node.getId()) &&
                this.newMinOccurs == otherChange.oldMinOccurs &&
                this.newMaxOccurs == otherChange.oldMaxOccurs;
    }

    /**
     * Gets the node being modified.
     *
     * @return the node
     */
    public XsdNode getNode() {
        return node;
    }

    /**
     * Gets the new minimum occurrences.
     *
     * @return the new minOccurs
     */
    public int getNewMinOccurs() {
        return newMinOccurs;
    }

    /**
     * Gets the new maximum occurrences.
     *
     * @return the new maxOccurs (or UNBOUNDED)
     */
    public int getNewMaxOccurs() {
        return newMaxOccurs;
    }

    /**
     * Formats cardinality as a readable string.
     *
     * @param minOccurs the minimum occurrences
     * @param maxOccurs the maximum occurrences (or UNBOUNDED)
     * @return formatted string (e.g., "[1..1]", "[0..*]")
     */
    private String formatCardinality(int minOccurs, int maxOccurs) {
        String max = maxOccurs == UNBOUNDED ? "*" : String.valueOf(maxOccurs);
        return "[" + minOccurs + ".." + max + "]";
    }
}
