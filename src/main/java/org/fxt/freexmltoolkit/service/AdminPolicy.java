package org.fxt.freexmltoolkit.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Machine-wide settings an administrator sets for every user of a computer.
 *
 * <p>Two optional files in one admin-only folder:
 * <ul>
 *   <li>{@value #POLICY_FILE_NAME}: <b>locked</b> settings. Each key overrides the user's own
 *       setting, cannot be changed in the application and is shown as managed in Settings.</li>
 *   <li>{@value #DEFAULTS_FILE_NAME}: <b>defaults</b>. Each key is used until the user changes it.</li>
 * </ul>
 * Keys are the ones of {@code FreeXmlToolkit.properties}. Keys starting with {@code fxt.} are
 * Java system properties (for example {@code fxt.telemetry.disabled},
 * {@code fxt.schema.namespaceFallback}); they are set at startup ({@link #applySystemProperties()}),
 * from the policy always, from the defaults only when not given on the command line. The policy-only
 * key {@value #KEY_UPDATES_ALLOWED}{@code =false} disables update checks and installs altogether.
 *
 * <p>The folder is {@code %ProgramData%\FreeXmlToolkit} on Windows,
 * {@code /Library/Application Support/FreeXmlToolkit} on macOS and {@code /etc/freexmltoolkit}
 * elsewhere. {@code -D}{@value #PROP_FILE} or the environment variable {@value #ENV_FILE} name a
 * policy file elsewhere (the defaults file is then looked for next to it). Documented in
 * {@code docs/administration.md}.
 */
public final class AdminPolicy {

    private static final Logger logger = LogManager.getLogger(AdminPolicy.class);

    /** System property naming the policy file. */
    public static final String PROP_FILE = "fxt.policy.file";
    /** Environment variable naming the policy file. */
    public static final String ENV_FILE = "FXT_POLICY_FILE";
    /** File name of the locked settings. */
    public static final String POLICY_FILE_NAME = "policy.properties";
    /** File name of the default settings. */
    public static final String DEFAULTS_FILE_NAME = "defaults.properties";
    /** Policy key: {@code false} disables the update check, "Check for Updates" and installing updates. */
    public static final String KEY_UPDATES_ALLOWED = "update.allowed";
    /** Policy key: {@code false} loads no plugins from the plugins folder (built-in ones still load). */
    public static final String KEY_EXTERNAL_PLUGINS_ALLOWED = "plugins.external.allowed";
    /** {@code false} disables XML encryption and decryption (page, editor and command line). */
    public static final String KEY_XMLENC_ALLOWED = "xmlenc.allowed";
    /** {@code false} blocks the legacy XML encryption algorithms (RSA-1_5, TripleDES, CBC, DH). */
    public static final String KEY_XMLENC_LEGACY_ALLOWED = "xmlenc.legacyAlgorithms.allowed";
    /** {@code false} disables external XSL-FO processors: only the bundled Apache FOP renders PDFs. */
    public static final String KEY_FOP_EXTERNAL_ALLOWED = "fop.external.allowed";
    /** Settings key of the automatic update check (see {@link PropertiesService#isUpdateCheckEnabled()}). */
    static final String KEY_UPDATE_CHECK = "update.check.enabled";
    /** Prefix of keys that are Java system properties rather than settings. */
    static final String SYSTEM_PREFIX = "fxt.";

    private static final AdminPolicy NONE = new AdminPolicy(null, new Properties(), new Properties());
    private static volatile AdminPolicy current;

    private final Path policyFile;
    private final Properties locked;
    private final Properties defaults;

    private AdminPolicy(Path policyFile, Properties locked, Properties defaults) {
        this.policyFile = policyFile;
        this.locked = locked;
        this.defaults = defaults;
        if (!updatesAllowed() && !locked.containsKey(KEY_UPDATE_CHECK)) {
            locked.setProperty(KEY_UPDATE_CHECK, "false");
        }
    }

    /** The policy of this computer, read once. */
    public static AdminPolicy current() {
        AdminPolicy policy = current;
        if (policy == null) {
            synchronized (AdminPolicy.class) {
                policy = current;
                if (policy == null) {
                    policy = load(System.getenv(), System.getProperties(), System.getProperty("os.name", ""));
                    current = policy;
                }
            }
        }
        return policy;
    }

    /** Reads the policy and sets its {@code fxt.*} system properties; call first thing at startup. */
    public static AdminPolicy install() {
        AdminPolicy policy = current();
        policy.applySystemProperties();
        if (policy.isActive()) {
            logger.info("Administrator settings from {}: {} locked, {} defaults", policy.folder(),
                    policy.locked.size(), policy.defaults.size());
        }
        return policy;
    }

    /** Replaces the current policy (tests); {@code null} reads it again on the next access. */
    static void setCurrentForTests(AdminPolicy policy) {
        current = policy;
    }

    /**
     * Reads the policy for the given environment.
     *
     * @param environment      environment variables
     * @param systemProperties system properties
     * @param osName           {@code os.name}
     */
    static AdminPolicy load(Map<String, String> environment, Properties systemProperties, String osName) {
        Path policyFile = policyFile(environment, systemProperties, osName);
        if (policyFile == null) {
            return NONE;
        }
        Properties locked = read(policyFile);
        Path parent = policyFile.toAbsolutePath().getParent();
        Properties defaults = parent != null ? read(parent.resolve(DEFAULTS_FILE_NAME)) : new Properties();
        return new AdminPolicy(policyFile, locked, defaults);
    }

    /** A policy from in-memory settings (tests). */
    static AdminPolicy of(Path policyFile, Properties locked, Properties defaults) {
        return new AdminPolicy(policyFile, copy(locked), copy(defaults));
    }

    /** The policy file to read: the explicit one, else the platform's folder. */
    static Path policyFile(Map<String, String> environment, Properties systemProperties, String osName) {
        String explicit = systemProperties.getProperty(PROP_FILE);
        if (explicit == null || explicit.isBlank()) {
            explicit = environment.get(ENV_FILE);
        }
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit.trim());
        }
        Path folder = defaultFolder(environment, osName);
        return folder != null ? folder.resolve(POLICY_FILE_NAME) : null;
    }

    /** The platform's admin folder. */
    static Path defaultFolder(Map<String, String> environment, String osName) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                String programData = environment.get("ProgramData");
                if (programData == null || programData.isBlank()) {
                    programData = environment.getOrDefault("ALLUSERSPROFILE", "C:\\ProgramData");
                }
                return Path.of(programData, "FreeXmlToolkit");
            }
            if (os.contains("mac")) {
                return Path.of("/Library/Application Support/FreeXmlToolkit");
            }
            return Path.of("/etc/freexmltoolkit");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Properties read(Path file) {
        Properties properties = new Properties();
        if (!Files.isRegularFile(file)) {
            return properties;
        }
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
            // values are taken as written, without surrounding blanks
            for (String key : properties.stringPropertyNames()) {
                properties.setProperty(key, properties.getProperty(key).trim());
            }
        } catch (IOException | RuntimeException e) {
            logger.warn("Cannot read the administrator settings {}: {}", file, e.toString());
        }
        return properties;
    }

    private static Properties copy(Properties source) {
        Properties copy = new Properties();
        if (source != null) {
            copy.putAll(source);
        }
        return copy;
    }

    // ---- queries ------------------------------------------------------------------------------

    /** @return whether a policy or defaults file was found */
    public boolean isActive() {
        return !locked.isEmpty() || !defaults.isEmpty();
    }

    /** @return the folder the files were read from, or {@code null} */
    public Path folder() {
        return policyFile != null ? policyFile.toAbsolutePath().getParent() : null;
    }

    /** @return the policy file that was looked for, or {@code null} */
    public Path policyFile() {
        return policyFile;
    }

    /** @return whether the administrator locked this setting */
    public boolean isLocked(String key) {
        return key != null && locked.containsKey(key);
    }

    /** @return whether any of the settings is locked */
    public boolean isAnyLocked(String... keys) {
        for (String key : keys) {
            if (isLocked(key)) {
                return true;
            }
        }
        return false;
    }

    /** @return the locked value, or {@code null} when the setting is not locked */
    public String lockedValue(String key) {
        return key == null ? null : locked.getProperty(key);
    }

    /** @return the administrator's default, or {@code null} */
    public String defaultValue(String key) {
        return key == null ? null : defaults.getProperty(key);
    }

    /** @return the keys that have an administrator default */
    public Set<String> defaultKeys() {
        return Collections.unmodifiableSet(new TreeSet<>(defaults.stringPropertyNames()));
    }

    /** @return the locked keys */
    public Set<String> lockedKeys() {
        return Collections.unmodifiableSet(new TreeSet<>(locked.stringPropertyNames()));
    }

    /** @return {@code false} when the administrator disabled updates ({@value #KEY_UPDATES_ALLOWED}=false) */
    public boolean updatesAllowed() {
        return !"false".equalsIgnoreCase(locked.getProperty(KEY_UPDATES_ALLOWED, "true"));
    }

    /** @return {@code false} when the administrator disabled plugins from the plugins folder */
    public boolean externalPluginsAllowed() {
        return !"false".equalsIgnoreCase(locked.getProperty(KEY_EXTERNAL_PLUGINS_ALLOWED, "true"));
    }

    /** @return {@code false} when the administrator disabled XML encryption */
    public boolean xmlEncryptionAllowed() {
        return !"false".equalsIgnoreCase(locked.getProperty(KEY_XMLENC_ALLOWED, "true"));
    }

    /** @return {@code false} when the administrator disabled external XSL-FO processors */
    public boolean externalFoProcessorsAllowed() {
        return !"false".equalsIgnoreCase(locked.getProperty(KEY_FOP_EXTERNAL_ALLOWED, "true"));
    }

    /** @return {@code false} when the administrator blocked the legacy XML encryption algorithms */
    public boolean legacyXmlEncryptionAllowed() {
        return !"false".equalsIgnoreCase(locked.getProperty(KEY_XMLENC_LEGACY_ALLOWED, "true"));
    }

    /**
     * Sets the {@code fxt.*} keys as system properties: the locked ones always, the defaults only
     * when the property is not set yet.
     */
    public void applySystemProperties() {
        for (String key : defaults.stringPropertyNames()) {
            if (key.startsWith(SYSTEM_PREFIX) && System.getProperty(key) == null) {
                System.setProperty(key, defaults.getProperty(key));
            }
        }
        for (String key : locked.stringPropertyNames()) {
            if (key.startsWith(SYSTEM_PREFIX)) {
                System.setProperty(key, locked.getProperty(key));
            }
        }
    }

    /**
     * The effective settings as plain {@link Properties}: the user's values, the administrator's
     * defaults for missing keys, the locked values over everything. A copy (also through
     * {@code putAll}) keeps them, unlike the lazy {@link #overlay()}.
     *
     * @param user the user's own settings
     */
    public Properties effective(Properties user) {
        Properties effective = new Properties();
        for (String key : defaults.stringPropertyNames()) {
            effective.setProperty(key, defaults.getProperty(key));
        }
        for (var entry : user.entrySet()) {   // the stored entries, not the overlay
            effective.put(entry.getKey(), entry.getValue());
        }
        for (String key : locked.stringPropertyNames()) {
            effective.setProperty(key, locked.getProperty(key));
        }
        return effective;
    }

    /**
     * The values of {@code saved} that belong into the user's file: a locked key keeps the user's
     * previous value (the policy's value is never stored), a default the user did not change is not
     * stored (so a changed default reaches the user).
     *
     * @param saved    effective settings as edited by a caller of {@code loadProperties()}
     * @param previous the user's settings before
     */
    public Properties userValues(Properties saved, Properties previous) {
        Properties user = overlay();
        for (var entry : saved.entrySet()) {
            String key = String.valueOf(entry.getKey());
            boolean userHad = previous.containsKey(key);
            if (isLocked(key)) {
                if (userHad) {
                    user.put(key, previous.get(key));
                }
            } else if (!userHad && entry.getValue().equals(defaults.getProperty(key))) {
                continue;
            } else {
                user.put(key, entry.getValue());
            }
        }
        return user;
    }

    /**
     * The settings as {@link Properties} with this policy laid over them: a locked key always
     * reads as the locked value and cannot be set; a missing key falls back to the default.
     */
    public Properties overlay() {
        return new PolicyProperties(this);
    }

    /**
     * {@link Properties} that read through an {@link AdminPolicy}. The stored entries stay the
     * user's own values (so saving never writes the administrator's values into the user's file);
     * only {@link #getProperty} and {@link #setProperty} apply the policy.
     */
    static final class PolicyProperties extends Properties {

        private final transient AdminPolicy policy;

        PolicyProperties(AdminPolicy policy) {
            this.policy = policy;
        }

        @Override
        public String getProperty(String key) {
            if (policy.isLocked(key)) {
                return policy.lockedValue(key);
            }
            String value = super.getProperty(key);
            return value != null ? value : policy.defaultValue(key);
        }

        @Override
        public String getProperty(String key, String defaultValue) {
            String value = getProperty(key);
            return value != null ? value : defaultValue;
        }

        @Override
        public synchronized Object setProperty(String key, String value) {
            if (policy.isLocked(key)) {
                return policy.lockedValue(key);
            }
            return super.setProperty(key, value);
        }

        @Override
        public synchronized boolean equals(Object o) {
            return super.equals(o);
        }

        @Override
        public synchronized int hashCode() {
            return super.hashCode();
        }
    }
}
