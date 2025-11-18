package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.registry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorMode;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XmlContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers.CompletionProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for completion providers.
 * Manages provider registration and queries them in priority order.
 */
public class ProviderRegistry {

    private static final Logger logger = LogManager.getLogger(ProviderRegistry.class);

    private final List<CompletionProvider> providers = new CopyOnWriteArrayList<>();

    /**
     * Registers a completion provider.
     *
     * @param provider the provider to register
     */
    public void registerProvider(CompletionProvider provider) {
        providers.add(provider);
        providers.sort(Comparator.comparingInt(CompletionProvider::getPriority).reversed());
        logger.debug("Registered provider: {} (priority: {})", provider.getName(), provider.getPriority());
    }

    /**
     * Unregisters a completion provider.
     *
     * @param provider the provider to unregister
     */
    public void unregisterProvider(CompletionProvider provider) {
        providers.remove(provider);
        logger.debug("Unregistered provider: {}", provider.getName());
    }

    /**
     * Gets completions for the given context.
     * Queries providers in priority order and returns results from the first applicable provider.
     *
     * @param context the XML context
     * @param mode    the editor mode
     * @return list of completion items (never null, may be empty)
     */
    public List<CompletionItem> getCompletions(XmlContext context, EditorMode mode) {
        for (CompletionProvider provider : providers) {
            if (provider.canProvideCompletions(context, mode)) {
                logger.debug("Using provider: {}", provider.getName());
                List<CompletionItem> items = provider.getCompletions(context);
                logger.debug("Provider returned {} items", items.size());
                return items;
            }
        }

        logger.debug("No provider found for context: {}", context.getType());
        return new ArrayList<>();
    }

    /**
     * Gets the number of registered providers.
     *
     * @return the provider count
     */
    public int getProviderCount() {
        return providers.size();
    }

    /**
     * Clears all registered providers.
     */
    public void clearProviders() {
        providers.clear();
        logger.debug("Cleared all providers");
    }
}
