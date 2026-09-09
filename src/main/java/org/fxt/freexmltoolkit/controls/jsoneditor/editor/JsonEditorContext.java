package org.fxt.freexmltoolkit.controls.jsoneditor.editor;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.JsonCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.JsonCommandManager;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonDocument;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.editor.core.NodeSelectionModel;

/**
 * Central coordinator of the JSON structured views (Tree and Grid): the JSON counterpart of
 * {@code XmlEditorContext}.
 *
 * <p>Owns the {@link JsonDocument}, the undo/redo {@link JsonCommandManager} and the
 * {@link NodeSelectionModel}. The shell keeps ONE context per JSON document across the
 * Text/Tree/Graphic views, so edits and undo history survive switching modes.</p>
 *
 * <p>PropertyChangeEvents fired:</p>
 * <ul>
 *   <li>{@code "document"} – the document was (re)loaded or replaced</li>
 *   <li>{@code "modelChanged"} – a command was executed, undone or redone (new value: revision)</li>
 *   <li>{@code "dirty"}, {@code "canUndo"}, {@code "canRedo"}, {@code "editMode"}</li>
 *   <li>{@code "lossyFormatDetected"} – the loaded text carried comments / JSON5 syntax the
 *       model cannot preserve (fired once per load, old {@code false} → new {@code true})</li>
 * </ul>
 */
public class JsonEditorContext {

    private static final Logger logger = LogManager.getLogger(JsonEditorContext.class);

    private static final int DEFAULT_INDENT = 2;

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final JsonCommandManager commandManager = new JsonCommandManager();
    private final NodeSelectionModel<JsonNode> selectionModel = new NodeSelectionModel<>();

    private JsonDocument document;
    private boolean editMode = true;
    private boolean dirty = false;
    private boolean trailingNewline = false;
    private boolean lossyFormat = false;
    private long revision = 0;

    /** Creates a context over an empty document (no root value). */
    public JsonEditorContext() {
        this(new JsonDocument());
    }

    /** Creates a context over the given document. */
    public JsonEditorContext(JsonDocument document) {
        this.document = document != null ? document : new JsonDocument();
        commandManager.addPropertyChangeListener("dirty", evt -> setDirty((Boolean) evt.getNewValue()));
        commandManager.addPropertyChangeListener("canUndo",
                evt -> pcs.firePropertyChange("canUndo", evt.getOldValue(), evt.getNewValue()));
        commandManager.addPropertyChangeListener("canRedo",
                evt -> pcs.firePropertyChange("canRedo", evt.getOldValue(), evt.getNewValue()));
    }

    // ==================== Document ====================

    /**
     * Parses the text into a fresh document, clearing the undo history and the selection.
     *
     * @param text the JSON / JSONC / JSON5 text
     * @throws com.google.gson.JsonSyntaxException when the text is not valid JSON
     */
    public void loadDocumentFromString(String text) {
        JsonDocument parsed = JsonNodeFactory.parse(text);
        trailingNewline = text != null && text.endsWith("\n");
        commandManager.clear();
        selectionModel.clearSelection();
        setDirty(false);
        setDocument(parsed);
        lossyFormat = hasLossyConstructs(text);
        if (lossyFormat) {
            pcs.firePropertyChange("lossyFormatDetected", false, true);
        }
    }

    /** @return the current document (never {@code null}; may have no root value) */
    public JsonDocument getDocument() {
        return document;
    }

    /** Replaces the document and fires {@code "document"}. */
    public void setDocument(JsonDocument document) {
        JsonDocument old = this.document;
        this.document = document != null ? document : new JsonDocument();
        pcs.firePropertyChange("document", old, this.document);
    }

    /** @return the detected source format ({@code json}, {@code jsonc} or {@code json5}) */
    public String getFormat() {
        return document.getFormat();
    }

    /** @return whether the loaded text carried comments / JSON5 syntax the model drops on save */
    public boolean isLossyFormat() {
        return lossyFormat;
    }

    /**
     * Serializes the document with the configured JSON indent (settings, fallback 2 spaces),
     * keeping a trailing newline iff the loaded text ended with one.
     */
    public String serializeToString() {
        String json = document.serialize(resolveIndent(), 0);
        return trailingNewline ? json + "\n" : json;
    }

