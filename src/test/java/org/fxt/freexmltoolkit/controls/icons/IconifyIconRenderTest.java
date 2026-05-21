/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
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

package org.fxt.freexmltoolkit.controls.icons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * Headless (Monocle) rendering checks for {@link IconifyIcon}: confirms the bundled SVG path data
 * is actually accepted by JavaFX {@link SVGPath} (non-empty geometry), that multi-path and
 * basic-shape icons produce paths, that sizing scales the geometry, and that an unknown literal
 * degrades to a placeholder without throwing.
 */
@ExtendWith(ApplicationExtension.class)
class IconifyIconRenderTest {

    @Start
    void start(Stage stage) {
        stage.setScene(new Scene(new StackPane(), 100, 100));
    }

    private IconifyIcon onFx(String literal, double size) throws Exception {
        AtomicReference<IconifyIcon> ref = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            IconifyIcon icon = new IconifyIcon(literal);
            icon.setIconSize(size);
            icon.setIconColor(Color.RED);
            // Force CSS/layout so SVGPath geometry is realized.
            new Scene(new StackPane(icon)).getRoot().applyCss();
            ((StackPane) icon.getParent()).layout();
            ref.set(icon);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "FX task timed out for " + literal);
        return ref.get();
    }

    private Group contentGroup(IconifyIcon icon) {
        // The single managed child is the Group of SVGPaths / placeholder.
        return (Group) icon.getChildrenUnmodifiable().get(0);
    }

    private long svgPathCount(IconifyIcon icon) {
        return contentGroup(icon).getChildrenUnmodifiable().stream()
                .filter(n -> n instanceof SVGPath).count();
    }

    @Test
    @DisplayName("Single-path icon renders with non-empty geometry")
    void singlePathRenders() throws Exception {
        IconifyIcon icon = onFx("bi-save", 16);
        assertEquals(1, svgPathCount(icon), "bi-save should have one path");
        SVGPath p = (SVGPath) contentGroup(icon).getChildrenUnmodifiable().get(0);
        assertFalse(p.getContent().isBlank());
        assertTrue(p.getBoundsInLocal().getWidth() > 0, "path should have width");
        assertEquals(Color.RED, p.getFill());
    }

    @Test
    @DisplayName("Multi-path icon produces multiple SVGPaths")
    void multiPathRenders() throws Exception {
        assertTrue(svgPathCount(onFx("bi-trash", 16)) >= 2, "bi-trash has multiple paths");
    }

    @Test
    @DisplayName("Basic-shape icon (circle) is converted to a path")
    void circleShapeRenders() throws Exception {
        IconifyIcon icon = onFx("bi-circle-fill", 16);
        assertTrue(svgPathCount(icon) >= 1, "bi-circle-fill should convert <circle> to a path");
        assertTrue(contentGroup(icon).getBoundsInLocal().getWidth() > 0);
    }

    @Test
    @DisplayName("iconSize scales the rendered geometry")
    void sizeScales() throws Exception {
        double w16 = onFx("bi-save", 16).getLayoutBounds().getWidth();
        double w32 = onFx("bi-save", 32).getLayoutBounds().getWidth();
        assertTrue(w32 > w16 * 1.5, "32px icon should be markedly larger than 16px (" + w16 + " vs " + w32 + ")");
    }

    @Test
    @DisplayName("Unknown literal renders a placeholder and does not throw")
    void unknownLiteralFallsBack() throws Exception {
        IconifyIcon icon = onFx("bi-does-not-exist-xyz", 16);
        assertEquals(0, svgPathCount(icon), "unknown icon must have no SVG paths");
        assertEquals(1, contentGroup(icon).getChildrenUnmodifiable().size(),
                "unknown icon should render exactly one placeholder node");
    }
}
