package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;

/**
 * Command to edit an existing facet's value.
 * Supports undo by restoring the original value.
 * <p>
 * Updates the underlying XsdFacet model, which automatically triggers
 * view refresh via PropertyChangeListener.
 *
 * @since 2.0
 */
public class EditFacetCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(EditFacetCommand.class);

    private final XsdFacet facet;
    private final String oldValue;
    private final String newValue;
    private final boolean oldFixed;
    private final boolean newFixed;

    /**
     * Creates a new edit facet command (value only).
     *
     * @param facet    the facet to edit
     * @param newValue the new facet value
     */
    public EditFacetCommand(XsdFacet facet, String newValue) {
        this(facet, newValue, facet != null ? facet.isFixed() : false);
    }

    /**
     * Private constructor for merging commands.
     * Allows setting oldValue/oldFixed explicitly instead of reading from facet.
     *
     * @param facet    the facet to edit
     * @param oldValue the original old value
     * @param oldFixed the original old fixed flag
     * @param newValue the new value
     * @param newFixed the new fixed flag
     */
    private EditFacetCommand(XsdFacet facet, String oldValue, boolean oldFixed, String newValue, boolean newFixed) {
        if (facet == null) {
            throw new IllegalArgumentException("Facet cannot be null");
        }
        if (newValue == null || newValue.trim().isEmpty()) {
            throw new IllegalArgumentException("New value cannot be null or empty");
        }

        this.facet = facet;
        this.oldValue = oldValue;
        this.newValue = newValue.trim();
        this.oldFixed = oldFixed;
        this.newFixed = newFixed;
    }

    /**
     * Creates a new edit facet command (value and fixed flag).
     *
     * @param facet    the facet to edit
     * @param newValue the new facet value
     * @param newFixed the new fixed flag
     */
    public EditFacetCommand(XsdFacet facet, String newValue, boolean newFixed) {
        if (facet == null) {
            throw new IllegalArgumentException("Facet cannot be null");
        }
        if (newValue == null || newValue.trim().isEmpty()) {
            throw new IllegalArgumentException("New value cannot be null or empty");
        }

        this.facet = facet;
        this.oldValue = facet.getValue();
        this.newValue = newValue.trim();
        this.oldFixed = facet.isFixed();
        this.newFixed = newFixed;

        // Check if there's actually a change
        if (this.oldValue != null && this.oldValue.equals(this.newValue) && this.oldFixed == this.newFixed) {
            logger.debug("No change detected for facet {}", facet.getFacetType());
        }
    }

    @Override
    public boolean execute() {
        // Update the facet - this will fire PropertyChangeEvent
        facet.setValue(newValue);
        facet.setFixed(newFixed);

        logger.info("Edited {} facet from '{}' to '{}'{}",
                facet.getFacetType().getXmlName(),
                oldValue,
                newValue,
                newFixed != oldFixed ? " (fixed: " + oldFixed + " → " + newFixed + ")" : "");
        return true;
    }

    @Override
    public boolean undo() {
        // Restore old value - this will fire PropertyChangeEvent
        facet.setValue(oldValue);
        facet.setFixed(oldFixed);

        logger.info("Restored {} facet from '{}' back to '{}'{}",
                facet.getFacetType().getXmlName(),
                newValue,
                oldValue,
                newFixed != oldFixed ? " (fixed: " + newFixed + " → " + oldFixed + ")" : "");
        return true;
    }

    @Override
    public String getDescription() {
        return "Edit " + facet.getFacetType().getXmlName() + " facet to '" + newValue + "'";
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Consecutive edits of the same facet can be merged
        if (!(other instanceof EditFacetCommand otherEdit)) {
            return false;
        }

        return this.facet.getId().equals(otherEdit.facet.getId()) &&
                this.newValue.equals(otherEdit.oldValue) &&
                this.newFixed == otherEdit.oldFixed;
    }

    @Override
    public XsdCommand mergeWith(XsdCommand other) {
        if (!canMergeWith(other)) {
            throw new IllegalArgumentException("Cannot merge with command: " + other);
        }

        EditFacetCommand otherEdit = (EditFacetCommand) other;
        // Create a new command that goes from this.oldValue to otherEdit.newValue
        // Use private constructor to preserve original oldValue/oldFixed
        return new EditFacetCommand(this.facet, this.oldValue, this.oldFixed, otherEdit.newValue, otherEdit.newFixed);
    }

    /**
     * Gets the facet being edited.
     *
     * @return the facet
     */
    public XsdFacet getFacet() {
        return facet;
    }

    /**
     * Gets the old value.
     *
     * @return the old value
     */
    public String getOldValue() {
        return oldValue;
    }

    /**
     * Gets the new value.
     *
     * @return the new value
     */
    public String getNewValue() {
        return newValue;
    }
}
