package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.OpenDocument;

/**
 * The "Schema Analysis" tool tab: analyzes the active XSD document off the FX thread
 * ({@link SchemaAnalysisRunner}) and presents the result in four sub-tabs — Statistics,
 * Quality Checks, Identity Constraints and XPath Validation. Findings navigate to the
 * schema node in the Tree view. Opened from the Schema activity's tool strip.
 */
public class SchemaAnalysisView extends BorderPane {

    public static final String TITLE = "Schema Analysis";
    public static final String ICON = "bi-clipboard-data";
    static final String NO_DOCUMENT = "Open an XSD document first.";

    private static final Logger logger = LogManager.getLogger(SchemaAnalysisView.class);

    private final EditorHost editorHost;
    private final Label subtitle = new Label();
    private final Label status = new Label();
    private final Button refresh = new Button("Refresh", AnalysisSupport.icon("bi-arrow-clockwise", 16));
    private final TabPane tabs = new TabPane();
    private final StatisticsSection statistics;
    private final QualitySection quality;
    private final IdentityConstraintsSection constraints;
    private final XPathSection xpath;
    private final StackPane overlay = new StackPane();
    private Future<?> running;
    private SchemaAnalysisData data;

    /** Creates the view and immediately analyzes the host's active XSD document. */
    public SchemaAnalysisView(EditorHost editorHost) {
        this(editorHost, null);
        refresh();
    }

    /** Creates the view showing a prepared result (no analysis is started). */
    public SchemaAnalysisView(SchemaAnalysisData data, EditorHost editorHost) {
        this(editorHost, data);
        setData(data);
    }

    private SchemaAnalysisView(EditorHost editorHost, SchemaAnalysisData initial) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-analysis");
        setPadding(new Insets(12, 16, 12, 16));

        Label title = new Label("SCHEMA ANALYSIS");
        title.getStyleClass().add("fxt-side-panel-title");
        subtitle.getStyleClass().add("fxt-analysis-subtitle");
        VBox titles = new VBox(2, title, subtitle);
        HBox.setHgrow(titles, Priority.ALWAYS);
        refresh.setId("analysis-refresh");
        refresh.getStyleClass().add("fxt-tool-button");
        refresh.setOnAction(e -> refresh());
        status.setId("analysis-status");
        status.getStyleClass().add("fxt-placeholder-text");
        HBox header = new HBox(12, titles, status, refresh);
        header.getStyleClass().add("fxt-analysis-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 10, 0));
        setTop(header);

        statistics = new StatisticsSection(editorHost);
        quality = new QualitySection(editorHost);
        constraints = new IdentityConstraintsSection(editorHost);
        xpath = new XPathSection(editorHost);
        tabs.setId("schema-analysis-tabs");
        tabs.getStyleClass().add("fxt-analysis-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                tab("Statistics", "bi-bar-chart", statistics),
                tab("Quality Checks", "bi-award", quality),
                tab("Identity Constraints", "bi-key", constraints),
                tab("XPath Validation", "bi-check2-circle", xpath));

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(48, 48);
        Label analyzing = new Label("Analyzing…");
        analyzing.getStyleClass().add("fxt-placeholder-text");
        VBox overlayContent = new VBox(8, spinner, analyzing);
        overlayContent.setAlignment(Pos.CENTER);
        overlay.getStyleClass().add("fxt-analysis-overlay");
        overlay.getChildren().add(overlayContent);
        overlay.setVisible(false);
        overlay.setManaged(false);
        setCenter(new StackPane(tabs, overlay));
    }

    private static Tab tab(String text, String icon, Region content) {
        Tab tab = new Tab(text, content);
        tab.setGraphic(AnalysisSupport.icon(icon, 14));
        tab.setClosable(false);
        return tab;
    }

    /** Re-analyzes the host's active XSD document off the FX thread. */
    public void refresh() {
        if (editorHost == null) {
            status.setText(NO_DOCUMENT);
            return;
        }
        Optional<OpenDocument> doc = editorHost.getActiveDocument();
        if (doc.isEmpty() || doc.get().getFileType() != EditorFileType.XSD) {
            status.setText(NO_DOCUMENT);
            return;
        }
        String text = editorHost.getActiveText().orElse("");
        String name = doc.get().getDisplayName();
        Path path = doc.get().getPath();
        cancelRunning();
        showBusy(true);
        status.setText("Analyzing " + name + "…");
        running = FxtGui.executorService.submit(() -> {
            SchemaAnalysisData result = null;
            String outcome;
            try {
                result = SchemaAnalysisRunner.analyze(text, name, path);
                outcome = null;
            } catch (InterruptedException | CancellationException e) {
                outcome = "Cancelled.";
            } catch (Throwable t) {
                logger.warn("Schema analysis failed for {}", name, t);
                outcome = "ERROR: " + (t.getMessage() != null ? t.getMessage() : t.toString());
            }
            SchemaAnalysisData finalResult = result;
            String finalOutcome = outcome;
            Platform.runLater(() -> {
                running = null;
                showBusy(false);
                if (finalResult != null) {
                    setData(finalResult);
                } else {
                    status.setText(finalOutcome);
                    status.getStyleClass().add("fxt-lib-error");
                }
            });
        });
    }

    private void setData(SchemaAnalysisData data) {
        this.data = data;
        statistics.setData(data);
        quality.setData(data);
        constraints.setData(data);
        xpath.setData(data);
        int unused = data.statistics().unusedTypes() == null ? 0 : data.statistics().unusedTypes().size();
        subtitle.setText(data.documentName()
                + " · Score " + data.quality().score() + " / 100 (" + data.quality().getScoreDescription() + ")"
                + " · " + AnalysisSupport.plural(data.quality().issues().size(), "issue")
                + " · " + AnalysisSupport.plural(unused, "unused type"));
        status.getStyleClass().remove("fxt-lib-error");
        status.setText("Analyzed " + data.documentName());
    }

    private void showBusy(boolean busy) {
        overlay.setVisible(busy);
        overlay.setManaged(busy);
        tabs.setDisable(busy);
        refresh.setDisable(busy);
    }

    private void cancelRunning() {
        Future<?> current = running;
        if (current != null) {
            current.cancel(true);
            running = null;
        }
    }

    /** Cancels a running analysis; call when the tool tab closes. */
    public void dispose() {
        cancelRunning();
    }

    /** @return the sub-tab titles in display order (for tests/observers). */
    public List<String> subTabTitles() {
        return tabs.getTabs().stream().map(Tab::getText).toList();
    }

    /** @return the result currently shown, if any. */
    public Optional<SchemaAnalysisData> getData() {
        return Optional.ofNullable(data);
    }

    /** @return the header status text (for tests/observers). */
    public String getStatusText() {
        return status.getText();
    }
}
