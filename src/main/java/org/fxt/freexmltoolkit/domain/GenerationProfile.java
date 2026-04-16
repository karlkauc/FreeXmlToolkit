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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A generation profile defines how XML example data should be generated from an XSD schema.
 * It contains a list of XPath rules that control value generation for specific elements/attributes,
 * along with global settings like batch count and output file naming.
 * Profiles can be saved, loaded, and reused across sessions.
 */
public class GenerationProfile {

    private String name;
    private String description;
    private String schemaFile;
    private int batchCount;
    private String fileNamePattern;
    private boolean mandatoryOnly;
    private int maxOccurrences;
    private List<XPathRule> rules;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public GenerationProfile() {
        this.batchCount = 1;
        this.fileNamePattern = "example_{seq:3}.xml";
        this.mandatoryOnly = false;
        this.maxOccurrences = 3;
        this.rules = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public GenerationProfile(String name) {
        this();
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSchemaFile() {
        return schemaFile;
    }

    public void setSchemaFile(String schemaFile) {
        this.schemaFile = schemaFile;
    }

    public int getBatchCount() {
        return batchCount;
    }

    public void setBatchCount(int batchCount) {
        this.batchCount = Math.max(1, batchCount);
    }

    public String getFileNamePattern() {
        return fileNamePattern;
    }

    public void setFileNamePattern(String fileNamePattern) {
        this.fileNamePattern = fileNamePattern;
    }

    public boolean isMandatoryOnly() {
        return mandatoryOnly;
    }

    public void setMandatoryOnly(boolean mandatoryOnly) {
        this.mandatoryOnly = mandatoryOnly;
    }

    public int getMaxOccurrences() {
        return maxOccurrences;
    }

    public void setMaxOccurrences(int maxOccurrences) {
        this.maxOccurrences = Math.max(1, maxOccurrences);
    }

    public List<XPathRule> getRules() {
        return rules;
    }

    public void setRules(List<XPathRule> rules) {
        this.rules = rules != null ? new ArrayList<>(rules) : new ArrayList<>();
    }

    public void addRule(XPathRule rule) {
        this.rules.add(rule);
    }

    public void removeRule(XPathRule rule) {
        this.rules.remove(rule);
    }

    public List<XPathRule> getEnabledRules() {
        return rules.stream().filter(XPathRule::isEnabled).toList();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public GenerationProfile deepCopy() {
        GenerationProfile copy = new GenerationProfile();
        copy.name = this.name;
        copy.description = this.description;
        copy.schemaFile = this.schemaFile;
        copy.batchCount = this.batchCount;
        copy.fileNamePattern = this.fileNamePattern;
        copy.mandatoryOnly = this.mandatoryOnly;
        copy.maxOccurrences = this.maxOccurrences;
        copy.rules = new ArrayList<>();
        for (XPathRule rule : this.rules) {
            copy.rules.add(rule.deepCopy());
        }
        copy.createdAt = this.createdAt;
        copy.updatedAt = this.updatedAt;
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GenerationProfile that = (GenerationProfile) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "GenerationProfile{name='" + name + "', rules=" + rules.size() + ", batch=" + batchCount + "}";
    }
}
