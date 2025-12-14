package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxt.freexmltoolkit.service.XmlValidationError;
import org.fxt.freexmltoolkit.service.XmlValidationResult;
import org.fxt.freexmltoolkit.util.ContextMenuFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced XML Code Editor with professional features inspired by modern IDEs.
 * Provides advanced editing capabilities, intelligent auto-completion,
 * multi-cursor support, and performance optimization for large files.
 *
 * @deprecated This is a V1 editor class. V2 editors should be used instead.
 *             This class will be removed in a future version.
 */
@Deprecated(since = "2.0", forRemoval = true)
public class EnhancedXmlCodeEditor extends StackPane {

    private static final Logger logger = LogManager.getLogger(EnhancedXmlCodeEditor.class);

    private final CodeArea codeArea;
    private final ExecutorService executor;
    private final ProgressManager progressManager;

    // Enhanced features
    private boolean syntaxHighlightingEnabled = true;
    private final boolean autoCompletionEnabled = true;
    private boolean bracketMatchingEnabled = true;
    private final boolean autoIndentEnabled = true;

    // Performance optimization settings
    private static final int LARGE_FILE_THRESHOLD = 100000; // 100KB threshold
    private static final int SYNTAX_HIGHLIGHTING_CHUNK_SIZE = 20000; // Process in 20KB chunks
    private boolean isLargeFile = false;
    private long lastHighlightingTime = 0;

    // Multi-cursor and advanced editing
    private boolean multiCursorMode = false;
    private final List<Integer> multiCursorPositions = new ArrayList<>();
    private boolean blockSelectionMode = false;

    // Enhanced editing features
    private final boolean smartPairEnabled = true;
    private final Map<String, String> xmlPairs = Map.of(
            "<", ">",
            "\"", "\"",
            "'", "'",
            "(", ")",
            "[", "]",
            "{", "}"
    );

