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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * macOS implementation: sets the per-user default handler via the Launch Services API
 * {@code LSSetDefaultRoleHandlerForContentType} — the same override Finder's
 * "Change All…" writes. No administrator rights required.
 *
 * <p>The API is reached without any native Java binding by running a small JXA
 * (JavaScript for Automation) script through {@code osascript -l JavaScript}, which is
 * available on every macOS installation. The script resolves each extension to its UTI
 * with {@code UTTypeCreatePreferredIdentifierForTag} (covering dynamic {@code dyn.*}
 * UTIs), records the previous handler and sets the new one.
 */
public class MacAssociationStrategy implements AssociationStrategy {

    private static final Logger logger = LogManager.getLogger(MacAssociationStrategy.class);

    static final String BUNDLE_ID = "org.fxt.freexmltoolkit";
    static final String PREVIOUS_HANDLER_KEY_PREFIX = "fileAssociations.mac.previous.";
    private static final String LSREGISTER = "/System/Library/Frameworks/CoreServices.framework"
            + "/Frameworks/LaunchServices.framework/Support/lsregister";

    private final CommandRunner runner;
    private final Path launcher;
    private final KeyValueStore store;

    public MacAssociationStrategy(CommandRunner runner, Path launcher, KeyValueStore store) {
        this.runner = runner;
        this.launcher = launcher;
        this.store = store;
    }

    /**
     * JXA script that sets the default handler per extension. Arguments are
     * {@code ext=bundleId} pairs; output is one {@code ext|uti|previousHandler|status}
     * line per argument. Pure.
     */
    static String buildSetHandlerScript() {
        return """
                ObjC.import('CoreServices');
                function run(argv) {
                    const out = [];
                    const kLSRolesAll = 0xFFFFFFFF;
                    for (const arg of argv) {
                        const sep = arg.indexOf('=');
                        const ext = arg.substring(0, sep);
                        const bundleId = arg.substring(sep + 1);
                        const uti = $.UTTypeCreatePreferredIdentifierForTag(
                                $.kUTTagClassFilenameExtension, ext, $());
                        const prevRef = $.LSCopyDefaultRoleHandlerForContentType(uti, kLSRolesAll);
                        const prev = prevRef ? ObjC.unwrap(prevRef) : '';
                        const status = $.LSSetDefaultRoleHandlerForContentType(uti, kLSRolesAll, bundleId);
                        out.push(ext + '|' + ObjC.unwrap(uti) + '|' + prev + '|' + status);
                    }
                    return out.join('\\n');
                }
                """;
    }

    /**
     * JXA script that queries the current default handler per extension. Arguments are
     * extensions; output is one {@code ext|handler} line per argument. Pure.
     */
    static String buildQueryHandlerScript() {
        return """
                ObjC.import('CoreServices');
                function run(argv) {
                    const out = [];
                    const kLSRolesAll = 0xFFFFFFFF;
                    for (const ext of argv) {
                        const uti = $.UTTypeCreatePreferredIdentifierForTag(
                                $.kUTTagClassFilenameExtension, ext, $());
                        const handler = $.LSCopyDefaultRoleHandlerForContentType(uti, kLSRolesAll);
                        out.push(ext + '|' + (handler ? ObjC.unwrap(handler) : ''));
                    }
                    return out.join('\\n');
                }
                """;
    }

    /**
     * Parses {@code ext|uti|previousHandler|status} lines. Pure.
     */
    static List<HandlerChange> parseSetHandlerOutput(String stdout) {
        List<HandlerChange> changes = new ArrayList<>();
        if (stdout == null) {
            return changes;
        }
        for (String line : stdout.split("\\R")) {
            String[] parts = line.split("\\|", -1);
            if (parts.length == 4 && !parts[0].isBlank()) {
                int status;
                try {
                    status = Integer.parseInt(parts[3].trim());
                } catch (NumberFormatException e) {
                    status = Integer.MIN_VALUE;
                }
                changes.add(new HandlerChange(parts[0].trim(), parts[1].trim(), parts[2].trim(), status));
            }
        }
        return changes;
    }

    /**
     * One extension's handler change as reported by the JXA script.
     */
    record HandlerChange(String extension, String uti, String previousHandler, int status) {
        boolean success() {
            return status == 0;
        }
    }

    @Override
    public FileAssociationResult register(Collection<FileTypeDescriptor> types,
                                          Collection<FileTypeDescriptor> allTypes) {
        Map<String, String> targets = new LinkedHashMap<>();
        for (FileTypeDescriptor type : types) {
            for (String ext : type.extensions()) {
                targets.put(ext, BUNDLE_ID);
            }
        }
        List<String> errors = setHandlers(targets, true);
        if (!errors.isEmpty()) {
            return FileAssociationResult.failure(
                    "Could not set FreeXmlToolkit as default for all types — see details.", errors);
        }
        return FileAssociationResult.ok("FreeXmlToolkit is now the default application for the selected file types.");
    }

