package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The grid header in a narrow column: it compacts, then moves its tool buttons into the ⋯ menu,
 * so every action stays reachable instead of being clipped away.
 */
@ExtendWith(ApplicationExtension.class)
class GridViewHeaderOverflowTest {

    private static final List<String> OVERFLOW = List.of("grid-more-optimal-widths", "grid-more-word-wrap",
            "grid-more-expand-all", "grid-more-collapse-all");

    private Stage stage;
    private XmlGridView grid;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        grid = new XmlGridView();
        grid.setXml("<root><a>1</a><b>2</b></root>");
        stage.setScene(new Scene(grid, 1200, 400));
        stage.show();
    }

    private <T> T fx(java.util.concurrent.Callable<T> action) {
        T result = WaitForAsyncUtils.waitForAsyncFx(5000, action);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    private void width(double w) {
        fx(() -> {
            stage.setWidth(w);
            return null;
        });
        fx(() -> {
            grid.layout();
            return null;
        });
    }

    private GridViewHeader header() {
        return fx(() -> (GridViewHeader) grid.lookup(".fxt-grid-header"));
    }

    private MenuButton more() {
        return fx(() -> (MenuButton) grid.lookup("#grid-more"));
    }

    private MenuItem item(String id) {
        return fx(() -> more().getItems().stream().filter(i -> id.equals(i.getId())).findFirst().orElseThrow());
    }

    private boolean shown(String id) {
        return fx(() -> {
            Node node = grid.lookup("#" + id);
            return node != null && node.isVisible() && node.isManaged();
        });
    }

    @Test
    void aWideColumnShowsEveryToolInTheBar() {
        width(1200);

        assertEquals(GridViewHeader.Mode.FULL, header().mode());
        assertTrue(shown("grid-optimal-widths"));
        assertTrue(shown("grid-expand-all"));
        for (String id : OVERFLOW) {
            assertFalse(fx(() -> item(id).isVisible()), id + " stays out of the menu");
        }
    }

    @Test
    void aNarrowColumnMovesTheToolsIntoTheMoreMenu() {
        width(330);

        assertEquals(GridViewHeader.Mode.MINIMAL, header().mode());
        assertFalse(shown("grid-optimal-widths"));
        assertFalse(shown("grid-collapse-all"));
        for (String id : OVERFLOW) {
            assertTrue(fx(() -> item(id).isVisible()), id + " is reachable through ⋯");
        }
        // ⋯ itself is inside the visible strip
        double moreRight = fx(() -> more().localToScene(more().getLayoutBounds()).getMaxX());
        double headerRight = fx(() -> header().localToScene(header().getLayoutBounds()).getMaxX());
        assertTrue(moreRight <= headerRight + 0.5, moreRight + " <= " + headerRight);
    }

    @Test
    void theMenuWrapToggleFollowsAndDrivesTheGrid() {
        width(330);
        CheckMenuItem wrap = (CheckMenuItem) item("grid-more-word-wrap");
        boolean before = fx(() -> grid.getCanvasView().isWordWrap());

        fx(() -> {
            wrap.setSelected(!wrap.isSelected());
            wrap.fire();
            return null;
        });

        assertEquals(!before, fx(() -> grid.getCanvasView().isWordWrap()));
        fx(() -> {
            wrap.setSelected(before);
            wrap.fire();
            return null;
        });
    }

    @Test
    void widenedAgainTheToolsReturnToTheBar() {
        width(330);
        width(1200);

        assertEquals(GridViewHeader.Mode.FULL, header().mode());
        assertTrue(shown("grid-optimal-widths"));
    }
}
