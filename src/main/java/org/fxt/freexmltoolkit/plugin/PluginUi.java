package org.fxt.freexmltoolkit.plugin;

import java.nio.file.Path;

import javafx.scene.Node;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;

/** The application window as plugins see it (JavaFX thread only). */
public interface PluginUi {

    /** @return the editor area: open documents, the active text, schema binding, tool tabs … */
    EditorHost editorHost();

    /** @return the main window (owner for dialogs) */
    Window window();

    /** Opens a file in the editor. */
    void openFile(Path file);

    /** Shows an activity by its id (built-in ones too: {@code explorer}, {@code validation} …). */
    void showActivity(String id);

    /** Re-asks every plugin activity and menu item whether it is visible (after a setting changed). */
    void refreshContributions();

    /** Shows a bar above the editor (one at a time; replaces the one shown). */
    void showEditorBar(Node bar);

    /** Removes the bar shown with {@link #showEditorBar(Node)}. */
    void hideEditorBar();
}
