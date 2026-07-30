package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    void xmlResultsAreSyntaxHighlightedAndPlainTextResultsAreNot(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("doc.xml");
        Files.writeString(file, "<root><item id=\"1\">a</item><item id=\"2\">b</item></root>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("item")).orElse(false));

        QueryConsole console = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new QueryConsole(host));

        // An XML-producing XPath gets XML highlighting in the results pane.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("//item");
            console.runForTest();
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> {
            String r = console.getResultsText();
            return r != null && r.contains("item") && !"Running…".equals(r);
        });
        Set<String> xmlClasses = WaitForAsyncUtils.waitForAsyncFx(2000, console::resultsStyleClassesForTest);
        assertTrue(xmlClasses.contains("tagmark") && xmlClasses.contains("anytag"),
                "XML results should carry XML highlight classes, got: " + xmlClasses);

        // A scalar result (plain text) stays unstyled.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("count(//item)");
            console.runForTest();
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> {
            String r = console.getResultsText();
            return r != null && !r.isBlank() && !"Running…".equals(r) && !r.contains("item");
        });
        Set<String> plainClasses = WaitForAsyncUtils.waitForAsyncFx(2000, console::resultsStyleClassesForTest);
        assertTrue(plainClasses.isEmpty(),
                "plain-text results should have no highlight classes, got: " + plainClasses
                        + " for text: " + console.getResultsText());
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

    @Test
    void snippetsMenuShowsAFundsXmlSectionFromTheRepository() throws Exception {
        org.fxt.freexmltoolkit.service.XPathSnippetRepository repo =
                org.fxt.freexmltoolkit.service.XPathSnippetRepository.getInstance();
        String name = "fxt-test-fund-summary-" + System.nanoTime();
        repo.saveSnippet(org.fxt.freexmltoolkit.domain.XPathSnippet.builder()
                .name(name)
                .description("test snippet")
                .type(org.fxt.freexmltoolkit.domain.XPathSnippet.SnippetType.XQUERY)
                .category(org.fxt.freexmltoolkit.domain.XPathSnippet.SnippetCategory.ANALYSIS)
                .query("for $f in //Fund return $f/Name")
                .tags(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPostDownloadRegistrar.SNIPPET_TAG)
                .build());

        QueryConsole console = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new QueryConsole(host));
        List<javafx.scene.control.MenuItem> items =
                WaitForAsyncUtils.waitForAsyncFx(2000, console::snippetsMenuItemsForTest);

        assertTrue(items.stream().anyMatch(i -> "FUNDSXML".equals(i.getText())),
                "the snippets menu must contain the FUNDSXML section header");
        javafx.scene.control.MenuItem snippetItem = items.stream()
                .filter(i -> i.getText() != null && i.getText().contains(name))
                .findFirst().orElse(null);
        assertNotNull(snippetItem, "the seeded FundsXML snippet must appear in the menu");
        assertTrue(snippetItem.getText().startsWith("XQuery:"),
                "the item label must carry its kind: " + snippetItem.getText());

        // Each snippet is a submenu offering "Load into console" and "Open in editor".
        javafx.scene.control.Menu submenu =
                org.junit.jupiter.api.Assertions.assertInstanceOf(javafx.scene.control.Menu.class,
                        snippetItem, "each snippet entry must be a submenu");
        javafx.scene.control.MenuItem load = childItem(submenu, "Load into console");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            load.fire();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("for $f in //Fund return $f/Name", console.getXQueryTextForTest());
        assertTrue(console.isXQueryModeForTest(), "an XQuery snippet must switch the console to XQuery mode");

        // "Open in editor" opens the repository snippet as an untitled XQuery document.
        javafx.scene.control.MenuItem open = childItem(submenu, "Open in editor");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            open.fire();
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getOpenDocuments().stream()
                .anyMatch(d -> d.getFileType() == EditorFileType.XQUERY));
        assertTrue(host.getActiveText().orElse("").contains("for $f in //Fund"),
                "the opened editor tab must contain the snippet's query");
    }

    @Test
    void savedSnippetOpensAsAnEditorTab() throws Exception {
        QueryConsole console = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new QueryConsole(host));
        String name = "fxt-test-open-in-editor-" + System.nanoTime();

        File saved = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXQuery("for $x in /root return $x");
            return console.saveSnippetForTest(name);
        });
        createdSnippets.add(saved);
        assertNotNull(saved, "saving the snippet must return a file");

        List<javafx.scene.control.MenuItem> items =
                WaitForAsyncUtils.waitForAsyncFx(2000, console::snippetsMenuItemsForTest);
        javafx.scene.control.Menu submenu = (javafx.scene.control.Menu) items.stream()
                .filter(i -> i.getText() != null && i.getText().contains(name))
                .findFirst().orElseThrow();
        javafx.scene.control.MenuItem open = childItem(submenu, "Open in editor");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            open.fire();
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveText()
                .map(t -> t.contains("for $x in /root return $x")).orElse(false));
        assertEquals(EditorFileType.XQUERY,
                host.getActiveDocument().orElseThrow().getFileType(),
                "the saved .xquery snippet must open as an XQuery document");
    }

    @Test
    void userSnippetSubmenuOffersManagementActionsButFundsXmlDoesNot() {
        QueryConsole console = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new QueryConsole(host));
        String name = "fxt-test-manage-actions-" + System.nanoTime();

        File saved = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("//item");
            return console.saveSnippetForTest(name);
        });
        createdSnippets.add(saved);
        assertNotNull(saved);

        // A read-only FundsXML repository snippet for comparison.
        String repoName = "fxt-test-readonly-" + System.nanoTime();
        org.fxt.freexmltoolkit.service.XPathSnippetRepository.getInstance()
                .saveSnippet(org.fxt.freexmltoolkit.domain.XPathSnippet.builder()
                        .name(repoName)
                        .description("read-only test snippet")
                        .type(org.fxt.freexmltoolkit.domain.XPathSnippet.SnippetType.XPATH)
                        .query("//Fund")
                        .tags(org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPostDownloadRegistrar.SNIPPET_TAG)
                        .build());

        List<javafx.scene.control.MenuItem> items =
                WaitForAsyncUtils.waitForAsyncFx(2000, console::snippetsMenuItemsForTest);

        javafx.scene.control.Menu userMenu = (javafx.scene.control.Menu) items.stream()
                .filter(i -> i.getText() != null && i.getText().contains(name))
                .findFirst().orElseThrow();
        for (String action : List.of("Overwrite with current query", "Rename…", "Delete…")) {
            assertNotNull(childItem(userMenu, action),
                    "user snippet submenu must offer '" + action + "'");
        }

        javafx.scene.control.Menu repoMenu = (javafx.scene.control.Menu) items.stream()
                .filter(i -> i.getText() != null && i.getText().contains(repoName))
                .findFirst().orElseThrow();
        assertTrue(repoMenu.getItems().stream().noneMatch(i -> "Delete…".equals(i.getText())),
                "FundsXML repository snippets must stay read-only (no Delete)");
        assertTrue(repoMenu.getItems().stream()
                        .noneMatch(i -> "Overwrite with current query".equals(i.getText())),
                "FundsXML repository snippets must stay read-only (no Overwrite)");
    }

    @Test
    void overwriteReplacesTheSnippetFileContent() throws Exception {
        QueryConsole console = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new QueryConsole(host));
        String name = "fxt-test-overwrite-" + System.nanoTime();

        File saved = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("//a");
            return console.saveSnippetForTest(name);
        });
        createdSnippets.add(saved);
        assertNotNull(saved);

        // Overwrite is bound to the snippet's kind (XPath field), even while the
        // console is showing the XQuery mode.
        File overwritten = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("//b");
            console.setXQuery("for $x in /r return $x"); // switches mode to XQuery
            return console.overwriteSnippetForTest(saved, false);
        });
        assertEquals(saved, overwritten, "overwrite must land on the same file");
        assertEquals("//b", Files.readString(saved.toPath()),
                "the file must contain the XPath field's text, not the XQuery text");
        assertTrue(console.getResultsText().startsWith("Overwrote snippet"),
                "the results pane must confirm the overwrite: " + console.getResultsText());
    }

    @Test
    void overwriteWithBlankInputIsRejected() throws Exception {
        QueryConsole console = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new QueryConsole(host));
        String name = "fxt-test-overwrite-blank-" + System.nanoTime();

        File saved = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("//keep");
            return console.saveSnippetForTest(name);
        });
        createdSnippets.add(saved);
        assertNotNull(saved);

        File result = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("");
            return console.overwriteSnippetForTest(saved, false);
        });
        assertNull(result, "a blank input must not overwrite the snippet");
        assertEquals("//keep", Files.readString(saved.toPath()), "the file must stay unchanged");
    }

    @Test
    void renameSnippetRenamesTheFileAndCollisionIsRejected() {
        QueryConsole console = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new QueryConsole(host));
        String suffix = "-" + System.nanoTime();
        String nameA = "fxt-test-rename-a" + suffix;
        String nameB = "fxt-test-rename-b" + suffix;
        String newName = "fxt-test-rename-new" + suffix;

        File fileA = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("//a");
            return console.saveSnippetForTest(nameA);
        });
        File fileB = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("//b");
            return console.saveSnippetForTest(nameB);
        });
        createdSnippets.add(fileA);
        createdSnippets.add(fileB);

        // Rename A → the new file exists, the old one is gone, the menu shows it.
        File renamed = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> console.renameSnippetForTest(fileA, newName));
        createdSnippets.add(renamed);
        assertNotNull(renamed, "the rename must succeed");
        assertTrue(renamed.getName().contains(newName));
        assertTrue(renamed.exists());
        assertFalse(fileA.exists(), "the old file must be gone after the rename");
        List<javafx.scene.control.MenuItem> items =
                WaitForAsyncUtils.waitForAsyncFx(2000, console::snippetsMenuItemsForTest);
        assertTrue(items.stream().anyMatch(i -> i.getText() != null && i.getText().contains(newName)),
                "the menu must list the renamed snippet");

        // Renaming B to the taken name is rejected and both files survive.
        File collision = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> console.renameSnippetForTest(fileB, newName));
        assertNull(collision, "a rename onto an existing snippet must be rejected");
        assertTrue(console.getResultsText().contains("already exists"),
                "the results pane must explain the collision: " + console.getResultsText());
        assertTrue(fileB.exists());
        assertTrue(renamed.exists());
    }

    @Test
    void deleteSnippetRemovesFileAndMenuEntry() {
        QueryConsole console = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new QueryConsole(host));
        String name = "fxt-test-delete-" + System.nanoTime();

        File saved = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath("//gone");
            return console.saveSnippetForTest(name);
        });
        createdSnippets.add(saved);
        assertNotNull(saved);

        boolean deleted = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> console.deleteSnippetForTest(saved));
        assertTrue(deleted, "the delete must succeed");
        assertFalse(saved.exists(), "the snippet file must be gone");
        List<javafx.scene.control.MenuItem> items =
                WaitForAsyncUtils.waitForAsyncFx(2000, console::snippetsMenuItemsForTest);
        assertFalse(items.stream().anyMatch(i -> i.getText() != null && i.getText().contains(name)),
                "the menu must no longer list the deleted snippet");
    }

    /** Finds a direct child of {@code menu} by its exact label. */
    private static javafx.scene.control.MenuItem childItem(javafx.scene.control.Menu menu, String text) {
        return menu.getItems().stream()
                .filter(i -> text.equals(i.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "submenu must contain '" + text + "': " + menu.getItems()));
    }
}
