package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the Schema Analysis tool tab: four sub-tabs, the unused-type list,
 * the quality issues table with its text filter, and the identity-constraints table.
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
    void showsFourSubTabs() {
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(List.of("Statistics", "Quality Checks", "Identity Constraints", "XPath Validation"),
                view.subTabTitles());
        assertTrue(view.getData().isPresent());
        assertTrue(view.getStatusText().contains("test.xsd"), view.getStatusText());
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
    void constraintsTableListsKeyAndKeyRef() {
        WaitForAsyncUtils.waitForFxEvents();
        TableView<?> table = (TableView<?>) view.lookup("#analysis-constraints-table");
        assertNotNull(table);
        assertEquals(2, table.getItems().size());
        assertNotNull(view.lookup("#analysis-xpath-table"));
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
