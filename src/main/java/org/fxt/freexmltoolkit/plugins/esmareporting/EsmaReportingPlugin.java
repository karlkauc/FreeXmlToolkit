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

package org.fxt.freexmltoolkit.plugins.esmareporting;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.fxt.freexmltoolkit.plugin.PluginUi;
import org.fxt.freexmltoolkit.plugins.IdentifierKind;
import org.fxt.freexmltoolkit.plugins.IndustryPanel;
import org.fxt.freexmltoolkit.plugins.regreport.ActionMatrix;
import org.fxt.freexmltoolkit.plugins.regreport.CatalogDescriber;
import org.fxt.freexmltoolkit.plugins.regreport.EsmaSchemas;
import org.fxt.freexmltoolkit.plugins.regreport.FieldCatalog;
import org.fxt.freexmltoolkit.plugins.regreport.RegRuleSet;
import org.fxt.freexmltoolkit.plugins.regreport.RegValidator;
import org.fxt.freexmltoolkit.util.ProjectLinks;

/**
 * ESMA's transaction reports in ISO 20022: the EMIR Refit derivatives trade report (auth.030). The
 * reports are bound to ESMA's schemas (also inside the BizData envelope) and checked with ESMA's
 * validation rules: the fields that are mandatory or not applicable per action type and level,
 * header counts, UTI, codes and identifiers; the Inspector names every element with its field
 * number. Off by default (Settings → Extensions).
 */
public final class EsmaReportingPlugin implements FxtPlugin {

    private static final Logger logger = LogManager.getLogger(EsmaReportingPlugin.class);

    /** The plugin's and the activity's id. */
    public static final String ID = "esma-reporting";
    static final String ESMA_EMIR_URL = "https://www.esma.europa.eu/data-reporting/emir-reporting";

    static final Supplier<FieldCatalog> EMIR_FIELDS = CatalogDescriber.lazy("/plugins/esmareporting/emir/emir-fields.json");
    private static volatile ActionMatrix emirMatrix;
    private static volatile RegRuleSet emirRules;

    @Override public String id() { return ID; }
    @Override public String name() { return "ESMA Reporting (EMIR)"; }
    @Override public String version() { return "1.0"; }
    @Override public boolean enabledByDefault() { return false; }

    @Override
    public String description() {
        return "ESMA transaction reports in ISO 20022: EMIR Refit trade reports with ESMA's validation rules.";
    }

    /** @return the report kind of a document ({@code EMIR}), or {@code null} */
    static String kind(RegValidator.Root root, String content) {
        if (root == null) {
            return null;
        }
        if (EsmaSchemas.EMIR.equals(root.namespace())) {
            return "EMIR";
        }
        if (EsmaSchemas.ENVELOPE.equals(root.namespace())) {
            String head = content.length() > 8000 ? content.substring(0, 8000) : content;
            if (head.contains(EsmaSchemas.EMIR)) {
                return "EMIR";
            }
        }
        return null;
    }

    static ActionMatrix emirMatrix() {
        if (emirMatrix == null) {
            emirMatrix = ActionMatrix.load("/plugins/esmareporting/emir/emir-matrix.json");
        }
        return emirMatrix;
    }

    /** @return the rules of a report, or {@code null} for other documents */
    static RegRuleSet rulesFor(RegValidator.Root root, String content) {
        if ("EMIR".equals(kind(root, content))) {
            if (emirRules == null) {
                emirRules = EmirRules.rules(EMIR_FIELDS.get(), emirMatrix());
            }
            return emirRules;
        }
        return null;
    }

    @Override
    public void register(PluginContext context) {
        RegValidator validator = new RegValidator("ESMA Reporting", () -> true,
                (java.util.function.BiFunction<RegValidator.Root, String, RegRuleSet>) EsmaReportingPlugin::rulesFor);
        context.addValidator(validator);
        context.addElementDescriber(new CatalogDescriber(() -> true, List.of(EMIR_FIELDS)));
        try {
            EsmaSchemas.register(context, EsmaSchemas.EMIR, "ESMA EMIR Refit derivatives trade report (auth.030.001.03)");
        } catch (RuntimeException e) {
            logger.warn("ESMA schemas not registered: {}", e.getMessage());
        }
        context.addDocumentBadge((path, content) -> {
            String label = label(content);
            return label == null ? null : new DocumentBadge.Badge(label, "bi-arrow-left-right",
                    "Recognised as ESMA report; ESMA's schema and validation rules apply.", Workflow.VALIDATION,
                    ui -> ui.showActivity(ID));
        });
        context.addMenuItem(new PluginMenuItem(PluginMenuItem.Menu.HELP, "esma-emir-reporting", "ESMA EMIR Reporting",
                "bi-globe", ui -> ProjectLinks.openInBrowser(ESMA_EMIR_URL), null));
        context.addActivity(new PluginActivity(ID, "ESMA Reporting", "bi-arrow-left-right", Workflow.VALIDATION,
                ui -> new IndustryPanel(ui, "ESMA REPORTING",
                        "ESMA's transaction reports in ISO 20022. EMIR Refit trade reports (auth.030) are bound to "
                                + "ESMA's schema - also in the BizData envelope - and checked with ESMA's validation rules: "
                                + "which fields are mandatory or not applicable for each action type (NEWT, MODI, VALU, "
                                + "CORR, TERM, EROR, REVI, POSC) at trade and position level, the record count, the UTI, "
                                + "LEI, ISIN and ISO codes. The Inspector shows the EMIR field number of every element.",
                        List.of(new IndustryPanel.Action("esma-new-emir", "New EMIR Error Report (Example)",
                                "bi-file-earmark-plus", ActionColor.CREATE, ui2 -> openExample(ui2, "emir-eror-example.xml"))),
                        EsmaReportingPlugin::describe, validator, List.of(IdentifierKind.LEI, IdentifierKind.ISIN)),
                null));
    }

    private static final Pattern ACTION = Pattern.compile("<(?:\\w+:)?Rpt>\\s*<(?:\\w+:)?(New|Mod|Crrctn|Termntn|PosCmpnt|ValtnUpd|Err|Rvv)>");

    /** @return the number of reports per action type, in order of appearance */
    static Map<String, Integer> actions(String content) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Matcher m = ACTION.matcher(content);
        while (m.find()) {
            counts.merge(EmirRules.ACTIONS.get(m.group(1)), 1, Integer::sum);
        }
        return counts;
    }

    /** @return the badge text ({@code EMIR · 3 reports}), or {@code null} */
    static String label(String content) {
        String kind = content == null ? null : kind(RegValidator.rootOf(content), content);
        if (kind == null) {
            return null;
        }
        int reports = actions(content).values().stream().mapToInt(Integer::intValue).sum();
        return kind + " · " + reports + (reports == 1 ? " report" : " reports");
    }

    /** @return what the panel says about the document, or {@code null} */
    static String describe(String content) {
        String label = label(content);
        if (label == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        actions(content).forEach((action, n) -> parts.add(n + " × " + action));
        return "EMIR Refit trade report (auth.030.001.03)\n" + label + (parts.isEmpty() ? "" : "\n" + String.join(", ", parts));
    }

    static void openExample(PluginUi ui, String name) {
        try (InputStream in = EsmaReportingPlugin.class.getResourceAsStream("/plugins/esmareporting/examples/" + name)) {
            if (in == null) {
                return;
            }
            ui.editorHost().openGeneratedDocument(new String(in.readAllBytes(), StandardCharsets.UTF_8), EditorFileType.XML, name);
        } catch (Exception e) {
            logger.warn("Example {} not opened: {}", name, e.getMessage());
        }
    }
}
