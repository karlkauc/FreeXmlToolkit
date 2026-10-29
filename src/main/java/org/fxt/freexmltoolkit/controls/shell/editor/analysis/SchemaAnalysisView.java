package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.OpenDocument;

/**
 * The "Schema Analysis" tool tab: analyzes the active XSD document off the FX thread
 * ({@link SchemaAnalysisRunner}) and presents the result in five sub-tabs — Statistics,
 * Types, Quality Checks, Identity Constraints and XPath Validation. Findings navigate to the
 * schema node in the Tree view; the header exports the full report in every format. Opened
 * from the Schema activity's tool strip.
 */
public class SchemaAnalysisView extends BorderPane {

    public static final String TITLE = "Schema Analysis";
    public static final String ICON = "bi-clipboard-data";
    static final String NO_DOCUMENT = "Open an XSD document first.";

    private static final Logger logger = LogManager.getLogger(SchemaAnalysisView.class);

    private final EditorHost editorHost;
    @FXML private HBox header;
    @FXML private Label subtitle;
    @FXML private Label status;
    @FXML private Button refresh;
    @FXML private TabPane tabs;
    @FXML private Tab statisticsTab;
    @FXML private Tab typesTab;
    @FXML private Tab qualityTab;
    @FXML private Tab constraintsTab;
    @FXML private Tab xpathTab;
    private final StatisticsSection statistics;
    private final TypesSection types;
    private final QualitySection quality;
    private final IdentityConstraintsSection constraints;
    private final XPathSection xpath;
    @FXML private StackPane overlay;
    private Future<?> running;
    private SchemaAnalysisData data;
    /** The document analyzed last (refresh target while this tool tab is the active one). */
    private OpenDocument document;

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
        FxmlViews.load(this, "/pages/views/SchemaAnalysisView.fxml");

        // Export sits between the status and Refresh.
        header.getChildren().add(header.getChildren().indexOf(refresh), AnalysisSupport.reportMenu(
                "Schema Analysis Report", status, () -> data == null ? null : SchemaAnalysisReport.full(data)));

        statistics = new StatisticsSection(editorHost);
        types = new TypesSection(editorHost);
        quality = new QualitySection(editorHost);
        quality.setOnNestingLimitChanged(this::refresh);
        constraints = new IdentityConstraintsSection(editorHost);
        xpath = new XPathSection(editorHost);
        statisticsTab.setContent(statistics);
        typesTab.setContent(types);
        qualityTab.setContent(quality);
        constraintsTab.setContent(constraints);
        xpathTab.setContent(xpath);
    }

    @FXML
    public void onRefresh() {
        refresh();
    }

    /**
     * Re-analyzes an XSD document off the FX thread: the host's active document if it is an
     * XSD, else the document analyzed last (if still open), else any open XSD. The fallbacks
     * matter because this tool tab is itself the active tab whenever its Refresh button is
     * clicked.
     */
    public void refresh() {
        if (editorHost == null) {
            status.setText(NO_DOCUMENT);
            return;
        }
        Optional<OpenDocument> doc = editorHost.getActiveDocument().filter(SchemaAnalysisView::isXsd);
        if (doc.isEmpty() && document != null && editorHost.getOpenDocuments().contains(document)) {
            doc = Optional.of(document);
        }
        if (doc.isEmpty()) {
            doc = editorHost.getOpenDocuments().stream().filter(SchemaAnalysisView::isXsd).findFirst();
        }
        if (doc.isEmpty()) {
            status.setText(NO_DOCUMENT);
            return;
        }
        refresh(doc.get());
    }

    /** Analyzes {@code doc} (which must be open in the host) off the FX thread. */
    public void refresh(OpenDocument doc) {
        if (editorHost == null || doc == null || !isXsd(doc)) {
            status.setText(NO_DOCUMENT);
            return;
        }
        Optional<String> documentText = editorHost.getDocumentText(doc);
        if (documentText.isEmpty()) {
            status.setText(NO_DOCUMENT);
            return;
        }
        document = doc;
        String text = documentText.get();
        String name = doc.getDisplayName();
        Path path = doc.getPath();
        int nestingLimit = quality.nestingLimit();
        cancelRunning();
        showBusy(true);
        status.setText("Analyzing " + name + "…");
        running = FxtGui.executorService.submit(() -> {
            SchemaAnalysisData result = null;
            String outcome;
            long t0 = System.nanoTime();
            boolean cancelled = false;
            org.fxt.freexmltoolkit.service.telemetry.QueryFailure failure = null;
            try {
                result = SchemaAnalysisRunner.analyze(text, name, path, nestingLimit);
                outcome = null;
            } catch (InterruptedException | CancellationException e) {
                outcome = "Cancelled.";
                cancelled = true;
            } catch (Throwable t) {
                // classified first (a schema that does not parse is invalid_input), then logged
                failure = org.fxt.freexmltoolkit.service.telemetry.QueryFailure.of(t);
                logger.warn("Schema analysis failed for {}", name, t);
                outcome = "ERROR: " + (t.getMessage() != null ? t.getMessage() : t.toString());
            }
            long inputChars = text != null ? text.length() : -1;
            if (cancelled) {
                org.fxt.freexmltoolkit.service.telemetry.UsageEvents.schemaAnalysisCancelled(inputChars, t0);
            } else {
                org.fxt.freexmltoolkit.service.telemetry.UsageEvents.schemaAnalyzed(inputChars, t0, failure);
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
        types.setData(data);
        types.setDocument(document, () -> {
            if (document != null) {
                refresh(document);
            }
        });
        quality.setData(data);
        constraints.setData(data);
        xpath.setData(data);
        int unused = data.statistics().unusedTypes() == null ? 0 : data.statistics().unusedTypes().size();
        int unreachable = data.unreachableComponents().size();
        subtitle.setText(data.documentName()
                + " · Score " + data.quality().score() + " / 100 (" + data.quality().getScoreDescription() + ")"
                + " · " + AnalysisSupport.plural(data.quality().issues().size(), "issue")
                + " · " + AnalysisSupport.plural(unused, "unused type")
                + (unreachable > unused ? " · " + unreachable + " unreachable components" : ""));
        status.getStyleClass().remove("fxt-lib-error");
        status.setText("Analyzed " + data.documentName());
    }

    private static boolean isXsd(OpenDocument doc) {
        return doc != null && doc.getFileType() == EditorFileType.XSD;
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
