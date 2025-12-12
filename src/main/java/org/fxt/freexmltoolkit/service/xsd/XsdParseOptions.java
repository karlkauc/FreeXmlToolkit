package org.fxt.freexmltoolkit.service.xsd;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Configuration options for XSD parsing.
 * Use the builder pattern to create instances.
 */
public final class XsdParseOptions {

    /**
     * Mode for handling xs:include directives.
     */
    public enum IncludeMode {
        /**
         * Inline all included content into a single schema.
         * The resulting ParsedSchema will have no include references,
         * all content is merged into the main schema.
         */
        FLATTEN,

        /**
         * Preserve the include structure.
         * Include directives are kept, and each included file is tracked
         * separately for multi-file serialization.
         */
        PRESERVE_STRUCTURE
    }

    /**
     * Listener for progress updates during parsing.
     */
    @FunctionalInterface
    public interface ProgressListener {
        /**
         * Called when progress is made during parsing.
         *
         * @param message description of current operation
         * @param current current item being processed (0-based)
         * @param total   total items to process (-1 if unknown)
         */
        void onProgress(String message, int current, int total);
    }

    // Default values
    public static final IncludeMode DEFAULT_INCLUDE_MODE = IncludeMode.PRESERVE_STRUCTURE;
    public static final boolean DEFAULT_RESOLVE_IMPORTS = true;
    public static final boolean DEFAULT_CACHE_ENABLED = true;
    public static final Duration DEFAULT_CACHE_EXPIRY = Duration.ofHours(24);
    public static final int DEFAULT_MAX_INCLUDE_DEPTH = 50;
    public static final Duration DEFAULT_NETWORK_TIMEOUT = Duration.ofSeconds(30);

    private final IncludeMode includeMode;
    private final boolean resolveImports;
    private final boolean cacheEnabled;
    private final Duration cacheExpiry;
    private final int maxIncludeDepth;
    private final Duration networkTimeout;
    private final ProgressListener progressListener;
    private final Consumer<String> warningHandler;

    private XsdParseOptions(Builder builder) {
        this.includeMode = builder.includeMode;
        this.resolveImports = builder.resolveImports;
        this.cacheEnabled = builder.cacheEnabled;
        this.cacheExpiry = builder.cacheExpiry;
        this.maxIncludeDepth = builder.maxIncludeDepth;
        this.networkTimeout = builder.networkTimeout;
        this.progressListener = builder.progressListener;
        this.warningHandler = builder.warningHandler;
    }

    /**
     * Creates a new builder with default options.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the default options.
     *
     * @return default options instance
     */
    public static XsdParseOptions defaults() {
        return new Builder().build();
    }

    /**
     * Returns options configured for flattening schemas.
     *
     * @return options with FLATTEN include mode
     */
    public static XsdParseOptions forFlattening() {
        return builder().includeMode(IncludeMode.FLATTEN).build();
    }

    /**
     * Returns options configured for preserving structure.
     *
     * @return options with PRESERVE_STRUCTURE include mode
     */
    public static XsdParseOptions forPreservingStructure() {
        return builder().includeMode(IncludeMode.PRESERVE_STRUCTURE).build();
    }

    /**
     * @return the mode for handling xs:include directives
     */
    public IncludeMode getIncludeMode() {
        return includeMode;
    }

    /**
     * @return true if xs:import directives should be resolved
     */
    public boolean isResolveImports() {
        return resolveImports;
    }

    /**
     * @return true if caching is enabled
     */
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    /**
     * @return the cache expiry duration
     */
    public Duration getCacheExpiry() {
        return cacheExpiry;
    }

    /**
     * @return the maximum depth for nested includes
     */
    public int getMaxIncludeDepth() {
        return maxIncludeDepth;
    }

    /**
     * @return the timeout for network operations
     */
    public Duration getNetworkTimeout() {
        return networkTimeout;
    }

    /**
     * @return the progress listener, or null if none configured
     */
    public ProgressListener getProgressListener() {
        return progressListener;
    }

    /**
     * @return the warning handler, or null if none configured
     */
    public Consumer<String> getWarningHandler() {
        return warningHandler;
    }

    /**
     * Reports progress if a listener is configured.
     *
     * @param message description of current operation
     * @param current current item (0-based)
     * @param total   total items (-1 if unknown)
     */
    public void reportProgress(String message, int current, int total) {
        if (progressListener != null) {
            progressListener.onProgress(message, current, total);
        }
    }

    /**
     * Reports a warning if a handler is configured.
     *
     * @param warning the warning message
     */
    public void reportWarning(String warning) {
        if (warningHandler != null) {
            warningHandler.accept(warning);
        }
    }

