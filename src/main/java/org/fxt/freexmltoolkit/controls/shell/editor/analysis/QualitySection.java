package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker.IssueCategory;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker.IssueSeverity;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker.NamingConvention;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker.QualityIssue;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker.QualityResult;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityExporter;

/**
 * "Quality Checks" sub-tab: score tile, severity counts, a filterable issues table (severity /
 * category / text) and a details pane. Selecting an issue reveals its node in the Tree view;
 * affected elements are links to the corresponding global declarations. Exports via
 * {@link XsdQualityExporter}.
 */
final class QualitySection extends VBox {

    private static final String ALL = "All";

    private final EditorHost editorHost;
    private final Label status = new Label();
    private final Label scoreNumber = new Label("–");
    private final Label scoreDescription = new Label();
    private final Label scoreChecks = new Label();
    private final Label scoreNaming = new Label();
    private final VBox scoreTile = new VBox(2, scoreNumber, scoreDescription, scoreChecks, scoreNaming);
    private final FlowPane chips = new FlowPane(6, 6);
    private final ComboBox<String> severityFilter = new ComboBox<>();
    private final ComboBox<String> categoryFilter = new ComboBox<>();
    private final TextField search = new TextField();
    private final ObservableList<QualityIssue> issues = FXCollections.observableArrayList();
    private final FilteredList<QualityIssue> filtered = new FilteredList<>(issues);
    private final TableView<QualityIssue> table = new TableView<>();
    private final VBox details = new VBox(4);
    private QualityResult result;

    QualitySection(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-analysis-section");
        setSpacing(10);

        status.getStyleClass().add("fxt-placeholder-text");
        HBox toolbar = new HBox(8, AnalysisSupport.exportMenu("Schema Quality", status, this::export), status);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        scoreTile.getStyleClass().add("fxt-analysis-score");
        scoreTile.setAlignment(Pos.CENTER);
        scoreNumber.getStyleClass().add("fxt-analysis-score-number");
        scoreDescription.getStyleClass().add("fxt-analysis-score-description");
        scoreChecks.getStyleClass().add("fxt-analysis-key");
        scoreNaming.getStyleClass().add("fxt-analysis-key");
        chips.setAlignment(Pos.CENTER_LEFT);
        VBox summary = new VBox(8, AnalysisSupport.groupTitle("Issues by severity"), chips);
        summary.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(summary, Priority.ALWAYS);
        HBox header = new HBox(16, scoreTile, summary);
        header.setAlignment(Pos.CENTER_LEFT);

        severityFilter.setId("analysis-quality-severity");
        severityFilter.getItems().add(ALL);
        for (IssueSeverity s : IssueSeverity.values()) {
            severityFilter.getItems().add(AnalysisSupport.titleCase(s));
        }
        severityFilter.setValue(ALL);
        categoryFilter.setId("analysis-quality-category");
        categoryFilter.getItems().add(ALL);
        for (IssueCategory c : IssueCategory.values()) {
            categoryFilter.getItems().add(AnalysisSupport.titleCase(c));
        }
        categoryFilter.setValue(ALL);
        search.setId("analysis-quality-search");
        search.setPromptText("Search issues…");
        search.setPrefWidth(220);
        Button clear = new Button("Clear", AnalysisSupport.icon("bi-x-circle", 14));
        clear.getStyleClass().add("fxt-tool-button");
        clear.setOnAction(e -> {
            severityFilter.setValue(ALL);
            categoryFilter.setValue(ALL);
            search.clear();
        });
        severityFilter.valueProperty().addListener((obs, o, n) -> applyFilter());
        categoryFilter.valueProperty().addListener((obs, o, n) -> applyFilter());
        search.textProperty().addListener((obs, o, n) -> applyFilter());
        Label filterIcon = new Label(null, AnalysisSupport.icon("bi-funnel", 14));
        HBox filterBar = new HBox(8, filterIcon, severityFilter, categoryFilter, search, clear);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        table.setId("analysis-quality-table");
        table.getColumns().add(AnalysisSupport.column("Severity", i -> AnalysisSupport.titleCase(i.severity()), 90));
        table.getColumns().add(AnalysisSupport.column("Category", i -> AnalysisSupport.titleCase(i.category()), 170));
        table.getColumns().add(AnalysisSupport.column("Message", QualityIssue::message, 420));
        table.getColumns().add(AnalysisSupport.column("Location", QualitySection::location, -1));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(AnalysisSupport.emptyLabel("No quality issues found."));
        SortedList<QualityIssue> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            showDetails(n);
            if (n != null) {
                reveal(n);
            }
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        details.getStyleClass().add("fxt-analysis-details");
        details.setMinHeight(90);

        getChildren().addAll(toolbar, header, filterBar, table, details);
    }

