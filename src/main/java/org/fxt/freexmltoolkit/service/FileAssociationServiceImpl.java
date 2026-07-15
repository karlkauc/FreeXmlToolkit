package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.FileAssociationResult;
import org.fxt.freexmltoolkit.domain.UnifiedEditorFileType;
import org.fxt.freexmltoolkit.service.fileassoc.AssociationStrategy;
import org.fxt.freexmltoolkit.service.fileassoc.CommandRunner;
import org.fxt.freexmltoolkit.service.fileassoc.FileTypeDescriptor;
import org.fxt.freexmltoolkit.service.fileassoc.KeyValueStore;
import org.fxt.freexmltoolkit.service.fileassoc.LinuxAssociationStrategy;
import org.fxt.freexmltoolkit.service.fileassoc.MacAssociationStrategy;
import org.fxt.freexmltoolkit.service.fileassoc.ProcessCommandRunner;
import org.fxt.freexmltoolkit.service.fileassoc.WindowsAssociationStrategy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default {@link FileAssociationService} implementation. Selects the platform strategy
 * once at construction and maps {@link UnifiedEditorFileType}s to the fixed set of
 * registrable extensions.
 */
public class FileAssociationServiceImpl implements FileAssociationService {

    private static final Logger logger = LogManager.getLogger(FileAssociationServiceImpl.class);

    private static volatile FileAssociationServiceImpl instance;

    /**
     * Registrable extensions per type. Deliberately excludes {@code jsonc}/{@code json5}
     * (no MIME types in the shared databases) — only mainstream extensions are offered.
     */
    private static final Map<UnifiedEditorFileType, FileTypeDescriptor> DESCRIPTORS =
            new EnumMap<>(Map.of(
                    UnifiedEditorFileType.XML,
                    new FileTypeDescriptor("XML", "XML Document", Set.of("xml"), "application/xml"),
                    UnifiedEditorFileType.XSD,
                    new FileTypeDescriptor("XSD", "XML Schema Definition", Set.of("xsd"), "application/xml"),
                    UnifiedEditorFileType.XSLT,
                    new FileTypeDescriptor("XSLT", "XSLT Stylesheet", Set.of("xsl", "xslt"), "application/xslt+xml"),
                    UnifiedEditorFileType.SCHEMATRON,
                    new FileTypeDescriptor("SCHEMATRON", "Schematron Schema", Set.of("sch", "schematron"),
                            LinuxAssociationStrategy.SCHEMATRON_MIME),
                    UnifiedEditorFileType.JSON,
                    new FileTypeDescriptor("JSON", "JSON Document", Set.of("json"), "application/json")));

    private final Path launcher;
    private final AssociationStrategy strategy;

    public static FileAssociationServiceImpl getInstance() {
        if (instance == null) {
            synchronized (FileAssociationServiceImpl.class) {
                if (instance == null) {
                    instance = new FileAssociationServiceImpl(
                            new ProcessCommandRunner(),
                            ApplicationLauncherLocator.getApplicationLauncher(),
                            Path.of(System.getProperty("user.home")));
                }
            }
        }
        return instance;
    }

    private FileAssociationServiceImpl(CommandRunner runner, Path launcher, Path userHome) {
        this(runner, launcher, userHome, propertiesBackedStore());
    }

    /**
     * Visible for tests: injects the command runner, paths and the previous-handler store.
     */
    FileAssociationServiceImpl(CommandRunner runner, Path launcher, Path userHome, KeyValueStore store) {
        this.launcher = launcher;
        if (ApplicationLauncherLocator.isWindows()) {
            this.strategy = new WindowsAssociationStrategy(runner, launcher);
        } else if (ApplicationLauncherLocator.isMacOS()) {
            this.strategy = new MacAssociationStrategy(runner, launcher, store);
        } else {
            this.strategy = new LinuxAssociationStrategy(runner, launcher, userHome, store);
        }
    }

    private static KeyValueStore propertiesBackedStore() {
        return new KeyValueStore() {
            @Override
            public String get(String key) {
                try {
                    return ServiceRegistry.get(PropertiesService.class).get(key);
                } catch (Exception e) {
                    logger.warn("Could not read property {}", key, e);
                    return null;
                }
            }

            @Override
            public void set(String key, String value) {
                try {
                    ServiceRegistry.get(PropertiesService.class).set(key, value);
                } catch (Exception e) {
                    logger.warn("Could not store property {}", key, e);
                }
            }
        };
    }

    @Override
    public boolean isSupported() {
        if (!Files.isExecutable(launcher)) {
            return false;
        }
        // Launch Services can only target a real .app bundle
        return !ApplicationLauncherLocator.isMacOS() || launcher.toString().contains(".app/Contents/");
    }

    @Override
    public String getUnsupportedReason() {
        return "File associations can only be managed from the installed application "
                + "(no native launcher was found — running from the IDE or a build directory).";
    }

    @Override
    public RegistrationState getRegistrationState(String extension) {
        FileTypeDescriptor descriptor = DESCRIPTORS.values().stream()
                .filter(d -> d.extensions().contains(extension.toLowerCase()))
                .findFirst().orElse(null);
        if (descriptor == null || !isSupported()) {
            return RegistrationState.UNKNOWN;
        }
        return strategy.state(descriptor);
    }

    @Override
    public synchronized FileAssociationResult register(Set<UnifiedEditorFileType> types) {
        if (!isSupported()) {
            return FileAssociationResult.failure(getUnsupportedReason(), List.of());
        }
        if (types.isEmpty()) {
            return FileAssociationResult.failure("No file types selected.", List.of());
        }
        logger.info("Registering file associations for {}", types);
        return strategy.register(descriptorsFor(types), DESCRIPTORS.values());
    }

    @Override
    public synchronized FileAssociationResult unregister(Set<UnifiedEditorFileType> types) {
        if (!isSupported()) {
            return FileAssociationResult.failure(getUnsupportedReason(), List.of());
        }
        if (types.isEmpty()) {
            return FileAssociationResult.failure("No file types selected.", List.of());
        }
        logger.info("Unregistering file associations for {}", types);
        return strategy.unregister(descriptorsFor(types), types.containsAll(DESCRIPTORS.keySet()));
    }

    @Override
    public boolean opensSystemSettingsPage() {
        return strategy.opensSystemSettingsPage();
    }

    @Override
    public Set<String> extensionsFor(UnifiedEditorFileType type) {
        FileTypeDescriptor descriptor = DESCRIPTORS.get(type);
        return descriptor == null ? Set.of() : descriptor.extensions();
    }

    private static List<FileTypeDescriptor> descriptorsFor(Set<UnifiedEditorFileType> types) {
        List<FileTypeDescriptor> descriptors = new ArrayList<>();
        for (UnifiedEditorFileType type : types) {
            FileTypeDescriptor descriptor = DESCRIPTORS.get(type);
            if (descriptor != null) {
                descriptors.add(descriptor);
            }
        }
        return descriptors;
    }
}
