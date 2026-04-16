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

package org.fxt.freexmltoolkit.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A rule that specifies how a particular XPath in the generated XML should be populated.
 * Rules are matched against element/attribute paths during XML generation.
 */
public class XPathRule {

    private String xpath;
    private GenerationStrategy strategy;
    private Map<String, String> config;
    private int priority;
    private boolean enabled;

    public XPathRule() {
        this.strategy = GenerationStrategy.AUTO;
        this.config = new HashMap<>();
        this.priority = 0;
        this.enabled = true;
    }

    public XPathRule(String xpath, GenerationStrategy strategy) {
        this();
        this.xpath = xpath;
        this.strategy = strategy;
    }

    public XPathRule(String xpath, GenerationStrategy strategy, Map<String, String> config) {
        this(xpath, strategy);
        if (config != null) {
            this.config = new HashMap<>(config);
        }
    }

    public String getXpath() {
        return xpath;
    }

    public void setXpath(String xpath) {
        this.xpath = xpath;
    }

    public GenerationStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(GenerationStrategy strategy) {
        this.strategy = strategy;
    }

    public Map<String, String> getConfig() {
        return config;
    }

    public void setConfig(Map<String, String> config) {
        this.config = config != null ? new HashMap<>(config) : new HashMap<>();
    }

    public String getConfigValue(String key) {
        return config.get(key);
    }

    public void setConfigValue(String key, String value) {
        config.put(key, value);
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public XPathRule deepCopy() {
        XPathRule copy = new XPathRule();
        copy.xpath = this.xpath;
        copy.strategy = this.strategy;
        copy.config = new HashMap<>(this.config);
        copy.priority = this.priority;
        copy.enabled = this.enabled;
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XPathRule that = (XPathRule) o;
        return priority == that.priority
                && enabled == that.enabled
                && Objects.equals(xpath, that.xpath)
                && strategy == that.strategy
                && Objects.equals(config, that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(xpath, strategy, config, priority, enabled);
    }

    @Override
    public String toString() {
        return "XPathRule{xpath='" + xpath + "', strategy=" + strategy + ", enabled=" + enabled + "}";
    }
}
