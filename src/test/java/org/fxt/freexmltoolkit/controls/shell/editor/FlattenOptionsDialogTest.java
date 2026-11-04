package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the flatten options dialog maps its controls to {@code FlattenOptions}
 * (defaults fully reduced, resolved includes always dropped) — without showing it
 * modally.
 */
@ExtendWith(ApplicationExtension.class)
class FlattenOptionsDialogTest {

    private FlattenOptionsDialog dialog;

    @Start
    void start(Stage stage) {
        dialog = new FlattenOptionsDialog();
    }

    @Test
    void defaultsToFullyReducedOutput() {
        var opts = WaitForAsyncUtils.waitForAsyncFx(2000, () -> dialog.currentOptions());
        assertTrue(opts.removeAnnotations());
        assertTrue(opts.removeComments());
        assertTrue(opts.minify());
        assertTrue(opts.removeUnusedTypes());
        assertTrue(opts.removeResolvedIncludes());
    }

    @Test
    void reflectsToggledOptionsButAlwaysDropsResolvedIncludes() {
        var opts = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            dialog.setOptions(false, false, false, false);
            return dialog.currentOptions();
        });
        assertFalse(opts.removeAnnotations());
        assertFalse(opts.removeComments());
        assertFalse(opts.minify());
        assertFalse(opts.removeUnusedTypes());
        assertTrue(opts.removeResolvedIncludes(), "flatten output is standalone by design");
    }

    @Test
    void sourceFileTrackingIsOffByDefaultAndCanBeEnabled() {
        var defaults = WaitForAsyncUtils.waitForAsyncFx(2000, () -> dialog.currentOptions());
        assertFalse(defaults.trackSourceFiles(), "tracking adds markers, so it is opt-in");

        var enabled = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            dialog.setOptions(false, false, false, false, true);
            return dialog.currentOptions();
        });
        assertTrue(enabled.trackSourceFiles());
    }

    @Test
    void okReturnsOptionsCancelReturnsNull() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            assertNotNull(dialog.getResultConverter().call(ButtonType.OK).options());
            assertNull(dialog.getResultConverter().call(ButtonType.CANCEL));
            return null;
        });
    }

    @Test
    void defaultOutputSitsNextToTheSourceWithSuffix() {
        assertEquals(Path.of("/x/FundsXML_flattened.xsd"), FlattenOptionsDialog.defaultOutput(Path.of("/x/FundsXML.xsd")));
        assertEquals(Path.of("/x/schema_flattened.xsd"), FlattenOptionsDialog.defaultOutput(Path.of("/x/schema")));
    }

    @Test
    void outputIsPrefilledAndValidated(@TempDir Path tmp) throws Exception {
        Path source = Files.writeString(tmp.resolve("FundsXML.xsd"), "<xs:schema/>");
        FlattenOptionsDialog withSource = WaitForAsyncUtils.waitForAsyncFx(2000, () -> new FlattenOptionsDialog(source));
        assertEquals(tmp.resolve("FundsXML_flattened.xsd").toAbsolutePath().normalize(),
                WaitForAsyncUtils.waitForAsyncFx(2000, withSource::outputFile));
        assertNull(WaitForAsyncUtils.waitForAsyncFx(2000, withSource::validate));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            withSource.setOutput(source.toString());
            assertNotNull(withSource.validate(), "the original must not be overwritten");
            withSource.setOutput(tmp.resolve("missing/out.xsd").toString());
            assertNotNull(withSource.validate(), "the folder must exist");
            withSource.setOutput(tmp.toString());
            assertNotNull(withSource.validate(), "a folder is no file");
            withSource.setOutput("");
            assertNull(withSource.validate(), "empty keeps the unsaved-tab behaviour");
            var request = withSource.getResultConverter().call(ButtonType.OK);
            assertNull(request.outputFile());
            return null;
        });
    }

    @Test
    void withoutSourceTheFieldStartsEmpty() {
        assertNull(WaitForAsyncUtils.waitForAsyncFx(2000, dialog::outputFile));
    }
}
