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
 * Progress reporting hook for FundsXML download operations.
 *
 * <p>Implementations should be cheap and non-blocking — callbacks may fire many times
 * per second on the I/O thread. UI updates must hop to the JavaFX application thread
 * via {@code Platform.runLater}.
 */
@FunctionalInterface
public interface DownloadProgressCallback {

    /**
     * Reports progress of an in-flight operation.
     *
     * @param stage           High-level phase, e.g. {@code "Fetching release info"},
     *                        {@code "Downloading schema"}, {@code "Extracting"}.
     * @param bytesDownloaded Bytes transferred so far. {@code -1} when not applicable
     *                        (e.g. during JSON metadata fetches).
     * @param totalBytes      Total expected bytes, or {@code -1} if unknown.
     * @param message         Human-readable status text suitable for display.
     */
    void onProgress(String stage, long bytesDownloaded, long totalBytes, String message);

    /** No-op callback for code paths that don't need progress reporting. */
    DownloadProgressCallback NO_OP = (stage, bytes, total, msg) -> {
    };
}
