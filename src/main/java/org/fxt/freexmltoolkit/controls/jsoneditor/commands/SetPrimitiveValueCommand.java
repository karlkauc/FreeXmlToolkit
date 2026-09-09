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

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;

/**
 * Sets the value of a {@link JsonPrimitive}.
 *
 * <p>The original value object is captured at construction time and restored
 * verbatim on undo, so the primitive's type (including JSON {@code null}) and
 * the exact number representation survive an undo.</p>
 *
 * <p>Consecutive edits produced while the user is typing into the same field
 * can be marked as a <em>continuation</em> via {@link #asContinuation()}; the
 * command manager then merges them into one undo step that spans from the
 * first command's old value to the last command's new value.</p>
 *
 * @since 2.0
 */
public class SetPrimitiveValueCommand implements JsonCommand {

    private final JsonPrimitive target;
    private final Object oldValue;
    private final Object newValue;
    private final boolean continuation;

    /**
     * Creates a command that replaces the primitive's value.
     *
     * @param target   the primitive to change
     * @param newValue the new value (String, Number, Boolean or {@code null})
     * @throws IllegalArgumentException if {@code target} is null
     */
    public SetPrimitiveValueCommand(JsonPrimitive target, Object newValue) {
        this(target, newValue, false);
    }

    /**
     * Creates a command that replaces the primitive's value.
     *
     * @param target       the primitive to change
     * @param newValue     the new value (String, Number, Boolean or {@code null})
     * @param continuation true if this edit continues a previous edit of the same
     *                     field (typing) and may be merged with it
     * @throws IllegalArgumentException if {@code target} is null
     */
    public SetPrimitiveValueCommand(JsonPrimitive target, Object newValue, boolean continuation) {
        if (target == null) {
            throw new IllegalArgumentException("Target primitive must not be null");
        }
        this.target = target;
        this.oldValue = target.getValue();
        this.newValue = newValue;
        this.continuation = continuation;
    }

    private SetPrimitiveValueCommand(JsonPrimitive target, Object oldValue, Object newValue, boolean continuation) {
        this.target = target;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.continuation = continuation;
    }

    /**
     * Returns a copy of this command marked as a typing continuation, i.e. one
     * that may be merged into the preceding edit of the same primitive.
     *
     * @return the continuation command
     */
    public SetPrimitiveValueCommand asContinuation() {
        return new SetPrimitiveValueCommand(target, oldValue, newValue, true);
    }

    /**
     * @return true if this command continues a previous edit of the same field
     */
    public boolean isContinuation() {
        return continuation;
    }

    /**
     * @return the primitive changed by this command
     */
    public JsonPrimitive getTarget() {
        return target;
    }

    @Override
    public boolean execute() {
        target.setValue(newValue);
        return true;
    }

    @Override
    public boolean undo() {
        target.setValue(oldValue);
        return true;
    }

    @Override
    public String getDescription() {
        return "Set value to " + describe(newValue);
    }

    @Override
    public boolean canMergeWith(JsonCommand other) {
        return other instanceof SetPrimitiveValueCommand cmd
                && cmd.target == this.target
                && cmd.continuation;
    }

    @Override
    public JsonCommand mergeWith(JsonCommand other) {
        if (!canMergeWith(other)) {
            throw new IllegalArgumentException("Cannot merge commands");
        }
        SetPrimitiveValueCommand cmd = (SetPrimitiveValueCommand) other;
        return new SetPrimitiveValueCommand(target, this.oldValue, cmd.newValue, this.continuation);
    }

    private static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return "\"" + s + "\"";
        }
        return value.toString();
    }
}
