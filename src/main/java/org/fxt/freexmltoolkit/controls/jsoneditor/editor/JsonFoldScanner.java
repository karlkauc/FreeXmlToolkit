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

package org.fxt.freexmltoolkit.controls.jsoneditor.editor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.TreeMap;

import org.fxt.freexmltoolkit.controls.v2.editor.managers.FoldingRegion;
import org.fxt.freexmltoolkit.service.json.JsonTokenizer;
import org.fxt.freexmltoolkit.service.json.JsonTokenizer.Kind;
import org.fxt.freexmltoolkit.service.json.JsonTokenizer.Token;

/**
 * Finds the foldable regions of JSON / JSONC / JSON5 text: every object or array whose closing
 * bracket is at least {@code minLines} lines below its opening one, plus block comments of that
 * size. Brackets inside strings and comments do not count. One pass over the tokens.
 */
public final class JsonFoldScanner {

    private JsonFoldScanner() {
    }

    private record Open(int line, String label, int depth) {
    }

    /**
     * @param text     the text
     * @param minLines minimum distance in lines between the opening and the closing bracket
     * @return the regions by 0-based start line (the outermost region of a line wins)
     */
    public static Map<Integer, FoldingRegion> scan(String text, int minLines) {
        Map<Integer, FoldingRegion> regions = new TreeMap<>();
        if (text == null || text.isEmpty()) {
            return regions;
        }
        Deque<Open> stack = new ArrayDeque<>();
        String lastKey = null;
        boolean afterColon = false;
        int line = 0;
        for (Token token : JsonTokenizer.tokenize(text)) {
            Kind kind = token.kind();
            switch (kind) {
                case BEGIN_OBJECT, BEGIN_ARRAY -> {
                    String bracket = kind == Kind.BEGIN_OBJECT ? "{…}" : "[…]";
                    String label = afterColon && lastKey != null ? lastKey + " " + bracket : bracket;
                    stack.push(new Open(line, label, stack.size()));
                    afterColon = false;
                    lastKey = null;
                }
                case END_OBJECT, END_ARRAY -> {
                    if (!stack.isEmpty()) {
                        Open open = stack.pop();
                        if (line - open.line() >= minLines) {
                            // the outermost region starting on a line wins (it closes last)
                            regions.put(open.line(), new FoldingRegion(open.line(), line, open.label(), open.depth()));
                        }
                    }
                    afterColon = false;
                }
                case STRING, IDENTIFIER -> {
                    if (!afterColon) {
                        lastKey = JsonTokenizer.unquote(token);
                    } else {
                        afterColon = false;
                    }
                }
                case COLON -> afterColon = true;
                case BLOCK_COMMENT -> {
                    int end = line + newlines(token.text());
                    if (end - line >= minLines && !regions.containsKey(line)) {
                        regions.put(line, new FoldingRegion(line, end, "/*…*/", stack.size()));
                    }
                }
                case WHITESPACE, LINE_COMMENT -> {
                    // no structure
                }
                default -> afterColon = false;
            }
            line += newlines(token.text());
        }
        return regions;
    }

    private static int newlines(String text) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }
}