    void setData(SchemaAnalysisData data) {
        result = data.quality();
        int score = result.score();
        scoreNumber.setText(score + " / 100");
        scoreDescription.setText(result.getScoreDescription());
        scoreChecks.setText(result.passedChecks() + " of " + result.totalChecks() + " checks passed");
        NamingConvention naming = result.dominantNamingConvention();
        scoreNaming.setText("Naming: " + (naming == null ? "–" : naming.getDisplayName()));
        scoreTile.getStyleClass().removeAll("fxt-analysis-score-good", "fxt-analysis-score-fair", "fxt-analysis-score-poor");
        scoreTile.getStyleClass().add(score >= 75 ? "fxt-analysis-score-good"
                : score >= 60 ? "fxt-analysis-score-fair" : "fxt-analysis-score-poor");

        chips.getChildren().clear();
        Map<IssueSeverity, Long> counts = new java.util.EnumMap<>(IssueSeverity.class);
        for (QualityIssue issue : result.issues()) {
            counts.merge(issue.severity(), 1L, Long::sum);
        }
        if (result.issues().isEmpty()) {
            chips.getChildren().add(AnalysisSupport.chip("No issues", "ok"));
        }
        for (IssueSeverity s : IssueSeverity.values()) {
            long n = counts.getOrDefault(s, 0L);
            if (n > 0) {
                chips.getChildren().add(AnalysisSupport.chip(
                        AnalysisSupport.plural(n, AnalysisSupport.titleCase(s).toLowerCase(Locale.ROOT)),
                        s.name().toLowerCase(Locale.ROOT)));
            }
        }
        issues.setAll(result.issues());
        showDetails(null);
        status.setText("");
    }

    private void applyFilter() {
        String sev = severityFilter.getValue();
        String cat = categoryFilter.getValue();
        String text = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        filtered.setPredicate(issue -> {
            if (sev != null && !ALL.equals(sev) && !sev.equals(AnalysisSupport.titleCase(issue.severity()))) {
                return false;
            }
            if (cat != null && !ALL.equals(cat) && !cat.equals(AnalysisSupport.titleCase(issue.category()))) {
                return false;
            }
            if (text.isEmpty()) {
                return true;
            }
            return contains(issue.message(), text) || contains(issue.suggestion(), text)
                    || contains(issue.xpath(), text)
                    || (issue.affectedElements() != null
                        && issue.affectedElements().stream().anyMatch(a -> contains(a, text)));
        });
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private void showDetails(QualityIssue issue) {
        details.getChildren().clear();
        if (issue == null) {
            details.getChildren().add(AnalysisSupport.emptyLabel("Select an issue to see its suggestion and affected elements."));
            return;
        }
        Label message = new Label(issue.message());
        message.setWrapText(true);
        message.getStyleClass().add("fxt-analysis-value");
        details.getChildren().add(message);
        if (issue.suggestion() != null && !issue.suggestion().isBlank()) {
            Label suggestion = new Label("Suggestion: " + issue.suggestion());
            suggestion.setWrapText(true);
            suggestion.getStyleClass().add("fxt-analysis-key");
            details.getChildren().add(suggestion);
        }
        if (issue.affectedElements() != null && !issue.affectedElements().isEmpty()) {
            FlowPane links = new FlowPane(6, 2);
            links.getChildren().add(new Label("Affected: "));
            for (String name : issue.affectedElements()) {
                Hyperlink link = new Hyperlink(name);
                link.setOnAction(e -> {
                    if (editorHost != null) {
                        editorHost.revealTypeByName(name);
                    }
                });
                links.getChildren().add(link);
            }
            details.getChildren().add(links);
        }
    }

    private void reveal(QualityIssue issue) {
        if (editorHost == null) {
            return;
        }
        boolean found = issue.xpath() != null && editorHost.revealSchemaNodeByXPath(issue.xpath());
        if (!found && issue.affectedElements() != null && !issue.affectedElements().isEmpty()) {
            editorHost.revealTypeByName(issue.affectedElements().getFirst());
        }
    }

    private static String location(QualityIssue issue) {
        if (issue.xpath() != null && !issue.xpath().isBlank()) {
            return issue.xpath();
        }
        if (issue.affectedElements() != null && !issue.affectedElements().isEmpty()) {
            return String.join(", ", issue.affectedElements());
        }
        return issue.getSourceFileName();
    }

    private void export(AnalysisSupport.ExportFormat format, Path target) throws Exception {
        XsdQualityExporter exporter = new XsdQualityExporter();
        switch (format) {
            case CSV -> exporter.exportToCsv(result, target);
            case JSON -> exporter.exportToJson(result, target);
            case HTML -> exporter.exportToHtml(result, target);
            case PDF -> exporter.exportToPdf(result, target);
            case EXCEL -> exporter.exportToExcel(result, target);
        }
    }

    /** @return the checker result currently shown (for tests/observers). */
    XsdQualityChecker.QualityResult resultForTest() {
        return result;
    }
}
