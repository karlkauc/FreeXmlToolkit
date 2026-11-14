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

package org.fxt.freexmltoolkit.controls.jsoneditor.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.ui.IntelliSensePopup;
import org.fxt.freexmltoolkit.service.jsonschema.JsonCompletionProvider;
import org.fxt.freexmltoolkit.service.jsonschema.JsonCompletionProvider.Proposal;
import org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaResolver;

/**
 * Schema-driven completion in the JSON text editor: Ctrl+Space anywhere, and automatically
 * after typing {@code "} at a key position or {@code :} after a key. Proposals come from
 * {@link JsonCompletionProvider}; the list is filtered again on every keystroke while open.
 * Does nothing while no JSON Schema is bound.
 */
public final class JsonIntelliSenseEngine {

    /** Above this size the document is not re-read for completion on every keystroke. */
    private static final int MAX_TEXT = 4 * 1024 * 1024;

    private final CodeArea codeArea;
    private final Supplier<JsonSchemaResolver> schema;
    private final IntelliSensePopup popup = new IntelliSensePopup();
    private List<Proposal> shown = List.of();
    /** True while a completion is being inserted (its text change must not re-trigger). */
    private boolean inserting;

    /**
     * @param codeArea the editor's area
     * @param schema   the bound schema, or a supplier of {@code null} while none is bound
     */
    public JsonIntelliSenseEngine(CodeArea codeArea, Supplier<JsonSchemaResolver> schema) {
        this.codeArea = codeArea;
        this.schema = schema;
        popup.setOnItemSelected(this::accept);
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        codeArea.addEventHandler(KeyEvent.KEY_TYPED, this::onKeyTyped);
        codeArea.plainTextChanges().subscribe(change -> {
            if (popup.isShowing() && !inserting) {
                Platform.runLater(() -> show(false));
            }
        });
        codeArea.focusedProperty().addListener((obs, was, is) -> {
            if (!is) {
                popup.hide();
            }
        });
    }

    private void onKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.SPACE && event.isShortcutDown()) {
            show(true);
            event.consume();
            return;
        }
        if (!popup.isShowing()) {
            return;
        }
        switch (event.getCode()) {
            case DOWN -> {
                popup.selectNext();
                event.consume();
            }
            case UP -> {
                popup.selectPrevious();
                event.consume();
            }
            case ENTER, TAB -> {
                CompletionItem selected = popup.getSelectedItem();
                if (selected != null) {
                    accept(selected);
                    event.consume();
                }
            }
            case ESCAPE -> {
                popup.hide();
                event.consume();
            }
            default -> {
                // typing goes on; the list follows through the text listener
            }
        }
    }

    private void onKeyTyped(KeyEvent event) {
        String typed = event.getCharacter();
        if (popup.isShowing() || typed == null || typed.length() != 1) {
            return;
        }
        char c = typed.charAt(0);
        if (c == '"' || c == ':') {
            // after the character is in the text
            Platform.runLater(() -> show(false));
        }
    }

    /**
     * Shows the proposals for the caret position (hides the popup when there are none).
     *
     * @param explicit whether the user asked (Ctrl+Space)
     * @return the proposals shown
     */
    public List<Proposal> show(boolean explicit) {
        JsonSchemaResolver resolver = schema.get();
        if (resolver == null || codeArea.getLength() > MAX_TEXT || codeArea.getScene() == null) {
            popup.hide();
            return List.of();
        }
        List<Proposal> proposals = JsonCompletionProvider.complete(codeArea.getText(), codeArea.getCaretPosition(),
                resolver);
        shown = proposals;
        if (proposals.isEmpty()) {
            popup.hide();
            return proposals;
        }
        List<CompletionItem> items = new ArrayList<>();
        for (int i = 0; i < proposals.size(); i++) {
            items.add(toItem(proposals.get(i), proposals.size() - i));
        }
        if (popup.isShowing()) {
            popup.updateItems(items);
        } else {
            Optional<Bounds> caret = codeArea.getCaretBounds();
            if (caret.isPresent()) {
                popup.show(items, codeArea.getScene().getWindow(), caret.get().getMinX(), caret.get().getMaxY() + 4);
            }
        }
        return proposals;
    }

    /** @return whether the proposal list is open */
    public boolean isShowing() {
        return popup.isShowing();
    }

    /** Hides the proposal list. */
    public void hide() {
        popup.hide();
    }

    private static CompletionItem toItem(Proposal proposal, int relevance) {
        CompletionItemType type = proposal.kind() == JsonCompletionProvider.ProposalKind.PROPERTY
                ? CompletionItemType.PROPERTY : CompletionItemType.VALUE;
        String description = proposal.description();
        if (proposal.deprecated()) {
            description = description == null ? "Deprecated" : "Deprecated - " + description;
        }
        return new CompletionItem.Builder(proposal.label(), proposal.insertText(), type)
                .description(description)
                .dataType(proposal.typeText())
                .required(proposal.required())
                .relevanceScore(relevance)
                .build();
    }

    /** Inserts the proposal behind a list item. */
    private void accept(CompletionItem item) {
        Proposal proposal = shown.stream()
                .filter(p -> p.label().equals(item.getLabel()) && p.insertText().equals(item.getInsertText()))
                .findFirst().orElse(null);
        popup.hide();
        if (proposal == null) {
            return;
        }
        apply(proposal);
    }

    /**
     * Inserts a proposal (one undo step) and places the caret.
     *
     * @param proposal the proposal (its range refers to the current text)
     */
    public void apply(Proposal proposal) {
        int end = Math.min(proposal.replaceEnd(), codeArea.getLength());
        int start = Math.min(proposal.replaceStart(), end);
        inserting = true;
        try {
            codeArea.replaceText(start, end, proposal.insertText());
            codeArea.moveTo(start + proposal.caretOffset());
            codeArea.requestFollowCaret();
        } finally {
            inserting = false;
        }
        org.fxt.freexmltoolkit.service.telemetry.EditStats.feature("json_completion_accepted");
    }
}
