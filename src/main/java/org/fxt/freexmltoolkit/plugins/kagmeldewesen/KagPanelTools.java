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

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.BiFunction;

import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.PanelStatus;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.plugin.PluginUi;
import org.fxt.freexmltoolkit.plugins.IndustryPanel;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/**
 * The KAG-Meldewesen panel's tools and options: DeRiMV import from CSV/Excel and export to Excel/CSV, grid
 * formulas for DeRiMV and AIF files, and the rule settings.
 */
final class KagPanelTools {

    /** Test hook: answers the file choosers ({@code true} = save dialog) instead of showing them. */
    static volatile BiFunction<Boolean, String, Path> chooser;

    private KagPanelTools() {
    }

    /** Adds the tools to the panel. */
    static IndustryPanel install(IndustryPanel panel) {
        return panel.withTool(new IndustryPanel.Action("kag-derimv-import", "Import DeRiMV from CSV/Excel…",
                        "bi-file-earmark-arrow-down", ActionColor.CREATE, ui -> importDerimv(ui, panel)))
                .withTool(new IndustryPanel.Action("kag-derimv-export", "Export DeRiMV to Excel/CSV…",
                        "bi-file-earmark-spreadsheet", ActionColor.NAVIGATE, ui -> exportDerimv(ui, panel)))
                .withTool(new IndustryPanel.Action("kag-grid-add", "Add Grid Formulas (DeRiMV, AIF)",
                        "bi-calculator", ActionColor.CREATE, ui -> gridCopy(ui, panel, true)))
                .withTool(new IndustryPanel.Action("kag-grid-remove", "Remove Grid Formulas",
                        "bi-eraser", ActionColor.DELETE, ui -> gridCopy(ui, panel, false)))
                .withOptions(new KagOptionsPane());
    }

    /**
     * Opens a copy of the active report with the grid formulas and filters (in the Graphic view) or without
     * them (in the Text view, ready for submission).
     */
    static void gridCopy(PluginUi ui, IndustryPanel panel, boolean add) {
        String xml = ui.editorHost().getActiveText().orElse(null);
        String kind = xml == null ? null : KagMeldewesenPlugin.kind(xml);
        if (kind == null) {
            PanelStatus.precondition(panel.status(), "Open a KAG report first.");
            return;
        }
        var annotations = KagGrid.annotations(kind, KagSettings.options());
        if (add && annotations.isEmpty()) {
            PanelStatus.precondition(panel.status(), "Grid formulas exist for DeRiMV reports and AIF files.");
            return;
        }
        String name = ui.editorHost().getActiveDocument().map(d -> d.getDisplayName()).orElse("report.xml")
                .replaceFirst("(?i)(-grid|-clean)?\\.xml$", "");
        panel.background(add ? "Add grid formulas" : "Remove grid formulas", () -> add
                ? org.fxt.freexmltoolkit.plugins.regreport.GridAnnotations.add(xml, annotations)
                : org.fxt.freexmltoolkit.plugins.regreport.GridAnnotations.remove(xml), result -> {
            ui.editorHost().openGeneratedDocument(result, EditorFileType.XML, name + (add ? "-grid.xml" : "-clean.xml"));
            ui.editorHost().setActiveViewMode(add ? org.fxt.freexmltoolkit.controls.shell.editor.ViewMode.GRAPHIC
                    : org.fxt.freexmltoolkit.controls.shell.editor.ViewMode.TEXT);
            PanelStatus.success(panel.status(), add ? "Grid formulas added - remove them before submitting."
                    : "Grid formulas removed.");
        });
    }

    private static Path choose(PluginUi ui, boolean save, String title, String initialName) {
        if (chooser != null) {
            return chooser.apply(save, initialName);
        }
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        if (save) {
            fc.setInitialFileName(initialName);
            fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Excel", "*.xlsx"),
                    new FileChooser.ExtensionFilter("CSV", "*.csv"));
        } else {
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV / Excel", "*.csv", "*.txt", "*.xlsx", "*.xls"));
        }
        File file = save ? FileChooserHelper.showSaveDialog(fc, ui.window()) : FileChooserHelper.showOpenDialog(fc, ui.window());
        return file == null ? null : file.toPath();
    }

    /** Reads a spreadsheet, opens the XML as a new document and checks it (F8). */
    static void importDerimv(PluginUi ui, IndustryPanel panel) {
        Path file = choose(ui, false, "Import DeRiMV from CSV/Excel", null);
        if (file == null) {
            return;
        }
        panel.background("Import " + file.getFileName(), () -> DerimvSpreadsheet.read(file), result -> {
            DerimvSpreadsheet.Header h = result.report().header();
            String name = "derimv-" + h.year() + "Q" + h.quarter() + ".xml";
            ui.editorHost().openGeneratedDocument(DerimvSpreadsheet.toXml(result.report()), EditorFileType.XML, name);
            int funds = result.report().funds().size();
            if (result.issues().isEmpty()) {
                PanelStatus.success(panel.status(), funds + (funds == 1 ? " fund" : " funds") + " imported from "
                        + file.getFileName() + ".");
            } else {
                StringBuilder text = new StringBuilder(funds + (funds == 1 ? " fund" : " funds") + " imported; "
                        + result.issues().size() + (result.issues().size() == 1 ? " row needs" : " rows need") + " a look:");
                result.issues().stream().limit(5).forEach(i -> text.append("\n• Row ").append(i.row()).append(": ").append(i.message()));
                if (result.issues().size() > 5) {
                    text.append("\n… and ").append(result.issues().size() - 5).append(" more.");
                }
                PanelStatus.precondition(panel.status(), text.toString());
            }
            ui.editorHost().runValidation();
        });
    }

    /** Writes the active DeRiMV report as Excel workbook or CSV (by the chosen extension). */
    static void exportDerimv(PluginUi ui, IndustryPanel panel) {
        String xml = ui.editorHost().getActiveText().orElse(null);
        if (xml == null || !"DeRiMV".equals(KagMeldewesenPlugin.kind(xml))) {
            PanelStatus.precondition(panel.status(), "Open a DeRiMV report (Derivatemeldung) first.");
            return;
        }
        String source = ui.editorHost().getActiveDocument().map(d -> d.getDisplayName()).orElse("derimv.xml");
        Path target = choose(ui, true, "Export DeRiMV to Excel/CSV", source.replaceFirst("(?i)\\.xml$", "") + ".xlsx");
        if (target == null) {
            return;
        }
        panel.background("Export " + target.getFileName(), () -> {
            DerimvSpreadsheet.Report report = DerimvSpreadsheet.fromXml(xml);
            if (target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                Files.write(target, DerimvSpreadsheet.toCsv(report));
            } else {
                try (OutputStream out = Files.newOutputStream(target)) {
                    DerimvSpreadsheet.writeXlsx(report, source, out);
                }
            }
            return report.funds().size();
        }, funds -> PanelStatus.success(panel.status(), funds + (funds == 1 ? " fund" : " funds") + " written to "
                + target.getFileName() + "."));
    }
}
