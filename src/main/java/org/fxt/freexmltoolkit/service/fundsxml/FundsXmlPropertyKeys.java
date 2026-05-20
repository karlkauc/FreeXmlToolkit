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

package org.fxt.freexmltoolkit.service.fundsxml;

/**
 * Property keys for the FundsXML Extensions feature. These keys live in the standard
 * {@code FreeXmlToolkit.properties} file and are read/written through the existing
 * {@code PropertiesService}.
 */
public final class FundsXmlPropertyKeys {

    /** Master toggle. When false, all FundsXML UI surfaces are hidden. */
    public static final String ENABLED = "fundsxml.enabled";

    /** The active schema version, e.g. "4.2.10". Empty when nothing is downloaded yet. */
    public static final String ACTIVE_VERSION = "fundsxml.active.version";

    /** ISO-8601 timestamp of the last "check GitHub for newer release" call. */
    public static final String LAST_UPDATE_CHECK = "fundsxml.last.update.check";

    /** Whether the background periodic update check runs. */
    public static final String UPDATE_CHECK_ENABLED = "fundsxml.update.check.enabled";

    /** Comma-separated list of installed schema versions (mirror of disk state). */
    public static final String LAST_DOWNLOADED_VERSIONS = "fundsxml.last.downloaded.versions";

    private FundsXmlPropertyKeys() {
    }
}
