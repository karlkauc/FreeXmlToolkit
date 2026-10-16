package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.fxml.FXML;
import javafx.scene.layout.HBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;

/**
 * One-time bar above the editor offering the FundsXML extension when a FundsXML document is
 * opened while the extension is off (UX plan 5.5). Layout in {@code views/FundsXmlHintBar.fxml}.
 */
public final class FundsXmlHintBar extends HBox {

    private final Runnable onEnable;
    private final Runnable onDismiss;

    /**
     * @param onEnable  turns the extension on (the bar closes itself first)
     * @param onDismiss called after "Not now" closed the bar
     */
    public FundsXmlHintBar(Runnable onEnable, Runnable onDismiss) {
        this.onEnable = onEnable;
        this.onDismiss = onDismiss;
        FxmlViews.load(this, "/pages/views/FundsXmlHintBar.fxml");
    }

    @FXML
    public void onEnable() {
        close();
        onEnable.run();
    }

    @FXML
    public void onDismiss() {
        close();
        onDismiss.run();
    }

    private void close() {
        if (getParent() instanceof javafx.scene.layout.Pane parent) {
            parent.getChildren().remove(this);
        }
    }
}
