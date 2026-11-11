package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The Transform panel's STYLESHEET and INPUT rows take any open editor tab: every open XSLT tab
 * as the stylesheet, every open XML tab as the input — saved or not, with the editor text.
 */
@ExtendWith(ApplicationExtension.class)
class TransformPanelEditorTabsTest {

    private static final String XSLT = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="xml"/>
              <xsl:template match="/*"><out><xsl:value-of select="."/></out></xsl:template>
            </xsl:stylesheet>
            """;

    private EditorHost host;
    private TransformPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        panel = new TransformPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 600));
        stage.show();
    }

    @Test
    void anUnsavedGeneratedStylesheetTabRunsWithItsEditorText(@TempDir Path tmp) throws Exception {
        OpenDocument xml = open(tmp, "doc.xml", "<greeting>Hello</greeting>");
        OpenDocument xslt = fx(() -> host.openGeneratedDocument(
                XSLT.replace("<out>", "<generated>").replace("</out>", "</generated>"),
                EditorFileType.XSLT, "generated.xslt"));

        // the open XSLT tab is offered, the active one first
        assertEquals(List.of(xslt), fx(panel::xsltTabCandidates));
        fx(() -> {
            panel.useStylesheetDocument(xslt);
            host.selectDocument(xml);
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> panel.getOutputText().contains("<generated>Hello</generated>"));
        assertTrue(panel.hasStylesheet());
        assertNull(panel.currentXsltFile(), "an unsaved tab has no file");
    }

    @Test
    void aSavedStylesheetTabRunsWithItsUnsavedEdits(@TempDir Path tmp) throws Exception {
        open(tmp, "doc.xml", "<greeting>Hello</greeting>");
        OpenDocument xslt = open(tmp, "t.xslt", XSLT);
        fx(() -> {
            String text = host.getActiveText().orElseThrow();
            int at = text.indexOf("<out>");
            host.replaceActiveTextRegion(at, at + "<out>".length(), "<edited>");
            int end = host.getActiveText().orElseThrow().indexOf("</out>");
            host.replaceActiveTextRegion(end, end + "</out>".length(), "</edited>");
            panel.useStylesheetDocument(xslt);
            return null;
        });
        assertEquals(tmp.resolve("t.xslt").toFile(), panel.currentXsltFile());
        // the stylesheet tab is in front: the input stays the XML document
        fx(() -> {
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> panel.getOutputText().contains("<edited>Hello</edited>"));
    }

    @Test
    void theStylesheetTabIsNeverOfferedAsInput(@TempDir Path tmp) throws Exception {
        OpenDocument xml = open(tmp, "doc.xml", "<a/>");
        OpenDocument xslt = open(tmp, "t.xslt", XSLT);
        fx(() -> {
            panel.useStylesheetDocument(xslt);
            return null;
        });
        List<OpenDocument> inputs = fx(panel::inputTabCandidates);
        assertEquals(List.of(xml), inputs);
    }

    @Test
    void aChosenInputTabStaysTheInputWhenAnotherTabIsActive(@TempDir Path tmp) throws Exception {
        OpenDocument first = open(tmp, "first.xml", "<greeting>First</greeting>");
        open(tmp, "second.xml", "<greeting>Second</greeting>");
        Path xslt = tmp.resolve("t.xslt");
        Files.writeString(xslt, XSLT);
        fx(() -> {
            panel.setXsltFile(xslt.toFile());
            panel.useInputDocument(first); // second.xml is the active tab
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> panel.getOutputText().contains("<out>First</out>"));
        assertSame(first, panel.currentInputDocumentOverride());

        // "Use active editor" follows the active tab again
        fx(() -> {
            panel.onUseActiveEditor();
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> panel.getOutputText().contains("<out>Second</out>"));
        assertNull(panel.currentInputDocumentOverride());
    }

    @Test
    void theChangeMenusListTheOpenTabs(@TempDir Path tmp) throws Exception {
        open(tmp, "doc.xml", "<a/>");
        open(tmp, "one.xslt", XSLT);
        open(tmp, "two.xsl", XSLT);
        fx(() -> {
            panel.onChangeXslt();
            panel.onChangeInput();
            return null;
        });
        ContextMenu xsltMenu = fx(() -> menuWith("transform-xslt-file"));
        List<String> xsltEntries = fx(() -> xsltMenu.getItems().stream()
                .filter(i -> i instanceof CheckMenuItem).map(MenuItem::getText).toList());
        assertEquals(2, xsltEntries.size(), xsltEntries.toString());
        assertTrue(xsltEntries.get(0).startsWith("two.xsl"), "active tab first: " + xsltEntries);
        assertTrue(xsltEntries.get(0).contains("(active)"), xsltEntries.toString());

        ContextMenu inputMenu = fx(() -> menuWith("transform-input-file"));
        List<String> inputEntries = fx(() -> inputMenu.getItems().stream()
                .filter(i -> i instanceof CheckMenuItem).map(MenuItem::getText).toList());
        assertEquals(List.of("doc.xml"), inputEntries);

        // choosing an entry sets the stylesheet
        fx(() -> {
            xsltMenu.getItems().stream().filter(i -> "transform-xslt-tab-1".equals(i.getId())).findFirst()
                    .orElseThrow().fire();
            xsltMenu.hide();
            inputMenu.hide();
            return null;
        });
        assertEquals(tmp.resolve("one.xslt").toFile(), panel.currentXsltFile());
    }

    @Test
    void aClosedSavedStylesheetTabStaysAsItsFile(@TempDir Path tmp) throws Exception {
        open(tmp, "doc.xml", "<a/>");
        OpenDocument xslt = open(tmp, "t.xslt", XSLT);
        fx(() -> {
            panel.useStylesheetDocument(xslt);
            host.closeActiveTab();
            return null;
        });
        assertNull(panel.stylesheetDocument());
        assertEquals(tmp.resolve("t.xslt").toFile(), panel.currentXsltFile());
    }

    // ----- helpers ------------------------------------------------------------------

    private ContextMenu menuWith(String itemId) {
        for (javafx.stage.Window window : javafx.stage.Window.getWindows()) {
            if (window instanceof ContextMenu menu
                    && menu.getItems().stream().anyMatch(i -> itemId.equals(i.getId()))) {
                return menu;
            }
        }
        throw new AssertionError("no menu with " + itemId);
    }

    private OpenDocument open(Path dir, String name, String text) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, text);
        fx(() -> {
            host.openFile(file);
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveDocument()
                .map(d -> file.equals(d.getPath())).orElse(false)
                && host.getActiveText().map(t -> !t.isEmpty()).orElse(false));
        return host.getActiveDocument().orElseThrow();
    }

    private static <T> T fx(java.util.concurrent.Callable<T> action) throws Exception {
        return WaitForAsyncUtils.waitForAsyncFx(3000, action);
    }
}
