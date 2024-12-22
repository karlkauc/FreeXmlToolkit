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

import org.fxt.freexmltoolkit.domain.ConnectionResult;

import java.net.URI;

/**
 * The ConnectionService interface provides methods for executing HTTP requests and retrieving text content from URLs.
 */
public interface ConnectionService {

    /**
     * Executes an HTTP request to the specified URL.
     *
     * @param url the URI of the target URL
     * @return a ConnectionResult object containing the details of the HTTP response
     */
    ConnectionResult executeHttpRequest(URI url);

    /**
     * Retrieves the text content from the specified URL.
     *
     * @param uri the URI of the target URL
     * @return a String containing the text content of the URL
     */
    String getTextContentFromURL(URI uri);
}