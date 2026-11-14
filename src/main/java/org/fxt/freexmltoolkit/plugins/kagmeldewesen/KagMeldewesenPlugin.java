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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.Workflow;
import org.fxt.freexmltoolkit.plugin.DocumentBadge;
import org.fxt.freexmltoolkit.plugin.FxtPlugin;
import org.fxt.freexmltoolkit.plugin.PluginActivity;
import org.fxt.freexmltoolkit.plugin.PluginContext;
import org.fxt.freexmltoolkit.plugin.PluginMenuItem;
import org.fxt.freexmltoolkit.plugin.PluginSchema;
import org.fxt.freexmltoolkit.plugin.PluginUi;
import org.fxt.freexmltoolkit.plugins.IdentifierKind;
import org.fxt.freexmltoolkit.plugins.IndustryPanel;
import org.fxt.freexmltoolkit.plugins.regreport.BundledSchemas;
import org.fxt.freexmltoolkit.plugins.regreport.CatalogDescriber;
import org.fxt.freexmltoolkit.plugins.regreport.FieldCatalog;
import org.fxt.freexmltoolkit.plugins.regreport.RegRuleSet;
import org.fxt.freexmltoolkit.plugins.regreport.RegValidator;
import org.fxt.freexmltoolkit.util.ProjectLinks;

/**
 * Reports of Austrian management companies (KAG / AIFM) to the FMA. So far the AIFMD reporting
 * (Annex IV): the AIFM file ({@code AIFMReportingInfo}) and the AIF file ({@code AIFReportingInfo}) on
 * ESMA's XML schemas v1.2, checked with the FMA's rules, with the field labels of ESMA's technical
 * guidance. Off by default (Settings → Extensions).
 */
public final class KagMeldewesenPlugin implements FxtPlugin {

    private static final Logger logger = LogManager.getLogger(KagMeldewesenPlugin.class);

    /** The plugin's and the activity's id. */
    public static final String ID = "kag-meldewesen";
    /** The FMA's information on the AIFMD reporting. */
    static final String FMA_AIFMD_URL =
            "https://www.fma.gv.at/investmentfonds-und-verwaltungsgesellschaften/aif-verwalter-alternativer-investmentfonds/faq/";

    static final String AIFMD_RESOURCES = "/plugins/kagmeldewesen/aifmd";
    static final List<String> AIFMD_SCHEMAS = List.of("AIFMD_DATAIF_V1.2.xsd", "AIFMD_DATMAN_V1.2.xsd",
            "AIFMD_REPORTING_DataTypes_V1.2.xsd");

    static final Supplier<FieldCatalog> AIF_FIELDS = CatalogDescriber.lazy(AIFMD_RESOURCES + "/aif-fields.json");
    static final Supplier<FieldCatalog> AIFM_FIELDS = CatalogDescriber.lazy(AIFMD_RESOURCES + "/aifm-fields.json");

    private static volatile RegRuleSet aifRules;
    private static volatile RegRuleSet aifmRules;

    @Override public String id() { return ID; }
    @Override public String name() { return "KAG-Meldewesen (FMA)"; }
    @Override public String version() { return "1.0"; }
    @Override public boolean enabledByDefault() { return false; }

    @Override
    public String description() {
        return "Reports of Austrian management companies to the FMA: AIFMD reporting (AIFM and AIF file) with the FMA's rules.";
    }

    /** @return the rules of a report's root element, or {@code null} for other documents */
    static RegRuleSet rulesFor(RegValidator.Root root) {
        if (!root.namespace().isEmpty()) {
            return null;
        }
        return switch (root.localName()) {
            case AifmdRules.AIF_ROOT -> {
                if (aifRules == null) {
                    aifRules = AifmdRules.aif(AIF_FIELDS.get());
                }
                yield aifRules;
            }
            case AifmdRules.AIFM_ROOT -> {
                if (aifmRules == null) {
                    aifmRules = AifmdRules.aifm(AIFM_FIELDS.get());
                }
                yield aifmRules;
            }
            default -> null;
        };
    }

