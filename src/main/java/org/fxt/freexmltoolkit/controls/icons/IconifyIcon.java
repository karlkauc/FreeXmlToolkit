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

package org.fxt.freexmltoolkit.controls.icons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.PaintConverter;
import javafx.css.converter.SizeConverter;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

/**
 * A JavaFX icon node backed by bundled <a href="https://iconify.design/">Iconify</a> SVG data,
 * resolved through {@link IconifyIconService}.
 * <p>
 * Drop-in replacement for Ikonli's {@code FontIcon}: it exposes the same {@code iconLiteral},
 * {@code iconSize} and {@code iconColor} properties (so FXML attributes carry over unchanged) and
 * can be used as the {@code graphic} of any control.
 * <p>
 * <b>Never throws on an unknown icon.</b> If the literal cannot be resolved, a neutral placeholder
 * (muted rounded square) is rendered and a warning is logged by {@link IconifyIconService}. This is
 * the key difference from {@code FontIcon}, which throws at runtime for invalid literals.
 * <p>
 * CSS: styleClass {@code iconify-icon}; supports {@code -fx-icon-size} and {@code -fx-icon-color}.
 */
public class IconifyIcon extends Region {

    private static final double DEFAULT_SIZE = IconifyIconService.VIEWBOX_SIZE; // 16
    private static final Color DEFAULT_COLOR = Color.web("#212529"); // Bootstrap body text color

    private final Group content = new Group();
    private final Scale scale = new Scale(1, 1);

    public IconifyIcon() {
        getStyleClass().add("iconify-icon");
        setManaged(true);
        content.getTransforms().add(scale);
        getChildren().add(content);

        iconLiteral.addListener((obs, o, n) -> rebuild());
        iconSize.addListener((obs, o, n) -> applySize());
        iconColor.addListener((obs, o, n) -> applyColor());

        applySize();
        rebuild();
    }

    public IconifyIcon(String iconLiteral) {
        this();
        setIconLiteral(iconLiteral);
    }

    // --- iconLiteral -------------------------------------------------------

    private final StringProperty iconLiteral = new SimpleStringProperty(this, "iconLiteral");

    public final StringProperty iconLiteralProperty() {
        return iconLiteral;
    }

    public final String getIconLiteral() {
        return iconLiteral.get();
    }

    public final void setIconLiteral(String value) {
        iconLiteral.set(value);
    }

    // --- iconSize (styleable: -fx-icon-size) -------------------------------

    private final DoubleProperty iconSize = new SimpleDoubleProperty(this, "iconSize", DEFAULT_SIZE);

    public final DoubleProperty iconSizeProperty() {
        return iconSize;
    }

    public final double getIconSize() {
        return iconSize.get();
    }

    public final void setIconSize(double value) {
        iconSize.set(value);
    }

    // --- iconColor (styleable: -fx-icon-color) -----------------------------

    private final ObjectProperty<Paint> iconColor =
            new SimpleObjectProperty<>(this, "iconColor", DEFAULT_COLOR);

    public final ObjectProperty<Paint> iconColorProperty() {
        return iconColor;
    }

    public final Paint getIconColor() {
        return iconColor.get();
    }

    public final void setIconColor(Paint value) {
        iconColor.set(value);
    }

    // --- rendering ---------------------------------------------------------

    private void rebuild() {
        content.getChildren().clear();
        // Honour Ikonli-style "literal:size[:color]" suffixes for backwards compatibility.
        applyInlineModifiers(getIconLiteral());
        List<IconifyIconService.IconPath> paths =
                IconifyIconService.getInstance().resolve(getIconLiteral());

        if (paths.isEmpty()) {
            content.getChildren().add(buildPlaceholder());
        } else {
            for (IconifyIconService.IconPath p : paths) {
                SVGPath svg = new SVGPath();
                svg.setContent(p.data());
                svg.setFillRule(p.evenOdd() ? FillRule.EVEN_ODD : FillRule.NON_ZERO);
                content.getChildren().add(svg);
            }
        }
        applyColor();
        applySize();
    }

    /**
     * Applies an Ikonli-style {@code literal:size[:color]} suffix, if present, to this icon's
     * {@code iconSize} / {@code iconColor}. The suffix is otherwise ignored by the renderer
     * (the service resolves the bare icon name).
     */
    private void applyInlineModifiers(String literal) {
        if (literal == null) {
            return;
        }
        String[] parts = literal.split(":");
        if (parts.length >= 2) {
            try {
                setIconSize(Double.parseDouble(parts[1].trim()));
            } catch (NumberFormatException ignored) {
                // Not a numeric size; leave iconSize unchanged.
            }
        }
        if (parts.length >= 3 && !parts[2].isBlank()) {
            try {
                setIconColor(Color.web(parts[2].trim()));
            } catch (IllegalArgumentException ignored) {
                // Not a valid colour; leave iconColor unchanged.
            }
        }
    }

