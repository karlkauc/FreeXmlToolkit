package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/** The side panel of a main-area page: section rows, the current mark and the filter. */
@ExtendWith(ApplicationExtension.class)
class PageNavigatorTest {

    private final List<String> selected = new ArrayList<>();
    private final List<String> queries = new ArrayList<>();
    private PageNavigator navigator;

    @Start
    void start(Stage stage) {
        navigator = new PageNavigator("test", "SETTINGS", "Edited in the main window.", List.of(
                new PageNavigator.Entry("appearance", "Appearance", "bi-palette", "theme dark light"),
                new PageNavigator.Entry("network", "Network & Security", "bi-globe", "proxy certificates")),
                selected::add, true);
        navigator.setOnFilter(queries::add);
        stage.setScene(new Scene(navigator, 300, 400));
        stage.show();
    }

    @Test
    void aRowSelectsItsSectionAndTheCurrentOneIsMarked(FxRobot robot) {
        robot.interact(() -> ((Button) navigator.lookup("#test-nav-network")).fire());
        assertEquals(List.of("network"), selected);
        robot.interact(() -> navigator.setCurrent("network"));
        assertEquals("network", navigator.currentKey());
        robot.interact(() -> navigator.setCurrent("appearance"));
        assertEquals("appearance", navigator.currentKey(), "only one row is marked");
    }

    @Test
    void theFilterMatchesLabelsAndKeywordsAndIsReported(FxRobot robot) {
        robot.interact(() -> navigator.filterField().setText(" Proxy "));
        assertEquals(List.of("network"), navigator.visibleKeys(), "keywords count as matches");
        assertEquals("proxy", queries.getLast(), "the page gets the normalised query");
        robot.interact(() -> navigator.filterField().setText(""));
        assertEquals(List.of("appearance", "network"), navigator.visibleKeys());
        assertTrue(navigator.matches("appearance", "DARK"));
        assertFalse(navigator.matches("appearance", "proxy"));
    }
}
