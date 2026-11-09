package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.shell.DialogTheming;

/**
 * Warms up the text editor shortly after start, while nobody waits: the first file a user opened
 * stalled the UI for 2.5-3.3 s on slow machines, all of it class loading and lambda linking of the
 * editor, its skin and the CSS (telemetry {@code 0986ef1efbc3dd89}, {@code 5e684feb5234b2a5}).
 *
 * <p>First the editor's classes are loaded on a background thread (without running their static
 * initializers), then one XML editor and a text area are built off-screen on the FX thread, styled
 * and laid out, and dropped. The FX part still takes its time on a slow machine, which is why the
 * stall watchdog does not report a stall inside {@link #buildOffScreen()}. Off with
 * {@code -Dfxt.editorWarmUp=false}.</p>
 */
public final class EditorWarmUp {

    private static final Logger logger = LogManager.getLogger(EditorWarmUp.class);

    /** Classes of the editor stack, loaded ahead (names, so a missing one costs nothing). */
    static final String[] CLASSES = {
            "org.fxmisc.richtext.GenericStyledArea",
            "org.fxmisc.richtext.CodeArea",
            "org.fxmisc.richtext.LineNumberFactory",
            "org.fxmisc.richtext.model.ReadOnlyStyledDocument",
            "org.fxmisc.richtext.model.GenericEditableStyledDocument",
            "org.fxmisc.flowless.VirtualFlow",
            "org.fxmisc.flowless.VirtualizedScrollPane",
            "org.reactfx.util.FingerTree",
            "org.reactfx.collection.MemoizationListImpl",
            "javafx.scene.control.skin.TextAreaSkin",
            "javafx.scene.control.skin.TextFieldSkin",
            "org.fxt.freexmltoolkit.controls.v2.editor.XmlCodeEditorV2",
            "org.fxt.freexmltoolkit.controls.shell.editor.XmlEditorView",
            "org.fxt.freexmltoolkit.controls.shell.editor.EditorTab",
    };

    private static volatile boolean done;

    private EditorWarmUp() {
    }

    /** Loads the classes (on the calling, non-FX thread), then builds the editor once on the FX thread. */
    public static void run() {
        if (done || !Boolean.parseBoolean(System.getProperty("fxt.editorWarmUp", "true"))) {
            return;
        }
        done = true;
        long t0 = System.nanoTime();
        ClassLoader loader = EditorWarmUp.class.getClassLoader();
        int loaded = 0;
        for (String name : CLASSES) {
            try {
                Class.forName(name, false, loader);
                loaded++;
            } catch (ClassNotFoundException | LinkageError e) {
                logger.debug("Warm-up: {} not loaded: {}", name, e.toString());
            }
        }
        logger.debug("Warm-up: {} classes loaded in {} ms", loaded, (System.nanoTime() - t0) / 1_000_000);
        Platform.runLater(EditorWarmUp::buildOffScreen);
    }

    /** Builds, styles and lays out an XML editor and a text area that are never shown (FX thread). */
    static void buildOffScreen() {
        long t0 = System.nanoTime();
        try {
            EditorView view = EditorViews.create(EditorFileType.XML);
            view.setText("<warm-up a=\"1\">\n  <b>text</b>\n</warm-up>\n");
            TextArea area = new TextArea("warm-up");
            VBox root = new VBox(view.getNode(), area);
            root.getStylesheets().addAll(DialogTheming.shellStylesheets());
            new Scene(root, 640, 400);
            root.applyCss();
            root.layout();
        } catch (RuntimeException | LinkageError e) {
            // only a speed-up: a failure here must never reach the user
            logger.debug("Warm-up of the editor failed: {}", e.toString());
        }
        logger.debug("Warm-up: editor built off-screen in {} ms", (System.nanoTime() - t0) / 1_000_000);
    }

    /** @return whether the warm-up ran (tests) */
    static boolean hasRun() {
        return done;
    }
}
