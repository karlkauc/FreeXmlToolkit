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
    /** Whether the additional plausibility checks (INT_*, not FMA rules) run (default on). */
    public static final String RULES_ADDITIONAL = "pk.rules.additional";
    /** Amount differences above this value are errors (EUR, default 3). */
    public static final String RULES_TOLERANCE = "pk.rules.tolerance";
    /** Amount differences above this value are warnings (EUR, default 0.99). */
    public static final String RULES_TOLERANCE_WARNING = "pk.rules.toleranceWarning";
    /** Not looked-through share of one fund in % of the VRG above which a warning is given (default 2). */
    public static final String RULES_LOOK_THROUGH_ASSET = "pk.rules.lookThroughAsset";
    /** Not looked-through share of the VRG in % above which a warning is given (default 10). */
    public static final String RULES_LOOK_THROUGH_TOTAL = "pk.rules.lookThroughTotal";

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

    /** @return the parameters of the rule check from the settings (defaults for missing or invalid values) */
    public static PkRuleCheck.Options ruleOptions() {
        PkRuleCheck.Options d = PkRuleCheck.Options.defaults();
        return new PkRuleCheck.Options(decimal(RULES_TOLERANCE, d.tolerance()),
                decimal(RULES_TOLERANCE_WARNING, d.toleranceWarning()),
                decimal(RULES_LOOK_THROUGH_ASSET, d.lookThroughAsset()),
                decimal(RULES_LOOK_THROUGH_TOTAL, d.lookThroughTotal()),
                d.maxLookThroughRows(), Boolean.parseBoolean(get(RULES_ADDITIONAL, "true")));
    }

    /** Stores the parameters of the rule check. */
    public static void setRuleOptions(PkRuleCheck.Options options) {
        set(RULES_ADDITIONAL, Boolean.toString(options.additional()));
        set(RULES_TOLERANCE, options.tolerance().toPlainString());
        set(RULES_TOLERANCE_WARNING, options.toleranceWarning().toPlainString());
        set(RULES_LOOK_THROUGH_ASSET, options.lookThroughAsset().toPlainString());
        set(RULES_LOOK_THROUGH_TOTAL, options.lookThroughTotal().toPlainString());
    }

    /** @return a non-negative decimal setting, or the fallback */
    static java.math.BigDecimal decimal(String key, java.math.BigDecimal fallback) {
        try {
            java.math.BigDecimal value = new java.math.BigDecimal(get(key, fallback.toPlainString()).strip().replace(',', '.'));
            return value.signum() < 0 ? fallback : value;
        } catch (NumberFormatException e) {
            return fallback;
        }
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
