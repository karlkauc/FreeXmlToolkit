package org.fxt.freexmltoolkit.controls.v2.editor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.Scene;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * A tooltip of the XML editor (hover help, fold arrows) is a CSS descendant of the code area, whose
 * {@code xml-highlighting.css} gives all {@code .text} the code colour: in the light theme the hover help
 * showed dark text on the dark tooltip. Tooltip text must stay light on its dark background in both themes.
 */
@ExtendWith(ApplicationExtension.class)
class XmlEditorTooltipColourTest {

    private XmlCodeEditorV2 editor;
    private Scene scene;

    @Start
    void start(Stage stage) {
        editor = new XmlCodeEditorV2(new MutableXmlSchemaProvider());
        BorderPane root = new BorderPane(editor);
        // the shell declares its sheets on the root, as tab_unified_shell.fxml does
        for (String css : new String[]{"design-tokens", "app-theme", "fxt-theme", "unified-shell"}) {
            root.getStylesheets().add(getClass().getResource("/css/" + css + ".css").toExternalForm());
        }
        scene = new Scene(root, 600, 300);
        stage.setScene(scene);
        stage.show();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void tooltipTextIsLightOnTheDarkTooltip(boolean dark) {
        WaitForAsyncUtils.waitForAsyncFx(10_000, () -> {
            ThemeManager.apply(scene, dark);
            editor.setText("<root>\n  <child/>\n</root>\n");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents(); // the theme reaches the scene's CSS first
        Color fill = WaitForAsyncUtils.waitForAsyncFx(10_000, () -> {
            Tooltip tip = new Tooltip("Element: child");
            Tooltip.install(editor.getCodeArea(), tip);
            var p = editor.getCodeArea().localToScreen(40, 20);
            tip.show(editor.getCodeArea(), p.getX(), p.getY());
            var tipRoot = tip.getScene().getRoot();
            tipRoot.applyCss();
            Color result = null;
            for (var node : tipRoot.lookupAll(".text")) {
                if (node instanceof Text text && text.getFill() instanceof Color color) {
                    result = color;
                }
            }
            tip.hide();
            return result;
        });
        assertTrue(fill != null, "the tooltip shows its text");
        // both themes give tooltips a dark background (app-theme #343a40, dark-theme #252526)
        assertTrue(fill.getBrightness() > 0.75,
                (dark ? "dark" : "light") + " theme: tooltip text must be light, was " + fill);
    }
}
