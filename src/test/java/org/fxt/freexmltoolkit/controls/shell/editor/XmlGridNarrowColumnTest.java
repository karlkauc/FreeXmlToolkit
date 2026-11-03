package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A grid in an editor column narrower than its header strip: the grid keeps to its column instead
 * of running under the pane beside it (the Properties pane in the shell), so its horizontal scroll
 * bar stays fully visible and scrolls the whole width back and forth.
 */
@ExtendWith(ApplicationExtension.class)
class XmlGridNarrowColumnTest {

    private static String wideTable() {
        StringBuilder sb = new StringBuilder("<Root><Items>");
        for (int i = 0; i < 6; i++) {
            sb.append("<Item id=\"").append(i).append("\" code=\"CODE-").append(i)
                    .append("-ABCDEFGHIJ\" description=\"A rather long description text for item ").append(i)
                    .append("\" category=\"Category-Number-").append(i).append("\" region=\"Lower Austria\">")
                    .append("<Name>Item name number ").append(i).append("</Name><Note>a note long enough</Note></Item>");
        }
        return sb.append("</Items></Root>").toString();
    }

    private XmlGridView grid;
    private StackPane properties;

    @Start
    void start(Stage stage) {
        grid = new XmlGridView();
        grid.setXml(wideTable());
        properties = new StackPane();
        properties.setMinWidth(300);
        SplitPane split = new SplitPane(grid, properties);
        split.setDividerPositions(0.55);
        stage.setScene(new Scene(split, 700, 500));
        stage.show();
        grid.getCanvasView().expandAll();
    }

    private <T> T fx(java.util.concurrent.Callable<T> action) {
        T result = WaitForAsyncUtils.waitForAsyncFx(5000, action);
        WaitForAsyncUtils.waitForFxEvents();
        return result;
    }

    @Test
    void theGridStaysLeftOfTheNeighbouringPane() {
        double gridRight = fx(() -> grid.localToScene(grid.getLayoutBounds()).getMaxX());
        double propertiesLeft = fx(() -> properties.localToScene(properties.getLayoutBounds()).getMinX());

        assertTrue(gridRight <= propertiesLeft, "grid ends at " + gridRight + ", the pane starts at " + propertiesLeft);
        assertTrue(fx(() -> grid.minWidth(-1)) < 400, "the header strip no longer sets a wide minimum");
    }

    @Test
    void theScrollBarReachesBothEnds() {
        ScrollBar bar = fx(() -> (ScrollBar) grid.lookupAll(".scroll-bar").stream()
                .filter(n -> n instanceof ScrollBar s && s.getOrientation() == javafx.geometry.Orientation.HORIZONTAL
                        && s.isVisible())
                .findFirst().orElseThrow());
        double barRight = fx(() -> bar.localToScene(bar.getLayoutBounds()).getMaxX());
        double propertiesLeft = fx(() -> properties.localToScene(properties.getLayoutBounds()).getMinX());
        assertTrue(barRight <= propertiesLeft, "the bar's right end is visible: " + barRight);

        fx(() -> {
            bar.setValue(bar.getMax());
            return null;
        });
        fx(() -> {
            bar.decrement();
            bar.setValue(0);
            return null;
        });
        assertEquals(0, fx(bar::getValue));
    }
}