    // Enhanced Bracket Matching and Tag-Pair Highlighting
    private boolean tagPairHighlightingEnabled = true;
    private boolean bracketRainbowEnabled = true;
    private BracketMatchResult currentBracketMatch;
    private final List<TagPairHighlight> activeTagHighlights = new ArrayList<>();
    private static final Pattern XML_TAG_PATTERN = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9-_]*)[^>]*(/?)>");
    private static final String[] RAINBOW_COLORS = {
            "#e74c3c", "#f39c12", "#f1c40f", "#27ae60", "#3498db", "#9b59b6", "#e91e63"
    };

    // Intelligent Folding System
    private boolean foldingEnabled = true;
    private final Map<Integer, XmlFoldingRegion> foldingRegions = new HashMap<>();
    private final List<XmlFoldingRegion> activeFolds = new ArrayList<>();
    private static final Pattern XML_ELEMENT_PATTERN = Pattern.compile("<([a-zA-Z][a-zA-Z0-9-_]*)[^>]*(?<!/)>");
    private static final Pattern XML_CLOSING_PATTERN = Pattern.compile("</([a-zA-Z][a-zA-Z0-9-_]*)>");
    private static final Pattern XML_COMMENT_PATTERN = Pattern.compile("<!--[\\s\\S]*?-->");
    private static final Pattern XML_CDATA_PATTERN = Pattern.compile("<!\\[CDATA\\[[\\s\\S]*?\\]\\]>");

    // Smart Auto-completion and IntelliSense with Snippet Support
    private XmlIntelliSense xmlIntelliSense;
    private ContextMenu autoCompletionPopup;
    private XmlSnippetManager snippetManager;
    private boolean snippetSupportEnabled = true;
    private boolean contextAwareCompletionEnabled = true;
    private final Map<String, String> xmlSnippets = new HashMap<>();

    // Advanced Search & Replace
    private XmlSearchReplacePanel searchReplacePanel;
    private boolean searchReplaceEnabled = true;

    // Modern Syntax Highlighting System
    private AdvancedXmlSyntaxHighlighter advancedHighlighter;
    private ModernXmlThemeManager themeManager;
    private final boolean advancedHighlightingEnabled = true;

    // XML Validation Integration
    private XmlValidationPanel validationPanel;
    private boolean validationEnabled = true;
    private boolean showValidationErrors = true;

    public EnhancedXmlCodeEditor() {
        // Initialize executor for background tasks
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("EnhancedXmlCodeEditor-Worker");
            return t;
        });

        // Initialize progress manager
        progressManager = ProgressManager.getInstance();

        // Initialize CodeArea with enhanced features
        codeArea = new CodeArea();
        setupCodeArea();

        // Initialize IntelliSense and Snippet Support
        initializeIntelliSense();
        initializeSnippetSupport();

        // Initialize Search & Replace
        initializeSearchReplace();

        // Initialize Modern Syntax Highlighting
        initializeAdvancedHighlighting();

        // Initialize XML Validation
        initializeValidation();

        // Setup keyboard shortcuts
        setupKeyboardShortcuts();

        // Setup context menu
        setupContextMenu();

        // Add to layout
        this.getChildren().add(codeArea);

        logger.info("EnhancedXmlCodeEditor initialized successfully with professional features");
    }

    /**
     * Set up the code area with enhanced XML-specific features
     */
    private void setupCodeArea() {
        // Add line numbers with enhanced styling
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

        // Set up enhanced syntax highlighting
        setupAdvancedSyntaxHighlighting();

        // Set up intelligent auto-indentation
        setupIntelligentAutoIndentation();

        // Set up bracket matching
        setupBracketMatching();

        // Set up intelligent folding system
        setupIntelligentFolding();

        // Apply enhanced CSS styling
        codeArea.getStylesheets().add(getClass().getResource("/css/xml-editor.css").toExternalForm());
        codeArea.getStylesheets().add(getClass().getResource("/css/schematron-editor.css").toExternalForm());
        codeArea.getStyleClass().addAll("code-area", "xml-code-area");

        // Set modern font
        codeArea.setStyle("-fx-font-family: 'Fira Code', 'Consolas', 'Monaco', 'Courier New', monospace; " +
                "-fx-font-size: 13px; " +
                "-fx-line-spacing: 1.2em;");

        // Enable word wrap for long lines (toggleable)
        codeArea.setWrapText(false);

        logger.debug("Enhanced CodeArea setup completed");
    }

    /**
     * Set up advanced syntax highlighting with performance optimization
     */
    private void setupAdvancedSyntaxHighlighting() {
        if (!syntaxHighlightingEnabled) {
            return;
        }

        // Apply syntax highlighting on text changes with adaptive debouncing
        codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(isLargeFile ? 800 : 200)) // Faster for small files
                .retainLatestUntilLater(executor)
                .supplyTask(() -> computeAdvancedHighlightingAsync(codeArea.getText()))
                .awaitLatest(codeArea.multiPlainChanges())
                .filterMap(t -> {
                    if (t.isSuccess()) {
                        return java.util.Optional.of(t.get());
                    } else {
                        logger.warn("Advanced syntax highlighting failed", t.getFailure());
                        return java.util.Optional.empty();
                    }
                })
                .subscribe(highlighting -> {
                    if (highlighting != null) {
                        codeArea.setStyleSpans(0, highlighting);
                        lastHighlightingTime = System.currentTimeMillis();

                        // Update bracket matching after highlighting
                        updateBracketMatching();
                    }
                });

        logger.debug("Advanced syntax highlighting enabled with performance optimization");
    }

    /**
     * Compute advanced syntax highlighting asynchronously
     */
    private Task<StyleSpans<Collection<String>>> computeAdvancedHighlightingAsync(String text) {
        Task<StyleSpans<Collection<String>>> task = new Task<StyleSpans<Collection<String>>>() {
            @Override
            protected StyleSpans<Collection<String>> call() throws Exception {
                long startTime = System.currentTimeMillis();

                // Check if file is large and optimize processing
                if (text.length() > LARGE_FILE_THRESHOLD) {
                    isLargeFile = true;
                    updateMessage("Processing large XML file with optimized highlighting...");
                    logger.debug("Processing large XML file ({} chars) with chunked highlighting", text.length());
                    return computeChunkedHighlighting(text);
                } else {
                    isLargeFile = false;
                    updateMessage("Applying XML syntax highlighting...");
                    StyleSpans<Collection<String>> result = EnhancedXmlSyntaxHighlighter.computeHighlighting(text);
                    long endTime = System.currentTimeMillis();
                    logger.debug("Advanced syntax highlighting completed in {}ms for {} chars",
                            endTime - startTime, text.length());
                    return result;
                }
            }
        };
        executor.execute(task);
        return task;
    }

    /**
     * Compute syntax highlighting in optimized chunks for large files
     */
    private StyleSpans<Collection<String>> computeChunkedHighlighting(String text) {
        if (text.length() <= SYNTAX_HIGHLIGHTING_CHUNK_SIZE) {
            return EnhancedXmlSyntaxHighlighter.computeHighlighting(text);
        }

        // For very large files, use streaming approach
        try {
            // Intelligent chunking based on XML structure
            return EnhancedXmlSyntaxHighlighter.computeStreamingHighlighting(text, SYNTAX_HIGHLIGHTING_CHUNK_SIZE);
        } catch (Exception e) {
            logger.warn("Streaming highlighting failed, falling back to basic highlighting", e);
            return EnhancedXmlSyntaxHighlighter.computeBasicHighlighting(text);
        }
    }

    /**
     * Set up intelligent auto-indentation for XML
     */
    private void setupIntelligentAutoIndentation() {
        if (!autoIndentEnabled) {
            return;
        }

        codeArea.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleIntelligentEnterKey(event);
            } else if (event.getCode() == KeyCode.TAB) {
                handleSmartTabKey(event);
            } else if (event.getCode() == KeyCode.BACK_SPACE) {
                handleSmartBackspace(event);
            }
        });

        // Auto-close XML tags
        codeArea.setOnKeyTyped(event -> {
            if (autoCompletionEnabled) {
                handleAutoTagClosing(event);
            }
        });
    }

    /**
     * Handle intelligent Enter key for auto-indentation
     */
    private void handleIntelligentEnterKey(KeyEvent event) {
        int caretPosition = codeArea.getCaretPosition();
        int paragraph = codeArea.getCurrentParagraph();

        // Get current line text and context
        String currentLine = codeArea.getParagraph(paragraph).getText();
        String previousLine = paragraph > 0 ? codeArea.getParagraph(paragraph - 1).getText() : "";

        // Calculate intelligent indentation
        String baseIndentation = extractIndentation(currentLine);
        String smartIndentation = calculateSmartIndentation(currentLine, previousLine, baseIndentation);

        // Check for special XML contexts
        int columnPosition = caretPosition - codeArea.position(paragraph, 0).toOffset();
        if (isInsideXmlTag(currentLine, columnPosition)) {
            // Inside XML tag - format attributes nicely
            smartIndentation = baseIndentation + "    "; // 4 spaces
        } else if (isAfterOpeningTag(currentLine)) {
            // After opening tag - increase indentation
            smartIndentation = baseIndentation + "    ";
        } else if (isBeforeClosingTag(currentLine, columnPosition)) {
            // Auto-insert closing tag
            String tagName = extractTagNameForClosing(currentLine, previousLine);
            if (tagName != null && !tagName.isEmpty()) {
                final String finalSmartIndentation = smartIndentation;
                final String finalBaseIndentation = baseIndentation;
                final int finalCaretPosition = caretPosition;
                Platform.runLater(() -> {
                    int newCaretPos = finalCaretPosition + 1 + finalSmartIndentation.length();
                    codeArea.insertText(finalCaretPosition, "\n" + finalSmartIndentation + "\n" + finalBaseIndentation + "</" + tagName + ">");
                    codeArea.moveTo(newCaretPos);
                });
                event.consume();
                return;
            }
        }

        // Insert intelligent indentation
        final String finalSmartIndentation = smartIndentation;
        final int finalCaretPosition = caretPosition;
        Platform.runLater(() -> {
            codeArea.insertText(finalCaretPosition, "\n" + finalSmartIndentation);
        });
        event.consume();
    }

    /**
     * Handle smart tab key behavior
     */
    private void handleSmartTabKey(KeyEvent event) {
        if (event.isShiftDown()) {
            // Shift+Tab: Decrease indentation
            decreaseIndentation();
        } else {
            // Tab: Increase indentation or trigger completion
            if (autoCompletionEnabled && shouldShowCompletion()) {
                showEnhancedAutoCompletion();
            } else {
                increaseIndentation();
            }
        }
        event.consume();
    }

    /**
     * Handle smart backspace (remove full indentation if at start of whitespace)
     */
    private void handleSmartBackspace(KeyEvent event) {
        int caretPosition = codeArea.getCaretPosition();
        int paragraph = codeArea.getCurrentParagraph();
        String currentLine = codeArea.getParagraph(paragraph).getText();

        int columnPosition = caretPosition - codeArea.position(paragraph, 0).toOffset();

        // Check if we're at the start of whitespace-only content
        if (columnPosition > 0 && columnPosition <= currentLine.length()) {
            String beforeCaret = currentLine.substring(0, columnPosition);
            if (beforeCaret.trim().isEmpty() && beforeCaret.length() >= 4) {
                // Remove 4 spaces (one indentation level)
                Platform.runLater(() -> {
                    int deleteLength = Math.min(4, beforeCaret.length());
                    codeArea.deleteText(caretPosition - deleteLength, caretPosition);
                });
                event.consume();
            }
        }

        // Default behavior for other cases
    }

    /**
     * Handle auto-closing of XML tags
     */
    private void handleAutoTagClosing(KeyEvent event) {
        String character = event.getCharacter();

        if (">".equals(character)) {
            int caretPosition = codeArea.getCaretPosition() - 1; // Before the >
            String textBeforeCaret = codeArea.getText(0, caretPosition);

            // Extract the current tag name
            Pattern tagPattern = Pattern.compile("<([a-zA-Z][a-zA-Z0-9-_]*)[^>]*$");
            Matcher matcher = tagPattern.matcher(textBeforeCaret);

            if (matcher.find()) {
                String tagName = matcher.group(1);
                String fullTagContent = matcher.group(0);

                // Don't auto-close if it's a self-closing tag or already has /
                if (!fullTagContent.contains("/") && !isVoidElement(tagName)) {
                    Platform.runLater(() -> {
                        int currentPos = codeArea.getCaretPosition();
                        codeArea.insertText(currentPos, "</" + tagName + ">");
                        codeArea.moveTo(currentPos); // Move back between tags
                    });
                }
            }
        }
    }

    /**
     * Set up enhanced bracket matching and tag-pair highlighting
     */
    private void setupBracketMatching() {
        if (!bracketMatchingEnabled) {
            return;
        }

        // Update bracket matching when caret moves
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            Platform.runLater(() -> {
                updateBracketMatching();
                if (tagPairHighlightingEnabled) {
                    updateTagPairHighlighting();
                }
            });
        });

        // Handle bracket auto-insertion
        codeArea.setOnKeyTyped(event -> {
            if (smartPairEnabled) {
                handleSmartPairing(event.getCharacter());
            }
        });

        logger.debug("Enhanced bracket matching and tag-pair highlighting initialized");
    }

    /**
         * Bracket match result representation
         */
        public record BracketMatchResult(int startPos, int endPos, BracketType type, boolean isMatch, String openBracket,
                                         String closeBracket) {
            public enum BracketType {
                ANGLE("<", ">"), SQUARE("[", "]"), ROUND("(", ")"), CURLY("{", "}"),
                QUOTE("\"", "\""), SINGLE_QUOTE("'", "'"), XML_TAG("<tag>", "</tag>");

                private final String open;
                private final String close;

                BracketType(String open, String close) {
                    this.open = open;
                    this.close = close;
                }

                public String getOpen() {
                    return open;
                }

                public String getClose() {
                    return close;
                }
            }

    }

    /**
         * Tag pair highlight representation
         */
        public record TagPairHighlight(int openStartPos, int openEndPos, int closeStartPos, int closeEndPos, String tagName,
                                       int nestingLevel, String highlightColor) {

        public boolean containsPosition(int position) {
                return (position >= openStartPos && position <= openEndPos) ||
                        (position >= closeStartPos && position <= closeEndPos);
            }
        }

    /**
     * Update bracket matching highlighting
     */
    private void updateBracketMatching() {
        if (!bracketMatchingEnabled) {
            return;
        }

        try {
            int caretPos = codeArea.getCaretPosition();
            String text = codeArea.getText();

            // Clear previous bracket highlighting
            clearBracketHighlighting();

            if (caretPos > 0 && caretPos <= text.length()) {
                // Check character before caret
                BracketMatchResult match = findBracketMatch(text, caretPos - 1);
                if (match != null) {
                    highlightBracketMatch(match);
                    currentBracketMatch = match;
                }

                // Check character at caret (if not at end)
                if (caretPos < text.length()) {
                    BracketMatchResult match2 = findBracketMatch(text, caretPos);
                    if (match2 != null) {
                        highlightBracketMatch(match2);
                        currentBracketMatch = match2;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error updating bracket matching", e);
        }
    }

    /**
     * Find bracket match for position
     */
    private BracketMatchResult findBracketMatch(String text, int position) {
        if (position < 0 || position >= text.length()) {
            return null;
        }

        char currentChar = text.charAt(position);
        BracketMatchResult.BracketType bracketType = getBracketType(currentChar);

        if (bracketType == null) {
            return null;
        }

        // Handle different bracket types
        switch (bracketType) {
            case ANGLE:
                return findAngleBracketMatch(text, position, currentChar);
            case SQUARE:
                return findSquareBracketMatch(text, position, currentChar);
            case ROUND:
                return findRoundBracketMatch(text, position, currentChar);
            case CURLY:
                return findCurlyBracketMatch(text, position, currentChar);
            case QUOTE:
            case SINGLE_QUOTE:
                return findQuoteMatch(text, position, currentChar);
            default:
                return null;
        }
    }

    /**
     * Get bracket type for character
     */
    private BracketMatchResult.BracketType getBracketType(char c) {
        switch (c) {
            case '<':
            case '>':
                return BracketMatchResult.BracketType.ANGLE;
            case '[':
            case ']':
                return BracketMatchResult.BracketType.SQUARE;
            case '(':
            case ')':
                return BracketMatchResult.BracketType.ROUND;
            case '{':
            case '}':
                return BracketMatchResult.BracketType.CURLY;
            case '"':
                return BracketMatchResult.BracketType.QUOTE;
            case '\'':
                return BracketMatchResult.BracketType.SINGLE_QUOTE;
            default:
                return null;
        }
    }

    /**
     * Find matching angle bracket (< >)
     */
    private BracketMatchResult findAngleBracketMatch(String text, int position, char currentChar) {
        if (currentChar == '<') {
            // Find matching >
            int level = 1;
            for (int i = position + 1; i < text.length() && level > 0; i++) {
                char c = text.charAt(i);
                if (c == '<') level++;
                else if (c == '>') level--;

                if (level == 0) {
                    return new BracketMatchResult(position, i, BracketMatchResult.BracketType.ANGLE,
                            true, "<", ">");
                }
            }
            // No match found
            return new BracketMatchResult(position, -1, BracketMatchResult.BracketType.ANGLE,
                    false, "<", ">");
        } else if (currentChar == '>') {
            // Find matching <
            int level = 1;
            for (int i = position - 1; i >= 0 && level > 0; i--) {
                char c = text.charAt(i);
                if (c == '>') level++;
                else if (c == '<') level--;

                if (level == 0) {
                    return new BracketMatchResult(i, position, BracketMatchResult.BracketType.ANGLE,
                            true, "<", ">");
                }
            }
            // No match found
            return new BracketMatchResult(-1, position, BracketMatchResult.BracketType.ANGLE,
                    false, "<", ">");
        }
        return null;
    }

    /**
     * Find matching square bracket ([ ])
     */
    private BracketMatchResult findSquareBracketMatch(String text, int position, char currentChar) {
        return findGenericBracketMatch(text, position, currentChar, '[', ']',
                BracketMatchResult.BracketType.SQUARE);
    }

    /**
     * Find matching round bracket (( ))
     */
    private BracketMatchResult findRoundBracketMatch(String text, int position, char currentChar) {
        return findGenericBracketMatch(text, position, currentChar, '(', ')',
                BracketMatchResult.BracketType.ROUND);
    }

    /**
     * Find matching curly bracket ({ })
     */
    private BracketMatchResult findCurlyBracketMatch(String text, int position, char currentChar) {
        return findGenericBracketMatch(text, position, currentChar, '{', '}',
                BracketMatchResult.BracketType.CURLY);
    }

    /**
     * Generic bracket matching algorithm
     */
    private BracketMatchResult findGenericBracketMatch(String text, int position, char currentChar,
                                                       char openChar, char closeChar,
                                                       BracketMatchResult.BracketType type) {
        if (currentChar == openChar) {
            // Find matching close
            int level = 1;
            for (int i = position + 1; i < text.length() && level > 0; i++) {
                char c = text.charAt(i);
                if (c == openChar) level++;
                else if (c == closeChar) level--;

                if (level == 0) {
                    return new BracketMatchResult(position, i, type, true,
                            String.valueOf(openChar), String.valueOf(closeChar));
                }
            }
            return new BracketMatchResult(position, -1, type, false,
                    String.valueOf(openChar), String.valueOf(closeChar));
        } else if (currentChar == closeChar) {
            // Find matching open
            int level = 1;
            for (int i = position - 1; i >= 0 && level > 0; i--) {
                char c = text.charAt(i);
                if (c == closeChar) level++;
                else if (c == openChar) level--;

                if (level == 0) {
                    return new BracketMatchResult(i, position, type, true,
                            String.valueOf(openChar), String.valueOf(closeChar));
                }
            }
            return new BracketMatchResult(-1, position, type, false,
                    String.valueOf(openChar), String.valueOf(closeChar));
        }
        return null;
    }

    /**
     * Find matching quote
     */
    private BracketMatchResult findQuoteMatch(String text, int position, char quoteChar) {
        // Look backwards first
        boolean inQuote = false;
        for (int i = 0; i < position; i++) {
            if (text.charAt(i) == quoteChar && (i == 0 || text.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            }
        }

        if (inQuote) {
            // We're inside quotes, find opening quote
            for (int i = position - 1; i >= 0; i--) {
                if (text.charAt(i) == quoteChar && (i == 0 || text.charAt(i - 1) != '\\')) {
                    return new BracketMatchResult(i, position,
                            quoteChar == '"' ? BracketMatchResult.BracketType.QUOTE : BracketMatchResult.BracketType.SINGLE_QUOTE,
                            true, String.valueOf(quoteChar), String.valueOf(quoteChar));
                }
            }
        } else {
            // We're at opening quote, find closing quote
            for (int i = position + 1; i < text.length(); i++) {
                if (text.charAt(i) == quoteChar && text.charAt(i - 1) != '\\') {
                    return new BracketMatchResult(position, i,
                            quoteChar == '"' ? BracketMatchResult.BracketType.QUOTE : BracketMatchResult.BracketType.SINGLE_QUOTE,
                            true, String.valueOf(quoteChar), String.valueOf(quoteChar));
                }
            }
        }

        return new BracketMatchResult(position, -1,
                quoteChar == '"' ? BracketMatchResult.BracketType.QUOTE : BracketMatchResult.BracketType.SINGLE_QUOTE,
                false, String.valueOf(quoteChar), String.valueOf(quoteChar));
    }

    /**
     * Update XML tag pair highlighting
     */
    private void updateTagPairHighlighting() {
        if (!tagPairHighlightingEnabled) {
            return;
        }

        try {
            int caretPos = codeArea.getCaretPosition();
            String text = codeArea.getText();

            // Clear previous tag highlights
            clearTagHighlighting();

            // Find tag at caret position
            TagPairHighlight tagPair = findTagPairAtPosition(text, caretPos);
            if (tagPair != null) {
                highlightTagPair(tagPair);
                activeTagHighlights.add(tagPair);
            }

        } catch (Exception e) {
            logger.warn("Error updating tag pair highlighting", e);
        }
    }

    /**
     * Find XML tag pair at position
     */
    private TagPairHighlight findTagPairAtPosition(String text, int position) {
        // Find all XML tags in the document
        List<XmlTag> tags = findAllXmlTags(text);
        if (tags.isEmpty()) {
            return null;
        }

        // Find tag containing the cursor position
        XmlTag currentTag = null;
        for (XmlTag tag : tags) {
            if (position >= tag.startPos && position <= tag.endPos) {
                currentTag = tag;
                break;
            }
        }

        if (currentTag == null) {
            return null;
        }

        // Find matching tag pair
        if (!currentTag.isClosing && !currentTag.isSelfClosing) {
            // Find matching closing tag
            XmlTag matchingTag = findMatchingClosingTag(tags, currentTag);
            if (matchingTag != null) {
                String color = getRainbowColor(currentTag.nestingLevel);
                return new TagPairHighlight(
                        currentTag.startPos, currentTag.endPos,
                        matchingTag.startPos, matchingTag.endPos,
                        currentTag.name, currentTag.nestingLevel, color
                );
            }
        } else if (currentTag.isClosing) {
            // Find matching opening tag
            XmlTag matchingTag = findMatchingOpeningTag(tags, currentTag);
            if (matchingTag != null) {
                String color = getRainbowColor(matchingTag.nestingLevel);
                return new TagPairHighlight(
                        matchingTag.startPos, matchingTag.endPos,
                        currentTag.startPos, currentTag.endPos,
                        currentTag.name, matchingTag.nestingLevel, color
                );
            }
        }

        return null;
    }

    /**
         * XML tag representation
         */
        private record XmlTag(String name, int startPos, int endPos, boolean isClosing, boolean isSelfClosing,
                              int nestingLevel) {
    }

    /**
     * Find all XML tags in text
     */
    private List<XmlTag> findAllXmlTags(String text) {
        List<XmlTag> tags = new ArrayList<>();
        Matcher matcher = XML_TAG_PATTERN.matcher(text);
        Stack<String> tagStack = new Stack<>();

        while (matcher.find()) {
            String fullMatch = matcher.group(0);
            String closingSlash = matcher.group(1);
            String tagName = matcher.group(2);
            String selfClosingSlash = matcher.group(3);

            boolean isClosing = "/".equals(closingSlash);
            boolean isSelfClosing = "/".equals(selfClosingSlash);

            int nestingLevel = tagStack.size();

            XmlTag tag = new XmlTag(tagName, matcher.start(), matcher.end(),
                    isClosing, isSelfClosing, nestingLevel);
            tags.add(tag);

            // Update nesting level tracking
            if (!isClosing && !isSelfClosing) {
                tagStack.push(tagName);
            } else if (isClosing && !tagStack.isEmpty() && tagStack.peek().equals(tagName)) {
                tagStack.pop();
            }
        }

        return tags;
    }

    /**
     * Find matching closing tag
     */
    private XmlTag findMatchingClosingTag(List<XmlTag> tags, XmlTag openingTag) {
        int level = 1;
        for (int i = tags.indexOf(openingTag) + 1; i < tags.size(); i++) {
            XmlTag tag = tags.get(i);
            if (tag.name.equals(openingTag.name)) {
                if (!tag.isClosing && !tag.isSelfClosing) {
                    level++;
                } else if (tag.isClosing) {
                    level--;
                    if (level == 0) {
                        return tag;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Find matching opening tag
     */
    private XmlTag findMatchingOpeningTag(List<XmlTag> tags, XmlTag closingTag) {
        int level = 1;
        for (int i = tags.indexOf(closingTag) - 1; i >= 0; i--) {
            XmlTag tag = tags.get(i);
            if (tag.name.equals(closingTag.name)) {
                if (tag.isClosing) {
                    level++;
                } else if (!tag.isClosing && !tag.isSelfClosing) {
                    level--;
                    if (level == 0) {
                        return tag;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Get rainbow color for nesting level
     */
    private String getRainbowColor(int nestingLevel) {
        if (!bracketRainbowEnabled) {
            return "#007bff"; // Default blue
        }
        return RAINBOW_COLORS[nestingLevel % RAINBOW_COLORS.length];
    }

    /**
     * Highlight bracket match with visual styling
     */
    private void highlightBracketMatch(BracketMatchResult match) {
        if (match.startPos() >= 0) {
            String styleClass = match.isMatch() ? "xml-bracket-match" : "xml-bracket-mismatch";
            // Apply highlighting style (simplified - would need custom StyleSpans implementation)
            logger.debug("Highlighting bracket at position {} with style {}", match.startPos(), styleClass);
        }

        if (match.endPos() >= 0) {
            String styleClass = match.isMatch() ? "xml-bracket-match" : "xml-bracket-mismatch";
            logger.debug("Highlighting bracket at position {} with style {}", match.endPos(), styleClass);
        }
    }

    /**
     * Highlight XML tag pair
     */
    private void highlightTagPair(TagPairHighlight tagPair) {
        // Apply visual highlighting to both opening and closing tags
        logger.debug("Highlighting tag pair: {} at positions {}-{} and {}-{} with color {}",
                tagPair.tagName(),
                tagPair.openStartPos(), tagPair.openEndPos(),
                tagPair.closeStartPos(), tagPair.closeEndPos(),
                tagPair.highlightColor());

        // In a full implementation, this would apply actual visual styling
        Platform.runLater(() -> {
            codeArea.getStyleClass().add("xml-tag-pair-highlighted");
        });
    }

    /**
     * Clear bracket highlighting
     */
    private void clearBracketHighlighting() {
        currentBracketMatch = null;
        // Clear visual highlighting (simplified)
    }

    /**
     * Clear tag highlighting
     */
    private void clearTagHighlighting() {
        activeTagHighlights.clear();
        Platform.runLater(() -> {
            codeArea.getStyleClass().remove("xml-tag-pair-highlighted");
        });
    }

    /**
     * Initialize enhanced IntelliSense system with snippet support
     */
    private void initializeIntelliSense() {
        xmlIntelliSense = new XmlIntelliSense();

        // Set up auto-completion popup
        autoCompletionPopup = new ContextMenu();

        codeArea.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE && event.isControlDown()) {
                // Ctrl+Space: Force show completion
                showEnhancedAutoCompletion();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                // Escape: Hide completion
                hideAutoCompletion();
                event.consume();
            } else if (event.getCode() == KeyCode.TAB && autoCompletionPopup.isShowing()) {
                // Tab: Accept first completion suggestion
                acceptFirstCompletion();
                event.consume();
            } else if (event.getCode() == KeyCode.J && event.isControlDown()) {
                // Ctrl+J: Show snippet completion
                showSnippetCompletion();
                event.consume();
            }
        });

        codeArea.setOnKeyTyped(event -> {
            String character = event.getCharacter();
            if ("<".equals(character)) {
                // Auto-trigger completion for <
                Platform.runLater(this::maybeShowEnhancedAutoCompletion);
            } else if (" ".equals(character) && isInsideXmlTag()) {
                // Auto-trigger attribute completion inside tags
                Platform.runLater(this::maybeShowAttributeCompletion);
            } else if (character.matches("[a-zA-Z]") && shouldTriggerIncrementalCompletion()) {
                // Incremental completion while typing
                Platform.runLater(this::maybeShowIncrementalCompletion);
            }
        });

        logger.debug("Enhanced IntelliSense system with snippet support initialized");
    }

    /**
     * Initialize XML snippet support
     */
    private void initializeSnippetSupport() {
        snippetManager = new XmlSnippetManager();

        // Load built-in XML snippets
        loadBuiltInXmlSnippets();

        logger.debug("XML snippet support initialized with {} snippets", xmlSnippets.size());
    }

    /**
     * XML Snippet Manager for handling code snippets
     */
    public static class XmlSnippetManager {
        private final Map<String, XmlSnippet> snippets = new HashMap<>();

        public static class XmlSnippet {
            private final String trigger;
            private final String template;
            private final String description;
            private final String category;
            private final List<String> placeholders;

            public XmlSnippet(String trigger, String template, String description, String category) {
                this.trigger = trigger;
                this.template = template;
                this.description = description;
                this.category = category;
                this.placeholders = extractPlaceholders(template);
            }

            private List<String> extractPlaceholders(String template) {
                List<String> placeholders = new ArrayList<>();
                Pattern pattern = Pattern.compile("\\$\\{([^}]+)\\}");
                Matcher matcher = pattern.matcher(template);

                while (matcher.find()) {
                    String placeholder = matcher.group(1);
                    if (!placeholders.contains(placeholder)) {
                        placeholders.add(placeholder);
                    }
                }

                return placeholders;
            }

            public String expandTemplate(Map<String, String> values) {
                String expanded = template;
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    expanded = expanded.replace("${" + entry.getKey() + "}", entry.getValue());
                }

                // Remove any remaining placeholders
                expanded = expanded.replaceAll("\\$\\{[^}]+\\}", "");

                return expanded;
            }

            // Getters
            public String getTrigger() {
                return trigger;
            }

            public String getTemplate() {
                return template;
            }

            public String getDescription() {
                return description;
            }

            public String getCategory() {
                return category;
            }

            public List<String> getPlaceholders() {
                return placeholders;
            }
        }

        public void addSnippet(XmlSnippet snippet) {
            snippets.put(snippet.getTrigger(), snippet);
        }

        public XmlSnippet getSnippet(String trigger) {
            return snippets.get(trigger);
        }

        public List<XmlSnippet> getSnippetsByCategory(String category) {
            return snippets.values().stream()
                    .filter(s -> s.getCategory().equals(category))
                    .collect(java.util.stream.Collectors.toList());
        }

        public List<XmlSnippet> getAllSnippets() {
            return new ArrayList<>(snippets.values());
        }

        public List<XmlSnippet> searchSnippets(String query) {
            return snippets.values().stream()
                    .filter(s -> s.getTrigger().contains(query) ||
                            s.getDescription().toLowerCase().contains(query.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    /**
     * Load built-in XML snippets
     */
    private void loadBuiltInXmlSnippets() {
        // Basic XML structures
        snippetManager.addSnippet(new XmlSnippetManager.XmlSnippet(
                "xml",
                "<?xml version=\"1.0\" encoding=\"${encoding:UTF-8}\"?>\n<${root:root}>\n    ${content}\n</${root}>",
                "Basic XML document structure",
                "basic"
        ));

        snippetManager.addSnippet(new XmlSnippetManager.XmlSnippet(
                "elem",
                "<${name}>\n    ${content}\n</${name}>",
                "XML element with content",
                "basic"
        ));

        snippetManager.addSnippet(new XmlSnippetManager.XmlSnippet(
                "selfelem",
                "<${name} ${attributes}/>",
                "Self-closing XML element",
                "basic"
        ));

        snippetManager.addSnippet(new XmlSnippetManager.XmlSnippet(
                "attr",
                "${name}=\"${value}\"",
                "XML attribute",
                "basic"
        ));

        snippetManager.addSnippet(new XmlSnippetManager.XmlSnippet(
                "comment",
                "<!-- ${comment} -->",
                "XML comment",
                "basic"
        ));

        snippetManager.addSnippet(new XmlSnippetManager.XmlSnippet(
                "cdata",
                "<![CDATA[${content}]]>",
                "CDATA section",
                "basic"
        ));

        // Common XML patterns
        snippetManager.addSnippet(new XmlSnippetManager.XmlSnippet(
                "list",
                "<${listName}>\n    <${itemName}>${item1}</${itemName}>\n    <${itemName}>${item2}</${itemName}>\n    <${itemName}>${item3}</${itemName}>\n</${listName}>",
                "XML list structure",
                "patterns"
        ));

        snippetManager.addSnippet(new XmlSnippetManager.XmlSnippet(
                "table",
                "<table>\n    <row>\n        <cell>${header1}</cell>\n        <cell>${header2}</cell>\n    </row>\n    <row>\n        <cell>${data1}</cell>\n        <cell>${data2}</cell>\n    </row>\n</table>",
                "XML table structure",
                "patterns"
        ));

        // Schema-related snippets
        snippetManager.addSnippet(new XmlSnippetManager.XmlSnippet(
                "xsd",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n           targetNamespace=\"${namespace}\"\n           xmlns=\"${namespace}\"\n           elementFormDefault=\"qualified\">\n    \n    <xs:element name=\"${rootElement}\" type=\"${rootType}\"/>\n    \n</xs:schema>",
                "Basic XSD schema structure",
                "schema"
        ));

        // XSLT snippets
        snippetManager.addSnippet(new XmlSnippetManager.XmlSnippet(
                "xslt",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n    \n    <xsl:template match=\"/\">\n        ${content}\n    </xsl:template>\n    \n</xsl:stylesheet>",
                "Basic XSLT stylesheet",
                "xslt"
        ));

        snippetManager.addSnippet(new XmlSnippetManager.XmlSnippet(
                "xsltemplate",
                "<xsl:template match=\"${match}\">\n    ${content}\n</xsl:template>",
                "XSLT template",
                "xslt"
        ));

        // Web-related XML snippets
        snippetManager.addSnippet(new XmlSnippetManager.XmlSnippet(
                "rss",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<rss version=\"2.0\">\n    <channel>\n        <title>${title}</title>\n        <description>${description}</description>\n        <link>${link}</link>\n        <item>\n            <title>${itemTitle}</title>\n            <description>${itemDescription}</description>\n            <link>${itemLink}</link>\n        </item>\n    </channel>\n</rss>",
                "RSS feed structure",
                "web"
        ));

        snippetManager.addSnippet(new XmlSnippetManager.XmlSnippet(
                "sitemap",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n    <url>\n        <loc>${url}</loc>\n        <lastmod>${date}</lastmod>\n        <changefreq>${frequency}</changefreq>\n        <priority>${priority}</priority>\n    </url>\n</urlset>",
                "XML sitemap structure",
                "web"
        ));

        logger.debug("Loaded {} built-in XML snippets", snippetManager.getAllSnippets().size());
    }

    /**
     * Initialize Search & Replace functionality
     */
    private void initializeSearchReplace() {
        if (!searchReplaceEnabled) {
            return;
        }

        searchReplacePanel = new XmlSearchReplacePanel();
        searchReplacePanel.setTargetCodeArea(codeArea);

        logger.debug("Advanced Search & Replace initialized");
    }

    /**
     * Initialize Modern Syntax Highlighting System
     */
    private void initializeAdvancedHighlighting() {
        if (!advancedHighlightingEnabled) {
            return;
        }

        // Initialize theme manager and advanced highlighter
        themeManager = ModernXmlThemeManager.getInstance();
        advancedHighlighter = new AdvancedXmlSyntaxHighlighter();

        // Listen for theme changes and update code area accordingly
        themeManager.addThemeChangeListener((oldTheme, newTheme) -> {
            Platform.runLater(() -> {
                applyThemeToCodeArea(newTheme);
                // Refresh highlighting
                if (!codeArea.getText().isEmpty()) {
                    refreshHighlighting();
                }
            });
        });

        // Apply current theme
        applyThemeToCodeArea(themeManager.getCurrentTheme());

        logger.debug("Modern XML Syntax Highlighting initialized with theme: {}",
                themeManager.getCurrentTheme().getDisplayName());
    }

    /**
     * Initialize XML Validation functionality
     */
    private void initializeValidation() {
        if (!validationEnabled) {
            return;
        }

        validationPanel = new XmlValidationPanel();

        // Setup validation callbacks
        validationPanel.setOnNavigateToLine(lineNumber -> {
            if (lineNumber > 0) {
                Platform.runLater(() -> {
                    try {
                        // Navigate to the specified line
                        int position = codeArea.getAbsolutePosition(lineNumber - 1, 0);
                        codeArea.moveTo(position);
                        codeArea.requestFollowCaret();
                        codeArea.requestFocus();

                        logger.debug("Navigated to line: {}", lineNumber);
                    } catch (Exception e) {
                        logger.warn("Failed to navigate to line {}", lineNumber, e);
                    }
                });
            }
        });

        validationPanel.setOnErrorSelected(error -> {
            // Highlight error location in editor if available
            if (error.hasLocation() && showValidationErrors) {
                highlightErrorLocation(error);
            }
        });

        validationPanel.setOnValidationCompleted(result -> {
            // Update syntax highlighting with validation errors if available
            if (result != null && showValidationErrors) {
                updateValidationHighlighting(result);
            }
        });

        // Listen for text changes and trigger validation if real-time is enabled
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (validationPanel != null && newText != null) {
                validationPanel.setXmlContent(newText);
            }
        });

        logger.debug("XML Validation initialized with real-time validation");
    }

    /**
     * Set up keyboard shortcuts
     */
    private void setupKeyboardShortcuts() {
        // Ctrl+D: Duplicate line
        codeArea.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.D) {
                duplicateCurrentLine();
                event.consume();
            } else if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.K) {
                deleteCurrentLine();
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.SLASH) {
                toggleXmlComments();
                event.consume();
            } else if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.F) {
                formatDocument();
                event.consume();
            } else if (event.isAltDown() && event.getCode() == KeyCode.UP) {
                moveLineUp();
                event.consume();
            } else if (event.isAltDown() && event.getCode() == KeyCode.DOWN) {
                moveLineDown();
                event.consume();
            } else if (event.isControlDown() && event.isAltDown() && event.getCode() == KeyCode.DOWN) {
                // Ctrl+Alt+Down: Add cursor below (multi-cursor)
                addCursorBelow();
                event.consume();
            } else if (event.isControlDown() && event.isAltDown() && event.getCode() == KeyCode.UP) {
                // Ctrl+Alt+Up: Add cursor above (multi-cursor)
                addCursorAbove();
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.U) {
                // Ctrl+U: Undo last cursor (multi-cursor)
                undoLastCursor();
                event.consume();
            } else if (event.isAltDown() && event.isShiftDown() && event.getCode() == KeyCode.I) {
                // Alt+Shift+I: Toggle block selection mode
                toggleBlockSelectionMode();
                event.consume();
            }
        });
    }

    /**
     * Set up enhanced context menu
     */
    private void setupContextMenu() {
        ContextMenu contextMenu = ContextMenuFactory.builder()
            .addCopyItem("Copy", () -> codeArea.copy())
            .addItem("Cut", "bi-scissors", ContextMenuFactory.COLOR_WARNING, () -> codeArea.cut())
            .addItem("Paste", "bi-clipboard", ContextMenuFactory.COLOR_INFO, () -> codeArea.paste())
            .addSeparator()
            .addItem("Format XML", "bi-code-square", ContextMenuFactory.COLOR_PRIMARY, this::formatDocument)
            .addItem("Validate XML", "bi-check-circle", ContextMenuFactory.COLOR_SUCCESS, this::validateXml)
            .addSeparator()
            .addItem("Extract Selection to File", "bi-file-earmark-arrow-down", ContextMenuFactory.COLOR_SECONDARY, this::extractSelectionToFile)
            .addItem("Generate XSD Schema", "bi-diagram-3", ContextMenuFactory.COLOR_PURPLE, this::generateSchema)
            .build();

        codeArea.setContextMenu(contextMenu);
    }

    // ========== Public API Methods ==========

    /**
     * Set text with performance optimization for large files
     */
    public void setText(String text) {
        if (text == null) {
            text = "";
        }

        // Check if this is a large file
        if (text.length() > LARGE_FILE_THRESHOLD) {
            isLargeFile = true;
            logger.info("Loading large XML file ({} chars) - performance mode enabled", text.length());

            // Use progress manager for large files
            final String finalText = text;
            progressManager.executeWithProgress(
                    "Loading XML content",
                    () -> {
                        Platform.runLater(() -> {
                            // Temporarily disable syntax highlighting for very large files
                            boolean originalHighlightingState = syntaxHighlightingEnabled;
                            if (finalText.length() > LARGE_FILE_THRESHOLD * 5) { // 500KB+
                                syntaxHighlightingEnabled = false;
                                logger.info("Syntax highlighting temporarily disabled for very large file");
                            }

                            codeArea.clear();
                            codeArea.insertText(0, finalText);

                            // Re-enable highlighting if it was disabled
                            if (!syntaxHighlightingEnabled && originalHighlightingState) {
                                syntaxHighlightingEnabled = originalHighlightingState;
                                setupAdvancedSyntaxHighlighting();
                            }
                        });
                        return null;
                    },
                    result -> logger.debug("Large XML file loaded successfully"),
                    error -> {
                        logger.error("Failed to load large XML file", error);
                        // Fallback to direct loading
                        Platform.runLater(() -> {
                            codeArea.clear();
                            codeArea.insertText(0, finalText);
                        });
                    }
            );
        } else {
            isLargeFile = false;
            codeArea.clear();
            codeArea.insertText(0, text);
        }
    }

    public String getText() {
        return codeArea.getText();
    }

    public CodeArea getCodeArea() {
        return codeArea;
    }

    // ========== Advanced Features Implementation ==========

    /**
     * Show enhanced auto-completion with snippet support
     */
    private void showEnhancedAutoCompletion() {
        if (!autoCompletionEnabled) {
            return;
        }

        int caretPosition = codeArea.getCaretPosition();
        String textBeforeCaret = codeArea.getText(0, caretPosition);

        List<CompletionItem> allCompletions = new ArrayList<>();

        // Get IntelliSense completions
        if (xmlIntelliSense != null) {
            var intelliSenseCompletions = xmlIntelliSense.getCompletions(textBeforeCaret, caretPosition);
            for (var completion : intelliSenseCompletions) {
                allCompletions.add(new CompletionItem(
                        completion.text(),
                        completion.insertText(),
                        "IntelliSense",
                        completion.text(),
                        CompletionItem.CompletionType.INTELLISENSE
                ));
            }
        }

        // Get snippet completions if enabled
        if (snippetSupportEnabled) {
            String currentWord = getCurrentWord(textBeforeCaret);
            if (!currentWord.isEmpty()) {
                List<XmlSnippetManager.XmlSnippet> matchingSnippets = snippetManager.searchSnippets(currentWord);
                for (XmlSnippetManager.XmlSnippet snippet : matchingSnippets) {
                    allCompletions.add(new CompletionItem(
                            snippet.getTrigger() + " (snippet)",
                            snippet.getTemplate(),
                            snippet.getCategory(),
                            snippet.getDescription(),
                            CompletionItem.CompletionType.SNIPPET
                    ));
                }
            }
        }

        // Get context-aware completions
        if (contextAwareCompletionEnabled) {
            List<CompletionItem> contextCompletions = getContextAwareCompletions(textBeforeCaret, caretPosition);
            allCompletions.addAll(contextCompletions);
        }

        displayCompletionPopup(allCompletions);
    }

    /**
         * Enhanced completion item
         */
        public record CompletionItem(String displayText, String insertText, String category, String description,
                                     CompletionType type) {
            public enum CompletionType {
                INTELLISENSE, SNIPPET, CONTEXT, ATTRIBUTE
            }

    }

    private void hideAutoCompletion() {
        if (autoCompletionPopup != null && autoCompletionPopup.isShowing()) {
            autoCompletionPopup.hide();
        }
    }

    /**
     * Show enhanced auto-completion with context awareness
     */
    private void maybeShowEnhancedAutoCompletion() {
        if (shouldShowCompletion()) {
            showEnhancedAutoCompletion();
        }
    }

    /**
     * Show attribute-specific completion
     */
    private void maybeShowAttributeCompletion() {
        if (isInsideXmlTag() && shouldShowAttributeCompletion()) {
            showAttributeCompletion();
        }
    }

    /**
     * Show incremental completion while typing
     */
    private void maybeShowIncrementalCompletion() {
        if (shouldShowIncrementalCompletion()) {
            showEnhancedAutoCompletion();
        }
    }

    /**
     * Show snippet-only completion
     */
    private void showSnippetCompletion() {
        if (!snippetSupportEnabled) {
            return;
        }

        List<CompletionItem> snippetCompletions = new ArrayList<>();

        for (XmlSnippetManager.XmlSnippet snippet : snippetManager.getAllSnippets()) {
            snippetCompletions.add(new CompletionItem(
                    snippet.getTrigger(),
                    snippet.getTemplate(),
                    snippet.getCategory(),
                    snippet.getDescription(),
                    CompletionItem.CompletionType.SNIPPET
            ));
        }

        displayCompletionPopup(snippetCompletions);
        logger.debug("Showed {} snippet completions", snippetCompletions.size());
    }

    /**
     * Show attribute completion for current XML tag
     */
    private void showAttributeCompletion() {
        int caretPos = codeArea.getCaretPosition();
        String text = codeArea.getText(0, caretPos);

        // Find current XML tag
        String currentTag = getCurrentXmlTagName(text);
        if (currentTag == null) {
            return;
        }

        List<CompletionItem> attributeCompletions = getAttributeCompletions(currentTag);
        if (!attributeCompletions.isEmpty()) {
            displayCompletionPopup(attributeCompletions);
            logger.debug("Showed {} attribute completions for tag {}", attributeCompletions.size(), currentTag);
        }
    }

    /**
     * Enhanced logic to determine if completion should be shown
     */
    private boolean shouldShowCompletion() {
        int caretPosition = codeArea.getCaretPosition();
        if (caretPosition > 0) {
            String textBeforeCaret = codeArea.getText(Math.max(0, caretPosition - 10), caretPosition);
            String charBefore = codeArea.getText(caretPosition - 1, caretPosition);

            // Show completion for XML tag start
            if ("<".equals(charBefore)) {
                return true;
            }

            // Show completion when typing inside tag (for attributes)
            if (" ".equals(charBefore) && isInsideXmlTag()) {
                return true;
            }

            // Show completion when typing element names or attribute values
            return charBefore.matches("[a-zA-Z]") && (isTypingElementName() || isTypingAttributeValue());
        }
        return false;
    }

    private boolean shouldShowAttributeCompletion() {
        return isInsideXmlTag() && !isInAttributeValue();
    }

    private boolean shouldTriggerIncrementalCompletion() {
        int caretPosition = codeArea.getCaretPosition();
        if (caretPosition < 3) {
            return false;
        }

        String recentText = codeArea.getText(caretPosition - 3, caretPosition);
        return recentText.matches("[a-zA-Z]{3}"); // Trigger after 3 characters
    }

    private boolean shouldShowIncrementalCompletion() {
        return getCurrentWord(codeArea.getText(0, codeArea.getCaretPosition())).length() >= 2;
    }

    /**
     * Insert completion item with enhanced features
     */
    private void insertCompletion(CompletionItem completion) {
        int caretPosition = codeArea.getCaretPosition();

        if (completion.type() == CompletionItem.CompletionType.SNIPPET) {
            insertSnippet(completion, caretPosition);
        } else {
            // Regular completion insertion
            String textToInsert = completion.insertText();

            // Handle word replacement
            String textBeforeCaret = codeArea.getText(0, caretPosition);
            String currentWord = getCurrentWord(textBeforeCaret);

            if (!currentWord.isEmpty()) {
                // Replace current word
                int wordStart = caretPosition - currentWord.length();
                codeArea.replaceText(wordStart, caretPosition, textToInsert);
            } else {
                // Simple insertion
                codeArea.insertText(caretPosition, textToInsert);
            }
        }

        hideAutoCompletion();
        logger.debug("Inserted {} completion: {}", completion.type(), completion.displayText());
    }

    /**
     * Insert snippet with placeholder support
     */
    private void insertSnippet(CompletionItem completion, int position) {
        String snippetTemplate = completion.insertText();

        // For now, insert template as-is (full placeholder support would require more complex implementation)
        String textToInsert = snippetTemplate;

        // Replace common placeholders with defaults
        textToInsert = textToInsert.replace("${encoding:UTF-8}", "UTF-8");
        textToInsert = textToInsert.replace("${root:root}", "root");
        textToInsert = textToInsert.replace("${content}", "");
        textToInsert = textToInsert.replace("${name}", "element");
        textToInsert = textToInsert.replace("${value}", "value");

        // Remove any remaining placeholders for basic implementation
        textToInsert = textToInsert.replaceAll("\\$\\{[^}]+\\}", "");

        codeArea.insertText(position, textToInsert);

        // Position cursor at first meaningful position
        int newCaretPos = position + findFirstMeaningfulPosition(textToInsert);
        codeArea.moveTo(Math.min(newCaretPos, codeArea.getLength()));
    }

    /**
     * Find first meaningful cursor position in inserted text
     */
    private int findFirstMeaningfulPosition(String text) {
        // Look for first position that's not whitespace or structural characters
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != ' ' && c != '\t' && c != '\n' && c != '<' && c != '>' && c != '"') {
                return i;
            }
        }
        return text.length();
    }

    /**
     * Accept first completion suggestion
     */
    private void acceptFirstCompletion() {
        if (autoCompletionPopup != null && autoCompletionPopup.isShowing() && !autoCompletionPopup.getItems().isEmpty()) {
            MenuItem firstItem = autoCompletionPopup.getItems().get(0);
            firstItem.fire();
        }
    }

    // ========== Helper Methods ==========

    private String extractIndentation(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }

    private String calculateSmartIndentation(String currentLine, String previousLine, String baseIndentation) {
        // Smart indentation logic based on XML context
        if (previousLine.trim().endsWith(">") && !previousLine.trim().endsWith("/>") &&
                !previousLine.trim().startsWith("</")) {
            return baseIndentation + "    "; // Increase indentation after opening tag
        }
        return baseIndentation;
    }

    private boolean isInsideXmlTag(String line, int position) {
        // Check if cursor is inside an XML tag
        int lastOpenBracket = line.lastIndexOf('<', position - 1);
        int lastCloseBracket = line.lastIndexOf('>', position - 1);
        return lastOpenBracket > lastCloseBracket;
    }

    private boolean isAfterOpeningTag(String line) {
        String trimmed = line.trim();
        return trimmed.endsWith(">") && !trimmed.endsWith("/>") && !trimmed.startsWith("</");
    }

    private boolean isBeforeClosingTag(String line, int position) {
        // Check if we need to insert closing tag
        return false; // Simplified for now
    }

    private String extractTagNameForClosing(String currentLine, String previousLine) {
        // Extract tag name for auto-closing
        Pattern pattern = Pattern.compile("<([a-zA-Z][a-zA-Z0-9-_]*)");
        Matcher matcher = pattern.matcher(previousLine);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private boolean isVoidElement(String tagName) {
        // Common XML void elements (HTML-style)
        String[] voidElements = {"area", "base", "br", "col", "embed", "hr", "img",
                "input", "link", "meta", "param", "source", "track", "wbr"};
        for (String voidElement : voidElements) {
            if (voidElement.equalsIgnoreCase(tagName)) {
                return true;
            }
        }
        return false;
    }

    // Advanced editing operations
    private void duplicateCurrentLine() {
        int paragraph = codeArea.getCurrentParagraph();
        String currentLine = codeArea.getParagraph(paragraph).getText();
        int endOfLine = codeArea.position(paragraph, currentLine.length()).toOffset();
        codeArea.insertText(endOfLine, "\n" + currentLine);
    }

    private void deleteCurrentLine() {
        int paragraph = codeArea.getCurrentParagraph();
        String currentLine = codeArea.getParagraph(paragraph).getText();
        int startOfLine = codeArea.position(paragraph, 0).toOffset();
        int endOfLine = codeArea.position(paragraph, currentLine.length()).toOffset();

        // Include the newline character if it exists
        if (endOfLine < codeArea.getLength()) {
            endOfLine++;
        }

        codeArea.deleteText(startOfLine, endOfLine);
    }

    private void toggleXmlComments() {
        // Toggle XML comments for selected text or current line
        String selectedText = codeArea.getSelectedText();
        if (selectedText.isEmpty()) {
            // Comment/uncomment current line
            int paragraph = codeArea.getCurrentParagraph();
            String currentLine = codeArea.getParagraph(paragraph).getText();

            if (currentLine.trim().startsWith("<!--") && currentLine.trim().endsWith("-->")) {
                // Uncomment
                String uncommented = currentLine.replace("<!--", "").replace("-->", "").trim();
                codeArea.replaceText(paragraph, 0, paragraph, currentLine.length(), uncommented);
            } else {
                // Comment
                String commented = "<!-- " + currentLine.trim() + " -->";
                codeArea.replaceText(paragraph, 0, paragraph, currentLine.length(), commented);
            }
        } else {
            // Comment/uncomment selection
            if (selectedText.startsWith("<!--") && selectedText.endsWith("-->")) {
                // Uncomment
                String uncommented = selectedText.substring(4, selectedText.length() - 3).trim();
                codeArea.replaceSelection(uncommented);
            } else {
                // Comment
                String commented = "<!-- " + selectedText + " -->";
                codeArea.replaceSelection(commented);
            }
        }
    }

    private void formatDocument() {
        // TODO: Implement XML formatting
        logger.info("XML formatting requested - to be implemented");
    }

    private void validateXml() {
        // TODO: Implement XML validation
        logger.info("XML validation requested - to be implemented");
    }

    private void extractSelectionToFile() {
        // TODO: Implement extract to file
        logger.info("Extract to file requested - to be implemented");
    }

    private void generateSchema() {
        // TODO: Implement schema generation
        logger.info("Schema generation requested - to be implemented");
    }

    private void moveLineUp() {
        // TODO: Implement move line up
        logger.debug("Move line up requested");
    }

    private void moveLineDown() {
        // TODO: Implement move line down
        logger.debug("Move line down requested");
    }

    private void increaseIndentation() {
        String selectedText = codeArea.getSelectedText();
        if (selectedText.isEmpty()) {
            // Add indentation at cursor
            codeArea.insertText(codeArea.getCaretPosition(), "    ");
        } else {
            // Indent selected lines
            String[] lines = selectedText.split("\n");
            StringBuilder indentedText = new StringBuilder();
            for (String line : lines) {
                indentedText.append("    ").append(line).append("\n");
            }
            codeArea.replaceSelection(indentedText.toString().replaceAll("\n$", ""));
        }
    }

    private void decreaseIndentation() {
        String selectedText = codeArea.getSelectedText();
        if (selectedText.isEmpty()) {
            // Remove indentation at cursor if possible
            int caretPos = codeArea.getCaretPosition();
            int paragraph = codeArea.getCurrentParagraph();
            String currentLine = codeArea.getParagraph(paragraph).getText();
            int columnPos = caretPos - codeArea.position(paragraph, 0).toOffset();

            if (columnPos >= 4 && currentLine.startsWith("    ", columnPos - 4)) {
                codeArea.deleteText(caretPos - 4, caretPos);
            }
        } else {
            // Unindent selected lines
            String[] lines = selectedText.split("\n");
            StringBuilder unindentedText = new StringBuilder();
            for (String line : lines) {
                if (line.startsWith("    ")) {
                    unindentedText.append(line.substring(4)).append("\n");
                } else {
                    unindentedText.append(line).append("\n");
                }
            }
            codeArea.replaceSelection(unindentedText.toString().replaceAll("\n$", ""));
        }
    }

    // ========== Enhanced Multi-Cursor Support ==========

    /**
     * Add cursor below the current line
     */
    private void addCursorBelow() {
        int currentParagraph = codeArea.getCurrentParagraph();
        if (currentParagraph < codeArea.getParagraphs().size() - 1) {
            int currentColumn = codeArea.getCaretColumn();
            int nextParagraph = currentParagraph + 1;
            String nextLine = codeArea.getParagraph(nextParagraph).getText();

            // Position cursor at same column or end of line if shorter
            int targetColumn = Math.min(currentColumn, nextLine.length());
            int targetPosition = codeArea.position(nextParagraph, targetColumn).toOffset();

            addMultiCursor(targetPosition);
            logger.debug("Added cursor below at paragraph {} column {}", nextParagraph, targetColumn);
        }
    }

    /**
     * Add cursor above the current line
     */
    private void addCursorAbove() {
        int currentParagraph = codeArea.getCurrentParagraph();
        if (currentParagraph > 0) {
            int currentColumn = codeArea.getCaretColumn();
            int prevParagraph = currentParagraph - 1;
            String prevLine = codeArea.getParagraph(prevParagraph).getText();

            // Position cursor at same column or end of line if shorter
            int targetColumn = Math.min(currentColumn, prevLine.length());
            int targetPosition = codeArea.position(prevParagraph, targetColumn).toOffset();

            addMultiCursor(targetPosition);
            logger.debug("Added cursor above at paragraph {} column {}", prevParagraph, targetColumn);
        }
    }

    /**
     * Add a multi-cursor at the specified position
     */
    private void addMultiCursor(int position) {
        if (!multiCursorPositions.contains(position)) {
            multiCursorPositions.add(position);
            multiCursorMode = true;

            // Visual indicator (simplified - would need custom styling in real implementation)
            Platform.runLater(() -> {
                updateMultiCursorVisuals();
            });
        }
    }

    /**
     * Remove the last added cursor
     */
    private void undoLastCursor() {
        if (!multiCursorPositions.isEmpty()) {
            multiCursorPositions.remove(multiCursorPositions.size() - 1);
            if (multiCursorPositions.isEmpty()) {
                multiCursorMode = false;
            }
            updateMultiCursorVisuals();
            logger.debug("Removed last cursor, {} cursors remaining", multiCursorPositions.size());
        }
    }

    /**
     * Toggle block selection mode
     */
    private void toggleBlockSelectionMode() {
        blockSelectionMode = !blockSelectionMode;
        logger.debug("Block selection mode: {}", blockSelectionMode);

        // Visual feedback
        if (blockSelectionMode) {
            codeArea.getStyleClass().add("block-selection-mode");
        } else {
            codeArea.getStyleClass().remove("block-selection-mode");
        }
    }

    /**
     * Update visual indicators for multi-cursors
     */
    private void updateMultiCursorVisuals() {
        // In a full implementation, this would add visual cursor indicators
        // For now, we log the cursor positions
        if (multiCursorMode && !multiCursorPositions.isEmpty()) {
            logger.debug("Multi-cursor mode active with {} cursors at positions: {}",
                    multiCursorPositions.size(), multiCursorPositions);
        }
    }

    /**
     * Clear all multi-cursors
     */
    public void clearMultiCursors() {
        multiCursorPositions.clear();
        multiCursorMode = false;
        updateMultiCursorVisuals();
        logger.debug("Cleared all multi-cursors");
    }

    /**
     * Get current multi-cursor positions
     */
    public List<Integer> getMultiCursorPositions() {
        return new ArrayList<>(multiCursorPositions);
    }

    /**
     * Check if multi-cursor mode is active
     */
    public boolean isMultiCursorMode() {
        return multiCursorMode;
    }

    // ========== Intelligent Folding System ==========

    /**
     * XML Folding Region representation
     */
    public static class XmlFoldingRegion {
        private final int startLine;
        private final int endLine;
        private final int startOffset;
        private final int endOffset;
        private final String tagName;
        private final FoldingType type;
        private boolean folded;
        private String foldedText;

        public enum FoldingType {
            XML_ELEMENT, XML_COMMENT, XML_CDATA, CUSTOM
        }

        public XmlFoldingRegion(int startLine, int endLine, int startOffset, int endOffset,
                                String tagName, FoldingType type) {
            this.startLine = startLine;
            this.endLine = endLine;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.tagName = tagName;
            this.type = type;
            this.folded = false;
            this.foldedText = generateFoldedText();
        }

        private String generateFoldedText() {
            switch (type) {
                case XML_ELEMENT:
                    return "<" + tagName + ">...</" + tagName + ">";
                case XML_COMMENT:
                    return "<!-- ... -->";
                case XML_CDATA:
                    return "<![CDATA[...]]>";
                default:
                    return "...";
            }
        }

        // Getters
        public int getStartLine() {
            return startLine;
        }

        public int getEndLine() {
            return endLine;
        }

        public int getStartOffset() {
            return startOffset;
        }

        public int getEndOffset() {
            return endOffset;
        }

        public String getTagName() {
            return tagName;
        }

        public FoldingType getType() {
            return type;
        }

        public boolean isFolded() {
            return folded;
        }

        public String getFoldedText() {
            return foldedText;
        }

        public void setFolded(boolean folded) {
            this.folded = folded;
        }

        public void setFoldedText(String foldedText) {
            this.foldedText = foldedText;
        }
    }

    /**
     * Set up intelligent folding system for XML elements
     */
    private void setupIntelligentFolding() {
        if (!foldingEnabled) {
            return;
        }

        // Update folding regions when text changes
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (newText != null && !newText.equals(oldText)) {
                // Debounce folding updates for performance
                Platform.runLater(() -> updateFoldingRegions(newText));
            }
        });

        // Handle folding keyboard shortcuts
        codeArea.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.MINUS) {
                // Ctrl+- : Fold current region
                foldCurrentRegion();
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.PLUS) {
                // Ctrl++ : Unfold current region
                unfoldCurrentRegion();
                event.consume();
            } else if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.MINUS) {
                // Ctrl+Shift+- : Fold all regions
                foldAllRegions();
                event.consume();
            } else if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.PLUS) {
                // Ctrl+Shift++ : Unfold all regions
                unfoldAllRegions();
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.K && event.isShiftDown()) {
                // Ctrl+K Shift : Toggle folding at cursor
                toggleFoldingAtCursor();
                event.consume();
            }
        });

        logger.debug("Intelligent XML folding system initialized");
    }

    /**
     * Update folding regions based on current text content
     */
    private void updateFoldingRegions(String text) {
        if (!foldingEnabled || text == null || text.isEmpty()) {
            return;
        }

        // Clear existing regions
        foldingRegions.clear();
        activeFolds.clear();

        try {
            // Find XML elements
            findXmlElementFoldingRegions(text);

            // Find comments
            findCommentFoldingRegions(text);

            // Find CDATA sections
            findCDataFoldingRegions(text);

            // Update folding indicators in UI
            updateFoldingIndicators();

            logger.debug("Updated {} folding regions", foldingRegions.size());
        } catch (Exception e) {
            logger.warn("Error updating folding regions", e);
        }
    }

    /**
     * Find XML element folding regions
     */
    private void findXmlElementFoldingRegions(String text) {
        String[] lines = text.split("\n");
        Stack<ElementStart> elementStack = new Stack<>();

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];

            // Find opening tags
            Matcher openMatcher = XML_ELEMENT_PATTERN.matcher(line);
            while (openMatcher.find()) {
                String tagName = openMatcher.group(1);
                int startOffset = getOffsetFromLine(lineIndex) + openMatcher.start();

                // Don't fold self-closing tags
                if (!line.substring(openMatcher.start(), openMatcher.end()).endsWith("/>")) {
                    elementStack.push(new ElementStart(tagName, lineIndex, startOffset));
                }
            }

            // Find closing tags
            Matcher closeMatcher = XML_CLOSING_PATTERN.matcher(line);
            while (closeMatcher.find()) {
                String tagName = closeMatcher.group(1);

                // Find matching opening tag
                ElementStart matchingStart = findMatchingStart(elementStack, tagName);
                if (matchingStart != null) {
                    int endOffset = getOffsetFromLine(lineIndex) + closeMatcher.end();

                    // Only create folding region if it spans multiple lines
                    if (lineIndex > matchingStart.line) {
                        XmlFoldingRegion region = new XmlFoldingRegion(
                                matchingStart.line, lineIndex,
                                matchingStart.offset, endOffset,
                                tagName, XmlFoldingRegion.FoldingType.XML_ELEMENT
                        );
                        foldingRegions.put(matchingStart.line, region);
                    }
                }
            }
        }
    }

    /**
     * Find comment folding regions
     */
    private void findCommentFoldingRegions(String text) {
        Matcher matcher = XML_COMMENT_PATTERN.matcher(text);

        while (matcher.find()) {
            int startOffset = matcher.start();
            int endOffset = matcher.end();

            int startLine = getLineFromOffset(startOffset);
            int endLine = getLineFromOffset(endOffset);

            // Only fold multi-line comments
            if (endLine > startLine) {
                XmlFoldingRegion region = new XmlFoldingRegion(
                        startLine, endLine, startOffset, endOffset,
                        "comment", XmlFoldingRegion.FoldingType.XML_COMMENT
                );
                foldingRegions.put(startLine, region);
            }
        }
    }

    /**
     * Find CDATA folding regions
     */
    private void findCDataFoldingRegions(String text) {
        Matcher matcher = XML_CDATA_PATTERN.matcher(text);

        while (matcher.find()) {
            int startOffset = matcher.start();
            int endOffset = matcher.end();

            int startLine = getLineFromOffset(startOffset);
            int endLine = getLineFromOffset(endOffset);

            // Only fold multi-line CDATA sections
            if (endLine > startLine) {
                XmlFoldingRegion region = new XmlFoldingRegion(
                        startLine, endLine, startOffset, endOffset,
                        "CDATA", XmlFoldingRegion.FoldingType.XML_CDATA
                );
                foldingRegions.put(startLine, region);
            }
        }
    }

    /**
         * Helper class for tracking element starts
         */
        private record ElementStart(String tagName, int line, int offset) {
    }

    /**
     * Find matching opening tag in stack
     */
    private ElementStart findMatchingStart(Stack<ElementStart> stack, String closingTagName) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            ElementStart element = stack.get(i);
            if (element.tagName.equals(closingTagName)) {
                // Remove this and all elements after it (handles nested tags)
                for (int j = stack.size() - 1; j >= i; j--) {
                    stack.remove(j);
                }
                return element;
            }
        }
        return null;
    }

    /**
     * Get text offset from line number
     */
    private int getOffsetFromLine(int lineIndex) {
        try {
            return codeArea.position(lineIndex, 0).toOffset();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get line number from text offset
     */
    private int getLineFromOffset(int offset) {
        try {
            return codeArea.offsetToPosition(offset, null).getMajor();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Fold the current region (region containing the cursor)
     */
    private void foldCurrentRegion() {
        int currentLine = codeArea.getCurrentParagraph();
        XmlFoldingRegion region = findFoldingRegionAtLine(currentLine);

        if (region != null && !region.isFolded()) {
            foldRegion(region);
            logger.debug("Folded region: {} at line {}", region.getTagName(), region.getStartLine());
        }
    }

    /**
     * Unfold the current region
     */
    private void unfoldCurrentRegion() {
        int currentLine = codeArea.getCurrentParagraph();
        XmlFoldingRegion region = findFoldingRegionAtLine(currentLine);

        if (region != null && region.isFolded()) {
            unfoldRegion(region);
            logger.debug("Unfolded region: {} at line {}", region.getTagName(), region.getStartLine());
        }
    }

    /**
     * Toggle folding at cursor position
     */
    private void toggleFoldingAtCursor() {
        int currentLine = codeArea.getCurrentParagraph();
        XmlFoldingRegion region = findFoldingRegionAtLine(currentLine);

        if (region != null) {
            if (region.isFolded()) {
                unfoldRegion(region);
            } else {
                foldRegion(region);
            }
        }
    }

    /**
     * Fold all available regions
     */
    private void foldAllRegions() {
        for (XmlFoldingRegion region : foldingRegions.values()) {
            if (!region.isFolded()) {
                foldRegion(region);
            }
        }
        logger.debug("Folded all {} regions", foldingRegions.size());
    }

    /**
     * Unfold all folded regions
     */
    private void unfoldAllRegions() {
        List<XmlFoldingRegion> toUnfold = new ArrayList<>(activeFolds);
        for (XmlFoldingRegion region : toUnfold) {
            unfoldRegion(region);
        }
        logger.debug("Unfolded all {} regions", toUnfold.size());
    }

    /**
     * Find folding region at specific line
     */
    private XmlFoldingRegion findFoldingRegionAtLine(int line) {
        // Check direct match
        XmlFoldingRegion region = foldingRegions.get(line);
        if (region != null) {
            return region;
        }

        // Check if line is within any region
        for (XmlFoldingRegion r : foldingRegions.values()) {
            if (line >= r.getStartLine() && line <= r.getEndLine()) {
                return r;
            }
        }

        return null;
    }

    /**
     * Fold a specific region
     */
    private void foldRegion(XmlFoldingRegion region) {
        if (region.isFolded()) {
            return;
        }

        try {
            // Mark as folded
            region.setFolded(true);
            activeFolds.add(region);

            // In a full implementation, this would hide the folded content
            // and show a folded text indicator
            // For now, we simulate this by adding visual styling
            updateFoldingVisuals();

        } catch (Exception e) {
            logger.warn("Error folding region", e);
        }
    }

    /**
     * Unfold a specific region
     */
    private void unfoldRegion(XmlFoldingRegion region) {
        if (!region.isFolded()) {
            return;
        }

        try {
            // Mark as unfolded
            region.setFolded(false);
            activeFolds.remove(region);

            // In a full implementation, this would restore the original content
            updateFoldingVisuals();

        } catch (Exception e) {
            logger.warn("Error unfolding region", e);
        }
    }

    /**
     * Update folding indicators in the UI
     */
    private void updateFoldingIndicators() {
        // In a full implementation, this would add clickable fold/unfold icons
        // to the line number gutter
        Platform.runLater(() -> {
            for (XmlFoldingRegion region : foldingRegions.values()) {
                // Add visual folding indicators (simplified)
                codeArea.getStyleClass().add("has-folding-regions");
            }
        });
    }

    /**
     * Update visual representation of folding
     */
    private void updateFoldingVisuals() {
        Platform.runLater(() -> {
            // Apply or remove folding visual styles
            if (activeFolds.isEmpty()) {
                codeArea.getStyleClass().remove("has-active-folds");
            } else {
                codeArea.getStyleClass().add("has-active-folds");
            }
        });
    }

    // ========== Enhanced Helper Methods ==========

    /**
     * Check if cursor is inside an XML tag
     */
    private boolean isInsideXmlTag() {
        int caretPos = codeArea.getCaretPosition();
        String text = codeArea.getText(0, caretPos);

        int lastOpen = text.lastIndexOf('<');
        int lastClose = text.lastIndexOf('>');

        return lastOpen > lastClose;
    }

    /**
     * Check if cursor is in an attribute value
     */
    private boolean isInAttributeValue() {
        int caretPos = codeArea.getCaretPosition();
        String text = codeArea.getText(0, caretPos);

        // Simple check for being inside quotes
        int lastQuote = text.lastIndexOf('"');
        if (lastQuote == -1) {
            lastQuote = text.lastIndexOf('\'');
        }

        if (lastQuote != -1) {
            String afterQuote = text.substring(lastQuote + 1);
            return !afterQuote.contains("\"") && !afterQuote.contains("'");
        }

        return false;
    }

    /**
     * Check if currently typing an element name
     */
    private boolean isTypingElementName() {
        int caretPos = codeArea.getCaretPosition();
        if (caretPos == 0) return false;

        String text = codeArea.getText(0, caretPos);
        return text.matches(".*<[a-zA-Z][a-zA-Z0-9-_]*$");
    }

    /**
     * Check if currently typing an attribute value
     */
    private boolean isTypingAttributeValue() {
        int caretPos = codeArea.getCaretPosition();
        if (caretPos == 0) return false;

        String text = codeArea.getText(0, caretPos);
        return text.matches(".*=[\"'][^\"']*$");
    }

    /**
     * Get current word being typed
     */
    private String getCurrentWord(String textBeforeCaret) {
        if (textBeforeCaret.isEmpty()) {
            return "";
        }

        int i = textBeforeCaret.length() - 1;
        while (i >= 0 && Character.isLetterOrDigit(textBeforeCaret.charAt(i))) {
            i--;
        }

        return textBeforeCaret.substring(i + 1);
    }

    /**
     * Get current XML tag name
     */
    private String getCurrentXmlTagName(String textBeforeCaret) {
        Pattern tagPattern = Pattern.compile("<([a-zA-Z][a-zA-Z0-9-_]*)(?:[^>]*)$");
        Matcher matcher = tagPattern.matcher(textBeforeCaret);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Get context-aware completions
     */
    private List<CompletionItem> getContextAwareCompletions(String textBeforeCaret, int caretPosition) {
        List<CompletionItem> completions = new ArrayList<>();

        // Add context-specific suggestions based on current position
        if (isInsideXmlTag()) {
            // Suggest common XML attributes
            completions.add(new CompletionItem("id", "id=\"\"", "attribute", "Unique identifier", CompletionItem.CompletionType.ATTRIBUTE));
            completions.add(new CompletionItem("class", "class=\"\"", "attribute", "CSS class", CompletionItem.CompletionType.ATTRIBUTE));
            completions.add(new CompletionItem("style", "style=\"\"", "attribute", "Inline styles", CompletionItem.CompletionType.ATTRIBUTE));
        }

        return completions;
    }

    /**
     * Get attribute completions for specific tag
     */
    private List<CompletionItem> getAttributeCompletions(String tagName) {
        List<CompletionItem> completions = new ArrayList<>();

        // Common attributes for all tags
        completions.add(new CompletionItem("id", "id=\"\"", "common", "Unique identifier", CompletionItem.CompletionType.ATTRIBUTE));
        completions.add(new CompletionItem("class", "class=\"\"", "common", "CSS class", CompletionItem.CompletionType.ATTRIBUTE));

        // Tag-specific attributes
        switch (tagName.toLowerCase()) {
            case "img":
                completions.add(new CompletionItem("src", "src=\"\"", "img", "Image source", CompletionItem.CompletionType.ATTRIBUTE));
                completions.add(new CompletionItem("alt", "alt=\"\"", "img", "Alternative text", CompletionItem.CompletionType.ATTRIBUTE));
                break;
            case "a":
                completions.add(new CompletionItem("href", "href=\"\"", "link", "Link URL", CompletionItem.CompletionType.ATTRIBUTE));
                completions.add(new CompletionItem("target", "target=\"\"", "link", "Link target", CompletionItem.CompletionType.ATTRIBUTE));
                break;
            case "input":
                completions.add(new CompletionItem("type", "type=\"\"", "input", "Input type", CompletionItem.CompletionType.ATTRIBUTE));
                completions.add(new CompletionItem("name", "name=\"\"", "input", "Input name", CompletionItem.CompletionType.ATTRIBUTE));
                completions.add(new CompletionItem("value", "value=\"\"", "input", "Input value", CompletionItem.CompletionType.ATTRIBUTE));
                break;
        }

        return completions;
    }

    /**
     * Display completion popup with enhanced items
     */
    private void displayCompletionPopup(List<CompletionItem> completions) {
        if (completions.isEmpty()) {
            return;
        }

        autoCompletionPopup.getItems().clear();

        // Sort completions by type and name
        completions.sort((a, b) -> {
            int typeCompare = a.type().compareTo(b.type());
            if (typeCompare != 0) {
                return typeCompare;
            }
            return a.displayText().compareToIgnoreCase(b.displayText());
        });

        // Limit to top 20 completions for performance
        List<CompletionItem> limitedCompletions = completions.subList(0, Math.min(20, completions.size()));

        for (CompletionItem completion : limitedCompletions) {
            MenuItem item = new MenuItem(completion.displayText());
            item.setOnAction(e -> insertCompletion(completion));

            // Add visual styling based on completion type
            switch (completion.type()) {
                case SNIPPET:
                    item.getStyleClass().add("snippet-completion");
                    break;
                case ATTRIBUTE:
                    item.getStyleClass().add("attribute-completion");
                    break;
                case CONTEXT:
                    item.getStyleClass().add("context-completion");
                    break;
                default:
                    item.getStyleClass().add("intellisense-completion");
                    break;
            }

            autoCompletionPopup.getItems().add(item);
        }

        // Show popup at caret position
        try {
            var caretBounds = codeArea.getCaretBounds();
            if (caretBounds.isPresent()) {
                autoCompletionPopup.show(codeArea, caretBounds.get().getMinX(), caretBounds.get().getMaxY());
            }
        } catch (Exception e) {
            logger.warn("Could not show completion popup", e);
        }
    }

    // ========== Public Smart Completion API ==========

    /**
     * Enable or disable snippet support
     */
    public void setSnippetSupportEnabled(boolean enabled) {
        this.snippetSupportEnabled = enabled;
        logger.debug("Snippet support {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Enable or disable context-aware completion
     */
    public void setContextAwareCompletionEnabled(boolean enabled) {
        this.contextAwareCompletionEnabled = enabled;
        logger.debug("Context-aware completion {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Add custom snippet
     */
    public void addCustomSnippet(String trigger, String template, String description, String category) {
        if (snippetManager != null) {
            snippetManager.addSnippet(new XmlSnippetManager.XmlSnippet(trigger, template, description, category));
            logger.debug("Added custom snippet: {}", trigger);
        }
    }

    /**
     * Get snippet manager
     */
    public XmlSnippetManager getSnippetManager() {
        return snippetManager;
    }

    // ========== Search & Replace API ==========

    /**
     * Show search panel (search only)
     */
    public void showSearchPanel() {
        if (searchReplacePanel != null) {
            if (!getChildren().contains(searchReplacePanel)) {
                getChildren().add(0, searchReplacePanel); // Add at top
            }
            searchReplacePanel.show();
            logger.debug("Search panel shown");
        }
    }

    /**
     * Show search & replace panel (search and replace)
     */
    public void showSearchReplacePanel() {
        showSearchPanel(); // Same panel, different usage
    }

    /**
     * Hide search panel
     */
    public void hideSearchPanel() {
        if (searchReplacePanel != null) {
            searchReplacePanel.hide();
            getChildren().remove(searchReplacePanel);
            logger.debug("Search panel hidden");
        }
    }

    /**
     * Toggle search panel visibility
     */
    public void toggleSearchPanel() {
        if (searchReplacePanel != null && searchReplacePanel.isSearchVisible()) {
            hideSearchPanel();
        } else {
            showSearchPanel();
        }
    }

    /**
     * Find next occurrence using search panel
     */
    public void findNext() {
        if (searchReplacePanel != null && searchReplacePanel.isSearchVisible()) {
            // Trigger find next in search panel
            logger.debug("Find next requested");
        }
    }

    /**
     * Find previous occurrence using search panel
     */
    public void findPrevious() {
        if (searchReplacePanel != null && searchReplacePanel.isSearchVisible()) {
            // Trigger find previous in search panel
            logger.debug("Find previous requested");
        }
    }

    /**
     * Enable or disable search & replace functionality
     */
    public void setSearchReplaceEnabled(boolean enabled) {
        this.searchReplaceEnabled = enabled;
        if (enabled && searchReplacePanel == null) {
            initializeSearchReplace();
        } else if (!enabled && searchReplacePanel != null) {
            hideSearchPanel();
            searchReplacePanel = null;
        }
        logger.debug("Search & Replace {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Check if search & replace is enabled
     */
    public boolean isSearchReplaceEnabled() {
        return searchReplaceEnabled;
    }

    /**
     * Get search & replace panel
     */
    public XmlSearchReplacePanel getSearchReplacePanel() {
        return searchReplacePanel;
    }

    // ========== Public Bracket Matching API ==========

    /**
     * Enable or disable bracket matching
     */
    public void setBracketMatchingEnabled(boolean enabled) {
        this.bracketMatchingEnabled = enabled;
        if (!enabled) {
            clearBracketHighlighting();
        }
        logger.debug("Bracket matching {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Enable or disable tag pair highlighting
     */
    public void setTagPairHighlightingEnabled(boolean enabled) {
        this.tagPairHighlightingEnabled = enabled;
        if (!enabled) {
            clearTagHighlighting();
        }
        logger.debug("Tag pair highlighting {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Enable or disable bracket rainbow coloring
     */
    public void setBracketRainbowEnabled(boolean enabled) {
        this.bracketRainbowEnabled = enabled;
        if (tagPairHighlightingEnabled) {
            updateTagPairHighlighting();
        }
        logger.debug("Bracket rainbow coloring {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Get current bracket match
     */
    public BracketMatchResult getCurrentBracketMatch() {
        return currentBracketMatch;
    }

    /**
     * Get active tag highlights
     */
    public List<TagPairHighlight> getActiveTagHighlights() {
        return new ArrayList<>(activeTagHighlights);
    }

    /**
     * Jump to matching bracket or tag
     */
    public void jumpToMatchingBracket() {
        if (currentBracketMatch != null && currentBracketMatch.isMatch()) {
            int caretPos = codeArea.getCaretPosition();
            int targetPos;

            // Determine which position to jump to
            if (Math.abs(caretPos - currentBracketMatch.startPos()) <= 1) {
                targetPos = currentBracketMatch.endPos();
            } else {
                targetPos = currentBracketMatch.startPos();
            }

            codeArea.moveTo(targetPos);
            logger.debug("Jumped to matching bracket at position {}", targetPos);
        }
    }

    /**
     * Select content between matching brackets
     */
    public void selectBracketContent() {
        if (currentBracketMatch != null && currentBracketMatch.isMatch()) {
            int startPos = currentBracketMatch.startPos() + 1;
            int endPos = currentBracketMatch.endPos();

            if (startPos < endPos) {
                codeArea.selectRange(startPos, endPos);
                logger.debug("Selected bracket content from {} to {}", startPos, endPos);
            }
        }
    }

    /**
     * Select entire tag pair including tags
     */
    public void selectTagPair() {
        if (!activeTagHighlights.isEmpty()) {
            TagPairHighlight tagPair = activeTagHighlights.get(0);
            codeArea.selectRange(tagPair.openStartPos(), tagPair.closeEndPos());
            logger.debug("Selected tag pair: {}", tagPair.tagName());
        }
    }

    // ========== Public Folding API ==========

    /**
     * Enable or disable folding system
     */
    public void setFoldingEnabled(boolean enabled) {
        this.foldingEnabled = enabled;
        if (enabled) {
            setupIntelligentFolding();
            updateFoldingRegions(codeArea.getText());
        } else {
            // Clear all folding
            unfoldAllRegions();
            foldingRegions.clear();
            activeFolds.clear();
        }
        logger.debug("Folding system {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Check if folding is enabled
     */
    public boolean isFoldingEnabled() {
        return foldingEnabled;
    }

    /**
     * Get all folding regions
     */
    public Map<Integer, XmlFoldingRegion> getFoldingRegions() {
        return new HashMap<>(foldingRegions);
    }

    /**
     * Get currently active (folded) regions
     */
    public List<XmlFoldingRegion> getActiveFolds() {
        return new ArrayList<>(activeFolds);
    }

    /**
     * Fold region by line number
     */
    public boolean foldRegionAtLine(int line) {
        XmlFoldingRegion region = foldingRegions.get(line);
        if (region != null && !region.isFolded()) {
            foldRegion(region);
            return true;
        }
        return false;
    }

    /**
     * Unfold region by line number
     */
    public boolean unfoldRegionAtLine(int line) {
        XmlFoldingRegion region = foldingRegions.get(line);
        if (region != null && region.isFolded()) {
            unfoldRegion(region);
            return true;
        }
        return false;
    }

    // ========== Enhanced Auto-Pairing ==========

    /**
     * Handle smart pair insertion for XML brackets, quotes, etc.
     */
    private void handleSmartPairing(String character) {
        if (!smartPairEnabled) return;

        String closingChar = xmlPairs.get(character);
        if (closingChar != null) {
            int caretPos = codeArea.getCaretPosition();

            // Check if we need to insert closing character
            if (shouldInsertClosingChar(character, caretPos)) {
                Platform.runLater(() -> {
                    codeArea.insertText(caretPos, closingChar);
                    codeArea.moveTo(caretPos); // Move back between the pair
                });

                logger.debug("Inserted smart pair: {} -> {}", character, closingChar);
            }
        }

        // Auto-close XML tags for '<' character
        if ("<".equals(character)) {
            int caretPos = codeArea.getCaretPosition();
            handleXmlTagAutoClose(caretPos - 1);
        }
    }

    /**
     * Handle XML tag auto-closing when user types '<'
     */
    private void handleXmlTagAutoClose(int position) {
        // Check if this looks like the start of a closing tag
        String text = codeArea.getText();
        if (position + 1 < text.length() && text.charAt(position + 1) == '/') {
            // This is a closing tag, try to auto-complete it
            autoCompleteClosingTag(position);
        }
    }

    /**
     * Auto-complete closing tag
     */
    private void autoCompleteClosingTag(int position) {
        String text = codeArea.getText();

        // Find the most recent unclosed tag
        List<XmlTag> tags = findAllXmlTags(text.substring(0, position));
        Stack<String> openTags = new Stack<>();

        for (XmlTag tag : tags) {
            if (!tag.isClosing && !tag.isSelfClosing) {
                openTags.push(tag.name);
            } else if (tag.isClosing && !openTags.isEmpty() && openTags.peek().equals(tag.name)) {
                openTags.pop();
            }
        }

        if (!openTags.isEmpty()) {
            String tagToClose = openTags.peek();
            Platform.runLater(() -> {
                int currentPos = codeArea.getCaretPosition();
                codeArea.insertText(currentPos, "/" + tagToClose + ">");
                // Move cursor after the inserted text
                codeArea.moveTo(currentPos + tagToClose.length() + 2);
            });
            logger.debug("Auto-completed closing tag: {}", tagToClose);
        }
    }

    /**
     * Determine if closing character should be inserted
     */
    private boolean shouldInsertClosingChar(String openChar, int position) {
        // Don't insert if next character is already the closing character
        if (position < codeArea.getLength()) {
            String nextChar = codeArea.getText(position, position + 1);
            String expectedClose = xmlPairs.get(openChar);
            if (expectedClose != null && expectedClose.equals(nextChar)) {
                return false;
            }
        }

        // For quotes, check if we're already inside quotes
        if ("\"".equals(openChar) || "'".equals(openChar)) {
            return !isInsideQuotes(position, openChar);
        }

        return true;
    }

    /**
     * Check if position is inside quotes
     */
    private boolean isInsideQuotes(int position, String quoteChar) {
        String textBefore = codeArea.getText(0, position);
        int count = 0;
        for (int i = 0; i < textBefore.length(); i++) {
            if (textBefore.substring(i, i + 1).equals(quoteChar)) {
                count++;
            }
        }
        return count % 2 == 1; // Odd number means we're inside quotes
    }

    // ========== Theme Management ==========

    /**
     * Apply theme styles to the code area
     */
    private void applyThemeToCodeArea(ModernXmlThemeManager.XmlHighlightTheme theme) {
        if (theme == null || codeArea == null) return;

        try {
            // Apply theme CSS to the code area
            String themeCss = theme.generateThemeCss();

            // Clear existing theme styles
            codeArea.getStylesheets().removeIf(css -> css.contains("xml-theme-"));

            // Create inline CSS for the theme using base styles
            String backgroundColor = theme.getBaseStyle("background") != null ? theme.getBaseStyle("background") : "#ffffff";
            String textColor = theme.getBaseStyle("text") != null ? theme.getBaseStyle("text") : "#000000";
            String fontFamily = theme.getBaseStyle("fontFamily") != null ? theme.getBaseStyle("fontFamily") : "Monaco, 'Courier New', monospace";
            String fontSize = theme.getBaseStyle("fontSize") != null ? theme.getBaseStyle("fontSize") : "12";

            String inlineStyle = String.format(
                    "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-family: %s; -fx-font-size: %spx;",
                    backgroundColor,
                    textColor,
                    fontFamily,
                    fontSize
            );

            codeArea.setStyle(inlineStyle);

            // Add theme-specific stylesheet if available
            if (!themeCss.isEmpty()) {
                // Note: For full CSS support, we would need to add the CSS to a stylesheet
                // For now, we apply basic styling directly
                logger.debug("Applied theme '{}' to code area", theme.getDisplayName());
            }

        } catch (Exception e) {
            logger.warn("Failed to apply theme to code area", e);
        }
    }

    /**
     * Refresh syntax highlighting with current theme
     */
    private void refreshHighlighting() {
        if (!advancedHighlightingEnabled || advancedHighlighter == null) return;

        try {
            String text = codeArea.getText();
            if (text.isEmpty()) return;

            // Refresh highlighting in background for performance
            Task<StyleSpans<Collection<String>>> task = advancedHighlighter.computeHighlightingAsync(text);

            task.setOnSucceeded(e -> {
                StyleSpans<Collection<String>> highlighting = task.getValue();
                if (highlighting != null) {
                    Platform.runLater(() -> {
                        try {
                            codeArea.setStyleSpans(0, highlighting);
                            logger.debug("Refreshed XML syntax highlighting with {} spans", highlighting.getSpanCount());
                        } catch (Exception ex) {
                            logger.warn("Failed to apply refreshed highlighting", ex);
                        }
                    });
                }
            });

            task.setOnFailed(e -> {
                Throwable exception = task.getException();
                logger.warn("Failed to refresh syntax highlighting", exception);
            });

            executor.submit(task);

        } catch (Exception e) {
            logger.warn("Error during highlighting refresh", e);
        }
    }

    // ========== Validation Methods ==========

    /**
     * Highlight error location in the editor
     */
    private void highlightErrorLocation(XmlValidationError error) {
        if (!error.hasLocation()) return;

        try {
            int lineNumber = error.getLineNumber();
            int columnNumber = error.getColumnNumber();

            // Get line start position
            int lineStart = codeArea.getAbsolutePosition(lineNumber - 1, 0);
            int position = columnNumber > 0 ?
                    Math.min(lineStart + columnNumber - 1, codeArea.getLength()) :
                    lineStart;

            // Highlight the error location temporarily
            Platform.runLater(() -> {
                codeArea.moveTo(position);
                codeArea.requestFollowCaret();

                // TODO: Add temporary highlighting overlay for error location
                logger.debug("Highlighted error at line {}, column {}", lineNumber, columnNumber);
            });

        } catch (Exception e) {
            logger.warn("Failed to highlight error location", e);
        }
    }

    /**
     * Update syntax highlighting with validation errors
     */
    private void updateValidationHighlighting(XmlValidationResult result) {
        if (result == null || !showValidationErrors) return;

        try {
            // For now, we'll integrate this with the existing syntax highlighting
            // In the future, we could add error underlines or different styling
            refreshHighlighting();

            logger.debug("Updated validation highlighting with {} errors, {} warnings",
                    result.getErrorCount(), result.getWarningCount());

        } catch (Exception e) {
            logger.warn("Failed to update validation highlighting", e);
        }
    }

    /**
     * Get the validation panel
     */
    public XmlValidationPanel getValidationPanel() {
        return validationPanel;
    }

    /**
     * Set validation enabled/disabled
     */
    public void setValidationEnabled(boolean enabled) {
        this.validationEnabled = enabled;

        if (enabled && validationPanel == null) {
            initializeValidation();
        } else if (!enabled && validationPanel != null) {
            validationPanel.shutdown();
            validationPanel = null;
        }

        logger.debug("XML Validation {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Set whether to show validation errors in editor
     */
    public void setShowValidationErrors(boolean show) {
        this.showValidationErrors = show;
        logger.debug("Show validation errors in editor {}", show ? "enabled" : "disabled");
    }

    /**
     * Trigger manual validation of current content
     */
    public void validateCurrentContent() {
        if (validationPanel != null) {
            String content = codeArea.getText();
            if (content != null && !content.trim().isEmpty()) {
                validationPanel.validateXml(content, null);
            }
        }
    }

    /**
     * Trigger manual validation with specific schema
     */
    public void validateCurrentContent(String schemaPath) {
        if (validationPanel != null) {
            String content = codeArea.getText();
            if (content != null && !content.trim().isEmpty()) {
                validationPanel.validateXml(content, schemaPath);
            }
        }
    }

    // ========== Cleanup ==========

    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            logger.debug("EnhancedXmlCodeEditor executor shutdown");
        }

        if (validationPanel != null) {
            validationPanel.shutdown();
        }

        hideAutoCompletion();
    }
}