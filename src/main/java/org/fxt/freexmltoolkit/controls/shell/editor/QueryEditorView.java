package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.function.Supplier;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxt.freexmltoolkit.controls.shared.CodeAreaFontZoom;
import org.fxt.freexmltoolkit.controls.shared.XPathSyntaxHighlighter;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.XPathIntelliSenseEngine;

/**
 * {@link EditorView} for standalone XPath/XQuery documents ({@code .xpath},
 * {@code .xq}, {@code .xquery}, {@code .xqm}, {@code .xqy}): a plain
 * {@link CodeArea} with the Query Console's XPath/XQuery syntax highlighting,
 * IntelliSense and font zoom. Ctrl+Enter triggers the run handler wired via
 * {@link #configureQuerySupport(Runnable, Supplier)}.
 */
final class QueryEditorView implements EditorView {

    private final CodeArea codeArea = new CodeArea();
    private final VBox root = new VBox();
    private final XPathIntelliSenseEngine intelliSense;
    private Runnable runHandler;

    QueryEditorView(boolean xquery) {
        codeArea.getStyleClass().add("fxt-query-editor");
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.textProperty().addListener((obs, oldText, newText) ->
                codeArea.setStyleSpans(0, XPathSyntaxHighlighter.computeHighlighting(newText)));
        CodeAreaFontZoom.install(codeArea);

        intelliSense = new XPathIntelliSenseEngine(codeArea, xquery);

        // Ctrl+Enter runs the query. While the completion popup is open the engine
        // consumes Enter first, so this filter — added after the engine's — only
        // fires when no popup is showing (same pattern as the Query Console).
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && e.isShortcutDown()
                    && !intelliSense.isPopupShowing() && runHandler != null) {
                runHandler.run();
                e.consume();
            }
        });

        VirtualizedScrollPane<CodeArea> scroll = new VirtualizedScrollPane<>(codeArea);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);
    }

    @Override
    public Region getNode() {
        return root;
    }

    @Override
    public void setText(String text) {
        codeArea.replaceText(text == null ? "" : text);
        codeArea.getUndoManager().forgetHistory();
    }

    @Override
    public String getText() {
        return codeArea.getText();
    }

    @Override
    public CodeArea getCodeArea() {
        return codeArea;
    }

    @Override
    public void configureQuerySupport(Runnable runHandler, Supplier<String> xmlContextSupplier) {
        this.runHandler = runHandler;
        if (xmlContextSupplier != null) {
            intelliSense.setXmlContentSupplier(xmlContextSupplier);
        }
    }
}
