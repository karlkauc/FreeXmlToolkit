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

import org.xml.sax.SAXParseException;

import java.io.File;
import java.util.List;

/**
 * Interface for XML validation services.
 *
 * <p>This interface defines the contract for different XML parser implementations
 * that can be used for XML schema validation.</p>
 */
public interface XmlValidationService {

    /**
     * Validates an XML string against a schema file.
     *
     * @param xmlString  the XML content to validate
     * @param schemaFile the XSD schema file to validate against
     * @return a list of validation errors (empty if valid)
     */
    List<SAXParseException> validateText(String xmlString, File schemaFile);

    /**
     * Gets the name of the validation service implementation.
     *
     * @return the name of the validator
     */
    String getValidatorName();

    /**
     * Checks if the service supports XSD 1.1 features.
     *
     * @return true if XSD 1.1 is supported, false otherwise
     */
    boolean supportsXsd11();
}
