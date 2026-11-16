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

package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.plugin.PluginManager;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * The one-time bar above the editor offering a switched-off extension that recognises the opened document
 * ({@link org.fxt.freexmltoolkit.plugin.FxtPlugin#recognises}). <b>Switch On</b> stores the extension's
 * setting; extensions load at start, so the bar then asks for a restart.
 */
public final class PluginHintBar extends HBox {

    @FXML private Label message;
    @FXML private Button enableButton;
    @FXML private Button dismissButton;

    private final String pluginId;
    private final String pluginName;

    /** Shows the offer for one extension and records that it was offered. */
    public PluginHintBar(String pluginId, String pluginName) {
        this.pluginId = pluginId;
        this.pluginName = pluginName;
        FxmlViews.load(this, "/pages/views/PluginHintBar.fxml");
        message.setText(I18n.t("PluginHintBar.offer", pluginName));
        setting(PluginManager.hintShownKey(pluginId), "true");
    }

    @FXML
    public void onEnable() {
        setting(PluginManager.enabledKey(pluginId), "true");
        message.setText(I18n.t("PluginHintBar.restart", pluginName));
        enableButton.setVisible(false);
        enableButton.setManaged(false);
        dismissButton.setText(I18n.t("PluginHintBar.close"));
    }

    @FXML
    public void onDismiss() {
        if (getParent() instanceof javafx.scene.layout.Pane parent) {
            parent.getChildren().remove(this);
        }
    }

    private static void setting(String key, String value) {
        try {
            ServiceRegistry.get(PropertiesService.class).set(key, value);
        } catch (RuntimeException e) {
            // no settings service (tests)
        }
    }

    // test access
    String messageForTesting() {
        return message.getText();
    }
}
