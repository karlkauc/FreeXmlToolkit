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

import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Startup policy for the FundsXML extension: decides what (if anything) should
 * happen when the application boots.
 *
 * <p>Pure decision logic — no network calls of its own (the update check is
 * delegated to {@link FundsXmlUpdateChecker}), no threads, no UI. The caller
 * (ShellBootstrap) maps the returned {@link Action} onto background work.
 *
 * <p>Policy:
 * <ul>
 *   <li>Feature disabled → {@link Action#NONE}.</li>
 *   <li>Cache empty → {@link Action#DOWNLOAD_INITIAL}. This deliberately bypasses
 *       the 24-hour update-check throttle: a failed offline attempt yesterday must
 *       not block today's first install.</li>
 *   <li>Cache populated → run the throttled update check; a newer release yields
 *       {@link Action#DOWNLOAD_UPDATE}, otherwise {@link Action#REGISTER_ONLY}
 *       (re-seed the in-memory snippet repository and re-register favorites from
 *       the on-disk cache — both are idempotent).</li>
 * </ul>
 */
public class FundsXmlStartupSync {

    private static final Logger logger = LogManager.getLogger(FundsXmlStartupSync.class);

    /** What the caller should do after the policy evaluation. */
    public enum Action {
        /** Feature disabled — do nothing. */
        NONE,
        /** Cache is up to date — only re-register cached content (offline, idempotent). */
        REGISTER_ONLY,
        /** Nothing installed yet — download everything now. */
        DOWNLOAD_INITIAL,
        /** A newer release is available — download it in the background. */
        DOWNLOAD_UPDATE
    }

    private final Supplier<Properties> propertiesLoader;
    private final FundsXmlExtensionService extensionService;
    private final FundsXmlUpdateChecker updateChecker;

    public FundsXmlStartupSync(Supplier<Properties> propertiesLoader,
                               FundsXmlExtensionService extensionService,
                               FundsXmlUpdateChecker updateChecker) {
        this.propertiesLoader = Objects.requireNonNull(propertiesLoader);
        this.extensionService = Objects.requireNonNull(extensionService);
        this.updateChecker = Objects.requireNonNull(updateChecker);
    }

    /**
     * Evaluates the startup policy. Never throws: any failure downgrades to the
     * most conservative applicable action.
     */
    public Action determineAction() {
        Properties props = propertiesLoader.get();
        if (!Boolean.parseBoolean(props.getProperty(FundsXmlPropertyKeys.ENABLED, "false"))) {
            return Action.NONE;
        }
        if (extensionService.isCacheEmpty()) {
            logger.info("FundsXML enabled but no content cached yet — scheduling initial download");
            return Action.DOWNLOAD_INITIAL;
        }
        try {
            if (updateChecker.runIfDue().isPresent()) {
                logger.info("Newer FundsXML release available — scheduling background update");
                return Action.DOWNLOAD_UPDATE;
            }
        } catch (Exception e) {
            logger.warn("FundsXML startup update check failed: {}", e.getMessage());
        }
        return Action.REGISTER_ONLY;
    }
}
