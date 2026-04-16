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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.GenerationContext;

/**
 * Copies the value from another XPath that was already generated in the same document.
 * <p>
 * Config key: "ref" - the XPath whose generated value should be copied.
 * If the referenced XPath hasn't been generated yet, returns an empty string.
 */
public class XPathRefValueStrategy implements ValueStrategy {

    private static final Logger logger = LogManager.getLogger(XPathRefValueStrategy.class);

    @Override
    public String resolve(XsdExtendedElement element, Map<String, String> config, GenerationContext context) {
        String refXpath = config.get("ref");
        if (refXpath == null || refXpath.isBlank()) {
            logger.warn("XPATH_REF strategy used without 'ref' config key");
            return "";
        }

        String value = context.getGeneratedValue(refXpath);
        if (value == null) {
            logger.debug("Referenced XPath '{}' not yet generated, returning empty", refXpath);
            return "";
        }
        return value;
    }
}
