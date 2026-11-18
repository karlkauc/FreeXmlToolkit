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
import org.fxt.freexmltoolkit.controls.shared.PlainTextCodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.XPathIntelliSenseEngine;
import org.fxt.freexmltoolkit.service.query.QueryError;
import org.fxt.freexmltoolkit.service.query.QueryLanguage;
import org.fxt.freexmltoolkit.service.standards.StandardsSettings;

/**
 * {@link EditorView} for standalone XPath/XQuery documents ({@code .xpath},
 * {@code .xq}, {@code .xquery}, {@code .xqm}, {@code .xqy}): a plain
 * {@link CodeArea} with the Query Console's XPath/XQuery syntax highlighting,
 * IntelliSense and font zoom. Ctrl+Enter triggers the run handler wired via
 * {@link #configureQuerySupport(Runnable, Supplier)}.
 */
final class QueryEditorView implements EditorView {

    private final CodeArea codeArea = new PlainTextCodeArea();
    private final VBox root = new VBox();
    private final XPathIntelliSenseEngine intelliSense;
    private Runnable runHandler;
    /** The run target's text (the check declares its prefixes and suggests its namespaces), or {@code null}. */
    private Supplier<String> xmlContext;
    private final QueryInputMarks marks;

    /** The start of a document: enough for its namespace declarations, cheap to check against. */
    private static String head(String text) {
        return text == null ? null : text.substring(0, Math.min(text.length(), 16_384));
    }

    QueryEditorView(boolean xquery) {
        QuerySettings.install();
        codeArea.getStyleClass().add("fxt-query-editor");
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        // syntax highlighting, the check while typing and run errors as marks
        marks = new QueryInputMarks(codeArea,
                () -> xquery ? QueryLanguage.XQUERY : QueryLanguage.XPATH,
                () -> {
                    var tool = StandardsSettings.current().forTool(StandardsSettings.TOOL_QUERY_CONSOLE, null);
                    return xquery ? tool.xquery().xpathVersion() : tool.xpath();
                },
                () -> xmlContext == null ? null : head(xmlContext.get()), null);
        if (xquery) {
            marks.setXQueryVersion(() -> StandardsSettings.current()
                    .forTool(StandardsSettings.TOOL_QUERY_CONSOLE, null).xquery());
        }
        CodeAreaFontZoom.install(codeArea);

        intelliSense = new XPathIntelliSenseEngine(codeArea, xquery);
        intelliSense.setVersion(() -> { // the functions of the chosen version
            var tool = StandardsSettings.current().forTool(StandardsSettings.TOOL_QUERY_CONSOLE, null);
            return xquery ? tool.xquery().xpathVersion() : tool.xpath();
        });

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
        this.xmlContext = xmlContextSupplier;
        if (xmlContextSupplier != null) {
            intelliSense.setXmlContentSupplier(xmlContextSupplier);
        }
    }

    @Override
    public void showQueryError(QueryError error) {
        marks.showError(error);
    }

    /** @return the marks of the query (tests) */
    QueryInputMarks marks() {
        return marks;
    }
}
