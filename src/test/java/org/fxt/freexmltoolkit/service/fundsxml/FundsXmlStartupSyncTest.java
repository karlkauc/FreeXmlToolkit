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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Properties;

import org.fxt.freexmltoolkit.domain.FundsXmlMetadata;
import org.fxt.freexmltoolkit.domain.GitHubRelease;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("FundsXmlStartupSync Tests")
class FundsXmlStartupSyncTest {

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
        props.setProperty(FundsXmlPropertyKeys.ENABLED, "true");
        props.setProperty(FundsXmlPropertyKeys.UPDATE_CHECK_ENABLED, "true");

        extensionService = new StubExtensionService(cache);
    }

    private FundsXmlStartupSync sync() {
        FundsXmlUpdateChecker checker = new FundsXmlUpdateChecker(
                () -> props, extensionService, cache, fixedClock, Duration.ofHours(24));
        return new FundsXmlStartupSync(() -> props, extensionService, checker);
    }

    @Test
    @DisplayName("Feature disabled → NONE")
    void disabledMeansNone() {
        props.setProperty(FundsXmlPropertyKeys.ENABLED, "false");
        assertEquals(FundsXmlStartupSync.Action.NONE, sync().determineAction());
    }

    @Test
    @DisplayName("Enabled + empty cache → DOWNLOAD_INITIAL")
    void emptyCacheMeansInitialDownload() {
        assertEquals(FundsXmlStartupSync.Action.DOWNLOAD_INITIAL, sync().determineAction());
    }

    @Test
    @DisplayName("Empty cache bypasses the 24h throttle (stale failed check must not block install)")
    void emptyCacheBypassesThrottle() {
        writeLastCheck(fixedNow.minus(Duration.ofMinutes(5)));
        assertEquals(FundsXmlStartupSync.Action.DOWNLOAD_INITIAL, sync().determineAction());
    }

    @Test
    @DisplayName("Installed + newer release → DOWNLOAD_UPDATE")
    void newerReleaseMeansUpdate() throws Exception {
        Files.createDirectories(cache.getSchemaVersionDir("4.2.9"));
        extensionService.nextRelease = release("4.2.10");

        assertEquals(FundsXmlStartupSync.Action.DOWNLOAD_UPDATE, sync().determineAction());
    }

    @Test
    @DisplayName("Installed + no newer release → REGISTER_ONLY")
    void upToDateMeansRegisterOnly() throws Exception {
        Files.createDirectories(cache.getSchemaVersionDir("4.2.10"));
        extensionService.nextRelease = null;

        assertEquals(FundsXmlStartupSync.Action.REGISTER_ONLY, sync().determineAction());
    }

    @Test
    @DisplayName("Installed + throttled check → REGISTER_ONLY")
    void throttledCheckMeansRegisterOnly() throws Exception {
        Files.createDirectories(cache.getSchemaVersionDir("4.2.10"));
        writeLastCheck(fixedNow.minus(Duration.ofHours(1)));
        extensionService.nextRelease = release("9.9.9");

        assertEquals(FundsXmlStartupSync.Action.REGISTER_ONLY, sync().determineAction());
        assertEquals(0, extensionService.calls, "throttled check must not hit GitHub");
    }

    @Test
    @DisplayName("Installed + check failure (offline) → REGISTER_ONLY")
    void offlineMeansRegisterOnly() throws Exception {
        Files.createDirectories(cache.getSchemaVersionDir("4.2.10"));
        extensionService.throwOnNextCall = true;

        assertEquals(FundsXmlStartupSync.Action.REGISTER_ONLY, sync().determineAction());
    }

    // -----------------------------------------------------------------
    // Helpers / doubles
    // -----------------------------------------------------------------

    private void writeLastCheck(Instant when) {
        FundsXmlMetadata meta = cache.loadMetadata();
        meta.setLastUpdateCheck(when.toString());
        cache.saveMetadata(meta);
    }

    private static GitHubRelease release(String tag) {
        return new GitHubRelease(tag, "FundsXML " + tag, null, null, null, null, "2026-01-23T10:30:00Z");
    }

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