    @Override
    public FileAssociationResult unregister(Collection<FileTypeDescriptor> types, boolean coversAllTypes) {
        Map<String, String> targets = new LinkedHashMap<>();
        List<String> withoutPrevious = new ArrayList<>();
        for (FileTypeDescriptor type : types) {
            for (String ext : type.extensions()) {
                String previous = store.get(PREVIOUS_HANDLER_KEY_PREFIX + ext);
                if (previous != null && !previous.isBlank() && !BUNDLE_ID.equals(previous)) {
                    targets.put(ext, previous);
                } else {
                    withoutPrevious.add("." + ext);
                }
            }
        }
        List<String> errors = targets.isEmpty() ? List.of() : setHandlers(targets, false);
        if (!errors.isEmpty()) {
            return FileAssociationResult.failure(
                    "Could not restore the previous default application for all types — see details.", errors);
        }
        // Forget restored handlers
        targets.keySet().forEach(ext -> store.set(PREVIOUS_HANDLER_KEY_PREFIX + ext, ""));
        if (!withoutPrevious.isEmpty()) {
            return FileAssociationResult.ok("Done. macOS keeps FreeXmlToolkit as handler for "
                    + String.join(", ", withoutPrevious)
                    + " because no previous default is known — use Finder > Get Info > \"Open with\" "
                    + "+ \"Change All…\" to pick another application.");
        }
        return FileAssociationResult.ok("Previous default applications restored.");
    }

    @Override
    public RegistrationState state(FileTypeDescriptor type) {
        try {
            Path script = writeScript(buildQueryHandlerScript());
            try {
                List<String> command = new ArrayList<>(List.of("osascript", "-l", "JavaScript",
                        script.toString()));
                command.addAll(type.extensions());
                CommandRunner.CommandResult result = runner.run(command);
                if (!result.success()) {
                    return RegistrationState.UNKNOWN;
                }
                boolean allDefault = true;
                for (String line : result.stdout().split("\\R")) {
                    String[] parts = line.split("\\|", -1);
                    if (parts.length == 2 && !parts[0].isBlank()) {
                        allDefault &= BUNDLE_ID.equals(parts[1].trim());
                    }
                }
                return allDefault ? RegistrationState.DEFAULT : RegistrationState.REGISTERED;
            } finally {
                Files.deleteIfExists(script);
            }
        } catch (IOException e) {
            logger.warn("Could not query default handler state for {}", type.id(), e);
            return RegistrationState.UNKNOWN;
        }
    }

    /**
     * Runs the set-handler script for the given ext→bundleId map; records previous
     * handlers when {@code recordPrevious} is set. Returns per-extension errors.
     */
    private List<String> setHandlers(Map<String, String> targets, boolean recordPrevious) {
        List<String> errors = new ArrayList<>();
        try {
            Path script = writeScript(buildSetHandlerScript());
            try {
                List<String> command = new ArrayList<>(List.of("osascript", "-l", "JavaScript",
                        script.toString()));
                targets.forEach((ext, bundleId) -> command.add(ext + "=" + bundleId));

                CommandRunner.CommandResult result = runner.run(command);
                if (!result.success()) {
                    // The app bundle may not be known to Launch Services yet — force
                    // registration once and retry.
                    forceLaunchServicesRegistration();
                    result = runner.run(command);
                }
                if (!result.success()) {
                    errors.add("osascript failed: " + result.stderr().trim());
                    return errors;
                }
                for (HandlerChange change : parseSetHandlerOutput(result.stdout())) {
                    if (!change.success()) {
                        errors.add("Could not set default for ." + change.extension()
                                + " (Launch Services status " + change.status() + ")");
                    } else if (recordPrevious && !change.previousHandler().isBlank()
                            && !BUNDLE_ID.equals(change.previousHandler())) {
                        store.set(PREVIOUS_HANDLER_KEY_PREFIX + change.extension(), change.previousHandler());
                    }
                }
            } finally {
                Files.deleteIfExists(script);
            }
        } catch (IOException e) {
            errors.add("Could not run osascript: " + e.getMessage());
        }
        return errors;
    }

    private void forceLaunchServicesRegistration() {
        Path bundle = appBundle();
        if (bundle == null) {
            return;
        }
        try {
            runner.run(List.of(LSREGISTER, "-f", bundle.toString()));
        } catch (IOException e) {
            logger.warn("lsregister failed", e);
        }
    }

    /**
     * Resolves the .app bundle root from the launcher path
     * ({@code …/FreeXmlToolkit.app/Contents/MacOS/FreeXmlToolkit}). Pure.
     */
    Path appBundle() {
        Path p = launcher;
        while (p != null && !p.toString().endsWith(".app")) {
            p = p.getParent();
        }
        return p;
    }

    private Path writeScript(String content) throws IOException {
        Path script = Files.createTempFile("fxt-file-assoc", ".js");
        Files.writeString(script, content);
        return script;
    }
}
