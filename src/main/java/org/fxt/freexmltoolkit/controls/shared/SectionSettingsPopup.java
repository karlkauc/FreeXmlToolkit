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

package org.fxt.freexmltoolkit.controls.shared;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A popup window that displays section settings for a {@link CustomizableSectionContainer}.
 * Shows checkboxes for visibility and supports drag-and-drop reordering.
 */
public class SectionSettingsPopup {

    private static final DataFormat POPUP_SECTION_FORMAT = new DataFormat("application/x-popup-section-id");

    private final CustomizableSectionContainer container;
    private final Popup popup;
    private final VBox content;
    private final VBox itemsBox;
    private List<String> localOrder;
    private String dragSourceId;

    public SectionSettingsPopup(CustomizableSectionContainer container) {
        this.container = container;
        this.popup = new Popup();
        this.popup.setAutoHide(true);

        content = new VBox(8);
        content.getStyleClass().add("settings-popup");
        content.setPadding(new Insets(12));
        content.setMinWidth(220);
        content.setMaxWidth(300);

        // Header
        Label header = new Label("Customize Sections");
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        itemsBox = new VBox(2);

        // Reset button
        Button resetButton = new Button("Reset to Defaults");
        FontIcon resetIcon = new FontIcon("bi-arrow-counterclockwise");
        resetIcon.setIconSize(14);
        resetButton.setGraphic(resetIcon);
        resetButton.getStyleClass().add("settings-popup-reset-button");
        resetButton.setMaxWidth(Double.MAX_VALUE);
        resetButton.setOnAction(e -> {
            container.resetToDefaults();
            popup.hide();
        });

        content.getChildren().addAll(header, new Separator(), itemsBox, new Separator(), resetButton);
        popup.getContent().add(content);
    }

    /**
     * Refreshes the popup content with the current order and visibility state.
     */
    public void refresh(List<String> order, Map<String, Boolean> visibility,
                        Map<String, Boolean> enabled) {
        this.localOrder = new ArrayList<>(order);
        itemsBox.getChildren().clear();

        Map<String, SectionDefinition> sections = container.getRegisteredSections();

        // Filter to valid sections first to get accurate count
        List<String> validIds = localOrder.stream()
                .filter(sections::containsKey)
                .toList();

        for (int i = 0; i < validIds.size(); i++) {
            String sectionId = validIds.get(i);
            SectionDefinition def = sections.get(sectionId);

            boolean isEnabled = enabled.getOrDefault(sectionId, true);
            boolean isVisible = visibility.getOrDefault(sectionId, true);

            HBox row = createRow(sectionId, def, isVisible, isEnabled, i, validIds.size());
            itemsBox.getChildren().add(row);
        }
    }

    public void show(Node anchor) {
        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        if (bounds != null) {
            popup.show(anchor, bounds.getMinX(), bounds.getMaxY() + 4);
        }
    }

    public void hide() {
        popup.hide();
    }

