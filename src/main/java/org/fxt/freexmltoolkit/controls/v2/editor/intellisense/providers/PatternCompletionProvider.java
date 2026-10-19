package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorMode;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextType;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XmlContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;

/**
 * Pattern-based completion provider that suggests previously used elements and attributes.
 * This is a fallback provider that analyzes the document to suggest completions.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Extracts element names from document</li>
 *   <li>Extracts attribute names from document</li>
 *   <li>Frequency-based relevance scoring</li>
 *   <li>Works without schema</li>
 * </ul>
 */
public class PatternCompletionProvider implements CompletionProvider {

    private static final Logger logger = LogManager.getLogger(PatternCompletionProvider.class);

    private final EditorContext editorContext;

    // Pattern to extract element names: <elementName ...>
    private static final Pattern ELEMENT_PATTERN = Pattern.compile("<([a-zA-Z_][\\w:.-]*)(?:\\s|>|/)");

    // Pattern to extract attribute names: attribute="value"
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("\\s+([a-zA-Z_][\\w:.-]*)\\s*=");

    // Cache for parsed patterns: the names of one text revision (written on the FX thread)
    private long cachedRevision = -1;
    private Map<String, Integer> cachedElements = null;
    private Map<String, Integer> cachedAttributes = null;
    /** Counts text edits (FX thread), so a stale cache is detected without comparing whole texts. */
    private long textRevision;
    /** Revision a background re-parse is running for (-1 = none). */
    private long refreshingRevision = -1;

    /** Re-parses the names off the FX thread; one daemon thread shared by all editors. */
    private static final java.util.concurrent.ExecutorService PARSER =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "PatternCompletions");
                t.setDaemon(true);
                return t;
            });

    /**
     * Creates a pattern completion provider.
     *
     * @param editorContext the editor context
     */
    public PatternCompletionProvider(EditorContext editorContext) {
        this.editorContext = Objects.requireNonNull(editorContext, "EditorContext cannot be null");
        if (editorContext.getCodeArea() != null) {
            editorContext.getCodeArea().plainTextChanges().subscribe(change -> textRevision++);
        }
    }

    @Override
    public boolean canProvideCompletions(XmlContext context, EditorMode mode) {
        // Provide for all modes as fallback
        // Only provide for element and attribute contexts
        return context.getType() == ContextType.ELEMENT ||
               context.getType() == ContextType.ATTRIBUTE;
    }

    @Override
    public List<CompletionItem> getCompletions(XmlContext context) {
        List<CompletionItem> items = new ArrayList<>();

        if (cachedElements == null) {
            // First use: parse now, there is nothing to show yet
            String documentText = getDocumentText(context);
            if (documentText == null || documentText.isEmpty()) {
                return items;
            }
            parsePatterns(documentText);
            cachedRevision = textRevision;
        } else if (cachedRevision != textRevision) {
            // The document changed: offer the names seen so far now and refresh them off the FX thread
            // (two regex passes over the whole document cost tens of ms per typed '<' on large files)
            refreshInBackground();
        }

        // Provide completions based on context type
        switch (context.getType()) {
            case ELEMENT -> items.addAll(getElementCompletions());
            case ATTRIBUTE -> items.addAll(getAttributeCompletions());
            default -> { }
        }

        logger.debug("PatternProvider returned {} completions for context: {}",
                     items.size(), context.getType());
        return items;
    }

    /**
     * Gets the full document text from EditorContext.
     */
    private String getDocumentText(XmlContext _context) {
        return editorContext.getText();
    }

    private void refreshInBackground() {
        long revision = textRevision;
        if (refreshingRevision == revision) {
            return;
        }
        refreshingRevision = revision;
        String text = editorContext.getText();
        PARSER.execute(() -> {
            Map<String, Integer> elements = new HashMap<>();
            Map<String, Integer> attributes = new HashMap<>();
            collectPatterns(text, elements, attributes);
            javafx.application.Platform.runLater(() -> {
                if (refreshingRevision == revision) {
                    refreshingRevision = -1;
                }
                cachedElements = elements;
                cachedAttributes = attributes;
                cachedRevision = revision;
            });
        });
    }

    /**
     * Parses patterns from document text.
     */
    private void parsePatterns(String text) {
        cachedElements = new HashMap<>();
        cachedAttributes = new HashMap<>();
        collectPatterns(text, cachedElements, cachedAttributes);
    }

    private static void collectPatterns(String text, Map<String, Integer> cachedElements,
                                        Map<String, Integer> cachedAttributes) {
        if (text == null) {
            return;
        }

        // Extract element names
        Matcher elementMatcher = ELEMENT_PATTERN.matcher(text);
        while (elementMatcher.find()) {
            String elementName = elementMatcher.group(1);
            cachedElements.merge(elementName, 1, Integer::sum);
        }

        // Extract attribute names
        Matcher attributeMatcher = ATTRIBUTE_PATTERN.matcher(text);
        while (attributeMatcher.find()) {
            String attributeName = attributeMatcher.group(1);
            cachedAttributes.merge(attributeName, 1, Integer::sum);
        }

        logger.debug("Parsed {} unique elements, {} unique attributes",
                     cachedElements.size(), cachedAttributes.size());
    }

    /**
     * Gets element completions based on document patterns.
     */
    private List<CompletionItem> getElementCompletions() {
        List<CompletionItem> items = new ArrayList<>();

        if (cachedElements == null || cachedElements.isEmpty()) {
            return items;
        }

        // Sort by frequency (descending)
        List<Map.Entry<String, Integer>> sortedElements = new ArrayList<>(cachedElements.entrySet());
        sortedElements.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        // Create completion items
        for (Map.Entry<String, Integer> entry : sortedElements) {
            String elementName = entry.getKey();
            int frequency = entry.getValue();

            CompletionItem item = new CompletionItem.Builder(
                elementName,
                elementName,
                CompletionItemType.ELEMENT
            )
            .description("Used " + frequency + " time(s) in document")
            .relevanceScore(50 + Math.min(frequency * 5, 50)) // Base 50, max 100
            .build();

            items.add(item);
        }

        return items;
    }

    /**
     * Gets attribute completions based on document patterns.
     */
    private List<CompletionItem> getAttributeCompletions() {
        List<CompletionItem> items = new ArrayList<>();

        if (cachedAttributes == null || cachedAttributes.isEmpty()) {
            return items;
        }

        // Sort by frequency (descending)
        List<Map.Entry<String, Integer>> sortedAttributes = new ArrayList<>(cachedAttributes.entrySet());
        sortedAttributes.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        // Create completion items
        for (Map.Entry<String, Integer> entry : sortedAttributes) {
            String attributeName = entry.getKey();
            int frequency = entry.getValue();

            CompletionItem item = new CompletionItem.Builder(
                attributeName,
                attributeName + "=\"\"",
                CompletionItemType.ATTRIBUTE
            )
            .description("Used " + frequency + " time(s) in document")
            .relevanceScore(50 + Math.min(frequency * 5, 50)) // Base 50, max 100
            .build();

            items.add(item);
        }

        return items;
    }

    @Override
    public int getPriority() {
        return 10; // Lowest priority - fallback provider
    }

    @Override
    public String getName() {
        return "Pattern Completion Provider";
    }

    /**
     * Invalidates the cache.
     * Call this when document changes significantly.
     */
    public void invalidateCache() {
        cachedRevision = -1;
        cachedElements = null;
        cachedAttributes = null;
        logger.debug("Pattern cache invalidated");
    }
}
