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
    void optionsAndToolsShareOneCollapsedSection() {
        WaitForAsyncUtils.waitForFxEvents();
        assertNull(panel.lookup("#transform-overflow"), "no hidden ⋮ menu: options and tools are one section");
        var section = (SidePanelSection) panel.lookup("#transform-options-section");
        assertNotNull(section, "OPTIONS & TOOLS must exist");
        assertTrue(section.isCollapsed(), "secondary options start collapsed below the run");
        for (String id : new String[]{"#transform-live-preview", "#transform-watch-xslt", "#transform-profile",
                "#transform-trace", "#transform-auto-open"}) {
            assertInstanceOf(javafx.scene.control.CheckBox.class, panel.lookup(id), id + " must be a visible switch");
        }
        assertTrue(panel.toolLabels().containsAll(java.util.List.of(
                        "Debug XSLT…", "Batch Transform…", "Execution Statistics")),
                "the tools must stay visible rows, was: " + panel.toolLabels());
    }

    @Test
    void outputMethodIsOneChoiceWithAllSixFormats() {
        WaitForAsyncUtils.waitForFxEvents();
        @SuppressWarnings("unchecked")
        var combo = (javafx.scene.control.ComboBox<TransformPanel.FormatChoice>) panel.lookup("#transform-output-method");
        assertNotNull(combo, "OUTPUT METHOD must be a single choice field");
        assertEquals(java.util.List.of("Auto", "XML", "HTML", "XHTML", "Text", "JSON"),
                combo.getItems().stream().map(Object::toString).toList());
        assertEquals(TransformPanel.FormatChoice.AUTO, panel.getFormatChoice(), "Auto must be the default");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setOutputFormat(org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.JSON);
            return null;
        });
        assertEquals(TransformPanel.FormatChoice.JSON, panel.getFormatChoice());
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
        var sectionLabels = WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.lookupAll(".fxt-sp-section-header").stream()
                .flatMap(h -> h.lookupAll(".label").stream())
                .filter(n -> n instanceof javafx.scene.control.Label)
                .map(n -> ((javafx.scene.control.Label) n).getText())
                .toList());
        assertTrue(sectionLabels.containsAll(java.util.List.of(
                        "STYLESHEET", "INPUT", "OUTPUT METHOD", "PARAMETERS")),
                "the mockup sections must exist, was: " + sectionLabels);
        // The primary action is a full-width "Run Transform" button.
        assertNotNull(panel.lookup("#transform-run"), "the Run Transform primary button must exist");
    }

    @Test
    void queriesPointToTheQueryConsoleInsteadOfOwnSections() {
        WaitForAsyncUtils.waitForFxEvents();
        var sectionLabels = WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.lookupAll(".fxt-sp-section-header").stream()
                .flatMap(h -> h.lookupAll(".label").stream())
                .filter(n -> n instanceof javafx.scene.control.Label)
                .map(n -> ((javafx.scene.control.Label) n).getText())
                .toList());
        assertFalse(sectionLabels.contains("XPATH") || sectionLabels.contains("XQUERY"),
                "queries against the open document live in the Query Console (UX plan 3.3): " + sectionLabels);
        int before = host.queryConsoleRequestsProperty().get();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ((javafx.scene.control.Button) panel.lookup("#transform-open-query-console")).fire();
            return null;
        });
        assertEquals(before + 1, host.queryConsoleRequestsProperty().get(), "the pointer opens the Query Console");
    }

    @Test
    void runAutomaticallyCanBeSwitchedOff() {
        WaitForAsyncUtils.waitForFxEvents();
        var box = (javafx.scene.control.CheckBox) panel.lookup("#transform-auto-run");
        assertNotNull(box, "the automatic run is visible next to Run Transform (UX plan 3.6)");
        assertTrue(box.isSelected(), "on by default (the previous behaviour)");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setRunAutomatically(false);
            return null;
        });
        assertFalse(panel.readyToAutoRun(), "no automatic run while switched off");
    }
}