    @Override
    public void register(PluginContext context) {
        RegValidator validator = new RegValidator("KAG-Meldewesen", () -> true, KagMeldewesenPlugin::rulesFor);
        context.addValidator(validator);
        context.addElementDescriber(new CatalogDescriber(() -> true, List.of(AIF_FIELDS, AIFM_FIELDS)));
        try {
            Path dir = BundledSchemas.install(ID, "aifmd-1.2", AIFMD_RESOURCES, AIFMD_SCHEMAS);
            context.addSchema(new PluginSchema(null, dir.resolve("AIFMD_DATAIF_V1.2.xsd").toAbsolutePath().toString(),
                    "ESMA AIFMD reporting, AIF file (v1.2)", AifmdRules.AIF_ROOT));
            context.addSchema(new PluginSchema(null, dir.resolve("AIFMD_DATMAN_V1.2.xsd").toAbsolutePath().toString(),
                    "ESMA AIFMD reporting, AIFM file (v1.2)", AifmdRules.AIFM_ROOT));
        } catch (RuntimeException e) {
            logger.warn("KAG-Meldewesen schemas not registered: {}", e.getMessage());
        }
        context.addDocumentBadge((path, content) -> {
            String label = label(content);
            return label == null ? null : new DocumentBadge.Badge(label, "bi-briefcase",
                    "Recognised as AIFMD report; the ESMA schema and the FMA's rules apply.", Workflow.VALIDATION,
                    ui -> ui.showActivity(ID));
        });
        context.addMenuItem(new PluginMenuItem(PluginMenuItem.Menu.HELP, "kag-fma-aifmd", "FMA AIFMD Reporting",
                "bi-globe", ui -> ProjectLinks.openInBrowser(FMA_AIFMD_URL), null));
        context.addActivity(new PluginActivity(ID, "KAG-Meldewesen", "bi-briefcase", Workflow.VALIDATION,
                ui -> new IndustryPanel(ui, "KAG-MELDEWESEN",
                        "Reports of management companies to the FMA. AIFMD reporting: the AIFM file and the AIF file are "
                                + "bound to ESMA's schema v1.2 and checked with the FMA's rules (reporting member state AT, "
                                + "reporting period, content blocks, conditional fields, percentages adding up to 100, "
                                + "rankings, LEI and ISIN check digits). The Inspector shows the ESMA field number, "
                                + "guidance and FMA rule of every element.",
                        List.of(new IndustryPanel.Action("kag-new-aif", "New AIF File (Example)", "bi-file-earmark-plus",
                                        ActionColor.CREATE, ui2 -> openExample(ui2, "aif-example.xml")),
                                new IndustryPanel.Action("kag-new-aifm", "New AIFM File (Example)", "bi-file-earmark-plus",
                                        ActionColor.CREATE, ui2 -> openExample(ui2, "aifm-example.xml"))),
                        KagMeldewesenPlugin::describe, validator,
                        List.of(IdentifierKind.LEI, IdentifierKind.ISIN, IdentifierKind.BIC)),
                null));
    }

    private static final Pattern FIRST_RECORD = Pattern.compile(
            "<FilingType>\\s*(\\w+)\\s*</FilingType>.*?<ReportingPeriodType>\\s*(\\w+)\\s*</ReportingPeriodType>\\s*"
                    + "<ReportingPeriodYear>\\s*(\\d{4})", Pattern.DOTALL);

    /** @return the kind of AIFMD report, or {@code null} for other documents */
    static String kind(String content) {
        RegValidator.Root root = content == null ? null : RegValidator.rootOf(content);
        if (root == null || !root.namespace().isEmpty()) {
            return null;
        }
        return switch (root.localName()) {
            case AifmdRules.AIF_ROOT -> "AIF";
            case AifmdRules.AIFM_ROOT -> "AIFM";
            default -> null;
        };
    }

    /** @return the badge text ({@code AIFMD AIF Y1/2025}), or {@code null} */
    static String label(String content) {
        String kind = kind(content);
        if (kind == null) {
            return null;
        }
        Matcher m = FIRST_RECORD.matcher(content.length() > 20_000 ? content.substring(0, 20_000) : content);
        return "AIFMD " + kind + (m.find() ? " " + m.group(2) + "/" + m.group(3) : "");
    }

    /** @return what the panel says about the document, or {@code null} */
    static String describe(String content) {
        String kind = kind(content);
        if (kind == null) {
            return null;
        }
        String tag = "<" + kind + "RecordInfo>";
        int records = 0;
        for (int i = content.indexOf(tag); i >= 0; i = content.indexOf(tag, i + tag.length())) {
            records++;
        }
        Matcher m = FIRST_RECORD.matcher(content.length() > 20_000 ? content.substring(0, 20_000) : content);
        String period = m.find() ? m.group(1) + " · " + m.group(2) + "/" + m.group(3) + " · " : "";
        return "AIFMD " + kind + " file (ESMA schema v1.2)\n" + period + records + (records == 1 ? " record" : " records");
    }

    static void openExample(PluginUi ui, String name) {
        try (InputStream in = KagMeldewesenPlugin.class.getResourceAsStream("/plugins/kagmeldewesen/examples/" + name)) {
            if (in == null) {
                return;
            }
            ui.editorHost().openGeneratedDocument(new String(in.readAllBytes(), StandardCharsets.UTF_8), EditorFileType.XML, name);
        } catch (Exception e) {
            logger.warn("Example {} not opened: {}", name, e.getMessage());
        }
    }
}
