package org.fxt.freexmltoolkit.plugin;

import java.nio.file.Path;

/** Told when a document has been opened in the editor (on the JavaFX thread). */
@FunctionalInterface
public interface DocumentListener {

    /**
     * @param path    the file, or {@code null} for an unsaved document
     * @param content the text as loaded
     */
    void documentOpened(PluginUi ui, Path path, String content);
}
