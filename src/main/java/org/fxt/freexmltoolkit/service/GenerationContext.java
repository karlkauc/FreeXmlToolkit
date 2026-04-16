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

package org.fxt.freexmltoolkit.service;

import java.util.HashMap;
import java.util.Map;

/**
 * Mutable state tracked during a single XML generation run.
 * Persists across batch files for sequences and enum cycling,
 * but resets per-file values for XPath references within a single document.
 */
public class GenerationContext {

    private final Map<String, Integer> sequenceCounters = new HashMap<>();
    private final Map<String, String> generatedValues = new HashMap<>();
    private final Map<String, Integer> enumCyclePositions = new HashMap<>();
    private int fileIndex;
    private String currentXPath;

    public int nextSequenceValue(String ruleId, int start, int step) {
        int current = sequenceCounters.getOrDefault(ruleId, start - step);
        int next = current + step;
        sequenceCounters.put(ruleId, next);
        return next;
    }

    public int getSequenceValue(String ruleId) {
        return sequenceCounters.getOrDefault(ruleId, 0);
    }

    public void recordGeneratedValue(String xpath, String value) {
        generatedValues.put(xpath, value);
    }

    public String getGeneratedValue(String xpath) {
        return generatedValues.get(xpath);
    }

    public boolean hasGeneratedValue(String xpath) {
        return generatedValues.containsKey(xpath);
    }

    public int nextEnumPosition(String ruleId, int enumSize) {
        if (enumSize <= 0) {
            return 0;
        }
        int current = enumCyclePositions.getOrDefault(ruleId, -1);
        int next = (current + 1) % enumSize;
        enumCyclePositions.put(ruleId, next);
        return next;
    }

    public int getFileIndex() {
        return fileIndex;
    }

    public void setFileIndex(int fileIndex) {
        this.fileIndex = fileIndex;
    }

    public String getCurrentXPath() {
        return currentXPath;
    }

    public void setCurrentXPath(String currentXPath) {
        this.currentXPath = currentXPath;
    }

    /**
     * Resets per-file state. Sequence counters and enum cycle positions
     * persist across files; generated values are cleared since they are
     * only valid within a single document.
     */
    public void resetForNewFile() {
        generatedValues.clear();
        fileIndex++;
    }

    /**
     * Resets all state. Used when starting a completely new generation run.
     */
    public void resetAll() {
        sequenceCounters.clear();
        generatedValues.clear();
        enumCyclePositions.clear();
        fileIndex = 0;
        currentXPath = null;
    }
}
