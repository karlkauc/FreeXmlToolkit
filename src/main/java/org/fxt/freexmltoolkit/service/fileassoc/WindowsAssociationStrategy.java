package org.fxt.freexmltoolkit.service.fileassoc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.FileAssociationResult;
import org.fxt.freexmltoolkit.service.FileAssociationService.RegistrationState;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Windows implementation: registers ProgIds and application capabilities under
 * {@code HKCU\Software\Classes} via {@code reg.exe} — per user, no administrator rights.
 *
 * <p>Windows 10/11 protects the actual per-extension default (the {@code UserChoice}
 * key) with a signed hash, so no application can set itself as default silently.
 * The sanctioned flow, implemented here, is to register the app's capabilities and then
 * open the Windows Settings "Default apps" page where the user confirms with one click.
 */
public class WindowsAssociationStrategy implements AssociationStrategy {

    private static final Logger logger = LogManager.getLogger(WindowsAssociationStrategy.class);

    static final String APP_NAME = "FreeXmlToolkit";
    static final String APP_DESCRIPTION = "Universal Toolkit for XML";
    static final String CLASSES_KEY = "HKCU\\Software\\Classes";
    static final String CAPABILITIES_KEY = "HKCU\\Software\\" + APP_NAME + "\\Capabilities";
    static final String REGISTERED_APPLICATIONS_KEY = "HKCU\\Software\\RegisteredApplications";
    static final String SETTINGS_URI = "ms-settings:defaultapps?registeredAppUser=" + APP_NAME;
    static final String SETTINGS_URI_FALLBACK = "ms-settings:defaultapps";

    private final CommandRunner runner;
    private final Path launcher;

    public WindowsAssociationStrategy(CommandRunner runner, Path launcher) {
        this.runner = runner;
        this.launcher = launcher;
    }

    @Override
    public boolean opensSystemSettingsPage() {
        return true;
    }

    /**
     * ProgId used for one extension, e.g. {@code FreeXmlToolkit.xml}.
     */
    static String progId(String extension) {
        return APP_NAME + "." + extension;
    }

    /**
     * Builds all {@code reg add} commands needed to register the given types.
     * Pure — no OS interaction.
     */
    List<List<String>> buildRegisterCommands(Collection<FileTypeDescriptor> types) {
        List<List<String>> commands = new ArrayList<>();
        String launcherPath = launcher.toString();

        // Application capabilities (once) — makes the app selectable in Settings > Default apps
        commands.add(List.of("reg", "add", CAPABILITIES_KEY,
                "/v", "ApplicationName", "/d", APP_NAME, "/f"));
        commands.add(List.of("reg", "add", CAPABILITIES_KEY,
                "/v", "ApplicationDescription", "/d", APP_DESCRIPTION, "/f"));
        commands.add(List.of("reg", "add", REGISTERED_APPLICATIONS_KEY,
                "/v", APP_NAME, "/d", "Software\\" + APP_NAME + "\\Capabilities", "/f"));

        for (FileTypeDescriptor type : types) {
            for (String ext : sorted(type.extensions())) {
                String progId = progId(ext);
                String progIdKey = CLASSES_KEY + "\\" + progId;
                commands.add(List.of("reg", "add", progIdKey,
                        "/ve", "/d", type.description() + " (" + APP_NAME + ")", "/f"));
                commands.add(List.of("reg", "add", progIdKey + "\\DefaultIcon",
                        "/ve", "/d", "\"" + launcherPath + "\",0", "/f"));
                commands.add(List.of("reg", "add", progIdKey + "\\shell\\open\\command",
                        "/ve", "/d", "\"" + launcherPath + "\" \"%1\"", "/f"));
                commands.add(List.of("reg", "add", CLASSES_KEY + "\\." + ext + "\\OpenWithProgids",
                        "/v", progId, "/t", "REG_SZ", "/d", "", "/f"));
                commands.add(List.of("reg", "add", CAPABILITIES_KEY + "\\FileAssociations",
                        "/v", "." + ext, "/d", progId, "/f"));
            }
        }
        return commands;
    }

    /**
     * Builds the {@code reg delete} commands for the given types. Pure.
     */
    List<List<String>> buildUnregisterCommands(Collection<FileTypeDescriptor> types) {
        List<List<String>> commands = new ArrayList<>();
        for (FileTypeDescriptor type : types) {
            for (String ext : sorted(type.extensions())) {
                String progId = progId(ext);
                commands.add(List.of("reg", "delete", CLASSES_KEY + "\\" + progId, "/f"));
                commands.add(List.of("reg", "delete", CLASSES_KEY + "\\." + ext + "\\OpenWithProgids",
                        "/v", progId, "/f"));
                commands.add(List.of("reg", "delete", CAPABILITIES_KEY + "\\FileAssociations",
                        "/v", "." + ext, "/f"));
            }
        }
        return commands;
    }

