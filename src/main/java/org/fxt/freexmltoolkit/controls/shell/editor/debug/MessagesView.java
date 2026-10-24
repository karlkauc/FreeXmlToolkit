package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.service.XsltTransformationResult.TransformationMessage;

/**
 * Live {@code xsl:message} output (plus Saxon warnings and errors) of the debugged run,
 * appended as it happens. Double-clicking a message jumps to its stylesheet line. A toolbar
 * filters by level (all / messages / warnings / errors) and by text, copies the selected — or
 * all shown — messages, and clears the list. Shown as the "Messages" tab of the DEBUG pane;
 * {@link #setOnCountChanged} feeds the tab's badge (the total, independent of the filter).
 */
public class MessagesView extends VBox {

    /** Oldest messages are dropped beyond this many rows. */
    static final int MAX_ROWS = 1000;

    /** Level filter choices. */
    enum LevelFilter {
        ALL("All levels"), MESSAGES("Messages"), WARNINGS("Warnings"), ERRORS("Errors");

        private final String label;

        LevelFilter(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final ObservableList<TransformationMessage> all = FXCollections.observableArrayList();
    private final FilteredList<TransformationMessage> shown = new FilteredList<>(all, m -> true);
    private final ListView<TransformationMessage> list = new ListView<>(shown);
    private final ChoiceBox<LevelFilter> level = new ChoiceBox<>(FXCollections.observableArrayList(LevelFilter.values()));
    private final TextField search = new TextField();
    private final Label placeholder = new Label("No messages yet — xsl:message output appears here.");
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
                setText(format(msg));
                if (isError(msg)) {
                    getStyleClass().add("sev-error");
                } else if (isWarning(msg)) {
                    getStyleClass().add("sev-warning");
                }
            }
        });
        list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        list.setPlaceholder(placeholder);
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                TransformationMessage msg = list.getSelectionModel().getSelectedItem();
                if (msg != null) {
                    onJumpTo.accept(msg);
                }
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        level.setValue(LevelFilter.ALL);
        level.valueProperty().addListener((obs, old, v) -> applyFilter());
        search.setPromptText("Filter messages");
        search.textProperty().addListener((obs, old, v) -> applyFilter());
        HBox.setHgrow(search, Priority.ALWAYS);
        Button copy = DebugToolbar.action("Copy", "bi-clipboard", "Copy the selected messages, or all shown ones");
        copy.setOnAction(e -> copyToClipboard());
        Button clear = DebugToolbar.action("Clear", "bi-trash", "Remove all messages");
        clear.setOnAction(e -> clear());
        HBox toolbar = new HBox(level, search, copy, clear);
        toolbar.getStyleClass().add("fxt-debug-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(toolbar, list);
    }

    /** Appends a message (FX thread) and scrolls to it when it is shown. */
    public void append(TransformationMessage message) {
        if (message == null) {
            return;
        }
        if (all.size() >= MAX_ROWS) {
            all.remove(0);
        }
        all.add(message);
        if (!shown.isEmpty() && shown.get(shown.size() - 1) == message) {
            list.scrollTo(shown.size() - 1);
        }
        onCountChanged.run();
    }

    public void setAll(List<TransformationMessage> messages) {
        all.setAll(messages == null ? List.of() : messages);
        onCountChanged.run();
    }

    public void clear() {
        all.clear();
        onCountChanged.run();
    }

    /** @return the number of messages (all of them, regardless of the filter). */
    public int getRowCount() {
        return all.size();
    }

    /** @return the number of messages the filter shows. */
    public int getVisibleCount() {
        return shown.size();
    }

    /** Invoked on double-click with the message to jump to. */
    public void setOnJumpTo(Consumer<TransformationMessage> handler) {
        this.onJumpTo = handler == null ? m -> { } : handler;
    }

    /** Invoked after every change of the row count (tab badge). */
    public void setOnCountChanged(Runnable handler) {
        this.onCountChanged = handler == null ? () -> { } : handler;
    }

    /** Sets the filters (tests, and the toolbar's controls). */
    void setFilter(LevelFilter levelFilter, String text) {
        level.setValue(levelFilter == null ? LevelFilter.ALL : levelFilter);
        search.setText(text == null ? "" : text);
    }

    /**
     * Copies the selected messages — or, with none selected, all shown ones — one per entry
     * ("LEVEL @ location" line, then the text).
     *
     * @return the copied text
     */
    String copyToClipboard() {
        List<TransformationMessage> source = list.getSelectionModel().getSelectedItems().isEmpty()
                ? List.copyOf(shown) : List.copyOf(list.getSelectionModel().getSelectedItems());
        StringBuilder sb = new StringBuilder();
        for (TransformationMessage m : source) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(format(m));
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(content);
        return sb.toString();
    }

    /** Selects the shown message at {@code index} (tests). */
    void select(int index) {
        list.getSelectionModel().clearAndSelect(index);
    }

    private void applyFilter() {
        LevelFilter lf = level.getValue() == null ? LevelFilter.ALL : level.getValue();
        String text = search.getText() == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        shown.setPredicate(m -> matchesLevel(m, lf) && (text.isEmpty()
                || format(m).toLowerCase(Locale.ROOT).contains(text)));
        placeholder.setText(all.isEmpty() ? "No messages yet — xsl:message output appears here."
                : "No message matches the filter.");
    }

    private static boolean matchesLevel(TransformationMessage m, LevelFilter lf) {
        return switch (lf) {
            case ALL -> true;
            case ERRORS -> isError(m);
            case WARNINGS -> isWarning(m);
            case MESSAGES -> !isError(m) && !isWarning(m);
        };
    }

    static boolean isError(TransformationMessage m) {
        String l = m.getLevel() == null ? "" : m.getLevel();
        return l.startsWith("ERROR") || l.startsWith("TERMINATE");
    }

    static boolean isWarning(TransformationMessage m) {
        return m.getLevel() != null && m.getLevel().startsWith("WARNING");
    }

    private static String format(TransformationMessage msg) {
        String level = msg.getLevel() == null ? "" : msg.getLevel();
        String where = msg.getLocation() == null || msg.getLocation().isBlank() ? "" : "  @ " + msg.getLocation();
        return level + where + "\n" + (msg.getMessage() == null ? "" : msg.getMessage());
    }
}
