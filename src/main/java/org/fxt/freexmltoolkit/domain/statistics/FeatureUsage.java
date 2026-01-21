package org.fxt.freexmltoolkit.domain.statistics;

import java.time.LocalDateTime;

/**
 * Tracks usage of individual features for skill discovery.
 * Used to show which features have been discovered and how often they're used.
 */
public class FeatureUsage {

    private String featureId;
    private String featureName;
    private String category;
    private int useCount;
    private LocalDateTime firstUsed;
    private LocalDateTime lastUsed;
    private boolean discovered;

    /**
     * Default constructor for serialization.
     */
    public FeatureUsage() {
        this.useCount = 0;
        this.discovered = false;
    }

    /**
     * Creates a new FeatureUsage.
     *
     * @param featureId the ID of the feature
     * @param featureName the name of the feature
     * @param category the category of the feature
     */
    public FeatureUsage(String featureId, String featureName, String category) {
        this.featureId = featureId;
        this.featureName = featureName;
        this.category = category;
        this.useCount = 0;
        this.discovered = false;
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
     * Returns the feature name.
     *
     * @return the feature name
     */
    public String getFeatureName() {
        return featureName;
    }

    /**
     * Sets the feature name.
     *
     * @param featureName the feature name
     */
    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }

    /**
     * Returns the category.
     *
     * @return the category
     */
    public String getCategory() {
        return category;
    }

    /**
     * Sets the category.
     *
     * @param category the category
     */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * Returns the use count.
     *
     * @return the use count
     */
    public int getUseCount() {
        return useCount;
    }

    /**
     * Sets the use count.
     *
     * @param useCount the use count
     */
    public void setUseCount(int useCount) {
        this.useCount = useCount;
    }

    /**
     * Increments the use count by one.
     */
    public void incrementUseCount() {
        this.useCount++;
    }

    /**
     * Returns the first used timestamp.
     *
     * @return the first used timestamp
     */
    public LocalDateTime getFirstUsed() {
        return firstUsed;
    }

    /**
     * Sets the first used timestamp.
     *
     * @param firstUsed the first used timestamp
     */
    public void setFirstUsed(LocalDateTime firstUsed) {
        this.firstUsed = firstUsed;
    }

    /**
     * Returns the last used timestamp.
     *
     * @return the last used timestamp
     */
    public LocalDateTime getLastUsed() {
        return lastUsed;
    }

    /**
     * Sets the last used timestamp.
     *
     * @param lastUsed the last used timestamp
     */
    public void setLastUsed(LocalDateTime lastUsed) {
        this.lastUsed = lastUsed;
    }

    /**
     * Checks if the feature has been discovered.
     *
     * @return true if the feature has been discovered
     */
    public boolean isDiscovered() {
        return discovered;
    }

    /**
     * Sets whether the feature has been discovered.
     *
     * @param discovered true if discovered
     */
    public void setDiscovered(boolean discovered) {
        this.discovered = discovered;
    }

    /**
     * Get usage level based on use count.
     *
     * @return the usage level
     */
    public UsageLevel getUsageLevel() {
        if (useCount == 0) return UsageLevel.NEVER;
        if (useCount < 5) return UsageLevel.RARELY;
        if (useCount < 20) return UsageLevel.OCCASIONALLY;
        if (useCount < 50) return UsageLevel.FREQUENTLY;
        return UsageLevel.EXPERT;
    }

    /**
     * Enumeration of usage levels.
     */
    public enum UsageLevel {
        /** Feature has never been used */
        NEVER("Never used"),
        /** Feature has been rarely used */
        RARELY("Rarely used"),
        /** Feature has been occasionally used */
        OCCASIONALLY("Occasionally used"),
        /** Feature has been frequently used */
        FREQUENTLY("Frequently used"),
        /** Expert level usage */
        EXPERT("Expert level");

        private final String description;

        /**
         * Creates a UsageLevel.
         *
         * @param description the description
         */
        UsageLevel(String description) {
            this.description = description;
        }

        /**
         * Returns the description.
         *
         * @return the description
         */
        public String getDescription() {
            return description;
        }
    }
}
