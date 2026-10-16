package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * {@link InspectorKeyValueList}: rows render name/value, a click edits a value in place (Enter
 * commits, Esc cancels), read-only rows neither edit nor offer a delete button.
 */
@ExtendWith(ApplicationExtension.class)
class InspectorKeyValueListTest {

    private InspectorKeyValueList<String> list;
    private final List<String> commits = new ArrayList<>();
    private final List<String> deletes = new ArrayList<>();

    @Start
    void start(Stage stage) {
        list = new InspectorKeyValueList<>();
        list.setOnValueCommit((entry, value) -> commits.add(entry.item() + "=" + value));
        list.setOnDelete(entry -> deletes.add(entry.item()));
        list.setItems(List.of(
                new InspectorKeyValueList.Entry<>("min", "minLength", "1"),
                new InspectorKeyValueList.Entry<>("pat", "pattern", "[A-Z]+", true, "Inherited")));
        stage.setScene(new Scene(new StackPane(list), 320, 160));
        stage.show();
    }

    private List<Node> values() {
        return new ArrayList<>(list.lookupAll(".fxt-inspector-kv-value"));
    }

    @Test
    void enterCommitsAnEditedValueAndEscapeCancels(FxRobot robot) {
        assertEquals(2, list.size());
        assertEquals(1, list.readOnlyCount());

        robot.clickOn(values().get(0));
        WaitForAsyncUtils.waitForFxEvents();
        TextField field = (TextField) list.lookup(".fxt-inspector-kv-editor");
        assertNotNull(field, "a click on an editable value opens an in-place editor");
        robot.interact(() -> field.setText("5"));
        robot.interact(() -> field.getOnAction().handle(new javafx.event.ActionEvent()));
        assertEquals(List.of("min=5"), commits);
        assertNull(list.lookup(".fxt-inspector-kv-editor"), "the editor closes after Enter");

        robot.clickOn(values().get(0));
        WaitForAsyncUtils.waitForFxEvents();
        TextField again = (TextField) list.lookup(".fxt-inspector-kv-editor");
        robot.interact(() -> again.setText("9"));
        robot.type(KeyCode.ESCAPE);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(List.of("min=5"), commits, "Esc must not commit");
    }

    @Test
    void readOnlyRowsCannotBeEditedOrDeleted(FxRobot robot) {
        robot.clickOn(values().get(1));
        WaitForAsyncUtils.waitForFxEvents();
        assertNull(list.lookup(".fxt-inspector-kv-editor"), "read-only rows must not open an editor");
        assertEquals(1, list.lookupAll(".fxt-inspector-kv-delete").size(), "only the editable row has a delete button");

        Button delete = (Button) list.lookup(".fxt-inspector-kv-delete");
        robot.interact(delete::fire);
        assertEquals(List.of("min"), deletes);
    }

    @Test
    void anEmptyListShowsItsPlaceholder() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            list.setPlaceholderText("No facets");
            list.setItems(List.of());
        });
        assertEquals(0, list.size());
        assertNotNull(list.lookup(".fxt-inspector-kv-placeholder"));
    }
}
