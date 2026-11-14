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

package org.fxt.freexmltoolkit.plugins.regreport;

import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.plugin.PluginContext;
import org.fxt.freexmltoolkit.plugin.PluginSchema;

/**
 * ESMA's ISO 20022 report schemas, shared by the reporting extensions: the business data envelope
 * (head.003) is bound to one driver schema that imports the header and every known report
 * (money market fund report, EMIR, SFTR, MiFIR), so a report in its envelope validates whichever
 * extension registered it; each report's own namespace is bound to its schema for documents that
 * start with {@code Document}. Installed once under {@code ~/.freeXmlToolkit/regreport/esma/}.
 */
public final class EsmaSchemas {

    /** Namespace of the business data envelope. */
    public static final String ENVELOPE = "urn:iso:std:iso:20022:tech:xsd:head.003.001.01";
    public static final String MMF = "urn:iso:std:iso:20022:tech:xsd:DRAFT6auth.093.001.01";
    public static final String EMIR = "urn:iso:std:iso:20022:tech:xsd:auth.030.001.03";
    public static final String SFTR = "urn:iso:std:iso:20022:tech:xsd:auth.052.001.02";
    public static final String MIFIR = "urn:iso:std:iso:20022:tech:xsd:auth.016.001.01";

    static final String RESOURCES = "/plugins/regreport/esma";
    static final List<String> FILES = List.of("esma-envelope.xsd", "head.003.001.01.xsd", "head.001.001.01_ESMAUG_1.0.0.xsd",
            "MMF_Regulatory_reporting_MoneyMarketFundReport_V04_DRAFT_auth_093_001_01.xsd",
            "auth.030.001.03_ESMAUG_DATTAR_1.1.0.xsd", "auth.052.001.02_ESMAUG_1.2.0.xsd",
            "auth.016.001.01_ESMAUG_Reporting_1.1.0.xsd");

    private EsmaSchemas() {
    }

    /** @return the folder with the installed schemas */
    public static Path install() {
        return BundledSchemas.install("regreport", "esma", RESOURCES, FILES);
    }

    /** @return the schema file of a report namespace */
    public static String fileOf(String namespace) {
        return switch (namespace) {
            case MMF -> FILES.get(3);
            case EMIR -> FILES.get(4);
            case SFTR -> FILES.get(5);
            case MIFIR -> FILES.get(6);
            default -> FILES.getFirst();
        };
    }

    /**
     * Binds the envelope to the driver schema and the given report namespaces to their schemas.
     *
     * @param reports pairs of namespace and description
     */
    public static void register(PluginContext context, String... reports) {
        Path dir = install();
        context.addSchema(new PluginSchema(ENVELOPE, dir.resolve(FILES.getFirst()).toAbsolutePath().toString(),
                "ESMA report in its business data envelope (BizData)", null));
        for (int i = 0; i + 1 < reports.length; i += 2) {
            context.addSchema(new PluginSchema(reports[i], dir.resolve(fileOf(reports[i])).toAbsolutePath().toString(),
                    reports[i + 1], null));
        }
    }
}
