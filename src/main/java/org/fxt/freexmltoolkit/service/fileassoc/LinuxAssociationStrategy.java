package org.fxt.freexmltoolkit.service.fileassoc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.FileAssociationResult;
import org.fxt.freexmltoolkit.service.FileAssociationService.RegistrationState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Linux implementation: writes a per-user desktop entry to
 * {@code ~/.local/share/applications} and sets the default handler per MIME type via
 * {@code xdg-mime default} (which edits {@code ~/.config/mimeapps.list}) — entirely
 * per user, no root required.
 *
 * <p>Schematron files have no MIME type in the shared-mime-info database, so a small
 * per-user MIME package ({@code application/x-schematron+xml} with {@code *.sch} /
 * {@code *.schematron} globs) is installed via {@code xdg-mime install --mode user}.
 */
public class LinuxAssociationStrategy implements AssociationStrategy {

    private static final Logger logger = LogManager.getLogger(LinuxAssociationStrategy.class);

    public static final String DESKTOP_ID = "freexmltoolkit.desktop";
    public static final String SCHEMATRON_MIME = "application/x-schematron+xml";
    static final String PREVIOUS_DEFAULT_KEY_PREFIX = "fileAssociations.linux.previous.";
    static final String MIME_PACKAGE_FILE_NAME = "freexmltoolkit-schematron.xml";

    private final CommandRunner runner;
    private final Path launcher;
    private final Path userHome;
    private final KeyValueStore store;

    public LinuxAssociationStrategy(CommandRunner runner, Path launcher, Path userHome, KeyValueStore store) {
        this.runner = runner;
        this.launcher = launcher;
        this.userHome = userHome;
        this.store = store;
    }

    /**
     * Builds the per-user desktop entry content. The MimeType line always declares all
     * supported MIME types (declaring capability is harmless); which of them the app is
     * the <em>default</em> for is controlled separately via {@code xdg-mime default}. Pure.
     */
    static String buildDesktopEntry(Path launcher, Path iconPng, Collection<FileTypeDescriptor> allTypes) {
        Set<String> mimes = new LinkedHashSet<>();
        allTypes.forEach(t -> mimes.add(t.mimeType()));
        StringBuilder sb = new StringBuilder();
        sb.append("[Desktop Entry]\n")
                .append("Type=Application\n")
                .append("Name=FreeXmlToolkit\n")
                .append("Comment=Universal Toolkit for XML\n")
                .append("Exec=\"").append(launcher).append("\" %f\n");
        if (iconPng != null) {
            sb.append("Icon=").append(iconPng).append('\n');
        }
        sb.append("Terminal=false\n")
                .append("Categories=Development;\n")
                .append("MimeType=").append(String.join(";", mimes)).append(";\n");
        return sb.toString();
    }

