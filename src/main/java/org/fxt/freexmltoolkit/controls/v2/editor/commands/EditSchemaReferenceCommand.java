package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import java.util.Objects;

import org.fxt.freexmltoolkit.controls.v2.model.XsdImport;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchemaReference;

/**
 * Changes the {@code namespace} (imports only) and {@code schemaLocation} of an
 * xs:import / xs:include / xs:redefine / xs:override.
 *
 * @since 2.6
 */
public class EditSchemaReferenceCommand implements XsdCommand {

    private final XsdSchemaReference reference;
    private final String oldNamespace;
    private final String oldLocation;
    private final String newNamespace;
    private final String newLocation;

    /**
     * @param reference    the directive
     * @param namespace    the new namespace (ignored unless an import; blank removes it)
     * @param location     the new schemaLocation (blank removes it on an import)
     */
    public EditSchemaReferenceCommand(XsdSchemaReference reference, String namespace, String location) {
        if (reference == null) {
            throw new IllegalArgumentException("Reference is required");
        }
        this.reference = reference;
        this.oldNamespace = reference instanceof XsdImport imp ? imp.getNamespace() : null;
        this.oldLocation = reference.getSchemaLocation();
        this.newNamespace = reference instanceof XsdImport ? blankToNull(namespace) : null;
        this.newLocation = blankToNull(location);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** @return the rule check: only an import may lack its schemaLocation */
    public EditCheck check() {
        if (newLocation == null && !(reference instanceof XsdImport)) {
            return EditCheck.blocked("xs:" + SchemaLayoutRules.directiveName(reference)
                    + " needs a schemaLocation.");
        }
        return EditCheck.allowed();
    }

    /** @return true when namespace or location change */
    public boolean changesSomething() {
        return !Objects.equals(oldLocation, newLocation)
                || (reference instanceof XsdImport && !Objects.equals(oldNamespace, newNamespace));
    }

    @Override
    public boolean execute() {
        if (!check().isAllowed()) {
            return false;
        }
        apply(newNamespace, newLocation);
        return true;
    }

    @Override
    public boolean undo() {
        apply(oldNamespace, oldLocation);
        return true;
    }

    private void apply(String namespace, String location) {
        if (reference instanceof XsdImport imp) {
            imp.setNamespace(namespace);
        }
        reference.setSchemaLocation(location);
    }

    @Override
    public String getDescription() {
        return "Edit xs:" + SchemaLayoutRules.directiveName(reference);
    }
}
