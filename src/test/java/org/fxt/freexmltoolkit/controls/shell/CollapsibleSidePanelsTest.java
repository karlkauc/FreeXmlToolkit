package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the collapsible left side panel and right inspector: both can be collapsed
 * (fully hidden) and re-opened via the same mechanism, while the Activity Bar always
 * stays visible.
 */
@ExtendWith(ApplicationExtension.class)
class CollapsibleSidePanelsTest {

    private UnifiedShellView shell;

    @Start
    void start(Stage stage) {
        shell = new UnifiedShellView();
        stage.setScene(new Scene(shell, 1300, 700));
        stage.show();
    }

    @Test
    void bothPanelsVisibleWhenDocumentOpen() throws Exception {
        openSampleAndNormalize();
        assertTrue(shell.isLeftPanelOpen());
        assertTrue(shell.isInspectorOpen());
        assertTrue(shell.getLeftPanelWrapper().isManaged(), "left panel managed when a doc is open");
        assertTrue(shell.getInspectorWrapper().isManaged(), "inspector managed when a doc is open");
        // The Activity Bar (BorderPane left) is always visible.
        assertNotNull(shell.getLeft());
        assertTrue(shell.getLeft().isVisible() && shell.getLeft().isManaged());
    }

    @Test
    void collapseAndReopenLeftPanel() throws Exception {
        openSampleAndNormalize();

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.setLeftPanelVisible(false));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(shell.isLeftPanelOpen());
        assertFalse(shell.getLeftPanelWrapper().isManaged(), "collapsed left panel is unmanaged (hidden)");
        // Activity Bar must remain visible while the panel is collapsed.
        assertTrue(shell.getLeft().isVisible());

        // Re-open via the toolbar toggle (same mechanism for both sides). The toggle is now
        // unselected; ToggleButton.fire() flips it to selected and runs the show action.
        ToggleButton toggle = (ToggleButton) shell.lookup("#toggle-left-panel");
        assertNotNull(toggle);
        assertFalse(toggle.isSelected(), "toggle reflects the collapsed state");
        WaitForAsyncUtils.waitForAsyncFx(2000, toggle::fire);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(shell.isLeftPanelOpen());
        assertTrue(shell.getLeftPanelWrapper().isManaged());
    }

    @Test
    void collapseAndReopenInspector() throws Exception {
        openSampleAndNormalize();

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.setInspectorVisible(false));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(shell.isInspectorOpen());
        assertFalse(shell.getInspectorWrapper().isManaged(), "collapsed inspector is unmanaged (hidden)");

        ToggleButton toggle = (ToggleButton) shell.lookup("#toggle-inspector");
        assertNotNull(toggle);
        assertFalse(toggle.isSelected());
        WaitForAsyncUtils.waitForAsyncFx(2000, toggle::fire);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(shell.isInspectorOpen());
        assertTrue(shell.getInspectorWrapper().isManaged());
    }

    @Test
    void collapseChevronsExistOnBothSides() throws Exception {
        openSampleAndNormalize();
        long chevrons = shell.lookupAll(".fxt-panel-collapse").stream()
                .filter(Node::isVisible).count();
        assertTrue(chevrons >= 2, "each open panel carries a collapse chevron");
    }

    /** Opens a sample XML doc and normalizes both panels to open (independent of persisted prefs). */
    private void openSampleAndNormalize() throws Exception {
        Path xml = Files.createTempFile("panels", ".xml");
        Files.writeString(xml, "<root><a/></root>");
        xml.toFile().deleteOnExit();
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            shell.openFile(xml);
            shell.setLeftPanelVisible(true);
            shell.setInspectorVisible(true);
        });
        WaitForAsyncUtils.waitForFxEvents();
    }
}
