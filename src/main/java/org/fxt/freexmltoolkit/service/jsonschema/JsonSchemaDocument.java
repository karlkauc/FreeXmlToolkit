/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.service.jsonschema;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.JsonService;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A JSON Schema as a walkable model: the root schema plus every schema resource it reaches
 * through {@code $ref} (other files, loaded on first use), with the {@code $id} / anchor
 * index needed to resolve references. The validator stays networknt's; this model answers
 * what a schema allows and describes at a place (completion, hover, inspector, samples).
 *
 * <p>Loading other files may download them (remote {@code $ref}s), so build and query it
 * off the FX thread. Not thread-safe for concurrent first loads; queries after loading are
 * read-only.
 */
public final class JsonSchemaDocument {

    private static final Logger logger = LogManager.getLogger(JsonSchemaDocument.class);

    /** The draft a schema is written in (decides {@code items} / {@code prefixItems}, {@code $defs}). */
    public enum Draft {
        DRAFT_4, DRAFT_6, DRAFT_7, DRAFT_2019_09, DRAFT_2020_12;

        /** @return the draft named by a {@code $schema} URI, {@link #DRAFT_2020_12} when unknown */
        static Draft of(String schemaUri) {
            if (schemaUri == null) {
                return DRAFT_2020_12;
            }
            if (schemaUri.contains("draft-04")) {
                return DRAFT_4;
            }
            if (schemaUri.contains("draft-06")) {
                return DRAFT_6;
            }
            if (schemaUri.contains("draft-07")) {
                return DRAFT_7;
            }
            if (schemaUri.contains("2019-09")) {
                return DRAFT_2019_09;
            }
            return DRAFT_2020_12;
        }

        /** @return whether {@code items} as an array means a tuple (before 2020-12) */
        boolean tupleItems() {
            return this != DRAFT_2020_12;
        }
    }

    /**
     * A schema in context: the JSON value (an object or a boolean schema) and the base URI
     * its relative references resolve against.
     *
     * @param json the schema value
     * @param base the base URI ({@code $id} of the enclosing resource)
     */
    public record Node(JsonElement json, URI base) {

        /** @return the schema as an object, or {@code null} for a boolean schema */
        public JsonObject object() {
            return json != null && json.isJsonObject() ? json.getAsJsonObject() : null;
        }

        /** @return a sub-schema of this one (its own {@code $id} changes the base) */
        Node child(JsonElement value) {
            URI childBase = base;
            if (value != null && value.isJsonObject() && value.getAsJsonObject().has("$id")) {
                childBase = resolve(base, value.getAsJsonObject().get("$id").getAsString());
            }
            return new Node(value, childBase);
        }

        /** @return identity of the schema value for cycle detection */
        String key() {
            return base + "@" + System.identityHashCode(json);
        }
    }

    private final Node root;
    private final Draft draft;
    /** Every schema resource by its absolute URI (without fragment): files and embedded {@code $id}s. */
    private final Map<URI, Node> resources = new HashMap<>();
    /** Plain-name anchors ({@code $anchor}, {@code $dynamicAnchor}, draft-7 {@code $id: "#name"}) by resource. */
    private final Map<URI, Map<String, Node>> anchors = new HashMap<>();
    /** Loads another schema file by URI ({@code null} when it cannot be loaded). */
    private final Function<URI, JsonElement> loader;

    private JsonSchemaDocument(JsonElement json, URI location, Function<URI, JsonElement> loader) {
        this.loader = loader;
        URI base = location;
        if (json != null && json.isJsonObject() && json.getAsJsonObject().has("$id")) {
            base = resolve(location, json.getAsJsonObject().get("$id").getAsString());
        }
        this.root = new Node(json, base);
        String schemaUri = json != null && json.isJsonObject() && json.getAsJsonObject().has("$schema")
                ? json.getAsJsonObject().get("$schema").getAsString() : null;
        this.draft = Draft.of(schemaUri);
        index(root, location);
        if (!base.equals(location)) {
            resources.put(stripFragment(location), root);
        }
    }

    /**
     * Loads a schema file; files it references are loaded when a reference is followed.
     *
     * @param file the schema file
     * @return the document
     * @throws IOException when the file cannot be read
     * @throws com.google.gson.JsonParseException when it is not JSON
     */
    public static JsonSchemaDocument load(File file) throws IOException {
        String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        JsonService service = new JsonService();
        return new JsonSchemaDocument(service.parseAuto(text), file.getAbsoluteFile().toURI(),
                uri -> loadReferenced(service, uri));
    }

    /**
     * A schema given as text (tests, schemas held in memory). Other files are not loaded.
     *
     * @param text     the schema text
     * @param location its URI (base of relative references)
     * @return the document
     */
    public static JsonSchemaDocument of(String text, URI location) {
        return new JsonSchemaDocument(new JsonService().parseAuto(text), location, uri -> null);
    }

