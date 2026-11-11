package org.fxt.freexmltoolkit.controls.shell.editor.ai;

import javafx.fxml.FXML;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.ai.context.AiContext;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderConfig;
import org.fxt.freexmltoolkit.util.DialogHelper;

/** Shows exactly what will be sent to an AI provider: the provider, local or remote, the data level and the text. */
public class AiSendPreviewDialog extends Dialog<Boolean> {

    @FXML private Label providerLabel;
    @FXML private Label badge;
    @FXML private Label levelLabel;
    @FXML private Label droppedLabel;
    @FXML private TextArea requestText;
    @FXML private CheckBox dontShow;

    public AiSendPreviewDialog(ProviderConfig provider, boolean local, AiContext context) {
        DialogHelper.prepare(this);
        setTitle(I18n.t("AiSendPreviewDialog.title"));
        setHeaderText(I18n.t("AiSendPreviewDialog.header"));
        ButtonType send = new ButtonType(I18n.t("AiSendPreviewDialog.send"), ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(send, ButtonType.CANCEL);
        getDialogPane().setContent(FxmlViews.loadContent(this, "/pages/dialogs/AiSendPreviewDialog.fxml"));
        providerLabel.setText(provider.name() + " · " + provider.model() + " · " + provider.host());
        badge.setText(I18n.t(local ? "Ai.badge.local" : "Ai.badge.remote"));
        badge.getStyleClass().add(local ? "fxt-wf-validation" : "fxt-wf-pdf");
        levelLabel.setText(I18n.t("AiSendPreviewDialog.level", I18n.t("Ai.level." + context.level().name())));
        if (context.dropped().isEmpty()) {
            droppedLabel.setVisible(false);
            droppedLabel.setManaged(false);
        } else {
            droppedLabel.setText(I18n.t("AiSendPreviewDialog.dropped", String.join(", ", context.dropped())));
        }
        requestText.setText(context.preview());
        setResultConverter(button -> button == send);
    }

    /** "Don't show again for this provider" was ticked. */
    public boolean dontShowAgain() {
        return dontShow.isSelected();
    }
}
