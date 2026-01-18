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

package org.fxt.freexmltoolkit.controls.v2.view;

/**
 * Custom SVG icon paths for XSD SimpleType and ComplexType visualization.
 * <p>
 * SimpleType icons are designed to look like their base types but with a distinctive
 * "S" badge in the bottom-right corner to indicate they are type definitions.
 * <p>
 * ComplexType icons show hierarchical structure/composition.
 * <p>
 * All icons use a 16x16 viewBox.
 *
 * @see BootstrapIconPaths
 * @see SvgIconRenderer
 */
public final class XsdTypeIconPaths {

    private XsdTypeIconPaths() {
        // Utility class
    }

    /**
     * Registers all XSD type icons with BootstrapIconPaths.
     * Should be called during application startup.
     */
    public static void registerAll() {
        // ComplexType definition icon - hierarchical structure box
        BootstrapIconPaths.registerPath("xsd-complex-type", COMPLEX_TYPE);

        // Element with ComplexType reference - box with nested content
        BootstrapIconPaths.registerPath("xsd-element-complex", ELEMENT_COMPLEX);

        // SimpleType icons with "S" badge
        BootstrapIconPaths.registerPath("xsd-simple-generic", SIMPLE_GENERIC);
        BootstrapIconPaths.registerPath("xsd-simple-string", SIMPLE_STRING);
        BootstrapIconPaths.registerPath("xsd-simple-number", SIMPLE_NUMBER);
        BootstrapIconPaths.registerPath("xsd-simple-decimal", SIMPLE_DECIMAL);
        BootstrapIconPaths.registerPath("xsd-simple-boolean", SIMPLE_BOOLEAN);
        BootstrapIconPaths.registerPath("xsd-simple-date", SIMPLE_DATE);
        BootstrapIconPaths.registerPath("xsd-simple-time", SIMPLE_TIME);
        BootstrapIconPaths.registerPath("xsd-simple-id", SIMPLE_ID);
        BootstrapIconPaths.registerPath("xsd-simple-uri", SIMPLE_URI);
        BootstrapIconPaths.registerPath("xsd-simple-binary", SIMPLE_BINARY);
        BootstrapIconPaths.registerPath("xsd-simple-token", SIMPLE_TOKEN);
        BootstrapIconPaths.registerPath("xsd-simple-name", SIMPLE_NAME);
        BootstrapIconPaths.registerPath("xsd-simple-language", SIMPLE_LANGUAGE);
    }

    // ==================== ComplexType Icon ====================

    /**
     * ComplexType Definition: Hierarchical structure showing composition.
     * A box with header and nested element rectangles inside.
     * Design: UML-like class diagram box showing internal structure.
     * Used for ComplexType definitions (the "C" badge version).
     */
    public static final String COMPLEX_TYPE =
        // Outer box with rounded corners
        "M2 1.5A1.5 1.5 0 0 1 3.5 0h9A1.5 1.5 0 0 1 14 1.5v13a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 2 14.5v-13z" +
        "M3.5 1a.5.5 0 0 0-.5.5v13a.5.5 0 0 0 .5.5h9a.5.5 0 0 0 .5-.5v-13a.5.5 0 0 0-.5-.5h-9z" +
        // Header divider line
        "M3 4h10v1H3z" +
        // Nested element 1 (small box)
        "M4 6h4v1.5H4z" +
        // Nested element 2 (small box, indented)
        "M5 8.5h5v1.5H5z" +
        // Nested element 3 (small box)
        "M4 11h6v1.5H4z";

    /**
     * Element with ComplexType reference: A structured box icon.
     * Shows that this element contains complex/structured content.
     * Design: Box with internal grid/structure pattern - like a container with items.
     */
    public static final String ELEMENT_COMPLEX =
        // Main document shape with fold corner
        "M2 2a2 2 0 0 1 2-2h5.5L14 4.5V14a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V2z" +
        "M3 2a1 1 0 0 1 1-1h4.5v3A1.5 1.5 0 0 0 10 5.5h3V14a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V2z" +
        // Internal structure - tree/hierarchy lines showing complex content
        "M5 7h2v1H5z" +
        "M6 8.5h3v1H6z" +
        "M5 10h2v1H5z" +
        "M6 11.5h3v1H6z" +
        // Vertical connector line
        "M4.5 7v5.5";

    // ==================== SimpleType Badge Component ====================

    /**
     * The "S" badge used in bottom-right corner of SimpleType icons.
     * Positioned at x=10, y=10 with size 6x6.
     */
    private static final String S_BADGE =
        // Badge circle background (will be filled)
        "M10 10a3 3 0 1 0 6 0a3 3 0 0 0-6 0z" +
        // Letter "S" inside (scaled and positioned)
        "M13 9.2c-.4-.3-.9-.4-1.3-.2-.3.1-.4.3-.4.5 0 .2.1.3.3.4l.6.2c.4.1.7.3.9.5.2.2.3.5.3.8 0 .4-.2.7-.5.9-.3.2-.7.3-1.1.3-.5 0-1-.1-1.4-.4l.3-.6c.3.2.7.4 1.1.4.2 0 .4 0 .5-.1.1-.1.2-.2.2-.4 0-.2-.1-.3-.3-.4l-.6-.2c-.4-.1-.7-.3-.9-.5-.2-.2-.3-.5-.3-.8 0-.4.2-.7.5-.9.3-.2.7-.3 1.2-.3.4 0 .8.1 1.2.3l-.3.5z";

