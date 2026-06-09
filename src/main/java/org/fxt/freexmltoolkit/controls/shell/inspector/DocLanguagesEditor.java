package org.fxt.freexmltoolkit.controls.shell.inspector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation;

/**
 * Editable list of per-language {@code xs:documentation} entries: each row is a small language code
 * field plus a text area, with Add/Remove. Reports the current list via {@link #setOnChange} when a
 * row loses focus or is added/removed — letting the inspector preserve multi-language documentation
 * (instead of collapsing it to a single string).
 */
public class DocLanguagesEditor extends VBox {

    private final VBox rowsBox = new VBox(6);
    private Consumer<List<XsdDocumentation>> onChange;
    private boolean updating;

    public DocLanguagesEditor() {
        setSpacing(6);
        Button add = new Button("Add language");
        IconifyIcon plus = new IconifyIcon("bi-plus-circle");
        plus.setIconSize(14);
        add.setGraphic(plus);
        add.setOnAction(e -> {
            addRow("", "");
            commit();
        });
        getChildren().addAll(rowsBox, add);
    }

    /** Sets the callback invoked with the current entries whenever they change. */
    public void setOnChange(Consumer<List<XsdDocumentation>> onChange) {
        this.onChange = onChange;
    }

    /** Loads the rows from the given entries (an empty list shows one blank row); fires no change. */
    public void setEntries(List<XsdDocumentation> entries) {
        updating = true;
        try {
            rowsBox.getChildren().clear();
            if (entries == null || entries.isEmpty()) {
                addRow("", "");
            } else {
                for (XsdDocumentation doc : entries) {
                    addRow(doc.getLang() == null ? "" : doc.getLang(),
                            doc.getText() == null ? "" : doc.getText());
                }
            }
        } finally {
            updating = false;
        }
    }

    /** Appends an editable (lang, text) row. */
    public void addRow(String lang, String text) {
        TextField langField = new TextField(lang);
        langField.setPromptText("lang");
        langField.setPrefWidth(64);
        langField.setMaxWidth(64);
        langField.getStyleClass().add("fxt-inspector-edit");

        TextArea textArea = new TextArea(text);
        textArea.setPromptText("documentation");
        textArea.setPrefRowCount(2);
        textArea.setWrapText(true);
        textArea.getStyleClass().add("fxt-inspector-edit");
        HBox.setHgrow(textArea, Priority.ALWAYS);

        Button remove = new Button("✕");
        remove.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        HBox row = new HBox(6, langField, textArea, remove);
        remove.setOnAction(e -> {
            rowsBox.getChildren().remove(row);
            commit();
        });
        langField.focusedProperty().addListener((o, was, now) -> {
            if (was && !now) {
                commit();
            }
        });
        textArea.focusedProperty().addListener((o, was, now) -> {
            if (was && !now) {
                commit();
            }
        });
        rowsBox.getChildren().add(row);
    }

    /** Fires the change callback with the current entries (no-op while loading). */
    public void commit() {
        if (!updating && onChange != null) {
            onChange.accept(collect());
        }
    }

    /** @return the non-blank entries currently in the editor. */
    public List<XsdDocumentation> collect() {
        List<XsdDocumentation> out = new ArrayList<>();
        for (Node node : rowsBox.getChildren()) {
            if (node instanceof HBox row) {
                String lang = ((TextField) row.getChildren().get(0)).getText();
                String text = ((TextArea) row.getChildren().get(1)).getText();
                if (text != null && !text.isBlank()) {
                    out.add(lang == null || lang.isBlank()
                            ? new XsdDocumentation(text)
                            : new XsdDocumentation(text, lang));
                }
            }
        }
        return out;
    }

    /** @return the non-blank documentation texts joined by blank lines (for the read accessor). */
    public String getCombinedText() {
        return rowsBox.getChildren().stream()
                .filter(HBox.class::isInstance)
                .map(n -> ((TextArea) ((HBox) n).getChildren().get(1)).getText())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining("\n\n"));
    }

    /** @return the current number of rows (for tests/observers). */
    public int getRowCount() {
        return rowsBox.getChildren().size();
    }
}
