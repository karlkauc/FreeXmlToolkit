package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The editor's bottom dock: OUTPUT and DEBUG share it, switched by a strip that only shows for two. */
@ExtendWith(ApplicationExtension.class)
class EditorBottomDockTest {

    private static final long FX_TIMEOUT_MS = 10_000;

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void outputAloneLooksLikeThePlainPanel() {
        TransformOutputPanel out = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> host.transformOutputPanel());
        EditorBottomDock dock = host.bottomDock();
        assertSame(dock, host.getBottom(), "the dock is the editor's bottom region");
        assertEquals(List.of(TransformOutputPanel.DOCK_KEY), dock.keys());
        assertFalse(dock.isShowing(), "hidden until the first run");
        assertFalse(dock.isStripShown(), "no toggle strip for a single panel");
        assertFalse(out.isShowing());

        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> out.showPending("Transforming…"));
        assertTrue(out.isShowing(), "a run reveals OUTPUT");
        assertTrue(dock.isShowing(TransformOutputPanel.DOCK_KEY));

        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, out::hide);
        assertFalse(dock.isShowing(), "✕ hides the dock");
    }

    @Test
    void secondPanelAddsTheStripAndResultsDoNotStealTheFront() {
        TransformOutputPanel out = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> host.transformOutputPanel());
        EditorBottomDock dock = host.bottomDock();
        Region debug = new StackPane();
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            dock.register("debug", debug, "DEBUG");
            dock.show("debug");
        });
        assertTrue(dock.isStripShown(), "two panels → toggle strip");
        assertTrue(dock.isShowing("debug"));
        assertFalse(out.isShowing(), "OUTPUT is registered but not in front");
        assertNotNull(dock.lookup("#bottom-dock-debug"), "each panel has a toggle");

        // A debug run's result goes to OUTPUT without taking DEBUG out of the front.
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> {
            out.showTransformResult("<x/>", org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat.XML, 3);
            dock.select("debug");
            dock.markUpdated(TransformOutputPanel.DOCK_KEY);
        });
        assertTrue(dock.isShowing("debug"), "DEBUG stays in front");
        assertTrue(dock.lookup("#bottom-dock-output").getStyleClass().contains(EditorBottomDock.UPDATED_CLASS),
                "OUTPUT's toggle is flagged as updated");
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> dock.select(TransformOutputPanel.DOCK_KEY));
        assertFalse(dock.lookup("#bottom-dock-output").getStyleClass().contains(EditorBottomDock.UPDATED_CLASS),
                "selecting clears the flag");
        assertTrue(out.isShowing());

        // Ending the session removes DEBUG; OUTPUT keeps the dock alive.
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> dock.remove("debug"));
        assertEquals(List.of(TransformOutputPanel.DOCK_KEY), dock.keys());
        assertFalse(dock.isStripShown());
        assertTrue(dock.isShowing(), "the dock stays while OUTPUT has content");

        // Removing the selected last panel hides the dock.
        WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> dock.remove(TransformOutputPanel.DOCK_KEY));
        assertFalse(dock.isShowing());
    }

    @Test
    void heightIsClampedToTheMinimum() {
        EditorBottomDock dock = WaitForAsyncUtils.waitForAsyncFx(FX_TIMEOUT_MS, () -> host.bottomDock());
        assertTrue(dock.getPrefHeight() >= EditorBottomDock.MIN_HEIGHT);
        assertEquals(EditorBottomDock.MIN_HEIGHT, dock.getMinHeight());
    }
}
