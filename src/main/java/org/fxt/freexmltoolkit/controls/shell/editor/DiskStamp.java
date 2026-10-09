package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * What the editor last knew about a document's file on disk. Two stamps that differ mean the
 * file was changed (or deleted, or recreated) by something other than the editor's own save.
 *
 * @param exists             whether the file exists
 * @param lastModifiedMillis the modification time in epoch milliseconds (0 when missing)
 * @param size               the size in bytes (0 when missing)
 */
public record DiskStamp(boolean exists, long lastModifiedMillis, long size) {

    /** The stamp of a file that does not exist. */
    public static final DiskStamp MISSING = new DiskStamp(false, 0, 0);

    /**
     * Reads the current stamp of {@code path}. Never throws.
     *
     * @return the stamp, {@link #MISSING} when the file does not exist, or {@code null} when the
     *         state could not be determined (e.g. a network drive that is temporarily
     *         unreachable) - callers must not mistake that for a deletion
     */
    public static DiskStamp of(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return new DiskStamp(true, attrs.lastModifiedTime().toMillis(), attrs.size());
        } catch (NoSuchFileException | NotDirectoryException e) {
            return MISSING;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
