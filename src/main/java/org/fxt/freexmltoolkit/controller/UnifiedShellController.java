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

    @FXML
    public void initialize() {
        shellRoot.getChildren().setAll(new UnifiedShellView());
    }
}
