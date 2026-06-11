package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
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
 * Verifies the redesigned Transform side panel (Figma mockup "Redesign · Unified —
 * Transform (XSLT)"): all former settings remain available — the secondary toggles
 * and tools moved into the header's overflow (⋮) menu, the output format became a
 * segmented control, and the INPUT section can override the active editor with an
 * explicit file.
 */
@ExtendWith(ApplicationExtension.class)
class TransformPanelRedesignTest {

    private static final String XSLT = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="xml"/>
              <xsl:template match="/greeting"><out><xsl:value-of select="."/></out></xsl:template>
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
    void overflowMenuKeepsAllSecondaryOptions() {
        WaitForAsyncUtils.waitForFxEvents();
        MenuButton overflow = (MenuButton) panel.lookup("#transform-overflow");
        assertNotNull(overflow, "the panel header must carry the ⋮ overflow menu");
        var texts = overflow.getItems().stream().map(MenuItem::getText)
                .filter(java.util.Objects::nonNull).toList();
        assertTrue(texts.containsAll(java.util.List.of(
                        "Live preview", "Watch stylesheet file", "Profile run", "Trace run",
                        "Auto-open result tab", "Debug XSLT…", "Batch Transform…")),
                "all former ADVANCED options must remain reachable, was: " + texts);
        long checkItems = overflow.getItems().stream().filter(i -> i instanceof CheckMenuItem).count();
        assertEquals(5, checkItems, "the five toggles must be check menu items");
    }

    @Test
    void outputMethodSegmentsCoverAllSixChoices() {
        WaitForAsyncUtils.waitForFxEvents();
        var segments = panel.lookupAll(".fxt-seg").stream()
                .filter(n -> n instanceof javafx.scene.control.ToggleButton)
                .map(n -> ((javafx.scene.control.ToggleButton) n).getText())
                .toList();
        assertTrue(segments.containsAll(java.util.List.of("Auto", "XML", "HTML", "XHTML", "Text", "JSON")),
                "the OUTPUT METHOD control must keep all six format choices, was: " + segments);
        // Default: Auto selected.
        boolean autoSelected = panel.lookupAll(".fxt-seg").stream()
                .anyMatch(n -> n instanceof javafx.scene.control.ToggleButton t
                        && "Auto".equals(t.getText()) && t.isSelected());
        assertTrue(autoSelected, "Auto must be the default output method");
    }

    @Test
    void inputOverrideTransformsTheChosenFileInsteadOfTheActiveEditor(@TempDir Path tmp) throws Exception {
        // No document open — only the override file feeds the transform.
        Path input = tmp.resolve("override.xml");
        Files.writeString(input, "<greeting>FromFile</greeting>");
        Path sheet = tmp.resolve("t.xslt");
        Files.writeString(sheet, XSLT);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setXsltFile(sheet.toFile());
            panel.setInputOverride(input.toFile());
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> panel.getOutputText().contains("<out>FromFile</out>"));
        assertTrue(panel.getOutputText().contains("<out>FromFile</out>"), panel.getOutputText());

        // "Use active editor" reverts: with no document open, the guard message appears.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setInputOverride(null);
            panel.transform();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS,
                () -> panel.getOutputText().equals("No document open."));
        assertEquals("No document open.", panel.getOutputText());
    }

    @Test
    void mockupSectionsArePresent() {
        WaitForAsyncUtils.waitForFxEvents();
        var sectionLabels = panel.lookupAll(".fxt-sp-section-header").stream()
                .flatMap(h -> h.lookupAll(".label").stream())
                .filter(n -> n instanceof javafx.scene.control.Label)
                .map(n -> ((javafx.scene.control.Label) n).getText())
                .toList();
        assertTrue(sectionLabels.containsAll(java.util.List.of(
                        "STYLESHEET", "INPUT", "OUTPUT METHOD", "PARAMETERS", "XPATH", "XQUERY")),
                "the mockup sections must exist, was: " + sectionLabels);
        // The primary action is a full-width "Run Transform" button.
        assertNotNull(panel.lookup("#transform-run"), "the Run Transform primary button must exist");
    }
}
