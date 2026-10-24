package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.List;
import java.util.function.Consumer;

import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.service.XsltTransformationResult.TransformationMessage;

/**
 * Live {@code xsl:message} output (plus Saxon warnings and errors) of the debugged run,
 * appended as it happens. Double-clicking a message jumps to its stylesheet line. Shown as
 * the "Messages" tab of the DEBUG pane; {@link #setOnCountChanged} feeds the tab's badge.
 */
public class MessagesView extends VBox {

    /** Oldest messages are dropped beyond this many rows. */
    static final int MAX_ROWS = 1000;

    private final ListView<TransformationMessage> list = new ListView<>();
    private Consumer<TransformationMessage> onJumpTo = m -> { };
    private Runnable onCountChanged = () -> { };

    public MessagesView() {
        getStyleClass().add("fxt-debug-panel");

        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(TransformationMessage msg, boolean empty) {
                super.updateItem(msg, empty);
                getStyleClass().removeAll("sev-error", "sev-warning");
                if (empty || msg == null) {
                    setText(null);
                    return;
                }
                String level = msg.getLevel() == null ? "" : msg.getLevel();
                String where = msg.getLocation() == null || msg.getLocation().isBlank() ? "" : "  @ " + msg.getLocation();
                setText(level + where + "\n" + (msg.getMessage() == null ? "" : msg.getMessage()));
                if (level.startsWith("ERROR") || level.startsWith("TERMINATE")) {
                    getStyleClass().add("sev-error");
                } else if (level.startsWith("WARNING")) {
                    getStyleClass().add("sev-warning");
                }
            }
        });
        list.setPlaceholder(new Label("No messages yet — xsl:message output appears here."));
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                TransformationMessage msg = list.getSelectionModel().getSelectedItem();
                if (msg != null) {
                    onJumpTo.accept(msg);
                }
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        getChildren().add(list);
    }

    /** Appends a message (FX thread) and scrolls to it. */
    public void append(TransformationMessage message) {
        if (message == null) {
            return;
        }
        if (list.getItems().size() >= MAX_ROWS) {
            list.getItems().remove(0);
        }
        list.getItems().add(message);
        list.scrollTo(list.getItems().size() - 1);
        onCountChanged.run();
    }

    public void setAll(List<TransformationMessage> messages) {
        list.getItems().setAll(messages == null ? List.of() : messages);
        onCountChanged.run();
    }

    public void clear() {
        list.getItems().clear();
        onCountChanged.run();
    }

    public int getRowCount() {
        return list.getItems().size();
    }

    /** Invoked on double-click with the message to jump to. */
    public void setOnJumpTo(Consumer<TransformationMessage> handler) {
        this.onJumpTo = handler == null ? m -> { } : handler;
    }

    /** Invoked after every change of the row count (tab badge). */
    public void setOnCountChanged(Runnable handler) {
        this.onCountChanged = handler == null ? () -> { } : handler;
    }
}
