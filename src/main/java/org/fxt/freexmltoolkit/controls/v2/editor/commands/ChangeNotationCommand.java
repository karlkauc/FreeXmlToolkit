package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNotation;

/**
 * Changes the {@code public} and {@code system} identifiers of an xs:notation.
 *
 * @since 2.6
 */
public class ChangeNotationCommand implements XsdCommand {

    private final XsdNotation notation;
    private final String oldPublic;
    private final String oldSystem;
    private final String newPublic;
    private final String newSystem;

    public ChangeNotationCommand(XsdNotation notation, String publicId, String systemId) {
        if (notation == null) {
            throw new IllegalArgumentException("Notation is required");
        }
        this.notation = notation;
        this.oldPublic = notation.getPublicId();
        this.oldSystem = notation.getSystemId();
        this.newPublic = publicId;
        this.newSystem = systemId;
    }

    /** @return the rule check: a notation needs a public or a system identifier */
    public EditCheck check() {
        boolean noPublic = newPublic == null || newPublic.isBlank();
        boolean noSystem = newSystem == null || newSystem.isBlank();
        return noPublic && noSystem
                ? EditCheck.blocked("A notation needs a public or a system identifier.")
                : EditCheck.allowed();
    }

    @Override
    public boolean execute() {
        if (!check().isAllowed()) {
            return false;
        }
        notation.setPublicId(newPublic);
        notation.setSystemId(newSystem);
        return true;
    }

    @Override
    public boolean undo() {
        notation.setPublicId(oldPublic);
        notation.setSystemId(oldSystem);
        return true;
    }

    @Override
    public String getDescription() {
        return "Edit notation '" + notation.getName() + "'";
    }
}
