package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.FileAssociationResult;
import org.fxt.freexmltoolkit.domain.UnifiedEditorFileType;

import java.util.Set;

/**
 * Registers FreeXmlToolkit as handler (and, where the OS allows it, as the default
 * application) for the supported file types — per user, without administrator rights.
 *
 * <p>Platform behavior:
 * <ul>
 *   <li><b>Windows:</b> registers a ProgId and application capabilities under
 *       {@code HKCU\Software\Classes}; Windows does not allow setting the default
 *       silently, so the Windows "Default apps" settings page is opened for the user
 *       to confirm ({@link #opensSystemSettingsPage()} returns true).</li>
 *   <li><b>macOS:</b> sets the per-user default handler via Launch Services.</li>
 *   <li><b>Linux:</b> writes a per-user desktop entry and sets defaults via
 *       {@code xdg-mime}.</li>
 * </ul>
 *
 * <p>All operations may block on external processes — never call them on the FX thread.
 */
public interface FileAssociationService {

    /**
     * Registration state of a single file extension.
     */
    enum RegistrationState {
        /** The application is not registered for the extension. */
        NOT_REGISTERED,
        /** The application is registered as a handler, but not (verifiably) the default. */
        REGISTERED,
        /** The application is the default handler for the extension. */
        DEFAULT,
        /** The state could not be determined. */
        UNKNOWN
    }

    /**
     * @return true when file associations can be managed — i.e. the app runs from an
     * installed package with a native launcher (not from the IDE / gradle run)
     */
    boolean isSupported();

    /**
     * @return a user-facing English explanation when {@link #isSupported()} is false
     */
    String getUnsupportedReason();

    /**
     * Determines the registration state for a single extension (without leading dot).
     */
    RegistrationState getRegistrationState(String extension);

    /**
     * Registers the application for all extensions of the given file types and makes it
     * the default where the platform allows it.
     */
    FileAssociationResult register(Set<UnifiedEditorFileType> types);

    /**
     * Removes the registration for the given file types, restoring the previously
     * recorded default handler where one was recorded.
     */
    FileAssociationResult unregister(Set<UnifiedEditorFileType> types);

    /**
     * @return true when {@link #register(Set)} finishes by opening the OS settings page
     * where the user must confirm the default app (Windows)
     */
    boolean opensSystemSettingsPage();

    /**
     * @return the file extensions (without dot) registered for the given type
     */
    Set<String> extensionsFor(UnifiedEditorFileType type);
}
