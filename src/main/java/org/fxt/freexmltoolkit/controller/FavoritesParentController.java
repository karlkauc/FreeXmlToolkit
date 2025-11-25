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

package org.fxt.freexmltoolkit.controller;

import java.io.File;

/**
 * Interface for controllers that can act as parent for the FavoritesPanel.
 * This allows the FavoritesPanel to work with different editor controllers
 * (XmlUltimateController, XsdController, etc.) without tight coupling.
 */
public interface FavoritesParentController {

    /**
     * Load a file into the editor.
     * @param file the file to load
     */
    void loadFileToNewTab(File file);

    /**
     * Get the currently active/displayed file.
     * @return the current file, or null if no file is open
     */
    File getCurrentFile();
}
