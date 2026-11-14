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

package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.plugin.ElementDescriber;
import org.fxt.freexmltoolkit.plugin.PluginManager;

/**
 * The plugins' descriptions of an element of the XML editor model ({@link ElementDescriber}),
 * shown in the Inspector and in the grid's name tooltip.
 */
public final class ElementDescriptions {

    private ElementDescriptions() {
    }

    /** @return the plugins' descriptions joined by blank lines, or {@code null} when none knows the element */
    public static String of(XmlElement element) {
        if (element == null || !PluginManager.getInstance().hasElementDescribers()) {
            return null;
        }
        List<String> texts = PluginManager.getInstance().describe(info(element));
        return texts.isEmpty() ? null : String.join("\n\n", texts);
    }

    /** @return what a describer gets to know about the element */
    public static ElementDescriber.ElementInfo info(XmlElement element) {
        List<String> path = new ArrayList<>();
        List<Map<String, String>> ancestors = new ArrayList<>();
        for (XmlNode node = element.getParent(); node instanceof XmlElement parent; node = parent.getParent()) {
            path.add(parent.getName());
            ancestors.add(Map.copyOf(parent.getAttributes()));
        }
        Collections.reverse(path);
        Collections.reverse(ancestors);
        path.add(element.getName());
        Map<String, String> parentTexts = element.getParent() instanceof XmlElement parent ? leafTexts(parent) : Map.of();
        String text = element.hasElementChildren() ? "" : String.valueOf(element.getTextContent()).strip();
        return new ElementDescriber.ElementInfo(List.copyOf(path), Map.copyOf(element.getAttributes()),
                leafTexts(element), parentTexts, text, List.copyOf(ancestors));
    }

    private static Map<String, String> leafTexts(XmlElement element) {
        Map<String, String> texts = new LinkedHashMap<>();
        for (XmlElement child : element.getChildElements()) {
            if (!child.hasElementChildren()) {
                texts.putIfAbsent(child.getName(), String.valueOf(child.getTextContent()).strip());
            }
        }
        return Collections.unmodifiableMap(texts);
    }
}
