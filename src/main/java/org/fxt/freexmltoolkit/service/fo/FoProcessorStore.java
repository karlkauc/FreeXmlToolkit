package org.fxt.freexmltoolkit.service.fo;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.AdminPolicy;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

/**
 * The configured external XSL-FO processors and the default one, kept in the settings:
 * {@value #KEY_PROCESSORS} (JSON list), {@value #KEY_DEFAULT} ({@value #BUILTIN_ID} or a
 * processor id) and {@value #KEY_LAST} (the PDF/FOP panel's last choice). An administrator can
 * give or lock them in {@code defaults.properties} / {@code policy.properties} like any setting;
 * {@code fop.external.allowed=false} in the policy turns external processors off.
 */
public final class FoProcessorStore {

    private static final Logger logger = LogManager.getLogger(FoProcessorStore.class);

    /** Settings key of the processor list (JSON). */
    public static final String KEY_PROCESSORS = "fop.processors";
    /** Settings key of the default processor ({@value #BUILTIN_ID} or an id). */
    public static final String KEY_DEFAULT = "fop.processor.default";
    /** Settings key of the processor last chosen in the PDF/FOP panel. */
    public static final String KEY_LAST = "fop.processor.last";
    /** Policy key: {@code false} disables external processors (only the bundled FOP renders). */
    public static final String KEY_EXTERNAL_ALLOWED = AdminPolicy.KEY_FOP_EXTERNAL_ALLOWED;
    /** The id of the bundled Apache FOP. */
    public static final String BUILTIN_ID = "builtin";

    private static final Type LIST_TYPE = new TypeToken<List<Stored>>() { }.getType();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Function<String, String> getter;
    private final BiConsumer<String, String> setter;
    private final java.util.function.BooleanSupplier externalAllowed;

    /**
     * @param getter          reads a setting ({@code null} when unset)
     * @param setter          writes a setting
     * @param externalAllowed whether the administrator allows external processors
     */
    public FoProcessorStore(Function<String, String> getter, BiConsumer<String, String> setter,
                            java.util.function.BooleanSupplier externalAllowed) {
        this.getter = getter;
        this.setter = setter;
        this.externalAllowed = externalAllowed;
    }

    /** @return the store backed by the application settings and the administrator policy */
    public static FoProcessorStore settings() {
        var properties = PropertiesServiceImpl.getInstance();
        return new FoProcessorStore(properties::get, properties::set,
                () -> AdminPolicy.current().externalFoProcessorsAllowed());
    }

    /** @return whether external processors may run */
    public boolean externalAllowed() {
        return externalAllowed.getAsBoolean();
    }

    /** @return the configured processors (empty when none or not allowed) */
    public List<FoProcessorConfig> list() {
        if (!externalAllowed()) {
            return List.of();
        }
        String json = getter.apply(KEY_PROCESSORS);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Stored> stored = GSON.fromJson(json, LIST_TYPE);
            List<FoProcessorConfig> result = new ArrayList<>();
            if (stored != null) {
                for (Stored s : stored) {
                    if (s != null) {
                        result.add(s.toConfig());
                    }
                }
            }
            return List.copyOf(result);
        } catch (JsonParseException | IllegalStateException e) {
            logger.warn("Ignoring unreadable setting {}: {}", KEY_PROCESSORS, e.getMessage());
            return List.of();
        }
    }

    /** Replaces the processor list. */
    public void save(List<FoProcessorConfig> processors) {
        List<Stored> stored = new ArrayList<>();
        for (FoProcessorConfig config : processors) {
            stored.add(Stored.of(config));
        }
        setter.accept(KEY_PROCESSORS, stored.isEmpty() ? "" : GSON.toJson(stored, LIST_TYPE));
        String def = getter.apply(KEY_DEFAULT);
        if (def != null && !BUILTIN_ID.equals(def) && processors.stream().noneMatch(p -> p.id().equals(def))) {
            setter.accept(KEY_DEFAULT, BUILTIN_ID);
        }
    }

    /** @return the id of the default processor ({@value #BUILTIN_ID} when unset or gone) */
    public String defaultId() {
        return validIdOrBuiltin(getter.apply(KEY_DEFAULT));
    }

    /** Sets the default processor ({@value #BUILTIN_ID} or an id). */
    public void setDefaultId(String id) {
        setter.accept(KEY_DEFAULT, id == null || id.isBlank() ? BUILTIN_ID : id);
    }

    /** @return the processor last chosen in the PDF/FOP panel, else the default */
    public String lastId() {
        String last = getter.apply(KEY_LAST);
        return last == null || last.isBlank() ? defaultId() : validIdOrBuiltin(last);
    }

    /** Remembers the PDF/FOP panel's choice. */
    public void setLastId(String id) {
        setter.accept(KEY_LAST, id == null || id.isBlank() ? BUILTIN_ID : id);
    }

    /**
     * Finds the processor for a run.
     *
     * @param idOrName {@code null}, blank or {@value #BUILTIN_ID} = the bundled FOP; otherwise the
     *                 id or name of a configured processor
     * @return the external processor, or empty for the bundled FOP
     * @throws IllegalArgumentException when no processor has that id or name, or external
     *                                  processors are not allowed
     */
    public Optional<FoProcessorConfig> resolve(String idOrName) {
        if (idOrName == null || idOrName.isBlank() || BUILTIN_ID.equalsIgnoreCase(idOrName.strip())) {
            return Optional.empty();
        }
        if (!externalAllowed()) {
            throw new IllegalArgumentException("External XSL-FO processors are disabled by the administrator ("
                    + KEY_EXTERNAL_ALLOWED + "=false).");
        }
        return list().stream().filter(p -> p.matches(idOrName)).findFirst()
                .map(Optional::of)
                .orElseThrow(() -> new IllegalArgumentException("No XSL-FO processor named \"" + idOrName.strip()
                        + "\" is configured. Add it in Settings → XSL-FO Processors."));
    }

    private String validIdOrBuiltin(String id) {
        if (id == null || id.isBlank() || BUILTIN_ID.equals(id)) {
            return BUILTIN_ID;
        }
        return list().stream().anyMatch(p -> p.id().equals(id)) ? id : BUILTIN_ID;
    }

    /** The JSON shape (field names are the file format; keep them stable). */
    private static final class Stored {
        String id;
        String name;
        String preset;
        String executable;
        String arguments;
        String workingDir;
        Integer timeoutSeconds;

        static Stored of(FoProcessorConfig c) {
            Stored s = new Stored();
            s.id = c.id();
            s.name = c.name();
            s.preset = c.preset().name();
            s.executable = c.executable();
            s.arguments = c.arguments();
            s.workingDir = c.workingDir().isEmpty() ? null : c.workingDir();
            s.timeoutSeconds = c.timeoutSeconds() == FoProcessorConfig.DEFAULT_TIMEOUT_SECONDS
                    ? null : c.timeoutSeconds();
            return s;
        }

        FoProcessorConfig toConfig() {
            return new FoProcessorConfig(id, name, FoProcessorPreset.of(preset), executable, arguments, workingDir,
                    timeoutSeconds == null ? 0 : timeoutSeconds);
        }
    }
}
