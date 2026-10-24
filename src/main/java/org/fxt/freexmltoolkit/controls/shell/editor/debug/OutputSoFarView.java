package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.Locale;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controls.shared.CodeAreaFontZoom;
import org.fxt.freexmltoolkit.controls.shell.editor.ResultHighlighter;

/**
 * The "Output" tab of the DEBUG pane: what the transformation has written so far, refreshed at
 * every pause (read while the run is blocked), and the complete result once it finishes.
 * Read-only, highlighted, with Copy; the end of the text is scrolled into view.
 */
public class OutputSoFarView extends VBox {

    private final Label info = new Label("The output appears here while the run is paused.");
    private final CodeArea area = new CodeArea();

    public OutputSoFarView() {
        getStyleClass().add("fxt-debug-panel");
        info.getStyleClass().add("fxt-output-status");
        info.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(info, Priority.ALWAYS);
        Button copy = DebugToolbar.action("Copy", "bi-clipboard", "Copy the output shown here");
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(area.getText());
            Clipboard.getSystemClipboard().setContent(content);
        });
        HBox toolbar = new HBox(info, copy);
        toolbar.getStyleClass().add("fxt-debug-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        area.setEditable(false);
        area.getStyleClass().addAll("fxt-transform-output", "fxt-query-results");
        area.setParagraphGraphicFactory(LineNumberFactory.get(area));
        CodeAreaFontZoom.install(area);
        VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<>(area);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(toolbar, scroll);
    }

    /** Shows the output written up to the current pause ({@code null}: nothing to read). */
    public void showSoFar(String text) {
        show(text == null ? "" : text,
                text == null ? "No output to show." : String.format(Locale.ROOT,
                        "Written so far: %,d chars — the rest follows when the run continues", text.length()));
    }

    /** Shows the complete result of a finished run. */
    public void showFinal(String text) {
        String value = text == null ? "" : text;
        show(value, String.format(Locale.ROOT, "Complete result: %,d chars (also in OUTPUT)", value.length()));
    }

    /** Clears the tab (a new run starts). */
    public void clear() {
        show("", "The output appears here while the run is paused.");
    }

    private void show(String text, String infoText) {
        info.setText(infoText);
        if (text.equals(area.getText())) {
            return;
        }
        area.replaceText(text);
        if (!text.isEmpty() && text.length() <= ResultHighlighter.HIGHLIGHT_LIMIT_CHARS) {
            String trimmed = text.stripLeading();
            ResultHighlighter.highlight(area, text, trimmed.startsWith("<"),
                    trimmed.startsWith("{") || trimmed.startsWith("["));
        }
        area.moveTo(area.getLength());
        area.requestFollowCaret();
    }

    /** The shown text (tests). */
    public String getText() {
        return area.getText();
    }

    /** The info line (tests). */
    public String getInfo() {
        return info.getText();
    }
}
