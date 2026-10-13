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

package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.shell.FxmlColumns;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.service.ExecutionStats;
import org.fxt.freexmltoolkit.service.ExecutionStatsService;
import org.fxt.freexmltoolkit.util.FormattingUtils;

/**
 * Developer tool tab: history of recorded technical operations (XSLT, XQuery, XPath,
 * validation, XProc, FOP) with their resource consumption, a per-run detail report,
 * and CSV/JSON export for offline analysis (e.g. sizing server/cloud offloading).
 *
 * <p>Lives as a singleton tool tab (see {@code EditorHost.openExecutionStats()}); rows
 * arrive live via an {@link ExecutionStatsService} listener. Call {@link #dispose()}
 * when the tab closes.</p>
 */
public class ExecutionStatsView extends VBox {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML private TableView<ExecutionStats> table;
    @FXML private TableColumn<ExecutionStats, String> timeColumn;
    @FXML private TableColumn<ExecutionStats, String> operationColumn;
    @FXML private TableColumn<ExecutionStats, String> targetColumn;
    @FXML private TableColumn<ExecutionStats, String> durationColumn;
    @FXML private TableColumn<ExecutionStats, String> cpuColumn;
    @FXML private TableColumn<ExecutionStats, String> memoryColumn;
    @FXML private TableColumn<ExecutionStats, String> gcColumn;
    @FXML private TableColumn<ExecutionStats, String> inColumn;
    @FXML private TableColumn<ExecutionStats, String> outColumn;
    @FXML private TableColumn<ExecutionStats, String> statusColumn;
    @FXML private TextArea detail;
    private final Consumer<ExecutionStats> listener =
            stats -> Platform.runLater(() -> table.getItems().add(0, stats));

    /** Loads the layout from {@code /pages/views/ExecutionStatsView.fxml} and binds the columns. */
    public ExecutionStatsView() {
        FxmlViews.load(this, "/pages/views/ExecutionStatsView.fxml");

        FxmlColumns.text(timeColumn, s -> s.startedAt() != null ? TIME.format(s.startedAt()) : "");
        FxmlColumns.text(operationColumn, s -> s.type().name());
        FxmlColumns.text(targetColumn, ExecutionStats::target);
        FxmlColumns.text(durationColumn, s -> ExecutionStats.formatMillis(s.wallMillis()));
        FxmlColumns.text(cpuColumn, s -> s.cpuMillis() >= 0 ? ExecutionStats.formatMillis(s.cpuMillis()) : "-");
        FxmlColumns.text(memoryColumn, s -> formatSignedBytes(s.heapDeltaBytes()));
        FxmlColumns.text(gcColumn, s -> s.gcCount() > 0 ? s.gcCount() + " (" + s.gcTimeMillis() + " ms)" : "-");
        FxmlColumns.text(inColumn, s -> s.inputChars() >= 0 ? FormattingUtils.formatFileSize(s.inputChars()) : "-");
        FxmlColumns.text(outColumn, s -> s.outputChars() >= 0 ? FormattingUtils.formatFileSize(s.outputChars()) : "-");
        FxmlColumns.text(statusColumn, s -> s.success() ? "ok" : "error");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, selected) -> detail.setText(selected != null ? buildReportText(selected) : ""));

        table.getItems().setAll(ExecutionStatsService.getInstance().snapshot());
        ExecutionStatsService.getInstance().addListener(listener);
    }

    /** Unregisters the live-update listener; call when the hosting tab closes. */
    public void dispose() {
        ExecutionStatsService.getInstance().removeListener(listener);
    }

    @FXML
    public void onExportCsv() {
        export("csv");
    }

    @FXML
    public void onExportJson() {
        export("json");
    }

    @FXML
    public void onClear() {
        ExecutionStatsService.getInstance().clear();
        table.getItems().clear();
        detail.clear();
    }

    private void export(String format) {
        List<ExecutionStats> stats = List.copyOf(table.getItems());
        if (stats.isEmpty()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Execution Statistics");
        chooser.setInitialFileName("execution-statistics." + format);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                format.toUpperCase() + " files", "*." + format));
        java.io.File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) {
            return;
        }
        try {
            String content = "csv".equals(format)
                    ? ExecutionStatsService.toCsv(stats)
                    : ExecutionStatsService.toJson(stats);
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            org.fxt.freexmltoolkit.util.DialogHelper.showActionError("Export failed",
                    "The statistics could not be written to " + file.getName() + ".",
                    org.fxt.freexmltoolkit.util.DialogHelper.Remedies.EXPORT, e);
        }
    }

    /** Full-detail plain-text report for one run (shown in the detail area). */
    static String buildReportText(ExecutionStats s) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(s.type()).append(" · ").append(s.target()).append(" ===\n");
        if (s.startedAt() != null) {
            sb.append("Started:      ").append(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(s.startedAt())).append('\n');
        }
        sb.append("Status:       ").append(s.success() ? "success" : "error — " + s.errorSummary()).append('\n');
        sb.append("Wall time:    ").append(s.wallMillis()).append(" ms\n");
        sb.append("CPU time:     ").append(s.cpuMillis() >= 0 ? s.cpuMillis() + " ms" : "unsupported").append('\n');
        sb.append("Heap before:  ").append(FormattingUtils.formatFileSize(s.heapBeforeBytes())).append('\n');
        sb.append("Heap delta:   ").append(formatSignedBytes(s.heapDeltaBytes())).append(" (approximate)\n");
        sb.append("GC activity:  ").append(s.gcCount()).append(" collection(s), ")
                .append(s.gcTimeMillis()).append(" ms\n");
        sb.append("Input size:   ").append(s.inputChars() >= 0
                ? FormattingUtils.formatFileSize(s.inputChars()) : "unknown").append('\n');
        sb.append("Output size:  ").append(s.outputChars() >= 0
                ? FormattingUtils.formatFileSize(s.outputChars()) : "unknown").append('\n');
        if (!s.phaseMillis().isEmpty()) {
            sb.append("Phases:\n");
            s.phaseMillis().forEach((name, ms) ->
                    sb.append("  ").append(name).append(": ").append(ms).append(" ms\n"));
        }
        if (s.wallMillis() > 0 && s.inputChars() > 0) {
            double kbPerSec = (s.inputChars() / 1024.0) / (s.wallMillis() / 1000.0);
            sb.append("Throughput:   ").append(String.format(java.util.Locale.US, "%.1f KB/s", kbPerSec)).append('\n');
        }
        return sb.toString();
    }

    private static String formatSignedBytes(long bytes) {
        if (bytes < 0) {
            return "-" + FormattingUtils.formatFileSize(-bytes);
        }
        return FormattingUtils.formatFileSize(bytes);
    }

    // ----- test/observer accessors ---

    public int getRowCount() {
        return table.getItems().size();
    }

    public String getDetailText() {
        return detail.getText();
    }

    /** Selects the given row index (for tests). */
    public void selectRow(int index) {
        table.getSelectionModel().select(index);
    }
}
