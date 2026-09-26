package org.fxt.freexmltoolkit.controls.v2.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.fxt.freexmltoolkit.controls.v2.editor.services.MutableXmlSchemaProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Regression test for the dark theme in the XML text view: element text without a syntax class
 * (e.g. the content of {@code <xs:documentation>}) must not stay RichTextFX's default black, and
 * the line-number gutter must not keep a light inline background. Both follow a live theme switch.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCodeEditorDarkThemeTest {

    private XmlCodeEditorV2 editor;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        editor = new XmlCodeEditorV2(new MutableXmlSchemaProvider());
        HBox root = new HBox(editor);
        editor.prefWidthProperty().bind(root.widthProperty());
        editor.prefHeightProperty().bind(root.heightProperty());
        for (String css : new String[]{"/css/design-tokens.css", "/css/app-theme.css",
                "/css/fxt-theme.css", "/css/unified-shell.css"}) {
            root.getStylesheets().add(getClass().getResource(css).toExternalForm());
        }
        Scene scene = new Scene(root, 700, 300);
        stage.setScene(scene);
        ThemeManager.apply(scene, false);
        stage.show();
    }

    @Test
    void plainTextAndGutterFollowTheDarkTheme() {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            editor.setText("<a>\n  <b>Root element of FundsXML</b>\n</a>\n");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        switchTheme(true);
        Color darkText = contentTextFill();
        assertTrue(darkText.getBrightness() > 0.7,
                "plain element text must be light on the dark surface, was " + darkText);
        Color darkGutter = gutterBackground();
        assertTrue(darkGutter.getBrightness() < 0.3,
                "line-number gutter must be dark in the dark theme, was " + darkGutter);

        switchTheme(false);
        assertTrue(contentTextFill().getBrightness() < 0.3, "plain element text is dark again in light");
        assertTrue(gutterBackground().getBrightness() > 0.8, "gutter is light again in light");
    }

    private void switchTheme(boolean dark) {
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            ThemeManager.apply(editor.getScene(), dark);
            editor.getScene().getRoot().applyCss();
            editor.getScene().getRoot().layout();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private Color contentTextFill() {
        return WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            List<Text> all = new java.util.ArrayList<>();
            collectTexts(editor.getCodeArea(), all);
            List<Text> texts = all.stream().filter(t -> t.getText().contains("Root element")).toList();
            assertFalse(texts.isEmpty(), () -> "the plain content text node is rendered; segments: "
                    + all.stream().map(t -> "[" + t.getText() + "|" + t.getStyleClass() + "]").toList());
            return (Color) texts.get(0).getFill();
        });
    }

    /** Walks the scene graph (RichTextFX paragraph cells are not reachable via lookupAll). */
    private static void collectTexts(Node node, List<Text> out) {
        if (node instanceof Text t) {
            out.add(t);
        }
        if (node instanceof javafx.scene.Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                collectTexts(child, out);
            }
        }
    }

    private static void collectByClass(Node node, String styleClass, List<Node> out) {
        if (node.getStyleClass().contains(styleClass)) {
            out.add(node);
        }
        if (node instanceof javafx.scene.Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                collectByClass(child, styleClass, out);
            }
        }
    }

    private Color gutterBackground() {
        return WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            List<Node> gutters = new java.util.ArrayList<>();
            collectByClass(editor.getCodeArea(), "lineno", gutters);
            Node gutter = gutters.isEmpty() ? null : gutters.get(0);
            assertNotNull(gutter, "a line-number gutter cell is rendered");
            Region region = (Region) gutter;
            assertNotNull(region.getBackground(), "gutter has an opaque background");
            return (Color) region.getBackground().getFills().get(0).getFill();
        });
    }
}