    // ==================== SimpleType Icons ====================

    /**
     * Generic SimpleType: "T" symbol with S badge.
     * Used when base type is unknown.
     */
    public static final String SIMPLE_GENERIC =
        // Letter "T" symbol (main icon area 0-12)
        "M1 2h10v1.5H6.5V11H5V3.5H1V2z" +
        S_BADGE;

    /**
     * SimpleType based on string: "Aa" text with S badge.
     * Shows typography/text nature.
     */
    public static final String SIMPLE_STRING =
        // Letter "A" with crossbar
        "M4.5 2L1 11h1.5l.8-2h3.4l.8 2H9L5.5 2h-1zm.5 2l1.2 3H3.8L5 4z" +
        // Small "a"
        "M9.5 6c-.8 0-1.5.7-1.5 1.5v.5c0 .6.3 1.1.8 1.4-.2.3-.3.5-.3.8 0 .4.4.8 1 .8h1c.3 0 .5-.2.5-.5v-3c0-.8-.7-1.5-1.5-1.5zm0 1c.3 0 .5.2.5.5V9h-.5c-.3 0-.5-.2-.5-.5V7.5c0-.3.2-.5.5-.5z" +
        S_BADGE;

    /**
     * SimpleType based on integer: "123" numbers with S badge.
     * Shows numeric nature.
     */
    public static final String SIMPLE_NUMBER =
        // "1" digit
        "M2 10V4.5L1 5.5V4l1.5-1.5H3.5V10H2z" +
        // "2" digit
        "M5 4c.8 0 1.5.5 1.5 1.3 0 .5-.3 1-.8 1.5L4 8.5h2.5V10H3.5V8.7l2-2.2c.2-.2.3-.4.3-.6 0-.3-.2-.5-.5-.5-.4 0-.6.2-.6.5H3.5C3.5 4.7 4.2 4 5 4z" +
        // "3" digit
        "M8 4c.8 0 1.5.5 1.5 1.2 0 .4-.2.7-.5.9.4.2.7.5.7 1 0 .8-.7 1.4-1.7 1.4-.9 0-1.5-.5-1.5-1.2h1c0 .2.2.4.5.4s.5-.2.5-.4c0-.3-.2-.4-.6-.4H7.2V6h.5c.3 0 .5-.2.5-.4 0-.2-.2-.4-.5-.4-.3 0-.5.2-.5.4h-1C6.2 4.5 7 4 8 4z" +
        S_BADGE;

    /**
     * SimpleType based on decimal/float: ".5" with S badge.
     * Shows decimal/floating point nature.
     */
    public static final String SIMPLE_DECIMAL =
        // Decimal point
        "M2 9a1 1 0 1 0 0 2 1 1 0 0 0 0-2z" +
        // "5" digit (larger)
        "M4.5 3H9V4.5H5.5V6H8c1.1 0 2 .9 2 2s-.9 2-2 2H4.5V8.5H8c.3 0 .5-.2.5-.5s-.2-.5-.5-.5H4.5V3z" +
        S_BADGE;

    /**
     * SimpleType based on boolean: Toggle switch with S badge.
     */
    public static final String SIMPLE_BOOLEAN =
        // Toggle track (rounded rectangle)
        "M4 5.5A2.5 2.5 0 0 0 1.5 8 2.5 2.5 0 0 0 4 10.5h4A2.5 2.5 0 0 0 10.5 8 2.5 2.5 0 0 0 8 5.5H4z" +
        "M4 6.5h4a1.5 1.5 0 0 1 0 3H4a1.5 1.5 0 0 1 0-3z" +
        // Toggle knob (on position)
        "M8 6a2 2 0 1 0 0 4 2 2 0 0 0 0-4z" +
        S_BADGE;

    /**
     * SimpleType based on date: Calendar with S badge.
     */
    public static final String SIMPLE_DATE =
        // Calendar body
        "M1 3.5A1.5 1.5 0 0 1 2.5 2h7A1.5 1.5 0 0 1 11 3.5v7A1.5 1.5 0 0 1 9.5 12h-7A1.5 1.5 0 0 1 1 10.5v-7z" +
        "M2.5 3a.5.5 0 0 0-.5.5v7a.5.5 0 0 0 .5.5h7a.5.5 0 0 0 .5-.5v-7a.5.5 0 0 0-.5-.5h-7z" +
        // Header line
        "M1 5h10v1H1z" +
        // Calendar binding holes
        "M3.5 1v2M8.5 1v2" +
        // Date grid dots
        "M3 7h1.5v1.5H3zM6 7h1.5v1.5H6zM3 9h1.5v1.5H3z" +
        S_BADGE;

