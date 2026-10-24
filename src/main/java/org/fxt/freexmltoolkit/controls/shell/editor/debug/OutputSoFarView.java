package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.Locale;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
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
 * Read-only, highlighted, with Copy; the end of the text is scrolled into view. When the
 * stylesheet writes {@code xsl:result-document}s a selector switches between the principal and
 * the secondary results; "Documents" lists what the run loaded with {@code doc()},
 * {@code document()} or {@code collection()} (files open in the editor).
 */
public class OutputSoFarView extends VBox {

    private final Label info = new Label("The output appears here while the run is paused.");
    private final CodeArea area = new CodeArea();
    private static final String PRINCIPAL = "Principal result";
    private final ChoiceBox<String> resultChoice = new ChoiceBox<>();
    private final MenuButton documents = new MenuButton("Documents");
    /** The texts of the current state: "" = principal, else result-document URI. */
    private java.util.Map<String, String> texts = new java.util.LinkedHashMap<>();
    private boolean finalResult;
    private java.util.function.Consumer<String> onOpenDocument = uri -> { };

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
        resultChoice.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(String key) {
                return key == null || key.isEmpty() ? PRINCIPAL
                        : org.fxt.freexmltoolkit.debugger.SourceLocations.fileName(key);
            }

            @Override
            public String fromString(String label) {
                return label;
            }
        });
        resultChoice.valueProperty().addListener((obs, old, key) -> showSelected());
        resultChoice.setVisible(false);
        resultChoice.setManaged(false);
        org.fxt.freexmltoolkit.controls.icons.IconifyIcon docIcon =
                new org.fxt.freexmltoolkit.controls.icons.IconifyIcon("bi-files");
        docIcon.setIconSize(14);
        documents.setGraphic(docIcon);
        documents.getStyleClass().addAll("fxt-sp-action", "fxt-output-action");
        documents.setVisible(false);
        documents.setManaged(false);
        HBox toolbar = new HBox(resultChoice, info, documents, copy);
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
        showSoFar(text, java.util.Map.of(), java.util.List.of());
    }

    /**
     * Shows the output written up to the current pause, with the secondary results written so
     * far and the documents loaded so far.
     */
    public void showSoFar(String principal, java.util.Map<String, String> resultDocuments, java.util.List<String> loaded) {
        finalResult = false;
        update(principal, resultDocuments, loaded);
    }

    /** Shows the complete result of a finished run. */
    public void showFinal(String text) {
        showFinal(text, java.util.Map.of(), java.util.List.of());
    }

    /** Shows the complete results of a finished run and the documents it loaded. */
    public void showFinal(String principal, java.util.Map<String, String> resultDocuments, java.util.List<String> loaded) {
        finalResult = true;
        update(principal == null ? "" : principal, resultDocuments, loaded);
    }

    /** Clears the tab (a new run starts). */
    public void clear() {
        texts = new java.util.LinkedHashMap<>();
        resultChoice.getItems().clear();
        resultChoice.setVisible(false);
        resultChoice.setManaged(false);
        setDocuments(java.util.List.of());
        show("", "The output appears here while the run is paused.");
    }

    /** Invoked with the URI of a loaded document to open (Documents menu). */
    public void setOnOpenDocument(java.util.function.Consumer<String> handler) {
        this.onOpenDocument = handler == null ? uri -> { } : handler;
    }

    private void update(String principal, java.util.Map<String, String> resultDocuments, java.util.List<String> loaded) {
        java.util.Map<String, String> next = new java.util.LinkedHashMap<>();
        next.put("", principal);
        if (resultDocuments != null) {
            next.putAll(resultDocuments);
        }
        texts = next;
        String selected = resultChoice.getValue();
        resultChoice.getItems().setAll(next.keySet());
        boolean several = next.size() > 1;
        resultChoice.setVisible(several);
        resultChoice.setManaged(several);
        resultChoice.setValue(selected != null && next.containsKey(selected) ? selected : "");
        setDocuments(loaded == null ? java.util.List.of() : loaded);
        showSelected();
    }

    private void showSelected() {
        String key = resultChoice.getValue() == null ? "" : resultChoice.getValue();
        String text = texts.get(key);
        String what = key.isEmpty() ? "" : org.fxt.freexmltoolkit.debugger.SourceLocations.fileName(key) + ": ";
        if (text == null) {
            show("", key.isEmpty() && !finalResult ? "No output to show." : "");
        } else if (finalResult) {
            show(text, what + String.format(Locale.ROOT, key.isEmpty()
                    ? "Complete result: %,d chars (also in OUTPUT)" : "%,d chars", text.length()));
        } else {
            show(text, what + String.format(Locale.ROOT,
                    "Written so far: %,d chars — the rest follows when the run continues", text.length()));
        }
    }

    private void setDocuments(java.util.List<String> loaded) {
        documents.getItems().clear();
        for (String uri : loaded) {
            MenuItem item = new MenuItem(uri);
            boolean file = uri.startsWith("file:");
            item.setDisable(!file);
            item.setOnAction(e -> onOpenDocument.accept(uri));
            documents.getItems().add(item);
        }
        documents.setText("Documents (" + loaded.size() + ")");
        documents.setVisible(!loaded.isEmpty());
        documents.setManaged(!loaded.isEmpty());
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

    /** Selects a result: {@code ""} = principal, else a result-document URI (tests). */
    void selectResult(String key) {
        resultChoice.setValue(key);
    }

    /** The loaded documents as listed (tests). */
    java.util.List<String> documentItems() {
        return documents.getItems().stream().map(MenuItem::getText).toList();
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