    /**
     * Builds the shared-mime-info package declaring the Schematron MIME type. Pure.
     */
    static String buildSchematronMimeXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <mime-info xmlns="http://www.freedesktop.org/standards/shared-mime-info">
                    <mime-type type="application/x-schematron+xml">
                        <comment>Schematron schema</comment>
                        <sub-class-of type="application/xml"/>
                        <glob pattern="*.sch"/>
                        <glob pattern="*.schematron"/>
                    </mime-type>
                </mime-info>
                """;
    }

    /**
     * Removes {@code desktopId} from the default/added associations of the given MIME
     * types in a {@code mimeapps.list} file, dropping lines that become empty.
     * Unrelated sections and entries are preserved verbatim. Pure.
     */
    static String removeDefaults(String mimeappsContent, Set<String> mimes, String desktopId) {
        StringBuilder out = new StringBuilder();
        String section = "";
        for (String line : mimeappsContent.split("\\R", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                section = trimmed;
                out.append(line).append('\n');
                continue;
            }
            boolean relevantSection = section.equals("[Default Applications]")
                    || section.equals("[Added Associations]");
            int eq = line.indexOf('=');
            if (relevantSection && eq > 0 && mimes.contains(line.substring(0, eq).trim())) {
                String mime = line.substring(0, eq).trim();
                List<String> handlers = new ArrayList<>();
                for (String handler : line.substring(eq + 1).split(";")) {
                    if (!handler.isBlank() && !handler.trim().equals(desktopId)) {
                        handlers.add(handler.trim());
                    }
                }
                if (!handlers.isEmpty()) {
                    out.append(mime).append('=').append(String.join(";", handlers)).append(";\n");
                }
                // line dropped entirely when no other handler remains
            } else {
                out.append(line).append('\n');
            }
        }
        // split with -1 keeps a trailing empty element; avoid growing trailing newlines
        String result = out.toString();
        return result.endsWith("\n\n") ? result.substring(0, result.length() - 1) : result;
    }

    Path desktopFilePath() {
        return userHome.resolve(".local/share/applications").resolve(DESKTOP_ID);
    }

    /**
     * Icon shipped in the app-image ({@code lib/<launcher-name>.png} per jpackage layout),
     * or null when not present (IDE mode). Pure lookup.
     */
    Path findIconPng() {
        Path appDir = launcher.getParent() != null ? launcher.getParent().getParent() : null;
        if (appDir == null) {
            return null;
        }
        for (String candidate : List.of("lib/freexmltoolkit.png", "lib/FreeXmlToolkit.png")) {
            Path icon = appDir.resolve(candidate);
            if (Files.exists(icon)) {
                return icon;
            }
        }
        return null;
    }

    @Override
    public FileAssociationResult register(Collection<FileTypeDescriptor> types,
                                          Collection<FileTypeDescriptor> allTypes) {
        List<String> errors = new ArrayList<>();
        try {
            Path desktopFile = desktopFilePath();
            Files.createDirectories(desktopFile.getParent());
            Files.writeString(desktopFile, buildDesktopEntry(launcher, findIconPng(), allTypes));
        } catch (IOException e) {
            return FileAssociationResult.failure(
                    "Could not write the desktop entry: " + e.getMessage(), List.of(e.toString()));
        }

        if (types.stream().anyMatch(t -> SCHEMATRON_MIME.equals(t.mimeType()))) {
            installSchematronMimeType(errors);
        }

        for (String mime : mimesOf(types)) {
            recordPreviousDefault(mime);
            runCommand(List.of("xdg-mime", "default", DESKTOP_ID, mime), errors,
                    "Could not set default for " + mime);
        }

        // Best effort — some minimal systems lack this tool; associations still work.
        try {
            runner.run(List.of("update-desktop-database",
                    desktopFilePath().getParent().toString()));
        } catch (IOException e) {
            logger.debug("update-desktop-database not available", e);
        }

        if (!errors.isEmpty()) {
            return FileAssociationResult.failure(
                    "Registration finished with errors — see details.", errors);
        }
        return FileAssociationResult.ok("FreeXmlToolkit is now the default application for the selected file types.");
    }

    @Override
    public FileAssociationResult unregister(Collection<FileTypeDescriptor> types, boolean coversAllTypes) {
        List<String> errors = new ArrayList<>();
        Set<String> mimesWithoutPrevious = new LinkedHashSet<>();

        for (String mime : mimesOf(types)) {
            String previous = store.get(PREVIOUS_DEFAULT_KEY_PREFIX + mime);
            if (previous != null && !previous.isBlank() && !DESKTOP_ID.equals(previous)) {
                runCommand(List.of("xdg-mime", "default", previous, mime), errors,
                        "Could not restore previous default for " + mime);
                store.set(PREVIOUS_DEFAULT_KEY_PREFIX + mime, "");
            } else {
                mimesWithoutPrevious.add(mime);
            }
        }

        if (!mimesWithoutPrevious.isEmpty()) {
            rewriteMimeappsList(mimesWithoutPrevious, errors);
        }

        if (coversAllTypes) {
            try {
                Files.deleteIfExists(desktopFilePath());
            } catch (IOException e) {
                errors.add("Could not remove desktop entry: " + e.getMessage());
            }
            uninstallSchematronMimeType();
        }

        if (!errors.isEmpty()) {
            return FileAssociationResult.failure("Unregistration finished with errors — see details.", errors);
        }
        return FileAssociationResult.ok("File type registration removed.");
    }

    @Override
    public RegistrationState state(FileTypeDescriptor type) {
        try {
            CommandRunner.CommandResult result = runner.run(
                    List.of("xdg-mime", "query", "default", type.mimeType()));
            if (result.success() && result.stdout().trim().equals(DESKTOP_ID)) {
                return RegistrationState.DEFAULT;
            }
            return Files.exists(desktopFilePath())
                    ? RegistrationState.REGISTERED
                    : RegistrationState.NOT_REGISTERED;
        } catch (IOException e) {
            logger.warn("Could not query xdg-mime default for {}", type.mimeType(), e);
            return RegistrationState.UNKNOWN;
        }
    }

    private void installSchematronMimeType(List<String> errors) {
        try {
            Path mimeFile = Files.createTempDirectory("fxt-mime").resolve(MIME_PACKAGE_FILE_NAME);
            Files.writeString(mimeFile, buildSchematronMimeXml());
            runCommand(List.of("xdg-mime", "install", "--mode", "user", mimeFile.toString()),
                    errors, "Could not install the Schematron MIME type");
        } catch (IOException e) {
            errors.add("Could not install the Schematron MIME type: " + e.getMessage());
        }
    }

    private void uninstallSchematronMimeType() {
        // xdg-mime uninstall resolves the package by file name inside the user MIME dir.
        Path installed = userHome.resolve(".local/share/mime/packages").resolve(MIME_PACKAGE_FILE_NAME);
        if (!Files.exists(installed)) {
            return;
        }
        try {
            runner.run(List.of("xdg-mime", "uninstall", "--mode", "user", installed.toString()));
        } catch (IOException e) {
            logger.warn("Could not uninstall Schematron MIME type", e);
        }
    }

    private void recordPreviousDefault(String mime) {
        try {
            CommandRunner.CommandResult result = runner.run(List.of("xdg-mime", "query", "default", mime));
            String previous = result.stdout().trim();
            if (result.success() && !previous.isEmpty() && !DESKTOP_ID.equals(previous)) {
                store.set(PREVIOUS_DEFAULT_KEY_PREFIX + mime, previous);
            }
        } catch (IOException e) {
            logger.debug("Could not query previous default for {}", mime, e);
        }
    }

    private void rewriteMimeappsList(Set<String> mimes, List<String> errors) {
        Path mimeapps = userHome.resolve(".config/mimeapps.list");
        if (!Files.exists(mimeapps)) {
            return;
        }
        try {
            String rewritten = removeDefaults(Files.readString(mimeapps), mimes, DESKTOP_ID);
            Files.writeString(mimeapps, rewritten);
        } catch (IOException e) {
            errors.add("Could not update " + mimeapps + ": " + e.getMessage());
        }
    }

    private void runCommand(List<String> command, List<String> errors, String errorPrefix) {
        try {
            CommandRunner.CommandResult result = runner.run(command);
            if (!result.success()) {
                errors.add(errorPrefix + ": " + result.stderr().trim());
            }
        } catch (IOException e) {
            errors.add(errorPrefix + ": " + e.getMessage());
        }
    }

    private static Set<String> mimesOf(Collection<FileTypeDescriptor> types) {
        Set<String> mimes = new LinkedHashSet<>();
        types.forEach(t -> mimes.add(t.mimeType()));
        return mimes;
    }
}
