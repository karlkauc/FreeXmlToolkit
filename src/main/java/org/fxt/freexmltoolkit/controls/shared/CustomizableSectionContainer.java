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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A reusable container that wraps sections (typically TitledPanes) and provides:
 * <ul>
 *   <li>Drag-and-drop reordering of sections</li>
 *   <li>Show/hide toggle per section via context menu or settings popup</li>
 *   <li>Persistence of order and visibility via PropertiesService</li>
 *   <li>Reset to defaults</li>
 *   <li>External enable/disable for context-dependent sections (e.g. XSD-dependent)</li>
 * </ul>
 */
public class CustomizableSectionContainer extends VBox {

    private static final Logger logger = LogManager.getLogger(CustomizableSectionContainer.class);
    private static final DataFormat SECTION_DATA_FORMAT = new DataFormat("application/x-section-id");

    private final String containerId;
    private final LinkedHashMap<String, SectionDefinition> registeredSections = new LinkedHashMap<>();
    private final Map<String, VBox> sectionWrappers = new HashMap<>();
    private final Map<String, Boolean> sectionEnabled = new HashMap<>();

    private List<String> currentOrder = new ArrayList<>();
    private Map<String, Boolean> currentVisibility = new HashMap<>();

    private SectionSettingsPopup settingsPopup;
    private String dragSourceId;

    public CustomizableSectionContainer(String containerId) {
        this.containerId = containerId;
        getStyleClass().add("customizable-section-container");
        setSpacing(0);
    }

    /**
     * Registers a section. Call this before {@link #initialize()}.
     */
    public void addSection(SectionDefinition section) {
        registeredSections.put(section.sectionId(), section);
        sectionEnabled.put(section.sectionId(), true);
    }

    /**
     * Builds the UI, loads persisted order/visibility, and renders sections.
     */
    public void initialize() {
        loadPersistedState();
        buildSectionWrappers();
        renderSections();
    }

    /**
     * Enables or disables a section externally (e.g. XSD-dependent sections).
     * A disabled section is hidden regardless of user visibility preference.
     */
    public void setSectionEnabled(String sectionId, boolean enabled) {
        sectionEnabled.put(sectionId, enabled);
        VBox wrapper = sectionWrappers.get(sectionId);
        if (wrapper != null) {
            boolean shouldShow = enabled && currentVisibility.getOrDefault(sectionId, true);
            wrapper.setVisible(shouldShow);
            wrapper.setManaged(shouldShow);
        }
    }

    /**
     * Returns whether a section is currently enabled.
     */
    public boolean isSectionEnabled(String sectionId) {
        return sectionEnabled.getOrDefault(sectionId, true);
    }

    /**
     * Sets user visibility for a section and persists.
     */
    public void setSectionVisible(String sectionId, boolean visible) {
        currentVisibility.put(sectionId, visible);
        VBox wrapper = sectionWrappers.get(sectionId);
        if (wrapper != null) {
            boolean shouldShow = visible && sectionEnabled.getOrDefault(sectionId, true);
            wrapper.setVisible(shouldShow);
            wrapper.setManaged(shouldShow);
        }
        persistState();
    }

    /**
     * Returns user visibility preference for a section.
     */
    public boolean isSectionVisible(String sectionId) {
        return currentVisibility.getOrDefault(sectionId, true);
    }

    /**
     * Resets order and visibility to defaults and persists.
     */
    public void resetToDefaults() {
        currentOrder = registeredSections.values().stream()
                .sorted(Comparator.comparingInt(SectionDefinition::defaultOrder))
                .map(SectionDefinition::sectionId)
                .collect(Collectors.toList());

        currentVisibility.clear();
        for (var entry : registeredSections.entrySet()) {
            currentVisibility.put(entry.getKey(), entry.getValue().defaultVisible());
        }

        persistState();
        renderSections();

        if (settingsPopup != null) {
            settingsPopup.refresh(currentOrder, currentVisibility, sectionEnabled);
        }
    }

