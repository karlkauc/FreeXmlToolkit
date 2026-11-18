package org.fxt.freexmltoolkit.controls.shell.editor;

import java.net.URI;
import java.util.function.Consumer;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.service.query.QueryError;

/**
 * A failed XPath / XQuery run shown for diagnosis: code (linked to the W3C error index), kind, a clickable
 * position, the message, the query line with a caret under the error, the hint and "Did you mean"
 * corrections that are applied with one click. Layout in {@code pages/panels/QueryErrorView.fxml}.
 */
public final class QueryErrorView extends VBox {

    /** Opens the W3C error index; tests replace it. */
    static Consumer<URI> linkOpener = uri -> org.fxt.freexmltoolkit.util.DesktopActions.browse(uri, e -> { });

    @FXML private Hyperlink codeLink;
    @FXML private Label codeLabel;
    @FXML private Label kindLabel;
    @FXML private Hyperlink positionLink;
    @FXML private Button askAiButton;
    @FXML private Label messageLabel;
    @FXML private Label excerptLabel;
    @FXML private VBox hintBox;
    @FXML private Label hintLabel;
    @FXML private FlowPane suggestionPane;

    private QueryError error;
    private Consumer<QueryError> onGoTo = e -> { };
    private Consumer<QueryError.Suggestion> onApply = s -> { };
    private Consumer<QueryError> onAskAi = e -> { };

    public QueryErrorView() {
        FxmlViews.load(this, "/pages/panels/QueryErrorView.fxml");
        setId("query-error-view");
        askAiButton.visibleProperty().bind(org.fxt.freexmltoolkit.controls.shell.editor.ai.AiState.availableProperty());
        askAiButton.managedProperty().bind(askAiButton.visibleProperty());
        clear();
    }

    /** Called with the error when its position is clicked. */
    public void setOnGoTo(Consumer<QueryError> handler) {
        onGoTo = handler == null ? e -> { } : handler;
    }

    /** Called with a correction when its button is clicked. */
    public void setOnApply(Consumer<QueryError.Suggestion> handler) {
        onApply = handler == null ? s -> { } : handler;
    }

    /** Called with the error when "Ask AI to fix" is clicked. */
    public void setOnAskAi(Consumer<QueryError> handler) {
        onAskAi = handler == null ? e -> { } : handler;
    }

    /** Shows {@code e}; {@code null} hides the view. */
    public void show(QueryError e) {
        error = e;
        boolean shown = e != null;
        setVisible(shown);
        setManaged(shown);
        if (!shown) {
            return;
        }
        String url = e.specUrl();
        String code = e.code() == null ? "" : e.code();
        codeLink.setText(code);
        show(codeLink, url != null);
        codeLabel.setText(code);
        show(codeLabel, url == null && !code.isEmpty());
        kindLabel.setText(e.kind().label());
        positionLink.setText(e.positionLabel());
        show(positionLink, e.hasPosition());
        messageLabel.setText(e.message());
        excerptLabel.setText(e.excerpt());
        show(excerptLabel, !e.excerpt().isEmpty());
        hintLabel.setText(e.hint() == null ? "" : e.hint());
        show(hintBox, e.hint() != null);
        suggestionPane.getChildren().remove(1, suggestionPane.getChildren().size());
        for (QueryError.Suggestion s : e.suggestions()) {
            Button apply = new Button(s.label());
            apply.getStyleClass().add("fxt-query-error-suggestion");
            apply.setOnAction(a -> onApply.accept(s));
            suggestionPane.getChildren().add(apply);
        }
        show(suggestionPane, !e.suggestions().isEmpty());
    }

    /** Hides the view. */
    public void clear() {
        show((QueryError) null);
    }

    /** @return the error shown, or {@code null} */
    public QueryError error() {
        return error;
    }

    @FXML
    public void onOpenSpec() {
        if (error != null && error.specUrl() != null) {
            linkOpener.accept(URI.create(error.specUrl()));
        }
    }

    @FXML
    public void onGoToPosition() {
        if (error != null) {
            onGoTo.accept(error);
        }
    }

    @FXML
    public void onAskAi() {
        if (error != null) {
            onAskAi.accept(error);
        }
    }

    private static void show(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
