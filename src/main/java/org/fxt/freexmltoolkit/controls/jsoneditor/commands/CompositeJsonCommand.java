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

package org.fxt.freexmltoolkit.controls.jsoneditor.commands;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs several JSON commands as one undoable step (e.g. inserting a table column into every
 * object of an array, renaming a column, dropping several properties). Executes the parts in
 * order; when a part fails, the parts already executed are undone again so the document is
 * left as it was. Undo reverts the parts in reverse order.
 */
public final class CompositeJsonCommand implements JsonCommand {

    private final String description;
    private final List<JsonCommand> parts;
    private int executedParts = 0;

    /**
     * @param description the human-readable description of the whole step
     * @param parts       the commands to run, in order (never modified)
     */
    public CompositeJsonCommand(String description, List<? extends JsonCommand> parts) {
        this.description = description;
        this.parts = new ArrayList<>(parts);
    }

    /** @return the commands this step consists of, in execution order */
    public List<JsonCommand> getParts() {
        return List.copyOf(parts);
    }

    /** @return whether the composite has no parts at all */
    public boolean isEmpty() {
        return parts.isEmpty();
    }

    @Override
    public boolean execute() {
        executedParts = 0;
        for (JsonCommand part : parts) {
            if (!part.execute()) {
                rollback();
                return false;
            }
            executedParts++;
        }
        return true;
    }

    /** Undoes the parts executed so far (after a failed part), in reverse order. */
    private void rollback() {
        for (int i = executedParts - 1; i >= 0; i--) {
            parts.get(i).undo();
        }
        executedParts = 0;
    }

    @Override
    public boolean undo() {
        boolean ok = true;
        for (int i = executedParts - 1; i >= 0; i--) {
            ok &= parts.get(i).undo();
        }
        executedParts = 0;
        return ok;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
