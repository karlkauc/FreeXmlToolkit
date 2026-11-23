package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdIdentityConstraintAnalyzer;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.ReportFormat;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.ReportModel;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.report.ReportWriters;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.util.FileChooserHelper;

/** Small UI helpers shared by the Schema Analysis sections (columns, chips, export menu). */
final class AnalysisSupport {

    private static final Logger logger = LogManager.getLogger(AnalysisSupport.class);

    private AnalysisSupport() {
    }

    /** Writes one report format to a file; may throw (the writers declare checked exceptions). */
    @FunctionalInterface
    interface ExportWriter {
        void write(ReportFormat format, Path target) throws Exception;
    }

    /** A small rounded count chip, e.g. "3 errors", tinted by {@code tone} (error/warning/info/suggestion/ok). */
    static Label chip(String text, String tone) {
        Label chip = new Label(text);
        chip.getStyleClass().addAll("fxt-analysis-chip", "fxt-analysis-chip-" + tone);
        return chip;
    }

    /**
     * Makes {@code chip} toggle {@code filter} between {@code value} and {@code all}; the chip
     * carries {@code fxt-analysis-chip-active} while its value is the current filter.
     */
    static void toggleChip(Label chip, Property<String> filter, String value, String all) {
        chip.setCursor(Cursor.HAND);
        chip.setTooltip(new Tooltip(I18n.t("AnalysisSupport.click-to-show-only-these")));
        chip.setOnMouseClicked(e -> filter.setValue(value.equals(filter.getValue()) ? all : value));
        Runnable sync = () -> {
            boolean active = value.equals(filter.getValue());
            if (active != chip.getStyleClass().contains("fxt-analysis-chip-active")) {
                if (active) {
                    chip.getStyleClass().add("fxt-analysis-chip-active");
                } else {
                    chip.getStyleClass().remove("fxt-analysis-chip-active");
                }
            }
        };
        filter.addListener((obs, o, n) -> sync.run());
        sync.run();
    }

    /** "3 issues" when nothing is filtered out, else "Showing 1 of 3 issues". */
    static String countText(int visible, int total, String singular) {
        if (visible == total) {
            return plural(total, singular);
        }
        return I18n.t("AnalysisSupport.showing-of", visible, plural(total, singular));
    }

    /** The icon for an identity-constraint validation status (colour via {@code fxt-analysis-sev-*}). */
    static IconifyIcon statusIcon(XsdIdentityConstraintAnalyzer.ValidationStatus status, int size) {
        String literal = switch (status) {
            case VALID -> "bi-check-circle-fill";
            case WARNING -> "bi-exclamation-triangle-fill";
            case ERROR -> "bi-x-circle-fill";
        };
        IconifyIcon icon = icon(literal, size);
        icon.getStyleClass().add("fxt-analysis-sev-icon");
        return icon;
    }

