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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.GenerationContext;

/**
 * Generates auto-incrementing values using a configurable pattern.
 * <p>
 * Config keys:
 * <ul>
 *   <li>"pattern" - template string with {seq:N} placeholder, where N is the zero-pad width
 *       (e.g., "ID-{seq:4}" produces "ID-0001", "ID-0002", ...)</li>
 *   <li>"start" - starting value (default: 1)</li>
 *   <li>"step" - increment step (default: 1)</li>
 * </ul>
 */
public class SequenceValueStrategy implements ValueStrategy {

    private static final Pattern SEQ_PATTERN = Pattern.compile("\\{seq(?::(\\d+))?}");

    @Override
    public String resolve(XsdExtendedElement element, Map<String, String> config, GenerationContext context) {
        String pattern = config.getOrDefault("pattern", "{seq}");
        int start = parseIntOrDefault(config.get("start"), 1);
        int step = parseIntOrDefault(config.get("step"), 1);

        String ruleId = context.getCurrentXPath() != null ? context.getCurrentXPath() : "default";
        int value = context.nextSequenceValue(ruleId, start, step);

        return replaceSequencePlaceholders(pattern, value);
    }

    static String replaceSequencePlaceholders(String pattern, int value) {
        Matcher matcher = SEQ_PATTERN.matcher(pattern);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String widthStr = matcher.group(1);
            int width = widthStr != null ? Integer.parseInt(widthStr) : 0;
            String replacement = width > 0
                    ? String.format("%0" + width + "d", value)
                    : String.valueOf(value);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        // If no placeholder was found, just return the value
        if (result.toString().equals(pattern)) {
            return String.valueOf(value);
        }
        return result.toString();
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
