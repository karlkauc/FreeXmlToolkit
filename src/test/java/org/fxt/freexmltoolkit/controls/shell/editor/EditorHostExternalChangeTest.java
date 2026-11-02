package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.view.UnresolvedReferencesBar;
import org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.XmlCanvasView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification that the {@link EditorHost} notices files changed or deleted on disk by
 * another program and applies the user's answer (reload/ignore, keep/close).
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostExternalChangeTest {

    /** Scripted answers in place of the modal dialogs; records what was asked. */
    private static final class FakePrompt implements ExternalChangePrompt {
        volatile ChangedChoice changedAnswer = ChangedChoice.IGNORE;
        volatile DeletedChoice deletedAnswer = DeletedChoice.KEEP;
        final List<String> asked = java.util.Collections.synchronizedList(new ArrayList<>());

        @Override
        public ChangedChoice fileChanged(OpenDocument document, boolean dirty) {
            asked.add("changed:" + document.getDisplayName() + ":" + dirty);
            return changedAnswer;
        }

        @Override
        public DeletedChoice fileDeleted(OpenDocument document, boolean dirty) {
            asked.add("deleted:" + document.getDisplayName() + ":" + dirty);
            return deletedAnswer;
        }

        @Override
        public ChangedChoice dependencyChanged(OpenDocument document, List<Path> files) {
            asked.add("dependency:" + document.getDisplayName() + ":" + files.stream()
                    .map(file -> file.getFileName().toString()).sorted().toList());
            return changedAnswer;
        }
    }

    private EditorHost host;
    private final FakePrompt prompt = new FakePrompt();
    private long mtimeOffset = 10_000;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        host.setExternalChangePrompt(prompt);
        stage.setScene(new Scene(host, 800, 600));
        stage.show();
    }

    @Test
    void reloadReplacesTheContentAndLeavesTheDocumentClean(@TempDir Path tmp) throws Exception {
        Path file = open(tmp, "doc.xml", "<old/>");
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;

        writeExternally(file, "<new-from-disk/>");
        checkUntil(() -> "<new-from-disk/>".equals(activeText()));

        assertEquals(List.of("changed:doc.xml:false"), prompt.asked);
        assertFalse(host.getActiveDocument().orElseThrow().isDirty());
        check();
        assertEquals(1, prompt.asked.size(), "a reloaded file must not be reported again");
    }

    @Test
    void reloadDiscardsUnsavedChanges(@TempDir Path tmp) throws Exception {
        Path file = open(tmp, "doc.xml", "<old/>");
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeEditorView().setText("<mine/>"));
        assertTrue(host.getActiveDocument().orElseThrow().isDirty());

        writeExternally(file, "<theirs/>");
        checkUntil(() -> "<theirs/>".equals(activeText()));

        assertEquals(List.of("changed:doc.xml:true"), prompt.asked);
        assertFalse(host.getActiveDocument().orElseThrow().isDirty());
    }

    @Test
    void ignoreKeepsTheContentMarksItDirtyAndAsksOnlyOnce(@TempDir Path tmp) throws Exception {
        Path file = open(tmp, "doc.xml", "<old/>");

        writeExternally(file, "<new-from-disk/>");
        checkUntil(() -> !prompt.asked.isEmpty());
        check();

        assertEquals(List.of("changed:doc.xml:false"), prompt.asked);
        assertEquals("<old/>", activeText());
        assertTrue(host.getActiveDocument().orElseThrow().isDirty(),
                "editor and disk differ, so the document has something to save");

        writeExternally(file, "<changed-again/>");
        checkUntil(() -> prompt.asked.size() == 2);
    }

    @Test
    void touchedFileWithIdenticalContentDoesNotPrompt(@TempDir Path tmp) throws Exception {
        Path file = open(tmp, "doc.xml", "<same/>");
        DiskStamp before = host.getActiveDocument().orElseThrow().getDiskStamp();

        writeExternally(file, "<same/>");
        checkUntil(() -> !before.equals(host.getActiveDocument().orElseThrow().getDiskStamp()));

        assertTrue(prompt.asked.isEmpty());
        assertFalse(host.getActiveDocument().orElseThrow().isDirty());
    }

    @Test
    void savingFromTheEditorIsNotAnExternalChange(@TempDir Path tmp) throws Exception {
        Path file = open(tmp, "doc.xml", "<old/>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeEditorView().setText("<saved-by-me/>"));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.saveActive()).get(30, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals("<saved-by-me/>", Files.readString(file));

        check();

        assertTrue(prompt.asked.isEmpty());
        assertFalse(host.getActiveDocument().orElseThrow().isDirty());
    }

    @Test
    void keepingADeletedFileLeavesTheTabOpenAndSavingRecreatesIt(@TempDir Path tmp) throws Exception {
        Path file = open(tmp, "doc.xml", "<keep-me/>");

        Files.delete(file);
        checkUntil(() -> !prompt.asked.isEmpty());
        check();

        assertEquals(List.of("deleted:doc.xml:false"), prompt.asked, "a kept file is asked about once");
        assertEquals(1, host.getOpenDocuments().size());
        assertEquals("<keep-me/>", activeText());
        assertTrue(host.getActiveDocument().orElseThrow().isDirty());

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.saveActive()).get(30, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals("<keep-me/>", Files.readString(file));
        check();
        assertEquals(1, prompt.asked.size(), "recreating the file by saving is not an external change");
        assertFalse(host.getActiveDocument().orElseThrow().isDirty());
    }

    @Test
    void closingADeletedFileRemovesTabAndDocument(@TempDir Path tmp) throws Exception {
        Path keep = open(tmp, "other.xml", "<other/>");
        Path file = open(tmp, "doc.xml", "<gone/>");
        prompt.deletedAnswer = ExternalChangePrompt.DeletedChoice.CLOSE;

        Files.delete(file);
        checkUntil(() -> host.getOpenDocuments().size() == 1);

        assertEquals(List.of("deleted:doc.xml:false"), prompt.asked);
        assertEquals(keep, host.getOpenDocuments().get(0).getPath());
        assertEquals("<other/>", activeText());
    }

    @Test
    void reloadRebuildsTheStructuredViewOfASchema(@TempDir Path tmp) throws Exception {
        Path file = open(tmp, "schema.xsd", schemaWithType("OldType"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveViewMode(ViewMode.TREE));
        assertEquals(List.of("OldType"), namedTypes());
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;

        writeExternally(file, schemaWithType("NewType"));
        checkUntil(() -> List.of("NewType").equals(namedTypes()));

        assertEquals(ViewMode.TREE, host.activeViewModeProperty().get(), "the view mode survives a reload");
        assertFalse(host.getActiveDocument().orElseThrow().isDirty());
    }

    @Test
    void reloadRebuildsTheGraphicViewOfASchema(@TempDir Path tmp) throws Exception {
        Path file = open(tmp, "schema.xsd", schemaWithElement("oldCard", ""));
        showGraphic();
        waitForCard("oldCard");
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;

        writeExternally(file, schemaWithElement("newCard", ""));
        checkUntil(() -> hasCard("newCard"));

        assertEquals(List.of("changed:schema.xsd:false"), prompt.asked);
        assertFalse(hasCard("oldCard"));
        assertEquals(ViewMode.GRAPHIC, host.activeViewModeProperty().get());
        assertFalse(host.getActiveDocument().orElseThrow().isDirty());
    }

    @Test
    void reloadRebuildsTheGraphicViewOfALargeSchema(@TempDir Path tmp) throws Exception {
        String padding = "<!-- " + "x".repeat(300 * 1024) + " -->";
        Path file = open(tmp, "large.xsd", schemaWithElement("oldCard", padding));
        showGraphic();
        waitForCard("oldCard");
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;

        writeExternally(file, schemaWithElement("newCard", padding));
        checkUntil(() -> hasCard("newCard"));

        assertEquals(List.of("changed:large.xsd:false"), prompt.asked);
        assertFalse(hasCard("oldCard"));
    }

    @Test
    void reloadRebuildsTheGridOfAnXmlDocument(@TempDir Path tmp) throws Exception {
        Path file = open(tmp, "doc.xml", "<root><a>1</a></root>");
        showGraphic();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> gridRows() > 0);
        int before = gridRows();
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;

        writeExternally(file, "<root><a>1</a><b>2</b><c>3</c></root>");
        checkUntil(() -> gridRows() > before);

        assertEquals(List.of("changed:doc.xml:false"), prompt.asked);
        assertEquals(ViewMode.GRAPHIC, host.activeViewModeProperty().get());
    }

    @Test
    void changedIncludeAsksOnceAndReloadShowsItsContent(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("core.xsd"), schemaWithElement("oldCard", ""));
        Path main = open(tmp, "main.xsd", schemaIncluding("core.xsd"));
        showGraphic();
        waitForCard("oldCard");
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;

        writeExternally(tmp.resolve("core.xsd"), schemaWithElement("newCard", ""));
        checkUntil(() -> hasCard("newCard"));
        check();

        assertEquals(List.of("dependency:main.xsd:[core.xsd]"), prompt.asked);
        assertFalse(hasCard("oldCard"));
        assertFalse(host.getActiveDocument().orElseThrow().isDirty(), "the main file did not change");
        assertEquals(schemaIncluding("core.xsd"), Files.readString(main));
    }

    @Test
    void aTouchedIncludeAsksNothingAndALaterChangeStillAsks(@TempDir Path tmp) throws Exception {
        Path core = tmp.resolve("core.xsd");
        Files.writeString(core, schemaWithElement("oldCard", ""));
        open(tmp, "main.xsd", schemaIncluding("core.xsd"));
        showGraphic();
        waitForCard("oldCard");
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;

        writeExternally(core, schemaWithElement("oldCard", ""));
        check();
        check();
        assertEquals(List.of(), prompt.asked, "the same content, only a new modification time");

        writeExternally(core, schemaWithElement("newCard", ""));
        checkUntil(() -> hasCard("newCard"));
        assertEquals(List.of("dependency:main.xsd:[core.xsd]"), prompt.asked);
    }

    @Test
    void aChangeOfTheSameSizeIsNoticed(@TempDir Path tmp) throws Exception {
        Path core = tmp.resolve("core.xsd");
        Files.writeString(core, schemaWithElement("cardA", ""));
        open(tmp, "main.xsd", schemaIncluding("core.xsd"));
        showGraphic();
        waitForCard("cardA");
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;

        writeExternally(core, schemaWithElement("cardB", ""));
        checkUntil(() -> hasCard("cardB"));
        assertEquals(List.of("dependency:main.xsd:[core.xsd]"), prompt.asked);
    }

    @Test
    void aLargeSchemaReparsesItsChangedIncludeInTheBackground(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("core.xsd"), schemaWithElement("oldCard", ""));
        String padding = "<!-- " + "x".repeat(300 * 1024) + " -->";
        open(tmp, "main.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  %s
                  <xs:include schemaLocation="core.xsd"/>
                </xs:schema>
                """.formatted(padding));
        showGraphic();
        waitForCard("oldCard");
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;

        writeExternally(tmp.resolve("core.xsd"), schemaWithElement("newCard", ""));
        checkUntil(() -> hasCard("newCard"));
        check();

        assertEquals(List.of("dependency:main.xsd:[core.xsd]"), prompt.asked);
    }

    @Test
    void twoChangedIncludesAskOneQuestion(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("a.xsd"), schemaWithElement("a", ""));
        Files.writeString(tmp.resolve("b.xsd"), schemaWithElement("b", ""));
        open(tmp, "main.xsd", schemaIncluding("a.xsd", "b.xsd"));
        showGraphic();
        waitForCard("a");

        writeExternally(tmp.resolve("a.xsd"), schemaWithElement("a2", ""));
        writeExternally(tmp.resolve("b.xsd"), schemaWithElement("b2", ""));
        checkUntil(() -> !prompt.asked.isEmpty());
        check();

        assertEquals(List.of("dependency:main.xsd:[a.xsd, b.xsd]"), prompt.asked);
    }

    @Test
    void ignoringAChangedIncludeKeepsTheModelAndAsksAgainOnlyForANewChange(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("core.xsd"), schemaWithElement("oldCard", ""));
        open(tmp, "main.xsd", schemaIncluding("core.xsd"));
        showGraphic();
        waitForCard("oldCard");

        writeExternally(tmp.resolve("core.xsd"), schemaWithElement("newCard", ""));
        checkUntil(() -> !prompt.asked.isEmpty());
        check();

        assertEquals(1, prompt.asked.size());
        assertTrue(hasCard("oldCard"));
        assertFalse(host.getActiveDocument().orElseThrow().isDirty(), "the document itself is unchanged");

        writeExternally(tmp.resolve("core.xsd"), schemaWithElement("newestCard", ""));
        checkUntil(() -> prompt.asked.size() == 2);
    }

    @Test
    void nestedIncludeAndImportAreNoticed(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("nested.xsd"), schemaWithElement("nestedCard", ""));
        Files.writeString(tmp.resolve("middle.xsd"), schemaIncluding("nested.xsd"));
        Files.writeString(tmp.resolve("other.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:other"/>
                """);
        open(tmp, "main.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="middle.xsd"/>
                  <xs:import namespace="urn:other" schemaLocation="other.xsd"/>
                </xs:schema>
                """);
        showGraphic();
        waitForCard("nestedCard");

        writeExternally(tmp.resolve("nested.xsd"), schemaWithElement("nestedCard2", ""));
        checkUntil(() -> !prompt.asked.isEmpty());
        assertEquals(List.of("dependency:main.xsd:[nested.xsd]"), prompt.asked);

        writeExternally(tmp.resolve("other.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:other">
                  <xs:element name="imported" type="xs:string"/>
                </xs:schema>
                """);
        checkUntil(() -> prompt.asked.size() == 2);
        assertEquals("dependency:main.xsd:[other.xsd]", prompt.asked.get(1));
    }

    @Test
    void unsavedTextSurvivesAReloadCausedByAnInclude(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("core.xsd"), schemaWithElement("oldCard", ""));
        Path main = open(tmp, "main.xsd", schemaIncluding("core.xsd"));
        String edited = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="core.xsd"/>
                  <xs:element name="typedByMe" type="xs:string"/>
                </xs:schema>
                """;
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.activeEditorView().setText(edited));
        showGraphic();
        waitForCard("typedByMe");
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;

        writeExternally(tmp.resolve("core.xsd"), schemaWithElement("newCard", ""));
        checkUntil(() -> hasCard("newCard"));

        assertTrue(hasCard("typedByMe"));
        assertEquals(edited, activeText());
        assertTrue(host.getActiveDocument().orElseThrow().isDirty());
        assertEquals(schemaIncluding("core.xsd"), Files.readString(main));
    }

    @Test
    void deletedIncludeIsReportedByTheBarAfterReload(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("core.xsd"), schemaWithElement("oldCard", ""));
        open(tmp, "main.xsd", schemaIncluding("core.xsd"));
        showGraphic();
        waitForCard("oldCard");
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;

        Files.delete(tmp.resolve("core.xsd"));
        checkUntil(() -> !hasCard("oldCard"));

        assertEquals(List.of("dependency:main.xsd:[core.xsd]"), prompt.asked);
        assertTrue(barMentions("core.xsd"));
    }

    @Test
    void aMissingIncludeThatAppearsIsNoticed(@TempDir Path tmp) throws Exception {
        open(tmp, "main.xsd", schemaIncluding("later.xsd"));
        showGraphic();
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> barMentions("later.xsd"));

        writeExternally(tmp.resolve("later.xsd"), schemaWithElement("arrived", ""));
        checkUntil(() -> hasCard("arrived"));

        assertEquals(List.of("dependency:main.xsd:[later.xsd]"), prompt.asked);
    }

    @Test
    void aClosedTabNoLongerWatchesItsIncludes(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("core.xsd"), schemaWithElement("oldCard", ""));
        open(tmp, "other.xml", "<other/>");
        Path main = open(tmp, "main.xsd", schemaIncluding("core.xsd"));
        showGraphic();
        waitForCard("oldCard");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.closeActiveTab());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.getOpenDocuments().size() == 1);
        writeExternally(tmp.resolve("core.xsd"), schemaWithElement("newCard", ""));
        check();

        assertTrue(prompt.asked.isEmpty());
        assertTrue(Files.exists(main));
    }

    @Test
    void aTextViewWithAModelIsAskedToo(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("core.xsd"), schemaWithType("OldType"));
        open(tmp, "main.xsd", schemaIncluding("core.xsd"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveViewMode(ViewMode.TREE));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveViewMode(ViewMode.TEXT));
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;

        writeExternally(tmp.resolve("core.xsd"), schemaWithType("NewType"));
        checkUntil(() -> List.of("NewType").equals(namedTypes()));

        assertEquals(1, prompt.asked.size());
    }

    @Test
    void openCardsStayOpenWhenTheModelIsRebuilt(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("core.xsd"), schemaWithElement("oldCard", ""));
        open(tmp, "main.xsd", nestedSchema("core.xsd", "first", "second"));
        showGraphic();
        waitForCard("first");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            card("first").setExpanded(false);
            card("second").setExpanded(true);
        });
        boolean secondWasOpen = onFx(() -> card("second").isExpanded());
        assertTrue(secondWasOpen);
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;

        writeExternally(tmp.resolve("core.xsd"), schemaWithElement("newCard", ""));
        checkUntil(() -> hasCard("newCard"));

        assertFalse(onFx(() -> card("first").isExpanded()), "a card the user closed stays closed");
        assertTrue(onFx(() -> card("second").isExpanded()), "a card the user opened stays open");
        assertTrue(hasCard("insideSecond"));
    }

    /** The reported case: a real multi-file schema, its big include changed, with a deferred card opened. */
    @Test
    void fundsXmlNoticesItsCoreFileAndKeepsADeferredCardOpen(@TempDir Path tmp) throws Exception {
        try (var files = Files.list(Path.of("src/test/resources/schema/include_files"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".xsd")).toList()) {
                Files.copy(file, tmp.resolve(file.getFileName()));
            }
        }
        Path main = tmp.resolve("FundsXML4.xsd");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(main));
        WaitForAsyncUtils.waitFor(60, TimeUnit.SECONDS, () -> onFx(() -> host.getActiveDocument()
                .filter(d -> main.equals(d.getPath())).map(d -> d.getDiskStamp() != null).orElse(false)));
        showGraphic();
        WaitForAsyncUtils.waitFor(60, TimeUnit.SECONDS, () -> onFx(() -> graphReady()));
        List<String> opened = onFx(() -> {
            XsdNodeRenderer.VisualNode deferred = firstDeferred(graph().getRootNode());
            assertNotNull(deferred, "the schema is large enough to defer cards");
            List<String> path = labelPath(deferred);
            deferred.setExpanded(true);
            return path;
        });
        prompt.changedAnswer = ExternalChangePrompt.ChangedChoice.RELOAD;
        Object before = onFx(() -> graph());

        Path core = tmp.resolve("FundsXML4_Core.xsd");
        writeExternally(core, Files.readString(core) + "<!-- changed -->\n");
        WaitForAsyncUtils.waitFor(60, TimeUnit.SECONDS, () -> {
            check();
            return onFx(() -> graphReady() && graph() != before);
        });

        assertEquals(List.of("dependency:FundsXML4.xsd:[FundsXML4_Core.xsd]"), prompt.asked);
        assertTrue(onFx(() -> {
            XsdNodeRenderer.VisualNode card = byLabelPath(graph().getRootNode(), opened);
            return card != null && card.isExpanded();
        }), "the deferred card " + opened + " is open again");
    }

    private boolean graphReady() {
        return host.lookupAll("*").stream().anyMatch(n -> n instanceof XsdGraphView view
                && view.getScene() != null && view.getRootNode() != null);
    }

    private static XsdNodeRenderer.VisualNode firstDeferred(XsdNodeRenderer.VisualNode card) {
        if (card.hasUnloadedChildren()) {
            return card;
        }
        for (XsdNodeRenderer.VisualNode child : card.getChildren()) {
            XsdNodeRenderer.VisualNode found = firstDeferred(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<String> labelPath(XsdNodeRenderer.VisualNode card) {
        List<String> path = new ArrayList<>();
        for (XsdNodeRenderer.VisualNode node = card; node.getParent() != null; node = node.getParent()) {
            path.add(0, node.getLabel());
        }
        return path;
    }

    private static XsdNodeRenderer.VisualNode byLabelPath(XsdNodeRenderer.VisualNode root, List<String> path) {
        XsdNodeRenderer.VisualNode node = root;
        for (String label : path) {
            XsdNodeRenderer.VisualNode next = null;
            for (XsdNodeRenderer.VisualNode child : node.getChildren()) {
                if (label.equals(child.getLabel())) {
                    next = child;
                    break;
                }
            }
            if (next == null) {
                return null;
            }
            node = next;
        }
        return node;
    }

    @Test
    void summaryNamesTheFilesAndCountsTheRest() {
        OpenDocument document = OpenDocument.forPath(Path.of("main.xsd"));
        assertEquals("a.xsd, used by main.xsd, was changed on disk.",
                ExternalChangePrompt.dependencySummary(document, List.of(Path.of("a.xsd"))));
        assertEquals("a.xsd, b.xsd, c.xsd and 2 more, used by main.xsd, were changed on disk.",
                ExternalChangePrompt.dependencySummary(document, List.of(Path.of("a.xsd"), Path.of("b.xsd"),
                        Path.of("c.xsd"), Path.of("d.xsd"), Path.of("e.xsd"))));
    }

    // ----- helpers ----------------------------------------------------------

    private static String schemaIncluding(String... files) {
        StringBuilder includes = new StringBuilder();
        for (String file : files) {
            includes.append("  <xs:include schemaLocation=\"").append(file).append("\"/>\n");
        }
        return "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" + includes + "</xs:schema>\n";
    }

    /** A schema with an include and two elements that each have a child element. */
    private static String nestedSchema(String include, String first, String second) {
        return """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="%s"/>
                  <xs:element name="%s"><xs:complexType><xs:sequence>
                    <xs:element name="insideFirst" type="xs:string"/>
                  </xs:sequence></xs:complexType></xs:element>
                  <xs:element name="%s"><xs:complexType><xs:sequence>
                    <xs:element name="insideSecond" type="xs:string"/>
                  </xs:sequence></xs:complexType></xs:element>
                </xs:schema>
                """.formatted(include, first, second);
    }

    private static <T> T onFx(Callable<T> call) throws Exception {
        return WaitForAsyncUtils.asyncFx(call).get(10, TimeUnit.SECONDS);
    }

    private XsdNodeRenderer.VisualNode card(String label) {
        return findCard(graph().getRootNode(), label);
    }

    private XsdGraphView graph() {
        return host.lookupAll("*").stream().filter(XsdGraphView.class::isInstance)
                .map(XsdGraphView.class::cast).filter(view -> view.getScene() != null).findFirst().orElseThrow();
    }

    private static XsdNodeRenderer.VisualNode findCard(XsdNodeRenderer.VisualNode card, String label) {
        if (label.equals(card.getLabel())) {
            return card;
        }
        for (XsdNodeRenderer.VisualNode child : card.getChildren()) {
            XsdNodeRenderer.VisualNode found = findCard(child, label);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** @return whether a label of the hint bar above the diagram names this text */
    private boolean barMentions(String text) {
        return WaitForAsyncUtils.waitForAsyncFx(10_000, () -> host.lookupAll("*").stream()
                .filter(UnresolvedReferencesBar.class::isInstance)
                .anyMatch(bar -> bar.isVisible() && bar.lookupAll(".label").stream()
                        .anyMatch(node -> node instanceof javafx.scene.control.Label label
                                && label.getText() != null && label.getText().contains(text))));
    }

    private static String schemaWithElement(String name, String extra) {
        return """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  %s
                  <xs:element name="%s" type="xs:string"/>
                </xs:schema>
                """.formatted(extra, name);
    }

    private void showGraphic() {
        WaitForAsyncUtils.waitForAsyncFx(30_000, () -> host.setActiveViewMode(ViewMode.GRAPHIC));
    }

    private void waitForCard(String label) throws Exception {
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> hasCard(label));
    }

    /** @return whether the visible Graphic view of the active schema shows a card with this label */
    private boolean hasCard(String label) {
        return WaitForAsyncUtils.waitForAsyncFx(10_000, () -> {
            for (Node node : host.lookupAll("*")) {
                if (node instanceof XsdGraphView graph && graph.isVisible() && graph.getScene() != null
                        && graph.getRootNode() != null && hasCard(graph.getRootNode(), label)) {
                    return true;
                }
            }
            return false;
        });
    }

    private static boolean hasCard(XsdNodeRenderer.VisualNode card, String label) {
        if (label.equals(card.getLabel())) {
            return true;
        }
        for (XsdNodeRenderer.VisualNode child : card.getChildren()) {
            if (hasCard(child, label)) {
                return true;
            }
        }
        return false;
    }

    private int gridRows() {
        return WaitForAsyncUtils.waitForAsyncFx(10_000, () -> host.lookupAll("*").stream()
                .filter(XmlCanvasView.class::isInstance).findFirst()
                .map(view -> ((XmlCanvasView) view).visibleRowCount()).orElse(0));
    }

    private static String schemaWithType(String name) {
        return """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="%s"><xs:sequence/></xs:complexType>
                </xs:schema>
                """.formatted(name);
    }

    private Path open(Path dir, String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(file));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getActiveDocument().filter(d -> file.equals(d.getPath()))
                        .map(d -> d.getDiskStamp() != null).orElse(false)));
        return file;
    }

    /** Writes like another program would, with a modification time that is certain to differ. */
    private void writeExternally(Path file, String content) throws Exception {
        Files.writeString(file, content);
        mtimeOffset += 10_000;
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + mtimeOffset));
    }

    private String activeText() {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveText().orElse(null));
    }

    private List<String> namedTypes() {
        return WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getActiveNamedTypes().stream().map(XsdNode::getName).toList());
    }

    /** Runs one complete external-change check (probe + handling). */
    private void check() throws Exception {
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !host.isExternalChangeCheckRunning());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.checkExternalChangesNow());
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !host.isExternalChangeCheckRunning());
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Checks repeatedly until {@code condition} holds (the reload itself is asynchronous). */
    private void checkUntil(Callable<Boolean> condition) throws Exception {
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> {
            check();
            return condition.call();
        });
    }
}
