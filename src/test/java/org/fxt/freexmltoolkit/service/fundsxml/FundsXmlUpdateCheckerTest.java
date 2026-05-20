/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.service.fundsxml;

import org.fxt.freexmltoolkit.domain.FundsXmlMetadata;
import org.fxt.freexmltoolkit.domain.GitHubRelease;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FundsXmlUpdateChecker Tests")
class FundsXmlUpdateCheckerTest {

    @TempDir
    Path tempDir;

    private FundsXmlCache cache;
    private Properties props;
    private StubExtensionService extensionService;
    private final Instant fixedNow = Instant.parse("2026-05-20T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);

    @BeforeEach
    void setUp() throws Exception {
        Constructor<FundsXmlCache> ctor = FundsXmlCache.class.getDeclaredConstructor(Path.class);
        ctor.setAccessible(true);
        cache = ctor.newInstance(tempDir.resolve("fundsxml"));

        props = new Properties();
        // Defaults that pass the gating checks; individual tests override as needed
        props.setProperty(FundsXmlPropertyKeys.ENABLED, "true");
        props.setProperty(FundsXmlPropertyKeys.UPDATE_CHECK_ENABLED, "true");

        extensionService = new StubExtensionService(cache);
    }

    private FundsXmlUpdateChecker checker() {
        return new FundsXmlUpdateChecker(() -> props, extensionService, cache, fixedClock, Duration.ofHours(24));
    }

    @Test
    @DisplayName("Skipped when feature disabled")
    void skipsWhenFeatureDisabled() {
        props.setProperty(FundsXmlPropertyKeys.ENABLED, "false");
        extensionService.nextRelease = release("4.2.10");

        assertTrue(checker().runIfDue().isEmpty());
        assertEquals(0, extensionService.calls);
    }

    @Test
    @DisplayName("Skipped when update check disabled")
    void skipsWhenUpdateCheckDisabled() {
        props.setProperty(FundsXmlPropertyKeys.UPDATE_CHECK_ENABLED, "false");
        extensionService.nextRelease = release("4.2.10");

        assertTrue(checker().runIfDue().isEmpty());
        assertEquals(0, extensionService.calls);
    }

    @Test
    @DisplayName("Skipped when last check is within the throttle window")
    void skipsWhenRecentlyChecked() {
        // Last check 1h ago — well within the 24h throttle window
        writeLastCheck(fixedNow.minus(Duration.ofHours(1)));
        extensionService.nextRelease = release("4.2.10");

        assertTrue(checker().runIfDue().isEmpty());
        assertEquals(0, extensionService.calls);
    }

    @Test
    @DisplayName("Runs and surfaces a release when throttle has expired")
    void runsAfterThrottleExpires() {
        writeLastCheck(fixedNow.minus(Duration.ofHours(25)));
        extensionService.nextRelease = release("4.2.10");

        Optional<GitHubRelease> result = checker().runIfDue();

        assertTrue(result.isPresent());
        assertEquals("4.2.10", result.get().tagName());
        assertEquals(1, extensionService.calls);
    }

    @Test
    @DisplayName("Runs when no last-check timestamp has been recorded yet")
    void runsOnFirstUse() {
        // No metadata file; cache returns a fresh empty metadata object
        extensionService.nextRelease = release("4.2.10");

        Optional<GitHubRelease> result = checker().runIfDue();

        assertTrue(result.isPresent());
        assertEquals(1, extensionService.calls);
    }

    @Test
    @DisplayName("Returns empty when no newer release is available")
    void noNewRelease() {
        writeLastCheck(fixedNow.minus(Duration.ofDays(7)));
        extensionService.nextRelease = null;

        assertTrue(checker().runIfDue().isEmpty());
        assertEquals(1, extensionService.calls);
    }

    @Test
    @DisplayName("Liberal: corrupt last-check timestamp still allows the check to run")
    void corruptTimestampFallsBackToRunning() {
        FundsXmlMetadata meta = cache.loadMetadata();
        meta.setLastUpdateCheck("not-an-instant");
        cache.saveMetadata(meta);
        extensionService.nextRelease = release("4.2.10");

        assertTrue(checker().runIfDue().isPresent());
    }

    @Test
    @DisplayName("Exceptions from the extension service degrade gracefully")
    void extensionServiceCrashTreatedAsNoUpdate() {
        writeLastCheck(fixedNow.minus(Duration.ofDays(2)));
        extensionService.throwOnNextCall = true;

        assertTrue(checker().runIfDue().isEmpty());
    }

    private void writeLastCheck(Instant when) {
        FundsXmlMetadata meta = cache.loadMetadata();
        meta.setLastUpdateCheck(when.toString());
        cache.saveMetadata(meta);
    }

    private static GitHubRelease release(String tag) {
        return new GitHubRelease(tag, "FundsXML " + tag, null, null, null, null, "2026-01-23T10:30:00Z");
    }

    // -----------------------------------------------------------------
    // Test doubles
    // -----------------------------------------------------------------

    private static class StubExtensionService extends FundsXmlExtensionService {
        GitHubRelease nextRelease;
        boolean throwOnNextCall;
        int calls;

        StubExtensionService(FundsXmlCache cache) {
            super(cache, new GitHubReleaseClient(uri -> {
                throw new java.io.IOException("Network disabled in tests");
            }));
        }

        @Override
        public GitHubRelease checkForUpdates() {
            calls++;
            if (throwOnNextCall) {
                throwOnNextCall = false;
                throw new RuntimeException("simulated failure");
            }
            return nextRelease;
        }
    }

}
