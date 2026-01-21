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

package org.fxt.freexmltoolkit.controls.jsoneditor.model;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Represents a JSON primitive value (string, number, boolean, null).
 */
public class JsonPrimitive extends JsonNode {

    private Object value;
    private JsonNodeType type;

    /**
     * Creates a new JSON null primitive.
     */
    public JsonPrimitive() {
        this.type = JsonNodeType.NULL;
        this.value = null;
    }

    /**
     * Creates a new JSON string primitive.
     *
     * @param value the string value
     */
    public JsonPrimitive(String value) {
        this.type = JsonNodeType.STRING;
        this.value = value;
    }

    /**
     * Creates a new JSON number primitive.
     *
     * @param value the numeric value
     */
    public JsonPrimitive(Number value) {
        this.type = JsonNodeType.NUMBER;
        this.value = value;
    }

    /**
     * Creates a new JSON boolean primitive.
     *
     * @param value the boolean value
     */
    public JsonPrimitive(Boolean value) {
        this.type = JsonNodeType.BOOLEAN;
        this.value = value;
    }

    /**
     * Creates a null primitive.
     *
     * @return a new JsonPrimitive representing a JSON null value
     */
    public static JsonPrimitive nullValue() {
        return new JsonPrimitive();
    }

    @Override
    public JsonNodeType getNodeType() {
        return type;
    }

    /**
     * Gets the raw value.
     *
     * @return the underlying value object (String, Number, Boolean, or null)
     */
    public Object getValue() {
        return value;
    }

    /**
     * Sets the value and updates the type accordingly.
     *
     * @param value the new value (String, Number, Boolean, or null)
     */
    public void setValue(Object value) {
        Object oldValue = this.value;
        JsonNodeType oldType = this.type;

        if (value == null) {
            this.value = null;
            this.type = JsonNodeType.NULL;
        } else if (value instanceof String) {
            this.value = value;
            this.type = JsonNodeType.STRING;
        } else if (value instanceof Number) {
            this.value = value;
            this.type = JsonNodeType.NUMBER;
        } else if (value instanceof Boolean) {
            this.value = value;
            this.type = JsonNodeType.BOOLEAN;
        } else {
            this.value = value.toString();
            this.type = JsonNodeType.STRING;
        }

        pcs.firePropertyChange("value", oldValue, this.value);
        if (oldType != this.type) {
            pcs.firePropertyChange("type", oldType, this.type);
        }
    }

    /**
     * Checks if this is a null value.
     *
     * @return true if this primitive represents a JSON null value
     */
    public boolean isNull() {
        return type == JsonNodeType.NULL;
    }

    /**
     * Checks if this is a string value.
     *
     * @return true if this primitive contains a string value
     */
    public boolean isString() {
        return type == JsonNodeType.STRING;
    }

    /**
     * Checks if this is a number value.
     *
     * @return true if this primitive contains a numeric value
     */
    public boolean isNumber() {
        return type == JsonNodeType.NUMBER;
    }

    /**
     * Checks if this is a boolean value.
     *
     * @return true if this primitive contains a boolean value
     */
    public boolean isBoolean() {
        return type == JsonNodeType.BOOLEAN;
    }

    /**
     * Gets the value as a string.
     *
     * @return the string representation of the value, or null if the value is null
     */
    public String getAsString() {
        if (value == null) return null;
        return value.toString();
    }

    /**
     * Gets the value as an integer.
     *
     * @return the integer value
     * @throws IllegalStateException if the value is not a number
     */
    public int getAsInt() {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw new IllegalStateException("Not a number");
    }

    /**
     * Gets the value as a long.
     *
     * @return the long value
     * @throws IllegalStateException if the value is not a number
     */
    public long getAsLong() {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        throw new IllegalStateException("Not a number");
    }

    /**
     * Gets the value as a double.
     *
     * @return the double value
     * @throws IllegalStateException if the value is not a number
     */
    public double getAsDouble() {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        throw new IllegalStateException("Not a number");
    }

    /**
     * Gets the value as a BigDecimal.
     *
     * @return the BigDecimal value
     * @throws IllegalStateException if the value is not a number
     */
    public BigDecimal getAsBigDecimal() {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        throw new IllegalStateException("Not a number");
    }

    /**
     * Gets the value as a BigInteger.
     *
     * @return the BigInteger value
     * @throws IllegalStateException if the value is not a number
     */
    public BigInteger getAsBigInteger() {
        if (value instanceof BigInteger) {
            return (BigInteger) value;
        }
        if (value instanceof Number) {
            return new BigInteger(String.valueOf(((Number) value).longValue()));
        }
        throw new IllegalStateException("Not a number");
    }

    /**
     * Gets the value as a boolean.
     *
     * @return the boolean value
     * @throws IllegalStateException if the value is not a boolean
     */
    public boolean getAsBoolean() {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        throw new IllegalStateException("Not a boolean");
    }

    @Override
    public JsonNode deepCopy() {
        JsonPrimitive copy = new JsonPrimitive();
        copy.setKey(getKey());
        copy.value = this.value;
        copy.type = this.type;
        return copy;
    }

    @Override
    public String serialize(int indent, int currentIndent) {
        if (value == null) {
            return "null";
        }
        if (type == JsonNodeType.STRING) {
            return "\"" + escapeString((String) value) + "\"";
        }
        if (type == JsonNodeType.BOOLEAN) {
            return value.toString();
        }
        if (type == JsonNodeType.NUMBER) {
            // Handle special number formats
            if (value instanceof Double) {
                double d = (Double) value;
                if (Double.isInfinite(d) || Double.isNaN(d)) {
                    return "null"; // JSON doesn't support Infinity/NaN
                }
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    return String.valueOf((long) d);
                }
            }
            return value.toString();
        }
        return "null";
    }

    private String escapeString(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    @Override
    public String getDisplayLabel() {
        String key = getKey();
        String valueStr = getValueAsString();

        if (key != null) {
            return key + ": " + valueStr;
        }
        return valueStr;
    }

    @Override
    public String getValueAsString() {
        if (value == null) {
            return "null";
        }
        if (type == JsonNodeType.STRING) {
            String s = (String) value;
            if (s.length() > 50) {
                return "\"" + s.substring(0, 47) + "...\"";
            }
            return "\"" + s + "\"";
        }
        return value.toString();
    }
}
