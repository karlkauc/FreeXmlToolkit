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

package org.fxt.freexmltoolkit.plugins.regreport;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

/**
 * The fields of a reporting format as the regulator's tables describe them: id, name, element path,
 * obligation (M/C/O/F), guidance and an optional national rule. Generated from the regulator's
 * workbooks by a script under {@code scripts/} and bundled as JSON:
 *
 * <pre>{"format": "…", "source": "…", "fields": [{"id": "48", "name": "…", "path": "Root/Record/…",
 *   "obligation": "M", "guidance": "…", "rule": "…", "check": "currency"}]}</pre>
 *
 * Paths are local names from the root element, separated by {@code /}.
 */
public final class FieldCatalog {

    /** A field. */
    public static final class Field {
        String id;
        String name;
        String path;
        String obligation;
        String guidance;
        String rule;
        String check;

        public String id() { return id == null ? "" : id; }
        public String name() { return name == null ? "" : name; }
        public String path() { return path == null ? "" : path; }
        /** @return {@code M}, {@code O}, {@code F} or a conditional text starting with {@code C} */
        public String obligation() { return obligation == null ? "" : obligation; }
        public String guidance() { return guidance == null ? "" : guidance; }
        /** @return the national rule of the supervisor (e.g. the FMA's), empty when there is none */
        public String rule() { return rule == null ? "" : rule; }

        /**
         * @return a plain code check derived from the rule ({@code currency}, {@code country},
         *         {@code eu-country}, {@code isin}, {@code lei}), empty for none ({@link CatalogChecks})
         */
        public String check() { return check == null ? "" : check; }

        /** @return {@code Field 48 · Total AuM amount} */
        public String label() {
            return "Field " + id() + " · " + name();
        }
    }

    private String format;
    private String source;
    private List<Field> fields = List.of();
    private transient Map<String, Field> byPath;
    private transient Map<String, Field> byId;

    /** @return the catalogue of a bundled resource */
    public static FieldCatalog load(String resource) {
        try (InputStream in = FieldCatalog.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing resource " + resource);
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                FieldCatalog catalog = new Gson().fromJson(reader, FieldCatalog.class);
                catalog.index();
                return catalog;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void index() {
        byPath = new LinkedHashMap<>();
        byId = new LinkedHashMap<>();
        for (Field field : fields) {
            byPath.putIfAbsent(field.path(), field);
            byId.putIfAbsent(field.id(), field);
        }
    }

    /** @return the format's name */
    public String format() {
        return format == null ? "" : format;
    }

    /** @return where the field table comes from */
    public String source() {
        return source == null ? "" : source;
    }

    /** @return all fields in table order */
    public Collection<Field> fields() {
        return Collections.unmodifiableList(fields);
    }

    /** @return the field at a path of local names from the root, or {@code null} */
    public Field byPath(String path) {
        return byPath.get(path);
    }

    /** @return the field with an id, or {@code null} */
    public Field byId(String id) {
        return byId.get(id);
    }

    /** @return {@code Field 22 · Inception Date}, or the id when it is unknown */
    public String label(String id) {
        Field field = byId(id);
        return field == null ? "Field " + id : field.label();
    }

    /** @return the text for the Inspector / tooltip: label, obligation, national rule and guidance */
    public static String describe(Field field) {
        StringBuilder text = new StringBuilder(field.label());
        if (!field.obligation().isEmpty()) {
            text.append('\n').append("M/C/O/F: ").append(field.obligation().replace('\n', ' '));
        }
        if (!field.rule().isEmpty()) {
            text.append('\n').append("Rule: ").append(field.rule());
        }
        if (!field.guidance().isEmpty()) {
            String guidance = field.guidance().strip();
            if (guidance.length() > 700) {
                guidance = guidance.substring(0, 700).strip() + " …";
            }
            text.append("\n\n").append(guidance);
        }
        return text.toString();
    }
}
