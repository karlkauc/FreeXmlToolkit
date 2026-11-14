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

import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.theme.Workflow;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.plugin.DocumentBadge;
import org.fxt.freexmltoolkit.plugin.FxtPlugin;
import org.fxt.freexmltoolkit.plugin.PluginActivity;
import org.fxt.freexmltoolkit.plugin.PluginContext;
import org.fxt.freexmltoolkit.plugin.PluginMenuItem;
import org.fxt.freexmltoolkit.plugin.PluginSchema;
import org.fxt.freexmltoolkit.plugin.PluginUi;
import org.fxt.freexmltoolkit.util.ProjectLinks;

/**
 * Support for the FMA pension fund reporting (PK-Meldewesen): the quarterly report
 * (PK-QMV, {@code Nationale_Meldung_pfq}), the annual report (PK-FJMV, {@code Nationale_Meldung_pfa})
 * and the ECB report ({@code EZB_Meldung_pfe}).
 *
 * <p>Like FundsXML the plugin is always loaded and its own switch ({@link PkSettings#ENABLED})
 * turns it on, so it can offer itself once when a report is opened. While on it contributes the
 * activity, a status-bar badge and FMA links; the taxonomy's schemas are bound to the reports by
 * their root element (the reports have no namespace, and the schema location they declare is
 * usually a path on the sender's network).</p>
 */
public final class PkMeldewesenPlugin implements FxtPlugin {

    private static final Logger logger = LogManager.getLogger(PkMeldewesenPlugin.class);

    /** The plugin's and the activity's id. */
    public static final String ID = "pk-meldewesen";

    /** The FMA's page of the pension fund reporting. */
    static final String FMA_REPORTING_URL = "https://www.fma.gv.at/pensionskassen/";
    /** The FMA Incoming Plattform, where the reports are submitted. */
    static final String FMA_INCOMING_URL = "https://incoming.fma.gv.at/";

    private final PkTaxonomy taxonomy;

    /** The plugin over the user's taxonomy store. */
    public PkMeldewesenPlugin() {
        this(PkTaxonomy.shared());
    }

    PkMeldewesenPlugin(PkTaxonomy taxonomy) {
        this.taxonomy = taxonomy;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "PK-Meldewesen (FMA)";
    }

    @Override
    public String version() {
        return "1.0";
    }

    @Override
    public String description() {
        return "Austrian pension fund reports to the FMA (PK-QMV, PK-FJMV, ECB): schemas, recognition and tools.";
    }

    @Override
    public void register(PluginContext context) {
        context.addActivity(new PluginActivity(ID, "PK-Meldewesen", "bi-bank2", Workflow.PENSION,
                ui -> new PkMeldewesenPanel(ui, taxonomy), PkSettings::isEnabled));
        context.addMenuItem(new PluginMenuItem(PluginMenuItem.Menu.HELP, "pk-fma-reporting",
                I18n.t("PkMeldewesenPlugin.fma-reporting"), "bi-globe",
                ui -> ProjectLinks.openInBrowser(FMA_REPORTING_URL), PkSettings::isEnabled));
        context.addMenuItem(new PluginMenuItem(PluginMenuItem.Menu.HELP, "pk-fma-incoming",
                I18n.t("PkMeldewesenPlugin.fma-incoming"), "bi-box-arrow-up-right",
                ui -> ProjectLinks.openInBrowser(FMA_INCOMING_URL), PkSettings::isEnabled));
        registerSchemas(context);
        context.addDocumentBadge(PkMeldewesenPlugin::badgeFor);
        context.addDocumentListener((ui, path, content) -> {
            if (shouldOfferHint(content)) {
                showHint(ui);
            }
        });
    }

    /**
     * Binds the active taxonomy's three schemas to their root elements. The bundled taxonomy is
     * copied into the store first if it is not there (a one-time copy of a few MB).
     */
    void registerSchemas(PluginContext context) {
        try {
            if (taxonomy.schema(PkTaxonomy.BUNDLED_VERSION, PkModule.PFQ) == null) {
                taxonomy.ensureBundled();
            }
            String version = taxonomy.activeVersion();
            for (PkModule module : PkModule.values()) {
                Path schema = taxonomy.schema(version, module);
                if (schema != null) {
                    context.addSchema(new PluginSchema(null, schema.toAbsolutePath().toString(),
                            "FMA PK-Meldewesen " + module.title() + " (" + module.shortName() + "), Taxonomie " + version,
                            module.rootElement()));
                }
            }
        } catch (RuntimeException e) {
            logger.warn("PK-Meldewesen schemas not registered: {}", e.getMessage());
        }
    }

    /** @return the status-bar badge of a PK report while the extension is on */
    static DocumentBadge.Badge badgeFor(Path path, String content) {
        if (!PkSettings.isEnabled()) {
            return null;
        }
        PkReport report = PkReport.detect(content);
        if (report == null) {
            return null;
        }
        return new DocumentBadge.Badge(report.label(), "bi-bank2",
                I18n.t("PkMeldewesenPlugin.badge-tooltip", report.module().title()), Workflow.PENSION,
                ui -> ui.showActivity(ID));
    }

    /** @return whether to offer the extension for a document: a PK report, extension off, not offered before */
    static boolean shouldOfferHint(String content) {
        return !PkSettings.isEnabled() && !Boolean.parseBoolean(PkSettings.get(PkSettings.HINT_SHOWN, "false"))
                && PkReport.detect(content) != null;
    }

    /** Shows the one-time hint above the editor. */
    static void showHint(PluginUi ui) {
        PkSettings.set(PkSettings.HINT_SHOWN, "true");
        ui.showEditorBar(new PkHintBar(() -> {
            PkSettings.setEnabled(true);
            ui.refreshContributions();
        }, () -> { }));
    }
}
