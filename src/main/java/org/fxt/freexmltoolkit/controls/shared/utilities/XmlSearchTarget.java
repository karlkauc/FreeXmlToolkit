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

package org.fxt.freexmltoolkit.controls.shared.utilities;

/**
 * Abstraction for a component that can be searched with a simple
 * "find next / find previous" interaction.
 *
 * <p>This lets a single search UI (the inline {@code UnifiedSearchBar} or the
 * modal {@code FindReplaceDialog}) drive different editor views — the text
 * editor ({@code XmlCodeEditorV2}) as well as the graphical canvas view
 * ({@code XmlCanvasView}) — without coupling the UI to a concrete view type.</p>
 */
public interface XmlSearchTarget {

    /**
     * Navigates to the next (or previous) match of the given text, selecting and
     * revealing it.
     *
     * @param text    the text to find (case-insensitive); ignored if null/empty
     * @param forward true to move to the next match, false for the previous match
     * @return true if a match was found and navigated to
     */
    boolean find(String text, boolean forward);

    /**
     * Counts all matches of the given text and navigates to the first one.
     *
     * @param text the text to find (case-insensitive)
     * @return the number of matches, or {@code -1} if the count is unknown
     */
    int findAll(String text);

    /**
     * Clears any internal search state (e.g. the cached match list).
     */
    void clearSearch();
}
