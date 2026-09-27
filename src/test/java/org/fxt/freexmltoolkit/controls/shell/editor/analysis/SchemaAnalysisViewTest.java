package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the Schema Analysis tool tab: five sub-tabs, the unused-type list,
 * the Types tab, the quality issues table with its text filter, and the identity-constraints table.
 */
@ExtendWith(ApplicationExtension.class)
class SchemaAnalysisViewTest {

    private SchemaAnalysisView view;
    private SchemaAnalysisData data;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        data = SchemaAnalysisRunner.analyze(SchemaAnalysisRunnerTest.XSD, "test.xsd", null);
        view = new SchemaAnalysisView(data, null);
        stage.setScene(new Scene(view, 1100, 700));
        stage.show();
    }

    @Test
    void showsFiveSubTabsAndTheReportExportMenu() {
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(List.of("Statistics", "Types", "Quality Checks", "Identity Constraints", "XPath Validation"),
                view.subTabTitles());
        assertTrue(view.getData().isPresent());
        assertTrue(view.getStatusText().contains("test.xsd"), view.getStatusText());
        MenuButton export = (MenuButton) view.lookup("#analysis-export-schema-analysis-report");
        assertNotNull(export, "full report export in the header");
        assertEquals(5, export.getItems().size(), "one item per report format");
        assertNotNull(view.lookup("#analysis-export-identity-constraints"), "constraints export");
        assertNotNull(view.lookup("#analysis-export-xpath-validation"), "xpath export");
    }

    @Test
    @SuppressWarnings("unchecked")
    void typesTabListsComponentsFiltersByChipAndKeepsRemoveDisabledWithoutHost() throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        TableView<?> table = (TableView<?>) view.lookup("#analysis-types-table");
        assertNotNull(table);
        assertEquals(2, table.getItems().size(), "PersonType + OrphanType");
        Label count = (Label) view.lookup("#analysis-types-count");
        assertEquals("2 components", count.getText());
        Button remove = (Button) view.lookup("#analysis-types-remove-unused");
        assertTrue(remove.isDisabled(), "no editor host → nothing to remove into");
        assertEquals("Remove unused (1)…", remove.getText());

        FlowPane chips = (FlowPane) view.lookup("#analysis-types-chips");
        Label unusedChip = chips.getChildren().stream().map(Label.class::cast)
                .filter(c -> c.getText().startsWith(TypesSection.UNUSED)).findFirst().orElseThrow();
        assertEquals("Unused 1", unusedChip.getText());
        WaitForAsyncUtils.asyncFx(() -> unusedChip.getOnMouseClicked().handle(null));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> table.getItems().size() == 1);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("Showing 1 of 2 components", count.getText());

        // selecting the used type fills the "used in" list with the referring element
        WaitForAsyncUtils.asyncFx(() -> unusedChip.getOnMouseClicked().handle(null));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> table.getItems().size() == 2);
        TextField search = (TextField) view.lookup("#analysis-types-search");
        WaitForAsyncUtils.asyncFx(() -> search.setText("person"));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> table.getItems().size() == 1);
        WaitForAsyncUtils.asyncFx(() -> table.getSelectionModel().select(0));
        ListView<?> usages = (ListView<?>) view.lookup("#analysis-types-usages");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> usages.getItems().size() == 1);
        WaitForAsyncUtils.asyncFx(() -> search.setText(""));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> table.getItems().size() == 2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void statisticsShowsGroupsCyclesAndComplexityCards() {
        WaitForAsyncUtils.waitForFxEvents();
        ListView<String> groups = (ListView<String>) view.lookup("#analysis-unused-groups");
        assertNotNull(groups);
        assertTrue(groups.getItems().isEmpty(), "the test schema declares no groups");
        ListView<String> cycles = (ListView<String>) view.lookup("#analysis-cycles");
        assertNotNull(cycles);
        assertTrue(cycles.getItems().isEmpty());
        assertNotNull(view.lookup("#analysis-references"), "schema references table exists (hidden when empty)");
        assertFalse(view.lookup("#analysis-references").getParent().isVisible());
        FlowPane naming = (FlowPane) view.lookup("#analysis-quality-naming");
        assertNotNull(naming);
        assertFalse(naming.getChildren().isEmpty(), "naming convention chips");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsUnusedTypesByName() {
        WaitForAsyncUtils.waitForFxEvents();
        ListView<String> unused = (ListView<String>) view.lookup("#analysis-unused-types");
        assertNotNull(unused);
        assertEquals(List.of("OrphanType"), unused.getItems());
    }

    @Test
    void statisticsShowsKpiTilesAndCoverageBar() {
        WaitForAsyncUtils.waitForFxEvents();
        HBox kpis = (HBox) view.lookup("#analysis-kpis");
        assertNotNull(kpis);
        assertEquals(6, kpis.getChildren().size(), "one KPI tile per declaration kind");
        ProgressBar coverage = (ProgressBar) view.lookup("#analysis-coverage-bar");
        assertNotNull(coverage);
        assertEquals(data.statistics().documentationCoveragePercent() / 100.0, coverage.getProgress(), 1e-6);
        assertTrue(coverage.getStyleClass().stream().anyMatch(c -> c.startsWith("fxt-analysis-bar-")));
        assertNotNull(view.lookup("#analysis-top-types"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void qualityTableShowsAllIssuesAndFiltersByText() throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        TableView<?> table = (TableView<?>) view.lookup("#analysis-quality-table");
        assertNotNull(table);
        assertEquals(data.quality().issues().size(), table.getItems().size());

        TextField search = (TextField) view.lookup("#analysis-quality-search");
        WaitForAsyncUtils.asyncFx(() -> search.setText("zzz-no-such-issue"));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> table.getItems().isEmpty());

        WaitForAsyncUtils.asyncFx(() -> search.setText(""));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> table.getItems().size() == data.quality().issues().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void severityChipTogglesTheSeverityFilter() throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        TableView<?> table = (TableView<?>) view.lookup("#analysis-quality-table");
        ComboBox<String> severity = (ComboBox<String>) view.lookup("#analysis-quality-severity");
        FlowPane chips = (FlowPane) view.lookup("#analysis-quality-severity-chips");
        Label count = (Label) view.lookup("#analysis-quality-count");
        assertNotNull(chips);
        assertFalse(chips.getChildren().isEmpty(), "the test schema produces issues");
        int total = data.quality().issues().size();
        assertEquals(total + " issues", count.getText());

        Label chip = (Label) chips.getChildren().getFirst();
        String wanted = chip.getText().replaceAll("^\\d+ ", "").replaceAll("s$", "");
        long expected = data.quality().issues().stream()
                .filter(i -> AnalysisSupport.titleCase(i.severity()).equalsIgnoreCase(wanted)).count();

        WaitForAsyncUtils.asyncFx(() -> chip.getOnMouseClicked().handle(null));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> !QualitySection.ALL.equals(severity.getValue()));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> table.getItems().size() == expected);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(chip.getStyleClass().contains("fxt-analysis-chip-active"));
        assertEquals("Showing " + expected + " of " + total + " issues", count.getText());

        WaitForAsyncUtils.asyncFx(() -> chip.getOnMouseClicked().handle(null));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> QualitySection.ALL.equals(severity.getValue()));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> table.getItems().size() == total);
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(chip.getStyleClass().contains("fxt-analysis-chip-active"));
    }

    @Test
    void constraintsTableListsKeyAndKeyRef() {
        WaitForAsyncUtils.waitForFxEvents();
        TableView<?> table = (TableView<?>) view.lookup("#analysis-constraints-table");
        assertNotNull(table);
        assertEquals(2, table.getItems().size());
        assertNotNull(view.lookup("#analysis-xpath-table"));
    }

    @Test
    void xpathTableListsEveryExpressionAndFiltersByStatus() throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        TableView<?> table = (TableView<?>) view.lookup("#analysis-xpath-table");
        FlowPane chips = (FlowPane) view.lookup("#analysis-xpath-chips");
        Label count = (Label) view.lookup("#analysis-xpath-count");
        // key: selector + field, keyref: selector + field — all valid in the test schema
        assertEquals(4, table.getItems().size());
        assertEquals("4 expressions", count.getText());
        assertEquals(1, chips.getChildren().size(), "only a 'valid' chip when nothing is wrong");
        Label valid = (Label) chips.getChildren().getFirst();
        assertEquals("4 valid", valid.getText());

        WaitForAsyncUtils.asyncFx(() -> valid.getOnMouseClicked().handle(null));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> valid.getStyleClass().contains("fxt-analysis-chip-active"));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(4, table.getItems().size());
        assertEquals("4 expressions", count.getText());
    }

    @Test
    void constraintTypeChipFiltersTheTable() throws Exception {
        WaitForAsyncUtils.waitForFxEvents();
        TableView<?> table = (TableView<?>) view.lookup("#analysis-constraints-table");
        FlowPane chips = (FlowPane) view.lookup("#analysis-constraints-type-chips");
        Label count = (Label) view.lookup("#analysis-constraints-count");
        assertEquals(2, chips.getChildren().size(), "one chip per constraint kind present (key, keyref)");
        assertEquals("2 constraints", count.getText());

        Label keyrefChip = (Label) chips.getChildren().get(1);
        assertEquals("1 keyref", keyrefChip.getText());
        WaitForAsyncUtils.asyncFx(() -> keyrefChip.getOnMouseClicked().handle(null));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> table.getItems().size() == 1);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(keyrefChip.getStyleClass().contains("fxt-analysis-chip-active"));
        assertEquals("Showing 1 of 2 constraints", count.getText());

        WaitForAsyncUtils.asyncFx(() -> keyrefChip.getOnMouseClicked().handle(null));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> table.getItems().size() == 2);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("2 constraints", count.getText());
    }

    @Test
    void withoutEditorHostRefreshReportsMissingDocument() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.refresh();
            return null;
        });
        assertEquals("Open an XSD document first.", view.getStatusText());
    }
}
