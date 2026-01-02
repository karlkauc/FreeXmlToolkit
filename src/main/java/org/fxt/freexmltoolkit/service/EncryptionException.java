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

package org.fxt.freexmltoolkit.service;

/**
 * Exception thrown when password encryption or key derivation fails.
 *
 * @since 1.2.3
 */
public class EncryptionException extends Exception {

    /**
     * Constructs a new EncryptionException with the specified detail message.
     *
     * @param message the detail message
     */
    public EncryptionException(String message) {
        super(message);
    }

    /**
     * Constructs a new EncryptionException with the specified detail message
     * and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new EncryptionException with the specified cause.
     *
     * @param cause the cause
     */
    public EncryptionException(Throwable cause) {
        super(cause);
    }
}
