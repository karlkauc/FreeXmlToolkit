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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.fxt.freexmltoolkit.service.json.JsonTokenizer;
import org.fxt.freexmltoolkit.service.json.JsonTokenizer.Kind;
import org.fxt.freexmltoolkit.service.json.JsonTokenizer.Token;

/**
 * Where the caret is in JSON / JSONC / JSON5 text that may be incomplete (the user is
 * typing): in a key position, in a value position, or elsewhere, with the instance path of
 * that place, the word being typed and the keys the enclosing object already has.
 */
public final class JsonCaretContext {

    /** What can be typed at the caret. */
    public enum Kind2 {
        /** A member key of an object. */
        KEY,
        /** A value (of a member or an array item, or the root). */
        VALUE,
        /** Nothing to complete (after a key, after a value, inside a comment, …). */
        NONE
    }

    /**
     * The caret context.
     *
     * @param kind          key, value or none
     * @param path          for KEY the path of the enclosing object; for VALUE the path of the value
     * @param partial       the word typed so far (without its opening quote), "" when none
     * @param replaceStart  start of the text a completion replaces (the partial token)
     * @param replaceEnd    end of it (the whole token, also the part after the caret)
     * @param quoted        whether the partial token starts with a quote
     * @param existingKeys  the keys the enclosing object has (for KEY; the partial excluded)
     */
    public record Result(Kind2 kind, List<Object> path, String partial, int replaceStart, int replaceEnd,
                         boolean quoted, Set<String> existingKeys) {

        static Result none(int offset) {
            return new Result(Kind2.NONE, List.of(), "", offset, offset, false, Set.of());
        }
    }

    private enum State { EXPECT_KEY, EXPECT_COLON, EXPECT_VALUE, AFTER_VALUE }

    private static final class Frame {
        final boolean object;
        State state;
        String key;
        int index;
        final Set<String> keys = new LinkedHashSet<>();

        Frame(boolean object) {
            this.object = object;
            this.state = object ? State.EXPECT_KEY : State.EXPECT_VALUE;
        }

        Object childSegment() {
            return object ? key : (Object) index;
        }
    }

    private JsonCaretContext() {
    }

