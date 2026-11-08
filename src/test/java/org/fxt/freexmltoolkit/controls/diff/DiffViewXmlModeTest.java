package org.fxt.freexmltoolkit.controls.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.XmlStructuralDiff;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff.Change;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The Compare view's XML mode: structural differences, navigation, options, back to text. */
@ExtendWith(ApplicationExtension.class)
class DiffViewXmlModeTest {

    private static final String LEFT = """
            <order id="1" date="2026-11-08">
              <item sku="A">Apple</item>
              <item sku="B">Pear</item>
            </order>
            """;
    /** Same data, other formatting and attribute order; one changed value, one added element. */
    private static final String RIGHT = """
            <order date="2026-11-08" id="1"><item sku="A">Apple</item>
            <item sku="B">Plum</item><item sku="C">Fig</item></order>
            """;

    private TabPane tabs;

    @Start
    void start(Stage stage) {
        tabs = new TabPane();
        stage.setScene(new Scene(tabs, 1100, 700));
        stage.show();
    }

    private DiffView open(Path tmp, String left, String right) throws Exception {
        Path rightFile = tmp.resolve("right.xml");
        Files.writeString(rightFile, right);
        DiffView diff = WaitForAsyncUtils.waitForAsyncFx(10000, () -> {
            DiffView d = new DiffView("left.xml", left, text -> { }, rightFile.toFile());
            tabs.getTabs().add(d);
            tabs.getSelectionModel().select(d);
            return d;
        });
        WaitForAsyncUtils.waitForFxEvents();
        return diff;
    }

    @Test
    void xmlModeListsOnlyRealDifferences(@TempDir Path tmp) throws Exception {
        DiffView diff = open(tmp, LEFT, RIGHT);
        assertTrue(diff.isXmlModeAvailableForTesting());
        long lineChunks = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            diff.setModeForTesting(DiffView.Mode.TEXT);
            return diff.getChunksForTesting().stream().filter(c -> !c.isEqual()).count();
        });
        assertTrue(lineChunks > 0, "the line diff sees the reformatting");

        List<XmlStructuralDiff.Difference> differences = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            diff.setModeForTesting(DiffView.Mode.XML);
            return diff.getXmlDifferencesForTesting();
        });
        assertEquals(2, differences.size(), differences.toString());
        assertTrue(differences.stream().anyMatch(d -> d.change() == Change.CHANGED && "Pear".equals(d.leftValue())
                && "Plum".equals(d.rightValue())), differences.toString());
        assertTrue(differences.stream().anyMatch(d -> d.change() == Change.ADDED && "item".equals(d.name())),
                differences.toString());
        assertEquals("2 differences", WaitForAsyncUtils.waitForAsyncFx(2000, diff::getXmlSummaryForTesting));
        assertTrue(diff.getChunksForTesting().isEmpty(), "no merge arrows in XML mode");

        // Next walks the differences in order of the left lines and moves both panes there: first the
        // added <item> (left: its parent <order>, line 1), then the changed text (left line 3)
        int[] first = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            diff.navigateForTesting(1);
            return diff.getCaretParagraphsForTesting();
        });
        assertEquals(0, first[0]);
        assertEquals(1, first[1], "right: the added <item> on line 2");
        int[] second = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            diff.navigateForTesting(1);
            return diff.getCaretParagraphsForTesting();
        });
        assertEquals(2, second[0], "left: line 3 (<item sku=\"B\">Pear)");
        assertEquals(1, second[1], "right: line 2");
    }

    @Test
    void optionsAndErrors(@TempDir Path tmp) throws Exception {
        DiffView diff = open(tmp, "<r><a/><!-- x --><b/></r>", "<r><b/><a/></r>");
        List<XmlStructuralDiff.Difference> strict = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            diff.setXmlOptions(XmlStructuralDiff.Options.DEFAULTS);
            diff.setModeForTesting(DiffView.Mode.XML);
            return diff.getXmlDifferencesForTesting();
        });
        assertFalse(strict.isEmpty());
        String relaxed = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            diff.setXmlOptions(new XmlStructuralDiff.Options(true, true, false, true, List.of()));
            diff.setModeForTesting(DiffView.Mode.XML);
            return diff.getXmlSummaryForTesting();
        });
        assertEquals("The documents are equivalent as XML.", relaxed);

        String broken = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            diff.setTextsForTesting("<r>", "<r/>");
            return diff.getXmlSummaryForTesting();
        });
        assertTrue(broken.startsWith("Cannot compare as XML. Left: Not well-formed"), broken);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> diff.setXmlOptions(XmlStructuralDiff.Options.DEFAULTS));
    }

    @Test
    void xmlModeNeedsXmlOnBothSides(@TempDir Path tmp) throws Exception {
        DiffView diff = open(tmp, "plain text", "<a/>");
        assertFalse(diff.isXmlModeAvailableForTesting());
    }
}
