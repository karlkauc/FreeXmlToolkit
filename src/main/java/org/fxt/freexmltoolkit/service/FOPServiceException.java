/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) 2023.
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
 * Exception thrown when PDF generation using Apache FOP fails.
 * This exception wraps underlying FOP, transformer, or I/O errors
 * and provides meaningful error messages to callers.
 */
public class FOPServiceException extends Exception {

    /**
     * Constructs a new FOPServiceException with the specified detail message.
     *
     * @param message the detail message
     */
    public FOPServiceException(String message) {
        super(message);
    }

    /**
     * Constructs a new FOPServiceException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of this exception
     */
    public FOPServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
