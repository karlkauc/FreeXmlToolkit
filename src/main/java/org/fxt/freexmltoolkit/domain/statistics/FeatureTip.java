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

    public FeatureTip() {
    }

    public FeatureTip(String featureId, String tipMessage, String actionLink, int priority) {
        this.featureId = featureId;
        this.tipMessage = tipMessage;
        this.actionLink = actionLink;
        this.priority = priority;
    }

    public FeatureTip(String featureId, String tipMessage, String actionLink, int priority, String iconLiteral) {
        this.featureId = featureId;
        this.tipMessage = tipMessage;
        this.actionLink = actionLink;
        this.priority = priority;
        this.iconLiteral = iconLiteral;
    }

    // Getters and Setters

    public String getFeatureId() {
        return featureId;
    }

    public void setFeatureId(String featureId) {
        this.featureId = featureId;
    }

    public String getTipMessage() {
        return tipMessage;
    }

    public void setTipMessage(String tipMessage) {
        this.tipMessage = tipMessage;
    }

    public String getActionLink() {
        return actionLink;
    }

    public void setActionLink(String actionLink) {
        this.actionLink = actionLink;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getIconLiteral() {
        return iconLiteral;
    }

    public void setIconLiteral(String iconLiteral) {
        this.iconLiteral = iconLiteral;
    }
}
