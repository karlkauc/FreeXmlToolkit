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

package org.fxt.freexmltoolkit.plugins.pkmeldewesen;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * The settings of the PK-Meldewesen extension. Like FundsXML, the plugin is always loaded and
 * its own switch ({@link #ENABLED}) decides whether it does anything, so it can offer itself
 * when a report is opened while it is off.
 */
public final class PkSettings {

    /** Master switch (default off). */
    public static final String ENABLED = "pk.enabled";
    /** Set once the "this looks like a PK report" hint was shown. */
    public static final String HINT_SHOWN = "pk.hint.shown";
    /** The taxonomy version in use (a folder name of {@link PkTaxonomy}); empty = the newest. */
    public static final String TAXONOMY_VERSION = "pk.taxonomy.version";

    private PkSettings() {
    }

    /** @return whether the extension is switched on */
    public static boolean isEnabled() {
        return Boolean.parseBoolean(get(ENABLED, "false"));
    }

    /** Switches the extension on or off. */
    public static void setEnabled(boolean enabled) {
        set(ENABLED, Boolean.toString(enabled));
    }

    /** @return a setting, or the fallback when it is unset or no settings are available (tests, CLI) */
    static String get(String key, String fallback) {
        try {
            String value = ServiceRegistry.get(PropertiesService.class).loadProperties().getProperty(key);
            return value == null || value.isBlank() ? fallback : value;
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** Stores a setting (ignored when no settings are available). */
    static void set(String key, String value) {
        try {
            ServiceRegistry.get(PropertiesService.class).set(key, value);
        } catch (Throwable t) {
            // no settings service (CLI, tests)
        }
    }
}
