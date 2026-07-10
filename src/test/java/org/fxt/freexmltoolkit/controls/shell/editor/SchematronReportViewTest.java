package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the detailed Schematron report tool tab: it shows the
 * run metadata and one row per finding (severity, line, message, rule/test,
 * XPath context), and only offers the SVRL export when SVRL is available.
 */
@ExtendWith(ApplicationExtension.class)
class SchematronReportViewTest {

    private static final SchematronReportData DATA = new SchematronReportData(
            "fund.xml", new File("/tmp/rules.sch"),
            List.of(
                    new ValidationProblem("Schematron", "error", 12, "NAV must be positive",
                            "number(Nav) > 0", "/Funds/Fund[1]/Nav"),
                    new ValidationProblem("Schematron", "warning", 20, "Currency should be ISO",
                            "matches(Ccy,'[A-Z]{3}')", "/Funds/Fund[2]/Ccy")),
            "<svrl:schematron-output xmlns:svrl=\"http://purl.oclc.org/dsdl/svrl\"/>");

    private SchematronReportView view;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        view = new SchematronReportView(DATA, null);
        stage.setScene(new Scene(view, 1100, 600));
        stage.show();
    }

    @Test
    void showsAllFindingsWithDetails() {
        assertEquals(2, view.getFindingCount());
        @SuppressWarnings("unchecked")
        TableView<ValidationProblem> table = (TableView<ValidationProblem>)
                WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.lookup("#schematron-report-table"));
        assertNotNull(table);
        assertEquals(5, table.getColumns().size(), "Severity, Line, Message, Rule/Test, Context");
        ValidationProblem first = table.getItems().get(0);
        assertEquals("number(Nav) > 0", first.ruleId());
        assertEquals("/Funds/Fund[1]/Nav", first.context());
    }

    @Test
    void svrlSaveIsOfferedOnlyWhenSvrlExists() {
        Button svrlButton = WaitForAsyncUtils.waitForAsyncFx(2000, () -> findButton(view, "Save SVRL (XML)"));
        assertNotNull(svrlButton);
        assertFalse(svrlButton.isDisabled(), "SVRL export must be enabled when SVRL was captured");

        SchematronReportView withoutSvrl = WaitForAsyncUtils.waitForAsyncFx(2000, () ->
                new SchematronReportView(new SchematronReportData(
                        "fund.xml", new File("/tmp/rules.sch"), List.of(), null), null));
        Button disabled = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> findButton(withoutSvrl, "Save SVRL (XML)"));
        assertTrue(disabled.isDisabled(), "SVRL export must be disabled without SVRL");
    }

    @Test
    void offersHtmlSave() {
        Button htmlButton = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> findButton(view, "Save Report (HTML)"));
        assertNotNull(htmlButton);
        assertFalse(htmlButton.isDisabled());
    }

    /** Depth-first search for a button by its text (runs on the FX thread via callers). */
    private static Button findButton(javafx.scene.Node root, String text) {
        if (root instanceof Button b && text.equals(b.getText())) {
            return b;
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                Button found = findButton(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
