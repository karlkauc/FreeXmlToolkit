package org.fxt.freexmltoolkit.controller;

import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;

import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;

/**
 * Controller for the application's single top-level page ({@code tab_unified_shell.fxml},
 * loaded by {@code FxtGui} at startup). Its only job is to instantiate the
 * {@link UnifiedShellView} — the entire UI (Activity Bar, side panels, editor host,
 * inspector, status bar) — and mount it into the FXML root pane.
 */
public class UnifiedShellController {

    @FXML
    private StackPane shellRoot;

    private UnifiedShellView shellView;

    @FXML
    public void initialize() {
        shellView = new UnifiedShellView();
        shellRoot.getChildren().setAll(shellView);
    }

    /**
     * The shell view instance mounted by {@link #initialize()} — used by {@code FxtGui}
     * to route startup files (command-line arguments, macOS open-file events) into the editor.
     */
    public UnifiedShellView getShellView() {
        return shellView;
    }
}
