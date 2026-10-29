package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the expand/collapse state across rebuilds and of the XMLSpy view
 * actions: the state is keyed by the node's positional XPath (so same-named elements under
 * different parents are told apart and a rename elsewhere does not reset it), the selection
 * survives an edit, and Expand Fully / Collapse Unselected / Spacebar act on the selection.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewExpandStateTest {

    private static final String XML = """
            <root>
              <p><a><x>1</x></a></p>
              <q><a><x>2</x></a></q>
              <deep><l1><l2><l3>v</l3></l2></l1></deep>
            </root>
            """;

    private XmlCanvasView view;
    private XmlEditorContext context;

    @Start
    void start(Stage stage) {
        context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        view = new XmlCanvasView(context);
        stage.setScene(new Scene(view, 800, 600));
        stage.show();
    }

    private void fx(Runnable r) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            r.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void key(KeyCode code, boolean shortcut, boolean shift) {
        fx(() -> Event.fireEvent(view, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                shift, shortcut, false, false)));
    }

    private FlatRow visible(String label) {
        return view.visibleRowList().stream().filter(r -> label.equals(r.getLabel())).findFirst()
                .orElseThrow(() -> new AssertionError("no visible row " + label));
    }

    private List<FlatRow> visibleLabelled(String label) {
        return view.visibleRowList().stream().filter(r -> label.equals(r.getLabel())).toList();
    }

    private boolean isVisible(String label) {
        return view.visibleRowList().stream().anyMatch(r -> label.equals(r.getLabel()));
    }

    @Test
    void sameNamedElementsUnderDifferentParentsKeepTheirOwnExpandState() {
        fx(view::expandAll);
        List<FlatRow> aRows = visibleLabelled("a");
        assertEquals(2, aRows.size());
        FlatRow aUnderQ = aRows.get(1);
        fx(() -> {
            FlatRow.toggleExpand(aUnderQ, view.visibleRowList());
            view.rebuild();
        });

        aRows = visibleLabelled("a");
        assertTrue(aRows.get(0).isExpanded(), "p/a stays expanded");
        assertFalse(aRows.get(1).isExpanded(), "q/a stays collapsed (no key collision with p/a)");
    }

    @Test
    void renamingAnotherElementDoesNotResetTheExpandStateAndKeepsTheSelection() {
        fx(view::expandAll);
        FlatRow l2 = visible("l2");
        fx(() -> {
            FlatRow.toggleExpand(l2, view.visibleRowList());
            view.setSelectedNode((XmlElement) visible("q").getModelNode());
        });
        assertFalse(isVisible("l3"), "l3 hidden under the collapsed l2");

        // Rename p -> renamed through the adapter (an edit = rebuild + round-trip)
        fx(() -> {
            view.getAdapter().commitRowEdit(visible("p"), true, "renamed");
            view.refresh();
        });

        assertTrue(isVisible("renamed"));
        assertFalse(visible("l2").isExpanded(), "l2 stays collapsed after the rename");
        assertFalse(isVisible("l3"));
        assertTrue(visible("deep").isExpanded(), "deep stays expanded");
        assertNotNull(view.getSelectedNode(), "selection survives the rebuild");
        assertEquals("q", ((XmlElement) view.getSelectedNode()).getName());
    }

    @Test
    void expandFullyOpensTheWholeSubtreeOfTheSelection() {
        fx(view::collapseAll);
        fx(() -> view.setSelectedNode((XmlElement) view.visibleRowList().get(0).getModelNode()));
        key(KeyCode.PLUS, true, true);
        assertTrue(isVisible("l3"), "Ctrl+Shift+Plus expands every descendant");
        assertTrue(visibleLabelled("a").stream().allMatch(FlatRow::isExpanded));
    }

    @Test
    void collapseUnselectedKeepsOnlyThePathToTheSelectionOpen() {
        fx(view::expandAll);
        fx(() -> view.setSelectedNode((XmlElement) visible("l2").getModelNode()));
        key(KeyCode.MINUS, true, true);
        assertTrue(isVisible("l2"), "the selected row stays visible");
        assertTrue(visible("l2").isExpanded(), "the selected row keeps its own state");
        assertTrue(visible("deep").isExpanded() && visible("l1").isExpanded(), "ancestors stay open");
        assertFalse(visible("p").isExpanded(), "unrelated containers collapse");
        assertFalse(isVisible("x"));
    }

    @Test
    void spacebarTogglesTheSelectedContainer() {
        fx(view::expandAll);
        fx(() -> view.setSelectedNode((XmlElement) visible("deep").getModelNode()));
        key(KeyCode.SPACE, false, false);
        assertFalse(visible("deep").isExpanded());
        assertFalse(isVisible("l1"));
        key(KeyCode.SPACE, false, false);
        assertTrue(visible("deep").isExpanded());
        assertTrue(isVisible("l1"));
    }
}
