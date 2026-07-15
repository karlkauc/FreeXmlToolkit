package org.fxt.freexmltoolkit.service.fileassoc;

import org.fxt.freexmltoolkit.domain.FileAssociationResult;
import org.fxt.freexmltoolkit.service.FileAssociationService.RegistrationState;

import java.util.Collection;

/**
 * Platform-specific implementation of file-association registration.
 * Implementations build their OS commands/content in pure, unit-testable methods and
 * execute them through an injected {@link CommandRunner}.
 */
public interface AssociationStrategy {

    /**
     * Registers the application for the given types and makes it the default where possible.
     *
     * @param types      the file types to register
     * @param allTypes   every type the application supports (used for capability declarations)
     */
    FileAssociationResult register(Collection<FileTypeDescriptor> types, Collection<FileTypeDescriptor> allTypes);

    /**
     * Unregisters the given types, restoring recorded previous defaults where available.
     *
     * @param types             the file types to unregister
     * @param coversAllTypes    true when {@code types} covers every supported type — the
     *                          strategy may then remove shared artifacts (desktop entry,
     *                          capabilities key)
     */
    FileAssociationResult unregister(Collection<FileTypeDescriptor> types, boolean coversAllTypes);

    /**
     * Determines the registration state for the given type.
     */
    RegistrationState state(FileTypeDescriptor type);

    /**
     * @return true when {@code register} ends by opening the OS settings page
     */
    default boolean opensSystemSettingsPage() {
        return false;
    }
}
