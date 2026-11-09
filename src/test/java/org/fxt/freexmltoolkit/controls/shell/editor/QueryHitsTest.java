package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Query Console hits: shown in the current view of the document, stepped through, stale after edits. */
@ExtendWith(ApplicationExtension.class)
class QueryHitsTest {

    private static final String XML = """
            <Fund>
              <Positions>
                <Position id="a"><Name>A</Name><TotalPercentage>40</TotalPercentage></Position>
                <Position id="b"><Name>B</Name><TotalPercentage>20</TotalPercentage></Position>
                <Position id="c"><Name>C</Name><TotalPercentage>35</TotalPercentage></Position>
              </Positions>
            </Fund>
            """;

    private EditorHost host;
    private QueryConsole console;

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        host = new EditorHost();
        console = new QueryConsole(host);
        VBox root = new VBox(host, console);
        VBox.setVgrow(host, Priority.ALWAYS);
        console.setPrefHeight(260);
        stage.setScene(new Scene(root, 1100, 800));
        stage.show();
    }

    private void open(Path tmp, ViewMode mode) throws Exception {
        Path xml = Files.writeString(tmp.resolve("fund.xml"), XML);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("Position")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveViewMode(mode));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.activeViewModeProperty().get() == mode));
    }

    private void run(String xpath) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            console.setXPath(xpath);
            console.runForTest();
            return null;
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> !"Running…".equals(console.getResultsText()) && console.getResultsText() != null
                        && !console.getResultsText().isBlank()));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void textViewSelectsTheHitsAndStepsThroughThem(@TempDir Path tmp) throws Exception {
        open(tmp, ViewMode.TEXT);
        run("//Position[TotalPercentage > 30]");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "1 of 2".equals(
                WaitForAsyncUtils.waitForAsyncFx(2000, () -> console.hits().counterText())));
        String selected = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveCodeArea().getSelectedText());
        assertTrue(selected.startsWith("<Position id=\"a\""), selected);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> console.hits().step(1));
        assertEquals("2 of 2", WaitForAsyncUtils.waitForAsyncFx(2000, () -> console.hits().counterText()));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveCodeArea().getSelectedText())
                .startsWith("<Position id=\"c\""));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> console.hits().step(1));
        assertEquals("1 of 2", WaitForAsyncUtils.waitForAsyncFx(2000, () -> console.hits().counterText()), "wraps around");
        assertEquals(ViewMode.TEXT, WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeViewModeProperty().get()));
    }

    @Test
    void treeViewSelectsTheNode(@TempDir Path tmp) throws Exception {
        open(tmp, ViewMode.TREE);
        run("//Position[TotalPercentage > 30]/Name");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> console.hits().step(1)); // the second hit: C
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.activeXmlNodeProperty().get() instanceof XmlElement e && "Name".equals(e.getName())
                        && "C".equals(e.getTextContent())));
        assertEquals(ViewMode.TREE, WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeViewModeProperty().get()));
    }

    @Test
    void graphicViewHighlightsTheTableRowsAndValuesGetNoHits(@TempDir Path tmp) throws Exception {
        open(tmp, ViewMode.GRAPHIC);
        run("//Position[TotalPercentage > 30]");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "1 of 2".equals(
                WaitForAsyncUtils.waitForAsyncFx(2000, () -> console.hits().counterText())));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            return host.activeXmlNodeProperty().get() instanceof XmlElement e && "Position".equals(e.getName());
        }));
        assertEquals(ViewMode.GRAPHIC, WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeViewModeProperty().get()));

        run("sum(//TotalPercentage)");
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> console.hits().locatedHits().isEmpty()));
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> console.hits().bar().isVisible()));
    }

    @Test
    void anEditMakesTheHitsStale(@TempDir Path tmp) throws Exception {
        open(tmp, ViewMode.TEXT);
        run("//Position");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> "1 of 3".equals(
                WaitForAsyncUtils.waitForAsyncFx(2000, () -> console.hits().counterText())));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveCodeArea().insertText(0, "<!-- x -->\n"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> console.hits().step(1));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> console.hits().counterText()).contains("changed"));
    }
}
