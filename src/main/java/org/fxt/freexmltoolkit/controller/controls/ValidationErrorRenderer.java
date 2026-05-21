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
 */

package org.fxt.freexmltoolkit.controller.controls;

import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.xml.sax.SAXParseException;

/**
 * Builds a uniform, colour-coded validation-error presentation that is shared
 * between the single-file and the batch view of the XSD Validation tab.
 * <p>
 * The visual layout mirrors the {@code online_viewer} reference application:
 * a severity badge, the {@code line:column} location, the error message and a
 * source-code snippet (the offending line +/- one line of context, with the
 * offending line highlighted). All colours come from the application CSS theme
 * so the cards stay readable in light and dark mode.
 */
public final class ValidationErrorRenderer {

    private ValidationErrorRenderer() {
        // utility class
    }

    /**
     * Computes the inclusive, 1-based range of source lines to display around
     * an error. The range is clamped to the available lines.
     *
     * @param errorLine  the 1-based line reported by the parser
     * @param radius     number of context lines above and below the error line
     * @param totalLines total number of lines in the source file
     * @return {@code {start, end}} (1-based, inclusive). {@code {0, 0}} means
     * there is nothing to show (no source lines or no usable line number).
     */
    public static int[] contextRange(int errorLine, int radius, int totalLines) {
        if (totalLines <= 0 || errorLine <= 0 || radius < 0) {
            return new int[]{0, 0};
        }
        int center = Math.min(errorLine, totalLines);
        int start = Math.max(1, center - radius);
        int end = Math.min(totalLines, center + radius);
        return new int[]{start, end};
    }

    /**
     * Computes the 1-based source line that should receive the error
     * highlight. This mirrors the clamping done by {@link #contextRange}: when
     * the parser reports a line beyond the end of the file (common for
     * "incomplete content" / "missing required element" errors, especially
     * after the single-file view pretty-formats the document), the highlight
     * is placed on the last available line instead of being silently dropped.
     *
     * @param errorLine  the 1-based line reported by the parser
     * @param totalLines total number of lines in the source file
     * @return the 1-based line to highlight, or {@code 0} when there is
     * nothing to highlight (no source lines or no usable line number)
     */
    public static int highlightLine(int errorLine, int totalLines) {
        if (totalLines <= 0 || errorLine <= 0) {
            return 0;
        }
        return Math.min(errorLine, totalLines);
    }

    /**
     * Builds a single error card.
     *
     * @param displayIndex zero-based index of the error (shown as {@code #n})
     * @param ex           the validation error
     * @param sourceLines  the lines of the validated XML file (may be {@code null})
     * @param goToAction   optional action wired to a "Go to error" button;
     *                     pass {@code null} to omit the button (e.g. in batch mode)
     * @return a styled card node
     */
    public static VBox buildErrorCard(int displayIndex, SAXParseException ex,
                                      List<String> sourceLines, Runnable goToAction) {
        VBox card = new VBox(6);
        card.getStyleClass().add("validation-error-card");

        // --- Header: severity badge + location ---
        Label badge = new Label("ERROR");
        badge.getStyleClass().addAll("validation-severity-badge", "validation-severity-badge-error");

        String location = "line " + ex.getLineNumber() + " : col " + ex.getColumnNumber();
        Label locationLabel = new Label(location);
        locationLabel.getStyleClass().add("validation-error-location");

        HBox header = new HBox(8, badge, locationLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(header);

        // --- Message ---
        Label message = new Label("#" + (displayIndex + 1) + ": " + ex.getLocalizedMessage());
        message.setWrapText(true);
        message.getStyleClass().add("validation-error-message");
        card.getChildren().add(message);

        // --- Source-code context (error line +/- 1) ---
        int total = (sourceLines == null) ? 0 : sourceLines.size();
        int[] range = contextRange(ex.getLineNumber(), 1, total);
        int highlight = highlightLine(ex.getLineNumber(), total);
        if (range[0] > 0) {
            VBox codeBox = new VBox();
            codeBox.getStyleClass().add("validation-code-context");
            for (int ln = range[0]; ln <= range[1]; ln++) {
                String text = sourceLines.get(ln - 1);

                Label number = new Label(Integer.toString(ln));
                number.getStyleClass().add("validation-code-lineno");
                number.setMinWidth(46);
                number.setMaxWidth(46);
                number.setAlignment(Pos.CENTER_RIGHT);

                Label code = new Label(text);
                code.getStyleClass().add("validation-code-text");
                HBox.setHgrow(code, Priority.ALWAYS);

                HBox row = new HBox(10, number, code);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("validation-code-line");
                if (ln == highlight) {
                    row.getStyleClass().add("validation-code-line-error");
                }
                codeBox.getChildren().add(row);
            }
            card.getChildren().add(codeBox);
        }

        // --- Optional "Go to error" action ---
        if (goToAction != null) {
            Button goTo = new Button("Go to error");
            goTo.getStyleClass().add("btn-secondary");
            IconifyIcon icon = new IconifyIcon("bi-box-arrow-up-right");
            icon.setIconSize(14);
            goTo.setGraphic(icon);
            goTo.setOnAction(e -> goToAction.run());
            card.getChildren().add(goTo);
        }

        return card;
    }

    /**
     * Builds a green success card used when validation passes.
     *
     * @param message the success message
     * @return a styled card node
     */
    public static VBox buildSuccessCard(String message) {
        IconifyIcon icon = new IconifyIcon("bi-check-circle-fill");
        icon.setIconSize(18);

        Label label = new Label(message);
        label.setWrapText(true);
        label.setGraphic(icon);
        label.setGraphicTextGap(10);
        label.getStyleClass().add("validation-success-message");

        VBox card = new VBox(label);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("validation-success-card");
        return card;
    }
}
