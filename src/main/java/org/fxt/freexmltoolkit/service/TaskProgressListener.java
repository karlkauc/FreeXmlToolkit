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
 * A listener interface for receiving progress updates from long-running tasks.
 *
 * <p>The UI layer can implement this interface to display detailed progress information
 * to the user during lengthy operations.</p>
 */
@FunctionalInterface
public interface TaskProgressListener {

    /**
     * A data object containing progress information.
     *
     * @param taskName       the name of the task
     * @param status         the current status (STARTED, RUNNING, FAILED, or FINISHED)
     * @param durationMillis the duration of the task in milliseconds (only relevant when status is FINISHED)
     */
    record ProgressUpdate(String taskName, Status status, long durationMillis) {
        /**
         * The possible states of a task.
         */
        public enum Status {STARTED, RUNNING, FAILED, FINISHED}
    }

    /**
     * Called when a progress update is available.
     *
     * @param update the progress update containing task name, status, and duration
     */
    void onProgressUpdate(ProgressUpdate update);
}