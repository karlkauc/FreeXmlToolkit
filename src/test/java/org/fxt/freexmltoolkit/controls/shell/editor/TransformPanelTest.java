package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.FavoritesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the Transform panel: XSLT transformation of the active
 * XML, and XPath evaluation, both producing output.
 */
@ExtendWith(ApplicationExtension.class)
class TransformPanelTest {

    private static final String XML = "<greeting>Hello</greeting>";
    private static final String XSLT = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="xml"/>
              <xsl:template match="/greeting"><out><xsl:value-of select="."/></out></xsl:template>
            </xsl:stylesheet>
            """;

    private EditorHost host;
    private TransformPanel panel;

    // Saved-query files created by tests, deleted in @AfterEach. FavoritesService
    // writes to a fixed user-home config directory and cannot be redirected, so the
    // tests use unique names and clean up after themselves to avoid polluting it.
    private final List<File> createdQueries = new ArrayList<>();

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new TransformPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 600));
        stage.show();
    }

    @AfterEach
    void deleteCreatedQueries() {
        for (File file : createdQueries) {
            if (file != null) {
                file.delete();
            }
        }
        createdQueries.clear();
    }

    @Test
    void transformsActiveXmlWithXslt(@TempDir Path tmp) throws Exception {
        openGreeting(tmp);
        Path xslt = tmp.resolve("t.xslt");
        Files.writeString(xslt, XSLT);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(xslt.toFile());
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getOutputText().contains("<out>Hello</out>"));
        assertTrue(panel.getOutputText().contains("<out>Hello</out>"), panel.getOutputText());
        // Transform stats are shown (elapsed time + output size).
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> panel.getTransformStats().contains("chars"));
        assertTrue(panel.getTransformStats().matches("\\d+ ms · \\d+ chars"), panel.getTransformStats());
    }

    @Test
    void evaluatesXPathAgainstActiveXml(@TempDir Path tmp) throws Exception {
        openGreeting(tmp);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXPathExpression("/greeting");
            panel.runXPath();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getOutputText().contains("Hello"));
        assertTrue(panel.getOutputText().contains("Hello"), panel.getOutputText());
    }

    @Test
    void evaluatesJsonPathAgainstActiveJson(@TempDir Path tmp) throws Exception {
        Path json = tmp.resolve("data.json");
        Files.writeString(json, "{\"fund\":{\"id\":\"EAM_2024\"}}");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("fund")).orElse(false));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXPathExpression("$.fund.id");
            panel.runXPath();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getOutputText().contains("EAM_2024"));
        assertTrue(panel.getOutputText().contains("EAM_2024"), panel.getOutputText());
    }

    @Test
    void passesParametersAndHonorsTextOutputFormat(@TempDir Path tmp) throws Exception {
        openGreeting(tmp);
        Path xslt = tmp.resolve("param.xslt");
        Files.writeString(xslt, """
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:param name="who" select="'nobody'"/>
                  <xsl:output method="text"/>
                  <xsl:template match="/">Hi <xsl:value-of select="$who"/></xsl:template>
                </xsl:stylesheet>
                """);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(xslt.toFile());
            panel.addParameter("who", "Karl");
            panel.setOutputFormat(org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.TEXT);
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getOutputText().contains("Hi Karl"));
        assertTrue(panel.getOutputText().contains("Hi Karl"), panel.getOutputText());
        assertFalse(panel.getOutputText().contains("<?xml"), "text output must not be wrapped as XML");
    }

    @Test
    void loadsASavedQueryFileIntoTheField(@TempDir Path tmp) throws Exception {
        Path q = tmp.resolve("q.xpath");
        Files.writeString(q, "/root/item[@id]\n");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.loadQueryFromFile(q.toFile());
            return null;
        });
        assertEquals("/root/item[@id]", panel.getQueryText());
    }

    @Test
    void savedQueriesMenuOffersManagementActionsAndOverwriteWorks() throws Exception {
        String name = "fxt-test-tp-manage-" + System.nanoTime();
        File saved = FavoritesService.getInstance().saveXPathQuery(name, "//a");
        createdQueries.add(saved);
        assertNotNull(saved);

        List<javafx.scene.control.MenuItem> items =
                WaitForAsyncUtils.waitForAsyncFx(2000, panel::savedQueriesMenuItemsForTest);
        javafx.scene.control.Menu submenu = (javafx.scene.control.Menu) items.stream()
                .filter(i -> i.getText() != null && i.getText().contains(name))
                .findFirst().orElseThrow();
        for (String action : List.of("Load into query field", "Open in editor",
                "Overwrite with current query", "Rename…", "Delete…")) {
            assertTrue(submenu.getItems().stream().anyMatch(i -> action.equals(i.getText())),
                    "saved query submenu must offer '" + action + "': " + submenu.getItems());
        }

        // Overwrite replaces the file with the field's text; a blank field is rejected.
        File overwritten = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXPathExpression("//b");
            return panel.overwriteSavedQueryForTest(saved);
        });
        assertEquals(saved, overwritten, "overwrite must land on the same file");
        assertEquals("//b", Files.readString(saved.toPath()));

        File blank = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXPathExpression("");
            return panel.overwriteSavedQueryForTest(saved);
        });
        assertNull(blank, "a blank field must not overwrite the query");
        assertEquals("//b", Files.readString(saved.toPath()), "the file must stay unchanged");
    }

    @Test
    void savedQueryRenameAndDeleteManageTheFile() {
        String suffix = "-" + System.nanoTime();
        File fileA = FavoritesService.getInstance().saveXPathQuery("fxt-test-tp-a" + suffix, "//a");
        File fileB = FavoritesService.getInstance().saveXPathQuery("fxt-test-tp-b" + suffix, "//b");
        createdQueries.add(fileA);
        createdQueries.add(fileB);

        // Rename A; renaming B onto the taken name is rejected.
        String newName = "fxt-test-tp-new" + suffix;
        File renamed = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> panel.renameSavedQueryForTest(fileA, newName));
        createdQueries.add(renamed);
        assertNotNull(renamed, "the rename must succeed");
        assertTrue(renamed.exists());
        assertFalse(fileA.exists(), "the old file must be gone after the rename");

        File collision = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> panel.renameSavedQueryForTest(fileB, newName));
        assertNull(collision, "a rename onto an existing query must be rejected");
        assertTrue(fileB.exists());
        assertTrue(renamed.exists());

        // Delete removes the file and its menu entry.
        boolean deleted = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> panel.deleteSavedQueryForTest(renamed));
        assertTrue(deleted, "the delete must succeed");
        assertFalse(renamed.exists());
        List<javafx.scene.control.MenuItem> items =
                WaitForAsyncUtils.waitForAsyncFx(2000, panel::savedQueriesMenuItemsForTest);
        assertFalse(items.stream().anyMatch(i -> i.getText() != null && i.getText().contains(newName)),
                "the menu must no longer list the deleted query");
    }

    @Test
    void exposesSaveAndSavedQueryControls() {
        WaitForAsyncUtils.waitForFxEvents();
        // lookupAll must run on the FX thread (scene graph may still be mutating)
        boolean hasSave = WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.lookupAll(".button").stream()
                .anyMatch(n -> n instanceof javafx.scene.control.Button b && "Save Query".equals(b.getText())));
        boolean hasSaved = WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.lookupAll(".menu-button").stream()
                .anyMatch(n -> n instanceof javafx.scene.control.MenuButton b && "Saved".equals(b.getText())));
        assertTrue(hasSave, "panel must offer a 'Save Query' action");
        assertTrue(hasSaved, "panel must offer a 'Saved' queries menu");
    }

    @Test
    void livePreviewAutoTransformsWhenEnabled(@TempDir Path tmp) throws Exception {
        openGreeting(tmp);
        Path xslt = tmp.resolve("t.xslt");
        Files.writeString(xslt, XSLT);

        // Enable live preview with a stylesheet set — do NOT call transform() manually.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(xslt.toFile());
            panel.setLivePreview(true);
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> panel.getOutputText().contains("<out>Hello</out>"));
        assertTrue(panel.getOutputText().contains("<out>Hello</out>"), panel.getOutputText());
    }

    @Test
    void runsXQueryAgainstActiveXml(@TempDir Path tmp) throws Exception {
        openGreeting(tmp);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setOutputFormat(org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.TEXT);
            panel.setXQuery("string(/greeting)");
            panel.runXQuery();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> panel.getOutputText().contains("Hello"));
        assertTrue(panel.getOutputText().contains("Hello"), panel.getOutputText());
    }

    @Test
    void xqueryResultIsShownAsTable(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("order.xml");
        Files.writeString(xml, "<order><item><sku>A</sku><qty>2</qty></item>"
                + "<item><sku>B</sku><qty>5</qty></item></order>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("order")).orElse(false));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXQuery("for $i in /order/item return $i");
            panel.runXQuery();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> panel.getResultRowCount() == 2);
        assertEquals(java.util.List.of("sku", "qty"), panel.getResultColumns());
        assertEquals(2, panel.getResultRowCount());
        assertTrue(panel.isResultTableShown(), "the table view must be shown for a tabular XQuery result");
    }

    @Test
    void writesHtmlPreviewFile() throws Exception {
        java.io.File file = TransformOutputPanel.writeHtmlPreview("<html><body>Preview Hi</body></html>");
        assertTrue(file.isFile() && file.getName().endsWith(".html"), file.toString());
        assertTrue(Files.readString(file.toPath()).contains("Preview Hi"));
    }

    @Test
    void detectsExternalXsltFileChange(@TempDir Path tmp) throws Exception {
        Path xslt = tmp.resolve("watch.xslt");
        Files.writeString(xslt, XSLT);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(xslt.toFile());
            return null;
        });
        // No change right after setting the file.
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.pollXsltChanged()));
        // Modify the stylesheet on disk with a later timestamp → detected.
        Files.writeString(xslt, XSLT + "\n<!-- edited -->");
        xslt.toFile().setLastModified(System.currentTimeMillis() + 3000);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.pollXsltChanged()));
        // ...and consumed (no second trigger until it changes again).
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.pollXsltChanged()));
    }

    @Test
    void recordsAndListsRecentXslt(@TempDir Path tmp) throws Exception {
        Path xslt = tmp.resolve("recent.xslt");
        Files.writeString(xslt, XSLT);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(xslt.toFile());
            panel.refreshRecentXsltMenu();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(panel.recentXsltNames().contains("recent.xslt"),
                "the chosen stylesheet must appear in the Recent XSLT menu, was: " + panel.recentXsltNames());
    }

    private void openGreeting(Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, XML);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("greeting")).orElse(false));
    }
}