    /**
     * Commands removing the shared capability registration (used when the last type
     * is unregistered). Pure.
     */
    List<List<String>> buildRemoveCapabilitiesCommands() {
        return List.of(
                List.of("reg", "delete", "HKCU\\Software\\" + APP_NAME, "/f"),
                List.of("reg", "delete", REGISTERED_APPLICATIONS_KEY, "/v", APP_NAME, "/f"));
    }

    /**
     * Command opening the Windows "Default apps" settings page. Pure.
     */
    static List<String> buildOpenSettingsCommand(String uri) {
        // "start" is a cmd built-in; the empty string is the window title argument.
        return List.of("cmd", "/c", "start", "", uri);
    }

    /**
     * Extracts the data of a named REG_SZ value from {@code reg query} output.
     * Returns null when the value is not present. Pure.
     */
    static String parseRegQueryValue(String stdout, String valueName) {
        if (stdout == null) {
            return null;
        }
        for (String line : stdout.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(valueName + " ") || trimmed.startsWith(valueName + "\t")) {
                int typeIdx = trimmed.indexOf("REG_SZ");
                if (typeIdx >= 0) {
                    return trimmed.substring(typeIdx + "REG_SZ".length()).trim();
                }
            }
        }
        return null;
    }

    @Override
    public FileAssociationResult register(Collection<FileTypeDescriptor> types,
                                          Collection<FileTypeDescriptor> allTypes) {
        List<String> errors = runAll(buildRegisterCommands(types), false);
        boolean settingsOpened = openDefaultAppsSettings();

        if (!errors.isEmpty()) {
            return FileAssociationResult.failure(
                    "Registration finished with errors — see details.", errors);
        }
        String message = settingsOpened
                ? "Registered. Windows Settings has been opened — please select FreeXmlToolkit "
                + "as the default app for the desired file types."
                : "Registered. Please open Windows Settings > Apps > Default apps and select "
                + "FreeXmlToolkit for the desired file types.";
        return new FileAssociationResult(true, message, settingsOpened, List.of());
    }

    @Override
    public FileAssociationResult unregister(Collection<FileTypeDescriptor> types, boolean coversAllTypes) {
        // Individual deletes may fail simply because the key never existed — tolerate that.
        List<String> errors = runAll(buildUnregisterCommands(types), true);
        if (coversAllTypes || !hasRemainingAssociations()) {
            runAll(buildRemoveCapabilitiesCommands(), true);
        }
        if (!errors.isEmpty()) {
            return FileAssociationResult.failure("Unregistration finished with errors — see details.", errors);
        }
        return FileAssociationResult.ok("File type registration removed.");
    }

    @Override
    public RegistrationState state(FileTypeDescriptor type) {
        try {
            for (String ext : type.extensions()) {
                CommandRunner.CommandResult result = runner.run(List.of(
                        "reg", "query", CAPABILITIES_KEY + "\\FileAssociations", "/v", "." + ext));
                if (!result.success()) {
                    return RegistrationState.NOT_REGISTERED;
                }
            }
            // Whether we are the actual default is hidden behind the UserChoice hash —
            // REGISTERED is the strongest state we can reliably report.
            return RegistrationState.REGISTERED;
        } catch (IOException e) {
            logger.warn("Could not query registration state for {}", type.id(), e);
            return RegistrationState.UNKNOWN;
        }
    }

    private boolean hasRemainingAssociations() {
        try {
            CommandRunner.CommandResult result = runner.run(List.of(
                    "reg", "query", CAPABILITIES_KEY + "\\FileAssociations"));
            return result.success() && result.stdout().contains("REG_SZ");
        } catch (IOException e) {
            return true; // assume something remains, keep capabilities
        }
    }

    private boolean openDefaultAppsSettings() {
        for (String uri : List.of(SETTINGS_URI, SETTINGS_URI_FALLBACK)) {
            try {
                if (runner.run(buildOpenSettingsCommand(uri)).success()) {
                    return true;
                }
            } catch (IOException e) {
                logger.warn("Could not open Windows default-apps settings via {}", uri, e);
            }
        }
        return false;
    }

    private List<String> runAll(List<List<String>> commands, boolean ignoreFailures) {
        List<String> errors = new ArrayList<>();
        for (List<String> command : commands) {
            try {
                CommandRunner.CommandResult result = runner.run(command);
                if (!result.success() && !ignoreFailures) {
                    errors.add(String.join(" ", command) + " failed: " + result.stderr().trim());
                }
            } catch (IOException e) {
                if (!ignoreFailures) {
                    errors.add(String.join(" ", command) + " failed: " + e.getMessage());
                }
            }
        }
        return errors;
    }

    private static List<String> sorted(Collection<String> values) {
        return values.stream().sorted().toList();
    }
}