    private Node buildPlaceholder() {
        // Neutral rounded square so missing icons are visible but never crash the UI.
        Rectangle r = new Rectangle(IconifyIconService.VIEWBOX_SIZE, IconifyIconService.VIEWBOX_SIZE);
        r.setArcWidth(4);
        r.setArcHeight(4);
        r.setFill(Color.TRANSPARENT);
        r.setStroke(Color.web("#adb5bd"));
        r.setStrokeWidth(1);
        return r;
    }

    private void applyColor() {
        Paint paint = getIconColor();
        for (Node n : content.getChildren()) {
            if (n instanceof SVGPath svg) {
                svg.setFill(paint);
            }
        }
    }

    private void applySize() {
        double size = getIconSize();
        double factor = size / IconifyIconService.VIEWBOX_SIZE;
        scale.setX(factor);
        scale.setY(factor);
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        requestLayout();
    }

    @Override
    protected void layoutChildren() {
        // Map the icon's 16x16 viewBox onto this region's box. The Scale (pivot 0,0)
        // already converts viewBox coordinates to pixels, so the content group must stay
        // anchored at the origin (0,0). Do NOT use relocate(): relocate() compensates for
        // the glyph's actual painted path bounds, which shoves icons that have padding
        // inside their viewBox (e.g. bi-x, bi-chevron-left, bi-dash) into the top-left
        // corner and makes them look misaligned and too small.
        content.setLayoutX(0);
        content.setLayoutY(0);
    }

    // --- CSS metadata ------------------------------------------------------

    private StyleableProperty<Number> iconSizeStyleable;
    private StyleableProperty<Paint> iconColorStyleable;

    private static final CssMetaData<IconifyIcon, Number> ICON_SIZE_META =
            new CssMetaData<>("-fx-icon-size", SizeConverter.getInstance(), DEFAULT_SIZE) {
                @Override
                public boolean isSettable(IconifyIcon n) {
                    return !n.iconSize.isBound();
                }

                @Override
                public StyleableProperty<Number> getStyleableProperty(IconifyIcon n) {
                    if (n.iconSizeStyleable == null) {
                        n.iconSizeStyleable = new StyleableNumberBridge(n);
                    }
                    return n.iconSizeStyleable;
                }
            };

    private static final CssMetaData<IconifyIcon, Paint> ICON_COLOR_META =
            new CssMetaData<>("-fx-icon-color", PaintConverter.getInstance(), DEFAULT_COLOR) {
                @Override
                public boolean isSettable(IconifyIcon n) {
                    return !n.iconColor.isBound();
                }

                @Override
                public StyleableProperty<Paint> getStyleableProperty(IconifyIcon n) {
                    if (n.iconColorStyleable == null) {
                        n.iconColorStyleable = new StyleablePaintBridge(n);
                    }
                    return n.iconColorStyleable;
                }
            };

    private static final List<CssMetaData<? extends Styleable, ?>> CSS_META;

    static {
        List<CssMetaData<? extends Styleable, ?>> list = new ArrayList<>(Region.getClassCssMetaData());
        list.add(ICON_SIZE_META);
        list.add(ICON_COLOR_META);
        CSS_META = Collections.unmodifiableList(list);
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return CSS_META;
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return CSS_META;
    }

    /** Bridges the CSS {@code -fx-icon-size} value into {@link #iconSize}. */
    private static final class StyleableNumberBridge extends StyleableObjectProperty<Number> {
        private final IconifyIcon owner;

        StyleableNumberBridge(IconifyIcon owner) {
            super(DEFAULT_SIZE);
            this.owner = owner;
        }

        @Override
        protected void invalidated() {
            owner.setIconSize(get().doubleValue());
        }

        @Override
        public Object getBean() {
            return owner;
        }

        @Override
        public String getName() {
            return "iconSizeStyle";
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return ICON_SIZE_META;
        }
    }

    /** Bridges the CSS {@code -fx-icon-color} value into {@link #iconColor}. */
    private static final class StyleablePaintBridge extends StyleableObjectProperty<Paint> {
        private final IconifyIcon owner;

        StyleablePaintBridge(IconifyIcon owner) {
            super(DEFAULT_COLOR);
            this.owner = owner;
        }

        @Override
        protected void invalidated() {
            owner.setIconColor(get());
        }

        @Override
        public Object getBean() {
            return owner;
        }

        @Override
        public String getName() {
            return "iconColorStyle";
        }

        @Override
        public CssMetaData<? extends Styleable, Paint> getCssMetaData() {
            return ICON_COLOR_META;
        }
    }
}
