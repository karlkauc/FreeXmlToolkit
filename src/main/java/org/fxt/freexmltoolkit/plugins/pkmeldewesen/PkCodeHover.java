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

import java.time.Duration;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.animation.PauseTransition;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxt.freexmltoolkit.controls.shell.ThemeManager;

/**
 * Shows the FMA label of a code under the mouse in the text editor of a PK report: row codes
 * ({@code R800-110}), column codes ({@code C800-100}, {@code Wert_C0010PK}, {@code C0010PK}) and
 * template / table codes ({@code PK.006.02.01.01}). A column code is looked up in the template the
 * text is in (the nearest {@code <PK.…} start tag before it).
 */
final class PkCodeHover {

    private static final Map<CodeArea, PkCodeHover> INSTALLED = new WeakHashMap<>();

    static final Pattern ROW = Pattern.compile("R\\d{3}-\\d{3}");
    static final Pattern COLUMN = Pattern.compile("(?:Wert_)?(C\\d{3}-\\d{3}|E?C\\d{4}[A-Z]{0,2})");
    static final Pattern TEMPLATE = Pattern.compile("PK\\.\\d{3}(?:\\.\\d{2})+");
    private static final Pattern TEMPLATE_TAG = Pattern.compile("<(PK\\.\\d{3}(?:\\.\\d{2})+)_");
    /** How far back the enclosing template is searched (a template's open list can be long). */
    private static final int TEMPLATE_SEARCH = 4_000_000;

    private final CodeArea area;
    private final Supplier<PkDictionary> dictionary;
    private final Popup popup = new Popup();
    private final VBox box = new VBox(2);
    private final Label title = new Label();
    private final Label body = new Label();
    private final PauseTransition hideDelay = new PauseTransition(javafx.util.Duration.millis(400));

    private PkCodeHover(CodeArea area, Supplier<PkDictionary> dictionary) {
        this.area = area;
        this.dictionary = dictionary;
        title.getStyleClass().add("fxt-debug-hover-title");
        body.getStyleClass().add("fxt-debug-hover-value");
        body.setWrapText(true);
        body.setMaxWidth(560);
        box.getChildren().addAll(title, body);
        box.getStyleClass().addAll("root", "fxt-debug-hover", "fxt-pk-hover");
        popup.getContent().add(box);
        popup.setAutoHide(true);
        box.setOnMouseEntered(e -> hideDelay.stop());
        box.setOnMouseExited(e -> hideDelay.playFromStart());
        hideDelay.setOnFinished(e -> popup.hide());
    }

    /** Installs the hover on a code area once (later calls do nothing). */
    static synchronized void installOn(CodeArea area, Supplier<PkDictionary> dictionary) {
        if (area == null || INSTALLED.containsKey(area)) {
            return;
        }
        PkCodeHover hover = new PkCodeHover(area, dictionary);
        INSTALLED.put(area, hover);
        if (area.getMouseOverTextDelay() == null) {
            area.setMouseOverTextDelay(Duration.ofMillis(500));
        }
        area.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, hover::onBegin);
        area.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, e -> hover.hideDelay.playFromStart());
    }

    /** Reads parts of a text (the code area's text is not copied as a whole: reports can be 180 MB). */
    interface TextSource {
        int length();

        String text(int start, int end);

        static TextSource of(String text) {
            return new TextSource() {
                @Override public int length() { return text.length(); }
                @Override public String text(int start, int end) { return text.substring(start, end); }
            };
        }
    }

    private void onBegin(MouseOverTextEvent e) {
        TextSource source = new TextSource() {
            @Override public int length() { return area.getLength(); }
            @Override public String text(int start, int end) { return area.getText(start, end); }
        };
        String[] shown = describeAt(source, e.getCharacterIndex(), dictionary.get());
        if (shown == null) {
            return;
        }
        hideDelay.stop();
        title.setText(shown[0]);
        body.setText(shown[1]);
        show(e.getScreenPosition().getX(), e.getScreenPosition().getY() + 14);
    }

    /**
     * @return the title and text of the code at the index, or {@code null} when there is no known code
     */
    static String[] describeAt(TextSource source, int index, PkDictionary dict) {
        if (dict == null || dict.isEmpty() || index < 0 || index >= source.length()) {
            return null;
        }
        int windowStart = Math.max(0, index - 80);
        String text = source.text(windowStart, Math.min(source.length(), index + 80));
        index -= windowStart;
        int start = index;
        while (start > 0 && isCodeChar(text.charAt(start - 1))) {
            start--;
        }
        int end = index;
        while (end < text.length() && isCodeChar(text.charAt(end))) {
            end++;
        }
        if (start == end) {
            return null;
        }
        String word = text.substring(start, end);
        String described = null;
        if (ROW.matcher(word).matches()) {
            described = PkDescriber.factText(dict, PkLocator.templateOfRow(word), word, null, null);
        } else if (TEMPLATE.matcher(word).lookingAt()) {
            // a code, or the element name of a template (PK.006.03_Durchrechnung_…)
            Matcher template = TEMPLATE.matcher(word);
            template.lookingAt();
            if (template.end() == word.length() || word.charAt(template.end()) == '_') {
                described = PkDescriber.templateLine(dict, template.group());
            }
        } else {
            Matcher column = COLUMN.matcher(word);
            if (column.matches()) {
                String template = enclosingTemplate(source, windowStart + start);
                if (template != null) {
                    described = PkDescriber.columnText(dict, template, column.group(1), null);
                }
            }
        }
        if (described == null) {
            return null;
        }
        int cut = described.indexOf('\n');
        return cut < 0 ? new String[] {described, ""} : new String[] {described.substring(0, cut), described.substring(cut + 1).strip()};
    }

    private static boolean isCodeChar(char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_';
    }

    /** @return the code of the nearest template start tag before the offset, or {@code null} */
    static String enclosingTemplate(TextSource source, int offset) {
        int chunk = 65_536;
        int end = Math.min(source.length(), offset + 1);
        int limit = Math.max(0, offset - TEMPLATE_SEARCH);
        while (end > limit) {
            int begin = Math.max(limit, end - chunk);
            // overlap by the longest tag start, so a tag cut by the chunk border is still found
            String part = source.text(begin, Math.min(source.length(), end + 80));
            int at = part.lastIndexOf("<PK.", end - begin);
            while (at >= 0) {
                Matcher m = TEMPLATE_TAG.matcher(part).region(at, Math.min(part.length(), at + 80));
                if (m.lookingAt()) {
                    return m.group(1);
                }
                at = at == 0 ? -1 : part.lastIndexOf("<PK.", at - 1);
            }
            end = begin;
        }
        return null;
    }

    private void show(double x, double y) {
        if (area.getScene() == null || area.getScene().getWindow() == null) {
            return;
        }
        box.getStyleClass().removeAll("fxt-theme-dark", "fxt-theme-light");
        box.getStyleClass().add(ThemeManager.currentIsDark() ? "fxt-theme-dark" : "fxt-theme-light");
        if (box.getStylesheets().isEmpty()) {
            for (String sheet : new String[] {"/css/design-tokens.css", "/css/unified-shell.css"}) {
                var url = PkCodeHover.class.getResource(sheet);
                if (url != null) {
                    box.getStylesheets().add(url.toExternalForm());
                }
            }
        }
        ThemeManager.syncAppearanceSheet(box);
        popup.show(area.getScene().getWindow(), x, y);
    }
}