    /**
     * Creates a settings gear button that opens the settings popup.
     */
    public Button createSettingsButton() {
        Button button = new Button();
        FontIcon gearIcon = new FontIcon("bi-gear");
        gearIcon.setIconSize(14);
        gearIcon.setIconColor(javafx.scene.paint.Color.web("#6c757d"));
        button.setGraphic(gearIcon);
        button.setTooltip(new Tooltip("Customize sections"));
        button.getStyleClass().addAll("settings-gear-button", "flat-button");
        button.setOnAction(e -> showSettingsPopup(button));
        return button;
    }

    /**
     * Returns the current section order (for testing/external use).
     */
    public List<String> getCurrentOrder() {
        return new ArrayList<>(currentOrder);
    }

    /**
     * Returns the container ID used for persistence keys.
     */
    public String getContainerId() {
        return containerId;
    }

    /**
     * Returns the registered section definitions.
     */
    public Map<String, SectionDefinition> getRegisteredSections() {
        return new LinkedHashMap<>(registeredSections);
    }

    /**
     * Moves a section to a new position and persists.
     */
    public void moveSection(String sectionId, int newIndex) {
        currentOrder.remove(sectionId);
        if (newIndex > currentOrder.size()) {
            newIndex = currentOrder.size();
        }
        currentOrder.add(newIndex, sectionId);
        persistState();
        renderSections();
    }

    // --- Private methods ---

