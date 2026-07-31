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

package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.fxt.freexmltoolkit.controls.shell.editor.SchemaRebindPolicy.RebindAction.*;
import static org.fxt.freexmltoolkit.controls.shell.editor.SchemaRebindPolicy.SchemaBindingOrigin.*;
import static org.fxt.freexmltoolkit.controls.shell.editor.SchemaRebindPolicy.decideRebind;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests the validation-time schema rebind decision (see {@link SchemaRebindPolicy}). */
public class SchemaRebindPolicyTest {

    private static final String URL_A = "https://example.org/a.xsd";
    private static final String URL_B = "https://example.org/b.xsd";

    @Test
    @DisplayName("A manual binding is always kept, whatever the buffer declares")
    void manualAlwaysKeeps() {
        assertEquals(KEEP, decideRebind(MANUAL, null, null));
        assertEquals(KEEP, decideRebind(MANUAL, URL_A, null));
        assertEquals(KEEP, decideRebind(MANUAL, URL_A, URL_B));
        assertEquals(KEEP, decideRebind(MANUAL, null, URL_A));
    }

    @Test
    @DisplayName("An unchanged declaration short-circuits to KEEP")
    void unchangedDeclarationKeeps() {
        assertEquals(KEEP, decideRebind(AUTO, URL_A, URL_A));
        assertEquals(KEEP, decideRebind(NONE, null, null));
        assertEquals(KEEP, decideRebind(AUTO, null, null));
    }

    @Test
    @DisplayName("A newly declared or changed location triggers DETECT")
    void changedDeclarationDetects() {
        assertEquals(DETECT, decideRebind(NONE, URL_A, null));
        assertEquals(DETECT, decideRebind(AUTO, URL_B, URL_A));
        assertEquals(DETECT, decideRebind(AUTO, URL_A, null));
    }

    @Test
    @DisplayName("A removed declaration clears an AUTO binding")
    void removedDeclarationClears() {
        assertEquals(CLEAR, decideRebind(AUTO, null, URL_A));
        assertEquals(CLEAR, decideRebind(NONE, null, URL_A));
    }
}
