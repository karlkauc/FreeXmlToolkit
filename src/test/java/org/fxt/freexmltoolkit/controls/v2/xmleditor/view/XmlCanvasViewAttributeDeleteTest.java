package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * TestFX verification that Delete on an attribute row of the XML grid removes only that attribute,
 * never its element (an attribute row's model node is the element).
 */
@ExtendWith(ApplicationExtension.class)
class XmlCanvasViewAttributeDeleteTest {

    private static final String XML = """
            <root>
              <Amount ccy="EUR" isFundCcy="true">5</Amount>
            </root>
            """;

    private XmlCanvasView view;
    private XmlEditorContext context;
    private String roundTripped;

    @Start
    void start(Stage stage) {
        RepeatingElementsTable.clearAllCaches();
        context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        view = new XmlCanvasView(context);
        view.setOnDocumentModified(xml -> roundTripped = xml);
        stage.setScene(new Scene(view, 900, 600));
        stage.show();
    }

    private void fx(Runnable r) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            r.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private FlatRow row(String label, boolean attribute) {
        return view.visibleRowList().stream()
                .filter(r -> label.equals(r.getLabel()) && r.getType().isAttribute() == attribute)
                .findFirst().orElseThrow(() -> new AssertionError("no row " + label));
    }

    private void pressDelete() {
        fx(() -> Event.fireEvent(view.canvasNode(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DELETE,
                false, false, false, false)));
    }

    private XmlElement amount() {
        return context.getDocument().getRootElement().getChildElement("Amount");
    }

    @Test
    void deleteOnAnAttributeRowRemovesOnlyTheAttribute() {
        fx(view::expandAll);
        fx(() -> view.selectRow(row("isFundCcy", true)));
        pressDelete();

        XmlElement amount = amount();
        assertNotNull(amount, "the element stays");
        assertEquals("5", amount.getTextContent());
        assertFalse(amount.hasAttribute("isFundCcy"), "the attribute is gone");
        assertTrue(amount.hasAttribute("ccy"), "the other attribute stays");
        assertTrue(roundTripped != null && roundTripped.contains("<Amount ccy=\"EUR\">5</Amount>"), roundTripped);

        FlatRow selected = WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.selectedRow);
        assertNotNull(selected, "a row stays selected");
        assertTrue(selected.getType().isAttribute() && "ccy".equals(selected.getLabel()),
                "the remaining attribute is selected");

        fx(() -> {
            context.undo();
            view.rebuild();
        });
        assertEquals("true", amount().getAttribute("isFundCcy"), "one undo restores it");
    }

    @Test
    void deletingTheLastAttributeSelectsTheElement() {
        fx(view::expandAll);
        fx(() -> view.selectRow(row("ccy", true)));
        pressDelete();
        fx(() -> view.selectRow(row("isFundCcy", true)));
        pressDelete();

        XmlElement amount = amount();
        assertNotNull(amount, "the element stays");
        assertTrue(amount.getAttributes().isEmpty());
        FlatRow selected = WaitForAsyncUtils.waitForAsyncFx(2000, () -> view.selectedRow);
        assertNotNull(selected);
        assertEquals("Amount", selected.getLabel());
        assertFalse(selected.getType().isAttribute());
    }
}