    /**
     * @param text   the text
     * @param offset the caret offset
     * @return the context at the caret
     */
    public static Result analyze(String text, int offset) {
        if (text == null || offset < 0 || offset > text.length()) {
            return Result.none(Math.max(0, offset));
        }
        List<Token> tokens = JsonTokenizer.tokenize(text);
        // the token the caret is in or right after (a word being typed)
        Token partial = null;
        int stop = tokens.size();
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.start() >= offset) {
                stop = i;
                break;
            }
            if (offset <= t.end()) {
                if (t.kind().isComment() && (offset < t.end() || t.kind() == Kind.LINE_COMMENT)) {
                    return Result.none(offset);
                }
                if (isWord(t.kind())) {
                    // inside a string, or touching the end of a word
                    if (t.kind() != Kind.STRING || offset < t.end() || t.unterminated()) {
                        partial = t;
                        stop = i;
                    } else {
                        stop = i + 1; // right after a closed string: it is complete
                    }
                    break;
                }
                if (offset < t.end()) {
                    stop = i + 1;
                    break;
                }
            }
        }
        Deque<Frame> stack = new ArrayDeque<>();
        boolean rootDone = false;
        for (int i = 0; i < stop; i++) {
            Token t = tokens.get(i);
            if (t.kind().isTrivia()) {
                continue;
            }
            Frame top = stack.peek();
            switch (t.kind()) {
                case BEGIN_OBJECT, BEGIN_ARRAY -> {
                    if (top != null && top.state == State.EXPECT_VALUE) {
                        top.state = State.AFTER_VALUE;
                    }
                    stack.push(new Frame(t.kind() == Kind.BEGIN_OBJECT));
                }
                case END_OBJECT, END_ARRAY -> {
                    if (!stack.isEmpty()) {
                        stack.pop();
                    }
                    if (stack.isEmpty()) {
                        rootDone = true;
                    }
                }
                case COLON -> {
                    if (top != null && top.object) {
                        top.state = State.EXPECT_VALUE;
                    }
                }
                case COMMA -> {
                    if (top != null) {
                        if (top.object) {
                            top.state = State.EXPECT_KEY;
                            top.key = null;
                        } else {
                            top.index++;
                            top.state = State.EXPECT_VALUE;
                        }
                    }
                }
                default -> {
                    if (top == null) {
                        rootDone = true;
                    } else if (top.object && top.state == State.EXPECT_KEY) {
                        top.key = JsonTokenizer.unquote(t);
                        top.keys.add(top.key);
                        top.state = State.EXPECT_COLON;
                    } else if (top.state == State.EXPECT_VALUE) {
                        top.state = State.AFTER_VALUE;
                    }
                }
            }
        }
        int replaceStart = partial != null ? partial.start() : offset;
        int replaceEnd = partial != null ? partial.end() : offset;
        if (partial != null) {
            // a string not closed yet runs on over the next lines: replace only up to the line end
            int lineEnd = text.indexOf('\n', offset);
            if (lineEnd >= 0 && lineEnd < replaceEnd) {
                replaceEnd = lineEnd;
            }
        }
        boolean quoted = partial != null && partial.kind() == Kind.STRING;
        String typed = "";
        if (partial != null) {
            typed = text.substring(partial.start() + (quoted ? 1 : 0), Math.max(partial.start() + (quoted ? 1 : 0), offset));
        }
        Frame top = stack.peek();
        if (top == null) {
            return rootDone ? Result.none(offset)
                    : new Result(Kind2.VALUE, List.of(), typed, replaceStart, replaceEnd, quoted, Set.of());
        }
        List<Object> containerPath = containerPath(stack);
        if (top.object && top.state == State.EXPECT_KEY) {
            Set<String> keys = new LinkedHashSet<>(top.keys);
            if (partial != null && replaceEnd < partial.end()) {
                // the open string swallowed the following lines: read them on their own
                keys.addAll(keysAfter(JsonTokenizer.tokenize(text.substring(replaceEnd)), 0));
            } else {
                keys.addAll(keysAfter(tokens, partial != null ? indexOf(tokens, partial) + 1 : stop));
            }
            if (partial != null) {
                keys.remove(JsonTokenizer.unquote(partial));
            }
            return new Result(Kind2.KEY, containerPath, typed, replaceStart, replaceEnd, quoted, keys);
        }
        if (top.state == State.EXPECT_VALUE) {
            List<Object> path = new ArrayList<>(containerPath);
            path.add(top.childSegment());
            return new Result(Kind2.VALUE, path, typed, replaceStart, replaceEnd, quoted, Set.of());
        }
        return Result.none(offset);
    }

    /**
     * The instance path of the key or value at an offset (hover): the member a key belongs
     * to, or the value under the offset.
     *
     * @param text   the text
     * @param offset an offset
     * @return the path, or {@code null} when the offset is not on a key or a scalar value
     */
    public static List<Object> pathAt(String text, int offset) {
        if (text == null || offset < 0 || offset >= text.length()) {
            return null;
        }
        List<Token> tokens = JsonTokenizer.tokenize(text);
        Token at = null;
        for (Token t : tokens) {
            if (offset >= t.start() && offset < t.end()) {
                at = t;
                break;
            }
        }
        if (at == null || !isWord(at.kind())) {
            return null;
        }
        Result result = analyze(text, at.end() == text.length() ? at.end() : at.start() + 1);
        if (result.kind() == Kind2.KEY) {
            List<Object> path = new ArrayList<>(result.path());
            path.add(JsonTokenizer.unquote(at));
            return path;
        }
        return result.kind() == Kind2.VALUE ? result.path() : null;
    }

    private static boolean isWord(Kind kind) {
        return kind == Kind.STRING || kind == Kind.IDENTIFIER || kind == Kind.NUMBER || kind == Kind.LITERAL;
    }

    private static int indexOf(List<Token> tokens, Token token) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i) == token) {
                return i;
            }
        }
        return tokens.size();
    }

    private static List<Object> containerPath(Deque<Frame> stack) {
        List<Frame> frames = new ArrayList<>(stack);
        java.util.Collections.reverse(frames); // outermost first
        List<Object> path = new ArrayList<>();
        for (int i = 0; i < frames.size() - 1; i++) {
            path.add(frames.get(i).childSegment());
        }
        return path;
    }

    /** The keys of the current object that follow the caret (until its closing bracket). */
    private static Set<String> keysAfter(List<Token> tokens, int from) {
        Set<String> keys = new LinkedHashSet<>();
        int depth = 0;
        Token previous = null;
        for (int i = from; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.kind().isTrivia()) {
                continue;
            }
            switch (t.kind()) {
                case BEGIN_OBJECT, BEGIN_ARRAY -> depth++;
                case END_OBJECT, END_ARRAY -> {
                    if (--depth < 0) {
                        return keys;
                    }
                }
                case COLON -> {
                    if (depth == 0 && previous != null && isWord(previous.kind())) {
                        keys.add(JsonTokenizer.unquote(previous));
                    }
                }
                default -> {
                    // values
                }
            }
            previous = t;
        }
        return keys;
    }
}
