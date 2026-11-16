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

package org.fxt.freexmltoolkit.plugins.kagmeldewesen;

import java.math.BigDecimal;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.i18n.I18n;

/** The OPTIONS section of the KAG-Meldewesen panel; stores every valid change at once (the next check uses it). */
public final class KagOptionsPane extends VBox {

    @FXML private TextField sumTolerance;
    @FXML private TextField relativeVarLimit;
    @FXML private CheckBox warn;
    @FXML private TextField warnAt;

    public KagOptionsPane() {
        FxmlViews.load(this, "/pages/panels/KagOptionsPane.fxml");
        KagSettings.Options o = KagSettings.options();
        sumTolerance.setText(o.sumTolerance().toPlainString());
        relativeVarLimit.setText(o.relativeVarLimit().toPlainString());
        warn.setSelected(o.warn());
        warnAt.setText(o.warnAt().toPlainString());
        warnAt.disableProperty().bind(warn.selectedProperty().not());
        sumTolerance.setTooltip(new Tooltip(I18n.t("KagOptionsPane.sumTolerance.tooltip")));
        relativeVarLimit.setTooltip(new Tooltip(I18n.t("KagOptionsPane.relativeVarLimit.tooltip")));
        warnAt.setTooltip(new Tooltip(I18n.t("KagOptionsPane.warnAt.tooltip")));
        sumTolerance.textProperty().addListener((obs, a, b) -> store());
        relativeVarLimit.textProperty().addListener((obs, a, b) -> store());
        warnAt.textProperty().addListener((obs, a, b) -> store());
        warn.selectedProperty().addListener((obs, a, b) -> store());
    }

    /** @return the options shown; an invalid number keeps the stored value */
    KagSettings.Options shown() {
        KagSettings.Options stored = KagSettings.options();
        return new KagSettings.Options(valid(sumTolerance, stored.sumTolerance(), false),
                valid(relativeVarLimit, stored.relativeVarLimit(), true), warn.isSelected(), valid(warnAt, stored.warnAt(), true));
    }

    private static BigDecimal valid(TextField field, BigDecimal fallback, boolean positive) {
        BigDecimal value = KagSettings.parse(field.getText());
        boolean ok = value != null && (!positive || value.signum() > 0);
        field.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("error"), !ok);
        return ok ? value : fallback;
    }

    private void store() {
        KagSettings.setOptions(shown());
    }
}
