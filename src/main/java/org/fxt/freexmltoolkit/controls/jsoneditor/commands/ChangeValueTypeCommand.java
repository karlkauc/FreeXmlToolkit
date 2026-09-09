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

import java.math.BigInteger;

import org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonValueParser;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeType;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;

/**
 * Converts a {@link JsonPrimitive} to another primitive type.
 *
 * <p>Conversion rules:</p>
 * <ul>
 *   <li>to {@code NUMBER}: the text form is parsed ({@link BigInteger} without
 *       fraction/exponent, {@link java.math.BigDecimal} otherwise); unparsable
 *       text becomes {@code 0}</li>
 *   <li>to {@code STRING}: the text form ({@code getAsString()}); JSON null
 *       becomes the empty string</li>
 *   <li>to {@code BOOLEAN}: {@link Boolean#parseBoolean} of the text form</li>
 *   <li>to {@code NULL}: always {@code null}</li>
 * </ul>
 *
 * <p>Converting to the type the primitive already has is a no-op and returns
 * {@code false}. Undo restores the original value object verbatim.</p>
 *
 * @since 2.0
 */
public class ChangeValueTypeCommand implements JsonCommand {

    private final JsonPrimitive target;
    private final JsonNodeType newType;
    private final Object oldValue;

    /**
     * Creates a type-change command.
     *
     * @param target  the primitive to convert
     * @param newType the primitive type to convert to
     * @throws IllegalArgumentException if target is null or newType is not a primitive type
     */
    public ChangeValueTypeCommand(JsonPrimitive target, JsonNodeType newType) {
        if (target == null) {
            throw new IllegalArgumentException("Target primitive must not be null");
        }
        if (newType != JsonNodeType.STRING && newType != JsonNodeType.NUMBER
                && newType != JsonNodeType.BOOLEAN && newType != JsonNodeType.NULL) {
            throw new IllegalArgumentException("Not a primitive type: " + newType);
        }
        this.target = target;
        this.newType = newType;
        this.oldValue = target.getValue();
    }

    @Override
    public boolean execute() {
        if (target.getNodeType() == newType) {
            return false;
        }
        target.setValue(convert(target, newType));
        return true;
    }

    @Override
    public boolean undo() {
        target.setValue(oldValue);
        return true;
    }

    @Override
    public String getDescription() {
        return "Change type to " + newType.name().toLowerCase();
    }

    private static Object convert(JsonPrimitive source, JsonNodeType newType) {
        String text = source.getAsString();
        return switch (newType) {
            case NULL -> null;
            case STRING -> text == null ? "" : text;
            case BOOLEAN -> Boolean.parseBoolean(text);
            case NUMBER -> {
                try {
                    yield JsonValueParser.parseNumber(text);
                } catch (IllegalArgumentException e) {
                    yield BigInteger.ZERO;
                }
            }
            default -> throw new IllegalStateException("Unexpected type: " + newType);
        };
    }
}
