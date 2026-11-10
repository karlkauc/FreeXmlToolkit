package org.fxt.freexmltoolkit.service.ai.provider;

import java.util.Optional;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.AdminPolicy;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;

/**
 * API keys of providers. Preferred: an environment variable named in the provider ({@code apiKeyEnv}). Otherwise a
 * key stored under {@code ai.provider.<id>.apiKey}, which {@code PropertiesServiceImpl} encrypts like the proxy
 * password ({@code PasswordEncryptionService.isPasswordProperty} covers the {@code .apiKey} suffix). A key in
 * {@code policy.properties} or {@code defaults.properties} is ignored: those files are readable by every user.
 * Keys never reach a log, the preview or an error message.
 */
public final class ProviderSecrets {

    private static final Logger logger = LogManager.getLogger(ProviderSecrets.class);

    private final Function<String, String> getter;
    private final BiConsumer<String, String> setter;
    private final Consumer<String> remover;
    private final Predicate<String> fromMachineFiles;
    private final Function<String, String> environment;

    public ProviderSecrets(Function<String, String> getter, BiConsumer<String, String> setter, Consumer<String> remover,
                           Predicate<String> fromMachineFiles, Function<String, String> environment) {
        this.getter = getter;
        this.setter = setter;
        this.remover = remover;
        this.fromMachineFiles = fromMachineFiles;
        this.environment = environment;
    }

    /** The secrets of this installation. */
    public static ProviderSecrets settings() {
        PropertiesService properties = PropertiesServiceImpl.getInstance();
        return new ProviderSecrets(properties::get, properties::set, key -> {
            Properties all = properties.loadProperties();
            if (all.remove(key) != null) {
                properties.saveProperties(all);
            }
        }, key -> AdminPolicy.current().isLocked(key) || AdminPolicy.current().defaultKeys().contains(key),
                System::getenv);
    }

    /** The setting that holds the stored key of a provider. */
    public static String keyName(String providerId) {
        return "ai.provider." + providerId + ".apiKey";
    }

    /** The key: from the named environment variable, else the stored one. */
    public Optional<String> apiKey(ProviderConfig provider) {
        if (provider.apiKeyEnv() != null) {
            String value = environment.apply(provider.apiKeyEnv());
            if (value != null && !value.isBlank()) {
                return Optional.of(value.strip());
            }
        }
        String name = keyName(provider.id());
        if (fromMachineFiles.test(name)) {
            logger.warn("Ignoring {} from the machine-wide policy or defaults file; keys belong in the user's "
                    + "settings or an environment variable", name);
            return Optional.empty();
        }
        String stored = getter.apply(name);
        return stored == null || stored.isBlank() ? Optional.empty() : Optional.of(stored.strip());
    }

    /** Whether a key is stored for the provider (not the environment variable). */
    public boolean hasStoredKey(String providerId) {
        String stored = getter.apply(keyName(providerId));
        return stored != null && !stored.isBlank();
    }

    public void store(String providerId, String key) {
        if (key == null || key.isBlank()) {
            remove(providerId);
        } else {
            setter.accept(keyName(providerId), key.strip());
        }
    }

    public void remove(String providerId) {
        remover.accept(keyName(providerId));
    }
}
