package org.fxt.freexmltoolkit.controls.unified;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shared.utilities.XmlSearchTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** "Find next" in a structured view moves on: the status count must not jump back to the first match. */
@ExtendWith(ApplicationExtension.class)
class UnifiedSearchBarNavigationTest {

    /** Records the calls; three matches, cursor like the grid's. */
    private static final class FakeTarget implements XmlSearchTarget {
        final List<String> calls = new ArrayList<>();
        int index = -1;

        @Override
        public boolean find(String text, boolean forward) {
            calls.add("find");
            index = forward ? (index + 1) % 3 : (index + 2) % 3;
            return true;
        }

        @Override
        public int findAll(String text) {
            calls.add("findAll");
            index = 0;
            return 3;
        }

        @Override
        public int countMatches(String text) {
            calls.add("count");
            return 3;
        }

        @Override
        public void clearSearch() {
            index = -1;
        }
    }

    private UnifiedSearchBar bar;
    private final FakeTarget target = new FakeTarget();

    @Start
    void start(Stage stage) {
        bar = new UnifiedSearchBar();
        bar.setCurrentSearchTarget(target);
        stage.setScene(new Scene(bar, 600, 60));
        stage.show();
    }

    private TextField field() {
        return (TextField) bar.getChildren().stream().filter(TextField.class::isInstance).findFirst().orElseThrow();
    }

    private void enter() {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            field().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void enterMovesToTheNextMatchAndOnlyCounts() {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            field().setText("x"); // typing jumps to the first match
            return null;
        });
        assertEquals(0, target.index);
        target.calls.clear();

        enter();
        assertEquals(1, target.index, "second match");
        enter();
        assertEquals(2, target.index, "third match, not back to the first");
        assertEquals(List.of("find", "count", "find", "count"), target.calls);
    }
}
