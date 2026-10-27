package org.fxt.freexmltoolkit.controls.v2.editor.intellisense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorMode;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Accepting an attribute completion opens the value completions of that attribute right away,
 * with the caret between the new quotes; an attribute without suggested values opens nothing.
 */
@ExtendWith(ApplicationExtension.class)
class AttributeValueAutoPopupTest {

    private static final String XSL = "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"";

    private XmlCodeEditorV2 editor;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        editor = new XmlCodeEditorV2(new MutableXmlSchemaProvider());
        stage.setScene(new Scene(editor, 700, 400));
        stage.show();
    }

    @Test
    void acceptingAnAttributeWithValuesShowsTheValues() throws Exception {
        String text = "<xsl:stylesheet " + XSL + " >\n</xsl:stylesheet>";
        openAttributeCompletionsBefore(text, " >");
        assertEquals("version", visibleLabels().getFirst(), "required version comes first");

        accept();
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> visibleLabels().contains("3.0"));
        assertEquals(List.of("3.0", "2.0", "1.0"), visibleLabels());
        String after = editor.getCodeArea().getText();
        int caret = editor.getCodeArea().getCaretPosition();
        assertTrue(after.startsWith("<xsl:stylesheet " + XSL + " version=\"\""), after);
        assertEquals(after.indexOf("version=\"") + "version=\"".length(), caret);

        accept();
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(editor.getCodeArea().getText().contains("version=\"3.0\""), editor.getCodeArea().getText());
    }

    @Test
    void attributeWithoutValuesOpensNoPopup() throws Exception {
        String text = "<xsl:stylesheet " + XSL + " version=\"3.0\">\n  <xsl:variable />\n</xsl:stylesheet>";
        openAttributeCompletionsBefore(text, " />");
        assertEquals("name", visibleLabels().getFirst());

        accept();
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(editor.getCodeArea().getText().contains("<xsl:variable name=\"\"/>"), editor.getCodeArea().getText());
        assertTrue(visibleLabels().isEmpty(), "no value popup for a free-text attribute: " + visibleLabels());
    }

    /** Sets the XSLT text, puts the caret after the space of {@code marker} and opens completions. */
    private void openAttributeCompletionsBefore(String text, String marker) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            editor.setText(text);
            editor.setEditorMode(EditorMode.XSLT);
            editor.getCodeArea().moveTo(text.indexOf(marker) + 1);
            editor.getIntelliSenseEngine().showCompletions();
            return null;
        });
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !visibleLabels().isEmpty());
    }

    /** Presses Enter in the completion list, as the user does to accept the selected item. */
    private void accept() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ListView<?> list = completionList();
            list.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            return null;
        });
    }

    private List<String> visibleLabels() {
        try {
            return WaitForAsyncUtils.asyncFx(() -> {
                ListView<?> list = completionList();
                return list == null ? List.<String>of()
                        : list.getItems().stream().map(i -> ((CompletionItem) i).getLabel()).toList();
            }).get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The list of the showing IntelliSense popup, or null. Must run on the FX thread. */
    private static ListView<?> completionList() {
        for (Window window : Window.getWindows()) {
            if (window instanceof Popup popup && popup.isShowing() && popup.getScene() != null) {
                if (popup.getScene().getRoot().lookup(".intellisense-list") instanceof ListView<?> list) {
                    return list;
                }
            }
        }
        return null;
    }
}
