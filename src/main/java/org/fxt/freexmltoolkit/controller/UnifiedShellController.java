package org.fxt.freexmltoolkit.controller;

import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;

import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;

/**
 * Controller for the new Unified shell preview tab ({@code tab_unified_shell.fxml}).
 * <p>
 * UI rebuild Phase 2: hosts the {@link UnifiedShellView} skeleton (Activity Bar,
 * side-panel host, editor host, inspector, status bar). Reachable via the
 * "Unified Shell (Preview)" navigation entry while the existing tabs remain the
 * default — the side-by-side migration approach (decision D3).
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
     * Opens the given file as a document in the shell's editor. Bridge used by
     * {@code MainController} to route a file (e.g. a {@code .sch} opened via legacy
     * file routing) into the shell after a legacy editor tab has been retired.
     *
     * @param file the file to open (ignored if {@code null} or the view is not ready)
     */
    public void openFile(java.io.File file) {
        if (shellView != null && file != null) {
            shellView.openFile(file);
        }
    }
}
