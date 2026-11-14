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

/**
 * The three reports of the FMA pension fund reporting (PK-Meldewesen), all described by one
 * taxonomy: the module code is part of the root element and of the file name
 * ({@code pk.modul.stichtag.lfd-nr[.zusatz].xml}).
 */
public enum PkModule {

    /** Quarterly report (PK-QMV, "Pension Funds Quarterly"). */
    PFQ("pfq", "Nationale_Meldung_pfq", "PK-QMV", "Quartalsmeldung"),
    /** Annual report (PK-FJMV, "Pension Funds Annual"). */
    PFA("pfa", "Nationale_Meldung_pfa", "PK-FJMV", "Jahresmeldung"),
    /** ECB report (Regulation (EU) 2018/231, "Pension Funds ECB"). */
    PFE("pfe", "EZB_Meldung_pfe", "PK-EZB", "EZB-Meldung");

    private final String code;
    private final String rootElement;
    private final String shortName;
    private final String title;

    PkModule(String code, String rootElement, String shortName, String title) {
        this.code = code;
        this.rootElement = rootElement;
        this.shortName = shortName;
        this.title = title;
    }

    /** @return the module code of the file name ({@code pfq}, {@code pfa}, {@code pfe}) */
    public String code() {
        return code;
    }

    /** @return the root element of its reports */
    public String rootElement() {
        return rootElement;
    }

    /** @return the short name shown in the badge ({@code PK-QMV}) */
    public String shortName() {
        return shortName;
    }

    /** @return the report's German title ({@code Quartalsmeldung}) */
    public String title() {
        return title;
    }

    /**
     * @param localName a root element's local name
     * @return the module of that root element, or {@code null}
     */
    public static PkModule ofRootElement(String localName) {
        for (PkModule module : values()) {
            if (module.rootElement.equals(localName)) {
                return module;
            }
        }
        return null;
    }

    /**
     * @param code a module code (case-insensitive)
     * @return the module, or {@code null}
     */
    public static PkModule ofCode(String code) {
        for (PkModule module : values()) {
            if (module.code.equalsIgnoreCase(code)) {
                return module;
            }
        }
        return null;
    }
}
