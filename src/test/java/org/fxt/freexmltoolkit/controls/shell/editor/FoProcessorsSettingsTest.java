package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.service.fo.FakeProcessors;
import org.fxt.freexmltoolkit.service.fo.FoProcessorConfig;
import org.fxt.freexmltoolkit.service.fo.FoProcessorPreset;
import org.fxt.freexmltoolkit.service.fo.FoProcessorStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** Settings → XSL-FO Processors lists the processors and changes the default; the dialog's test run. */
@ExtendWith(ApplicationExtension.class)
class FoProcessorsSettingsTest {

    private final Map<String, String> settings = new HashMap<>();
    private final FoProcessorStore store = new FoProcessorStore(settings::get, settings::put, () -> true);
    private final FoProcessorConfig xep = new FoProcessorConfig(null, "RenderX XEP", FoProcessorPreset.XEP,
            "/no/such/xep", null, "", 0);
    private FoProcessorsSettings view;

    @Start
    void start(Stage stage) {
        store.save(List.of(xep));
        view = new FoProcessorsSettings(store);
        stage.setScene(new Scene(view, 500, 300));
        stage.show();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsBuiltinAndExternalAndMakesOneTheDefault() throws Exception {
        ListView<FoProcessorsSettings.Row> list = (ListView<FoProcessorsSettings.Row>) view.lookup("#settings-fo-processors");
        Button edit = (Button) view.lookup("#settings-fo-edit");
        Button makeDefault = (Button) view.lookup("#settings-fo-default");
        assertEquals(2, list.getItems().size());
        assertEquals(FoProcessorStore.BUILTIN_ID, list.getItems().get(0).id());
        assertTrue(edit.isDisabled(), "the bundled FOP cannot be edited");
        assertTrue(makeDefault.isDisabled(), "it already is the default");

        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            list.getSelectionModel().select(1);
            return null;
        });
        assertFalse(edit.isDisabled());
        assertFalse(makeDefault.isDisabled());
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            view.onMakeDefault();
            return null;
        });
        assertEquals(xep.id(), store.defaultId());
        assertEquals(xep.id(), list.getSelectionModel().getSelectedItem().id(), "the selection stays");
        assertTrue(makeDefault.isDisabled());
    }

    @Test
    void theDialogsTestRunReportsSuccessAndFailure() {
        FoProcessorDialog.TestOutcome ok = FoProcessorDialog.runTest(FakeProcessors.config("Fake", "ok", null, 60));
        assertTrue(ok.ok(), ok.output());
        assertTrue(ok.output().contains("substituted"), ok.output());

        FoProcessorDialog.TestOutcome failed = FoProcessorDialog.runTest(FakeProcessors.config("Fake", "fail", null, 60));
        assertFalse(failed.ok());
        assertTrue(failed.output().contains("exit code 3"), failed.output());
    }
}
