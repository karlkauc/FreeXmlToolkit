package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.fo.FakeProcessors;
import org.fxt.freexmltoolkit.service.xsltproc.ExternalXsltRunnerTest;
import org.fxt.freexmltoolkit.service.xsltproc.SaxonInstaller;
import org.fxt.freexmltoolkit.service.xsltproc.SaxonInstallerTest;
import org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorConfig;
import org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorPreset;
import org.fxt.freexmltoolkit.service.xsltproc.XsltProcessorStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Settings → XSLT Processors lists the processors and changes the default; the dialog's fields and test run. */
@ExtendWith(ApplicationExtension.class)
class XsltProcessorsSettingsTest {

    private final Map<String, String> settings = new HashMap<>();
    private final XsltProcessorStore store = new XsltProcessorStore(settings::get, settings::put, () -> true);
    private final XsltProcessorConfig libxslt = new XsltProcessorConfig(null, "libxslt", XsltProcessorPreset.XSLTPROC,
            "/no/such/xsltproc", null, null, "", 0);
    private XsltProcessorsSettings view;
    private SaxonInstaller installer;
    private final java.nio.file.Path saxonDir = createTempDir();

    private static java.nio.file.Path createTempDir() {
        try {
            return java.nio.file.Files.createTempDirectory("fxt-saxon13");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Start
    void start(Stage stage) {
        store.save(List.of(libxslt));
        installer = new SaxonInstaller(saxonDir, SaxonInstaller.MAVEN_CENTRAL, uri -> {
            String path = uri.getPath();
            return java.nio.file.Files.readAllBytes(SaxonInstallerTest.saxon13Jar(path.substring(path.lastIndexOf('/') + 1)));
        });
        view = new XsltProcessorsSettings(store, installer);
        stage.setScene(new Scene(view, 500, 300));
        stage.show();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsBuiltinAndExternalAndMakesOneTheDefault() throws Exception {
        ListView<XsltProcessorsSettings.Row> list = (ListView<XsltProcessorsSettings.Row>) view.lookup("#settings-xslt-processors");
        Button edit = (Button) view.lookup("#settings-xslt-edit");
        Button makeDefault = (Button) view.lookup("#settings-xslt-default");
        assertEquals(2, list.getItems().size());
        assertEquals(XsltProcessorStore.BUILTIN_ID, list.getItems().get(0).id());
        assertTrue(edit.isDisabled(), "Saxon HE cannot be edited");
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            list.getSelectionModel().select(1);
            return null;
        });
        assertFalse(edit.isDisabled());
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            view.onMakeDefault();
            return null;
        });
        assertEquals(libxslt.id(), store.defaultId());
        assertTrue(makeDefault.isDisabled());
    }

    @Test
    @SuppressWarnings("unchecked")
    void addSaxonDownloadsOnceAndAddsTheProcessorOnce() throws Exception {
        SaxonInstallerTest.saxon13Jar("Saxon-HE-13.1.jar"); // skips without the Gradle jars
        ListView<XsltProcessorsSettings.Row> list = (ListView<XsltProcessorsSettings.Row>) view.lookup("#settings-xslt-processors");
        java.util.concurrent.atomic.AtomicInteger asked = new java.util.concurrent.atomic.AtomicInteger();
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            view.setDownloadHooks(() -> asked.incrementAndGet() > 1, e -> {
                throw new AssertionError(e);
            });
            view.onAddSaxon(); // declined
            return null;
        });
        assertEquals(1, asked.get());
        assertFalse(installer.isInstalled());
        assertEquals(1, store.list().size());

        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            view.onAddSaxon();
            return null;
        });
        WaitForAsyncUtils.waitFor(30, java.util.concurrent.TimeUnit.SECONDS, () -> store.list().size() == 2);
        XsltProcessorConfig added = store.list().get(1);
        assertEquals("Saxon HE 13.1", added.name());
        assertEquals(XsltProcessorPreset.SAXON_HE, added.preset());
        assertTrue(added.arguments().contains(installer.classpath()), added.arguments());
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(added.id(), list.getSelectionModel().getSelectedItem().id());

        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            view.onAddSaxon(); // installed: no question, no second entry
            return null;
        });
        assertEquals(2, asked.get());
        assertEquals(2, store.list().size());
        javafx.scene.control.Label status = (javafx.scene.control.Label) view.lookup("#settings-xslt-saxon-status");
        assertTrue(status.getText().contains("already"), status.getText());
    }

    @Test
    @SuppressWarnings("unchecked")
    void theDialogFollowsThePresetAndValidates() throws Exception {
        XsltProcessorDialog dialog = WaitForAsyncUtils.waitForAsyncFx(5000, () -> new XsltProcessorDialog(null, java.util.Set.of("taken")));
        var pane = dialog.getDialogPane();
        ComboBox<XsltProcessorPreset> preset = (ComboBox<XsltProcessorPreset>) pane.lookup("#xslt-processor-preset");
        TextField arguments = (TextField) pane.lookup("#xslt-processor-arguments");
        TextField parameters = (TextField) pane.lookup("#xslt-processor-parameters");
        TextField name = (TextField) pane.lookup("#xslt-processor-name");
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            preset.setValue(XsltProcessorPreset.XSLTPROC);
            return null;
        });
        assertEquals(XsltProcessorPreset.XSLTPROC.arguments(), arguments.getText());
        assertEquals("--stringparam {name} {value}", parameters.getText());
        assertEquals("xsltproc (libxslt)", name.getText());
        assertNull(WaitForAsyncUtils.waitForAsyncFx(5000, dialog::validate));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            name.setText("Taken");
            return null;
        });
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(5000, dialog::validate).contains("already"));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            name.setText("Mine");
            arguments.setText("${xml} ${xsl}");
            return null;
        });
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(5000, dialog::validate).contains("${out}"));
    }

    @Test
    void theDialogsTestRunChecksResultAndParameter() {
        XsltProcessorDialog.TestOutcome ok = XsltProcessorDialog.runTest(ExternalXsltRunnerTest.fake("ok", 60));
        assertTrue(ok.ok(), ok.status() + " / " + ok.output());
        XsltProcessorConfig noParams = new XsltProcessorConfig(null, "No params", XsltProcessorPreset.CUSTOM,
                FakeProcessors.java(), ExternalXsltRunnerTest.fake("ok", 60).arguments().replace(" ${params}", ""), "", "", 60);
        XsltProcessorDialog.TestOutcome missing = XsltProcessorDialog.runTest(noParams);
        assertFalse(missing.ok());
        assertTrue(missing.status().contains("parameter did not arrive"), missing.status());
        XsltProcessorDialog.TestOutcome failed = XsltProcessorDialog.runTest(ExternalXsltRunnerTest.fake("saxonerror", 60));
        assertFalse(failed.ok());
        assertTrue(failed.output().contains("XPST0008"), failed.output());
    }
}
