package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.Duration;

/**
 * Bridge for bi-directional synchronization between XmlTextView and XmlDocument model.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Listens to text changes in XmlTextView</li>
 *   <li>Parses XML text and updates the model in XmlEditorContext</li>
 *   <li>Listens to model changes and updates the text view</li>
 *   <li>Prevents synchronization loops</li>
 *   <li>Handles debouncing for performance</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * XmlEditorContext context = new XmlEditorContext();
 * XmlTextView textView = new XmlTextView(context);
 *
 * // Enable bi-directional sync
 * XmlTextModelBridge bridge = new XmlTextModelBridge(context, textView);
 * bridge.enable();
 *
 * // Now changes in text view update the model
 * // and changes in model update the text view
 * }</pre>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlTextModelBridge {

    /**
     * The editor context.
     */
    private final XmlEditorContext context;

    /**
     * The text view.
     */
    private final XmlTextView textView;

    /**
     * Whether synchronization is enabled.
     */
    private boolean enabled = false;

    /**
     * Whether to suppress text-to-model sync (to prevent loops).
     */
    private boolean suppressTextToModel = false;

    /**
     * Whether to suppress model-to-text sync (to prevent loops).
     */
    private boolean suppressModelToText = false;

    /**
     * Debounce duration for text changes (milliseconds).
     */
    private Duration debounceDuration = Duration.ofMillis(500);

    /**
     * Whether to auto-parse text changes.
     */
    private boolean autoParseEnabled = true;

    // ==================== Constructor ====================

    /**
     * Constructs a new XmlTextModelBridge.
     *
     * @param context  the editor context
     * @param textView the text view
     */
    public XmlTextModelBridge(XmlEditorContext context, XmlTextView textView) {
        this.context = context;
        this.textView = textView;
    }

    // ==================== Enable/Disable ====================

    /**
     * Enables bi-directional synchronization.
     */
    public void enable() {
        if (enabled) {
            return;
        }

        enabled = true;
        setupTextToModelSync();
        setupModelToTextSync();
    }

    /**
     * Disables bi-directional synchronization.
     */
    public void disable() {
        if (!enabled) {
            return;
        }

        enabled = false;
        // Note: Listeners remain attached but sync is disabled
    }

    /**
     * Checks if synchronization is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    // ==================== Text to Model Sync ====================

    /**
     * Sets up text-to-model synchronization.
     * Listens to text changes and updates the model.
     */
    private void setupTextToModelSync() {
        if (!autoParseEnabled) {
            return;
        }

        // Listen to text changes with debouncing
        textView.getCodeArea().multiPlainChanges()
                .successionEnds(debounceDuration)
                .subscribe(ignore -> {
                    if (enabled && !suppressTextToModel) {
                        syncTextToModel();
                    }
                });
    }

    /**
     * Synchronizes text to model.
     * Parses the current text and updates the document in the context.
     */
    private void syncTextToModel() {
        try {
            suppressModelToText = true;

            String text = textView.getText();

            // Skip empty text
            if (text == null || text.trim().isEmpty()) {
                return;
            }

            // Parse text to model
            XmlDocument newDoc = context.getParser().parse(text);

            // Update context document
            // Note: This creates a new document, so it will trigger document change listeners
            // We suppress model-to-text sync to prevent loops
            context.loadDocumentFromString(text);

        } catch (Exception e) {
            // If parsing fails, don't update the model
            // This allows users to have temporarily invalid XML while editing
            System.err.println("Text-to-model sync failed (invalid XML): " + e.getMessage());
        } finally {
            suppressModelToText = false;
        }
    }

    /**
     * Manually triggers text-to-model synchronization.
     * Useful for forcing a sync without waiting for debounce.
     */
    public void syncTextToModelNow() {
        if (!enabled || suppressTextToModel) {
            return;
        }

        syncTextToModel();
    }

    // ==================== Model to Text Sync ====================

    /**
     * Sets up model-to-text synchronization.
     * Listens to model changes and updates the text view.
     */
    private void setupModelToTextSync() {
        // Listen to document changes
        context.addPropertyChangeListener("document", this::onModelChanged);
    }

    /**
     * Called when the model changes.
     * Updates the text view with the serialized model.
     */
    private void onModelChanged(PropertyChangeEvent evt) {
        if (!enabled || suppressModelToText) {
            return;
        }

        try {
            suppressTextToModel = true;

            // Serialize model to text
            String text = context.serializeToString();

            // Update text view
            textView.setText(text);

        } catch (Exception e) {
            System.err.println("Model-to-text sync failed: " + e.getMessage());
        } finally {
            suppressTextToModel = false;
        }
    }

    // ==================== Configuration ====================

    /**
     * Sets the debounce duration for text changes.
     *
     * @param duration the debounce duration
     */
    public void setDebounceDuration(Duration duration) {
        this.debounceDuration = duration;
    }

    /**
     * Returns the debounce duration.
     *
     * @return the debounce duration
     */
    public Duration getDebounceDuration() {
        return debounceDuration;
    }

    /**
     * Sets whether auto-parsing of text changes is enabled.
     * If disabled, you must call syncTextToModelNow() manually.
     *
     * @param enabled true to enable auto-parsing
     */
    public void setAutoParseEnabled(boolean enabled) {
        this.autoParseEnabled = enabled;
    }

    /**
     * Returns whether auto-parsing is enabled.
     *
     * @return true if enabled
     */
    public boolean isAutoParseEnabled() {
        return autoParseEnabled;
    }

    /**
     * Validates the current text.
     * Attempts to parse it and returns whether it's valid XML.
     *
     * @return true if valid XML
     */
    public boolean validateText() {
        try {
            String text = textView.getText();
            if (text == null || text.trim().isEmpty()) {
                return false;
            }

            context.getParser().parse(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the validation error message for the current text.
     * Returns null if text is valid.
     *
     * @return error message, or null if valid
     */
    public String getValidationError() {
        try {
            String text = textView.getText();
            if (text == null || text.trim().isEmpty()) {
                return "Text is empty";
            }

            context.getParser().parse(text);
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }
}
