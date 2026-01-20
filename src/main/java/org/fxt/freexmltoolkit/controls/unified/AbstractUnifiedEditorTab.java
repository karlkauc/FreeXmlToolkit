package org.fxt.freexmltoolkit.controls.unified;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.domain.LinkedFileInfo;
import org.fxt.freexmltoolkit.domain.UnifiedEditorFileType;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.util.List;

/**
 * Abstract base class for all Unified Editor tabs.
 * Provides common functionality for XML, XSD, XSLT, and Schematron tabs.
 *
 * Features:
 * - Dirty tracking (unsaved changes) with "*" indicator
 * - File type icon and color coding
 * - Save/Reload/Discard methods
 * - Linked file detection
 * - Content access
 *
 * @since 2.0
 */
public abstract class AbstractUnifiedEditorTab extends Tab {

    /** The source file being edited. */
    protected final File sourceFile;
    /** The type of file being edited. */
    protected final UnifiedEditorFileType fileType;
    /** Property tracking the dirty state (unsaved changes). */
    protected final BooleanProperty dirty = new SimpleBooleanProperty(false);
    private final String originalTitle;

    /**
     * Creates a new Unified Editor tab.
     *
     * @param sourceFile the file being edited (can be null for new files)
     * @param fileType   the type of file being edited
     */
    protected AbstractUnifiedEditorTab(File sourceFile, UnifiedEditorFileType fileType) {
        super();
        this.sourceFile = sourceFile;
        this.fileType = fileType;
        this.originalTitle = sourceFile != null ? sourceFile.getName() : "Untitled." + fileType.getDefaultExtension();

        setText(originalTitle);
        setClosable(true);

        // Set icon based on file type
        FontIcon icon = new FontIcon(fileType.getIcon());
        icon.setIconSize(16);
        icon.setIconColor(Color.web(fileType.getColor()));
        setGraphic(icon);

        // Add style class for type-specific styling
        getStyleClass().addAll("unified-tab", fileType.getStyleClass());

        // Set tooltip with full path
        if (sourceFile != null) {
            setTooltip(new Tooltip(sourceFile.getAbsolutePath()));
        }

        // Listen to dirty changes to update title
        dirty.addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                setText(originalTitle + " *");
                if (!getStyleClass().contains("dirty")) {
                    getStyleClass().add("dirty");
                }
            } else {
                setText(originalTitle);
                getStyleClass().remove("dirty");
            }
        });
    }

    /**
     * Initializes the tab content.
     * Called after constructor, must be implemented by subclasses.
     */
    protected abstract void initializeContent();

    /**
     * Gets the current text content of the editor.
     *
     * @return the editor content as string
     */
    public abstract String getEditorContent();

    /**
     * Sets the text content of the editor.
     *
     * @param content the content to set
     */
    public abstract void setEditorContent(String content);

    /**
     * Saves the current content to the source file.
     *
     * @return true if save was successful, false otherwise
     */
    public abstract boolean save();

    /**
     * Saves the content to a new file.
     *
     * @param file the file to save to
     * @return true if save was successful, false otherwise
     */
    public abstract boolean saveAs(File file);

    /**
     * Reloads the content from the source file.
     * Discards any unsaved changes.
     */
    public abstract void reload();

    /**
     * Discards all unsaved changes and reverts to the last saved state.
     */
    public abstract void discardChanges();

    /**
     * Validates the content.
     *
     * @return validation result message, or null if valid
     */
    public abstract String validate();

    /**
     * Formats/pretty-prints the content.
     */
    public abstract void format();

    /**
     * Detects linked files referenced in this document.
     *
     * @return list of linked file information
     */
    public abstract List<LinkedFileInfo> detectLinkedFiles();

    /**
     * Gets the caret position (line:column).
     *
     * @return formatted position string like "Ln 1, Col 1"
     */
    public abstract String getCaretPosition();

    // Property accessors

    /**
     * Checks if this tab has unsaved changes.
     *
     * @return true if dirty
     */
    public boolean isDirty() {
        return dirty.get();
    }

    /**
     * Sets the dirty state.
     *
     * @param dirty true if has unsaved changes
     */
    public void setDirty(boolean dirty) {
        this.dirty.set(dirty);
    }

    public BooleanProperty dirtyProperty() {
        return dirty;
    }

    /**
     * Gets the source file being edited.
     *
     * @return the source file, or null for new files
     */
    public File getSourceFile() {
        return sourceFile;
    }

    /**
     * Gets the file type of this tab.
     *
     * @return the file type
     */
    public UnifiedEditorFileType getFileType() {
        return fileType;
    }

    /**
     * Gets a unique identifier for this tab (based on file path or object identity).
     *
     * @return the tab identifier
     */
    public String getTabId() {
        return sourceFile != null ? sourceFile.getAbsolutePath() : "new-" + System.identityHashCode(this);
    }

    /**
     * Checks if this tab is editing a new (unsaved) file.
     *
     * @return true if this is a new file
     */
    public boolean isNewFile() {
        return sourceFile == null;
    }

    /**
     * Updates the tab title (e.g., after save-as).
     *
     * @param title the new title
     */
    protected void updateTitle(String title) {
        // Note: This doesn't update originalTitle as the file changed
        setText(isDirty() ? title + " *" : title);
    }

    /**
     * Requests focus on the editor content.
     */
    public abstract void requestEditorFocus();

    // ==================== Undo/Redo ====================

    /**
     * Undoes the last edit operation.
     * Default implementation does nothing - override in subclasses.
     */
    public void undo() {
        // Default: do nothing
        // Subclasses should override to provide undo functionality
        CodeArea codeArea = getPrimaryCodeArea();
        if (codeArea != null) {
            codeArea.undo();
        }
    }

    /**
     * Redoes the last undone operation.
     * Default implementation does nothing - override in subclasses.
     */
    public void redo() {
        // Default: do nothing
        // Subclasses should override to provide redo functionality
        CodeArea codeArea = getPrimaryCodeArea();
        if (codeArea != null) {
            codeArea.redo();
        }
    }

    // ==================== New Methods for XPath Panel Integration ====================

    /**
     * Gets the primary code area for XPath panel and other integrations.
     * <p>
     * Subclasses should return their main text editing code area.
     * For tabs with multiple views (text/graphic), this should return
     * the text editor's code area.
     *
     * @return the primary CodeArea, or null if not available
     */
    public CodeArea getPrimaryCodeArea() {
        // Default implementation returns null.
        // Subclasses should override to provide their code area.
        return null;
    }

    /**
     * Gets the XPath expression for the current cursor position.
     * <p>
     * This is useful for showing the user's current location in the
     * document structure. For XML documents, this returns the XPath
     * from root to the element at the cursor position.
     *
     * @return the XPath string, or null if not available
     */
    public String getCurrentXPath() {
        // Default implementation returns null.
        // Subclasses that support XPath tracking should override.
        return null;
    }

    /**
     * Checks if this tab supports XPath queries.
     * <p>
     * Most tabs (XML, XSD, XSLT, Schematron) support XPath as they
     * contain XML content. This method can be used to enable/disable
     * XPath panel features.
     *
     * @return true if XPath queries are supported
     */
    public boolean supportsXPath() {
        // All current tab types support XPath as they're XML-based
        return true;
    }

    /**
     * Gets the document encoding (e.g., UTF-8).
     *
     * @return the encoding string, defaults to UTF-8
     */
    public String getEncoding() {
        return "UTF-8";
    }

    /**
     * Gets the document's XML declaration version.
     *
     * @return the XML version (e.g., "1.0"), or null if not available
     */
    public String getXmlVersion() {
        String content = getEditorContent();
        if (content != null && content.contains("<?xml")) {
            int versionIdx = content.indexOf("version=\"");
            if (versionIdx > 0 && versionIdx < 100) {
                int endIdx = content.indexOf("\"", versionIdx + 9);
                if (endIdx > versionIdx) {
                    return content.substring(versionIdx + 9, endIdx);
                }
            }
        }
        return "1.0";
    }

    /**
     * Gets summary statistics about the document.
     * <p>
     * Returns a string like "Lines: 150, Characters: 5432".
     * Useful for status bar display.
     *
     * @return summary statistics string
     */
    public String getDocumentStats() {
        String content = getEditorContent();
        if (content == null || content.isEmpty()) {
            return "Empty document";
        }
        int lines = (int) content.lines().count();
        int chars = content.length();
        return String.format("Lines: %d, Characters: %d", lines, chars);
    }
}