    private void loadPersistedState() {
        var props = PropertiesServiceImpl.getInstance();

        // Load order
        String orderStr = props.get("sidebar." + containerId + ".order");
        if (orderStr != null && !orderStr.isBlank()) {
            List<String> persistedOrder = List.of(orderStr.split(","));
            currentOrder = new ArrayList<>();

            // Add persisted sections that still exist
            for (String id : persistedOrder) {
                if (registeredSections.containsKey(id)) {
                    currentOrder.add(id);
                }
            }

            // Append new sections not in persisted order (sorted by defaultOrder)
            registeredSections.values().stream()
                    .filter(s -> !currentOrder.contains(s.sectionId()))
                    .sorted(Comparator.comparingInt(SectionDefinition::defaultOrder))
                    .forEach(s -> currentOrder.add(s.sectionId()));
        } else {
            // No persisted order: use defaults
            currentOrder = registeredSections.values().stream()
                    .sorted(Comparator.comparingInt(SectionDefinition::defaultOrder))
                    .map(SectionDefinition::sectionId)
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        // Load visibility
        for (var entry : registeredSections.entrySet()) {
            String visStr = props.get("sidebar." + containerId + "." + entry.getKey() + ".visible");
            if (visStr != null) {
                currentVisibility.put(entry.getKey(), Boolean.parseBoolean(visStr));
            } else {
                currentVisibility.put(entry.getKey(), entry.getValue().defaultVisible());
            }
        }
    }

    private void persistState() {
        var props = PropertiesServiceImpl.getInstance();

        // Persist order
        String orderStr = String.join(",", currentOrder);
        props.set("sidebar." + containerId + ".order", orderStr);

        // Persist visibility
        for (var entry : currentVisibility.entrySet()) {
            props.set("sidebar." + containerId + "." + entry.getKey() + ".visible",
                    String.valueOf(entry.getValue()));
        }
    }

    private void buildSectionWrappers() {
        for (var entry : registeredSections.entrySet()) {
            String sectionId = entry.getKey();
            SectionDefinition def = entry.getValue();

            VBox wrapper = new VBox();
            wrapper.getStyleClass().add("section-wrapper");
            wrapper.getChildren().add(def.content());
            VBox.setVgrow(def.content(), Priority.SOMETIMES);

            setupDragSource(wrapper, sectionId);
            setupDragTarget(wrapper, sectionId);
            setupContextMenu(wrapper, sectionId, def);

            sectionWrappers.put(sectionId, wrapper);
        }
    }

    private void renderSections() {
        getChildren().clear();
        for (String sectionId : currentOrder) {
            VBox wrapper = sectionWrappers.get(sectionId);
            if (wrapper != null) {
                boolean visible = currentVisibility.getOrDefault(sectionId, true)
                        && sectionEnabled.getOrDefault(sectionId, true);
                wrapper.setVisible(visible);
                wrapper.setManaged(visible);
                getChildren().add(wrapper);
            }
        }
    }

    private void setupDragSource(VBox wrapper, String sectionId) {
        wrapper.setOnDragDetected(event -> {
            Dragboard db = wrapper.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.put(SECTION_DATA_FORMAT, sectionId);
            db.setContent(content);
            dragSourceId = sectionId;

            wrapper.getStyleClass().add("section-dragging");
            event.consume();
        });

        wrapper.setOnDragDone(event -> {
            wrapper.getStyleClass().remove("section-dragging");
            dragSourceId = null;
            event.consume();
        });
    }

    private void setupDragTarget(VBox wrapper, String sectionId) {
        wrapper.setOnDragOver(event -> {
            if (event.getGestureSource() != wrapper && event.getDragboard().hasContent(SECTION_DATA_FORMAT)) {
                event.acceptTransferModes(TransferMode.MOVE);

                // Determine if dropping above or below center
                wrapper.getStyleClass().removeAll("drag-over-above", "drag-over-below");
                double midY = wrapper.getHeight() / 2;
                if (event.getY() < midY) {
                    wrapper.getStyleClass().add("drag-over-above");
                } else {
                    wrapper.getStyleClass().add("drag-over-below");
                }
            }
            event.consume();
        });

        wrapper.setOnDragExited(event -> {
            wrapper.getStyleClass().removeAll("drag-over-above", "drag-over-below");
            event.consume();
        });

        wrapper.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;

            if (db.hasContent(SECTION_DATA_FORMAT)) {
                String sourceId = (String) db.getContent(SECTION_DATA_FORMAT);
                if (!sourceId.equals(sectionId)) {
                    // Determine insert position based on mouse Y
                    double midY = wrapper.getHeight() / 2;
                    int targetIndex = currentOrder.indexOf(sectionId);
                    if (event.getY() >= midY) {
                        targetIndex++;
                    }

                    // Adjust index if source was before target
                    int sourceIndex = currentOrder.indexOf(sourceId);
                    currentOrder.remove(sourceId);
                    if (sourceIndex < targetIndex) {
                        targetIndex--;
                    }
                    currentOrder.add(targetIndex, sourceId);

                    persistState();
                    renderSections();

                    if (settingsPopup != null) {
                        settingsPopup.refresh(currentOrder, currentVisibility, sectionEnabled);
                    }

                    success = true;
                }
            }

            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void setupContextMenu(VBox wrapper, String sectionId, SectionDefinition def) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem hideItem = new MenuItem("Hide \"" + def.displayName() + "\"");
        FontIcon hideIcon = new FontIcon("bi-eye-slash");
        hideIcon.setIconSize(14);
        hideItem.setGraphic(hideIcon);
        hideItem.setOnAction(e -> setSectionVisible(sectionId, false));

        MenuItem customizeItem = new MenuItem("Customize Sections...");
        FontIcon customizeIcon = new FontIcon("bi-gear");
        customizeIcon.setIconSize(14);
        customizeItem.setGraphic(customizeIcon);
        customizeItem.setOnAction(e -> {
            // Find the settings button or use the wrapper as anchor
            showSettingsPopup(wrapper);
        });

        contextMenu.getItems().addAll(hideItem, new SeparatorMenuItem(), customizeItem);

        // Apply to TitledPane header if content is a TitledPane, else on wrapper via event
        Node content = def.content();
        if (content instanceof TitledPane titledPane) {
            titledPane.setContextMenu(contextMenu);
        } else {
            wrapper.setOnContextMenuRequested(event -> {
                contextMenu.show(wrapper, event.getScreenX(), event.getScreenY());
                event.consume();
            });
        }
    }

    private void showSettingsPopup(Node anchor) {
        if (settingsPopup == null) {
            settingsPopup = new SectionSettingsPopup(this);
        }

        // Toggle: close if already open
        if (settingsPopup.isShowing()) {
            settingsPopup.hide();
            return;
        }

        settingsPopup.refresh(currentOrder, currentVisibility, sectionEnabled);
        settingsPopup.show(anchor);
    }
}
