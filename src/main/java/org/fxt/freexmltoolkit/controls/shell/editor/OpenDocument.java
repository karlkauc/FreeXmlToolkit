package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Path;
import java.util.Objects;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Per-tab model of the Unified editor host: the file (or untitled placeholder),
 * its derived {@link EditorFileType}, an observable display name, and an
 * observable dirty flag. The editor content itself lives in the editor widget;
 * this model carries only the metadata the host/inspector need.
 */
public class OpenDocument {

    private Path path; // null = untitled
    private final StringProperty displayName = new SimpleStringProperty();
    private final BooleanProperty dirty = new SimpleBooleanProperty(false);

    private OpenDocument(Path path, String displayName) {
        this.path = path;
        this.displayName.set(displayName);
    }

    /** Creates a document backed by an existing/target file path. */
    public static OpenDocument forPath(Path path) {
        Objects.requireNonNull(path, "path");
        return new OpenDocument(path, fileName(path));
    }

    /** Creates an untitled document not yet associated with a file. */
    public static OpenDocument untitled(String displayName, EditorFileType type) {
        OpenDocument doc = new OpenDocument(null, displayName);
        doc.untitledType = Objects.requireNonNull(type, "type");
        return doc;
    }

    private EditorFileType untitledType = EditorFileType.OTHER;

    /** @return the file path, or {@code null} for an untitled document. */
    public Path getPath() {
        return path;
    }

    /** @return {@code true} if this document has no associated file yet. */
    public boolean isUntitled() {
        return path == null;
    }

    /**
     * Associates the document with a (new) file path, e.g. after Save As, and
     * updates the derived display name.
     */
    public void setPath(Path path) {
        this.path = Objects.requireNonNull(path, "path");
        this.displayName.set(fileName(path));
    }

    /** @return the file type, derived from the path (or the untitled type). */
    public EditorFileType getFileType() {
        return path != null ? EditorFileType.fromFileName(fileName(path)) : untitledType;
    }

    public String getDisplayName() {
        return displayName.get();
    }

    public StringProperty displayNameProperty() {
        return displayName;
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public void setDirty(boolean value) {
        dirty.set(value);
    }

    public BooleanProperty dirtyProperty() {
        return dirty;
    }

    private static String fileName(Path path) {
        Path name = path.getFileName();
        return name != null ? name.toString() : path.toString();
    }
}
