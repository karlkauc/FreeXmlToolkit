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

package org.fxt.freexmltoolkit.plugins.kagmeldewesen;

import java.math.BigDecimal;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/** The rule settings of the KAG-Meldewesen extension (panel section OPTIONS). */
public final class KagSettings {

    /** How far a sum of AIFMD percentages may be from 100 (percentage points, default 0.5). */
    public static final String AIFMD_SUM_TOLERANCE = "kag.aifmd.sumTolerance";
    /** DeRiMV: relative VaR above which a warning is given (%, default 200). */
    public static final String DERIMV_RVAR_LIMIT = "kag.derimv.relativeVarLimit";
    /** DeRiMV: whether a fund close to a limit is reported (default off). */
    public static final String DERIMV_WARN = "kag.derimv.utilisationWarning";
    /** DeRiMV: utilisation of a limit in % from which a warning is given (default 90). */
    public static final String DERIMV_WARN_AT = "kag.derimv.utilisationWarningAt";

    static final BigDecimal DEFAULT_WARN_AT = new BigDecimal(90);

    /**
     * All settings of the extension.
     *
     * @param sumTolerance     AIFMD sums of percentages
     * @param relativeVarLimit DeRiMV relative VaR warning limit
     * @param warn             whether the DeRiMV utilisation warning is on
     * @param warnAt           its threshold (% of a limit)
     */
    public record Options(BigDecimal sumTolerance, BigDecimal relativeVarLimit, boolean warn, BigDecimal warnAt) {

        /** @return the defaults */
        public static Options defaults() {
            return new Options(AifmdRules.ROUNDING, DerimvRules.RELATIVE_VAR_LIMIT, false, DEFAULT_WARN_AT);
        }

        /** @return the options of the DeRiMV rules */
        public DerimvRules.Options derimv() {
            return new DerimvRules.Options(relativeVarLimit, warn ? warnAt : null);
        }
    }

    private KagSettings() {
    }

    /** @return the stored options (defaults for missing or invalid values) */
    public static Options options() {
        Options d = Options.defaults();
        return new Options(decimal(AIFMD_SUM_TOLERANCE, d.sumTolerance()), positive(DERIMV_RVAR_LIMIT, d.relativeVarLimit()),
                Boolean.parseBoolean(get(DERIMV_WARN, "false")), positive(DERIMV_WARN_AT, d.warnAt()));
    }

    /** Stores the options. */
    public static void setOptions(Options options) {
        set(AIFMD_SUM_TOLERANCE, options.sumTolerance().toPlainString());
        set(DERIMV_RVAR_LIMIT, options.relativeVarLimit().toPlainString());
        set(DERIMV_WARN, Boolean.toString(options.warn()));
        set(DERIMV_WARN_AT, options.warnAt().toPlainString());
    }

    /** @return a non-negative decimal, or {@code null} when the text is none */
    static BigDecimal parse(String text) {
        if (text == null) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text.strip().replace(',', '.'));
            return value.signum() < 0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimal(String key, BigDecimal fallback) {
        BigDecimal value = parse(get(key, fallback.toPlainString()));
        return value == null ? fallback : value;
    }

    private static BigDecimal positive(String key, BigDecimal fallback) {
        BigDecimal value = decimal(key, fallback);
        return value.signum() == 0 ? fallback : value;
    }

    private static String get(String key, String fallback) {
        try {
            String value = ServiceRegistry.get(PropertiesService.class).loadProperties().getProperty(key);
            return value == null || value.isBlank() ? fallback : value;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static void set(String key, String value) {
        try {
            ServiceRegistry.get(PropertiesService.class).set(key, value);
        } catch (Throwable t) {
            // no settings service (CLI, tests)
        }
    }
}
