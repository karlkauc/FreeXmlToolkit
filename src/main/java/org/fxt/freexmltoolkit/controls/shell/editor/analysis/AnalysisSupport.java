package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Function;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.stage.FileChooser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/** Small UI helpers shared by the Schema Analysis sections (columns, chips, export menu). */
final class AnalysisSupport {

    private static final Logger logger = LogManager.getLogger(AnalysisSupport.class);

    private AnalysisSupport() {
    }

    /** The report export formats offered by the Statistics and Quality sections. */
    enum ExportFormat {
        CSV("csv", "CSV", "bi-filetype-csv"),
        JSON("json", "JSON", "bi-filetype-json"),
        HTML("html", "HTML", "bi-filetype-html"),
        PDF("pdf", "PDF", "bi-filetype-pdf"),
        EXCEL("xlsx", "Excel", "bi-file-earmark-excel");

        final String extension;
        final String label;
        final String icon;

        ExportFormat(String extension, String label, String icon) {
            this.extension = extension;
            this.label = label;
            this.icon = icon;
        }
    }

    /** Writes one report format to a file; may throw (the exporters declare checked exceptions). */
    @FunctionalInterface
    interface ExportWriter {
        void write(ExportFormat format, Path target) throws Exception;
    }

    /** A read-only text column backed by {@code value}. */
    static <T> TableColumn<T, String> column(String title, Function<T, String> value, double prefWidth) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(nullSafe(value.apply(c.getValue()))));
        if (prefWidth > 0) {
            column.setPrefWidth(prefWidth);
        }
        return column;
    }

    /** A small rounded count chip, e.g. "3 errors", tinted by {@code tone} (error/warning/info/suggestion/ok). */
    static Label chip(String text, String tone) {
        Label chip = new Label(text);
        chip.getStyleClass().addAll("fxt-analysis-chip", "fxt-analysis-chip-" + tone);
        return chip;
    }

    /** An uppercase group heading inside a section. */
    static Label groupTitle(String text) {
        Label label = new Label(text.toUpperCase(Locale.ROOT));
        label.getStyleClass().add("fxt-analysis-group-title");
        return label;
    }

    /** A muted placeholder / empty-state label. */
    static Label emptyLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("fxt-analysis-empty");
        return label;
    }

    /** The icon for a quality-issue severity (colour comes from the {@code fxt-analysis-sev-*} CSS class). */
    static IconifyIcon severityIcon(XsdQualityChecker.IssueSeverity severity, int size) {
        String literal = switch (severity) {
            case ERROR -> "bi-x-circle-fill";
            case WARNING -> "bi-exclamation-triangle-fill";
            case INFO -> "bi-info-circle-fill";
            case SUGGESTION -> "bi-lightbulb-fill";
        };
        IconifyIcon icon = icon(literal, size);
        icon.getStyleClass().add("fxt-analysis-sev-icon");
        return icon;
    }

    static IconifyIcon icon(String literal, int size) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(size);
        return icon;
    }

    /**
     * An "Export" menu with one item per {@link ExportFormat}. Choosing an item prompts for a
     * target file, writes it off the FX thread and reports the outcome in {@code status}.
     */
    static MenuButton exportMenu(String baseName, Label status, ExportWriter writer) {
        MenuButton menu = new MenuButton("Export", icon("bi-download", 16));
        menu.setId("analysis-export-" + baseName);
        menu.getStyleClass().add("fxt-tool-button");
        for (ExportFormat format : ExportFormat.values()) {
            MenuItem item = new MenuItem(format.label, icon(format.icon, 16));
            item.setOnAction(e -> export(menu, baseName, format, status, writer));
            menu.getItems().add(item);
        }
        return menu;
    }

    private static void export(Node owner, String baseName, ExportFormat format, Label status, ExportWriter writer) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export " + baseName + " as " + format.label);
        chooser.setInitialFileName(baseName.toLowerCase(Locale.ROOT).replace(' ', '-') + "." + format.extension);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                format.label + " File (*." + format.extension + ")", "*." + format.extension));
        File file = FileChooserHelper.showSaveDialog(chooser,
                owner.getScene() != null ? owner.getScene().getWindow() : null);
        if (file == null) {
            return;
        }
        status.setText("Exporting " + format.label + "…");
        FxtGui.executorService.submit(() -> {
            String outcome;
            try {
                writer.write(format, file.toPath());
                outcome = "Exported " + file.getName();
            } catch (Exception ex) {
                logger.warn("Schema analysis export failed", ex);
                outcome = "Export failed: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString());
            }
            String text = outcome;
            Platform.runLater(() -> status.setText(text));
        });
    }

    /** "NAMING_CONVENTION" → "Naming Convention". */
    static String titleCase(Enum<?> value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String word : value.name().toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    static String plural(long count, String singular) {
        return count + " " + singular + (count == 1 ? "" : "s");
    }
}
