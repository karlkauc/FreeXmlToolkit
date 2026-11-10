package org.fxt.freexmltoolkit.plugin;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

import org.fxt.freexmltoolkit.controls.theme.Workflow;

/**
 * A small mark in the status bar for the active document, so the user sees that a plugin has
 * recognised it (for example "FundsXML": its checks run with every validation). Asked on the JavaFX
 * thread whenever another document becomes active, a document has been loaded or the plugins'
 * settings changed — it must be fast (look at the text, no I/O).
 */
@FunctionalInterface
public interface DocumentBadge {

    /**
     * @param path    the file, or {@code null} for an unsaved document
     * @param content the document's text
     * @return the badge to show, or {@code null} when the plugin does not handle this document
     */
    Badge badgeFor(Path path, String content);

    /**
     * What the status bar shows.
     *
     * @param text     the short label ("FundsXML")
     * @param icon     a {@code bi-*} icon literal, or {@code null}
     * @param tooltip  what recognising the document means (which checks or tools apply)
     * @param workflow the colour family of the chip
     * @param onClick  what a click does (for example open the plugin's activity), or {@code null}
     */
    record Badge(String text, String icon, String tooltip, Workflow workflow, Consumer<PluginUi> onClick) {
        public Badge {
            Objects.requireNonNull(text, "text");
            workflow = workflow == null ? Workflow.NEUTRAL : workflow;
        }
    }
}
