/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024-2026.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package org.fxt.freexmltoolkit.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link VersionUtil} resolves a real (non-fallback) version from the
 * generated {@code build-info.properties} on the test classpath.
 */
class VersionUtilTest {

    @Test
    @DisplayName("Version is resolved from build-info.properties (not the fallback)")
    void versionIsNotFallback() {
        String version = VersionUtil.getVersion();
        assertNotNull(version);
        assertFalse(version.isBlank(), "version must not be blank");
        assertFalse(version.equals("0.0.0-dev"),
                "version must come from build-info.properties (got fallback)");
        assertTrue(version.matches("\\d+\\.\\d+(\\.\\d+)?(-.*)?"),
                "version should look like semver, got: " + version);
    }

    @Test
    @DisplayName("Build timestamp is populated when build-info.properties is on classpath")
    void buildTimestampIsPopulated() {
        String ts = VersionUtil.getBuildTimestamp();
        assertNotNull(ts);
        assertFalse(ts.isBlank(), "build timestamp should be set by Gradle");
    }

    @Test
    @DisplayName("Build timestamp formatted is human-readable")
    void buildTimestampFormatted() {
        String formatted = VersionUtil.getBuildTimestampFormatted();
        assertNotNull(formatted);
        assertTrue(formatted.contains("UTC") || formatted.isBlank(),
                "formatted timestamp should contain 'UTC' or be empty, got: " + formatted);
    }

    @Test
    @DisplayName("Vendor falls back to default when not in properties")
    void vendorIsSet() {
        assertNotNull(VersionUtil.getVendor());
        assertFalse(VersionUtil.getVendor().isBlank());
    }
}
