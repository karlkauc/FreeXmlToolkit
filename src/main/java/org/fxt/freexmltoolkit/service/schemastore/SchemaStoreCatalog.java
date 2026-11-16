package org.fxt.freexmltoolkit.service.schemastore;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * The <a href="https://www.schemastore.org">SchemaStore</a> catalog: which public JSON Schema
 * belongs to which file name ({@code docker-compose.yml}, {@code .github/workflows/*.yml},
 * {@code package.json}, …). Optional and off by default ({@value #ENABLED_KEY}); when on, the
 * catalog is downloaded once (through the {@link Fetcher} - in the application
 * {@code ConnectionService}) into a local file and refreshed when it is older than
 * {@link #MAX_AGE}. Only the catalog and the matched schema are downloaded; no file name or
 * content is sent. The catalog address can point at a mirror ({@value #URL_KEY}). UI-free.
 */
public final class SchemaStoreCatalog {

    private static final Logger logger = LogManager.getLogger(SchemaStoreCatalog.class);

    /** Setting / policy key: use the catalog to find schemas by file name. */
    public static final String ENABLED_KEY = "schemastore.enabled";
    /** Setting / policy key: the catalog's address (a mirror inside a company network, for example). */
    public static final String URL_KEY = "schemastore.catalogUrl";
    /** The public catalog. */
    public static final String DEFAULT_URL = "https://www.schemastore.org/api/json/catalog.json";
    /** A cached catalog older than this is refreshed in the background. */
    public static final Duration MAX_AGE = Duration.ofDays(7);

    /** One schema of the catalog. */
    public record Schema(String name, String description, List<String> fileMatch, String url) {
        public Schema {
            name = name == null ? "" : name;
            description = description == null ? "" : description;
            fileMatch = fileMatch == null ? List.of() : List.copyOf(fileMatch);
        }
    }

    /** A schema chosen for a file, with the pattern that chose it. */
    public record Match(Schema schema, String pattern) {
    }

    /** Downloads the catalog. */
    @FunctionalInterface
    public interface Fetcher {
        /**
         * @param uri the catalog's address
         * @return its bytes
         * @throws IOException when it cannot be read
         */
        byte[] fetch(URI uri) throws IOException;
    }

    private final Path cacheFile;
    private final Fetcher fetcher;
    private List<Schema> schemas;
    private FileTime loadedStamp;

    /**
     * @param cacheFile where the downloaded catalog is kept
     * @param fetcher   how it is downloaded
     */
    public SchemaStoreCatalog(Path cacheFile, Fetcher fetcher) {
        this.cacheFile = cacheFile;
        this.fetcher = fetcher;
    }

    private static final class Holder {
        static final SchemaStoreCatalog INSTANCE = new SchemaStoreCatalog(
                Path.of(System.getProperty("user.home"), ".freeXmlToolkit", "schemastore", "catalog.json"),
                SchemaStoreCatalog::fetchWithConnectionService);
    }

    private static volatile SchemaStoreCatalog override;

    /** @return the application's catalog (cache in {@code ~/.freeXmlToolkit/schemastore}) */
    public static SchemaStoreCatalog shared() {
        SchemaStoreCatalog o = override;
        return o != null ? o : Holder.INSTANCE;
    }

    /**
     * Test hook: replaces {@link #shared()}.
     *
     * @param catalog the catalog to use, {@code null} for the application's
     */
    public static void setShared(SchemaStoreCatalog catalog) {
        override = catalog;
    }

    private static byte[] fetchWithConnectionService(URI uri) throws IOException {
        var connection = org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.ConnectionService.class);
        var response = connection.fetchBinary(uri);
        if (response.status() != 200) {
            throw new IOException("The catalog could not be downloaded (HTTP " + response.status() + ") from " + uri);
        }
        return response.body();
    }

    // ---- reading ------------------------------------------------------------------------------

    /**
     * Reads a catalog ({@code {"schemas": [{"name", "description", "fileMatch", "url"}]}}).
     *
     * @param json the catalog text
     * @return its schemas that have a URL
     * @throws IOException when the text is no catalog
     */
    public static List<Schema> parse(String json) throws IOException {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("schemas")
                    || !root.getAsJsonObject().get("schemas").isJsonArray()) {
                throw new IOException("This is not a SchemaStore catalog (no \"schemas\" list).");
            }
            List<Schema> out = new ArrayList<>();
            for (JsonElement item : root.getAsJsonObject().getAsJsonArray("schemas")) {
                if (!item.isJsonObject()) {
                    continue;
                }
                JsonObject schema = item.getAsJsonObject();
                String url = string(schema, "url");
                if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
                    continue;
                }
                List<String> patterns = new ArrayList<>();
                if (schema.has("fileMatch") && schema.get("fileMatch").isJsonArray()) {
                    for (JsonElement pattern : schema.getAsJsonArray("fileMatch")) {
                        if (pattern.isJsonPrimitive()) {
                            patterns.add(pattern.getAsString());
                        }
                    }
                }
                out.add(new Schema(string(schema, "name"), string(schema, "description"), patterns, url));
            }
            return List.copyOf(out);
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("The catalog is not valid JSON: " + e.getMessage(), e);
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    /** @return the cached catalog's schemas, empty while there is none */
    public synchronized List<Schema> schemas() {
        try {
            if (!Files.isRegularFile(cacheFile)) {
                schemas = null;
                return List.of();
            }
            FileTime stamp = Files.getLastModifiedTime(cacheFile);
            if (schemas == null || !stamp.equals(loadedStamp)) {
                schemas = parse(Files.readString(cacheFile, StandardCharsets.UTF_8));
                loadedStamp = stamp;
            }
            return schemas;
        } catch (IOException e) {
            logger.warn("SchemaStore catalog {} cannot be read: {}", cacheFile, e.getMessage());
            schemas = List.of();
            return schemas;
        }
    }

    /**
     * @param file a JSON or YAML file
     * @return the catalog's schema for its name (the most specific pattern wins), empty when none
     */
    public Optional<Match> match(Path file) {
        Match best = null;
        for (Schema schema : schemas()) {
            String pattern = FileNamePatterns.bestMatch(schema.fileMatch(), file);
            if (pattern != null && (best == null
                    || FileNamePatterns.specificity(pattern) > FileNamePatterns.specificity(best.pattern()))) {
                best = new Match(schema, pattern);
            }
        }
        return Optional.ofNullable(best);
    }

    /** @return when the cached catalog was downloaded, empty when there is none */
    public Optional<Instant> lastUpdated() {
        try {
            return Files.isRegularFile(cacheFile)
                    ? Optional.of(Files.getLastModifiedTime(cacheFile).toInstant()) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** @return whether there is no cached catalog or it is older than {@link #MAX_AGE} */
    public boolean isStale() {
        return lastUpdated().map(t -> t.plus(MAX_AGE).isBefore(Instant.now())).orElse(true);
    }

    /** @return where the catalog is kept */
    public Path cacheFile() {
        return cacheFile;
    }

    // ---- downloading --------------------------------------------------------------------------

    /**
     * Downloads the catalog and keeps it (the old one stays when the new one is not a catalog).
     *
     * @param url the catalog's address
     * @return the number of schemas in it
     * @throws IOException when it cannot be downloaded or read
     */
    public int refresh(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url == null || url.isBlank() ? DEFAULT_URL : url.strip());
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid catalog address: " + url, e);
        }
        String text = new String(fetcher.fetch(uri), StandardCharsets.UTF_8);
        List<Schema> parsed = parse(text);
        Files.createDirectories(cacheFile.getParent());
        Path tmp = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        synchronized (this) {
            schemas = parsed;
            loadedStamp = Files.getLastModifiedTime(cacheFile);
        }
        logger.info("SchemaStore catalog updated from {}: {} schemas", uri, parsed.size());
        return parsed.size();
    }
}