    private HBox createRow(String sectionId, SectionDefinition def,
                           boolean isVisible, boolean isEnabled, int index, int totalCount) {
        HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 6, 4, 6));
        row.getStyleClass().add("settings-popup-item");

        // Drag handle
        FontIcon dragHandle = new FontIcon("bi-grip-vertical");
        dragHandle.setIconSize(12);
        dragHandle.getStyleClass().add("section-drag-handle");

        // Checkbox
        CheckBox checkBox = new CheckBox(def.displayName());
        checkBox.setSelected(isVisible);
        checkBox.setDisable(!isEnabled);
        if (!isEnabled) {
            checkBox.setTooltip(new Tooltip("Requires additional context to be available"));
        }
        checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            container.setSectionVisible(sectionId, newVal);
        });
        HBox.setHgrow(checkBox, Priority.ALWAYS);

        // Move up button
        Button moveUpBtn = new Button();
        FontIcon upIcon = new FontIcon("bi-chevron-up");
        upIcon.setIconSize(12);
        moveUpBtn.setGraphic(upIcon);
        moveUpBtn.getStyleClass().add("section-move-button");
        moveUpBtn.setTooltip(new Tooltip("Move up"));
        moveUpBtn.setDisable(index == 0);
        moveUpBtn.setOnAction(e -> moveAndRefresh(sectionId, index - 1));

        // Move down button
        Button moveDownBtn = new Button();
        FontIcon downIcon = new FontIcon("bi-chevron-down");
        downIcon.setIconSize(12);
        moveDownBtn.setGraphic(downIcon);
        moveDownBtn.getStyleClass().add("section-move-button");
        moveDownBtn.setTooltip(new Tooltip("Move down"));
        moveDownBtn.setDisable(index == totalCount - 1);
        moveDownBtn.setOnAction(e -> moveAndRefresh(sectionId, index + 1));

        row.getChildren().addAll(dragHandle, checkBox, moveUpBtn, moveDownBtn);

        // Drag-and-drop within the popup
        setupPopupDragSource(row, sectionId);
        setupPopupDragTarget(row, sectionId);

        return row;
    }

    private void moveAndRefresh(String sectionId, int newIndex) {
        localOrder.remove(sectionId);
        localOrder.add(newIndex, sectionId);
        container.moveSection(sectionId, newIndex);

        Map<String, Boolean> vis = new HashMap<>();
        Map<String, Boolean> en = new HashMap<>();
        for (String id : localOrder) {
            vis.put(id, container.isSectionVisible(id));
            en.put(id, container.isSectionEnabled(id));
        }
        refresh(localOrder, vis, en);
    }

    private void setupPopupDragSource(HBox row, String sectionId) {
        row.setOnDragDetected(event -> {
            var db = row.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent cc = new ClipboardContent();
            cc.put(POPUP_SECTION_FORMAT, sectionId);
            db.setContent(cc);
            dragSourceId = sectionId;
            row.setOpacity(0.5);
            event.consume();
        });

        row.setOnDragDone(event -> {
            row.setOpacity(1.0);
            dragSourceId = null;
            event.consume();
        });
    }

    private void setupPopupDragTarget(HBox row, String sectionId) {
        row.setOnDragOver(event -> {
            if (event.getGestureSource() != row && event.getDragboard().hasContent(POPUP_SECTION_FORMAT)) {
                event.acceptTransferModes(TransferMode.MOVE);
                row.getStyleClass().removeAll("drag-over-above", "drag-over-below");
                double midY = row.getHeight() / 2;
                if (event.getY() < midY) {
                    row.getStyleClass().add("drag-over-above");
                } else {
                    row.getStyleClass().add("drag-over-below");
                }
            }
            event.consume();
        });

        row.setOnDragExited(event -> {
            row.getStyleClass().removeAll("drag-over-above", "drag-over-below");
            event.consume();
        });

        row.setOnDragDropped(event -> {
            var db = event.getDragboard();
            boolean success = false;

            if (db.hasContent(POPUP_SECTION_FORMAT)) {
                String sourceId = (String) db.getContent(POPUP_SECTION_FORMAT);
                if (!sourceId.equals(sectionId)) {
                    double midY = row.getHeight() / 2;
                    int targetIndex = localOrder.indexOf(sectionId);
                    if (event.getY() >= midY) {
                        targetIndex++;
                    }

                    int sourceIndex = localOrder.indexOf(sourceId);
                    localOrder.remove(sourceId);
                    if (sourceIndex < targetIndex) {
                        targetIndex--;
                    }
                    localOrder.add(targetIndex, sourceId);

                    // Apply to container
                    container.moveSection(sourceId, targetIndex);

                    // Refresh popup display
                    Map<String, Boolean> vis = new HashMap<>();
                    Map<String, Boolean> en = new HashMap<>();
                    for (String id : localOrder) {
                        vis.put(id, container.isSectionVisible(id));
                        en.put(id, container.isSectionEnabled(id));
                    }
                    refresh(localOrder, vis, en);

                    success = true;
                }
            }

            event.setDropCompleted(success);
            event.consume();
        });
    }
}
