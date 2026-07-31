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

import java.util.Objects;

/**
 * Decides whether validation-time schema reconciliation keeps, re-detects, or clears a
 * document's XSD binding when the schema location declared in the editor buffer changes.
 * Pure logic, extracted from {@link EditorHost} so it is testable without JavaFX.
 */
final class SchemaRebindPolicy {

    /**
     * How the current XSD binding of a document came to be.
     * <ul>
     *   <li>{@link #NONE} — no schema bound; auto-detection may bind one</li>
     *   <li>{@link #AUTO} — bound by auto-detection; a later detection may replace or
     *       clear it when the declared location changes</li>
     *   <li>{@link #MANUAL} — explicitly assigned by the user (status bar, Validation
     *       panel, favorites); auto-detection never overrides or clears it</li>
     * </ul>
     */
    enum SchemaBindingOrigin { NONE, AUTO, MANUAL }

    /** What the validation-time reconcile should do with the binding. */
    enum RebindAction { KEEP, DETECT, CLEAR }

    private SchemaRebindPolicy() {
    }

    /**
     * @param origin       how the current binding was established
     * @param declared     schema location declared in the buffer right now ({@code null} = none)
     * @param lastDetected location the last auto-detection saw ({@code null} = none declared);
     *                     equal values short-circuit so an unchanged document costs no I/O
     */
    static RebindAction decideRebind(SchemaBindingOrigin origin, String declared, String lastDetected) {
        if (origin == SchemaBindingOrigin.MANUAL) {
            return RebindAction.KEEP;
        }
        if (Objects.equals(declared, lastDetected)) {
            return RebindAction.KEEP;
        }
        return declared == null ? RebindAction.CLEAR : RebindAction.DETECT;
    }
}
