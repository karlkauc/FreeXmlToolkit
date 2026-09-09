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

import org.fxt.freexmltoolkit.controls.v2.editor.core.Command;

/**
 * Interface for all JSON editing commands.
 *
 * <p>All modifications to the JSON model MUST go through commands so that the
 * {@link JsonCommandManager} can provide undo/redo. Commands store the complete
 * state needed for undo in their constructor or on first execution, and are
 * atomic: {@link #execute()} either applies the whole change and returns
 * {@code true}, or changes nothing and returns {@code false}.</p>
 *
 * @since 2.0
 */
public interface JsonCommand extends Command<JsonCommand> {

    // All methods are inherited from Command<JsonCommand>:
    // execute(), undo(), getDescription(), optionally canUndo(), canMergeWith(), mergeWith()
}
