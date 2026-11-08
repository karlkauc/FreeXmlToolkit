package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.input.Clipboard;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The Large File Viewer: indexing, go to line, selection and copy, find, opening a large file. */
@ExtendWith(ApplicationExtension.class)
class LargeFileViewTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 600));
        stage.show();
    }

    @AfterEach
    void reset() {
        EditorFileSync.largeFileAnswer = null;
    }

    private static Path orders(Path dir, int count) throws Exception {
        Path file = dir.resolve("orders.xml");
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("<orders>\n");
            for (int i = 1; i <= count; i++) {
                out.write("  <order id=\"" + i + "\"><amount>" + (i * 3) + "</amount></order>\n");
            }
            out.write("</orders>\n");
        }
        return file;
    }

    private LargeFileView open(Path file) throws Exception {
        LargeFileView view = WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.openLargeFileViewer(file));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.indexForTesting() != null));
        return view;
    }

    @Test
    void goToLineSelectCopyAndFind(@TempDir Path dir) throws Exception {
        Path file = orders(dir, 20_000);
        LargeFileView view = open(file);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, view::infoForTesting).contains("20,002 lines"));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.setLineForTesting("15000");
            view.onGoToLine();
            return null;
        });
        assertEquals(14_999, WaitForAsyncUtils.waitForAsyncFx(2000, view::selectionForTesting)[0]);
        WaitForAsyncUtils.waitForFxEvents();
        int first = WaitForAsyncUtils.waitForAsyncFx(2000, view::firstVisibleRowForTesting);
        assertTrue(first > 14_900 && first <= 14_999, "scrolled to the line: " + first + " "
                + WaitForAsyncUtils.waitForAsyncFx(2000, view::flowStateForTesting));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, view::statusForTesting).startsWith("Line 15,000"));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.showRow(1);
            view.copySelectionForTesting();
            return null;
        });
        assertEquals("  <order id=\"1\"><amount>3</amount></order>",
                WaitForAsyncUtils.waitForAsyncFx(2000, () -> Clipboard.getSystemClipboard().getString()));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.setFindForTesting("id=\"1999\\d\"", true);
            view.onFind();
            return null;
        });
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.hitsForTesting().size() == 10));
        assertEquals(19_991, WaitForAsyncUtils.waitForAsyncFx(2000, view::hitsForTesting).get(0).line());
        assertEquals(19_990, WaitForAsyncUtils.waitForAsyncFx(2000, view::selectionForTesting)[0],
                "the first hit is shown");
    }

    @Test
    void structureTreeRecordsShowInTextAndExtract(@TempDir Path dir) throws Exception {
        Path file = orders(dir, 2_500);
        LargeFileView view = open(file);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.selectStructureForTesting();
            return null;
        });
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> view.recordsForTesting().size() == LargeFileView.PAGE));
        assertEquals(List.of("#", "Element", "@id", "amount"),
                WaitForAsyncUtils.waitForAsyncFx(2000, view::recordColumnsForTesting));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, view::recordsLabelForTesting)
                .startsWith("Children of /orders[1]: 1,000 loaded, more available"));
        var root = WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.treeForTesting().getRoot());
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.getChildren().size() == LargeFileView.PAGE + 1);
        assertEquals("order id=\"1\"", LargeFileView.label(
                (org.fxt.freexmltoolkit.service.largefile.LargeXmlStructure.Node) root.getChildren().get(0).getValue()));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.onLoadMore();
            return null;
        });
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> view.recordsForTesting().size() == 2 * LargeFileView.PAGE));
        assertEquals(2_001, root.getChildren().size(), "the tree got the page too, and a new Load more entry");

        var tenth = WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.recordsForTesting().get(9));
        assertEquals("/orders[1]/order[10]", tenth.xpath());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.showInText(tenth);
            return null;
        });
        assertEquals(10, WaitForAsyncUtils.waitForAsyncFx(2000, view::selectionForTesting)[0], "line 11, row 10");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.extract(tenth);
            return null;
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getActiveText().map(t -> t.startsWith("<order id=\"10\">")).orElse(false)));
        assertEquals("<order id=\"10\"><amount>30</amount></order>",
                WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveText().orElse("")));
    }

    @Test
    void validatesAgainstASchemaAndListsProblems(@TempDir Path dir) throws Exception {
        Path xsd = Files.writeString(dir.resolve("orders.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="orders"><xs:complexType><xs:sequence>
                    <xs:element name="order" maxOccurs="unbounded"><xs:complexType><xs:sequence>
                      <xs:element name="amount" type="xs:short"/>
                    </xs:sequence><xs:attribute name="id" type="xs:int"/></xs:complexType></xs:element>
                  </xs:sequence></xs:complexType></xs:element>
                </xs:schema>
                """);
        Path file = orders(dir, 20_000);   // amount = 3 * id: above 32767 from order 10,923 on
        LargeFileView view = open(file);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.validate(xsd.toFile(), true);
            return null;
        });
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> !view.problemsForTesting().isEmpty()));
        var problems = WaitForAsyncUtils.waitForAsyncFx(2000, view::problemsForTesting);
        assertEquals(10_924, problems.get(0).line(), "order 10,923 is on line 10,924");
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, view::problemsLabelForTesting)
                .startsWith("orders.xsd: 1,000+ problems (stopped after 1000)"));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.validate(null, false);
            return null;
        });
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> view.problemsLabelForTesting().startsWith("The document is well-formed.")));
    }

    @Test
    void highlightsXml() {
        List<Text> parts = LargeFileView.highlight("<a id=\"1\">x<!-- c --></a>");
        assertEquals("<a", parts.get(0).getText());
        assertTrue(parts.get(0).getStyleClass().contains("fxt-lf-tag"));
        assertTrue(parts.stream().anyMatch(t -> t.getText().equals("id") && t.getStyleClass().contains("fxt-lf-attr")));
        assertTrue(parts.stream().anyMatch(t -> t.getText().equals("\"1\"") && t.getStyleClass().contains("fxt-lf-value")));
        assertTrue(parts.stream().anyMatch(t -> t.getText().equals("<!-- c -->") && t.getStyleClass().contains("fxt-lf-comment")));
        assertEquals("<a id=\"1\">x<!-- c --></a>", parts.stream().map(Text::getText).reduce("", String::concat));
    }

    @Test
    void aLargeFileCanOpenInTheViewer(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("big.xml");
        byte[] line = "<r>0123456789012345678901234567890123456789012345678901234567890123456789</r>\n"
                .getBytes(StandardCharsets.UTF_8);
        long target = LargeFileGuard.WARN_BYTES + 1024;
        try (var out = new java.io.BufferedOutputStream(Files.newOutputStream(file), 1 << 20)) {
            for (long written = 0; written < target; written += line.length) {
                out.write(line);
            }
        }
        boolean[] asked = {false};
        EditorFileSync.largeFileAnswer = (path, tooLarge) -> {
            asked[0] = true;
            return EditorFileSync.LargeFileChoice.VIEWER;
        };
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.lookup("#large-file-flow") != null));
        assertTrue(asked[0]);
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getOpenDocuments().stream().anyMatch(d -> file.equals(d.getPath()))),
                "no editor tab for the file");
        LargeFileView view = WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openLargeFileViewer(file));
        assertEquals(1, WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.lookupAll(".fxt-large-file-view").size()), "the open viewer is reused");
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.indexForTesting() != null));
    }
}
