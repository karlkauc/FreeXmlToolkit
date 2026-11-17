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

    /** The grouping is a stored setting: every test starts with the list. */
    @org.junit.jupiter.api.AfterEach
    void backToTheList() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setGrouping(null);
            return null;
        });
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
        var errorChip = (javafx.scene.control.Labeled) panel.lookup(".fxt-problems-chip-error");
        var warningChip = (javafx.scene.control.Labeled) panel.lookup(".fxt-problems-chip-warning");
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
    void revealJumpsToTheFirstProblemAndAgainOnEveryRequest(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root>\n<a/>\n<b/>\n<c/>\n</root>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(List.of(
                    new ValidationProblem("XSD", "error", 4, "broken element"),
                    new ValidationProblem("XSD", "error", 5, "another error")));
            host.revealProblems();
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveCaretLineColumn()[0] == 4);
        assertEquals(4, host.getActiveCaretLineColumn()[0], "the first problem is shown in the editor");

        // the caret moves away; a second request (dock already open) shows the problem again
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.goToLine(1);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveCaretLineColumn()[0] == 1);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.revealProblems();
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveCaretLineColumn()[0] == 4);
        assertEquals(4, host.getActiveCaretLineColumn()[0], "every request shows the selected problem");
    }

    /** Selects the first problem of {@code line} in a document opened in {@code mode}. */
    private void openInModeAndSelectProblem(Path xml, ViewMode mode, int line, String message) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveViewMode(mode));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.activeViewModeProperty().get() == mode));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(List.of(new ValidationProblem("XSD", "error", line, message)));
            @SuppressWarnings("unchecked")
            var list = (ListView<ValidationProblem>) panel.lookup("#problems-panel-list");
            list.getSelectionModel().select(0);
            return null;
        });
    }

    @Test
    void inTheTreeTheProblemSelectsItsNodeAndTheViewStays(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root>\n<a/>\n<b/>\n<c/>\n</root>");
        openInModeAndSelectProblem(xml, ViewMode.TREE, 3, "broken element");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.activeXmlNodeProperty().get() instanceof
                        org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement e && "b".equals(e.getName())));
        assertEquals(ViewMode.TREE, WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeViewModeProperty().get()));
    }

    @Test
    void inTheGraphicViewTheProblemSelectsItsGridRowAndTheViewStays(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root>\n<a/>\n<b>x</b>\n<c/>\n</root>");
        openInModeAndSelectProblem(xml, ViewMode.GRAPHIC, 3, "cvc-datatype-valid: 'x' is not valid");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.activeXmlNodeProperty().get() instanceof
                        org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement e && "b".equals(e.getName())));
        assertEquals(ViewMode.GRAPHIC, WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeViewModeProperty().get()));
    }

    @Test
    void inAnXsdTreeTheProblemSelectsTheSchemaNode(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("s.xsd");
        Files.writeString(xsd, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="root">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="first" type="xs:string"/>
                        <xs:element name="second" type="xs:int"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>
                """);
        openInModeAndSelectProblem(xsd, ViewMode.TREE, 6, "s4s-elt-invalid");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.activeSelectedNodeProperty().get() != null
                        && "second".equals(host.activeSelectedNodeProperty().get().getName())));
        assertEquals(ViewMode.TREE, WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeViewModeProperty().get()));
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
        assertFalse(shown(panel.lookup("#problems-panel-list")), "the list is hidden with its container");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setCollapsed(false);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(shown(panel.lookup("#problems-panel-list")));
    }

    /** @return whether the node and all its parents are visible */
    private static boolean shown(javafx.scene.Node node) {
        for (javafx.scene.Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return true;
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
    void aCounterFiltersBySeverityAndASecondClickShowsAllAgain() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(List.of(
                    new ValidationProblem("XSD", "error", 3, "broken element"),
                    new ValidationProblem("XSD", "error", 5, "another error"),
                    new ValidationProblem("Schematron", "warning", 7, "questionable date")));
            return null;
        });
        var errors = (javafx.scene.control.ToggleButton) panel.lookup("#problems-filter-errors");
        var warnings = (javafx.scene.control.ToggleButton) panel.lookup("#problems-filter-warnings");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            assertEquals("Show only errors", errors.getAccessibleText());
            errors.fire();
            assertEquals(ProblemsPanel.Filter.ERRORS, panel.getFilter());
            assertEquals(2, panel.getProblemCount());
            assertTrue(errors.isSelected() && !warnings.isSelected());
            assertEquals("Show all problems", errors.getAccessibleText());
            assertEquals("2", errors.getText(), "the counters keep counting all problems");
            assertEquals("1", warnings.getText());

            warnings.fire();
            assertEquals(1, panel.getProblemCount(), "the other counter switches to its severity");
            assertTrue(warnings.isSelected() && !errors.isSelected());

            warnings.fire();
            assertEquals(ProblemsPanel.Filter.ALL, panel.getFilter());
            assertEquals(3, panel.getProblemCount());
            assertFalse(errors.isSelected() || warnings.isSelected());

            errors.fire();
            // new results without errors: the filter would hide everything, so it goes
            host.setActiveProblems(List.of(new ValidationProblem("XSD", "warning", 2, "only a warning")));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(ProblemsPanel.Filter.ALL, panel.getFilter());
        assertEquals(1, panel.getProblemCount());
        assertFalse(errors.isSelected());
    }

    @Test
    void anOnlineFindingLinksToItsEntry() throws Exception {
        String url = "https://registers.esma.europa.eu/publication/details?core=esma_registers_firds&docId=1016711936";
        ValidationProblem isin = new ValidationProblem("ISIN", "warning", 4,
                "ISIN XS1555094066 (DVB 1 04/25/22 BOND) is no longer traded on any EU trading venue.").withLink(url);
        ValidationProblem xsd = new ValidationProblem("XSD", "error", 3, "broken element");
        List<String> opened = new java.util.ArrayList<>();
        var before = ProblemsPanel.linkOpener;
        ProblemsPanel.linkOpener = opened::add;
        try {
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                panel.setMinHeight(240);
                host.setActiveProblems(List.of(xsd, isin));
                return null;
            });
            WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                    () -> panel.lookupAll("#problems-open-link").size() == 1));
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                var link = (javafx.scene.control.Hyperlink) panel.lookup("#problems-open-link");
                assertEquals("ESMA FIRDS", link.getText());
                assertEquals("Open at ESMA FIRDS", link.getAccessibleText());
                link.fire();
                var menu = panel.contextMenu(isin);
                assertEquals("problems-ctx-open-link", menu.getItems().getFirst().getId());
                assertEquals("Open at ESMA FIRDS", menu.getItems().getFirst().getText());
                menu.getItems().getFirst().fire();
                assertTrue(panel.contextMenu(xsd).getItems().stream().noneMatch(i -> "problems-ctx-open-link".equals(i.getId())));
                return null;
            });
            assertEquals(List.of(url, url), opened);
            assertTrue(ProblemsPanel.describe(isin, "fund.xml").endsWith("\n    Link: " + url));
            assertFalse(ProblemsPanel.describe(xsd, "fund.xml").contains("Link:"));
        } finally {
            ProblemsPanel.linkOpener = before;
        }
        assertEquals("GLEIF", ProblemsPanel.linkService("https://search.gleif.org/#/record/5299009FXTMMFCP00069"));
        assertEquals("OpenFIGI", ProblemsPanel.linkService("https://www.openfigi.com/search#!?simpleSearchString=X"));
        assertEquals("example.org", ProblemsPanel.linkService("https://example.org/a"));
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
            assertEquals(List.of("problems-ctx-copy-message", "problems-ctx-copy", "problems-ctx-copy-all",
                    "problems-ctx-export-shown"), ids);
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

    // ---- categories, hints, grouping, export, collective fix -------------------------------------------

    static final String ISIN = "US0378331005";

    private static ValidationProblem isinProblem(String isin, ProblemCategory category, String severity, int line) {
        return new ValidationProblem("ISIN", severity, line, "ISIN " + isin + " finding", null, null)
                .withSubject(new ProblemSubject(ProblemSubject.Kind.ISIN, isin, "Name of " + isin))
                .withCategory(category);
    }

    private void setProblems(List<ValidationProblem> problems) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            host.setActiveProblems(problems);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private List<ValidationProblem> mixed() {
        List<ValidationProblem> problems = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            problems.add(isinProblem(ISIN, ProblemCategory.ISIN_NOT_TRADED, "info", 10 + i));
        }
        problems.add(isinProblem(ISIN, ProblemCategory.ISIN_CFI, "warning", 100));
        problems.add(isinProblem("IE00B4L5Y983", ProblemCategory.ISIN_UNKNOWN, "warning", 200));
        problems.add(new ValidationProblem("XSD", "error", 3, "cvc-type.3.1.3: wrong value"));
        return problems;
    }

    @Test
    void hintsHaveTheirOwnCounterAndCategoriesAndSearchFilter() {
        setProblems(mixed());
        var hintChip = (javafx.scene.control.Labeled) panel.lookup("#problems-filter-hints");
        assertEquals("50", hintChip.getText());
        assertEquals("2", ((javafx.scene.control.Labeled) panel.lookup("#problems-filter-warnings")).getText(),
                "hints no longer count as warnings");
        assertEquals("1", ((javafx.scene.control.Labeled) panel.lookup("#problems-filter-errors")).getText(),
                "nor as errors");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setFilter(ProblemsPanel.Filter.HINTS);
            return null;
        });
        assertEquals(50, panel.getProblemCount());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setFilter(ProblemsPanel.Filter.ALL);
            panel.setCategoryShown(ProblemCategory.ISIN_NOT_TRADED, false);
            return null;
        });
        assertEquals(3, panel.getProblemCount(), "the hint category hidden");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setCategoryShown(ProblemCategory.ISIN_NOT_TRADED, true);
            panel.setQuery("ie00b4");
            return null;
        });
        assertEquals(1, panel.getProblemCount(), "search finds the security by its ISIN");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setQuery("");
            return null;
        });
    }

    @Test
    void groupedBySecurityAndShownInTheList() {
        setProblems(mixed());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setGrouping(ProblemGroups.By.SUBJECT);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        @SuppressWarnings("unchecked")
        javafx.scene.control.TreeView<Object> tree =
                (javafx.scene.control.TreeView<Object>) panel.lookup("#problems-panel-tree");
        assertTrue(tree.isVisible());
        var groups = WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.getRoot().getChildren().stream()
                .map(javafx.scene.control.TreeItem::getValue).toList());
        assertEquals(3, groups.size(), "two securities and the schema error");
        ProblemGroups.Group apple = (ProblemGroups.Group) groups.stream()
                .filter(g -> g instanceof ProblemGroups.Group gr && gr.subject() != null && ISIN.equals(gr.subject().key()))
                .findFirst().orElseThrow();
        assertEquals(51, apple.count(), "51 occurrences of one security are one group");
        assertEquals(2, apple.kinds().size());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.showInList(apple);
            return null;
        });
        assertNull(panel.getGrouping(), "back to the list");
        assertEquals(51, panel.getProblemCount(), "only the group's problems");
        assertTrue(panel.lookup("#problems-focus").isVisible());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.onClearFocus();
            return null;
        });
        assertEquals(53, panel.getProblemCount());
    }

    @Test
    void theShownProblemsAreExportedToExcel(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        setProblems(mixed());
        java.nio.file.Path target = dir.resolve("out.xlsx");
        java.util.concurrent.CompletableFuture<java.io.File> written = new java.util.concurrent.CompletableFuture<>();
        ProblemsPanel.exportTarget = name -> target.toFile();
        ProblemsPanel.onExported = written::complete;
        try {
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                panel.setFilter(ProblemsPanel.Filter.WARNINGS);
                panel.onExportShown();
                return null;
            });
            java.io.File file = written.get(20, java.util.concurrent.TimeUnit.SECONDS);
            try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(java.nio.file.Files.newInputStream(file.toPath()))) {
                var problems = workbook.getSheet("Problems");
                assertEquals(2, problems.getLastRowNum(), "the two warnings");
                assertEquals("ISIN " + ISIN, problems.getRow(1).getCell(7).getStringCellValue());
                assertTrue(workbook.getSheet("By Object").getLastRowNum() >= 2);
                assertTrue(workbook.getSheet("Summary").getRow(8).getCell(1).getStringCellValue().contains("Warnings")
                        || java.util.stream.IntStream.rangeClosed(0, workbook.getSheet("Summary").getLastRowNum())
                        .mapToObj(r -> workbook.getSheet("Summary").getRow(r))
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(r -> r.getCell(1) != null && r.getCell(1).toString().contains("Warnings")),
                        "the filter is named");
            }
        } finally {
            ProblemsPanel.exportTarget = null;
            ProblemsPanel.onExported = null;
        }
    }

    @Test
    void aCollectiveFixCorrectsTheSecurityInEveryPlace() throws Exception {
        String isin = ISIN;
        StringBuilder xml = new StringBuilder("<Report>\n");
        for (int i = 0; i < 20; i++) {
            xml.append("<Position><ISIN>").append(isin).append("</ISIN><CFI>ESVUFR</CFI></Position>\n");
        }
        xml.append("</Report>\n");
        String firds = "{\"grouped\":{\"isin\":{\"groups\":[{\"doclist\":{\"docs\":[{\"id\":\"1\",\"isin\":\"" + isin
                + "\",\"gnr_full_name\":\"Apple\",\"gnr_cfi_code\":\"DBFTFB\"}]}}]}},"
                + "\"facet_counts\":{\"facet_pivot\":{\"isin,status\":[]}}}";
        var previous = org.fxt.freexmltoolkit.service.finance.isin.Isin.cache();
        var cache = new org.fxt.freexmltoolkit.service.finance.isin.Isin.Cache(null, java.time.Clock.systemUTC(),
                java.time.Duration.ofHours(1), () -> new org.fxt.freexmltoolkit.service.finance.isin.IsinLookup(
                uri -> new org.fxt.freexmltoolkit.service.finance.isin.IsinLookup.Response(200,
                        firds.getBytes(java.nio.charset.StandardCharsets.UTF_8)), null, java.time.Clock.systemUTC(), false, null),
                () -> false);
        cache.lookup(List.of(isin)); // FIRDS knows the CFI code
        org.fxt.freexmltoolkit.service.finance.isin.Isin.setCache(cache);
        ProblemsPanel.fixConfirmer = text -> text.contains("20 values");
        try {
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openGeneratedDocument(xml.toString(), EditorFileType.XML, "r.xml"));
            WaitForAsyncUtils.waitFor(5, java.util.concurrent.TimeUnit.SECONDS,
                    () -> host.getActiveText().map(t -> t.contains("ESVUFR")).orElse(false));
            List<ValidationProblem> problems = new java.util.ArrayList<>();
            for (int i = 0; i < 20; i++) {
                problems.add(isinProblem(isin, ProblemCategory.ISIN_CFI, "warning", 2 + i));
            }
            setProblems(problems);
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                panel.setGrouping(ProblemGroups.By.SUBJECT);
                return null;
            });
            WaitForAsyncUtils.waitForFxEvents();
            var fix = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                @SuppressWarnings("unchecked")
                var tree = (javafx.scene.control.TreeView<Object>) panel.lookup("#problems-panel-tree");
                var menu = panel.groupMenu(tree.getRoot().getChildren().getFirst().getValue());
                return menu.getItems().stream().filter(m -> "problems-group-fix".equals(m.getId())).findFirst().orElseThrow();
            });
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                fix.fire();
                return null;
            });
            WaitForAsyncUtils.waitFor(10, java.util.concurrent.TimeUnit.SECONDS,
                    () -> host.getActiveText().map(t -> !t.contains("ESVUFR")).orElse(false));
            String text = host.getActiveText().orElseThrow();
            assertEquals(20, text.split("<CFI>DBFTFB</CFI>", -1).length - 1, "every place");
            assertEquals(0, WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveProblems().size()),
                    "the fixed findings are gone");
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                host.getActiveCodeArea().undo();
                return null;
            });
            assertTrue(host.getActiveText().orElseThrow().contains("<CFI>ESVUFR</CFI>"), "one undo takes all back");
        } finally {
            org.fxt.freexmltoolkit.service.finance.isin.Isin.setCache(previous);
            ProblemsPanel.fixConfirmer = null;
        }
    }
}
