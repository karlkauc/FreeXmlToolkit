package org.fxt.freexmltoolkit.controls.shell;

import java.nio.file.Path;

import javafx.scene.Node;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.plugin.PluginUi;

/** The shell's side of {@link PluginUi}. */
final class ShellPluginUi implements PluginUi {

    private final UnifiedShellView shell;

    ShellPluginUi(UnifiedShellView shell) {
        this.shell = shell;
    }

    @Override
    public EditorHost editorHost() {
        return shell.getEditorHost();
    }

    @Override
    public Window window() {
        return shell.getScene() != null ? shell.getScene().getWindow() : null;
    }

    @Override
    public void openFile(Path file) {
        shell.openFile(file);
    }

    @Override
    public void showActivity(String id) {
        Activity.fromId(id).ifPresent(shell::showActivity);
    }

    @Override
    public void refreshContributions() {
        shell.refreshPluginContributions();
    }

    @Override
    public void showEditorBar(Node bar) {
        shell.showEditorBar(bar);
    }

    @Override
    public void hideEditorBar() {
        shell.hideEditorBar();
    }
}
