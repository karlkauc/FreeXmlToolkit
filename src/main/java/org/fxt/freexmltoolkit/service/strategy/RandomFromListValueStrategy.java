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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.GenerationContext;

/**
 * Picks a random value from a user-defined comma-separated list.
 * <p>
 * Config key: "values" - comma-separated list of possible values
 * (e.g., "Mueller,Schmidt,Huber").
 */
public class RandomFromListValueStrategy implements ValueStrategy {

    @Override
    public String resolve(XsdExtendedElement element, Map<String, String> config, GenerationContext context) {
        String valuesStr = config.get("values");
        if (valuesStr == null || valuesStr.isBlank()) {
            return "";
        }

        List<String> values = Arrays.stream(valuesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (values.isEmpty()) {
            return "";
        }

        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }
}
