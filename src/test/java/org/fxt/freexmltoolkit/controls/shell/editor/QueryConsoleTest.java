package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of {@link QueryConsole}: running an XPath against the
 * active document fills the results pane, and with no document open the console
 * disables Run / reports that no document is open.
 */
@ExtendWith(ApplicationExtension.class)
class QueryConsoleTest {

    private EditorHost host;

    // Snippet files created by the save tests, deleted in @AfterEach. FavoritesService
    // writes to a fixed user-home config directory and cannot be redirected, so the
    // tests use unique names and clean up after themselves to avoid polluting it.
    private final List<File> createdSnippets = new ArrayList<>();

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        host = new EditorHost();
    }

    @AfterEach
    void deleteCreatedSnippets() {
        for (File file : createdSnippets) {
            if (file != null) {
                file.delete();
            }
        }
        createdSnippets.clear();
    }

    @Test
    void runningXPathAgainstTheActiveDocumentShowsResults(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("doc.xml");
        Files.writeString(file, "<root><item id=\"1\">a</item><item id=\"2\">b</item></root>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("item")).orElse(false));

        QueryConsole console = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new QueryConsole(host));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("//item");
            console.runForTest();
            return null;
        });

        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> {
                    String r = console.getResultsText();
                    return r != null && !r.isBlank() && !"Running…".equals(r);
                });

        String results = console.getResultsText();
        assertTrue(results.contains("item"), "results should contain the matched elements: " + results);
        assertTrue(results.contains("a") && results.contains("b"),
                "results should contain both item values: " + results);
    }

    @Test
    void savingResultsWritesTheResultTextToFile(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("doc.xml");
        Files.writeString(file, "<root><item id=\"1\">a</item><item id=\"2\">b</item></root>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("item")).orElse(false));

        QueryConsole console = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new QueryConsole(host));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("//item");
            console.runForTest();
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> {
            String r = console.getResultsText();
            return r != null && !r.isBlank() && !"Running…".equals(r);
        });

        String results = console.getResultsText();
        Path out = tmp.resolve("results.txt");
        boolean ok = WaitForAsyncUtils.waitForAsyncFx(2000, () -> console.saveResultsToFile(out.toFile()));

        assertTrue(ok, "saving results should succeed");
        assertEquals(results, Files.readString(out), "the saved file must contain the result text");
    }

    @Test
    void runIsDisabledWhenNoDocumentIsOpen() {
        QueryConsole console = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new QueryConsole(host));
        WaitForAsyncUtils.waitForFxEvents();

        // With no document open, Run is disabled.
        assertTrue(console.isRunDisabledForTest(), "Run must be disabled when no document is open");

        // Even if invoked, it reports there is no document and does not produce results.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.runForTest();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(console.getResultsText().contains("item"),
                "no results should be produced without an active document");
        assertTrue(console.getResultsText().contains("No document open."),
                "the console should report that no document is open");
    }

    @Test
    void snippetsRoundTripForBothXPathAndXQuery() {
        QueryConsole console = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new QueryConsole(host));
        WaitForAsyncUtils.waitForFxEvents();

        // Unique, deterministic names so we never clobber real user favorites.
        String suffix = "_qctest_" + getClass().getSimpleName() + "_" + System.nanoTime();
        String xpathName = "snippet_xpath" + suffix;
        String xqueryName = "snippet_xquery" + suffix;

        // Save an XPath snippet from XPath mode.
        File xpathFile = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("//item[@id='1']");
            return console.saveSnippetForTest(xpathName);
        });
        createdSnippets.add(xpathFile);
        assertNotNull(xpathFile, "saving an XPath snippet must return a file");

        // Save an XQuery snippet from XQuery mode.
        File xqueryFile = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXQuery("for $x in /root/item return string($x)");
            return console.saveSnippetForTest(xqueryName);
        });
        createdSnippets.add(xqueryFile);
        assertNotNull(xqueryFile, "saving an XQuery snippet must return a file");

        // FavoritesService round-trips both kinds.
        FavoritesService favorites = FavoritesService.getInstance();
        assertTrue(favorites.getSavedXPathQueries().stream().anyMatch(f -> f.equals(xpathFile)),
                "the saved XPath snippet should be listed by FavoritesService");
        assertTrue(favorites.getSavedXQueryQueries().stream().anyMatch(f -> f.equals(xqueryFile)),
                "the saved XQuery snippet should be listed by FavoritesService");

        // Loading an XPath snippet switches to XPath mode and fills the field.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXQuery("placeholder"); // start in XQuery mode to prove the switch
            console.loadSnippet(xpathFile, false);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("//item[@id='1']", console.getXPathTextForTest(),
                "loading an XPath snippet should fill the XPath field");
        assertFalse(console.isXQueryModeForTest(), "loading an XPath snippet should switch to XPath mode");

        // Loading an XQuery snippet switches to XQuery mode and fills the area.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.loadSnippet(xqueryFile, true);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("for $x in /root/item return string($x)", console.getXQueryTextForTest(),
                "loading an XQuery snippet should fill the XQuery area");
        assertTrue(console.isXQueryModeForTest(), "loading an XQuery snippet should switch to XQuery mode");
    }
}
