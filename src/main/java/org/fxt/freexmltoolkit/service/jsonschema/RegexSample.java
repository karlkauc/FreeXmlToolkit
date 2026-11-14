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

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Builds one short string matching a regular expression, for the common patterns of schemas
 * ({@code ^[A-Z]{2}-\d+$}, {@code ^\w+@\w+\.com$}, {@code (ab|cd)?x}): literals, escapes,
 * character classes, groups with alternatives (the first one is taken) and quantifiers (their
 * minimum, at least one for {@code +}). The result is checked with {@link Pattern}; anything it
 * cannot build gives {@code null}.
 */
final class RegexSample {

    private final String regex;
    private int pos;

    private RegexSample(String regex) {
        this.regex = regex;
    }

    /**
     * @param regex a pattern (ECMA / Java syntax, searched like JSON Schema's {@code pattern})
     * @return a matching string, or {@code null}
     */
    static String of(String regex) {
        if (regex == null) {
            return null;
        }
        try {
            RegexSample sample = new RegexSample(regex);
            String result = sample.sequence();
            if (sample.pos < regex.length()) {
                return null;
            }
            return Pattern.compile(regex).matcher(result).find() ? result : null;
        } catch (RuntimeException e) {
            return null; // PatternSyntaxException, or a construct this builder does not know
        }
    }

    /** A sequence up to {@code |}, {@code )} or the end; alternatives after {@code |} are skipped. */
    private String sequence() {
        StringBuilder out = new StringBuilder();
        while (pos < regex.length()) {
            char c = regex.charAt(pos);
            if (c == ')') {
                return out.toString();
            }
            if (c == '|') {
                skipAlternatives();
                return out.toString();
            }
            String atom = atom();
            out.append(atom == null ? "" : repeat(atom));
        }
        return out.toString();
    }

    private void skipAlternatives() {
        int depth = 0;
        while (pos < regex.length()) {
            char c = regex.charAt(pos);
            if (c == '\\') {
                pos += 2;
                continue;
            }
            if (c == '[') {
                pos = classEnd(pos) + 1;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (depth == 0) {
                    return;
                }
                depth--;
            }
            pos++;
        }
    }

    /** @return the text of one atom ({@code null} for anchors) */
    private String atom() {
        char c = regex.charAt(pos++);
        switch (c) {
            case '^', '$' -> {
                return null;
            }
            case '.' -> {
                return "a";
            }
            case '(' -> {
                if (regex.startsWith("?:", pos)) {
                    pos += 2;
                } else if (regex.startsWith("?", pos)) {
                    throw new PatternSyntaxException("lookaround", regex, pos);
                }
                String inner = sequence();
                if (pos >= regex.length() || regex.charAt(pos) != ')') {
                    throw new PatternSyntaxException("group", regex, pos);
                }
                pos++;
                return inner;
            }
            case '[' -> {
                int end = classEnd(pos - 1);
                String body = regex.substring(pos, end);
                pos = end + 1;
                return String.valueOf(classChar(body));
            }
            case '\\' -> {
                char e = regex.charAt(pos++);
                return switch (e) {
                    case 'd' -> "1";
                    case 'w' -> "a";
                    case 's' -> " ";
                    case 'D', 'W', 'S' -> "-";
                    case 'b', 'B' -> "";
                    case 't' -> "\t";
                    case 'n' -> "\n";
                    case 'u' -> {
                        String hex = regex.substring(pos, pos + 4);
                        pos += 4;
                        yield String.valueOf((char) Integer.parseInt(hex, 16));
                    }
                    default -> String.valueOf(e);
                };
            }
            default -> {
                return String.valueOf(c);
            }
        }
    }

    /** Applies a quantifier after an atom. */
    private String repeat(String atom) {
        if (pos >= regex.length()) {
            return atom;
        }
        char q = regex.charAt(pos);
        int min;
        if (q == '?' || q == '*') {
            pos++;
            min = 0;
        } else if (q == '+') {
            pos++;
            min = 1;
        } else if (q == '{') {
            int close = regex.indexOf('}', pos);
            String[] range = regex.substring(pos + 1, close).split(",", -1);
            min = Integer.parseInt(range[0].trim());
            pos = close + 1;
        } else {
            return atom;
        }
        if (pos < regex.length() && (regex.charAt(pos) == '?' || regex.charAt(pos) == '+')) {
            pos++; // lazy / possessive
        }
        return atom.repeat(min);
    }

    private int classEnd(int open) {
        int i = open + 1;
        if (i < regex.length() && regex.charAt(i) == '^') {
            i++;
        }
        if (i < regex.length() && regex.charAt(i) == ']') {
            i++;
        }
        while (i < regex.length() && regex.charAt(i) != ']') {
            if (regex.charAt(i) == '\\') {
                i++;
            }
            i++;
        }
        if (i >= regex.length()) {
            throw new PatternSyntaxException("class", regex, open);
        }
        return i;
    }

    /** A character of a class body: its first member (a negated class: a letter it does not exclude). */
    private static char classChar(String body) {
        if (body.startsWith("^")) {
            for (char candidate : "aA1-_x".toCharArray()) {
                if (!Pattern.compile("[" + body.substring(1) + "]").matcher(String.valueOf(candidate)).matches()) {
                    return candidate;
                }
            }
            throw new PatternSyntaxException("negated class", body, 0);
        }
        if (body.startsWith("\\")) {
            return switch (body.charAt(1)) {
                case 'd' -> '1';
                case 'w' -> 'a';
                case 's' -> ' ';
                default -> body.charAt(1);
            };
        }
        return body.charAt(0);
    }
}
