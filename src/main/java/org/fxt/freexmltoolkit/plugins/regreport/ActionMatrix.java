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
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * The obligation of every field per action type and level, as ESMA's transaction reporting rules
 * tabulate it (EMIR, SFTR): {@code M} mandatory, {@code C} conditional, {@code O} optional,
 * {@code -} not applicable. With the element paths of each field (relative to the action element),
 * it checks a report: mandatory fields present, not applicable fields absent. Generated as JSON:
 *
 * <pre>{"1.4": {"name": "…", "codes": "EMIR-VR-1004-01 …", "TRAD": {"NEWT": "M", …},
 *   "PSTN": {…}, "paths": {"NEWT": ["CtrPtySpcfcData/…/LEI"], …}}}</pre>
 */
public final class ActionMatrix {

    /** A field of the matrix. */
    public static final class Entry {
        String name;
        String codes;
        Map<String, String> TRAD;
        Map<String, String> PSTN;
        Map<String, List<String>> paths;

        public String name() { return name == null ? "" : name; }
        /** @return ESMA's error codes of the field ({@code EMIR-VR-1004-01 …}) */
        public String codes() { return codes == null ? "" : codes; }

        /** @return the obligation for an action type at a level ({@code TRAD}, {@code PSTN}), empty when unknown */
        public String obligation(String level, String action) {
            Map<String, String> byAction = "PSTN".equals(level) ? PSTN : TRAD;
            String value = byAction == null ? null : byAction.get(action);
            return value == null ? "" : value;
        }

        /** @return the field's element paths under the action element of an action type */
        public List<String> paths(String action) {
            return paths == null ? List.of() : paths.getOrDefault(action, List.of());
        }
    }

    private final Map<String, Entry> entries;

    private ActionMatrix(Map<String, Entry> entries) {
        this.entries = entries;
    }

    /** @return the matrix of a bundled resource */
    public static ActionMatrix load(String resource) {
        try (InputStream in = ActionMatrix.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing resource " + resource);
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, Entry>>() { }.getType();
                return new ActionMatrix(new Gson().fromJson(reader, type));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @return the fields */
    public Map<String, Entry> entries() {
        return entries;
    }

    /**
     * Checks one action element (the child of a report that names the action type).
     *
     * @param rulePrefix ids are {@code <prefix>-<field number>}
     * @param action     the action type ({@code NEWT} …)
     * @param level      {@code TRAD} or {@code PSTN}
     */
    public void check(RecordNode actionElement, String action, String level, String rulePrefix, RegRuleSet.Findings out) {
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            Entry entry = e.getValue();
            List<String> paths = entry.paths(action);
            if (paths.isEmpty()) {
                continue;
            }
            String obligation = entry.obligation(level, action);
            boolean present = paths.stream().anyMatch(actionElement::filled);
            if ("M".equals(obligation) && !present) {
                out.error(rulePrefix + "-" + e.getKey(), actionElement.line(), "Field " + e.getKey() + " · " + entry.name()
                        + " is mandatory for action type " + action + (level.equals("PSTN") ? " at position level" : "") + ".");
            } else if ("-".equals(obligation) && present) {
                String path = paths.stream().filter(actionElement::filled).findFirst().orElse(paths.getFirst());
                out.error(rulePrefix + "-" + e.getKey(), actionElement.lineOf(path), "Field " + e.getKey() + " · " + entry.name()
                        + " must not be reported for action type " + action + (level.equals("PSTN") ? " at position level" : "") + ".");
            }
        }
    }
}
