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

import org.w3c.dom.ls.LSInput;

import java.io.InputStream;
import java.io.Reader;

/**
 * Simple implementation of {@link LSInput} for schema resolution.
 *
 * <p>This record provides a minimal implementation of the LSInput interface
 * used by XML schema parsers to resolve external resources like xs:import
 * and xs:include references.</p>
 *
 * <p>Key properties:
 * <ul>
 *   <li>{@code publicId} - The public identifier of the resource</li>
 *   <li>{@code systemId} - The system identifier (typically a file path or URL)</li>
 *   <li>{@code baseURI} - The base URI for resolving relative references within this resource</li>
 *   <li>{@code byteStream} - The input stream containing the resource content</li>
 * </ul>
 * </p>
 *
 * <p>The {@code baseURI} is critical for supporting nested imports - when a schema
 * imports another schema that itself has imports, the resolver needs to know the
 * base location of each schema to resolve its relative references correctly.</p>
 */
public record LSInputImpl(
        String publicId,
        String systemId,
        String baseURI,
        InputStream byteStream
) implements LSInput {

    @Override
    public Reader getCharacterStream() {
        return null;
    }

    @Override
    public void setCharacterStream(Reader characterStream) {
        // Immutable record - setter is no-op
    }

    @Override
    public InputStream getByteStream() {
        return byteStream;
    }

    @Override
    public void setByteStream(InputStream byteStream) {
        // Immutable record - setter is no-op
    }

    @Override
    public String getStringData() {
        return null;
    }

    @Override
    public void setStringData(String stringData) {
        // Immutable record - setter is no-op
    }

    @Override
    public String getSystemId() {
        return systemId;
    }

    @Override
    public void setSystemId(String systemId) {
        // Immutable record - setter is no-op
    }

    @Override
    public String getPublicId() {
        return publicId;
    }

    @Override
    public void setPublicId(String publicId) {
        // Immutable record - setter is no-op
    }

    @Override
    public String getBaseURI() {
        return baseURI;
    }

    @Override
    public void setBaseURI(String baseURI) {
        // Immutable record - setter is no-op
    }

    @Override
    public String getEncoding() {
        return "UTF-8";
    }

    @Override
    public void setEncoding(String encoding) {
        // Immutable record - setter is no-op
    }

    @Override
    public boolean getCertifiedText() {
        return false;
    }

    @Override
    public void setCertifiedText(boolean certifiedText) {
        // Immutable record - setter is no-op
    }
}