    /**
     * SimpleType based on time: Clock with S badge.
     */
    public static final String SIMPLE_TIME =
        // Clock circle
        "M6 1a5 5 0 1 0 0 10A5 5 0 0 0 6 1zM2 6a4 4 0 1 1 8 0 4 4 0 0 1-8 0z" +
        // Clock hands
        "M6 3v3.5l2 1" +
        S_BADGE;

    /**
     * SimpleType based on ID: Key with S badge.
     */
    public static final String SIMPLE_ID =
        // Key head (circle)
        "M4 3a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM2.5 5.5a1.5 1.5 0 1 1 3 0 1.5 1.5 0 0 1-3 0z" +
        // Key shaft
        "M5.5 6.5L10 11M8 9l1.5 1.5M9 8l1.5 1.5" +
        S_BADGE;

    /**
     * SimpleType based on URI: Link with S badge.
     */
    public static final String SIMPLE_URI =
        // Chain link 1
        "M4.5 4A2.5 2.5 0 0 0 2 6.5v1A2.5 2.5 0 0 0 4.5 10H5V9H4.5A1.5 1.5 0 0 1 3 7.5v-1A1.5 1.5 0 0 1 4.5 5H7V4H4.5z" +
        // Chain link 2
        "M7.5 4A2.5 2.5 0 0 1 10 6.5v1A2.5 2.5 0 0 1 7.5 10H7V9h.5A1.5 1.5 0 0 0 9 7.5v-1A1.5 1.5 0 0 0 7.5 5H5V4h2.5z" +
        // Connection bar
        "M4 7h4v1H4z" +
        S_BADGE;

    /**
     * SimpleType based on binary: Binary file with S badge.
     */
    public static final String SIMPLE_BINARY =
        // Document shape
        "M2 1.5A1.5 1.5 0 0 1 3.5 0H7l4 4v7.5A1.5 1.5 0 0 1 9.5 13h-6A1.5 1.5 0 0 1 2 11.5v-10z" +
        "M3.5 1a.5.5 0 0 0-.5.5v10a.5.5 0 0 0 .5.5h6a.5.5 0 0 0 .5-.5V5H7V1H3.5z" +
        // Binary "01" text
        "M4 6.5a.75.75 0 1 1 1.5 0 .75.75 0 0 1 0 1.5.75.75 0 0 1-.75-.75v-1.5z" +
        "M6 6h1v2.5H6z" +
        S_BADGE;

    /**
     * SimpleType based on token: Quote marks with S badge.
     */
    public static final String SIMPLE_TOKEN =
        // Opening quote
        "M3 4c-.8 0-1.5.7-1.5 1.5S2.2 7 3 7c.4 0 .8-.2 1-.5L3.5 8c-.2.3-.5.5-1 .5C1.5 8.5.5 7.5.5 6.5S1.5 3 3 3c.6 0 1 .3 1.2.6L3.5 4.2c-.1-.1-.3-.2-.5-.2z" +
        // Closing quote
        "M7 4c-.8 0-1.5.7-1.5 1.5S6.2 7 7 7c.4 0 .8-.2 1-.5L7.5 8c-.2.3-.5.5-1 .5C5.5 8.5 4.5 7.5 4.5 6.5S5.5 3 7 3c.6 0 1 .3 1.2.6L7.5 4.2c-.1-.1-.3-.2-.5-.2z" +
        // Underline (token indicator)
        "M1 10h8v1H1z" +
        S_BADGE;

    /**
     * SimpleType based on name: Tag/label with S badge.
     */
    public static final String SIMPLE_NAME =
        // Tag shape
        "M1 3v4.586l5.707 5.707a1 1 0 0 0 1.414 0l3.586-3.586a1 1 0 0 0 0-1.414L6 2.586A1 1 0 0 0 5.586 2H2a1 1 0 0 0-1 1z" +
        "M2 3h3.586l5 5-2.586 2.586-5-5V3z" +
        // Tag hole
        "M3.5 4a.5.5 0 1 0 0 1 .5.5 0 0 0 0-1z" +
        S_BADGE;

    /**
     * SimpleType based on language: Globe with S badge.
     */
    public static final String SIMPLE_LANGUAGE =
        // Globe circle
        "M6 1a5 5 0 1 0 0 10A5 5 0 0 0 6 1zM2 6a4 4 0 1 1 8 0 4 4 0 0 1-8 0z" +
        // Latitude line
        "M2 6h8" +
        // Longitude line (vertical)
        "M6 2v8" +
        // Curved longitude
        "M4 2.5c-.5 1-1 2-1 3.5s.5 2.5 1 3.5M8 2.5c.5 1 1 2 1 3.5s-.5 2.5-1 3.5" +
        S_BADGE;
}
