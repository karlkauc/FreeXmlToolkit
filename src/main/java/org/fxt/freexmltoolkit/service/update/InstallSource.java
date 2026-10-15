package org.fxt.freexmltoolkit.service.update;

/**
 * How the running FreeXmlToolkit installation was obtained.
 *
 * <p>Installations owned by a package manager must not overwrite themselves via the
 * built-in auto-updater: the package manager would lose track of the installed version,
 * and the install location is often read-only (Flatpak) or owned by another user. For
 * those sources the app only tells the user which command performs the upgrade.
 */
public enum InstallSource {

    /** Installed from Flathub; updates come through Flatpak / the software center. */
    FLATPAK("flatpak", "Flatpak", "flatpak update io.github.karlkauc.FreeXmlToolkit"),
    /** Installed from a Scoop bucket. */
    SCOOP("scoop", "Scoop", "scoop update freexmltoolkit"),
    /** Installed as a Homebrew cask. */
    HOMEBREW("homebrew", "Homebrew", "brew upgrade --cask freexmltoolkit"),
    /** Installed (or tracked) by the Windows Package Manager. */
    WINGET("winget", "winget", "winget upgrade --id KarlKauc.FreeXmlToolkit --exact"),
    /** Downloaded directly (installer or app-image ZIP); the built-in updater applies. */
    STANDALONE("standalone", "direct download", null);

    private final String id;
    private final String displayName;
    private final String upgradeCommand;

    InstallSource(String id, String displayName, String upgradeCommand) {
        this.id = id;
        this.displayName = displayName;
        this.upgradeCommand = upgradeCommand;
    }

    /** Stable lower-case id, as used by the marker file and {@code FXT_INSTALL_SOURCE}. */
    public String id() {
        return id;
    }

    /** Human-readable name of the package manager. */
    public String displayName() {
        return displayName;
    }

    /** Command that upgrades this installation, or {@code null} for {@link #STANDALONE}. */
    public String upgradeCommand() {
        return upgradeCommand;
    }

    /** True when a package manager owns the installation and self-update must stay off. */
    public boolean isManaged() {
        return this != STANDALONE;
    }

    /**
     * True when the app should not even look for updates itself. Flathub delivers updates
     * through the software center, so an extra in-app notification only adds noise.
     */
    public boolean suppressesUpdateCheck() {
        return this == FLATPAK;
    }

    /** Resolves an id (case-insensitive); {@code null} when it matches no source. */
    public static InstallSource fromId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        for (InstallSource source : values()) {
            if (source.id.equalsIgnoreCase(trimmed)) {
                return source;
            }
        }
        return null;
    }
}
