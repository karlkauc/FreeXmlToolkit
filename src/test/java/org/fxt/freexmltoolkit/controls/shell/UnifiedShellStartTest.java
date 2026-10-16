package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType;
import org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlHint;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlPropertyKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * UX plan phase 5 in the shell: the toolbar without a document (5.3, 5.4), the Welcome task rows
 * (5.2) and the one-time FundsXML hint (5.5).
 */
@ExtendWith(ApplicationExtension.class)
class UnifiedShellStartTest {

    private UnifiedShellView shell;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        shell = new UnifiedShellView();
        Scene scene = new Scene(shell, 1280, 800);
        scene.getStylesheets().addAll(
                getClass().getResource("/css/design-tokens.css").toExternalForm(),
                getClass().getResource("/css/unified-shell.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void withoutADocumentTheToolbarShowsOnlyNewAndOpen() {
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(shown("#action-new") && shown("#action-open"), "New and Open are always there");
        for (String id : new String[]{"#action-save", "#action-undo", "#action-format",
                "#action-query-console", "#doc-action-transform", "#doc-action-run", "#action-set-schema",
                "#doc-action-validate"}) {
            assertFalse(shown(id), id + " is hidden without a document");
        }
        assertFalse(shown(".fxt-view-switch"), "the view switch is hidden without a document");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getEditorHost().newDocument(EditorFileType.XML));
        WaitForAsyncUtils.waitForFxEvents();
        for (String id : new String[]{"#action-save", "#action-format", "#doc-action-validate",
                "#action-set-schema"}) {
            assertTrue(shown(id), id + " comes back with a document");
        }
        assertTrue(shown(".fxt-view-switch"));
    }

    @Test
    void templateCompareAndSpreadsheetLiveInTheMenuOnly() {
        WaitForAsyncUtils.waitForFxEvents();
        for (String id : new String[]{"#action-insert-template", "#action-compare", "#action-spreadsheet"}) {
            assertNull(lookup(id), id + " is no toolbar button any more");
        }
    }

    @Test
    void aFileTaskOpensItsPanelOnlyAfterAFileWasPicked() {
        AtomicInteger asked = new AtomicInteger();
        shell.setWelcomeFilePicker(() -> {
            asked.incrementAndGet();
            return false; // cancelled
        });
        Activity before = WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getSelectionModel().getActive());
        fire("#task-validate");
        assertEquals(1, asked.get(), "the task asks for a file first");
        assertEquals(before, shell.getSelectionModel().getActive(), "cancelling changes nothing");

        shell.setWelcomeFilePicker(() -> {
            shell.getEditorHost().newDocument(EditorFileType.XML);
            return true;
        });
        fire("#task-validate");
        assertEquals(Activity.VALIDATION, shell.getSelectionModel().getActive(),
                "with a file picked the Validation panel opens");
    }

    @Test
    void theSignTaskOpensTheSignaturePageWithoutAskingForAFile() {
        AtomicInteger asked = new AtomicInteger();
        shell.setWelcomeFilePicker(() -> {
            asked.incrementAndGet();
            return false;
        });
        fire("#task-sign");
        assertEquals(0, asked.get());
        assertEquals(Activity.SIGNATURE, shell.getSelectionModel().getActive());
    }

    @Test
    void fundsXmlHintIsShownOnceAndEnablesTheExtension() throws Exception {
        PropertiesService props = org.fxt.freexmltoolkit.di.ServiceRegistry.get(PropertiesService.class);
        String enabledBefore = props.get(FundsXmlPropertyKeys.ENABLED);
        String shownBefore = props.get(FundsXmlHint.SHOWN_KEY);
        try {
            props.set(FundsXmlPropertyKeys.ENABLED, "false");
            props.set(FundsXmlHint.SHOWN_KEY, "false");
            java.util.List<String> fetched = new java.util.concurrent.CopyOnWriteArrayList<>();
            shell.setFundsXmlContentFetcher(fetched::add); // no download in tests
            String fundsXml = "<?xml version=\"1.0\"?>\n<!-- a <comment> -->\n<FundsXML4><ControlData/></FundsXML4>";
            assertTrue(FundsXmlHint.shouldOffer(fundsXml));

            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                shell.showFundsXmlHint();
                return null;
            });
            assertNotNull(lookup("#fundsxml-hint"), "the hint bar sits above the editor");
            assertFalse(FundsXmlHint.shouldOffer(fundsXml), "it is offered only once");

            fire("#fundsxml-hint-enable");
            WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> lookup("#fundsxml-hint") == null);
            assertEquals("true", props.get(FundsXmlPropertyKeys.ENABLED), "the button turns the extension on");
            assertNotNull(lookup("#activity-fundsxml"), "the rail gets its FundsXML button");
            assertEquals(java.util.List.of("hint-enable"), fetched, "the content is fetched like after Settings");
        } finally {
            props.set(FundsXmlPropertyKeys.ENABLED, enabledBefore == null ? "false" : enabledBefore);
            props.set(FundsXmlHint.SHOWN_KEY, shownBefore == null ? "false" : shownBefore);
        }
    }

    @Test
    void theValidateTaskValidatesTheFileOnceItIsRead(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp)
            throws Exception {
        java.nio.file.Path xml = tmp.resolve("broken.xml");
        java.nio.file.Files.writeString(xml, "<root><a></b></root>");
        shell.setWelcomeFilePicker(() -> {
            shell.getEditorHost().openFile(xml);
            return true;
        });
        fire("#task-validate");
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> shell.getEditorHost().getActiveProblems().size()) > 0);
        assertEquals(Activity.VALIDATION, shell.getSelectionModel().getActive());
    }

    @Test
    void transformIsLabelledAndSampleXmlSitsInTheSchemaMenu() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getEditorHost().newDocument(EditorFileType.XSD));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("Transform", WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> ((ButtonBase) shell.lookup("#doc-action-transform")).getText()));
        javafx.scene.control.MenuItem sample = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            var split = (javafx.scene.control.SplitMenuButton) shell.lookup("#action-set-schema");
            return split.getItems().stream().filter(i -> "doc-action-generate-sample".equals(i.getId()))
                    .findFirst().orElse(null);
        });
        assertNotNull(sample, "Generate Sample XML is in the Schema menu");
        assertFalse(sample.isDisable(), "and enabled for an XSD");
        assertEquals("Save…", WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> ((ButtonBase) new org.fxt.freexmltoolkit.controls.shell.editor.TransformOutputPanel(
                        shell.getEditorHost()).lookup("#transform-output-save")).getText()));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            shell.openQueryConsole(); // the split's items are only in the scene graph once shown
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> shell.getQueryConsole().lookup("#query-run-hint")), "the console says Ctrl+Enter runs");
    }

    private Node lookup(String selector) {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.lookup(selector));
    }

    /** Present, visible and managed (hidden toolbar groups are both invisible and unmanaged). */
    private boolean shown(String selector) {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            Node node = shell.lookup(selector);
            if (node == null) {
                return false;
            }
            for (Node n = node; n != null; n = n.getParent()) {
                if (!n.isVisible() || !n.isManaged() && n != shell) {
                    return false;
                }
            }
            return true;
        });
    }

    private void fire(String selector) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ButtonBase button = (ButtonBase) shell.lookup(selector);
            assertNotNull(button, "missing " + selector);
            button.fire();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }
}
