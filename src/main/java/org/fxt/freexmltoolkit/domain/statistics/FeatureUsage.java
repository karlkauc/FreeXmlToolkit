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

    public FeatureUsage() {
        this.useCount = 0;
        this.discovered = false;
    }

    public FeatureUsage(String featureId, String featureName, String category) {
        this.featureId = featureId;
        this.featureName = featureName;
        this.category = category;
        this.useCount = 0;
        this.discovered = false;
    }

    // Getters and Setters

    public String getFeatureId() {
        return featureId;
    }

    public void setFeatureId(String featureId) {
        this.featureId = featureId;
    }

    public String getFeatureName() {
        return featureName;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getUseCount() {
        return useCount;
    }

    public void setUseCount(int useCount) {
        this.useCount = useCount;
    }

    public void incrementUseCount() {
        this.useCount++;
    }

    public LocalDateTime getFirstUsed() {
        return firstUsed;
    }

    public void setFirstUsed(LocalDateTime firstUsed) {
        this.firstUsed = firstUsed;
    }

    public LocalDateTime getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(LocalDateTime lastUsed) {
        this.lastUsed = lastUsed;
    }

    public boolean isDiscovered() {
        return discovered;
    }

    public void setDiscovered(boolean discovered) {
        this.discovered = discovered;
    }

    /**
     * Get usage level based on use count
     */
    public UsageLevel getUsageLevel() {
        if (useCount == 0) return UsageLevel.NEVER;
        if (useCount < 5) return UsageLevel.RARELY;
        if (useCount < 20) return UsageLevel.OCCASIONALLY;
        if (useCount < 50) return UsageLevel.FREQUENTLY;
        return UsageLevel.EXPERT;
    }

    public enum UsageLevel {
        NEVER("Never used"),
        RARELY("Rarely used"),
        OCCASIONALLY("Occasionally used"),
        FREQUENTLY("Frequently used"),
        EXPERT("Expert level");

        private final String description;

        UsageLevel(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
