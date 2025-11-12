package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;

/**
 * Command to add a new facet to an XSD restriction.
 * Supports undo by removing the facet.
 * <p>
 * Updates the underlying XsdRestriction model, which automatically triggers
 * view refresh via PropertyChangeListener.
 *
 * @since 2.0
 */
public class AddFacetCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AddFacetCommand.class);

    private final XsdRestriction restriction;
    private final XsdFacetType facetType;
    private final String value;
    private final boolean fixed;
    private XsdFacet createdFacet;

    /**
     * Creates a new add facet command.
     *
     * @param restriction the restriction to add the facet to
     * @param facetType   the type of facet to add
     * @param value       the facet value
     */
    public AddFacetCommand(XsdRestriction restriction, XsdFacetType facetType, String value) {
        this(restriction, facetType, value, false);
    }

    /**
     * Creates a new add facet command with fixed flag.
     *
     * @param restriction the restriction to add the facet to
     * @param facetType   the type of facet to add
     * @param value       the facet value
     * @param fixed       whether the facet is fixed (cannot be changed in derived types)
     */
    public AddFacetCommand(XsdRestriction restriction, XsdFacetType facetType, String value, boolean fixed) {
        if (restriction == null) {
            throw new IllegalArgumentException("Restriction cannot be null");
        }
        if (facetType == null) {
            throw new IllegalArgumentException("Facet type cannot be null");
        }
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Facet value cannot be null or empty");
        }

        this.restriction = restriction;
        this.facetType = facetType;
        this.value = value.trim();
        this.fixed = fixed;
    }

    @Override
    public boolean execute() {
        // Create new facet
        createdFacet = new XsdFacet(facetType, value);
        createdFacet.setFixed(fixed);

        // Add to restriction - this will fire PropertyChangeEvent
        restriction.addFacet(createdFacet);

        logger.info("Added {} facet with value '{}' to restriction (base: {})",
                facetType.getXmlName(), value, restriction.getBase());
        return true;
    }

    @Override
    public boolean undo() {
        if (createdFacet == null) {
            logger.warn("Cannot undo - no facet was created");
            return false;
        }

        // Remove facet - this will fire PropertyChangeEvent
        restriction.removeFacet(createdFacet);

        logger.info("Removed {} facet with value '{}' from restriction (base: {})",
                facetType.getXmlName(), value, restriction.getBase());
        return true;
    }

    @Override
    public String getDescription() {
        return "Add " + facetType.getXmlName() + " facet with value '" + value + "'";
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Facet commands should not be merged
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
     * Gets the facet type being added.
     *
     * @return the facet type
     */
    public XsdFacetType getFacetType() {
        return facetType;
    }

    /**
     * Gets the facet value.
     *
     * @return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * Gets the created facet (after execute).
     *
     * @return the created facet, or null if not yet executed
     */
    public XsdFacet getCreatedFacet() {
        return createdFacet;
    }
}
