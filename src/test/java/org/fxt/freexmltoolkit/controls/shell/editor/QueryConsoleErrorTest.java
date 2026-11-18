package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.query.QueryError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of how the {@link QueryConsole} shows a failed query: the error view with code,
 * position, excerpt and hint in place of the results, the mark in the input, the jump to the position, a
 * "Did you mean" correction, the check while typing and the hint for an empty result on a namespaced
 * document.
 */
@ExtendWith(ApplicationExtension.class)
class QueryConsoleErrorTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        host = new EditorHost();
    }

    private QueryConsole open(Path tmp, String xml) throws Exception {
        Path file = tmp.resolve("doc.xml");
        Files.writeString(file, xml);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("Fund")).orElse(false));
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> new QueryConsole(host));
    }

    private static void run(QueryConsole console, String xpath) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath(xpath);
            console.runForTest();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> {
            String r = console.getResultsText();
            return r != null && !"Running…".equals(r) && !r.isEmpty();
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static CodeArea xpathInput(QueryConsole console) {
        return console.xpathInput();
    }

    @Test
    void aFailedRunShowsTheErrorViewAndMarksTheInput(@TempDir Path tmp) throws Exception {
        QueryConsole console = open(tmp, "<funds><Fund><Ccy>EUR</Ccy><Name>A</Name></Fund></funds>");
        run(console, "distinct-values(//Ccy)/../Name");

        QueryErrorView view = console.errorView();
        assertTrue(view.isVisible(), "the error view replaces the results text");
        QueryError error = view.error();
        assertEquals("XPTY0019", error.code());
        assertEquals("XPTY0019", ((Hyperlink) view.lookup("#query-error-code")).getText());
        assertEquals("column 23", ((Hyperlink) view.lookup("#query-error-position")).getText());
        assertTrue(((Label) view.lookup("#query-error-excerpt")).getText().endsWith("^"));
        assertTrue(((Label) view.lookup("#query-error-hint")).getText().contains("\"!\""));
        assertTrue(console.getResultsText().startsWith("ERROR: XPTY0019"), "Copy / Save keep the text");

        List<QueryInputMarks.Mark> marks = WaitForAsyncUtils.waitForAsyncFx(2000, () -> console.activeMarks().marks());
        assertEquals(1, marks.size());
        assertEquals(22, marks.get(0).start(), "the '/' after distinct-values(…)");
        CodeArea input = xpathInput(console);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> input.getStyleOfChar(22).contains("fxt-query-error")));

        WaitForAsyncUtils.waitForAsyncFx(2000, view::onGoToPosition);
        assertEquals("/", WaitForAsyncUtils.waitForAsyncFx(2000, input::getSelectedText), "caret at the error");

        URIHolder opened = new URIHolder();
        var previous = QueryErrorView.linkOpener;
        QueryErrorView.linkOpener = uri -> opened.uri = uri.toString();
        try {
            WaitForAsyncUtils.waitForAsyncFx(2000, view::onOpenSpec);
        } finally {
            QueryErrorView.linkOpener = previous;
        }
        assertEquals("https://www.w3.org/TR/xpath-31/#ERRXPTY0019", opened.uri);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> input.appendText(" "));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> console.activeMarks().marks().stream()
                .noneMatch(QueryInputMarks.Mark::error)), "the run's mark goes away on the next edit");
    }

    @Test
    void aCorrectionIsAppliedWithOneClick(@TempDir Path tmp) throws Exception {
        QueryConsole console = open(tmp, "<funds><Fund><Ccy>EUR</Ccy></Fund><Fund><Ccy>EUR</Ccy></Fund></funds>");
        run(console, "count(distinct-value(//Ccy))");
        QueryErrorView view = console.errorView();
        assertEquals("XPST0017", view.error().code());
        Button suggestion = (Button) view.lookup(".fxt-query-error-suggestion");
        assertNotNull(suggestion);
        assertEquals("distinct-values", suggestion.getText());

        WaitForAsyncUtils.waitForAsyncFx(2000, suggestion::fire);
        assertEquals("count(distinct-values(//Ccy))", WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> xpathInput(console).getText()));

        WaitForAsyncUtils.waitForAsyncFx(2000, console::runForTest);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "1".equals(console.getResultsText().strip()));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(view.isVisible(), "a successful run shows the results again");
    }

    @Test
    void theCheckWhileTypingMarksASyntaxErrorBeforeRunning(@TempDir Path tmp) throws Exception {
        QueryConsole console = open(tmp, "<funds><Fund/></funds>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("//Fund[");
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.waitForAsyncFx(2000, () -> console.checkLabel().isVisible()));
        Label check = console.checkLabel();
        assertTrue(check.getText().startsWith("XPST0003 · column 7:"), check.getText());
        assertTrue(check.getStyleClass().contains("fxt-query-check-error"));
        assertTrue(console.activeMarks().marks().get(0).error());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("distinct-values(//Fund)/x");
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> check.getStyleClass().contains("fxt-query-check-warning")));
        assertFalse(console.activeMarks().marks().get(0).error(), "a compiler warning, amber");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("count(//Fund)");
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.waitForAsyncFx(2000, () -> !check.isVisible()));
    }

    @Test
    void anEmptyResultOnANamespacedDocumentExplainsWhy(@TempDir Path tmp) throws Exception {
        // with the default namespace off (the XPath standard), //Fund matches nothing in a namespaced document
        setProperty(QuerySettings.DEFAULT_NAMESPACE_KEY, "false");
        setProperty(QuerySettings.LIVE_PREVIEW_KEY, "false");
        try {
            QueryConsole console = open(tmp, "<funds xmlns=\"urn:funds\"><Fund/></funds>");
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                console.setXPath("//Fund");
                console.runForTest();
                return null;
            });
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                    () -> console.checkLabel().getStyleClass().contains("fxt-query-check-info")));
            String text = console.checkLabel().getText();
            assertTrue(text.contains("urn:funds") && text.contains("*:Fund"), text);
        } finally {
            setProperty(QuerySettings.DEFAULT_NAMESPACE_KEY, "true");
            setProperty(QuerySettings.LIVE_PREVIEW_KEY, "true");
        }
    }

    @Test
    void aBrokenDocumentIsShownAsTheDocumentsErrorAndItsPositionOpensTheDocumentThere(@TempDir Path tmp)
            throws Exception {
        QueryConsole console = open(tmp, "<funds>\n  <Fund>\n    <Name>A</Nam>\n  </Fund>\n</funds>");
        run(console, "//Fund/Name");
        QueryErrorView view = console.errorView();
        assertEquals("SXXP0003", view.error().code());
        String position = ((Hyperlink) view.lookup("#query-error-position")).getText();
        assertTrue(position.startsWith("document line 3, column "), position);
        assertTrue(((Label) view.lookup("#query-error-hint")).getText().contains("not well-formed"));

        WaitForAsyncUtils.waitForAsyncFx(2000, view::onGoToPosition);
        assertEquals(3, WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveCaretLineColumn()[0]),
                "the caret is at the error in the document");
    }

    @Test
    void aRelativeDocCallReadsTheFileNextToTheDocument(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("rates.xml"), "<rates><EUR>1</EUR></rates>");
        QueryConsole console = open(tmp, "<funds><Fund/></funds>");
        run(console, "name(doc('rates.xml')/*/*)");
        assertFalse(console.errorView().isVisible(), console.getResultsText());
        assertEquals("EUR", console.getResultsText().strip());
    }

    private static final String FUNDS_NS = "<FundsXML xmlns=\"urn:funds\" xmlns:fx=\"urn:fx\">"
            + "<Fund><Name>A</Name><fx:Code>X1</fx:Code></Fund><Fund><Name>B</Name></Fund></FundsXML>";

    private static void setProperty(String key, String value) {
        ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class).set(key, value);
    }

    @Test
    void theDocumentsPrefixesAndDefaultNamespaceNeedNoDeclaration(@TempDir Path tmp) throws Exception {
        setProperty(QuerySettings.DEFAULT_NAMESPACE_KEY, "true");
        QueryConsole console = open(tmp, FUNDS_NS);
        run(console, "string(//fx:Code)");
        assertFalse(console.errorView().isVisible(), console.getResultsText());
        assertEquals("X1", console.getResultsText().strip(), "fx: is the document's prefix, declared for the run");

        run(console, "count(//Fund)");
        assertEquals("2", console.getResultsText().strip(), "unprefixed names match the default namespace");
        var chip = console.defaultNsToggle();
        assertTrue(chip.isVisible() && chip.isSelected(), "the chip shows the default namespace is used");

        try {
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                chip.setSelected(false);
                console.onToggleDefaultNamespace();
                return null;
            });
            assertFalse(QuerySettings.useDocumentDefaultNamespace());
            run(console, "count(//Fund)");
            assertEquals("0", console.getResultsText().strip(), "off: the XPath standard, no match");
        } finally {
            setProperty(QuerySettings.DEFAULT_NAMESPACE_KEY, "true");
        }
    }

    @Test
    void theHitsAreCountedWhileTyping(@TempDir Path tmp) throws Exception {
        setProperty(QuerySettings.LIVE_PREVIEW_KEY, "true");
        QueryConsole console = open(tmp, "<funds><Fund/><Fund/><Fund/></funds>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("//Fund");
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> console.checkLabel().getText().startsWith("3 hits")));
        assertTrue(console.checkLabel().getStyleClass().contains("fxt-query-check-info"));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("count(//Fund)");
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> console.checkLabel().getText().startsWith("Result: 3")));
    }

    @Test
    void aRunEvaluatesOnceAndCanBeCancelled(@TempDir Path tmp) throws Exception {
        setProperty(QuerySettings.LIVE_PREVIEW_KEY, "false");
        try {
            QueryConsole console = open(tmp, "<funds><Fund/><Fund/></funds>");
            int before = QueryEvaluation.EVALUATIONS.get();
            run(console, "//Fund");
            assertEquals(before + 1, QueryEvaluation.EVALUATIONS.get(), "text, table and hits from one evaluation");
            assertEquals(2, WaitForAsyncUtils.waitForAsyncFx(2000, () -> console.hits().locatedHits().size()),
                    "the hits of that evaluation");

            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                console.setXQuery("count(for $i in 1 to 1000000000 let $x := $i * 2 where $x mod 3 = 0 return $x)");
                console.runForTest();
                return null;
            });
            WaitForAsyncUtils.sleep(700, TimeUnit.MILLISECONDS);
            assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, console::isRunning));
            WaitForAsyncUtils.waitForAsyncFx(2000, console::runForTest); // the Run button reads Cancel now
            assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, console::isRunning));
            assertEquals("Cancelled.", console.getResultsText());
        } finally {
            setProperty(QuerySettings.LIVE_PREVIEW_KEY, "true");
        }
    }

    @Test
    void traceLinesOfAnXQueryRunAreOfferedInTheTraceMenu(@TempDir Path tmp) throws Exception {
        QueryConsole console = open(tmp, "<funds><Fund/><Fund/></funds>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXQuery("count(trace(//Fund, 'funds'))");
            console.runForTest();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> console.getResultsText().contains("2"));
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.waitForAsyncFx(1000, console::getTraceLines).size() >= 1);
        String line = WaitForAsyncUtils.waitForAsyncFx(1000, console::getTraceLines).getFirst();
        assertTrue(line.startsWith("funds") && line.contains("/funds/Fund[1]"), line);
    }

    @Test
    void theExternalVariablesOfAnXQueryGetFieldsAndTheirValues(@TempDir Path tmp) throws Exception {
        QueryConsole console = open(tmp, "<funds><Fund/><Fund/></funds>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXQuery("declare variable $n as xs:integer external; count(//Fund) * $n");
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.waitForAsyncFx(1000, console::variableNames).contains("n"));

        WaitForAsyncUtils.waitForAsyncFx(2000, console::runForTest);
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> console.getResultsText().contains("$n"));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setVariable("n", "5");
            console.runForTest();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> console.getResultsText().endsWith("10"));
    }

    private static final class URIHolder {
        String uri;
    }
}
