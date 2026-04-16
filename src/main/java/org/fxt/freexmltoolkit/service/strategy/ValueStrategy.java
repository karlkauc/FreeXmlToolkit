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

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.GenerationContext;

/**
 * Strategy interface for generating values during profiled XML generation.
 * Each implementation handles a specific {@link org.fxt.freexmltoolkit.domain.GenerationStrategy}.
 */
public interface ValueStrategy {

    /** Sentinel value indicating the element should be omitted from output. */
    String OMIT_SENTINEL = "__OMIT__";

    /** Sentinel value indicating the element should use xsi:nil="true". */
    String NIL_SENTINEL = "__NIL__";

    /**
     * Resolves a value for the given element based on the strategy configuration.
     *
     * @param element the XSD element to generate a value for (may be null for simple cases)
     * @param config  strategy-specific configuration parameters
     * @param context the current generation context with state tracking
     * @return the generated value, or a sentinel value for special handling
     */
    String resolve(XsdExtendedElement element, Map<String, String> config, GenerationContext context);
}
