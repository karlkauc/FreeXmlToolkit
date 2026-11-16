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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import javafx.animation.PauseTransition;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxt.freexmltoolkit.controls.shared.utilities.XmlElementAtCaret;
import org.fxt.freexmltoolkit.controls.shell.ThemeManager;
import org.fxt.freexmltoolkit.plugin.ElementDescriber;
import org.fxt.freexmltoolkit.plugin.PluginManager;

/**
 * Shows what the extensions say about an element (field number, name, rule - {@link ElementDescriber})
 * when the mouse rests on its tag name in the text editor: the text counterpart of the Inspector's
 * DOCUMENTATION and the grid's name tooltip. Only for documents up to {@link #MAX_LENGTH} characters, and
 * not on code areas whose extension shows its own hover ({@link #CUSTOM_HOVER}).
 */
public final class DescriberHover {

    /** Code-area property an extension sets when it shows its own hover; this one stays away then. */
    public static final String CUSTOM_HOVER = "fxt.customHover";
    /** Longer documents get no hover (the element path needs a scan of the text). */
    static final int MAX_LENGTH = 2_000_000;

    private static final Map<CodeArea, DescriberHover> INSTALLED = new WeakHashMap<>();

    private final CodeArea area;
    private final Function<ElementDescriber.ElementInfo, List<String>> describer;
    private final Popup popup = new Popup();
    private final VBox box = new VBox(2);
    private final Label title = new Label();
    private final Label body = new Label();
    private final PauseTransition hideDelay = new PauseTransition(javafx.util.Duration.millis(400));

    private DescriberHover(CodeArea area, Function<ElementDescriber.ElementInfo, List<String>> describer) {
        this.area = area;
        this.describer = describer;
        title.getStyleClass().add("fxt-debug-hover-title");
        body.getStyleClass().add("fxt-debug-hover-value");
        body.setWrapText(true);
        body.setMaxWidth(560);
        box.getChildren().addAll(title, body);
        box.getStyleClass().addAll("root", "fxt-debug-hover", "fxt-describer-hover");
        popup.getContent().add(box);
        popup.setAutoHide(true);
        box.setOnMouseEntered(e -> hideDelay.stop());
        box.setOnMouseExited(e -> hideDelay.playFromStart());
        hideDelay.setOnFinished(e -> popup.hide());
    }

    /** Installs the hover on a code area once (later calls do nothing); the extensions' describers answer. */
    public static synchronized void installOn(CodeArea area) {
        installOn(area, info -> PluginManager.getInstance().describe(info));
    }

    static synchronized void installOn(CodeArea area, Function<ElementDescriber.ElementInfo, List<String>> describer) {
        if (area == null || INSTALLED.containsKey(area)) {
            return;
        }
        DescriberHover hover = new DescriberHover(area, describer);
        INSTALLED.put(area, hover);
        if (area.getMouseOverTextDelay() == null) {
            area.setMouseOverTextDelay(Duration.ofMillis(500));
        }
        area.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN, hover::onBegin);
        area.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, e -> hover.hideDelay.playFromStart());
    }

    private void onBegin(MouseOverTextEvent e) {
        if (Boolean.TRUE.equals(area.getProperties().get(CUSTOM_HOVER)) || area.getLength() > MAX_LENGTH) {
            return;
        }
        String[] shown = describeAt(area.getText(), e.getCharacterIndex(), describer);
        if (shown == null) {
            return;
        }
        hideDelay.stop();
        title.setText(shown[0]);
        body.setText(shown[1]);
        body.setVisible(!shown[1].isEmpty());
        body.setManaged(!shown[1].isEmpty());
        show(e.getScreenPosition().getX(), e.getScreenPosition().getY() + 14);
    }

    /** @return the local names from the root to the element whose tag name is at {@code index}, or {@code null} */
    static List<String> pathAt(String text, int index) {
        if (index < 0 || index >= text.length() || !isNameChar(text.charAt(index))) {
            return null;
        }
        int start = index;
        while (start > 0 && isNameChar(text.charAt(start - 1))) {
            start--;
        }
        boolean endTag = start > 1 && text.charAt(start - 1) == '/' && text.charAt(start - 2) == '<';
        if (!(start > 0 && text.charAt(start - 1) == '<') && !endTag) {
            return null; // not a tag name (text, attribute, …)
        }
        return XmlElementAtCaret.at(text, start).map(r -> localNames(r.xpath())).orElse(null);
    }

    /** @return {@code /a:Doc[1]/b[2]} → {@code [Doc, b]} */
    static List<String> localNames(String xpath) {
        List<String> names = new ArrayList<>();
        for (String step : xpath.split("/")) {
            if (step.isEmpty()) {
                continue;
            }
            String name = step.replaceFirst("\\[\\d+]$", "");
            names.add(name.substring(name.indexOf(':') + 1));
        }
        return names;
    }

    /** @return title and body of the descriptions of the element at {@code index}, or {@code null} */
    static String[] describeAt(String text, int index, Function<ElementDescriber.ElementInfo, List<String>> describer) {
        List<String> path = pathAt(text, index);
        if (path == null || path.isEmpty()) {
            return null;
        }
        List<String> texts = describer.apply(new ElementDescriber.ElementInfo(List.copyOf(path), Map.of(), Map.of(), Map.of(),
                "", List.of()));
        if (texts == null || texts.isEmpty()) {
            return null;
        }
        String all = String.join("\n\n", texts).strip();
        int cut = all.indexOf('\n');
        return cut < 0 ? new String[] {all, ""} : new String[] {all.substring(0, cut), all.substring(cut + 1).strip()};
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == ':';
    }

    private void show(double x, double y) {
        if (area.getScene() == null || area.getScene().getWindow() == null) {
            return;
        }
        box.getStyleClass().removeAll("fxt-theme-dark", "fxt-theme-light");
        box.getStyleClass().add(ThemeManager.currentIsDark() ? "fxt-theme-dark" : "fxt-theme-light");
        if (box.getStylesheets().isEmpty()) {
            for (String sheet : new String[] {"/css/design-tokens.css", "/css/unified-shell.css"}) {
                var url = DescriberHover.class.getResource(sheet);
                if (url != null) {
                    box.getStylesheets().add(url.toExternalForm());
                }
            }
        }
        ThemeManager.syncAppearanceSheet(box);
        popup.show(area.getScene().getWindow(), x, y);
    }
}
