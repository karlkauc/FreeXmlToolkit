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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;

/**
 * The conditional rules of ESMA's transaction reporting tables - the fields marked {@code C}, whose
 * obligation depends on other fields - as a table that {@link ActionMatrix} paths make checkable. A
 * rule names its field, a condition and a consequence:
 *
 * <pre>{"field": "1.7", "when": "1.5 in N", "then": "required"}
 * {"field": "2.76", "scope": "AsstTp", "when": "2.75 in CASH", "then": "required"}</pre>
 *
 * <ul>
 *   <li>{@code when}: atoms joined by {@code and} - {@code <field> in A,B}, {@code <field> notin A,B}
 *       (also true when the field is not reported), {@code <field> filled}, {@code <field> blank},
 *       {@code action in NEWT,MODI}, {@code sft in Repo,SL}; empty = always;</li>
 *   <li>{@code then}: {@code required}, {@code blank}, {@code values A,B} (when reported, one of them),
 *       {@code differs <field>,…} (not the value of another field), {@code anyof <field>,…},
 *       {@code oneof <field>,…} (exactly one), {@code atmostone <field>,…};</li>
 *   <li>{@code scope}: an element that repeats (a collateral component, a schedule period); the rule
 *       is checked inside each of them, with the fields' paths below it;</li>
 *   <li>{@code severity}: {@code warning} for ESMA's "should".</li>
 * </ul>
 *
 * A field's value is its text; where the schema expresses a code by a choice of elements, the
 * {@code values} map translates the chosen element's name into ESMA's code
 * ({@code "1.5": {"FI": "F", "NFI": "N"}}). A rule is checked only for the action types (and variants)
 * for which the matrix marks its field conditional ({@code C}), and only when its condition can be decided.
 */
public final class ConditionalRules {

    /** The table as stored. */
    static final class Table {
        Map<String, Map<String, String>> values;
        List<Raw> rules;
    }

    /** A rule as stored. */
    static final class Raw {
        String field;
        String when;
        String then;
        String scope;
        String severity;
    }

    /** One condition. */
    record Atom(String subject, String op, List<String> args) {
    }

    /** A parsed rule. */
    record Rule(String field, List<Atom> when, String consequence, List<String> args, String scope, boolean warning) {
    }

    /** What a field holds at one place: whether it is reported, and its values (codes). */
    record Value(boolean filled, Set<String> values, boolean undecided, int line) {
    }

    private final List<Rule> rules;
    private final Map<String, Map<String, String>> codes;

    private ConditionalRules(List<Rule> rules, Map<String, Map<String, String>> codes) {
        this.rules = rules;
        this.codes = codes;
    }

