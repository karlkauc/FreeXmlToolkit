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

package org.fxt.freexmltoolkit.service.json;

import java.math.BigInteger;
import java.util.List;

import org.fxt.freexmltoolkit.service.json.JsonTokenizer.Kind;
import org.fxt.freexmltoolkit.service.json.JsonTokenizer.Token;

/**
 * Tells JSON, JSONC and JSON5 apart and turns JSONC/JSON5 into strict JSON, both on the
 * tokens of {@link JsonTokenizer} (never on regular expressions, which took a URL inside a
 * string for a comment and an unquoted key).
 *
 * <p>{@link #toStrictJson(String)} keeps every line break, so line numbers of parse errors
 * and of schema violations still point into the original text.
 */
public final class JsonDialect {

    /** Plain JSON. */
    public static final String JSON = "json";
    /** JSON with comments. */
    public static final String JSONC = "jsonc";
    /** JSON5. */
    public static final String JSON5 = "json5";

    private JsonDialect() {
    }

    /**
     * Detects the dialect of a text: JSON5 when it uses single quotes, unquoted keys, JSON5
     * numbers or trailing commas, JSONC when it has comments, else JSON.
     *
     * @param text the text, may be {@code null}
     * @return {@link #JSON}, {@link #JSONC} or {@link #JSON5}
     */
    public static String detect(String text) {
        return detect(JsonTokenizer.tokenize(text));
    }

    /**
     * Detects the dialect of a token list (see {@link #detect(String)}).
     *
     * @param tokens the tokens of the text
     * @return {@link #JSON}, {@link #JSONC} or {@link #JSON5}
     */
    public static String detect(List<Token> tokens) {
        boolean comments = false;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            switch (t.kind()) {
                case IDENTIFIER -> {
                    return JSON5;
                }
                case STRING -> {
                    if (t.singleQuoted()) {
                        return JSON5;
                    }
                }
                case NUMBER -> {
                    if (t.json5Number()) {
                        return JSON5;
                    }
                }
                case LINE_COMMENT, BLOCK_COMMENT -> comments = true;
                case COMMA -> {
                    if (isTrailingComma(tokens, i)) {
                        return JSON5;
                    }
                }
                default -> {
                    // structure or whitespace: nothing to learn
                }
            }
        }
        return comments ? JSONC : JSON;
    }

    /**
     * @return whether the comma at {@code index} is followed (after trivia) by a closing bracket
     */
    public static boolean isTrailingComma(List<Token> tokens, int index) {
        for (int j = index + 1; j < tokens.size(); j++) {
            Kind kind = tokens.get(j).kind();
            if (!kind.isTrivia()) {
                return kind == Kind.END_OBJECT || kind == Kind.END_ARRAY;
            }
        }
        return false;
    }

    /**
     * Converts JSONC/JSON5 text into strict JSON: comments become blanks (their line breaks
     * stay), trailing commas are dropped, unquoted keys and single-quoted strings get double
     * quotes, JSON5 numbers become JSON numbers ({@code Infinity}/{@code NaN} stay, Gson reads
     * them leniently). Plain JSON comes back unchanged.
     *
     * @param text the text, may be {@code null}
     * @return the strict JSON text
     */
    public static String toStrictJson(String text) {
        if (text == null) {
            return null;
        }
        List<Token> tokens = JsonTokenizer.tokenize(text);
        if (JSON.equals(detect(tokens))) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            switch (t.kind()) {
                case LINE_COMMENT -> out.append(" ".repeat(t.text().length()));
                case BLOCK_COMMENT -> out.append(blankKeepingLines(t.text()));
                case COMMA -> out.append(isTrailingComma(tokens, i) ? " " : ",");
                case IDENTIFIER -> out.append('"').append(t.text()).append('"');
                case STRING -> out.append(t.singleQuoted() || t.text().contains("\\\n")
                        ? toDoubleQuoted(t.text()) : t.text());
                case NUMBER -> out.append(t.json5Number() ? toJsonNumber(t.text()) : t.text());
                default -> out.append(t.text());
            }
        }
        return out.toString();
    }

    /** @return the text with every character but line breaks replaced by a blank */
    static String blankKeepingLines(String text) {
        StringBuilder b = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            b.append(c == '\n' || c == '\r' ? c : ' ');
        }
        return b.toString();
    }

    /**
     * Rewrites a (single- or double-quoted, JSON5) string literal as a double-quoted JSON
     * string: {@code \'} becomes {@code '}, a bare {@code "} is escaped, a line continuation
     * ({@code \} + line break) is dropped.
     */
    static String toDoubleQuoted(String literal) {
        char quote = literal.charAt(0);
        boolean closed = literal.length() >= 2 && literal.charAt(literal.length() - 1) == quote;
        String body = literal.substring(1, closed ? literal.length() - 1 : literal.length());
        StringBuilder b = new StringBuilder(body.length() + 2).append('"');
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                char next = body.charAt(i + 1);
                if (next == '\'') {
                    b.append('\'');
                } else if (next == '\n') {
                    // line continuation
                } else if (next == '\r') {
                    if (i + 2 < body.length() && body.charAt(i + 2) == '\n') {
                        i++;
                    }
                } else {
                    b.append(c).append(next);
                }
                i++;
            } else if (c == '"') {
                b.append("\\\"");
            } else {
                b.append(c);
            }
        }
        return b.append('"').toString();
    }

    /** Converts a JSON5 number (hex, leading/trailing dot, plus sign, leading zeros) into JSON. */
    static String toJsonNumber(String number) {
        String sign = "";
        String t = number;
        if (t.startsWith("+")) {
            t = t.substring(1);
        } else if (t.startsWith("-")) {
            sign = "-";
            t = t.substring(1);
        }
        if (t.equals("Infinity") || t.equals("NaN")) {
            return sign + t;
        }
        if (t.startsWith("0x") || t.startsWith("0X")) {
            try {
                return sign + new BigInteger(t.substring(2), 16);
            } catch (NumberFormatException e) {
                return number;
            }
        }
        if (t.startsWith(".")) {
            t = "0" + t;
        }
        int exp = Math.max(t.indexOf('e'), t.indexOf('E'));
        String mantissa = exp < 0 ? t : t.substring(0, exp);
        String exponent = exp < 0 ? "" : t.substring(exp);
        if (mantissa.endsWith(".")) {
            mantissa = mantissa.substring(0, mantissa.length() - 1);
        }
        while (mantissa.length() > 1 && mantissa.charAt(0) == '0' && Character.isDigit(mantissa.charAt(1))) {
            mantissa = mantissa.substring(1);
        }
        return sign + mantissa + exponent;
    }
}
