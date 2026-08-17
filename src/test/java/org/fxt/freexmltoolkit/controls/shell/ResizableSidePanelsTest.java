package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the user-resizable side panels: the work area is a horizontal SplitPane whose
 * side columns are pixel-stable on window resize, and whose divider-set widths survive a
 * collapse/expand cycle via the persisted width preferences.
 */
@ExtendWith(ApplicationExtension.class)
class ResizableSidePanelsTest {

    private static final String LEFT_WIDTH_KEY = "shell.leftPanel.width";
    private static final String INSPECTOR_WIDTH_KEY = "shell.inspector.width";

    private UnifiedShellView shell;
    private Stage stage;
    private String savedLeftWidth;
    private String savedInspectorWidth;

    @Start
    void start(Stage stage) {
        // Tests write to the real user properties file — capture the width prefs for restore
        // and start from the defaults so persisted values from earlier runs can't leak in.
        savedLeftWidth = getPref(LEFT_WIDTH_KEY);
        savedInspectorWidth = getPref(INSPECTOR_WIDTH_KEY);
        removePref(LEFT_WIDTH_KEY);
        removePref(INSPECTOR_WIDTH_KEY);

        this.stage = stage;
        shell = new UnifiedShellView();
        stage.setScene(new Scene(shell, 1300, 700));
        stage.show();
    }

    @AfterEach
    void restoreWidthPrefs() {
        restorePref(LEFT_WIDTH_KEY, savedLeftWidth);
        restorePref(INSPECTOR_WIDTH_KEY, savedInspectorWidth);
    }

    @Test
    void workAreaIsResizableHorizontalSplit() throws Exception {
        openSampleAndNormalize();
        SplitPane workArea = shell.getWorkArea();
        assertEquals(javafx.geometry.Orientation.HORIZONTAL, workArea.getOrientation());
        assertEquals(3, workArea.getItems().size(), "side panel | editor | inspector");
        assertEquals(Boolean.FALSE, SplitPane.isResizableWithParent(shell.getLeftPanelWrapper()),
                "left panel keeps its pixel width on window resize");
        assertEquals(Boolean.FALSE, SplitPane.isResizableWithParent(shell.getInspectorWrapper()),
                "inspector keeps its pixel width on window resize");
    }

    @Test
    void dividerWidthSurvivesCollapseAndReopen() throws Exception {
        openSampleAndNormalize();
        SplitPane workArea = shell.getWorkArea();

        // Simulate a divider drag: make the left panel ~340 px wide.
        WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> workArea.setDividerPosition(0, 340 / workArea.getWidth()));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> Math.abs(shell.getLeftPanelWrapper().getWidth() - 340) <= 10);

        // Wait past the 400 ms save debounce, then check the width was persisted.
        WaitForAsyncUtils.sleep(700, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        String persisted = getPref(LEFT_WIDTH_KEY);
        assertNotNull(persisted, "divider drag persists the panel width");
        assertEquals(340, Double.parseDouble(persisted), 10);

        // Collapse and re-open: the persisted width must be restored.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.setLeftPanelVisible(false));
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.setLeftPanelVisible(true));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> Math.abs(shell.getLeftPanelWrapper().getWidth() - 340) <= 10);
    }

    @Test
    void windowResizeKeepsPanelWidthsStable() throws Exception {
        openSampleAndNormalize();
        double leftBefore = shell.getLeftPanelWrapper().getWidth();
        double inspectorBefore = shell.getInspectorWrapper().getWidth();
        double editorBefore = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> shell.getWorkArea().getItems().get(1).getLayoutBounds().getWidth());

        // Shrink (growing past the initial size overflows the headless Monocle framebuffer).
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> stage.setWidth(stage.getWidth() - 200));
        // The editor column absorbs the resize delta...
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> shell.getWorkArea().getItems().get(1).getLayoutBounds().getWidth() < editorBefore - 100);
        // ...while both side panels keep their pixel width.
        assertEquals(leftBefore, shell.getLeftPanelWrapper().getWidth(), 2,
                "left panel width is pixel-stable on window resize");
        assertEquals(inspectorBefore, shell.getInspectorWrapper().getWidth(), 2,
                "inspector width is pixel-stable on window resize");
    }

    /** Opens a sample XML doc and normalizes both panels to open (independent of persisted prefs). */
    private void openSampleAndNormalize() throws Exception {
        Path xml = Files.createTempFile("resizable", ".xml");
        Files.writeString(xml, "<root><a/></root>");
        xml.toFile().deleteOnExit();
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            shell.openFile(xml);
            shell.setLeftPanelVisible(true);
            shell.setInspectorVisible(true);
        });
        WaitForAsyncUtils.waitForFxEvents();
        // Both wrappers must have been laid out as split items before any width assertions.
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> shell.getLeftPanelWrapper().getWidth() > 0
                        && shell.getInspectorWrapper().getWidth() > 0);
    }

    // ----- properties-file helpers (tests share the real user properties file) -----

    private static String getPref(String key) {
        try {
            return ServiceRegistry.get(PropertiesService.class).get(key);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void removePref(String key) {
        try {
            PropertiesService service = ServiceRegistry.get(PropertiesService.class);
            Properties properties = service.loadProperties();
            if (properties.remove(key) != null) {
                service.saveProperties(properties);
            }
        } catch (Throwable ignored) {
            // properties service unavailable — nothing to clean
        }
    }

    private static void restorePref(String key, String value) {
        if (value == null) {
            removePref(key);
        } else {
            try {
                ServiceRegistry.get(PropertiesService.class).set(key, value);
            } catch (Throwable ignored) {
                // properties service unavailable — nothing to restore
            }
        }
    }
}