    private static int resolveIndent() {
        try {
            var properties = org.fxt.freexmltoolkit.di.ServiceRegistry.get(
                    org.fxt.freexmltoolkit.service.PropertiesService.class);
            if (properties != null) {
                int indent = properties.getJsonIndentSpaces();
                if (indent > 0) {
                    return indent;
                }
            }
        } catch (Exception e) {
            // Fall back to the default if the registry is unavailable (e.g. in tests)
            logger.debug("JSON indent setting unavailable, using default", e);
        }
        return DEFAULT_INDENT;
    }

    // ==================== Commands ====================

    /**
     * Executes a command through the undo stack.
     *
     * @return {@code true} when the command was applied
     * @throws IllegalStateException in read-only mode
     */
    public boolean executeCommand(JsonCommand command) {
        if (!editMode) {
            throw new IllegalStateException("Cannot execute commands in read-only mode");
        }
        boolean ok = commandManager.executeCommand(command);
        if (ok) {
            fireModelChanged();
        }
        return ok;
    }

    /** Undoes the last command. @return whether something was undone */
    public boolean undo() {
        boolean ok = commandManager.undo();
        if (ok) {
            fireModelChanged();
        }
        return ok;
    }

    /** Redoes the last undone command. @return whether something was redone */
    public boolean redo() {
        boolean ok = commandManager.redo();
        if (ok) {
            fireModelChanged();
        }
        return ok;
    }

    public boolean canUndo() {
        return commandManager.canUndo();
    }

    public boolean canRedo() {
        return commandManager.canRedo();
    }

    /** Drops the undo/redo history. */
    public void clearHistory() {
        commandManager.clear();
    }

    private void fireModelChanged() {
        pcs.firePropertyChange("modelChanged", null, Long.valueOf(++revision));
    }

    public JsonCommandManager getCommandManager() {
        return commandManager;
    }

    public NodeSelectionModel<JsonNode> getSelectionModel() {
        return selectionModel;
    }

    // ==================== State ====================

    public boolean isEditMode() {
        return editMode;
    }

    public void setEditMode(boolean editMode) {
        boolean old = this.editMode;
        this.editMode = editMode;
        pcs.firePropertyChange("editMode", old, editMode);
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        boolean old = this.dirty;
        this.dirty = dirty;
        pcs.firePropertyChange("dirty", old, dirty);
    }

    // ==================== Lossy format detection ====================

    /**
     * Whether the text uses constructs the model cannot round-trip: comments, single-quoted
     * strings, unquoted keys, trailing commas, hex numbers, {@code Infinity}/{@code NaN}.
     * String contents are skipped, so {@code "http://x"} or {@code "/* no *&#47;"} do not count.
     */
    public static boolean hasLossyConstructs(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '"') {
                // Skip a double-quoted string (with escapes)
                i++;
                while (i < n && text.charAt(i) != '"') {
                    i += text.charAt(i) == '\\' ? 2 : 1;
                }
                i++;
                continue;
            }
            if (c == '\'') {
                return true; // single-quoted string (JSON5)
            }
            if (c == '/' && i + 1 < n && (text.charAt(i + 1) == '/' || text.charAt(i + 1) == '*')) {
                return true; // comment
            }
            if (c == ',') {
                int j = i + 1;
                while (j < n && Character.isWhitespace(text.charAt(j))) {
                    j++;
                }
                if (j < n && (text.charAt(j) == '}' || text.charAt(j) == ']')) {
                    return true; // trailing comma
                }
            }
            if (Character.isLetter(c) || c == '_' || c == '$') {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '_'
                        || text.charAt(j) == '$')) {
                    j++;
                }
                String word = text.substring(i, j);
                if (!word.equals("true") && !word.equals("false") && !word.equals("null")) {
                    return true; // unquoted key, Infinity, NaN, hex prefix, …
                }
                i = j;
                continue;
            }
            if (c == '0' && i + 1 < n && (text.charAt(i + 1) == 'x' || text.charAt(i + 1) == 'X')) {
                return true; // hex number
            }
            i++;
        }
        return false;
    }

    // ==================== Listeners ====================

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(propertyName, listener);
    }
}
