package org.fxt.freexmltoolkit.screenshots;

import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Keyboard and editor helpers shared by the tutorial video scenarios: typing into the code area the
 * way a keyboard does (so auto-completion reacts), shortcuts fired without blocking on the modal
 * dialogs they open, and lookups of the active code area and open dialogs.
 */
final class VideoTyping {

    private final DocShell doc;
    private final DocVideoRecorder video;
    private final boolean german = "de".equals(System.getProperty("fxt.locale"));

    VideoTyping(DocShell doc, DocVideoRecorder video) {
        this.doc = doc;
        this.video = video;
    }

    /** @return the key cap name of the Ctrl key in the film's language */
    String ctrl() {
        return german ? "Strg" : "Ctrl";
    }

    /** @return {@code de} or {@code en} text, whichever matches the film's language */
    String say(String de, String en) {
        return german ? de : en;
    }

    /** Shows Ctrl(+Shift)+key and fires it later on the focused node: a modal dialog it opens does not block. */
    void shortcut(KeyCode code, boolean shift, String key) {
        video.showKey(ctrl() + "+" + (shift ? say("Umschalt+", "Shift+") : "") + key);
        Scene scene = doc.stage().getScene();
        Platform.runLater(() -> {
            Node target = scene.getFocusOwner() != null ? scene.getFocusOwner() : scene.getRoot();
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, true, false, false));
        });
        WaitForAsyncUtils.sleep(300, TimeUnit.MILLISECONDS);
    }

    /** Fires Ctrl(+Shift)+key on {@code target} and waits for it (no key cap). */
    void shortcutOn(Node target, KeyCode code, boolean shift) {
        doc.onFx(() -> {
            target.requestFocus();
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, true, false, false));
        });
    }

    /** Types {@code text} into the code area one key at a time (KEY_TYPED, Enter as KEY_PRESSED). */
    void type(CodeArea area, String text) {
        for (char c : text.toCharArray()) {
            String ch = String.valueOf(c);
            doc.onFx(() -> {
                area.requestFocus();
                if (c == '\n') {
                    Event.fireEvent(area, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
                } else {
                    Event.fireEvent(area, new KeyEvent(KeyEvent.KEY_TYPED, ch, "", KeyCode.UNDEFINED, false, false, false, false));
                }
            });
            video.pause(c == ' ' && text.isBlank() ? 20 : 140);
        }
    }

    /** Presses a key in the code area (the auto-completion popup listens there) and shows its key cap. */
    void press(CodeArea area, KeyCode code, String label) {
        video.showKey(label);
        doc.onFx(() -> {
            area.requestFocus();
            Event.fireEvent(area, new KeyEvent(KeyEvent.KEY_PRESSED, "", code == KeyCode.ENTER ? "\r" : "", code,
                    false, false, false, false));
            Event.fireEvent(area, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false));
        });
    }

    /** Clicks at {@code offset} of the code area (a real click) and puts the caret there. */
    void clickAt(CodeArea area, int offset) {
        Bounds b = doc.fx(() -> area.getCharacterBoundsOnScreen(offset, offset + 1).orElse(null));
        if (b != null) {
            video.click(b.getMinX() + 1, b.getCenterY());
        }
        doc.onFx(() -> {
            area.requestFocus();
            area.moveTo(offset);
        });
    }

    /** @return the code area of the active tab that holds {@code marker} */
    CodeArea area(String marker) {
        EditorHost host = doc.host();
        return doc.fx(() -> host.lookupAll(".code-area").stream()
                .filter(n -> n instanceof CodeArea c && shown(c) && c.getText().contains(marker))
                .map(CodeArea.class::cast).findFirst().orElseThrow());
    }

    /** @return the newest open dialog window, or {@code null} */
    Window dialog() {
        return doc.fx(() -> Window.getWindows().stream()
                .filter(w -> w instanceof Stage s && s != doc.stage() && s.isShowing())
                .reduce((a, b) -> b).orElse(null));
    }

    /** Closes popups (menus, combo lists) that Escape cannot reach without a window manager. */
    void hidePopups() {
        doc.onFx(() -> Window.getWindows().stream()
                .filter(w -> w instanceof javafx.stage.PopupWindow && w.isShowing()).toList()
                .forEach(Window::hide));
    }

    static boolean shown(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return node.getScene() != null;
    }
}
