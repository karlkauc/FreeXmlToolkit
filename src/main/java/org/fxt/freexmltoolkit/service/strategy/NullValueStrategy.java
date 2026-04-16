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
 * Returns the NIL sentinel, signaling that the element should be rendered
 * with xsi:nil="true" (for nillable elements).
 */
public class NullValueStrategy implements ValueStrategy {

    @Override
    public String resolve(XsdExtendedElement element, Map<String, String> config, GenerationContext context) {
        return NIL_SENTINEL;
    }
}
