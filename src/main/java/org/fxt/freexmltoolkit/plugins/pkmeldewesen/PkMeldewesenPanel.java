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

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.editor.PanelStatus;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.plugin.PluginUi;
import org.fxt.freexmltoolkit.util.DesktopActions;

/**
 * The PK-Meldewesen side panel: what the active report is (module, pension fund, period,
 * taxonomy), the taxonomy in use with its schema and FMA documentation, and the checks.
 */
public final class PkMeldewesenPanel extends VBox {

    private final PluginUi ui;
    private final PkTaxonomy taxonomy;

    @FXML private Label reportSummary;
    @FXML private Label taxonomyLabel;
    @FXML private Label status;

    /** The code area of the active document, watched for its text (it loads after the tab opens). */
    private org.fxmisc.richtext.CodeArea watched;
    private final javafx.animation.PauseTransition refreshDelay =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
    private final javafx.beans.value.ChangeListener<Integer> lengthListener =
            (obs, o, n) -> refreshDelay.playFromStart();

    /**
     * @param ui       the shell
     * @param taxonomy the taxonomy store
     */
    public PkMeldewesenPanel(PluginUi ui, PkTaxonomy taxonomy) {
        this.ui = ui;
        this.taxonomy = taxonomy;
        FxmlViews.load(this, "/pages/panels/PkMeldewesenPanel.fxml");
        getStyleClass().add("fxt-wf-pension");
        refreshDelay.setOnFinished(e -> refresh());
        ui.editorHost().activeTabProperty().addListener((obs, o, n) -> Platform.runLater(this::watchActive));
        watchActive();
    }

    /** Follows the active document's text and shows what it is. */
    private void watchActive() {
        if (watched != null) {
            watched.lengthProperty().removeListener(lengthListener);
        }
        watched = ui.editorHost().getActiveCodeArea();
        if (watched != null) {
            watched.lengthProperty().addListener(lengthListener);
        }
        refresh();
    }

    /** @return the report of the active document, or {@code null}; reads only the start of the text */
    private PkReport activeReport() {
        org.fxmisc.richtext.CodeArea area = ui.editorHost().getActiveCodeArea();
        if (area == null) {
            return null;
        }
        return PkReport.detect(area.getText(0, Math.min(area.getLength(), PkReport.SCAN_LIMIT)));
    }

    /** Shows the active report and the taxonomy in use. */
    void refresh() {
        PkReport report = activeReport();
        reportSummary.setText(report == null ? I18n.t("PkMeldewesenPanel.no-report") : summary(report));
        String version = taxonomy.activeVersion();
        String text = I18n.t("PkMeldewesenPanel.taxonomy-version", version);
        if (report != null && report.taxonomy() != null && !version.startsWith(report.taxonomy())) {
            text += "\n" + I18n.t("PkMeldewesenPanel.taxonomy-mismatch", report.taxonomy());
        }
        taxonomyLabel.setText(text);
    }

    /** @return the multi-line description of a report */
    static String summary(PkReport report) {
        StringBuilder text = new StringBuilder(report.label()).append(" · ").append(report.module().title());
        appendLine(text, I18n.t("PkMeldewesenPanel.pk-number"), report.pkNumber());
        appendLine(text, I18n.t("PkMeldewesenPanel.report-type"), report.reportType());
        appendLine(text, "LEI", report.attributes().get("LEI"));
        appendLine(text, I18n.t("PkMeldewesenPanel.taxonomy"), report.taxonomy());
        return text.toString();
    }

    private static void appendLine(StringBuilder text, String label, String value) {
        if (value != null && !value.isBlank()) {
            text.append('\n').append(label).append(": ").append(value);
        }
    }

    @FXML
    public void onValidate() {
        if (activeReport() == null) {
            PanelStatus.info(status, I18n.t("PkMeldewesenPanel.no-report"));
            return;
        }
        ui.editorHost().runValidation();
    }

    @FXML
    public void onOpenSchema() {
        PkReport report = activeReport();
        PkModule module = report != null ? report.module() : PkModule.PFQ;
        Path schema = taxonomy.schema(taxonomy.activeVersion(), module);
        if (schema == null) {
            PanelStatus.info(status, I18n.t("PkMeldewesenPanel.no-schema"));
            return;
        }
        ui.openFile(schema);
    }

    @FXML
    public void onOpenDocumentation() {
        Path pdf = taxonomy.documentation(taxonomy.activeVersion());
        if (pdf == null) {
            PanelStatus.info(status, I18n.t("PkMeldewesenPanel.no-documentation"));
            return;
        }
        DesktopActions.open(pdf.toFile(), e -> PanelStatus.info(status, String.valueOf(e.getMessage())));
    }

    @FXML
    public void onOpenFolder() {
        DesktopActions.open(taxonomy.dir(taxonomy.activeVersion()).toFile(),
                e -> PanelStatus.info(status, String.valueOf(e.getMessage())));
    }
}
