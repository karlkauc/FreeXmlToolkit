package org.fxt.freexmltoolkit.controls.shell.schema.overview;

import java.nio.file.Path;

import javafx.stage.Window;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdCommand;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchemaReference;

/**
 * What the {@link SchemaOverviewView} needs from the editor tab that shows it.
 *
 * @since 2.6
 */
public interface SchemaOverviewHost {

    /**
     * Runs a command on the document's shared undo stack and writes the model back to the
     * text.
     *
     * @return true when the command ran
     */
    boolean execute(XsdCommand command);

    /** The overview's selection changed; the Inspector follows it. */
    void selectionChanged(XsdNode node);

    /** Opens the type editor tab of a named global type. */
    void openTypeEditor(String typeName);

    /** Switches to the Graphic view and selects the node there. */
    void showInGraphic(XsdNode node);

    /** Opens the file an import/include/redefine/override points to. */
    void openReferencedFile(XsdSchemaReference reference);

    /** @return the document's file, or null when unsaved */
    Path documentPath();

    /** @return the owner window for dialogs, may be null */
    Window window();
}