    /** The icon for an identity-constraint kind. */
    static String constraintIcon(XsdIdentityConstraintAnalyzer.ConstraintType type) {
        return switch (type) {
            case KEY -> "bi-key";
            case KEYREF -> "bi-link-45deg";
            case UNIQUE -> "bi-fingerprint";
            case ASSERT -> "bi-check2-circle";
        };
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
    /**
     * @return the report name as the dialog shows it; {@code baseName} itself stays English, it is
     *         also the file name and the report's own title
     */
    static String reportTitle(String baseName) {
        String key = "AnalysisSupport.report." + baseName.toLowerCase(Locale.ROOT).replace(' ', '-');
        String text = I18n.t(key);
        return text.equals(key) ? baseName : text;
    }

    static MenuButton exportMenu(String baseName, Label status, ExportWriter writer) {
        MenuButton menu = new MenuButton(I18n.t("AnalysisSupport.export"), icon("bi-download", 16));
        menu.setId("analysis-export-" + baseName.toLowerCase(Locale.ROOT).replace(' ', '-'));
        menu.getStyleClass().add("fxt-tool-button");
        for (ReportFormat format : ReportFormat.values()) {
            MenuItem item = new MenuItem(format.label(), icon(format.icon(), 16));
            item.setOnAction(e -> export(menu, baseName, format, status, writer));
            menu.getItems().add(item);
        }
        return menu;
    }

    /**
     * An "Export" menu that renders the {@link ReportModel} supplied at click time through
     * {@link ReportWriters}; the supplier may return {@code null} while nothing is loaded.
     */
    static MenuButton reportMenu(String baseName, Label status, Supplier<ReportModel> model) {
        return exportMenu(baseName, status, (format, target) -> {
            ReportModel report = model.get();
            if (report == null) {
                throw new IllegalStateException(I18n.t("AnalysisSupport.nothing-to-export-yet"));
            }
            ReportWriters.write(report, format, target);
        });
    }

    private static void export(Node owner, String baseName, ReportFormat format, Label status, ExportWriter writer) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("AnalysisSupport.export-as", reportTitle(baseName), format.label()));
        chooser.setInitialFileName(baseName.toLowerCase(Locale.ROOT).replace(' ', '-') + "." + format.extension());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                I18n.t("AnalysisSupport.format-file", format.label(), format.extension()), "*." + format.extension()));
        File file = FileChooserHelper.showSaveDialog(chooser,
                owner.getScene() != null ? owner.getScene().getWindow() : null);
        if (file == null) {
            return;
        }
        status.setText(I18n.t("AnalysisSupport.exporting", format.label()));
        FxtGui.executorService.submit(() -> {
            String outcome;
            boolean ok = false;
            try {
                writer.write(format, file.toPath());
                outcome = I18n.t("AnalysisSupport.exported", file.getName());
                ok = true;
            } catch (Exception ex) {
                logger.warn("Schema analysis export failed", ex);
                outcome = I18n.t("AnalysisSupport.export-failed", ex.getMessage() != null ? ex.getMessage() : ex.toString());
            }
            String text = outcome;
            boolean written = ok;
            Platform.runLater(() -> {
                status.setText(text);
                if (written) {
                    org.fxt.freexmltoolkit.controls.shell.editor.ExportResult.saved(
                            owner.getScene() != null ? owner.getScene().getWindow() : null,
                            I18n.t("AnalysisSupport.what", baseName), file);
                }
            });
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

    /** "1 issue" / "3 issues": the count with the noun in the UI language (English rule for unknown nouns). */
    static String plural(long count, String singular) {
        boolean one = count == 1;
        return switch (singular) {
            case "issue" -> one ? I18n.t("AnalysisSupport.issue-one", count) : I18n.t("AnalysisSupport.issue-many", count);
            case "component" -> one ? I18n.t("AnalysisSupport.component-one", count) : I18n.t("AnalysisSupport.component-many", count);
            case "constraint" -> one ? I18n.t("AnalysisSupport.constraint-one", count) : I18n.t("AnalysisSupport.constraint-many", count);
            case "node" -> one ? I18n.t("AnalysisSupport.node-one", count) : I18n.t("AnalysisSupport.node-many", count);
            case "unused type" -> one ? I18n.t("AnalysisSupport.unused-type-one", count) : I18n.t("AnalysisSupport.unused-type-many", count);
            case "unreachable component" -> one ? I18n.t("AnalysisSupport.unreachable-component-one", count)
                    : I18n.t("AnalysisSupport.unreachable-component-many", count);
            case "error" -> one ? I18n.t("AnalysisSupport.error-one", count) : I18n.t("AnalysisSupport.error-many", count);
            case "warning" -> one ? I18n.t("AnalysisSupport.warning-one", count) : I18n.t("AnalysisSupport.warning-many", count);
            case "info" -> one ? I18n.t("AnalysisSupport.info-one", count) : I18n.t("AnalysisSupport.info-many", count);
            case "suggestion" -> one ? I18n.t("AnalysisSupport.suggestion-one", count) : I18n.t("AnalysisSupport.suggestion-many", count);
            case "key" -> one ? I18n.t("AnalysisSupport.key-one", count) : I18n.t("AnalysisSupport.key-many", count);
            case "keyref" -> one ? I18n.t("AnalysisSupport.keyref-one", count) : I18n.t("AnalysisSupport.keyref-many", count);
            case "unique" -> one ? I18n.t("AnalysisSupport.unique-one", count) : I18n.t("AnalysisSupport.unique-many", count);
            case "assert" -> one ? I18n.t("AnalysisSupport.assert-one", count) : I18n.t("AnalysisSupport.assert-many", count);
            default -> count + " " + singular + (one ? "" : "s");
        };
    }
}
