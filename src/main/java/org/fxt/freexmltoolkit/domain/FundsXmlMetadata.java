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

package org.fxt.freexmltoolkit.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Metadata about the locally cached FundsXML content
 * (~/.freeXmlToolkit/fundsxml/metadata.json).
 *
 * <p>Tracks which schema versions are installed, when each part of the cache was last
 * refreshed from GitHub, and which version is currently marked as active. Plain mutable
 * fields keep Gson (de)serialization trivial.
 */
public class FundsXmlMetadata {

    /** Installed schema versions, e.g. ["4.2.10", "4.2.9"]. */
    private List<String> installedSchemaVersions = new ArrayList<>();

    /** The version currently selected as active (used by quick-validate, etc.). */
    private String activeSchemaVersion;

    /** ISO-8601 timestamp of the last successful download/refresh of schemas. */
    private String schemaLastUpdated;

    /** ISO-8601 timestamp of the last successful download/refresh of examples. */
    private String examplesLastUpdated;

    /** ISO-8601 timestamp of the last successful download/refresh of Schematron rules. */
    private String schematronLastUpdated;

    /** ISO-8601 timestamp of the last successful download/refresh of XPath/XQuery snippets. */
    private String queriesLastUpdated;

    /** ISO-8601 timestamp of the last "check GitHub for newer release" call. */
    private String lastUpdateCheck;

    /** The latest schema tag observed on GitHub during the last update check. */
    private String latestKnownSchemaTag;

    public List<String> getInstalledSchemaVersions() {
        return installedSchemaVersions == null ? new ArrayList<>() : installedSchemaVersions;
    }

    public void setInstalledSchemaVersions(List<String> installedSchemaVersions) {
        this.installedSchemaVersions = installedSchemaVersions == null ? new ArrayList<>() : installedSchemaVersions;
    }

    public String getActiveSchemaVersion() {
        return activeSchemaVersion;
    }

    public void setActiveSchemaVersion(String activeSchemaVersion) {
        this.activeSchemaVersion = activeSchemaVersion;
    }

    public String getSchemaLastUpdated() {
        return schemaLastUpdated;
    }

    public void setSchemaLastUpdated(String schemaLastUpdated) {
        this.schemaLastUpdated = schemaLastUpdated;
    }

    public String getExamplesLastUpdated() {
        return examplesLastUpdated;
    }

    public void setExamplesLastUpdated(String examplesLastUpdated) {
        this.examplesLastUpdated = examplesLastUpdated;
    }

    public String getSchematronLastUpdated() {
        return schematronLastUpdated;
    }

    public void setSchematronLastUpdated(String schematronLastUpdated) {
        this.schematronLastUpdated = schematronLastUpdated;
    }

    public String getQueriesLastUpdated() {
        return queriesLastUpdated;
    }

    public void setQueriesLastUpdated(String queriesLastUpdated) {
        this.queriesLastUpdated = queriesLastUpdated;
    }

    public String getLastUpdateCheck() {
        return lastUpdateCheck;
    }

    public void setLastUpdateCheck(String lastUpdateCheck) {
        this.lastUpdateCheck = lastUpdateCheck;
    }

    public String getLatestKnownSchemaTag() {
        return latestKnownSchemaTag;
    }

    public void setLatestKnownSchemaTag(String latestKnownSchemaTag) {
        this.latestKnownSchemaTag = latestKnownSchemaTag;
    }
}