    /**
     * Creates a copy of these options with different include mode.
     *
     * @param mode the new include mode
     * @return new options with the specified mode
     */
    public XsdParseOptions withIncludeMode(IncludeMode mode) {
        return builder()
                .includeMode(mode)
                .resolveImports(this.resolveImports)
                .cacheEnabled(this.cacheEnabled)
                .cacheExpiry(this.cacheExpiry)
                .maxIncludeDepth(this.maxIncludeDepth)
                .networkTimeout(this.networkTimeout)
                .progressListener(this.progressListener)
                .warningHandler(this.warningHandler)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XsdParseOptions that = (XsdParseOptions) o;
        return resolveImports == that.resolveImports &&
                cacheEnabled == that.cacheEnabled &&
                maxIncludeDepth == that.maxIncludeDepth &&
                includeMode == that.includeMode &&
                Objects.equals(cacheExpiry, that.cacheExpiry) &&
                Objects.equals(networkTimeout, that.networkTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(includeMode, resolveImports, cacheEnabled,
                cacheExpiry, maxIncludeDepth, networkTimeout);
    }

    @Override
    public String toString() {
        return "XsdParseOptions{" +
                "includeMode=" + includeMode +
                ", resolveImports=" + resolveImports +
                ", cacheEnabled=" + cacheEnabled +
                ", cacheExpiry=" + cacheExpiry +
                ", maxIncludeDepth=" + maxIncludeDepth +
                ", networkTimeout=" + networkTimeout +
                '}';
    }

    /**
     * Builder for XsdParseOptions.
     */
    public static final class Builder {
        private IncludeMode includeMode = DEFAULT_INCLUDE_MODE;
        private boolean resolveImports = DEFAULT_RESOLVE_IMPORTS;
        private boolean cacheEnabled = DEFAULT_CACHE_ENABLED;
        private Duration cacheExpiry = DEFAULT_CACHE_EXPIRY;
        private int maxIncludeDepth = DEFAULT_MAX_INCLUDE_DEPTH;
        private Duration networkTimeout = DEFAULT_NETWORK_TIMEOUT;
        private ProgressListener progressListener;
        private Consumer<String> warningHandler;

        private Builder() {
        }

        /**
         * Sets the include mode.
         *
         * @param includeMode how to handle xs:include directives
         * @return this builder
         */
        public Builder includeMode(IncludeMode includeMode) {
            this.includeMode = Objects.requireNonNull(includeMode, "includeMode must not be null");
            return this;
        }

        /**
         * Sets whether to resolve xs:import directives.
         *
         * @param resolveImports true to resolve imports
         * @return this builder
         */
        public Builder resolveImports(boolean resolveImports) {
            this.resolveImports = resolveImports;
            return this;
        }

        /**
         * Sets whether caching is enabled.
         *
         * @param cacheEnabled true to enable caching
         * @return this builder
         */
        public Builder cacheEnabled(boolean cacheEnabled) {
            this.cacheEnabled = cacheEnabled;
            return this;
        }

        /**
         * Sets the cache expiry duration.
         *
         * @param cacheExpiry how long cached schemas remain valid
         * @return this builder
         */
        public Builder cacheExpiry(Duration cacheExpiry) {
            this.cacheExpiry = Objects.requireNonNull(cacheExpiry, "cacheExpiry must not be null");
            return this;
        }

        /**
         * Sets the maximum include depth.
         *
         * @param maxIncludeDepth maximum nesting level for includes
         * @return this builder
         * @throws IllegalArgumentException if depth is less than 1
         */
        public Builder maxIncludeDepth(int maxIncludeDepth) {
            if (maxIncludeDepth < 1) {
                throw new IllegalArgumentException("maxIncludeDepth must be at least 1");
            }
            this.maxIncludeDepth = maxIncludeDepth;
            return this;
        }

        /**
         * Sets the network timeout for remote schema fetching.
         *
         * @param networkTimeout timeout duration
         * @return this builder
         */
        public Builder networkTimeout(Duration networkTimeout) {
            this.networkTimeout = Objects.requireNonNull(networkTimeout, "networkTimeout must not be null");
            return this;
        }

        /**
         * Sets the progress listener.
         *
         * @param progressListener listener to receive progress updates
         * @return this builder
         */
        public Builder progressListener(ProgressListener progressListener) {
            this.progressListener = progressListener;
            return this;
        }

        /**
         * Sets the warning handler.
         *
         * @param warningHandler consumer to receive warning messages
         * @return this builder
         */
        public Builder warningHandler(Consumer<String> warningHandler) {
            this.warningHandler = warningHandler;
            return this;
        }

        /**
         * Builds the XsdParseOptions instance.
         *
         * @return the configured options
         */
        public XsdParseOptions build() {
            return new XsdParseOptions(this);
        }
    }
}
