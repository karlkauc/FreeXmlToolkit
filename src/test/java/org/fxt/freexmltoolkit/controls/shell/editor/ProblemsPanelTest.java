package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the PROBLEMS panel below the editor: it mirrors
 * {@link EditorHost#getActiveProblems()}, shows severity counts, jumps to a
 * problem's line on selection, collapses to its header and hides while empty.
 */
@ExtendWith(ApplicationExtension.class)
class ProblemsPanelTest {

    private EditorHost host;
    private ProblemsPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new ProblemsPanel(host);
        VBox column = new VBox(host, panel);
        VBox.setVgrow(host, Priority.ALWAYS);
        stage.setScene(new Scene(column, 1000, 600));
        stage.show();
    }

    @Test
    void hiddenInitiallyAndShowsWithCountsWhenProblemsArrive() {
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(panel.isVisible(), "panel must be hidden while there are no problems");
        assertFalse(panel.isManaged());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(List.of(
                    new ValidationProblem("XSD", "error", 3, "broken element"),
                    new ValidationProblem("XSD", "error", 5, "another error"),
                    new ValidationProblem("Schematron", "warning", 7, "questionable date")));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(panel.isVisible(), "panel must auto-show when problems arrive");
        assertEquals(3, panel.getProblemCount());
        Label errorChip = (Label) panel.lookup(".fxt-problems-chip-error");
        Label warningChip = (Label) panel.lookup(".fxt-problems-chip-warning");
        assertEquals("2", errorChip.getText());
        assertEquals("1", warningChip.getText());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(List.of());
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(panel.isVisible(), "panel must hide again when the problems clear");
    }

    @Test
    void rowsCarryASourceBadgeAndSchematronDetailsInTheTooltip() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            // The surrounding column gives the EditorHost all vertical space, which
            // squeezes the panel to its (tiny) min height — no ListView cells would
            // materialize. Reserve real height so the VirtualFlow renders rows.
            panel.setMinHeight(240);
            host.setActiveProblems(List.of(
                    new ValidationProblem("XSD", "error", 3, "broken element"),
                    new ValidationProblem("Schematron", "error", 7, "NAV must be positive",
                            "number(Nav) > 0", "/Funds/Fund[1]/Nav")));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Virtualized list cells materialize during layout — poll until the badges
        // of both rows exist instead of asserting immediately.
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () ->
                WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                    var texts = panel.lookupAll(".fxt-problems-source").stream()
                            .map(n -> ((Label) n).getText())
                            .toList();
                    return texts.contains("XSD") && texts.contains("Schematron");
                }));
    }

    @Test
    void detailTooltipIncludesRuleAndContext() {
        var tooltip = ProblemsPanel.detailTooltip(new ValidationProblem(
                "Schematron", "error", 7, "NAV must be positive",
                "number(Nav) > 0", "/Funds/Fund[1]/Nav"));
        assertTrue(tooltip.getText().contains("NAV must be positive"));
        assertTrue(tooltip.getText().contains("Rule / Test: number(Nav) > 0"));
        assertTrue(tooltip.getText().contains("Context: /Funds/Fund[1]/Nav"));

        var plain = ProblemsPanel.detailTooltip(new ValidationProblem("XSD", "error", 3, "broken"));
        assertFalse(plain.getText().contains("Rule / Test"),
                "problems without details must not render empty detail lines");
    }

    @Test
    void selectingAProblemJumpsToItsLine(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root>\n<a/>\n<b/>\n<c/>\n</root>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(List.of(
                    new ValidationProblem("XSD", "error", 3, "broken element")));
            return null;
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            @SuppressWarnings("unchecked")
            var list = (ListView<ValidationProblem>) panel.lookup("#problems-panel-list");
            list.getSelectionModel().select(0);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveCaretLineColumn()[0] == 3);
        assertEquals(3, host.getActiveCaretLineColumn()[0],
                "selecting the problem must move the caret to its line");
    }

    @Test
    void collapseTogglesListVisibilityButKeepsHeader() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(List.of(
                    new ValidationProblem("XSD", "error", 1, "x")));
            panel.setCollapsed(true);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(panel.isCollapsed());
        assertTrue(panel.isVisible(), "collapsed panel keeps its header visible");
        assertFalse(panel.lookup("#problems-panel-list").isVisible());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setCollapsed(false);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(panel.lookup("#problems-panel-list").isVisible());
    }

    @Test
    void replacingProblemsWhileARowIsSelectedDoesNotCrash() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(List.of(
                    new ValidationProblem("XSD", "error", 1, "first"),
                    new ValidationProblem("XSD", "error", 2, "second")));
            return null;
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            @SuppressWarnings("unchecked")
            var list = (ListView<ValidationProblem>) panel.lookup("#problems-panel-list");
            list.getSelectionModel().select(1);
            return null;
        });
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                host.setActiveProblems(List.of(
                        new ValidationProblem("XSD", "error", 1, "replaced")));
                return null;
            }));
            WaitForAsyncUtils.waitForFxEvents();
        }
        assertEquals(1, panel.getProblemCount());
    }

    @Test
    void selectingAProblemJumpsToItsColumn(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root>\n  <a x=\"1\"/>\n</root>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(List.of(new ValidationProblem("XSD", "error", 2, "bad attribute").withColumn(6)));
            return null;
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            @SuppressWarnings("unchecked")
            var list = (ListView<ValidationProblem>) panel.lookup("#problems-panel-list");
            list.getSelectionModel().select(0);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveCaretLineColumn()[0] == 2);
        assertArrayEquals(new int[]{2, 6}, host.getActiveCaretLineColumn(),
                "the caret lands on the reported column, not the line start");
    }

    @Test
    void describeGivesTheFullProblemText() {
        String longMessage = "cvc-complex-type.2.4.a: Invalid content was found starting with element 'x'. "
                + "One of '{\"http://www.w3.org/2001/XMLSchema\":annotation, \"http://www.w3.org/2001/XMLSchema\":simpleType}' is expected.";
        assertEquals("[Error] XSD: " + longMessage + " (order.xsd : 12:5)",
                ProblemsPanel.describe(new ValidationProblem("XSD", "error", 12, longMessage).withColumn(5), "order.xsd"));
        assertEquals("[Warning] Schematron: NAV must be positive (f.xml : 7)\n"
                        + "    Rule / Test: number(Nav) > 0\n    Context: /Funds/Fund[1]/Nav",
                ProblemsPanel.describe(new ValidationProblem("Schematron", "warning", 7, "NAV must be positive",
                        "number(Nav) > 0", "/Funds/Fund[1]/Nav"), "f.xml"));
        assertEquals("[Error] XSD: broken (line 3)",
                ProblemsPanel.describe(new ValidationProblem("XSD", "error", 3, "broken"), ""));
        assertEquals("[Error] XSD: a (d.xsd : 1)\n[Error] XSD: b (d.xsd : 2)",
                ProblemsPanel.describeAll(List.of(new ValidationProblem("XSD", "error", 1, "a"),
                        new ValidationProblem("XSD", "error", 2, "b")), "d.xsd"));
    }

    @Test
    void rowMenuCopiesMessageProblemAndAllProblems() throws Exception {
        ValidationProblem first = new ValidationProblem("XSD", "error", 3, "a very long message that the row cuts off");
        ValidationProblem second = new ValidationProblem("XSD", "warning", 4, "second");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(List.of(first, second));
            return null;
        });
        String[] copied = new String[3];
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            var menu = panel.contextMenu(first);
            var ids = menu.getItems().stream().map(javafx.scene.control.MenuItem::getId).toList();
            assertEquals(List.of("problems-ctx-copy-message", "problems-ctx-copy", "problems-ctx-copy-all"), ids);
            for (int i = 0; i < 3; i++) {
                menu.getItems().get(i).fire();
                copied[i] = javafx.scene.input.Clipboard.getSystemClipboard().getString();
            }
            return null;
        });
        assertEquals("a very long message that the row cuts off", copied[0]);
        assertTrue(copied[1].startsWith("[Error] XSD: a very long message that the row cuts off"), copied[1]);
        assertEquals(2, copied[2].lines().count(), copied[2]);
        assertTrue(copied[2].contains("[Warning] XSD: second"), copied[2]);
    }

    @Test
    void shortcutCopyCopiesTheSelectedProblem() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(List.of(new ValidationProblem("XSD", "error", 0, "selected one")));
            @SuppressWarnings("unchecked")
            var list = (ListView<ValidationProblem>) panel.lookup("#problems-panel-list");
            list.getSelectionModel().select(0);
            list.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "",
                    javafx.scene.input.KeyCode.C, false, !isMac(), false, isMac()));
            return null;
        });
        String copied = WaitForAsyncUtils.asyncFx(() -> javafx.scene.input.Clipboard.getSystemClipboard().getString())
                .get(2, TimeUnit.SECONDS);
        assertEquals("[Error] XSD: selected one", copied);
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }
}
