/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
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

package org.fxt.freexmltoolkit.controls.jsoneditor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.core.AbstractCommandManager;

/**
 * Manages command execution, undo, and redo operations for JSON editing.
 * Extends {@link AbstractCommandManager} with the {@link JsonCommand} type.
 *
 * @since 2.0
 */
public class JsonCommandManager extends AbstractCommandManager<JsonCommand> {

    /**
     * Constructs a new JsonCommandManager with the default history limit.
     */
    public JsonCommandManager() {
        super();
    }

    /**
     * Constructs a new JsonCommandManager with a custom history limit.
     *
     * @param historyLimit the maximum number of commands to keep
     */
    public JsonCommandManager(int historyLimit) {
        super(historyLimit);
    }
}
