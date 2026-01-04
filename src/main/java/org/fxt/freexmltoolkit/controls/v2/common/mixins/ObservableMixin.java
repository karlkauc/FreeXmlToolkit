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

package org.fxt.freexmltoolkit.controls.v2.common.mixins;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Mixin interface for PropertyChangeSupport pattern.
 *
 * <p>Classes implementing this interface provide observable property change notifications
 * without exposing the PropertyChangeSupport implementation details. This reduces
 * boilerplate code and provides a consistent API across the codebase.</p>
 *
 * <p>Implementing classes should:</p>
 * <ol>
 *   <li>Create a private field: {@code private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);}</li>
 *   <li>Implement {@link #getPropertyChangeSupport()} to return this field</li>
 *   <li>Use the default methods for adding/removing listeners and firing changes</li>
 * </ol>
 *
 * <p>Example implementation:</p>
 * <pre>{@code
 * public class MyClass implements ObservableMixin {
 *     private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
 *
 *     @Override
 *     public PropertyChangeSupport getPropertyChangeSupport() {
 *         return pcs;
 *     }
 *
 *     public void setName(String name) {
 *         String oldValue = this.name;
 *         this.name = name;
 *         firePropertyChange("name", oldValue, name);
 *     }
 * }
 * }</pre>
 *
 * @author Claude Code
 * @since 2.0
 */
public interface ObservableMixin {

    /**
     * Returns the PropertyChangeSupport instance for this observable.
     *
     * <p>Implementing classes should return a private final field initialized as:
     * {@code new PropertyChangeSupport(this)}</p>
     *
     * @return the PropertyChangeSupport instance
     */
    PropertyChangeSupport getPropertyChangeSupport();

    /**
     * Adds a PropertyChangeListener to listen to all property changes.
     *
     * @param listener the listener to add
     */
    default void addPropertyChangeListener(PropertyChangeListener listener) {
        getPropertyChangeSupport().addPropertyChangeListener(listener);
    }

    /**
     * Removes a PropertyChangeListener.
     *
     * @param listener the listener to remove
     */
    default void removePropertyChangeListener(PropertyChangeListener listener) {
        getPropertyChangeSupport().removePropertyChangeListener(listener);
    }

    /**
     * Adds a PropertyChangeListener for a specific property.
     *
     * @param propertyName the name of the property
     * @param listener the listener to add
     */
    default void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        getPropertyChangeSupport().addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Removes a PropertyChangeListener for a specific property.
     *
     * @param propertyName the name of the property
     * @param listener the listener to remove
     */
    default void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        getPropertyChangeSupport().removePropertyChangeListener(propertyName, listener);
    }

    /**
     * Fires a property change event.
     *
     * @param propertyName the name of the property
     * @param oldValue the old value
     * @param newValue the new value
     */
    default void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        getPropertyChangeSupport().firePropertyChange(propertyName, oldValue, newValue);
    }

    /**
     * Fires a property change event for an integer property.
     *
     * @param propertyName the name of the property
     * @param oldValue the old value
     * @param newValue the new value
     */
    default void firePropertyChange(String propertyName, int oldValue, int newValue) {
        getPropertyChangeSupport().firePropertyChange(propertyName, oldValue, newValue);
    }

    /**
     * Fires a property change event for a boolean property.
     *
     * @param propertyName the name of the property
     * @param oldValue the old value
     * @param newValue the new value
     */
    default void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
        getPropertyChangeSupport().firePropertyChange(propertyName, oldValue, newValue);
    }

    /**
     * Checks if there are any listeners registered (for any property or all properties).
     *
     * @return true if there are listeners, false otherwise
     */
    default boolean hasListeners() {
        return getPropertyChangeSupport().hasListeners(null);
    }

    /**
     * Checks if there are any listeners registered for a specific property.
     *
     * @param propertyName the name of the property
     * @return true if there are listeners for this property, false otherwise
     */
    default boolean hasListeners(String propertyName) {
        return getPropertyChangeSupport().hasListeners(propertyName);
    }
}
