package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorMode;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XmlContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;

import java.util.List;

/**
 * Interface for completion providers.
 * Each provider can supply completions for specific contexts.
 *
 * <p>Providers are registered in the ProviderRegistry and queried in priority order.</p>
 */
public interface CompletionProvider {

    /**
     * Checks if this provider can provide completions for the given context.
     *
     * @param context the XML context
     * @param mode    the editor mode
     * @return true if this provider is applicable
     */
    boolean canProvideCompletions(XmlContext context, EditorMode mode);

    /**
     * Gets completion items for the given context.
     *
     * @param context the XML context
     * @return list of completion items (never null, may be empty)
     */
    List<CompletionItem> getCompletions(XmlContext context);

    /**
     * Gets the priority of this provider.
     * Higher priority providers are queried first.
     *
     * @return the priority (higher = more important)
     */
    int getPriority();

    /**
     * Gets the name of this provider for logging and debugging.
     *
     * @return the provider name
     */
    String getName();
}
