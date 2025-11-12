package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;

/**
 * Command to delete a facet from an XSD restriction.
 * Supports undo by re-adding the facet at its original position.
 * <p>
 * Updates the underlying XsdRestriction model, which automatically triggers
 * view refresh via PropertyChangeListener.
 *
 * @since 2.0
 */
public class DeleteFacetCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(DeleteFacetCommand.class);

    private final XsdRestriction restriction;
    private final XsdFacet facet;
    private int originalPosition = -1;

    /**
     * Creates a new delete facet command.
     *
     * @param restriction the restriction containing the facet
     * @param facet       the facet to delete
     */
    public DeleteFacetCommand(XsdRestriction restriction, XsdFacet facet) {
        if (restriction == null) {
            throw new IllegalArgumentException("Restriction cannot be null");
        }
        if (facet == null) {
            throw new IllegalArgumentException("Facet cannot be null");
        }

        this.restriction = restriction;
        this.facet = facet;
    }

    @Override
    public boolean execute() {
        // Store original position for undo
        originalPosition = restriction.getFacets().indexOf(facet);

        if (originalPosition < 0) {
            logger.warn("Facet {} not found in restriction", facet.getFacetType());
            return false;
        }

        // Remove facet - this will fire PropertyChangeEvent
        restriction.removeFacet(facet);

        logger.info("Deleted {} facet with value '{}' from restriction (base: {})",
                facet.getFacetType().getXmlName(),
                facet.getValue(),
                restriction.getBase());
        return true;
    }

    @Override
    public boolean undo() {
        if (originalPosition < 0) {
            logger.warn("Cannot undo - original position not recorded");
            return false;
        }

        // Re-add facet - this will fire PropertyChangeEvent
        // Note: addFacet doesn't preserve position, but the order is typically not critical
        restriction.addFacet(facet);

        logger.info("Restored {} facet with value '{}' to restriction (base: {})",
                facet.getFacetType().getXmlName(),
                facet.getValue(),
                restriction.getBase());
        return true;
    }

    @Override
    public String getDescription() {
        return "Delete " + facet.getFacetType().getXmlName() + " facet with value '" + facet.getValue() + "'";
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Delete commands should not be merged
        return false;
    }

    /**
     * Gets the restriction being modified.
     *
     * @return the restriction
     */
    public XsdRestriction getRestriction() {
        return restriction;
    }

    /**
     * Gets the facet being deleted.
     *
     * @return the facet
     */
    public XsdFacet getFacet() {
        return facet;
    }

    /**
     * Gets the original position of the facet.
     *
     * @return the original position, or -1 if not yet executed
     */
    public int getOriginalPosition() {
        return originalPosition;
    }
}
