package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.ProblemsPanel;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationProblem;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The PROBLEMS panel below the editor on a small screen: its list stays inside the window (the
 * editor above gave way to it), and its height follows a drag on its top edge.
 */
@ExtendWith(ApplicationExtension.class)
class ProblemsPanelSizeTest {

    private static final String HEIGHT_KEY = "editor.problems.height";

    private UnifiedShellView shell;
    private Scene scene;
    private String savedHeight;

    @Start
    void start(Stage stage) {
        savedHeight = pref(HEIGHT_KEY);
        setPref(HEIGHT_KEY, null);
        shell = new UnifiedShellView();
        scene = new Scene(shell, 1100, 520); // a short window, like a small laptop screen
        stage.setScene(scene);
        stage.show();
    }

    @AfterEach
    void restore() {
        setPref(HEIGHT_KEY, savedHeight);
    }

    @Test
    void theListStaysVisibleInAShortWindowAndTheTopEdgeResizesIt() throws Exception {
        Path xml = Files.createTempFile("problems-size", ".xml");
        Files.writeString(xml, "<root><a/></root>");
        xml.toFile().deleteOnExit();
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> shell.openFile(xml));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> shell.getEditorHost().getActiveDocument().isPresent());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getEditorHost().setActiveProblems(List.of(
                new ValidationProblem("XSD", "error", 1, "first"),
                new ValidationProblem("XSD", "error", 1, "second"),
                new ValidationProblem("XSD", "error", 1, "third"))));
        WaitForAsyncUtils.waitForFxEvents();

        ProblemsPanel panel = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> (ProblemsPanel) shell.lookup(".fxt-problems-panel"));
        Node list = WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.lookup("#problems-panel-list"));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, panel::isVisible);
        Bounds listBounds = WaitForAsyncUtils.waitForAsyncFx(2000, () -> list.localToScene(list.getLayoutBounds()));
        double bottom = WaitForAsyncUtils.waitForAsyncFx(2000, () -> scene.getHeight());
        assertTrue(listBounds.getHeight() >= 40, "the list shows rows: " + listBounds);
        assertTrue(listBounds.getMaxY() <= bottom + 1, "the list ends inside the window: " + listBounds + " / " + bottom);

        // dragging the top edge up by 25 px makes the panel 25 px taller
        Node resizer = WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.lookup("#problems-panel-resizer"));
        double before = WaitForAsyncUtils.waitForAsyncFx(2000, panel::getHeight);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            Bounds b = resizer.localToScreen(resizer.getLayoutBounds());
            double x = b.getCenterX();
            double y = b.getCenterY();
            resizer.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, x, y));
            resizer.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, x, y - 25));
            resizer.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, x, y - 25));
        });
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> Math.abs(WaitForAsyncUtils.waitForAsyncFx(2000, panel::getHeight) - (before + 25)) <= 2);
        // persisted after the debounce
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> pref(HEIGHT_KEY) != null);
        assertEquals(Math.round(before + 25), Math.round(Double.parseDouble(pref(HEIGHT_KEY))));

        // collapsed: only the header; expanded again: the dragged height
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.setCollapsed(true));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, panel::getHeight) < 50);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.setCollapsed(false));
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> Math.abs(WaitForAsyncUtils.waitForAsyncFx(2000, panel::getHeight) - (before + 25)) <= 2);
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, double screenX, double screenY) {
        return new MouseEvent(type, 0, 0, screenX, screenY, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null);
    }

    private static String pref(String key) {
        try {
            return ServiceRegistry.get(PropertiesService.class).get(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void setPref(String key, String value) {
        try {
            var props = ServiceRegistry.get(PropertiesService.class);
            if (value == null) {
                var all = props.loadProperties();
                all.remove(key);
                props.saveProperties(all);
            } else {
                props.set(key, value);
            }
        } catch (RuntimeException e) {
            // no properties in this environment
        }
    }
}
