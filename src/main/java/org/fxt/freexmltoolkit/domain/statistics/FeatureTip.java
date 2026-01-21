package org.fxt.freexmltoolkit.domain.statistics;

/**
 * Represents a contextual tip for undiscovered features.
 * Used by the SkillTracker to suggest new features to users.
 */
public class FeatureTip {

    private String featureId;
    private String tipMessage;
    private String actionLink;  // Page ID for navigation (e.g., "xsd", "schematron")
    private int priority;       // Higher priority tips shown first
    private String iconLiteral; // Bootstrap icon for the tip

    /**
     * Default constructor for serialization.
     */
    public FeatureTip() {
    }

    /**
     * Creates a new FeatureTip without an icon.
     *
     * @param featureId the ID of the feature
     * @param tipMessage the tip message to display
     * @param actionLink the page ID for navigation
     * @param priority the priority of the tip (higher values shown first)
     */
    public FeatureTip(String featureId, String tipMessage, String actionLink, int priority) {
        this.featureId = featureId;
        this.tipMessage = tipMessage;
        this.actionLink = actionLink;
        this.priority = priority;
    }

    /**
     * Creates a new FeatureTip with an icon.
     *
     * @param featureId the ID of the feature
     * @param tipMessage the tip message to display
     * @param actionLink the page ID for navigation
     * @param priority the priority of the tip (higher values shown first)
     * @param iconLiteral the Bootstrap icon literal for the tip
     */
    public FeatureTip(String featureId, String tipMessage, String actionLink, int priority, String iconLiteral) {
        this.featureId = featureId;
        this.tipMessage = tipMessage;
        this.actionLink = actionLink;
        this.priority = priority;
        this.iconLiteral = iconLiteral;
    }

    // Getters and Setters

    /**
     * Returns the feature ID.
     *
     * @return the feature ID
     */
    public String getFeatureId() {
        return featureId;
    }

    /**
     * Sets the feature ID.
     *
     * @param featureId the feature ID
     */
    public void setFeatureId(String featureId) {
        this.featureId = featureId;
    }

    /**
     * Returns the tip message.
     *
     * @return the tip message
     */
    public String getTipMessage() {
        return tipMessage;
    }

    /**
     * Sets the tip message.
     *
     * @param tipMessage the tip message
     */
    public void setTipMessage(String tipMessage) {
        this.tipMessage = tipMessage;
    }

    /**
     * Returns the action link (page ID for navigation).
     *
     * @return the action link
     */
    public String getActionLink() {
        return actionLink;
    }

    /**
     * Sets the action link.
     *
     * @param actionLink the action link
     */
    public void setActionLink(String actionLink) {
        this.actionLink = actionLink;
    }

    /**
     * Returns the priority.
     *
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Sets the priority.
     *
     * @param priority the priority
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Returns the Bootstrap icon literal.
     *
     * @return the icon literal
     */
    public String getIconLiteral() {
        return iconLiteral;
    }

    /**
     * Sets the Bootstrap icon literal.
     *
     * @param iconLiteral the icon literal
     */
    public void setIconLiteral(String iconLiteral) {
        this.iconLiteral = iconLiteral;
    }
}
