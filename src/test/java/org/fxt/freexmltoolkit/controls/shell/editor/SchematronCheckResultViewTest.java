package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.SchematronErrorDetector.ErrorSeverity;
import org.fxt.freexmltoolkit.controls.SchematronErrorDetector.ErrorType;
import org.fxt.freexmltoolkit.controls.SchematronErrorDetector.SchematronError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The Schematron check result view lists detected issues in a table with a severity summary. */
@ExtendWith(ApplicationExtension.class)
class SchematronCheckResultViewTest {

    private static final List<SchematronError> ISSUES = List.of(
            new SchematronError(ErrorType.XML_SYNTAX, 2, 1, "Unclosed element", ErrorSeverity.ERROR),
            new SchematronError(ErrorType.STRUCTURAL, 5, 3, "Rule without context", ErrorSeverity.WARNING),
            new SchematronError(ErrorType.BEST_PRACTICE, 7, 1, "Consider adding a title", ErrorSeverity.INFO));

    private SchematronCheckResultView view;

    @Start
    void start(Stage stage) {
        view = new SchematronCheckResultView(ISSUES);
        stage.setScene(new Scene(view, 600, 400));
        stage.show();
    }

    @Test
    void listsAllIssuesWithSummary() {
        int rows = WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.getIssueCount());
        assertEquals(3, rows);
        String summary = WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.getSummaryText());
        assertTrue(summary.contains("1") && summary.toLowerCase().contains("error"), summary);
    }

    @Test
    void emptyIssuesShowsCleanSummary() {
        SchematronCheckResultView clean = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> new SchematronCheckResultView(List.of()));
        assertEquals(0, WaitForAsyncUtils.waitForAsyncFx(2000, clean::getIssueCount));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, clean::getSummaryText).toLowerCase().contains("no issue"));
    }
}
