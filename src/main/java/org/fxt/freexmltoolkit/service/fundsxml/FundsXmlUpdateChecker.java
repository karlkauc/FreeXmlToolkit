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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.FundsXmlMetadata;
import org.fxt.freexmltoolkit.domain.GitHubRelease;
import org.fxt.freexmltoolkit.service.PropertiesService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Encapsulates the startup-time "is there a newer FundsXML release on GitHub?" logic.
 *
 * <p>Separates the gating policy (feature enabled, check enabled, throttle window) from
 * the actual GitHub call (delegated to {@link FundsXmlExtensionService#checkForUpdates()})
 * and the UI notification (left to the caller). Makes the policy unit-testable without
 * touching the network or JavaFX.
 *
 * <p>The throttle window is 24 hours by default. The last-check timestamp lives in the
 * cache's {@code metadata.json}, written by {@code checkForUpdates()}.
 */
public class FundsXmlUpdateChecker {

    private static final Logger logger = LogManager.getLogger(FundsXmlUpdateChecker.class);

    static final Duration DEFAULT_THROTTLE = Duration.ofHours(24);

    private final Supplier<Properties> propertiesLoader;
    private final FundsXmlExtensionService extensionService;
    private final FundsXmlCache cache;
    private final Clock clock;
    private final Duration throttle;

    public FundsXmlUpdateChecker(PropertiesService propertiesService,
                                 FundsXmlExtensionService extensionService,
                                 FundsXmlCache cache) {
        this(propertiesService::loadProperties, extensionService, cache, Clock.systemUTC(), DEFAULT_THROTTLE);
    }

    /** Test-friendly constructor with injectable properties loader, clock, and throttle. */
    FundsXmlUpdateChecker(Supplier<Properties> propertiesLoader,
                          FundsXmlExtensionService extensionService,
                          FundsXmlCache cache,
                          Clock clock,
                          Duration throttle) {
        this.propertiesLoader = propertiesLoader;
        this.extensionService = extensionService;
        this.cache = cache;
        this.clock = clock;
        this.throttle = throttle;
    }

    /**
     * Performs the gated check. Returns a non-empty {@link Optional} when:
     * <ol>
     *   <li>The FundsXML feature is enabled.</li>
     *   <li>The periodic check is enabled.</li>
     *   <li>The last-check timestamp is older than {@link #throttle} (or never set).</li>
     *   <li>GitHub reports a release that is newer than anything installed locally.</li>
     * </ol>
     */
    public Optional<GitHubRelease> runIfDue() {
        Properties props = propertiesLoader.get();
        if (!Boolean.parseBoolean(props.getProperty(FundsXmlPropertyKeys.ENABLED, "false"))) {
            logger.debug("FundsXML update check skipped: feature disabled");
            return Optional.empty();
        }
        if (!Boolean.parseBoolean(props.getProperty(FundsXmlPropertyKeys.UPDATE_CHECK_ENABLED, "true"))) {
            logger.debug("FundsXML update check skipped: periodic check disabled");
            return Optional.empty();
        }
        if (!isThrottleExpired()) {
            logger.debug("FundsXML update check skipped: last check within {} hours",
                    throttle.toHours());
            return Optional.empty();
        }
        try {
            GitHubRelease release = extensionService.checkForUpdates();
            return Optional.ofNullable(release);
        } catch (Exception e) {
            logger.warn("FundsXML update check failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * @return true when no previous check is recorded or the recorded check is older
     *         than the throttle window.
     */
    boolean isThrottleExpired() {
        FundsXmlMetadata meta = cache.loadMetadata();
        String last = meta.getLastUpdateCheck();
        if (last == null || last.isBlank()) {
            return true;
        }
        try {
            Instant lastCheck = Instant.parse(last);
            Instant now = Instant.now(clock);
            return Duration.between(lastCheck, now).compareTo(throttle) >= 0;
        } catch (Exception e) {
            // Corrupt timestamp — be liberal and let the check run.
            logger.debug("Unparseable last-update-check timestamp '{}'; allowing check", last);
            return true;
        }
    }
}
