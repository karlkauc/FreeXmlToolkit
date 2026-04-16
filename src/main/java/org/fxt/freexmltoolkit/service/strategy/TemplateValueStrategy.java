/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
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

package org.fxt.freexmltoolkit.service.strategy;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.GenerationContext;

/**
 * Generates values from a template string with multiple placeholder types.
 * <p>
 * Supported placeholders:
 * <ul>
 *   <li>{seq:N} - auto-incrementing sequence, zero-padded to N digits</li>
 *   <li>{seq} - auto-incrementing sequence, no padding</li>
 *   <li>{date:format} - current date/time formatted with DateTimeFormatter pattern</li>
 *   <li>{random:N} - random N-digit number</li>
 *   <li>{ref:xpath} - value from another XPath in the same document</li>
 *   <li>{file:N} - current file index (batch), zero-padded to N digits</li>
 * </ul>
 * <p>
 * Config key: "pattern" - the template string.
 * Example: "ORD-{seq:4}-{date:yyyy}" produces "ORD-0001-2026"
 */
public class TemplateValueStrategy implements ValueStrategy {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(seq|date|random|ref|file)(?::([^}]+))?}");

    @Override
    public String resolve(XsdExtendedElement element, Map<String, String> config, GenerationContext context) {
        String pattern = config.get("pattern");
        if (pattern == null || pattern.isBlank()) {
            return "";
        }

        String ruleId = context.getCurrentXPath() != null ? context.getCurrentXPath() : "template";

        Matcher matcher = PLACEHOLDER.matcher(pattern);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String type = matcher.group(1);
            String param = matcher.group(2);
            String replacement = switch (type) {
                case "seq" -> resolveSeq(ruleId, param, config, context);
                case "date" -> resolveDate(param);
                case "random" -> resolveRandom(param);
                case "ref" -> resolveRef(param, context);
                case "file" -> resolveFileIndex(param, context);
                default -> matcher.group(0);
            };
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String resolveSeq(String ruleId, String param, Map<String, String> config, GenerationContext context) {
        int start = parseIntOrDefault(config.get("start"), 1);
        int step = parseIntOrDefault(config.get("step"), 1);
        int value = context.nextSequenceValue(ruleId, start, step);
        int width = param != null ? parseIntOrDefault(param, 0) : 0;
        return width > 0 ? String.format("%0" + width + "d", value) : String.valueOf(value);
    }

    private String resolveDate(String format) {
        if (format == null || format.isBlank()) {
            format = "yyyy-MM-dd";
        }
        try {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format));
        } catch (IllegalArgumentException e) {
            return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    private String resolveRandom(String param) {
        int digits = parseIntOrDefault(param, 4);
        digits = Math.max(1, Math.min(digits, 18));
        long min = (long) Math.pow(10, digits - 1);
        long max = (long) Math.pow(10, digits) - 1;
        long value = ThreadLocalRandom.current().nextLong(min, max + 1);
        return String.valueOf(value);
    }

    private String resolveRef(String xpath, GenerationContext context) {
        if (xpath == null || xpath.isBlank()) {
            return "";
        }
        String value = context.getGeneratedValue(xpath);
        return value != null ? value : "";
    }

    private String resolveFileIndex(String param, GenerationContext context) {
        int width = parseIntOrDefault(param, 0);
        int index = context.getFileIndex() + 1; // 1-based for display
        return width > 0 ? String.format("%0" + width + "d", index) : String.valueOf(index);
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
