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

/**
 * Eine Listener-Schnittstelle, um Fortschritts-Updates von langlaufenden Tasks zu erhalten.
 * Die UI-Schicht kann diese Schnittstelle implementieren, um dem Benutzer detaillierten Fortschritt anzuzeigen.
 */
@FunctionalInterface
public interface TaskProgressListener {

    /**
     * Ein Datenobjekt, das Fortschrittsinformationen enthält.
     *
     * @param taskName Der Name des Tasks.
     * @param status Der aktuelle Status (STARTED oder FINISHED).
     * @param durationMillis Die Dauer des Tasks in Millisekunden (nur bei FINISHED relevant).
     */
    record ProgressUpdate(String taskName, Status status, long durationMillis) {
        public enum Status {STARTED, RUNNING, FAILED, FINISHED}
    }

    void onProgressUpdate(ProgressUpdate update);
}