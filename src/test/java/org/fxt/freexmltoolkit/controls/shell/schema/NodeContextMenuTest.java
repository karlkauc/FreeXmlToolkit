package org.fxt.freexmltoolkit.controls.shell.schema;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the shared node-editing context menu (used by both the Tree and
 * Graphic views) fires the wired actions on the supplied node. Only the
 * non-dialog items are exercised here (dialog items would block on showAndWait).
 */
@ExtendWith(ApplicationExtension.class)
class NodeContextMenuTest {

    @Start
    void start(Stage stage) {
    }

    private MenuItem find(ContextMenu menu, String text) {
        return menu.getItems().stream()
                .filter(i -> text.equals(i.getText()))
                .findFirst().orElseThrow(() -> new AssertionError("menu item not found: " + text));
    }

    @Test
    void firesDeleteAddSequenceAndAddChoiceOnTheCurrentNode() {
        XsdNode node = new XsdElement("Target");
        RecordingActions actions = new RecordingActions();
        ContextMenu menu = WaitForAsyncUtils.waitForAsyncFx(2000, () -> NodeContextMenu.build(actions, () -> node));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            find(menu, "Delete").fire();
            find(menu, "Add Sequence").fire();
            find(menu, "Add Choice").fire();
            return null;
        });

        assertSame(node, actions.deleted, "Delete must act on the supplied node");
        assertSame(node, actions.sequenced, "Add Sequence must act on the supplied node");
        assertSame(node, actions.choiced, "Add Choice must act on the supplied node");
    }

    @Test
    void doesNothingWhenNoCurrentNode() {
        RecordingActions actions = new RecordingActions();
        ContextMenu menu = WaitForAsyncUtils.waitForAsyncFx(2000, () -> NodeContextMenu.build(actions, () -> null));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            find(menu, "Delete").fire();
            return null;
        });
        assertNull(actions.deleted, "no action should fire when there is no current node");
    }

    /** Captures which action was invoked with which node. */
    private static final class RecordingActions implements NodeEditActions {
        XsdNode deleted;
        XsdNode sequenced;
        XsdNode choiced;

        @Override
        public void addElement(XsdNode parent, String name) {
        }

        @Override
        public void addAttribute(XsdNode parent, String name) {
        }

        @Override
        public void addSequence(XsdNode element) {
            sequenced = element;
        }

        @Override
        public void addChoice(XsdNode element) {
            choiced = element;
        }

        @Override
        public void rename(XsdNode node, String newName) {
        }

        @Override
        public void changeType(XsdNode node, String newType) {
        }

        @Override
        public void changeCardinality(XsdNode node, int min, int max) {
        }

        @Override
        public void delete(XsdNode node) {
            deleted = node;
        }
    }
}