    private static JsonElement loadReferenced(JsonService service, URI uri) {
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                return service.parseAuto(Files.readString(new File(uri).toPath(), StandardCharsets.UTF_8));
            }
            if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
                File cached = service.resolveJsonSchemaLocation(uri.toString(), null);
                if (cached != null) {
                    return service.parseAuto(Files.readString(cached.toPath(), StandardCharsets.UTF_8));
                }
            }
        } catch (Exception e) {
            logger.debug("Could not load referenced schema {}: {}", uri, e.getMessage());
        }
        return null;
    }

    /** @return the root schema */
    public Node root() {
        return root;
    }

    /** @return the draft the root schema declares ({@code $schema}), 2020-12 by default */
    public Draft draft() {
        return draft;
    }

    // ==================== Index ====================

    /** Records the resources and anchors of one schema file / value. */
    private void index(Node node, URI fileLocation) {
        resources.putIfAbsent(stripFragment(node.base()), node);
        indexValue(node.json(), node.base(), new java.util.IdentityHashMap<>());
    }

    private void indexValue(JsonElement value, URI base, Map<JsonElement, Boolean> seen) {
        if (value == null || seen.put(value, Boolean.TRUE) != null) {
            return;
        }
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(item -> indexValue(item, base, seen));
            return;
        }
        if (!value.isJsonObject()) {
            return;
        }
        JsonObject object = value.getAsJsonObject();
        URI here = base;
        if (object.has("$id") && object.get("$id").isJsonPrimitive()) {
            String id = object.get("$id").getAsString();
            if (id.startsWith("#") && id.length() > 1) {
                // draft-6/7 plain-name fragment as $id
                anchors.computeIfAbsent(stripFragment(base), k -> new HashMap<>())
                        .putIfAbsent(id.substring(1), new Node(object, base));
            } else {
                here = resolve(base, id);
                resources.putIfAbsent(stripFragment(here), new Node(object, here));
            }
        }
        for (String anchorKey : new String[] {"$anchor", "$dynamicAnchor", "$recursiveAnchor"}) {
            if (object.has(anchorKey) && object.get(anchorKey).isJsonPrimitive()
                    && object.get(anchorKey).getAsJsonPrimitive().isString()) {
                anchors.computeIfAbsent(stripFragment(here), k -> new HashMap<>())
                        .putIfAbsent(object.get(anchorKey).getAsString(), new Node(object, here));
            }
        }
        for (var entry : object.entrySet()) {
            String key = entry.getKey();
            // values of these keywords are data, not schemas
            if (!key.equals("enum") && !key.equals("const") && !key.equals("default") && !key.equals("examples")) {
                indexValue(entry.getValue(), here, seen);
            }
        }
    }

    // ==================== References ====================

    /**
     * Resolves a {@code $ref} (or {@code $dynamicRef} / {@code $recursiveRef}) found in a schema.
     *
     * @param from the schema holding the reference
     * @param ref  the reference value
     * @return the referenced schema, or {@code null} when it cannot be found
     */
    public Node resolveRef(Node from, String ref) {
        if (ref == null) {
            return null;
        }
        URI target = resolve(from.base(), ref);
        URI resource = stripFragment(target);
        String fragment = target.getRawFragment() != null ? decode(target.getRawFragment()) : "";
        Node resourceNode = resources.get(resource);
        if (resourceNode == null) {
            resourceNode = loadResource(resource);
            if (resourceNode == null) {
                return null;
            }
        }
        if (fragment.isEmpty()) {
            return resourceNode;
        }
        if (fragment.startsWith("/")) {
            return pointer(resourceNode, fragment);
        }
        Map<String, Node> byName = anchors.get(resource);
        return byName == null ? null : byName.get(fragment);
    }

    private Node loadResource(URI resource) {
        JsonElement json = loader.apply(resource);
        if (json == null) {
            return null;
        }
        URI base = resource;
        if (json.isJsonObject() && json.getAsJsonObject().has("$id")) {
            base = resolve(resource, json.getAsJsonObject().get("$id").getAsString());
        }
        Node node = new Node(json, base);
        resources.put(resource, node);
        index(node, resource);
        return node;
    }

    /** Follows a JSON pointer inside a schema resource (base URIs updated by nested {@code $id}s). */
    private static Node pointer(Node start, String pointer) {
        Node current = start;
        for (String raw : pointer.substring(1).split("/", -1)) {
            String segment = raw.replace("~1", "/").replace("~0", "~");
            JsonElement json = current.json();
            JsonElement next = null;
            if (json != null && json.isJsonObject()) {
                next = json.getAsJsonObject().get(segment);
            } else if (json != null && json.isJsonArray()) {
                try {
                    int index = Integer.parseInt(segment);
                    if (index >= 0 && index < json.getAsJsonArray().size()) {
                        next = json.getAsJsonArray().get(index);
                    }
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            if (next == null) {
                return null;
            }
            current = current.child(next);
        }
        return current;
    }

    static URI resolve(URI base, String ref) {
        try {
            URI refUri = new URI(ref);
            if (base == null) {
                return refUri;
            }
            // "jar:" / opaque bases cannot resolve; keep the reference as is
            return base.isOpaque() ? refUri : base.resolve(refUri);
        } catch (Exception e) {
            return base;
        }
    }

    static URI stripFragment(URI uri) {
        if (uri == null || uri.getRawFragment() == null) {
            return uri;
        }
        try {
            return new URI(uri.getScheme(), uri.getSchemeSpecificPart(), null);
        } catch (Exception e) {
            return uri;
        }
    }

    private static String decode(String fragment) {
        try {
            return java.net.URLDecoder.decode(fragment.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return fragment;
        }
    }
}
