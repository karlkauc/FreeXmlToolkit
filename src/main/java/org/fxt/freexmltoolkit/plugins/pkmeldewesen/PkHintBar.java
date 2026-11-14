/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
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

package org.fxt.freexmltoolkit.plugins.pkmeldewesen;

import javafx.fxml.FXML;
import javafx.scene.layout.HBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;

/** The one-time bar offering the PK-Meldewesen extension when a report is opened while it is off. */
public final class PkHintBar extends HBox {

    private final Runnable onEnable;
    private final Runnable onDismiss;

    /**
     * @param onEnable  switches the extension on
     * @param onDismiss called when the user declines
     */
    public PkHintBar(Runnable onEnable, Runnable onDismiss) {
        this.onEnable = onEnable;
        this.onDismiss = onDismiss;
        FxmlViews.load(this, "/pages/views/PkHintBar.fxml");
    }

    @FXML
    public void onEnable() {
        close();
        onEnable.run();
    }

    @FXML
    public void onDismiss() {
        close();
        onDismiss.run();
    }

    private void close() {
        if (getParent() instanceof javafx.scene.layout.Pane parent) {
            parent.getChildren().remove(this);
        }
    }
}
