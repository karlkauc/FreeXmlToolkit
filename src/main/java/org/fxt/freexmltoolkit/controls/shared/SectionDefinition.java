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

package org.fxt.freexmltoolkit.controls.shared;

import javafx.scene.Node;

/**
 * Defines a section within a {@link CustomizableSectionContainer}.
 *
 * @param sectionId      stable identifier for persistence (must not change across versions)
 * @param displayName    human-readable name shown in the settings popup
 * @param content        the JavaFX node (typically a TitledPane) to display
 * @param defaultVisible whether this section is visible by default
 * @param defaultOrder   the default sort position (lower = higher in list)
 */
public record SectionDefinition(
        String sectionId,
        String displayName,
        Node content,
        boolean defaultVisible,
        int defaultOrder
) {
}
