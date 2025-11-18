package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.editor.SyntaxHighlightManager;
import org.fxt.freexmltoolkit.service.ThreadPoolManager;

/**
 * Syntax highlighting manager for XmlCodeEditorV2.
 * TODO: Implement full V2 version with configurable debouncing.
 *
 * <p>For now, delegates to V1 implementation.</p>
 */
public class SyntaxHighlightManagerV2 {

    private static final Logger logger = LogManager.getLogger(SyntaxHighlightManagerV2.class);

    private final CodeArea codeArea;
    private final SyntaxHighlightManager v1Manager;

    public SyntaxHighlightManagerV2(CodeArea codeArea) {
        this.codeArea = codeArea;
        // Delegate to V1 for now - get ThreadPoolManager instance
        ThreadPoolManager threadPoolManager = ThreadPoolManager.getInstance();
        this.v1Manager = new SyntaxHighlightManager(codeArea, threadPoolManager);
        logger.info("SyntaxHighlightManagerV2 created (using V1 delegate with ThreadPoolManager)");
    }

    /**
     * Applies syntax highlighting to text.
     * TODO: Implement V2 version with better debouncing.
     */
    public void applySyntaxHighlighting(String text) {
        v1Manager.applySyntaxHighlighting(text);
    }

    /**
     * Refreshes syntax highlighting.
     */
    public void refreshSyntaxHighlighting(String text) {
        v1Manager.refreshSyntaxHighlighting(text);
    }
}
