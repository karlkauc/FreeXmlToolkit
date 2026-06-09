package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine.TemplateMatchInfo;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.fxt.freexmltoolkit.service.XsltTransformationResult.TransformationMessage;

/** Read-only execution trace: template matches and {@code xsl:message} output. */
public class TraceView extends VBox {

    private final TableView<TemplateMatchInfo> matches = new TableView<>();
    private final TableView<TransformationMessage> messages = new TableView<>();

    public TraceView(XsltTransformationResult result) {
        setSpacing(10);
        setPadding(new Insets(16));
        getStyleClass().add("fxt-side-panel-content");

        Label matchTitle = new Label("TEMPLATE MATCHES");
        matchTitle.getStyleClass().add("fxt-side-panel-title");
        matches.getColumns().add(DebugTableColumns.col("Pattern", t -> t.pattern() != null ? t.pattern() : "", 200));
        matches.getColumns().add(DebugTableColumns.col("Name", t -> t.name() != null ? t.name() : "", 140));
        matches.getColumns().add(DebugTableColumns.col("Line", t -> Integer.toString(t.lineNumber()), 60));
        matches.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        matches.setPlaceholder(new Label("No template matches captured."));
        if (result.getTemplateMatches() != null) {
            matches.getItems().setAll(result.getTemplateMatches());
        }
        VBox.setVgrow(matches, Priority.ALWAYS);

        Label msgTitle = new Label("MESSAGES");
        msgTitle.getStyleClass().add("fxt-side-panel-title");
        messages.getColumns().add(DebugTableColumns.col("Level", TransformationMessage::getLevel, 80));
        messages.getColumns().add(DebugTableColumns.col("Message", TransformationMessage::getMessage, -1));
        messages.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        messages.setPlaceholder(new Label("No xsl:message output."));
        if (result.getMessages() != null) {
            messages.getItems().setAll(result.getMessages());
        }
        VBox.setVgrow(messages, Priority.ALWAYS);

        getChildren().addAll(matchTitle, matches, msgTitle, messages);
    }

    public int getMatchCount() {
        return matches.getItems().size();
    }

    public int getMessageCount() {
        return messages.getItems().size();
    }
}