    /** @return the rules of a bundled table */
    public static ConditionalRules load(String resource) {
        try (InputStream in = ConditionalRules.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing resource " + resource);
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return of(new Gson().fromJson(reader, Table.class));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static ConditionalRules of(Table table) {
        List<Rule> parsed = new ArrayList<>();
        for (Raw raw : table.rules) {
            parsed.add(parse(raw));
        }
        return new ConditionalRules(parsed, table.values == null ? Map.of() : table.values);
    }

    static Rule parse(Raw raw) {
        List<Atom> atoms = new ArrayList<>();
        if (raw.when != null && !raw.when.isBlank()) {
            for (String part : raw.when.trim().split("\\s+and\\s+")) {
                String[] words = part.trim().split("\\s+");
                if (words.length < 2 || !Set.of("in", "notin", "filled", "blank").contains(words[1])
                        || (words[1].equals("in") || words[1].equals("notin")) != (words.length == 3)) {
                    throw new IllegalArgumentException("Rule of field " + raw.field + ": cannot read \"" + part + "\"");
                }
                atoms.add(new Atom(words[0], words[1], words.length == 3 ? List.of(words[2].split(",")) : List.of()));
            }
        }
        String[] then = raw.then.trim().split("\\s+", 2);
        if (!Set.of("required", "blank", "values", "differs", "anyof", "oneof", "atmostone").contains(then[0])) {
            throw new IllegalArgumentException("Rule of field " + raw.field + ": unknown consequence \"" + raw.then + "\"");
        }
        List<String> args = then.length > 1 ? List.of(then[1].trim().split(",")) : List.of();
        return new Rule(raw.field, List.copyOf(atoms), then[0], args, raw.scope, "warning".equals(raw.severity));
    }

    /** @return every field number the rules name - checked, in a condition or in a consequence */
    public Set<String> fields() {
        Set<String> fields = new LinkedHashSet<>();
        for (Rule rule : rules) {
            fields.add(rule.field());
            rule.when().stream().map(Atom::subject).filter(s -> !s.equals("action") && !s.equals("sft")).forEach(fields::add);
            if (!rule.consequence().equals("values")) {
                fields.addAll(rule.args());
            }
        }
        return fields;
    }

    /**
     * Checks one action element.
     *
     * @param action     the action type ({@code NEWT} …)
     * @param variant    the matrix variant ({@code TRAD}, {@code TCTN.Repo} …)
     * @param sft        the SFT type ({@code Repo} …), or {@code null}
     * @param rulePrefix ids are {@code <prefix>-<field number>}
     */
    public void check(RecordNode actionElement, String action, String variant, String sft, ActionMatrix matrix,
                      String rulePrefix, RegRuleSet.Findings out) {
        Map<String, ActionMatrix.Entry> entries = matrix.entries();
        for (Rule rule : rules) {
            ActionMatrix.Entry entry = entries.get(rule.field());
            // only a plain C: ESMA's table also has "- C" (EROR, ETRM, COLU) where the field is not applicable
            if (entry == null || !"C".equals(entry.obligation(variant, action).trim())) {
                continue;
            }
            if (rule.scope() == null) {
                apply(rule, actionElement, "", actionElement, action, sft, matrix, rulePrefix, out);
            } else {
                for (String prefix : scopePrefixes(rule, matrix, action)) {
                    for (RecordNode place : actionElement.all(prefix)) {
                        apply(rule, place, prefix, actionElement, action, sft, matrix, rulePrefix, out);
                    }
                }
            }
        }
    }

    /** @return the paths (relative to the action element) of the scope element in the rule's field */
    private static Set<String> scopePrefixes(Rule rule, ActionMatrix matrix, String action) {
        Set<String> prefixes = new LinkedHashSet<>();
        for (String path : paths(matrix, rule.field(), action)) {
            String[] steps = path.split("/");
            for (int i = steps.length - 1; i >= 0; i--) {
                if (steps[i].equals(rule.scope())) {
                    prefixes.add(String.join("/", java.util.Arrays.copyOf(steps, i + 1)));
                    break;
                }
            }
        }
        return prefixes;
    }

    private void apply(Rule rule, RecordNode place, String prefix, RecordNode actionElement, String action, String sft,
                       ActionMatrix matrix, String rulePrefix, RegRuleSet.Findings out) {
        for (Atom atom : rule.when()) {
            Boolean holds = holds(atom, place, prefix, action, sft, matrix);
            if (holds == null || !holds) {
                return; // not met, or not decidable from the file
            }
        }
        Value target = value(rule.field(), place, prefix, action, matrix);
        String id = rulePrefix + "-" + rule.field();
        String label = label(matrix, rule.field());
        String when = rule.when().isEmpty() ? "" : " when " + describe(rule.when(), matrix);
        int line = place == actionElement ? actionElement.line() : place.line();
        String message = null;
        int at = line;
        switch (rule.consequence()) {
            case "required" -> {
                if (!target.filled() && !paths(matrix, rule.field(), action).isEmpty()) {
                    message = label + " must be reported" + when + ".";
                }
            }
            case "blank" -> {
                if (target.filled()) {
                    message = label + " must not be reported" + when + ".";
                    at = target.line();
                }
            }
            case "values" -> {
                if (target.filled() && !target.undecided() && target.values().stream().anyMatch(v -> !containsIgnoreCase(rule.args(), v))) {
                    message = label + " must be " + String.join(" or ", rule.args()) + when + ", not "
                            + String.join(", ", target.values()) + ".";
                    at = target.line();
                }
            }
            case "differs" -> {
                for (String other : rule.args()) {
                    Value v = value(other, place, prefix, action, matrix);
                    Set<String> same = target.values().stream().filter(x -> containsIgnoreCase(v.values(), x))
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    if (!same.isEmpty()) {
                        message = label + " must differ from " + label(matrix, other) + " (both \"" + same.iterator().next() + "\").";
                        at = target.line();
                        break;
                    }
                }
            }
            case "anyof", "oneof", "atmostone" -> {
                if (rule.args().stream().allMatch(f -> paths(matrix, f, action).isEmpty())) {
                    break;
                }
                long reported = rule.args().stream().filter(f -> value(f, place, prefix, action, matrix).filled()).count();
                String fields = rule.args().stream().map(f -> label(matrix, f)).collect(Collectors.joining(", "));
                if (rule.consequence().equals("anyof") && reported == 0) {
                    message = "At least one of " + fields + " must be reported" + when + ".";
                } else if (rule.consequence().equals("oneof") && reported != 1) {
                    message = "Exactly one of " + fields + " must be reported" + when + " (" + reported + " are).";
                } else if (rule.consequence().equals("atmostone") && reported > 1) {
                    message = "Only one of " + fields + " may be reported" + when + ".";
                }
            }
            default -> {
            }
        }
        if (message != null) {
            if (rule.warning()) {
                out.warning(id, at, message.replace(" must ", " should "));
            } else {
                out.error(id, at, message);
            }
        }
    }

    private Boolean holds(Atom atom, RecordNode place, String prefix, String action, String sft, ActionMatrix matrix) {
        switch (atom.subject()) {
            case "action" -> {
                return containsIgnoreCase(atom.args(), action) == atom.op().equals("in");
            }
            case "sft" -> {
                return sft != null && containsIgnoreCase(atom.args(), sft) == atom.op().equals("in");
            }
            default -> {
                Value v = value(atom.subject(), place, prefix, action, matrix);
                return switch (atom.op()) {
                    case "filled" -> v.filled();
                    case "blank" -> !v.filled();
                    case "in" -> v.undecided() ? null : v.values().stream().anyMatch(x -> containsIgnoreCase(atom.args(), x));
                    case "notin" -> !v.filled() ? Boolean.TRUE
                            : v.undecided() ? null : v.values().stream().noneMatch(x -> containsIgnoreCase(atom.args(), x));
                    default -> null;
                };
            }
        }
    }

    /** @return a field's value at a place: under a scope only the field's paths below it count */
    Value value(String field, RecordNode place, String prefix, String action, ActionMatrix matrix) {
        Map<String, String> map = codes.getOrDefault(field, Map.of());
        boolean filled = false;
        boolean undecided = false;
        int line = place.line();
        Set<String> values = new LinkedHashSet<>();
        for (String path : paths(matrix, field, action)) {
            String relative;
            if (prefix.isEmpty()) {
                relative = path;
            } else if (path.equals(prefix)) {
                relative = "";
            } else if (path.startsWith(prefix + "/")) {
                relative = path.substring(prefix.length() + 1);
            } else {
                continue;
            }
            String attribute = null;
            int slash = relative.lastIndexOf('/');
            String last = slash < 0 ? relative : relative.substring(slash + 1);
            if (last.startsWith("@")) {
                attribute = last.substring(1);
                relative = slash < 0 ? "" : relative.substring(0, slash);
            }
            String lastElement = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            if (lastElement.startsWith("@")) {
                lastElement = "";
            }
            for (RecordNode node : place.all(relative)) {
                String text = attribute != null ? node.attributes().getOrDefault(attribute, "").trim() : node.text().trim();
                boolean here = !text.isEmpty() || attribute == null && !node.children().isEmpty();
                if (!here) {
                    continue;
                }
                if (!filled) {
                    line = node.line();
                }
                filled = true;
                if (!text.isEmpty()) {
                    values.add(text);
                } else if (!node.children().isEmpty() && map.containsKey(node.children().getFirst().name())) {
                    values.add(map.get(node.children().getFirst().name()));
                } else if (map.containsKey(lastElement)) {
                    values.add(map.get(lastElement));
                } else {
                    undecided = true;
                }
            }
        }
        return new Value(filled, values, undecided && values.isEmpty(), line);
    }

    private static List<String> paths(ActionMatrix matrix, String field, String action) {
        ActionMatrix.Entry entry = matrix.entries().get(field);
        return entry == null ? List.of() : entry.paths(action);
    }

    private static String label(ActionMatrix matrix, String field) {
        ActionMatrix.Entry entry = matrix.entries().get(field);
        return "Field " + field + (entry == null || entry.name().isEmpty() ? "" : " · " + entry.name());
    }

    private static String describe(List<Atom> atoms, ActionMatrix matrix) {
        List<String> parts = new ArrayList<>();
        for (Atom atom : atoms) {
            // "2.10 filled and 2.10 notin CFDS" reads "2.10 Contract type is not CFDS"
            if (atom.op().equals("filled") && atoms.stream().anyMatch(a -> a.op().equals("notin") && a.subject().equals(atom.subject()))) {
                continue;
            }
            String subject = switch (atom.subject()) {
                case "action" -> "the action type";
                case "sft" -> "the SFT type";
                default -> {
                    ActionMatrix.Entry entry = matrix.entries().get(atom.subject());
                    yield atom.subject() + (entry == null || entry.name().isEmpty() ? "" : " " + entry.name());
                }
            };
            parts.add(subject + switch (atom.op()) {
                case "in" -> " is " + String.join(" or ", atom.args());
                case "notin" -> " is not " + String.join(" or ", atom.args());
                case "filled" -> " is reported";
                default -> " is not reported";
            });
        }
        return String.join(" and ", parts);
    }

    private static boolean containsIgnoreCase(java.util.Collection<String> list, String value) {
        String v = value.toLowerCase(Locale.ROOT);
        return list.stream().anyMatch(x -> x.toLowerCase(Locale.ROOT).equals(v));
    }
}
